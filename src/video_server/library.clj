;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.library
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [video-server.encoder :as encoder]
            [video-server.ffmpeg :as ffmpeg]
            [video-server.file :as file]
            [video-server.format :as format]
            [video-server.model :refer :all]
            [video-server.video :as video])
  (:import (java.io File)
           (java.util Locale)
           (video_server.model VideoKey)))

; Map of folders to video keys to videos
(defonce library (ref {}))

; Map of file to video key
(defonce ^:private files (ref {}))

(defn remove-all
  "Removes all videos from the library."
  []
  (dosync
    (ref-set library {})
    (ref-set files {})))

(defn video-key
  "Returns the key for the given Video, info, or title string."
  [data]
  (cond
    (map? data) (make-record VideoKey data)
    (string? data) (video-key (file/title-info data))
    (instance? File data) (video-key (file/file-base data))))

(defn video-for-file
  "Returns the video for the given File, or nil if the file is not in
  the library."
  [folder file]
  (or (get-in @library [folder (files file)])
      (get-in @library [folder (video-key file)])))

(defn add-video
  "Returns true if a new video was added, false if it was added to an
  existing video."
  [folder file]
  (when-let [info (ffmpeg/video-info file)]
    (when-let [container (video/video-container file info (:url folder))]
      (when-let [video (video/video-record container info)]
        (let [key (video-key video)
              exists (get-in @library [folder key])]
          (dosync
            (if exists
              (alter library update-in [folder key :containers] conj (first (:containers video)))
              (alter library update-in [folder] assoc key video))
            (alter files assoc file key)
            (not exists)))))))

(defn remove-video
  "Removes a file from a video. When the last file is removed, the
  video is also removed from the library."
  [folder file video]
  (let [key (video-key video)
        containers (remove #(= (.getName file) (:filename %)) (:containers video))]
    (log/debug "removing video" (str file))
    (dosync
      (if (empty? containers)
        (do (alter library update-in [folder] dissoc key)
            (alter files (partial apply dissoc) (map clojure.core/key (filter #(= key (val %)) @files))))
        (do (alter library update-in [folder key] assoc :containers containers)
            (alter files dissoc file))))))

(defn add-subtitle
  "Returns true if the subtitle was added to an existing video."
  ([folder file]
   (when-let [video (video-for-file folder file)]
     (add-subtitle folder file video)))
  ([folder file video]
   (log/debug "adding subtitle" (str file))
   (let [[_ lang _] (str/split (.getName file) #"\.")
         subtitle-url (video/encoded-url (:url folder) file)
         subtitle (->Subtitle (format/lang-name lang) lang (.getName file) subtitle-url (video/mimetype file))]
     (dosync
       (alter library update-in [folder (video-key video) :subtitles] conj subtitle)
       (alter files assoc file (video-key video))
       true))))

(defn remove-subtitle
  "Removes a subtitle file from a video."
  [folder file video]
  (let [key (video-key video)
        subtitles (remove #(= (.getName file) (:filename %)) (:subtitles video))]
    (log/debug "removing subtitle" (str file))
    (dosync
      (alter library assoc-in [folder key :subtitles] subtitles)
      (alter files dissoc file))))

(defn add-image
  "Returns true if the image was added to an existing video."
  ([folder file]
   (when-let [video (video-for-file folder file)]
     (add-image folder file video)))
  ([folder file video]
   (log/debug "adding image" (str file))
   (let [url (video/encoded-url (:url folder) file)]
     (dosync
       (alter library assoc-in [folder (video-key video) :poster] url)
       (alter files assoc file (video-key video))
       true))))

(defn remove-image
  "Removes an image file from a video."
  [folder file video]
  (let [key (video-key video)
        url (video/encoded-url (:url folder) file)]
    (log/debug "removing image" (str file))
    (dosync
      (when (= url (get-in @library [folder key :poster]))
        (alter library assoc-in [folder key :poster] nil))
      (alter files dissoc file))))

(defn remove-file
  "Removes a file from the library."
  [folder file]
  (when-let [video (video-for-file folder file)]
    (cond
      (file/video? file) (remove-video folder file video)
      (file/subtitles? file) (remove-subtitle folder file video)
      (file/image? file) (remove-image folder file video))))

(defn current-videos
  "Returns a sequence of all of the videos in the library."
  []
  (for [folder @library video (second folder)] (second video)))

