;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.util
  (:require [clj-http.client :as client]
            [clojure.core.cache :as cache]
            [clojure.data.json :as json]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import (java.net URLDecoder URLEncoder)))

(defn parse-long
  "Parses a long value from a string or number."
  [v]
  (when v (if (string? v) (when-not (str/blank? v) (Long/parseLong v)) (long v))))

(defn parse-double
  "Parses a double value from a string or number."
  [v]
  (when v (if (string? v) (when-not (str/blank? v) (Double/parseDouble v)) (double v))))

(defn parse-ints
  "Returns the integers found within the string."
  [string]
  (when-not (str/blank? string)
    (map #(Integer/parseInt %) (re-seq #"\d+" string))))

(defn find-ints
  "Returns the integers targeted by the regex pattern."
  [pattern string]
  (when-not (str/blank? string)
    (->> (re-find pattern string) (drop 1) (map #(Integer/parseInt %)))))

(defn encoded-url
  "Returns an encoded url for the file (and folder) that can be used
  by clients to access the file."
  [base path]
  (str base "/" (-> (URLEncoder/encode path "UTF-8")
                    (str/replace "%2F" "/")
                    (str/replace "+" "%20"))))

(defn decoded-url
  "Returns the base url and file from the encoded url."
  [url]
  (URLDecoder/decode (str url) "UTF-8"))

(defn split-equally [num coll]
  "Split a collection into a vector of (as close as possible) equally sized parts"
  (loop [num num
         parts []
         coll coll
         c (count coll)]
    (if (<= num 0)
      parts
      (let [t (quot (+ c num -1) num)]
        (recur (dec num) (conj parts (take t coll)) (drop t coll) (- c t))))))

(defmacro dopar
  "Creates a number of doseq blocks that run in parallel."
  [thread-count [sym coll] & body]
  `(dorun (pmap
            (fn [vals#]
              (doseq [~sym vals#]
                ~@body))
            (split-equally ~thread-count ~coll))))

(defn exec
  "Flattens and sanitizes the arguments before executing the shell
  command."
  [& cmd]
  (let [args (->> cmd flatten (remove nil?) (map str))]
    (log/trace "executing" (str/join " " args))
    (apply shell/sh args)))

(defn periodically
  "Peridoically calls a function. Returns a stopping function."
  [f ms]
  (let [p (promise)]
    (future
      (while
        (= (deref p ms "running") "running")
        (f)))
    #(deliver p "stop")))

(defn get-json
  "Retrieves and caches the JSON body."
  [cache url & {:keys [auth key-fn]}]
  (if (cache/has? @cache url)
    (swap! cache #(cache/hit % url))
    (do
      (log/trace "fetching" url)
      (let [resp (client/get (if auth (auth url) url) {:accept :json})]
        (when (= (:status resp) 200)
          (log/trace "fetched" url)
          (let [json (json/read-str (:body resp) :key-fn (or key-fn (comp keyword str/lower-case)))]
            (swap! cache #(cache/miss % url json)))))))
  (get @cache url))

