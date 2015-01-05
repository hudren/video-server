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
            [clojure.string :as str]
            [video-server.util :refer :all])
  (:import (java.net URLEncoder)))

(defonce ^:private cache (atom (cache/lru-cache-factory {})))

(defn- filter-values
  [m]
  (into {} (remove #(= (second %) "N/A") m)))

(defn- multi
  "Returns the comma separated values as a sequence."
  [s]
  (when-not (str/blank? s)
    (map str/trim (str/split s #","))))

(defn retrieve-id
  "Fetches the metadata for the given id."
  [id]
  (when-let [resp (get-json cache (str "http://www.omdbapi.com/?plot=full&r=json&i=" id))]
    (when (= (:response resp) "True")
      (filter-values resp))))

(defn retrieve-title
  "Fetches the metadata for the given title and year."
  [title & [year]]
  (let [resp (get-json cache (str "http://www.omdbapi.com/?plot=full&r=json&t=" (URLEncoder/encode title "UTF-8")
                                  (when year (str "&y=" year))))]
    (filter-values resp)))

(defn omdb-info
  "Extracts fields for storage from the metadata"
  [omdb]
  (let [md (merge (select-keys omdb [:title :runtime :rated :plot :type :poster])
                  {:imdb-id (:imdbid omdb)
                   :year (first (parse-ints (:year omdb)))
                   :genres (multi (:genre omdb))
                   :directors (when-let [d (:director omdb)] (list d))
                   :stars (multi (:actors omdb))
                   :languages (multi (:language omdb))})]
    (into {} (remove (comp nil? second) md))))

(defn omdb-metadata
  "Queries for metadata related to the given title and year."
  [title & [series year duration]]
  (let [resp (retrieve-title title year)]
    (when (= (:response resp) "True")
      resp)))

