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
            [video-server.video :refer [quality web-playback?]]))

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
    (or (get-in title [:info :seasons season :title])
        (get-in title [:seasons season :title]))))

(defn season-meta-titles
  "Returns the season titles from the metadata."
  [title]
  (into {} (for [season (concat (keys (:seasons title)) (-> title :info :seasons keys))]
             [season (season-title title season)])))

(defn season-titles
  "Returns the season number and title pairs for the given title."
  [title]
  (let [titles (season-meta-titles title)]
    (seq (map #(vector % (str "Season " % (when (titles %) (str ": " (titles %)))))
              (sort (remove nil? (distinct (map #(:season (video-info %)) (:videos title)))))))))

(defn season-desc
  "Returns a description of the seasons belonging to the title."
  [title]
  (let [seasons (set (map #(:season (video-info %)) (:videos title)))]
    (str "Season" (if (> (count seasons) 1) "s") " " (format-ranges (find-ranges seasons)))))

(defn episode-title
  "Returns the episode title from the metadata or video."
  [title video]
  (or (get-in title [:info :seasons (or (:season video) -1) :episodes (:episode video) :title])
      (:episode-title video)))

(defn episodes
  "Comparator for sorting videos by seasons and episodes or parts."
  [fk1 fk2]
  (let [k1 (second fk1)
        k2 (second fk2)
        c (compare (:season k1) (:season k2))]
    (if-not (zero? c) c
      (compare (:episode k1) (:episode k2)))))

(defn full-title
  "Returns the full title including part and episode."
  [title video]
  (let [series (or (:title (:info title)) (:title title))
        season (:season video)
        episode (:episode video)
        episode-title (episode-title title video)]
    (if episode
      (str series " - "
           (if season
             (if episode-title
               (str season "." episode " " episode-title)
               (str "Season " season " Episode " episode))
             (if episode-title
               (str episode ". " episode-title)
               (str "Part " episode))))
      series)))

(defn best-image
  "Returns the best image for the video."
  ([image title] (best-image image title nil nil))
  ([image title video] (best-image image title (:season video) (:episode video)))
  ([image title season episode]
   (or (get-in title [:seasons (or season -1) :episodes episode image])
       (get-in title [:seasons (or season -1) image])
       (image title)
       (get-in title [:seasons (first (sort (keys (:seasons title)))) image])
       (if-let [part (first (sort (keys (get-in title [:seasons -1 :episodes]))))]
         (get-in title [:seasons -1 :episodes part image])))))

(defn best-video
  "Returns the best video for the specified season and episode."
  [videos season episode]
  (let [videos (if season (filter (fn [[f k]] (= (:season k) season)) videos) videos)
        videos (if episode (filter (fn [[f k]] (= (:episode k) episode)) videos) videos)]
    (first (sort episodes videos))))

(defn best-container
  "Returns the best container to play within a web browser."
  [video]
  (first (filter web-playback? (sort quality (:containers video)))))

(defn best-containers
  "Returns one or more best containers for web playback."
  [video]
  (let [container (best-container video)]
    (seq (filter #(and (= (:dimension %) (:dimension container)) (web-playback? %)) (:containers video)))))

