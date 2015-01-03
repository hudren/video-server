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
            [video-server.file :refer [image-filter image? metadata? movie-filter subtitle-filter subtitle? video?]]
            [video-server.library :as library :refer [add-image add-info add-subtitle current-titles current-videos has-file?
                                                      norm-title remove-all title-for-file video-for-file]]
            [video-server.metadata :refer [read-metadata]]
            [video-server.process :refer [process-file]]
            [video-server.util :refer :all]
            [video-server.video :refer [modified]]))

(def ^:const stable-time 60000)
(def ^:const check-time 10000)

;; Set of changing files
(defonce pending-files (ref #{}))

(defn stable?
  "Returns true if the file has has content and has not been modified
  for a reasonable duration."
  [file]
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
  (distinct (concat (.listFiles (:file folder) (filter (:title video)))
                    (.listFiles (:file folder) (filter (norm-title (:title video)))))))

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
  (let [files (.listFiles (:file folder) (movie-filter))]
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
  [folder]
  (log/info "scanning" (str (:file folder)) "as" (:name folder))
  (remove-all)
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
  [folder]
  (when (seq @pending-files)
    #_(log/trace "checking pending files")
    (doseq [file @pending-files]
      (try (when (stable? file)
             (add-file folder file))
           (when (or (stable? file) (not (.exists file)))
             (dosync (alter pending-files disj file)))
           (catch Exception e (log/error e "adding pending file" (str file)))))))

(defn remove-file
  "Removes the file from the video library."
  [folder file]
  (when (has-file? folder file)
    (log/info "removing file" (str file))
    (library/remove-file folder file)))

(defn add-pending-file
  "Adds a new or changing file to the list of pending files."
  [folder file]
  (when (and (.isFile file) (not (contains? @pending-files file)))
    (log/info "watching file" (str file))
    (remove-file folder file)
    (dosync (alter pending-files conj file))))

(defn file-event-callback
  "Processes the file system events."
  [folder event filename]
  (when ((some-fn video? subtitle? image? metadata?) filename)
    (let [file (io/file filename)]
      (try (case event
             :create (if (stable? file)
                       (add-file folder file)
                       (add-pending-file folder file))
             :modify (add-pending-file folder file)
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

