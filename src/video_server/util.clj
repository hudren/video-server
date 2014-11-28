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
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

(defn parse-long
  "Parses a long value from a string or number."
  [v]
  (when v (if (string? v) (Long/parseLong v) (long v))))

(defn parse-double
  "Parses a double value from a string or number."
  [v]
  (when v (if (string? v) (Double/parseDouble v) (double v))))

(defn parse-ints
  "Returns the integers found within the string."
  [string]
  (map #(Integer/parseInt %) (re-seq #"\d+" string)))

(defn find-ints
  "Returns the integers targeted by the regex pattern."
  [pattern string]
  (->> (re-find pattern string) (drop 1) (map #(Integer/parseInt %))))

(defn exec
  "Flattens and sanitizes the arguments before executing the shell
  command."
  [& cmd]
  (let [args (->> cmd flatten (remove nil?) (map str))]
    (log/trace "executing" (str/join " " args))
    (apply shell/sh args)))

