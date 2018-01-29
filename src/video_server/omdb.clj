;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.omdb
  (:require [clojure.core.cache :as cache]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [video-server.util :refer :all])
  (:import java.net.URLEncoder
           java.text.SimpleDateFormat))

(defonce ^:private cache (atom (cache/lru-cache-factory {})))

(def ^:private api-key
  (delay (try (str/trim (slurp (io/resource "omdb.key")))
              (catch Exception _))))

(defn- authorize
  "Adds the api key to the url."
  [url]
  (str url (when-let [key @api-key] (str (if (.contains url "?") "&" "?") "apikey=" key))))

(defn- filter-values
  [m]
  (into {} (remove #(= (second %) "N/A") m)))

(defn- multi
  "Returns the comma separated values as a sequence."
  [s]
  (when-not (str/blank? s)
    (map str/trim (str/split s #","))))

(defn- date
  "Returns an ISO formatted date."
  [d]
  (let [input  (SimpleDateFormat. "dd MMM yyyy")
        output (SimpleDateFormat. "yyyy-MM-dd")]
    (when d (.format output (.parse input d)))))

(defn retrieve-id
  "Fetches the metadata for the given id."
  [id]
  (when-let [resp (get-json cache (str "http://www.omdbapi.com/?plot=full&r=json&i=" id)
                            :auth authorize)]
    (when (= (:response resp) "True")
      (filter-values resp))))

(defn retrieve-title
  "Fetches the metadata for the given title and year."
  [title & [series? year]]
  (let [resp (get-json cache (str "http://www.omdbapi.com/?plot=full&r=json&t=" (URLEncoder/encode title "UTF-8")
                                  (when-not (nil? series?) (if series? "&type=series" "&type=movie"))
                                  (when year (str "&y=" year)))
                       :auth authorize)]
    (filter-values resp)))

(defn retrieve-episode
  "Fetches the metadata for the given episode."
  [title season episode]
  (let [resp (get-json cache (str "http://www.omdbapi.com/?plot=full&r=json&t=" (URLEncoder/encode title "UTF-8")
                                  "&type=episode&season=" season "&episode=" episode)
                       :auth authorize)]
    (filter-values resp)))

(defn omdb-info
  "Extracts fields for storage from the metadata."
  [omdb]
  (let [md (merge (select-keys omdb [:title :runtime :rated :plot :type :poster])
                  {:imdb-id   (:imdbid omdb)
                   :released  (date (:released omdb))
                   :year      (first (parse-ints (:year omdb)))
                   :genres    (multi (:genre omdb))
                   :directors (when-let [d (:director omdb)] (list d))
                   :stars     (multi (:actors omdb))
                   :languages (multi (:language omdb))})]
    (into {} (remove (comp nil-or-blank? second) md))))

(defn omdb-episode-info
  "Extracts and converts fields from the episode metadata."
  [omdb]
  (merge (select-keys omdb [:title :runtime :plot])
         {:released  (date (:released omdb))
          :directors (multi (:director omdb))
          :writers   (multi (:writer omdb))}))

(defn omdb-metadata
  "Queries for metadata related to the given title and year."
  [title & [series? year]]
  (let [resp (retrieve-title title series? year)]
    (when (= (:response resp) "True")
      resp)))

(defn omdb-season-metadata
  "Queries for all episode metadata for the given title and season."
  [title season]
  (loop [seasons nil episode 1]
    (let [resp (retrieve-episode title season episode)]
      (if (= (:response resp) "True")
        (recur (update-in seasons [:episodes episode] merge (omdb-episode-info resp)) (inc episode))
        seasons))))

(defn omdb-episode-metadata
  "Queries for metadata for a single episode."
  [title season episode]
  (let [resp (retrieve-episode title season episode)]
    (when (= (:response resp) "True")
      (omdb-episode-info resp))))
