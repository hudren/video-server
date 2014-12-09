(ns user
  (:require [clojure.java.io :as io]
            [video-server.discovery :as discovery]
            [video-server.handler :as handler]
            [video-server.main :as main]
            [video-server.model :refer :all]
            [video-server.encoder :as encoder]
            [video-server.file :as file]
            [video-server.html :as html]
            [video-server.library :as library]
            [video-server.process :as process]
            [video-server.video :as video]
            [video-server.server :as server]
            [video-server.watcher :as watcher]))

(def port 8090)
(def encode true)
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
    (process/start-processing encode output-format output-size))
  (watcher/start-watcher folder)
  (server/start-server url port handler/app folder)
  (discovery/start-discovery url main/discovery-port))

