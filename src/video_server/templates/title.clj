(ns video-server.templates.title
  (:require [clojure.string :as str]
            [video-server.format :refer [format-date format-filetype format-runtime format-size lang-two-letter]]
            [video-server.library :refer [video-for-key]]
            [video-server.templates.site :refer :all]
            [video-server.title :refer [best-image episode-title full-title has-episodes? has-parts? has-seasons?
                                        season-titles]]
            [video-server.video :refer [web-playback?]])
  (:import (java.net URLEncoder)
           (java.util Locale)))

(defn container-desc
  "Returns a description of the contents of the container."
  [container]
  (let [lang (:language container)]
    [:tr (->> [(:dimension container)
               (when-not (= lang (.getDisplayLanguage (Locale/getDefault))) lang)
               (:video container)
               (:audio container)
               (format-size (:size container))
               [:a {:href (:url container) :download nil}
                (format-filetype (:filetype container))]]
              (remove #(if (string? %) (str/blank? %)))
              (map #(vector :td %)))]))

(defn video-link
  "Returns a button for the specified link."
  [link title & [icon]]
  [:a {:href link :target "_blank"}
   [:paper-button {:raised "raised"}
    [:div [:iron-icon {:icon (or icon "open-in-new")}] title]]])

(defn video-links
  "Returns a sequence of links for the video."
  [info]
  (when (seq (select-keys info [:website :trailer :wikipedia :imdb-id :netflix-id]))
    (list
      (when (:website info) (video-link (:website info) "Website" "home"))
      (when (:trailer info) (video-link (:trailer info) "Trailer" "theaters"))
      (when (:wikipedia info) (video-link (:wikipedia info) "Wikipedia"))
      (if (:imdb-id info)
        (video-link (str "http://www.imdb.com/title/" (:imdb-id info)) "IMDb")
        (video-link (str "http://www.imdb.com/find?q=" (URLEncoder/encode (:title info) "UTF-8")) "IMDb" "search"))
      (if (:netflix-id info)
        (video-link (str "http://dvd.netflix.com/Movie/" (:netflix-id info)) "Netflix")
        (video-link (str "http://dvd.netflix.com/Search?v1=" (URLEncoder/encode (:title info) "UTF-8")) "Netflix" "search")))))

(defn- video-type
  "Conditionally promotes the video type for better web playability."
  [container]
  (if (and (web-playback? container) (= (:mimetype container) "video/x-matroska")) "video/mp4" (:mimetype container)))

(defn video-tag
  "Returns the video tag for the specified video and containers."
  [video containers]
  (let [web (some #{"video/mp4" "video/webm" "video/ogg"} (map :mimetype containers))]
    [:video {:controls "" :preload "metadata"}
     (for [container containers]
       [:source {:src (:url container) :type (if-not web (video-type container) (:mimetype container))}])
     (for [subtitle (filter #(= (:mimetype %) "text/vtt") (:subtitles video))]
       [:track (merge {:kind    "subtitles"
                       :label   (:title subtitle)
                       :src     (:url subtitle)
                       :srclang (lang-two-letter (:language subtitle))}
                      (when (or (:default subtitle) (:forced subtitle)) {:default nil}))])]))

(defn title-url
  "Returns the URL for the given title."
  [title]
  (str "title?id=" (URLEncoder/encode (:id title) "UTF-8")))

(defn- selected-index
  "Returns the tab index of the selected season."
  [seasons selected]
  (ffirst (filter #(= (-> % second first) selected) (map-indexed vector seasons))))

(defn season-tabs
  "Returns the tabs for the given seasons."
  [url seasons selected]
  [:paper-tabs {:selected (selected-index seasons selected)}
   (for [season seasons]
     [:paper-tab {:link nil}
      [:a {:href (str url "&s=" (first season)) :class "horizontal center-center layout"}
       (second season)]])])

(defn episode-titles
  "Returns the episode number and title pairs for the given season."
  [title season]
  (when-let [episodes (seq (filter #(= (:season (second %)) season) (:videos title)))]
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
   [:p
    (when-let [year (:year info)] [:span.year year])
    (when-let [rated (:rated info)] [:span.rated rated])
    (when-let [runtime (when-not (= (:type info) "series") (:runtime info))] [:span.duration runtime])]
   (when-content :p.subjects (combine "Subjects" (:subjects info)))
   (when-content :p.genres (combine "Genres" (:genres info) (:netflix-genres info)))
   (when-content :p.directors (combine "Directed by" (:directors info)))
   (when-content :p.cast (combine "Cast" (:stars info) (:actors info)))
   (when-content :p.languages (when-let [languages (:languages info)]
                                (when-not (= languages (list "English"))
                                  (combine "Languages" languages))))
   (when-not (has-episodes? title season)
     [:table#containers.containers
      [:tbody (for [container containers] (container-desc container))]])])

(defn episode-info [info video containers]
  [:div
   [:p.overview (:plot info)]
   [:p
    (when-let [date (:released info)] [:p.released (format-date date)])
    (when-let [runtime (:duration video)] [:p.duration (format-runtime runtime)])
    (when-content :p.writers (combine "Written by" (:writers info)))
    (when-content :p.directors (combine "Directed by" (:directors info)))
    [:table#episode-containers.containers
     [:tbody (for [container containers] (container-desc container))]]]])

(defn title-template
  [title info video containers season episode exclude]
  (site-template
    {:title (full-title title video)
     :style "title.css"}
    (when (or (:year info) (:plot info))
      (title-desc (best-image :poster title season episode)
                  (title-info title info containers season)))
    [:div#links.block (video-links info)]
    (when (has-seasons? title)
      [:div#seasons.block (season-tabs (title-url title) (season-titles title) season)])
    [:div#season.block.two-columns {:class (if (or (has-episodes? title season) (has-parts? title)) :has-episodes)}
     [:div#episodes.left-column
      (when (has-episodes? title season)
        (episode-list title season episode))]
     [:div#episode.right-column
      (when (has-episodes? title season)
        (episode-info (get-in info [:seasons season :episodes episode]) video containers))]]
    (when-let [containers (seq (remove #(get exclude (:filetype %)) containers))]
      [:div#player.block
       (video-tag video containers)])))

