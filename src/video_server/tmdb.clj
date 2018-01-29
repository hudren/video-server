;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.tmdb
  (:require [clojure.core.cache :as cache]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [video-server.util :refer :all])
  (:import java.net.URLEncoder))

(defonce ^:private cache (atom (cache/lru-cache-factory {})))

(def ^:private api-key
  (delay (try (str/trim (slurp (io/resource "tmdb.key")))
              (catch Exception _))))

(defn- authorize
  "Adds the api key to the url."
  [url]
  (str url (when-let [key @api-key] (str (if (.contains url "?") "&" "?") "api_key=" key))))

(defn- norm-keyword
  "Normalizes Freebase JSON keys as Clojure keywords."
  [n]
  (-> n
      (str/replace \_ \-)
      keyword))

(defn search-tmdb
  "Queries The Movie Database to find information about the title."
  [title & [series? year]]
  (get-json cache
            (str "https://api.themoviedb.org/3/search/"
                 (if series? "tv" "movie")
                 "?query=" (URLEncoder/encode title "UTF-8")
                 (when year (str (if series? "&first_air_date_year=" "&year=") year)))
            :auth authorize :key-fn norm-keyword))

(defn search-movie-id
  "Returns the TMDb id for the specified movie title."
  [title]
  (->> title (search-tmdb "movie") :results first :id))

(defn tmdb-id
  "Returns the TMDb id for the given IMDb id, or nil."
  [imdb]
  (-> (get-json cache
                (str "https://api.themoviedb.org/3/find/" imdb "?external_source=imdb_id")
                :auth authorize :key-fn norm-keyword)
      :movie-results first :id))

(defn fetch-tmdb-tv
  "Returns the info for the specified TMDb movie id."
  [id]
  (get-json cache
            (str "https://api.themoviedb.org/3/tv/" id)
            :auth authorize :key-fn norm-keyword))

(defn fetch-tmdb-movie
  "Returns the info for the specified TMDb movie id."
  [id]
  (get-json cache
            (str "https://api.themoviedb.org/3/movie/" id "?append_to_response=credits,keywords,videos")
            :auth authorize :key-fn norm-keyword))

(defn search-for-ids
  [title & [series? year]]
  (when-let [result (first (:results (search-tmdb title series? year)))]
    (let [id (:id result)
          md (if series? (fetch-tmdb-tv id) (fetch-tmdb-movie id))]
      {:id id :imdb-id (:imdb-id md)})))

(defn tmdb-metadata
  "Returns the metadata for the specified movie."
  [imdb-or-id & [series?]]
  (let [id (if (and (string? imdb-or-id) (.startsWith imdb-or-id "tt"))
             (tmdb-id imdb-or-id)
             imdb-or-id)]
    (when id (if series? (fetch-tmdb-tv id) (fetch-tmdb-movie id)))))

(defn- names
  "Extracts the names from a sequence of maps."
  [vs]
  (seq (map (comp str/trim :name) vs)))

(defn crew
  "Extracts the names for a particular job."
  [md job]
  (->> md :credits :crew (filter #(= (:job %) job)) names))

(defn trailer
  "Returns the first YouTube trailer."
  [md]
  (when-let [v (->> md :videos :results
                    (filter #(and (= (:type %) "Trailer") (= (:site %) "YouTube")))
                    first :key)]
    (str "https://www.youtube.com/watch?v=" v)))

(defn tmdb-info
  "Returns normalized information regarding the movie."
  [md]
  (let [info (merge (select-keys md [:title :imdb-id :tagline])
                    {:released            (:release-date md)
                     :genres              (-> md :genres names)
                     :plot                (:overview md)
                     :creators            (-> md :created-by names)
                     :producers           (crew md "Producer")
                     :executive-producers (crew md "Executive Producer")
                     :directors           (crew md "Director")
                     :writers             (crew md "Writer")
                     :actors              (-> md :credits :cast names)
                     :networks            (-> md :networks names)
                     :languages           (-> md :spoken-langauges names)
                     :keywords            (-> md :keywords :keywords names)
                     :website             (:homepage md)
                     :trailer             (trailer md)})]
    (into {} (remove (comp nil-or-blank? second) info))))
