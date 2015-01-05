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
            [video-server.title :refer [episode-title season-title]]
            [video-server.video :refer [encoded-url sorting-title video-container video-record]])
  (:import (java.io File)
           (video_server.model Title VideoKey)))

; Map of folders to video keys to Videos
(defonce library (ref {}))

; Map of title strings to Titles
(defonce titles (ref {}))

; Map of file to video key
(defonce ^:private files (ref {}))

(defn remove-all
  "Removes all videos from the library."
  []
  (dosync
    (ref-set library {})
    (ref-set titles {})
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
    (str/join (re-seq #"[A-Za-z0-9&\-\(\) ]" simple))))

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
  ([folder key]
   (get-in @library [folder key]))
  ([[folder key]]
   (get-in @library [folder key])))

(defn title-for-id
  "Returns the Title associated with the given id."
  [id]
  (@titles id))

(defn title-for-key
  "Returns a Title for the video key."
  [key]
  (title-for-id (:title key)))

(defn video-for-file
  "Returns the Video for the given File."
  [folder file]
  (or (get-in @library [folder (files file)])
      (video-for-key folder (video-key file))))

(defn title-for-file
  "Returns the Title for the given File."
  [file]
  (@titles (:title (video-key file))))

(defn title-record
  "Returns a new Title containing the video."
  [title folder key]
  (make-record Title {:id (:title key)
                      :title title
                      :sorting (sorting-title title)
                      :videos #{[folder key]}}))

(defn add-video
  "Returns true if a new video was added, false if it was added to an
  existing video."
  [folder file]
  (when-let [info (video-info file)]
    (when-let [container (video-container file info (:url folder))]
      (when-let [video (video-record container info)]
        (let [key (video-key video)
              video-exists (get-in @library [folder key])
              title-exists (get @titles (:title key))]
          (dosync
            (if video-exists
              (alter library update-in [folder key :containers] conj (first (:containers video)))
              (alter library update-in [folder] assoc key video))
            (if title-exists
              (alter titles update-in [(:title key) :videos] conj [folder key])
              (alter titles assoc (:title key) (title-record (:title video) folder key)))
            (alter files assoc file key)
            (not video-exists)))))))

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
  "Adds the metadata to the Title."
  [title info]
  (log/debug "adding metadata for" (:title title))
  (dosync
    (alter titles assoc-in [(:title (video-key title)) :info] info)))

(defn- image-type
  "Returns the image type, :thumb or :poster."
  [file]
  (or (#{:thumb} (file-subtype file)) :poster))

(defn add-image
  "Adds an image to an existing Title."
  ([folder file]
   (when-let [title (title-for-file file)]
     (add-image folder file title)))
  ([folder file title]
   (log/debug "adding image" (str file))
   (let [key (video-key title)
         url (encoded-url (:url folder) file)]
     (dosync
       (alter titles assoc-in [(:title key) (image-type file)] url)))))

(defn remove-image
  "Removes an image file from a video."
  [folder file title]
  (let [key (video-key title)
        url (encoded-url (:url folder) file)
        image (image-type file)]
    (log/debug "removing image" (str file))
    (dosync
      (when (= url (get-in @titles [(:title key) image]))
        (alter titles assoc-in [(:title key) image] nil)))))

(defn remove-file
  "Removes a file from the library."
  [folder file]
  (when-let [title (title-for-file file)]
    (cond
      (image? file) (remove-image folder file title)))
  (when-let [video (video-for-file folder file)]
    (cond
      (video? file) (remove-video folder file video)
      (subtitle? file) (remove-subtitle folder file video))))

(defn ^:deprecated video-with-metadata
  "Appends missing fields from the title metadata."
  [video]
  (if-let [title (@titles (:title (video-key video)))]
    (let [info (:info title)]
      (merge video
             {:title (or (:title info) (:title title))
              :sorting (:sorting title)
              :poster (:poster title)
              :thumb (:thumb title)}
             (when-let [st (season-title title (:season video))]
               {:season-title st})
             (when-let [et (episode-title title video)]
               {:episode-title et})))
    video))

(defn current-videos
  "Returns a sequence of all of the videos in the library."
  []
  (map video-with-metadata (for [folder @library video (second folder)] (second video))))

(defn title-with-videos
  "Return the title with Videos subsituted for keys."
  [title]
  (assoc title :videos (map (fn [[f k]] (video-for-key f k)) (:videos title))))

(defn current-titles
  "Returns a sequence of all the titles in the library."
  []
  (map title-with-videos (vals @titles)))

