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
            [video-server.ffmpeg :refer [video-info]]
            [video-server.file :refer [file-base file-subtype image? mimetype subtitle? title-info video?]]
            [video-server.format :refer [lang-name]]
            [video-server.model :refer :all]
            [video-server.video :refer [encoded-url video-container video-record]])
  (:import (java.io File)
           (java.util Locale)
           (video_server.model VideoKey)))

; Map of folders to video keys to videos
(defonce library (ref {}))

; Map of file to video key
(defonce ^:private files (ref {}))

; Map of UUID to [folder key]
(defonce ^:private ids (ref {}))

(defn remove-all
  "Removes all videos from the library."
  []
  (dosync
    (ref-set library {})
    (ref-set files {})))

(defn has-file?
  "Returns whether the file belongs to the library."
  [folder file]
  (contains? @files file))

(defn norm-title
  "Returns a normalized title that can be used in a filename."
  [title]
  (let [simple (-> title
                   (str/replace " - " " ")
                   (str/replace "_" " ")
                   (str/replace "(Unrated)" ""))]
    (apply str (re-seq #"[A-Za-z0-9&\-\(\) ]" simple))))

(defn norm-key
  "Returns the map with normalized values."
  [data]
  (assoc data :title (str/lower-case (norm-title (:title data)))))

(defn video-key
  "Returns the key for the given Video, info, or title string."
  [data]
  (cond
    (map? data) (make-record VideoKey (norm-key data))
    (string? data) (video-key (title-info data))
    (instance? File data) (video-key (file-base data))))

(defn video-for-key
  "Returns the video for the specified video key."
  [folder key]
  (get-in @library [folder key]))

(defn video-for-file
  "Returns the video for the given File."
  [folder file]
  (or (get-in @library [folder (files file)])
      (video-for-key folder (video-key file))))

(defn video-for-id
  "Returns the video associated with the given UUID."
  [id]
  (let [[folder key] (@ids id)]
    (video-for-key folder key)))

(defn add-video
  "Returns true if a new video was added, false if it was added to an
  existing video."
  [folder file]
  (when-let [info (video-info file)]
    (when-let [container (video-container file info (:url folder))]
      (when-let [video (video-record container info)]
        (let [key (video-key video)
              exists (get-in @library [folder key])]
          (dosync
            (if exists
              (alter library update-in [folder key :containers] conj (first (:containers video)))
              (alter library update-in [folder] assoc key video))
            (alter files assoc file key)
            (alter ids assoc (:id video) [folder key])
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
            (alter files dissoc file)
            (alter ids dissoc (:id video)))))))

(defn add-subtitle
  "Returns true if the subtitle was added to an existing video."
  ([folder file]
   (when-let [video (video-for-file folder file)]
     (add-subtitle folder file video)))
  ([folder file video]
   (log/debug "adding subtitle" (str file))
   (let [[_ lang _] (str/split (.getName file) #"\.")
         subtitle-url (encoded-url (:url folder) file)
         subtitle (->Subtitle (lang-name lang) lang (.getName file) subtitle-url (mimetype file))]
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

(defn add-info
  "Returns true if the metadata was added to an existing video."
  [folder video info]
  (log/debug "adding metadata for" (:title video))
  (dosync
    (alter library assoc-in [folder (video-key video) :info] info)
    true))

(defn- image-type
  "Returns the image type, :thumb or :poster."
  [file]
  (or (#{:thumb} (file-subtype file)) :poster))

(defn add-image
  "Returns true if the image was added to an existing video."
  ([folder file]
   (when-let [video (video-for-file folder file)]
     (add-image folder file video)))
  ([folder file video]
   (log/debug "adding image" (str file))
   (let [url (encoded-url (:url folder) file)]
     (dosync
       (alter library assoc-in [folder (video-key video) (image-type file)] url)
       (alter files assoc file (video-key video))
       true))))

(defn remove-image
  "Removes an image file from a video."
  [folder file video]
  (let [key (video-key video)
        url (encoded-url (:url folder) file)
        image (image-type file)]
    (log/debug "removing image" (str file))
    (dosync
      (when (= url (get-in @library [folder key image]))
        (alter library assoc-in [folder key image] nil))
      (alter files dissoc file))))

(defn remove-file
  "Removes a file from the library."
  [folder file]
  (when-let [video (video-for-file folder file)]
    (cond
      (video? file) (remove-video folder file video)
      (subtitle? file) (remove-subtitle folder file video)
      (image? file) (remove-image folder file video))))

(defn current-videos
  "Returns a sequence of all of the videos in the library."
  []
  (for [folder @library video (second folder)] (second video)))

