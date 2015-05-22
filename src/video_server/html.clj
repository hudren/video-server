;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.html
  (:require [clojure.string :as str]
            [net.cgrand.enlive-html :refer :all]
            [video-server.format :refer [format-date format-filetype format-runtime format-size lang-two-letter]]
            [video-server.library :refer [video-for-key]]
            [video-server.title :refer [best-containers best-image best-video episode-title full-title has-episodes? has-parts?
                                        has-seasons? season-desc season-titles]]
            [video-server.video :refer [rank-containers web-playback?]])
  (:import (java.net URLEncoder)
           (java.util Locale)))

(defn video-desc
  "Returns a description of the video technical details."
  [video]
  (html (->> [(str/join ", " (distinct (map :dimension (:containers video))))
              (when-let [season (:season video)] (str "Season " season))
              (when-let [episode (:episode video)] (str "Episode " episode))
              (:episode-title video)
              (let [lang (str/join ", " (distinct (map :language (:containers video))))]
                (when-not (= lang (.getDisplayLanguage (Locale/getDefault))) lang))]
             (remove str/blank?)
             (map #(vector :span %)))))

(defn container-desc
  "Returns a description of the contents of the container."
  [container]
  (let [lang (:language container)]
    (html [:tr (->> [(:dimension container)
                     (when-not (= lang (.getDisplayLanguage (Locale/getDefault))) lang)
                     (:video container)
                     (:audio container)
                     (format-size (:size container))
                     [:a {:href (:url container) :download nil}
                      (format-filetype (:filetype container))]]
                    (remove #(if (string? %) (str/blank? %)))
                    (map #(vector :td %)))])))

(defn video-link
  "Returns a button for the specified link."
  [link title & [icon]]
  [:paper-button {:raised nil}
   [:core-icon {:icon (or icon "open-in-new")}]
   [:a {:href link :target "_blank"} title]])

(defn video-links
  "Returns a sequence of links for the video."
  [info]
  (when (seq (select-keys info [:website :trailer :wikipedia :imdb-id :netflix-id]))
    (vector
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
    (html [:video {:controls nil :preload "metadata"}
           (for [container containers]
             [:source {:src (:url container) :type (if-not web (video-type container) (:mimetype container))}])
           (for [subtitle (filter #(= (:mimetype %) "text/vtt") (:subtitles video))]
             [:track (merge {:kind "subtitles"
                             :label (:title subtitle)
                             :src (:url subtitle)
                             :srclang (lang-two-letter (:language subtitle))}
                            (when (or (:default subtitle) (:forced subtitle)) {:default nil}))])])))

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
  (html [:paper-tabs {:link nil :selected (selected-index seasons selected)}
         (for [season seasons]
           [:paper-tab
            [:a {:href (str url "&s=" (first season)) :horizontal nil :center-center nil :layout nil}
             (second season)]])]))

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
  (html [:ul
         (for [episode (episode-titles title season)]
           [:li (when (= (first episode) selected) {:class "selected"})
            [:a {:href (str (title-url title) (when season (str "&s=" season)) "&e=" (first episode))}
             (second episode)]])]))

(defn combine
  "Combines multiple lists, returning a comma separated String."
  [& lists]
  (if (string? (first lists))
    (let [values (combine (rest lists))]
      (when values (html [:span.label (str (first lists) ":")] (str " " values))))
    (let [values (distinct (remove nil? (flatten lists)))]
      (when (seq values)
        (str/join ", " values)))))

(defn when-content
  "Replaces the content or removes the element."
  [expr & value]
  #(when expr ((content (or value expr)) %)))

(defn if-add-class
  "Conditionally adds a class."
  [pred class]
  #(if pred ((add-class (name class)) %) %))

(defn if-add-class-or-remove
  "Conditionally adds a class."
  [pred class]
  #(if pred ((add-class (name class)) %)))

;;; Title listing

(defn title-item
  [title]
  (let [info (:info title)
        url (title-url title)]
    [:div.video
     [:div.poster.small
      [:a {:href url :target "_blank"}
       [:img {:src (or (best-image :poster title) "placeholder.png") :alt "poster"}]]]
     [:div.desc
      [:p
       [:span.title
        [:a {:href url :target "_blank"} (or (:title info) (:title title))]]
       [:span.info
        (when-let [year (:year info)] [:span.year (str year)])
        (when-let [rated (:rated info)] [:span.rated rated])
        (when-let [runtime (if (has-seasons? title) (season-desc title) (:runtime info))]
          [:span.duration runtime])]]
      (when-let [genres (combine "Genres" (:genres info))] [:p.genres genres])
      (when-let [stars (combine "Starring" (:stars info))] [:p.stars stars])]]))

(deftemplate titles-template "templates/titles.html"
  [titles]
  [:#content] (content (html (map title-item titles))))

(defsnippet no-titles "templates/no-titles.html" [:div]
  [folders]
  [:ul] (content (html (map #(vector :ul %) folders))))

(deftemplate no-titles-template "templates/titles.html"
  [dirs]
  [:#content] (content (no-titles dirs)))

(defn titles-page
  [titles dirs]
  (if (seq titles) (titles-template titles) (no-titles-template dirs)))

;;; Title page

(defsnippet title-info "templates/info.html" [:div#info]
  [title info containers season]
  [:span.year] (when-let [year (:year info)] (content (str year)))
  [:span.rated] (when-let [rated (:rated info)] (content rated))
  [:span.duration] (when-let [runtime (when-not (= (:type info) "series") (:runtime info))] (content runtime))
  [:p.plot] (content (:plot info))
  [:p.subjects] (when-content (combine "Subjects" (:subjects info)))
  [:p.genres] (when-content (combine "Genres" (:genres info) (:netflix-genres info)))
  [:p.directors] (when-content (combine "Directed by" (:directors info)))
  [:p.cast] (when-content (combine "Cast" (:stars info) (:actors info)))
  [:p.languages] (when-let [languages (:languages info)]
                   (when-not (= languages (list "English"))
                     (content (combine "Languages" languages))))
  [:table#containers :tbody] (when-not (has-episodes? title season)
                               (content (for [container containers] (container-desc container)))))

(defsnippet episode-info "templates/episode.html" [:div#episode]
  [info video containers]
  [:p.overview] (content (:plot info))
  [:span.released] (when-let [date (:released info)] (content (format-date date)))
  [:span.duration] (when-let [runtime (:duration video)] (content (format-runtime runtime)))
  [:p.writers] (when-content (combine "Written by" (:writers info)))
  [:p.directors] (when-content (combine "Directed by" (:directors info)))
  [:table#episode-containers :tbody] (content (for [container containers] (container-desc container))))

(deftemplate title-template "templates/title.html"
  [title info video containers season episode]
  [:head :title] (content (full-title title video))
  [:core-toolbar :div] (content (full-title title video))
  [:div#desc] (when (or (:year info) (:plot info)) identity)
  [:div#poster :img] (set-attr :src (or (best-image :poster title season episode) "placeholder.png"))
  [:div#info] (substitute (title-info title info (rank-containers video) season))
  [:div#links] (content (apply html (video-links info)))
  [:div#seasons] (when (has-seasons? title)
                   (content (season-tabs (title-url title) (season-titles title) season)))
  [:div#season] (if-add-class-or-remove (or (has-episodes? title season) (has-parts? title)) :has-episodes)
  [:div#episodes] (when (has-episodes? title season)
                    (content (episode-list title season episode)))
  [:div#episode] (when (has-episodes? title season)
                   (substitute (episode-info (get-in info [:seasons season :episodes episode]) video containers)))
  [:video] (when (seq containers) (substitute (video-tag video containers))))

(defn title-page
  "Returns the page displaying the title w/episodes and parts."
  [title season episode]
  (let [season (or season (ffirst (season-titles title)))
        episode (or episode (ffirst (episode-titles title season)))
        video (video-for-key (best-video (:videos title) season episode))
        containers (best-containers video)]
    (title-template title (:info title) video containers season episode)))

;;; Downloads page

(deftemplate downloads-template "templates/downloads.html"
  [host apk]
  [:span#url] (content (str host "/" apk)))

