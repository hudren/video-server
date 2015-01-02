;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.title
  (:require [video-server.format :refer :all]
            [video-server.util :refer :all]
            [video-server.video :refer [quality]]))

(defn- video-info
  [item]
  (if (vector? item) (second item) item))

(defn has-seasons?
  "Returns whether the title has seasons."
  [title]
  (some #(:season (video-info %)) (:videos title)))

(defn has-episodes?
  "Returns whether the title has episodes for the given season."
  [title season]
  (> (count (filter #(= (:season (video-info %)) season) (:videos title))) 1))

(defn has-parts?
  "Returns whether the title has multiple parts."
  [title]
  (and (> (count (:videos title)) 1)
       (not (has-seasons? title))))

(defn season-title
  "Returns the season title, if there is one."
  [title season]
  (when season
    (get-in title [:info :seasons (-> season str keyword) :title])))

(defn season-meta-titles
  "Returns the season titles from the metadata."
  [title]
  (into {} (for [season (-> title :info :seasons)]
             [(parse-long (name (first season))) (:title (second season))])))

(defn season-titles
  "Returns the season number and title pairs for the given title."
  [title]
  (let [titles (season-meta-titles title)]
    (seq (map #(vector % (str "Season " % (when (titles %) (str ": " (titles %)))))
              (sort (remove nil? (distinct (map #(:season (video-info %)) (:videos title)))))))))

(defn season-desc
  "Returns a description of the seasons belonging to the title."
  [title]
  (let [seasons (into #{} (map #(:season (video-info %)) (:videos title)))]
    (str "Season" (if (> (count seasons) 1) "s") " " (format-ranges (find-ranges seasons)))))

(defn episode-title
  "Returns the episode title from the metadata or video."
  [title video]
  (or (get-in title [:info
                     :seasons (-> (:season video) str keyword)
                     :episodes (-> (:episode video) str keyword)
                     :title])
      (:episode-title video)))

(defn episodes
  "Comparator for sorting videos by seasons and episodes or parts."
  [fk1 fk2]
  (let [k1 (second fk1)
        k2 (second fk2)
        c (compare (:season k1) (:season k2))]
    (if-not (zero? c) c
      (compare (:episode k1) (:episode k2)))))

(defn best-video
  "Returns the best video for the specified season and episode."
  [videos season episode]
  (let [videos (if season (filter (fn [[f k]] (= (:season k) season)) videos) videos)
        videos (if episode (filter (fn [[f k]] (= (:episode k) episode)) videos) videos)]
    (first (sort episodes videos))))

(defn best-container
  "Returns the best container to play within a web browser."
  [video]
  (first (filter #(and (.contains (:video %) "H.264") (.contains (:audio %) "AAC"))
                 (sort quality (:containers video)))))

