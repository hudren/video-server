(ns user
  (:require [clojure.java.io :as io]
            [net.cgrand.reload :refer [auto-reload]]
            [video-server.android :as android]
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
            [video-server.watcher :as watcher]))

(def port 8090)
(def hostname "Dev Server")
(def log-level "debug")
(def fake true)
(def encode true)
(def fetch true)
(def output-format :mkv)
(def output-size :720)

(def directory (main/default-folder))
(def options (main/read-options directory))
(def url (main/host-url port))
(def folder (->Folder "videos" (io/file directory) (str url "/" "videos") options))

(defn rescan []
  (watcher/scan-folder folder))

(defn video-for-title [title]
  (first (filter #(.contains (:title %) title) (library/current-videos))))

(defn title-for-title [title]
  (title-for-id (:title (video-key title))))

(defn fetch-film [title]
  (fetch-metadata (map->Video {:title title})))

(defn fetch-tv [title]
  (fetch-metadata (map->Video {:title title :season 1 :episode 1})))

(defn save-netflix [title netflix-id]
  (when-let [title (title-for-title title)]
    (as-> title x
      (read-metadata folder title)
      (assoc x :netflix-id (str netflix-id))
      (save-metadata folder title x))))

(defn fix-match [title imdb-id & [series?]]
  (when-let [title (title-for-title title)]
    (let [matches (if series? (query-tv (:title title)) (query-film (:title title)))]
      (when-let [fb (first (filter #(= (get-imdb-id %) imdb-id) (map (comp get-topic :mid) matches)))]
        (let [info (fetch-metadata title fb)]
          (save-metadata folder title info)
          (when-let [poster (:poster info)]
            (save-poster folder title poster)))))))

(defn start []
  (main/set-log-level (main/log-level log-level))
  (binding [encoder/*fake-encode* (and fake (nil? (:encode options)))]
    (process/start-encoding))
  (process/start-processing encode fetch output-format output-size)
  (watcher/start-watcher folder)
  (server/start-server url port (handler/app url) folder)
  (discovery/start-discovery url main/discovery-port hostname))

(auto-reload (find-ns 'video-server.html))

