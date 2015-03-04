(ns user
  (:require [clojure.edn :as edn]
            [clojure.data.json :as json]
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

(defn start []
  (main/set-log-level (main/log-level log-level))
  (binding [encoder/*fake-encode* (and fake (nil? (:encode options)))]
    (main/start options))
  (auto-reload (find-ns 'video-server.html)))

