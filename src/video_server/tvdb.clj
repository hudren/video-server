;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.tvdb
  (:require [clojure.core.cache :as cache]
            [clojure.data.zip.xml :as xml]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.zip :as zip]
            [video-server.util :refer :all])
  (:import java.net.URLEncoder))

(defonce ^:private cache (atom (cache/lru-cache-factory {})))

(def ^:private api-key
  (delay (try (str/trim (slurp (io/resource "tvdb.key")))
              (catch Exception _))))

(defn- multi
  "Returns the pipe separated values as a sequence."
  [s]
  (when-not (str/blank? s)
    (map str/trim (str/split s #"\|"))))

(defn search-tvdb
  "Searches for the series information based on series title."
  [title & [imdb]]
  (when-let [xml (get-xml cache (str "http://thetvdb.com/api/GetSeries.php?seriesname=" (URLEncoder/encode title "UTF-8")))]
    (let [root   (zip/xml-zip xml)
          series (seq (if imdb (xml/xml1-> root :Series [:IMDB_ID imdb])))
          series (or series (xml/xml1-> root :Series [:SeriesName title]))
          values (into {} (map (juxt :tag #(first (:content %))) (-> series first :content)))]
      values)))

(defn fetch-tvdb
  "Fetches complete series information as XML."
  [id]
  (try (get-xml cache (str "http://thetvdb.com/api/" @api-key "/series/" id "/all/en.xml"))
       (catch Exception _)))

(defn- thumbnail-info
  [episode]
  [(xml/xml1-> episode :SeasonNumber xml/text parse-long)
   (xml/xml1-> episode :EpisodeNumber xml/text parse-long)
   {:thumb (xml/xml1-> episode :filename xml/text)}])

(defn- episode-info
  [episode]
  [(xml/xml1-> episode :SeasonNumber xml/text parse-long)
   (xml/xml1-> episode :EpisodeNumber xml/text parse-long)
   {:title     (xml/xml1-> episode :EpisodeName xml/text)
    :plot      (xml/xml1-> episode :Overview xml/text)
    :released  (xml/xml1-> episode :FirstAired xml/text)
    :writers   (multi (xml/xml1-> episode :Writer xml/text))
    :directors (multi (xml/xml1-> episode :Director xml/text))}])

(defn- fetch-info
  "Fetches episode information for all seasons."
  [extract title imdb]
  (when-let [series (search-tvdb title imdb)]
    (when-let [info (fetch-tvdb (:seriesid series))]
      (loop [seasons  nil
             episodes (for [episode (xml/xml-> (zip/xml-zip info) :Episode)] (extract episode))]
        (if-let [ep (first episodes)]
          (recur (assoc-in seasons [:seasons (first ep) :episodes (second ep)] (prune (get ep 2)))
                 (next episodes))
          seasons)))))

(defn fetch-episode-thumb-urls
  "Fetches episode thumbnail URLs for all seasons."
  [title & [imdb]]
  (fetch-info thumbnail-info title imdb))

(defn fetch-episodes
  "Fetches episode information for all seasons."
  [title & [imdb]]
  (fetch-info episode-info title imdb))

(defn tvdb-season-metadata
  "Fetches episode information for a single season."
  ([title season]
   (tvdb-season-metadata title nil season))
  ([title imdb season]
   (get-in (fetch-episodes title imdb) [:seasons season])))

(defn tvdb-episode-metadata
  "Fetches metadata for a single episode."
  ([title season episode]
   (tvdb-episode-metadata title nil season episode))
  ([title imdb season episode]
   (get-in (tvdb-season-metadata title imdb season) [:episodes episode])))
