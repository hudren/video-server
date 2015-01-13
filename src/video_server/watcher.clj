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
            [video-server.file :refer [fullpath hidden? image-filter image? metadata? movie-filter subtitle-filter subtitle? video?]]
            [video-server.library :as library :refer [add-image add-info add-subtitle current-titles current-videos has-file?
                                                      norm-title title-for-file up-to-date? video-for-file]]
            [video-server.metadata :refer [read-metadata]]
            [video-server.process :refer [process-file]]
            [video-server.util :refer :all]
            [video-server.video :refer [modified]])
  (:import (java.io File FilenameFilter)))

(def ^:const stable-time 60000)
(def ^:const check-time 10000)

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

(defn add-metadata
  "Reads existing metadata for the given video."
  [folder title]
  (when title
    (when-let [info (read-metadata folder title)]
      (add-info title info))))

(defn- list-files
  "Lists files related to the video by title and filter."
  [folder video filter]
  (let [file (io/file (:file folder))]
    (distinct (concat (.listFiles file ^FilenameFilter (filter (:title video)))
                      (.listFiles file ^FilenameFilter (filter (norm-title (:title video))))))))

(defn add-subtitles
  "Performs the initial scan for subtitles."
  [folder video]
  (doseq [file (list-files folder video subtitle-filter)]
    (log/info "adding subtitle" (str file))
    (add-subtitle folder file video)))

(defn add-images
  "Performs the initial scan for image files."
  [folder title]
  (doseq [file (list-files folder title image-filter)]
    (log/info "adding image" (str file))
    (add-image folder file title)))

(defn add-videos
  "Performs the initial folder scan, adding videos to the library."
  [folder]
  (let [file (io/file (:file folder))
        files (.listFiles file (movie-filter))]
    (doseq [file (filter stable? files)]
      (log/info "adding video" (str file))
      (library/add-video folder file))
    (doseq [title (current-titles)]
      (add-metadata folder title)
      (add-images folder title))
    (doseq [video (sort-by modified (current-videos))]
      (add-subtitles folder video)
      (process-file folder video))))

(defn scan-folder
  "Loads all of the videos and subtitles in the folder."
  [folder path]
  (log/info "scanning" path "as" (:name folder))
  (add-videos folder))

(defn add-video
  "Adds a video file to the library and checks for subtitles and
  images if the video was added (is new) to the library."
  [folder file]
  (when (library/add-video folder file)
    (let [title (title-for-file file)]
      (add-metadata folder title)
      (add-images folder title))
    (let [video (video-for-file folder file)]
      (add-subtitles folder video))))

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

(defn file-event-callback
  "Processes the file system events."
  [folder event filename]
  (when ((some-fn video? subtitle? image? metadata?) filename)
    (let [file (io/file filename)]
      (when (and (.isFile file) (not (hidden? folder file)))
        (try (case event
               :create (if (stable? file)
                         (add-file folder file)
                         (add-pending-file folder file))
               :modify (add-pending-file folder file)
               :delete (remove-file folder file)
               nil)
             (catch Exception e (log/error e "error in file-event-callback")))))))

(defn watcher-spec
  "Returns a watcher spec for watching a particular folder."
  [folder]
  {:path (-> folder :file fullpath)
   :event-types [:create :modify :delete]
   :bootstrap (partial scan-folder folder)
   :callback (partial file-event-callback folder)
   :options {:recursive false}})

(defn start-watcher
  "Watches for file system changes in the given folders."
  [folders]
  (start-watch (map watcher-spec folders))
  (periodically check-pending-files check-time))

