;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.html.title
  (:require [video-server.format :refer [format-date format-filetype format-runtime format-size lang-two-letter]]
            [video-server.library :refer [video-for-key]]
            [video-server.html.site :refer :all]
            [video-server.html.table :refer [condense-table html-table]]
            [video-server.title :refer [best-image episode-title full-title has-episodes? has-parts? has-seasons?
                                        season-titles]]
            [video-server.video :refer [quality web-playback?]])
  (:import (java.util Locale)))

(defn container-desc
  "Returns a description of the contents of the container."
  [container]
  (let [lang (:language container)]
    [(:dimension container)
     (if-not (= lang (.getDisplayLanguage (Locale/getDefault))) lang)
     (:video container)
     (:audio container)
     (format-size (:size container))
     [:a {:href (:url container) :download nil}
      (format-filetype (:filetype container))]]))

(defn video-link
  "Returns a button for the specified link."
  [link title & [img]]
  [:a {:href link :target "_blank"}
   [:button.mdl-button.mdl-js-button.mdl-button--raised.mdl-js-ripple-effect
    (icon (or img "open-in-new")) title]])

(defn video-links
  "Returns a sequence of links for the video."
  [info]
  (if (seq (select-keys info [:website :trailer :wikipedia :imdb-id :netflix-id]))
    (list
      (if (:website info) (video-link (:website info) "Website" "home"))
      (if (:trailer info) (video-link (:trailer info) "Trailer" "theaters"))
      (if (:wikipedia info) (video-link (:wikipedia info) "Wikipedia"))
      (if (:imdb-id info)
        (video-link (str "http://www.imdb.com/title/" (:imdb-id info)) "IMDb")
        (video-link (str "http://www.imdb.com/find?q=" (url-encode (:title info))) "IMDb" "search"))
      (if (:netflix-id info)
        (video-link (str "http://dvd.netflix.com/Movie/" (:netflix-id info)) "Netflix")
        (video-link (str "http://dvd.netflix.com/Search?v1=" (url-encode (:title info))) "Netflix" "search")))))

(defn- video-type
  "Conditionally promotes the video type for better web playability."
  [container]
  (if (and (web-playback? container) (= (:mimetype container) "video/x-matroska")) "video/webm" (:mimetype container)))

(defn video-tag
  "Returns the video tag for the specified video and containers."
  [video containers]
  (let [web (some #{"video/mp4" "video/webm" "video/ogg"} (map :mimetype containers))]
    [:video {:controls true :preload "metadata"}
     (for [container containers]
       [:source {:src (:url container) :type (if-not web (video-type container) (:mimetype container))}])
     (for [subtitle (filter #(= (:mimetype %) "text/vtt") (:subtitles video))]
       [:track (merge {:kind    "subtitles"
                       :label   (:title subtitle)
                       :src     (:url subtitle)
                       :srclang (lang-two-letter (:language subtitle))}
                      (when (or (:default subtitle) (:forced subtitle)) {:default nil}))])
     [:p "Your browser does not support HTML5 video."]]))

(defn title-url
  "Returns the URL for the given title."
  [title]
  (str "title?id=" (url-encode (:id title))))

(defn season-tabs
  "Returns the tabs for the given seasons."
  [url seasons selected]
  [:div.mdl-tabs.mdl-js-tabs.mdl-js-ripple-effect
   [:div.mdl-tabs__tab-bar
    (for [season seasons]
      [:a.mdl-tabs__tab (merge {:href (str url "&s=" (first season))}
                               (if (= (first season) selected) {:class "is-active"}))
       (second season)])]])

(defn episode-titles
  "Returns the episode number and title pairs for the given season."
  [title season]
  (if-let [episodes (seq (filter #(= (:season (second %)) season) (:videos title)))]
    (let [videos (map video-for-key episodes)]
      (sort-by first (map #(vector (:episode %)
                                   (or (when-let [et (episode-title title %)]
                                         (str (:episode %) ". " et))
                                       (str (if season "Episode " "Part ") (:episode %))))
                          videos)))))

(defn episode-list
  "Returns the episode list highlighting the selected one."
  [title season selected]
  [:ul
   (for [episode (episode-titles title season)]
     [:li (when (= (first episode) selected) {:class "selected"})
      [:a {:href (str (title-url title) (when season (str "&s=" season)) "&e=" (first episode))}
       (second episode)]])])

(defn title-desc [image info]
  [:div#desc.block.two-column
   [:div#poster.left-column
    [:img {:src (or image "placeholder.png") :alt "poster"}]]
   [:div#info.right-column info]])

(defn title-info [title info containers season]
  [:div
   [:p.plot (:plot info)]
   [:p (separate
         (if-let [year (:year info)] [:span.year year])
         (if-let [rated (:rated info)] [:span.rated rated])
         (if-let [runtime (when-not (= (:type info) "series") (:runtime info))] [:span.duration runtime]))]
   (if-content :p.subjects (combine "Subjects" (:subjects info)))
   (if-content :p.genres (combine "Genres" (:genres info) (:netflix-genres info)))
   (if-content :p.directors (combine "Directed by" (:directors info)))
   (if-content :p.cast (combine "Cast" (:stars info) (:actors info)))
   (if-content :p.languages (when-let [languages (:languages info)]
                              (when-not (= languages (list "English"))
                                (combine "Languages" languages))))
   (if-not (has-episodes? title season)
     [:table#containers.containers (-> (map container-desc containers) condense-table html-table)])])

(defn episode-info [info video containers]
  [:div
   [:p.overview (:plot info)]
   [:p (separate
         (if-let [date (:released info)] [:span.released (format-date date)])
         (if-let [runtime (:duration video)] [:span.duration (format-runtime runtime)]))]
   (if-content :p.writers (combine "Written by" (:writers info)))
   (if-content :p.directors (combine "Directed by" (:directors info)))
   [:table#episode-containers.containers (-> (map container-desc containers) condense-table html-table)]])

(defn title-template
  [title info video playable season episode]
  (let [containers (sort quality (:containers video))]
    (site-template
      {:title  (full-title title video)
       :style  "title.css"
       :script "title.js"
       :onload "hideVideo()"}
      (if (or (:year info) (:plot info))
        (title-desc (best-image :poster title season episode)
                    (title-info title info containers season)))
      [:div#links.block (video-links info)]
      (if (has-seasons? title)
        [:div#seasons.block (season-tabs (title-url title) (season-titles title) season)])
      [:div#season.block.two-columns {:class (if (or (has-episodes? title season) (has-parts? title)) "has-episodes")}
       [:div#episodes.left-column
        (when (has-episodes? title season)
          (episode-list title season episode))]
       [:div#episode.right-column
        (when (has-episodes? title season)
          (episode-info (get-in info [:seasons season :episodes episode]) video containers))]]
      (if (seq playable)
        [:div#player.block.js-hidden
         (video-tag video playable)]))))

