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
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [video-server.directory :refer [watch-directory]]
            [video-server.file :refer [image-filter image? metadata? movie-filter subtitle-filter subtitle? video?]]
            [video-server.library :as library :refer [add-image add-info add-subtitle current-titles current-videos files-for-dir
                                                      has-file? norm-title title-for-file up-to-date? video-for-file]]
            [video-server.metadata :refer [read-metadata title-dir]]
            [video-server.process :refer [process-file process-title]]
            [video-server.util :refer :all]
            [video-server.video :refer [modified]])
  (:import (java.io File FilenameFilter)))

(def ^:const stable-time 60000)
(def ^:const check-time 10000)
(def ^:const scan-threads 4)

;; Set of changing files
(defonce pending-files (ref #{}))

(defn stable?
  "Returns true if the file has has content and has not been modified
  for a reasonable duration."
  [^File file]
  (and (.isFile file)
       (pos? (.length file))
       (or (image? file)
           (metadata? file)
           (< (.lastModified file) (- (System/currentTimeMillis) stable-time)))))

(defn remove-file
  "Removes the file from the video library."
  [folder file]
  (when (has-file? folder file)
    (log/info "removing file" (str file))
    (library/remove-file folder file)))

(defn add-pending-file
  "Adds a new or changing file to the list of pending files."
  [folder file]
  (when-not (contains? @pending-files [folder file])
    (log/info "watching file" (str file))
    (remove-file folder file)
    (dosync (alter pending-files conj [folder file]))))

(defn add-metadata
  "Reads existing metadata for the given video."
  [folder title]
  (when title
    (if-let [info (read-metadata title)]
      (add-info folder title info)
      (process-title folder title))))

(defn scan-videos
  "Scans the directory for videos."
  [folder path]
  (let [dir (io/file (or path (:file folder)))
        files (group-by stable? (.listFiles dir (movie-filter)))]
    (parseq scan-threads [file (get files true)]
      (log/info "adding video" (str file))
      (let [added (library/add-video folder file)]
        (when (:title added)
          (add-metadata folder (title-for-file file)))))
    (doseq [file (get files false)]
      (add-pending-file folder file))))

(defn scan-images
  "Scans the directory for images."
  [folder path]
  (let [dir (io/file (or path (:file folder)))]
    (parseq scan-threads [file (.listFiles dir (image-filter))]
      (when-let [title (title-for-file file)]
        (log/info "adding image" (str file))
        (add-image folder file title)))))

(defn scan-subtitles
  "Scans the directory for subtitles."
  [folder path]
  (let [dir (io/file (or path (:file folder)))]
    (parseq scan-threads [file (.listFiles dir (subtitle-filter))]
      (when-let [video (video-for-file folder file)]
        (log/info "adding subtitle" (str file))
        (add-subtitle folder file video)))))

(defn scan-dir
  "Loads all of the videos and subtitles in the folder."
  [folder path]
  (log/info "scanning" path "as" (:name folder))
  (scan-videos folder path)
  (parall (scan-images folder path)
          (scan-subtitles folder path))
  (doseq [video (sort-by modified (current-videos folder path))]
    (process-file folder video)))

(defn remove-dir
  "Removes all of the files in the directory."
  [folder dir]
  (doseq [file (files-for-dir dir)]
    (log/info "removing file" (str file))
    (library/remove-file folder file)))

(defn- list-files
  "Lists files related to the video by title and filter."
  [dir video filt]
  (let [file (io/file dir)]
    (distinct (concat (.listFiles file ^FilenameFilter (filt (:title video)))
                      (.listFiles file ^FilenameFilter (filt (norm-title (:title video))))))))

(defn add-images
  "Performs a scan for image files related to the title."
  [folder title]
  (doseq [file (list-files (title-dir title) title image-filter)]
    (log/info "adding image" (str file))
    (add-image folder file title)))

(defn add-subtitles
  "Performs a scan for subtitles related to the video."
  [folder video]
  (doseq [file (list-files (:file folder) video subtitle-filter)]
    (log/info "adding subtitle" (str file))
    (add-subtitle folder file video)))

(defn add-video
  "Adds a video file to the library and checks for subtitles and
  images if the video was added (is new) to the library."
  [folder file]
  (let [added (library/add-video folder file)]
    (when (:title added)
      (let [title (title-for-file file)]
        (add-metadata folder title)
        (add-images folder title)))
    (when (:video added)
      (let [video (video-for-file folder file)]
        (add-subtitles folder video)))))

(defn add-file
  "Adds a newly discovered file to the library and queues it for
  processing."
  [folder file]
  (log/info "adding file" (str file))
  (when (has-file? folder file)
    (library/remove-file folder file))
  (cond
    (video? file) (add-video folder file)
    (subtitle? file) (add-subtitle folder file)
    (image? file) (add-image folder file)
    (metadata? file) (add-metadata folder (title-for-file file)))
  (process-file folder file))

(defn check-pending-files
  "Checks the pending files and adds the stable ones to the library."
  []
  (doseq [[folder ^File file] @pending-files]
    (try (when (stable? file)
           (when-not (up-to-date? folder file)
             (add-file folder file)))
         (when (or (stable? file) (not (.exists file)))
           (dosync (alter pending-files disj [folder file])))
         (catch Exception e (log/error e "adding pending file" (str file))))))

(defn file-event
  "Processes the file system events related to files."
  [folder event file]
  (log/trace "file event" event file)
  (when ((some-fn video? subtitle? image? metadata?) file)
    (try (case event
           :create (if (stable? file)
                     (add-file folder file)
                     (add-pending-file folder file))
           :modify (add-pending-file folder file)
           :delete (remove-file folder file)
           nil)
         (catch Exception e (log/error e "error in file-event-callback")))))

(defn dir-event
  "Processes the file system events related to directories."
  [folder event dir]
  (log/trace "dir event" event dir)
  (try
    (case event
      :create (scan-dir folder dir)
      :delete (remove-dir folder dir)
      nil)
    (catch Exception e (log/error e "error in dir-event-callback"))))

(defn start-watcher
  "Watches for file system changes in the given folders."
  [folders]
  (doseq [folder folders]
    (watch-directory (:file folder) (partial #'file-event folder) (partial #'dir-event folder)))
  (periodically check-pending-files check-time))

