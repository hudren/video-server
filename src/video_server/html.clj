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

(defsnippet video-item "templates/video-item.html" [:div.video]
  [video]
  [:div.poster :a] (set-attr :href (str "video?id=" (:id video)))
  [:div.poster :img] (set-attr :src (or (:poster video) "placeholder.png"))
  [:span.title :a] (do-> (set-attr :href (str "video?id=" (:id video)))
                         (content (or (-> video :info :title) (:title video))))
  [:span.year] (when-let [year (-> video :info :year)] (content (str year)))
  [:span.rated] (when-let [rated (-> video :info :rated)] (content rated))
  [:span.duration] (when-let [runtime (-> video :info :runtime)] (content runtime))
  [:p.genres] (when-let [genres (-> video :info :genres)]
                (content (str "Genres: " (str/join ", " genres))))
  [:p.stars] (when-let [actors (or (-> video :info :stars) (-> video :info :actors))]
               (content (str "Starring: " (str/join ", " actors))))
  [:p.summary] (when-let [summary (video-desc video)] (content summary)))

(defsnippet info "templates/video-info.html" [:div#info]
  [video]
  [:span.year] (when-let [year (-> video :info :year)] (content (str year)))
  [:span.rated] (when-let [rated (-> video :info :rated)] (content rated))
  [:span.duration] (when-let [runtime (-> video :info :runtime)] (content runtime))
  [:p.plot] (content (-> video :info :plot))
  [:p.subjects] (when-let [subjects (-> video :info :subjects)]
                  (content (str "Subjects: " (str/join ", " subjects))))
  [:p.genres] (when-let [genres (or (-> video :info :netflix-genres) (-> video :info :genres))]
                (content (str "Genres: " (str/join ", " genres))))
  [:p.cast] (when-let [actors (or (-> video :info :actors) (-> video :info :stars))]
              (content (str "Starring: " (str/join ", " actors))))
  [:p.languages] (when-let [languages (-> video :info :languages)]
                   (when-not (= languages (list "English"))
                     (content (str "Languages: " (str/join ", " languages)))))
  [:ul :li] (clone-for [container (:containers video)]
                       [:li] (content (container-desc container))))

(deftemplate index-template "templates/index.html"
  [videos]
  [:#content] (content (map #(video-item %) videos)))

(deftemplate video-template "templates/video.html"
  [video play]
  [:head :title] (content (or (-> video :info :title) (:title video)))
  [:core-toolbar :div] (content (or (-> video :info :title) (:title video)))
  [:div#poster :img] (set-attr :src (or (:poster video) "placeholder.png"))
  [:div#info] (substitute (info video))
  [:video] (when play (do-> #_(set-attr :width (:width play))
                            #_(set-attr :height (:height play))
                            (set-attr :preload nil)
                            (append (html [:source {:src (:url play) :type "video/mp4"} nil])))))

(deftemplate downloads-template "templates/downloads.html"
  [host apk]
  [:span#url] (content (str host "/" apk)))

