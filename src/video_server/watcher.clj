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
            [video-server.file :refer [image-filter image? movie-filter subtitle-filter subtitle? video?]]
            [video-server.library :as library :refer [add-image add-subtitle current-videos remove-all video-for-file]]
            [video-server.process :refer [process-file]]
            [video-server.video :refer [modified]]))

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
  "Returns true if the file has has content and has not been modified
  for a reasonable duration."
  [file]
  (and (.isFile file)
       (pos? (.length file))
       (< (.lastModified file) (- (System/currentTimeMillis) stable-time))))

(defn add-subtitles
  "Performs the initial scan for subtitles."
  [folder video]
  (doseq [file (.listFiles (:file folder) (subtitle-filter (:title video)))]
    (log/info "adding subtitle" (str file))
    (add-subtitle folder file video)))

(defn add-images
  "Performs the initial scan for image files."
  [folder video]
  (doseq [file (.listFiles (:file folder) (image-filter (:title video)))]
    (log/info "adding image" (str file))
    (add-image folder file video)))

(defn add-videos
  "Performs the initial folder scan, adding videos to the library."
  [folder]
  (let [files (.listFiles (:file folder) (movie-filter))]
    (doseq [file (filter stable? files)]
      (log/info "adding file" (str file))
      (library/add-video folder file))
    (doseq [video (sort-by modified (current-videos))]
      (add-subtitles folder video)
      (add-images folder video)
      (process-file folder video))))

(defn scan-folder
  "Loads all of the videos and subtitles in the folder."
  [folder]
  (log/info "scanning" (str (:file folder)) "as" (:name folder))
  (remove-all) ; TODO this shouldn't be necessary
  (add-videos folder))

(defn add-video
  "Adds a video file to the library and checks for subtitles and
  images if the video was added (is new) to the library."
  [folder file]
  (when (library/add-video folder file)
    (let [video (video-for-file folder file)]
      (add-subtitles folder video)
      (add-images folder video))))

(defn add-file
  "Adds a newly discovered file to the library and queues it for
  processing."
  [folder file]
  (log/info "adding file" (str file))
  (when-let [video (video-for-file folder file)]
    (library/remove-file folder file))
  (cond
    (video? file) (add-video folder file)
    (subtitle? file) (add-subtitle folder file)
    (image? file) (add-image folder file))
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

(defn add-pending-file
  "Adds a new or changing file to the list of pending files."
  [file]
  (when (and (.isFile file) (not (contains? @pending-files file)))
    (log/info "watching file" (str file))
    (dosync (alter pending-files conj file))))

(defn remove-file
  "Removes the file from the video library."
  [folder file]
  (log/info "removing file" (str file))
  (library/remove-file folder file))

(defn file-event-callback
  "Processes the file system events."
  [folder event filename]
  (when ((some-fn video? subtitle? image?) filename)
    (let [file (io/file filename)]
      (try (case event
             :create (if (stable? file)
                       (add-file folder file)
                       (add-pending-file file))
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

