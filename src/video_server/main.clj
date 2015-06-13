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
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [trptcolin.versioneer.core :as version]
            [video-server.discovery :refer [start-discovery]]
            [video-server.encoder :refer [installed?]]
            [video-server.handler :refer [app]]
            [video-server.model :refer [->Folder]]
            [video-server.process :refer [start-processing]]
            [video-server.server :refer [start-server]]
            [video-server.watcher :refer [start-watcher]])
  (:import (ch.qos.logback.classic Level Logger)
           (java.io PushbackReader)
           (java.net InetAddress)
           (org.slf4j LoggerFactory))
  (:gen-class))

(def ^:const discovery-port 8394)

(defn exists?
  "Returns the canonical path if the directory exists."
  [home dir]
  (let [path (io/file home dir)]
    (when (.isDirectory path) (.getCanonicalPath path))))

(defn default-folder
  "Returns the default folder from which to serve videos by searching
  for commonly named directories in the user home directory."
  []
  (let [home (System/getProperty "user.home")]
    (or (some (partial exists? home) ["Videos" "Movies" "My Videos"])
        (.getCanonicalPath (io/file home "Videos")))))

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
   ["-n" "--name NAME" "Server name"
    :default (.getHostName (InetAddress/getLocalHost))]
   [nil "--encode BOOL" "Automatically transcode videos and subtitles"
    :default "true"
    :parse-fn #(Boolean/parseBoolean %)]
   [nil "--fetch BOOL" "Automatically retreive metadata from the Internet"
    :default "true"
    :parse-fn #(Boolean/parseBoolean %)]
   ["-f" "--format EXT" "Output format: mkv, mp4, m4v"
    :default "mkv"
    :parse-fn str/lower-case
    :validate [#{"mkv" "mp4" "m4v"} "The output format must be one of mkv, mp4 or m4v"]]
   ["-s" "--size INT" "Preferred video size when transcoding"
    :default 1080
    :parse-fn #(Integer/parseInt %)
    :validate [#{480 720 1080 2160} "The size must be 480, 720, 1080 or 2160"]]
   [nil "--log-level LEVEL" "Override the default logging level"
    :parse-fn log-level
    :validate [identity "The log level must be one of ALL, TRACE, DEBUG, INFO, WARN, ERROR or OFF"]]
   ["-v" "--version"]
   ["-h" "--help"]])

(defn version []
  (str "Videos@Home " (version/get-version "com.hudren.homevideo" "video-server")))

(defn usage
  "Returns a console formatted usage message."
  [summary]
  (str/join \newline
            [(version)
             "Usage: video-server [options] [folder...]" ""
             (str "The default folder is " (default-folder)) ""
             "Options:" summary]))

(defn exit
  "Exits the program, optionally displaying a message."
  [code & [msg]]
  (when msg (println msg))
  (System/exit code))

(defn read-edn
  "Reads EDN data from a file."
  [file]
  (when (.isFile file)
    (with-open [rdr (PushbackReader. (io/reader file))]
      (edn/read rdr))))

(defn read-options
  "Reads folder-specific options."
  [dir]
  (read-edn (io/file dir "options.edn")))

(defn unique-name
  "Returns a unique name that does not exist in the set of names."
  [name names]
  (loop [base (-> name str/lower-case (str/replace " " "")) index 2]
    (if (contains? names base)
      (recur (str name index) (inc index))
      base)))

(defn make-folders
  "Returns Folders corresponding to the directories."
  [dirs url]
  (loop [dirs dirs folders [] names #{}]
    (if-let [dir (first dirs)]
      (let [file (io/file (if (coll? dir) (first dir) dir))]
        (if (.isDirectory file)
          (let [options (if (coll? dir) (second dir) {})
                name (unique-name (.getName file) names)
                folder (->Folder name file (str url "/videos/" name) (merge options (read-options file)))]
            (recur (rest dirs) (conj folders folder) (conj names name)))
          (recur (rest dirs) folders names)))
      folders)))

(defn process-settings
  "Processes folders and options from the file or command line."
  [file dirs options url]
  (if (.isFile file)
    (let [settings (read-edn file)
          options (merge options (dissoc settings :folders))]
      [(make-folders (:folders settings) url) options])
    [(make-folders dirs url) options]))

(defn process-args
  "Processes the arguments looking for a settings file and/or folders
  to serve. The settings file may override options provided on the
  command line and provide folder-specific options."
  [args options url]
  (let [file (io/file (or (first args) "settings.edn"))
        dirs (if (and file (.exists file))
               (or (next args) (list (default-folder)))
               (if (seq args) args (list (default-folder))))
        dirs (filter #(.exists (io/file %)) dirs)]
    (process-settings file dirs options url)))

(defn start
  "Starts all of the components, returning the Jetty web server."
  [args options]
  (let [fmt (-> options :format keyword)
        size (-> options :size str keyword)
        url (host-url (:port options))
        [folders options] (process-args args options url)]
    (when-not (seq folders)
      (exit 2 (str "Specified video folder(s) were not found: " (str/join ", " args))))
    (start-processing (:encode options) (:fetch options) fmt size)
    (start-watcher folders)
    (try (let [server (start-server url (:port options) (app url folders) folders)]
           (start-discovery url discovery-port (:name options))
           server)
         (catch Exception e (exit 4 (str "Cannot start server: " (.getMessage e)))))))

(defn -main
  "Parses the command line options and starts the video server."
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:version options) (exit 0 (version))
      (:help options) (exit 0 (usage summary))
      errors (exit 1 (str/join \newline errors)))
    (set-log-level (:log-level options))
    (when-not (installed?) (exit 3 "ffmpeg not found on path."))
    (.join (start arguments options))))

