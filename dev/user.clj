(ns user
  (:require [clojure.java.io :as io]
            [video-server.android :as android]
            [video-server.discovery :as discovery]
            [video-server.encoder :as encoder :refer :all]
            [video-server.ffmpeg :as ffmpeg]
            [video-server.file :as file :refer :all]
            [video-server.format :refer :all]
            [video-server.handler :as handler]
            [video-server.html :as html]
            [video-server.library :as library :refer :all]
            [video-server.main :as main]
            [video-server.metadata :as metadata :refer :all]
            [video-server.model :refer :all]
            [video-server.omdb :as omdb :refer :all]
            [video-server.process :as process :refer :all]
            [video-server.server :as server]
            [video-server.util :as util :refer :all]
            [video-server.video :as video :refer :all]
            [video-server.watcher :as watcher]))

(def port 8090)
(def hostname "Local Server")
(def encode true)
(def fetch true)
(def output-format :mkv)
(def output-size :720)

(def dir (main/default-folder))
(def url (main/host-url port))
(def folder (->Folder "videos" (io/file dir) (str url "/" "videos")))

(defn rescan []
  (watcher/scan-folder folder))

(defn video-for-title [title]
  (first (filter #(.contains (:title %) title) (library/current-videos))))

(defn start []
  (main/set-log-level (main/log-level "debug"))
  (binding [encoder/*fake-encode* true]
    (process/start-processing encode fetch output-format output-size))
  (watcher/start-watcher folder)
  (server/start-server url port handler/app folder)
  (discovery/start-discovery url main/discovery-port hostname))

