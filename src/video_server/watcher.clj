;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.watcher
  (:require [clojure-watch.core :refer [start-watch]]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [video-server.file :as file]
            [video-server.library :as library]
            [video-server.process :as process]
            [video-server.video :as video]))

(def ^:const stable-time 30000)
(def ^:const check-time 15000)

;; Set of changing files
(defonce pending-files (ref #{}))

(defn periodically
  "Peridoically calls a function. Returns a stopping function."
  [f ms]
  (let [p (promise)]
    (future
      (while
        (= (deref p ms "running") "running")
        (f)))
    #(deliver p "stop")))

(defn stable?
  "Returns true if the file has not been modified for at least thirty
  seconds."
  [file]
  (< (.lastModified file) (- (System/currentTimeMillis) stable-time)))

(defn add-subtitles
  "Performs the initial scan for subtitles."
  ([folder]
   (doseq [video (library/current-videos)]
     (add-subtitles folder video)))
  ([folder video]
   (doseq [file (.listFiles (:file folder) (file/subtitle-filter (:title video)))]
     (log/info "adding subtitles" (str file))
     (library/add-subtitle folder file video))))

(defn add-videos
  "Performs the initial folder scan, adding videos to the library."
  [folder]
  (let [files (.listFiles (:file folder) (file/movie-filter))]
    (doseq [file files]
      (log/info "adding file" (str file))
      (library/add-video folder file))
    (doseq [video (sort-by video/last-modified (library/current-videos))]
      (add-subtitles folder video)
      (process/process-file folder video))))

(defn add-file
  "Adds a newly discovered file to the library."
  [folder file]
  (log/info "adding file" (str file))
  (when-let [video (library/video-for-file folder file)]
    (library/remove-file folder file))
  (cond
    (file/video? file) (library/add-video folder file)
    (file/subtitles? file) (library/add-subtitle folder file)))

(defn check-pending-files
  "Checks the pending files and adds the stable ones to the library."
  [folder]
  (when (seq @pending-files)
    (log/trace "checking pending files")
    (doseq [file @pending-files]
      (try (when (stable? file)
             (add-file folder file)
             (dosync (alter pending-files disj file))
             (process/process-file folder file))
           (catch Exception e (log/error e "adding pending file" (str file)))))))

(defn scan-folder
  "Loads all of the videos and subtitles in the folder."
  [folder]
  (log/info "scanning" (str (:file folder)) "as" (:name folder))
  (library/remove-all) ; TODO this shouldn't be necessary
  (add-videos folder))

(defn add-pending-file
  "Adds a new or changing file to the list of pending files."
  [file]
  (log/trace "adding pending file" (str file))
  (dosync (alter pending-files conj file)))

(defn remove-file
  "Removes the file from the video library."
  [folder file]
  (log/info "removing file" (str file))
  (library/remove-file folder file))

(defn file-event-callback
  "Processes the file system events."
  [folder event filename]
  (log/trace "file event:" event filename)
  (when (or (file/video? filename) (file/subtitles? filename))
    (let [file (io/file filename)]
      (try (case event
             :create (add-pending-file file)
             :modify (add-pending-file file)
             :delete (remove-file folder file)
             nil)
           (catch Exception e (log/error e "error in file-event-callback"))))))

(defn start-watcher
  "Watches for file system changes in the video folder."
  [folder]
  (scan-folder folder)
  (let [path (-> folder :file .getAbsolutePath)]
    (log/info "starting watcher on" path)
    (start-watch [{:path path
                   :event-types [:create :modify :delete]
                   :callback (partial file-event-callback folder)}])
    (periodically (partial check-pending-files folder) check-time)))

