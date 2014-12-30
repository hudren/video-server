(ns video-server.html
  (:require [clojure.string :as str]
            [net.cgrand.enlive-html :refer :all]
            [video-server.format :refer [format-bitrate format-duration format-size video-dimension]])
  (:import (java.util Locale)))

(defn video-desc
  "Returns a description of the video technical details."
  [video]
  (->> [(str/join ", " (distinct (map :dimension (:containers video))))
        (when-let [season (:season video)] (str "Season " season))
        (when-let [episode (:episode video)] (str "Episode " episode))
        (:episode-title video)
        (let [lang (str/join ", " (distinct (map :language (:containers video) )))]
          (when-not (= (str lang) (.getDisplayLanguage (Locale/getDefault)))
            lang))]
       (remove str/blank?)
       (str/join " - ")))

(defn container-desc
  "Returns a description of the contents of the container."
  [container]
  (let [lang (:language container)]
    (->> [(:dimension container)
          (when-not (= lang (.getDisplayLanguage (Locale/getDefault)))
            (:language container))
          (:video container)
          (:audio container)
          (format-size (:size container))
          (format-bitrate (:bitrate container))]
         (remove str/blank?)
         (str/join " - "))))

(defn video-link
  "Returns a button for the specified link."
  [link title & [icon]]
  [:paper-button {:raised nil}
   [:core-icon {:icon (or icon "launch")}]
   [:a {:href link :target "_blank"} title]])

(defn video-links
  "Returns a sequence of links for the video."
  [video]
  (when-let [info (:info video)]
    (into [] [(when (:website info) (video-link (:website info) "Website" "home"))
              (when (:trailer info) (video-link (:trailer info) "Trailer" "theaters"))
              (when (:wikipedia info) (video-link (:wikipedia info) "Wikipedia"))
              (when (:imdb info) (video-link (str "http://www.imdb.com/title/" (:imdb info)) "IMDB"))
              (when (:netflix info) (video-link (str "http://dvd.netflix.com/Movie/" (:netflix info)) "Netflix"))])))

(defn combine
  "Combines multiple lists, returning a comma separated String."
  [& lists]
  (if (string? (first lists))
    (let [values (combine (rest lists))]
      (when values (str (first lists) ": " values)))
    (let [values (distinct (remove nil? (flatten lists)))]
      (when (seq values)
        (str/join ", " values)))))

(defn when-content
  [expr]
  #(when expr ((content expr) %)))

(defsnippet video-item "templates/video-item.html" [:div.video]
  [video]
  [:div.poster :a] (set-attr :href (str "video?id=" (:id video)))
  [:div.poster :img] (set-attr :src (or (:poster video) "placeholder.png"))
  [:span.title :a] (do-> (set-attr :href (str "video?id=" (:id video)))
                         (content (or (-> video :info :title) (:title video))))
  [:span.year] (when-let [year (-> video :info :year)] (content (str year)))
  [:span.rated] (when-let [rated (-> video :info :rated)] (content rated))
  [:span.duration] (when-let [runtime (-> video :info :runtime)] (content runtime))
  [:p.genres] (when-content (combine "Genres" (-> video :info :genres)))
  [:p.stars] (when-content (combine "Starring" (-> video :info :stars)))
  [:p.summary] (when-content (video-desc video)))

(defsnippet info "templates/video-info.html" [:div#info]
  [video]
  [:span.year] (when-let [year (-> video :info :year)] (content (str year)))
  [:span.rated] (when-let [rated (-> video :info :rated)] (content rated))
  [:span.duration] (when-let [runtime (-> video :info :runtime)] (content runtime))
  [:p.plot] (content (-> video :info :plot))
  [:p.subjects] (when-content (combine "Subjects" (-> video :info :subjects)))
  [:p.genres] (when-content (combine "Genres" (-> video :info :genres) (-> video :info :netflix-genres)))
  [:p.cast] (when-content (combine "Cast" (-> video :info :stars) (-> video :info :actors)))
  [:p.languages] (when-let [languages (-> video :info :languages)]
                   (when-not (= languages (list "English"))
                     (content (combine "Languages" languages))))
  [:ul :li] (clone-for [container (:containers video)]
                       [:li] (content (container-desc container))))

(deftemplate index-template "templates/index.html"
  [videos]
  [:#content] (content (map #(video-item %) videos)))

(deftemplate video-template "templates/video.html"
  [video play]
  [:head :title] (content (or (-> video :info :title) (:title video)))
  [:core-toolbar :div] (content (or (-> video :info :title) (:title video)))
  [:div#desc] (when (or (:year (:info video)) (:plot (:info video))) identity)
  [:div#poster :img] (set-attr :src (or (:poster video) "placeholder.png"))
  [:div#info] (substitute (info video))
  [:div#actions] (content (apply html (video-links video)))
  [:video] (when play (do-> #_(set-attr :width (:width play))
                            #_(set-attr :height (:height play))
                            (set-attr :preload nil)
                            (append (html [:source {:src (:url play) :type "video/mp4"} nil])))))

(deftemplate downloads-template "templates/downloads.html"
  [host apk]
  [:span#url] (content (str host "/" apk)))

