(ns video-server.html
  (:require [clojure.string :as str]
            [net.cgrand.enlive-html :refer :all]
            [video-server.format :refer [format-bitrate format-duration format-size video-dimension]])
  (:import (java.util Locale)))

(defn quality
  "Returns a human-readable video quality description."
  [{:keys [width height]}]
  (video-dimension width height))

(defn can-play?
  "Returns whether the container is web playable."
  [container]
  (and (.contains (:video container) "H.264")
       (.contains (:audio container) "AAC")))

(defn can-play-from-link?
  "Returns whether Chrome will play the movie by clicking on the
  link."
  [container]
  (and (can-play? container)
       (.contains (:mimetype container) "matroska")))

(defn container-desc
  "Returns a description of the contents of the container."
  [container]
  (let [lang (:language container)]
    (->> [(quality container)
          (when-not (= lang (.getDisplayLanguage (Locale/getDefault)))
            (:language container))
          (:video container)
          (:audio container)
          (format-size (:size container))
          (format-bitrate (:bitrate container))]
         (remove str/blank?)
         (str/join " - "))))

(defn download-attr
  "Returns a function that sets the anchor download attribute if the
  video cannot be played by clicking the file link."
  [container]
  #(if (can-play-from-link? container) %
     (assoc % :attrs (assoc (:attrs % {}) :download nil))))

(defn download-link
  "Returns the link text for playing or downloading the video file."
  [container]
  (if (can-play-from-link? container) "Play" "Download"))

(defsnippet video-template "templates/video.html" [:div.video]
  [video]
  [:div.poster :img] (set-attr :src (or (:poster video) "placeholder.png"))
  [:span.title] (content (or (-> video :info :title) (:title video)))
  [:span.year] (content (str (-> video :info :year)))
  [:span.rated] (when-let [rated (-> video :info :rated)]
                  (content rated))
  [:span.duration] (content (-> video :info :runtime))
  [:p.genres] (when-let [genres (-> video :info :genres)]
                (content (str "Genres: " (str/join ", " genres))))
  [:p.stars] (when-let [actors (or (-> video :info :stars) (-> video :info :actors))]
               (content (str "Starring: " (str/join ", " actors))))
  [:ul :li] (clone-for [container (:containers video)]
                       [:p :span] (content (container-desc container))
                       [:a] (set-attr :href (:url container))
                       [:a] (set-attr :type (:mimetype container))
                       [:a] (download-attr container)
                       [:a] (content (download-link container))))

(deftemplate index-template "public/index.html"
  [videos]
  [:div.content] (clone-for [video videos]
                            [:div] (content (video-template video))))

