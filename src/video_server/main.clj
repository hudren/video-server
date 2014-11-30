;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.main
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [video-server.discovery :refer [start-discovery]]
            [video-server.handler :refer [app]]
            [video-server.model :refer [->Folder]]
            [video-server.process :refer [start-processing]]
            [video-server.server :refer [start-server]]
            [video-server.watcher :refer [start-watcher]])
  (:import (ch.qos.logback.classic Level Logger)
           (java.net InetAddress)
           (org.slf4j LoggerFactory))
  (:gen-class))

(def discovery-port 8394)

(defn default-folder []
  (str (System/getProperty "user.home") "/Movies"))

(defn host-url
  "Returns a url for this web server based on the IP address and web port."
  [port]
  (let [addr (.getAddress (InetAddress/getLocalHost))
        quads (mapv (partial bit-and 0xFF) addr)]
    (apply format "http://%d.%d.%d.%d:%d" (conj quads port))))

(defn log-level
  "Returns the Logback level from the string."
  [level]
  (case (str/lower-case level)
    "all" Level/ALL
    "trace" Level/TRACE
    "debug" Level/DEBUG
    "info" Level/INFO
    "warn" Level/WARN
    "error" Level/ERROR
    "off" Level/OFF
    nil))

(defn set-log-level
  "Sets the root logging level."
  [level]
  (when level (.setLevel ^Logger (LoggerFactory/getLogger Logger/ROOT_LOGGER_NAME) level)))

(def cli-options
  [["-p" "--port PORT" "HTTP Port"
    :default 8090
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-e" "--encode BOOLEAN" "Automatically transcode videos and subtitles"
    :default "true"
    :parse-fn #(Boolean/parseBoolean %)]
   ["-f" "--format EXT" "Output format: mkv, mp4, m4v"
    :default "mkv"
    :parse-fn str/lower-case
    :validate [#{"mkv" "mp4" "m4v"} "The output format must be one of mkv, mp4 or m4v"]]
   ["-s" "--size INT" "Preferred video size when transcoding"
    :default 720
    :parse-fn #(Integer/parseInt %)
    :validate [#{480 720 1080} "The size must be 480, 720 or 1080"]]
   ["-l" "--log-level LEVEL" "Override the default logging level"
    :parse-fn log-level
    :validate [identity "The log level must be one of ALL, TRACE, DEBUG, INFO, WARN, ERROR or OFF"]]
   ["-h" "--help"]])

(defn usage
  "Returns a console formatted usage message."
  [summary]
  (str/join \newline
            ["Usage: video-server [options] [folder]" ""
             (str "The default folder is " (default-folder)) ""
             "Options:" summary]))

(defn exit
  "Exits the program, optionally displaying a message."
  [code & [msg]]
  (when msg (println msg))
  (System/exit code))

(defn -main
  "Starts the video server."
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)
        dir (or (first arguments) (default-folder))
        url (host-url (:port options))
        folder (->Folder "video" (io/file dir) (str url "/" "video"))
        fmt (keyword (:format options))
        size (-> (:size options) str keyword)]
    (cond
      (:help options) (exit 0 (usage summary))
      (> (count arguments) 1) (exit 1 "Only one video folder is allowed.")
      errors (exit 1 (str/join \newline errors)))
    (set-log-level (:log-level options))
    (start-processing (:encode options) fmt size)
    (start-watcher folder)
    (let [server (start-server url folder (:port options) app)]
      (start-discovery url discovery-port)
      (.join server))))

