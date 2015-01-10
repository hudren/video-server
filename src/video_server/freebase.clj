;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.freebase
  (:require [clojure.core.cache :as cache]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [video-server.util :refer :all])
  (:import (java.net URLEncoder)))

(defonce ^:private cache (atom (cache/lru-cache-factory {})))

(def ^:private api-key (delay (str/trim (slurp (io/resource "freebase.key")))))

(defn- path->key
  "Converts key paths to keyword names."
  [n]
  (if (.startsWith n "/") (str/join "-" (str/split (subs n 1) #"/")) n))

(defn- norm-keyword
  "Normalizes Freebase JSON keys as Clojure keywords."
  [n]
  (-> n
      name
      path->key
      str/lower-case
      keyword))

(defn- authorize
  "Adds the api key to the url."
  [url]
  (str url (when-let [key @api-key] (str (if (.contains url "?") "&" "?") "key=" key))))

(defn search-freebase
  "Queries Freebase to find information about the title."
  [ftype title]
  (:result (get-json cache
                     (str "https://www.googleapis.com/freebase/v1/search?type=" ftype
                          "&query=" (URLEncoder/encode title "UTF-8"))
                     :auth authorize
                     :key-fn norm-keyword)))

(defn query-film
  "Queries Freebase for the mid related to the film title."
  [title]
  (if-let [results (search-freebase "/film/film" title)]
    (filter #(> (:score %) 5) results)))

(defn query-tv
  "Queries Freebase for the mid related to the TV program title."
  [title]
  (if-let [results (search-freebase "/tv/tv_program" title)]
    (filter #(> (:score %) 5) results)))

(defn get-topic
  "Fetches the topic for the given Freebase mid."
  [mid]
  (when mid (get-json cache (str "https://www.googleapis.com/freebase/v1/topic" mid)
                      :auth authorize
                      :key-fn norm-keyword)))

(defn- values
  ([md k]
   (-> md :property k :values))
  ([md k lang]
   (seq (filter #(= (:lang %) (name lang)) (values md k)))))

(defn- value
  ([md k]
   (first (values md k)))
  ([md k lang]
   (first (values md k lang))))

(defn get-released [md]
  (when-let [released (or (values md :film-film-initial_release_date)
                          (values md :tv-tv_program-air_date_of_first_episode))]
     (first (sort (map :value released)))))

(defn get-year [md]
  (first (parse-ints (get-released md))))

(defn get-runtimes [md]
  (seq (map (comp :value first :values :film-film_cut-runtime :property)
            (values md :film-film-runtime))))

(defn exact-match?
  "Determines whether an item is an exact match for the given title,
  year, and duration."
  [title year duration item]
  (and (.equalsIgnoreCase title (:name item))
       (or (not year)
           (= year (get-year (get-topic (:mid item)))))
       (or (not duration)
           (some #(and (number? %) (<= (Math/abs (- duration %)) 3))
                 (get-runtimes (get-topic (:mid item)))))))

(defn best-matches
  "Returns the best matches for the given title."
  [title series? year duration]
  (if series?
    (query-tv title)
    (let [films (query-film title)]
      (if-let [film (seq (filter (partial exact-match? title year duration) films))]
        film
        (let [shows (reverse (sort-by :score (into films (query-tv title))))]
          (if-let [program (seq (filter (partial exact-match? title year duration) shows))]
            program
            shows))))))

(defn freebase-metadata
  "Fetches Freebase for film related metadata."
  [title & [series? year duration]]
  (get-topic (:mid (first (best-matches title series? year duration)))))

(defn get-title [md]
  (when-let [desc (value md :type-object-name :en)]
    (:value desc)))

(defn- clean [s]
  (seq (distinct s)))

(defn get-notable-for [md]
  (clean (map :text (values md :common-topic-notable_for :en))))

(defn get-notable-types [md]
  (clean (map :text (values md :common-topic-notable_types :en))))

(defn get-imdb [md]
  (let [urls (map :value (values md :common-topic-topic_equivalent_webpage))]
        (first (filter #(.contains % "imdb.com") urls))))

(defn get-imdb-id [md]
  (if-let [url (get-imdb md)]
    (second (re-find #"/title/(.*)/" url))
    (let [keys (map :value (values md :type-object-key))]
      (when-let [key (first (filter #(.contains % "imdb") keys))]
        (second (re-find #"authority/imdb/title/(.*)" key))))))

(defn get-description [md]
  (when-let [desc (value md :common-topic-description :en)]
    (:value desc)))

(defn get-website [md]
  (:value (value md :common-topic-official_website)))

(defn get-language [md]
  (or (:text (value md :film-film-language :en))
      (:text (value md :film-film-language))))

(defn get-series [md]
  (:text (value md :film-film-film_series :en)))

(defn get-genres [md]
  (when-let [genres (or (values md :film-film-genre :en)
                        (values md :tv-tv_program-genre :en))]
    (clean (map :text genres))))

(defn get-rating [md]
  (when-let [ratings (values md :film-film-rating)]
    (first (map #(subs % 0 (- (count %) 6)) (filter #(.endsWith % " (USA)") (map :text ratings))))))

(defn get-subjects [md]
  (when-let [subjects (or (values md :film-film-subjects :en)
                          (values md :tv-tv_program-subjects :en))]
    (clean (map :text subjects))))

(defn get-taglines [md]
  (when-let [lines (values md :film-film-tagline :en)]
    (clean (map :text lines))))

(defn get-creators [md]
  (when-let [creators (values md :tv-tv_program-program_creator)]
    (clean (map :text creators))))

(defn- get-tv-producers [md]
  (when-let [producers (values md :tv-tv_program-tv_producer)]
    (map #(vector ((comp :text first :values :tv-tv_producer_term-producer :property) %)
                  ((comp :text first :values :tv-tv_producer_term-producer_type :property) %)) producers)))

(defn get-executive-producers [md]
  (if-let [producers (values md :film-film-executive_produced_by)]
    (clean (map :text producers))
    (when-let [producers (get-tv-producers md)]
      (clean (map first (filter #(= (second %) "Executive Producer") producers))))))

(defn get-producers [md]
  (if-let [producers (values md :film-film-produced_by)]
    (clean (map :text producers))
    (when-let [producers (get-tv-producers md)]
      (clean (map first (filter #(or (nil? (second %)) (= (second %) "Producer")) producers))))))

(defn get-directors [md]
  (when-let [directors (values md :film-film-directed_by)]
    (clean (map :text directors))))

(defn get-actors [md]
  (when-let [actors (values md :film-film-starring)]
    (clean (map (comp :text first :values :film-performance-actor :property) actors))))

(defn get-cast [md]
  (when-let [stars (values md :tv-tv_program-regular_cast)]
    (clean (map (comp :text first :values :tv-regular_tv_appearance-actor :property) stars))))

(defn get-networks [md]
  (when-let [networks (values md :tv-tv_program-original_network)]
    (clean (map (comp :text first :values :tv-tv_network_duration-network :property) networks))))

(defn get-country [md]
  (:text (or (value md :film-film-country :en)
             (value md :tv-tv_program-country_of_origin :en))))

(defn get-film-locations [md]
  (clean (map :text (values md :film-film-featured_film_locations :en))))

(defn get-trailers [md]
  (clean (map :value (values md :film-film-trailers))))

(defn get-wikipedia [md]
  (let [urls (map :value (values md :common-topic-topic_equivalent_webpage))
        wiki (filter #(.contains % "en.wikipedia.org") urls)
        film (filter #(.contains % "(film)") wiki)]
    (or (first film) (first wiki))))

(defn get-netflix-id [md]
  (when-let [id (value md :film-film-netflix_id)]
    (:value id)))

(defn get-netflix-genres [md]
  (clean (map :text (values md :media_common-netflix_title-netflix_genres :en))))

(defn freebase-info
  "Extracts normalized information from the metadata."
  [md]
  (let [info {:name (get-title md)
              :year (get-year md)
              :released (get-released md)
              :series (get-series md)
              :subjects (get-subjects md)
              :genres (get-genres md)
              :plot (get-description md)
              :creators (get-creators md)
              :producers (get-producers md)
              :executive-producers (get-executive-producers md)
              :directors (get-directors md)
              :actors (or (get-actors md) (get-cast md))
              :networks (get-networks md)
              :rated (get-rating md)
              :imdb-id (get-imdb-id md)
              :netflix-id (get-netflix-id md)
              :netflix-genres (get-netflix-genres md)
              :wikipedia (get-wikipedia md)
              :website (get-website md)
              :trailer (first (get-trailers md))
              :tagline (first (get-taglines md))}]
    (into {} (remove (comp nil? second) info))))

