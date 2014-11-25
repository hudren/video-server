(ns video-server.html
  (:require [clojure.string :as str]
            [net.cgrand.enlive-html :as html]
            [video-server.format :as format])
  (:import (java.util Locale)))

(defn quality
  "Returns a human-readable video quality description."
  [{:keys [width height]}]
  (format/video-dimension width height))

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
          (format/format-size (:size container))
          (format/format-bitrate (:bitrate container))]
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

(html/defsnippet video-template "templates/video.html" [:div]
  [video]
  [:h2] (html/content (:title video))
  [:div :> :p] (html/content (format/format-duration (:duration video)))
  [:ul :li] (html/clone-for [container (:containers video)]
                            [:p :span] (html/content (container-desc container))
                            [:a] (html/set-attr :href (:url container))
                            [:a] (html/set-attr :type (:mimetype container))
                            [:a] (download-attr container)
                            [:a] (html/content (download-link container))))

(html/deftemplate main-template "templates/videos.html"
  [videos]
  [:div] (html/clone-for [video videos]
                         [:div] (html/content (video-template video))))

