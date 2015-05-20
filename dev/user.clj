(ns user
  (:require [clojure.edn :as edn]
            [clojure.data.json :as json]
            [clojure.java.browse :refer [browse-url]]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [net.cgrand.reload :refer [auto-reload]]
            [video-server.android :as android]
            [video-server.directory :as directory]
            [video-server.discovery :as discovery]
            [video-server.encoder :as encoder :refer :all]
            [video-server.ffmpeg :as ffmpeg]
            [video-server.file :as file :refer :all]
            [video-server.format :refer :all]
            [video-server.freebase :as freebase :refer :all]
            [video-server.handler :as handler]
            [video-server.html :as html]
            [video-server.library :as library :refer :all]
            [video-server.main :as main]
            [video-server.metadata :as metadata :refer :all]
            [video-server.model :refer :all]
            [video-server.omdb :as omdb :refer :all]
            [video-server.process :as process :refer :all]
            [video-server.server :as server]
            [video-server.title :as title :refer :all]
            [video-server.tvdb :as tvdb :refer :all]
            [video-server.util :as util :refer :all]
            [video-server.video :as video :refer :all]
            [video-server.watcher :as watcher])
  (:import (java.net InetAddress)))

(def hostname (.getHostName (InetAddress/getLocalHost)))
(def port 8090)
(def output-format "mkv")
(def output-size 720)

(def log-level "debug")
(def fake true)
(def encode true)
(def fetch true)

(def args [])
(def options {:name hostname :port port :fake fake :encode encode :fetch fetch :format output-format :size output-size})

(defn video-for-title [title]
  (first (filter #(.contains ^String (:title %) title) (library/current-videos))))

(defn title-for-title [title]
  (title-for-id (:title (video-key title))))

(defn folder-for-title [title]
  (when-let [title (title-for-title title)]
    (let [videos (:videos title)]
      (if (= (count videos) 1)
        (ffirst videos)))))

(defn video-for-title [title]
  (when-let [title (title-for-title title)]
    (let [videos (:videos title)]
      (if (= (count videos) 1)
        (apply video-for-key (first videos))))))

(defn folder-video [title]
  (when-let [title (title-for-title title)]
    (let [videos (:videos title)]
      (if (= (count videos) 1)
        [(-> title :videos ffirst) (apply video-for-key (first videos))]))))

(defn info [title]
  (when-let [title (title-for-title title)]
    (when-let [folder (folder-for-title title)]
      (when-let [video (video-for-title title)]
        (let [container (container-to-encode (:containers video) (keyword (str output-size)))]
          (ffmpeg/video-info (io/file (:file folder) (:path container))))))))

(defn field [track label & ks]
  (when-let [v (get-in track ks)]
    (str label \= v)))

(defn fields [track & xs]
  (map #(apply field track %) xs))

(defn pad [s l]
  (apply str s (repeat (max 0 (- l (count s))) \space)))

(defmulti track-fields :codec_type)

(defmethod track-fields "video" [track]
  (fields track ["codec" :codec_name] ["sar" :sample_aspect_ratio] ["dar" :display_aspect_ratio]))

(defmethod track-fields "audio" [track]
  (fields track ["codec" :codec_name] ["default" :disposition :default] ["lang" :tags :language]))

(defmethod track-fields "subtitle" [track]
  (fields track ["codec" :codec_name] ["default" :disposition :default] ["forced" :disposition :forced] ["lang" :tags :language]))

(defmethod track-fields :default [track]
  (fields track ["codec" :codec_long_name]))

(defn tracks [title]
  (doseq [track (:streams (info title))]
    (apply println (str "Track #" (:index track) " " (pad (:codec_type track) 10)) (track-fields track))))

(defn fetch-film [title]
  (fetch-metadata (map->Video {:title title})))

(defn fetch-tv [title]
  (fetch-metadata (map->Video {:title title :season 1 :episode 1})))

(defn save-netflix [title netflix-id]
  (when-let [title (title-for-title title)]
    (as-> title x
      (read-metadata title)
      (assoc x :netflix-id (str netflix-id))
      (save-metadata title x))))

(defn fix-match [title imdb-id & [series?]]
  (when-let [title (title-for-title title)]
    (let [matches (if series? (query-tv (:title title)) (query-film (:title title)))]
      (let [fb (first (filter #(= (get-imdb-id %) imdb-id) (map (comp get-topic :mid) matches)))
            info (fetch-metadata title fb imdb-id)]
        (save-metadata title info)
        (when-let [poster (:poster info)]
          (save-poster title poster))))))

(defn match [title & [series? year duration]]
  (when-let [title (title-for-title title)]
    (let [fb (freebase-metadata (:title title) series? year duration)
          info (fetch-metadata title fb)]
      (save-metadata title info)
      (when-let [poster (:poster info)]
        (save-poster title poster)))))

(defn unmatch [title & {:as info}]
  (when-let [title (title-for-title title)]
    (save-metadata title (merge (select-keys title [:title]) info))
    #_(when-let [image (poster-for-title title)]
      (io/delete-file image))))

(defn update-file
  "Processes a file, preserving it's modification time."
  [file f & [output]]
  (let [modified (.lastModified file)]
    (f file)
    (.setLastModified (or output file) modified)))

(defn set-subtitle [title track]
  (when-let [title (title-for-title title)]
    (when-let [folder (folder-for-title title)]
      (when-let [video (video-for-title title)]
        (let [container (container-to-encode (:containers video) (keyword (str output-size)))]
          (update-file (io/file (:file folder) (:path container)) #(set-default-subtitles % track)))))))

(defn clear-subtitle [title]
  (when-let [title (title-for-title title)]
    (when-let [folder (folder-for-title title)]
      (when-let [video (video-for-title title)]
        (let [container (container-to-encode (:containers video) (keyword (str output-size)))]
          (update-file (io/file (:file folder) (:path container)) clear-subtitles))))))

(defn start []
  (main/set-log-level (main/log-level log-level))
  (binding [encoder/*fake-encode* (and fake (nil? (:encode options)))]
    (main/start args options))
  (auto-reload (find-ns 'video-server.html)))

(defn encode-title [title]
  (when-let [[folder video] (folder-video title)]
    (let [fmt (keyword output-format)
          size (encode-size video (keyword (str output-size)))]
      (when-let [spec (video-encode-spec folder video fmt size)]
        (encode-video spec)))))

(defn open
  "Opens the title in the system browser."
  ([] (browse-url (str "http://localhost:" port)))
  ([title]
   (when-let [title (title-for-title title)]
     (when title (browse-url (str "http://localhost:" port "/" (html/title-url title)))))))

