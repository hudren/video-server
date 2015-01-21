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
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [video-server.encoder :as encoder]
            [video-server.ffmpeg :refer [video-info]]
            [video-server.file :refer [descendant? filename file-base file-subtype fullpath image? mimetype relative-path subtitle?
                                       title-info video?]]
            [video-server.format :refer [lang-name]]
            [video-server.model :refer :all]
            [video-server.title :refer [best-image episode-title season-title]]
            [video-server.video :refer [sorting-title video-container video-record]]
            [video-server.util :refer :all])
  (:import (java.io File)
           (video_server.model Title VideoKey)))

; Map of folders to video keys to Videos
(defonce library (ref {}))

; Map of title strings to Titles
(defonce titles (ref {}))

; Map of file to [id|key modified]
(defonce files (ref {}))

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

(defn up-to-date?
  "Returns whether the file in the library is up to date with respect
  to the file on the file system."
  [folder ^File file]
  (when-let [existing (@files file)]
    (= (.lastModified file) (second existing))))

(defn folder-for-url
  "Returns the Folder related to the base url."
  [url]
  (ffirst (filter #(= (-> % first :url) url) @library)))

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
    (instance? VideoKey data) data
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

(defn title-for-file
  "Returns the Title for the given File."
  [file]
  (title-for-id (:title (video-key file))))

(defn video-for-file
  "Returns the Video for the given File."
  [folder file]
  (or (get-in @library [folder (first (files file))])
      (video-for-key folder (video-key file))))

(defn files-for-dir
  "Returns the files located in the directory."
  [dir]
  (filter #(descendant? dir %) (keys @files)))

(defn files-for-video
  "Returns the Files assoicated with the video."
  [video]
  (let [key (video-key video)]
    (map first (filter #(= (-> % second first) key) @files))))

(defn files-for-title
  "Returns all of the Files assoicated with the title."
  [title]
  (into (map first (filter #(= (-> % second first) (:id title)) @files))
        (mapcat (comp files-for-video second) (:videos title))))

(defn folders-for-title
  "Returns the Folders containing the given title."
  [title]
  (distinct (map first (:videos title))))

(defn title-record
  "Returns a new Title containing the video."
  [title folder key]
  (make-record Title {:id (:title key)
                      :title title
                      :sorting (sorting-title title)
                      :videos #{[folder key]}}))

(defn add-video
  "Adds a container to the library, returning a map indicating whether
  a new video and/or title was created."
  [folder file]
  (when-let [info (video-info file)]
    (when-let [container (video-container folder file info)]
      (when-let [video (video-record container info)]
        (dosync
          (let [key (video-key video)
                video-exists (get-in @library [folder key])
                title-exists (get @titles (:title key))]
            (if video-exists
              (alter library update-in [folder key :containers] conj (first (:containers video)))
              (alter library update-in [folder] assoc key video))
            (if title-exists
              (alter titles update-in [(:title key) :videos] conj [folder key])
              (alter titles assoc (:title key) (title-record (:title video) folder key)))
            (alter files assoc file [key (.lastModified ^File file)])
            {:video (not video-exists) :title (not title-exists)}))))))

(defn remove-video
  "Removes a file from a video. When the last file is removed, the
  video is also removed from the library."
  [folder file video]
  (log/debug "removing video" (str file))
  (dosync
    (let [key (video-key video)
          title (@titles (:title key))
          containers (remove #(= (filename file) (:filename %)) (:containers video))
          videos (remove #(= % [folder key]) (:videos title))]
      (if (empty? containers)
        (do (alter library update-in [folder] dissoc key)
            (alter files (partial apply dissoc) (files-for-video key))
            (if (empty? videos)
              (alter titles dissoc (:title key))
              (alter titles update-in [(:title key)] assoc :videos videos)))
        (do (alter library update-in [folder key] assoc :containers containers)
            (alter files dissoc file))))))

(defn add-subtitle
  "Returns true if the subtitle was added to an existing video."
  ([folder file]
   (when-let [video (video-for-file folder file)]
     (add-subtitle folder file video)))
  ([folder file video]
   (log/debug "adding subtitle" (str file))
   (let [[_ lang _] (str/split (filename file) #"\.")
         path (relative-path folder file)
         subtitle-url (encoded-url (:url folder) path)
         subtitle (->Subtitle path (filename file) (lang-name lang) lang subtitle-url (mimetype file))]
     (dosync
       (alter library update-in [folder (video-key video) :subtitles] conj subtitle)
       (alter files assoc file [(video-key video) (.lastModified ^File file)])
       true))))

(defn remove-subtitle
  "Removes a subtitle file from a video."
  [folder file video]
  (let [key (video-key video)
        subtitles (remove #(= (filename file) (:filename %)) (:subtitles video))]
    (log/debug "removing subtitle" (str file))
    (dosync
      (alter library assoc-in [folder key :subtitles] subtitles)
      (alter files dissoc file))))

(defn add-info
  "Adds the metadata to the Title, merging info for the folder."
  [folder title info]
  (log/debug "adding metadata for" (:title title))
  (dosync
    (alter titles assoc-in [(:title (video-key title)) :info] (merge-options info (-> folder :options :info)))))

(defn- image-type
  "Returns the image type, :thumb or :poster."
  [file]
  (or (#{:thumb} (file-subtype file)) :poster))

(defn- image-key
  "Returns the indexes to uniquely store the image."
  [path]
  (let [title (title-info path)]
    (->> [(when-let [season (:season title)] [:seasons season])
          (when-let [episode (:episode title)] [:episodes episode])
          (image-type path)]
         flatten
         (remove nil?))))

(defn add-image
  "Adds an image to an existing Title."
  ([folder file]
   (when-let [title (title-for-file file)]
     (add-image folder file title)))
  ([folder file title]
   (log/debug "adding image" (str file))
   (let [key (video-key title)
         path (relative-path folder file)
         url (encoded-url (:url folder) path)
         image (image-key path)]
     (dosync
       (alter titles assoc-in (cons (:title key) image) url)
       (alter files assoc file [(:id title) (.lastModified ^File file)])))))

(defn remove-image
  "Removes an image file from a video."
  [folder file title]
  (let [key (video-key title)
        path (relative-path folder file)
        url (encoded-url (:url folder) path)
        image (image-key path)]
    (log/debug "removing image" (str file))
    (dosync
      (when (= url (get-in @titles (cons (:title key) image)))
        (alter titles assoc-in (cons (:title key) image) nil)
        (alter files dissoc file)))))

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

(defn current-videos
  "Returns a sequence of all of the videos in the library."
  ([]
   (for [folder @library video (second folder)] (second video)))
  ([folder]
   (vals (@library folder)))
  ([folder path]
   (filter #(some (fn [file] (= (.getParentFile file) path)) (files-for-video %)) (current-videos folder))))

(defn current-titles
  "Returns a sequence of all the titles in the library."
  ([]
   (vals @titles))
  ([folder]
   (filter #(some #{folder} (folders-for-title %)) (vals @titles)))
  ([folder path]
   (filter #(some (fn [file] (= (.getParentFile file) path)) (files-for-title %)) (current-titles folder))))

(defn ^:deprecated video-with-metadata
  "Appends missing fields from the title metadata."
  [video]
  (if-let [title (@titles (:title (video-key video)))]
    (let [info (:info title)]
      (merge video
             {:title (or (:title info) (:title title))
              :sorting (:sorting title)
              :poster (best-image :poster title video)
              :thumb (:thumb title)}
             (when-let [st (season-title title (:season video))]
               {:season-title st})
             (when-let [et (episode-title title video)]
               {:episode-title et})))
    video))

(defn video-listing
  "Returns a sequence of all of the videos in the library."
  []
  (map video-with-metadata (current-videos)))

(defn title-with-videos
  "Return the title with Videos subsituted for keys."
  [title]
  (assoc title :videos (map (fn [[f k]] (video-for-key f k)) (:videos title))))

(defn title-listing
  "Returns a sequence of all the titles in the library with videos."
  []
  (map title-with-videos (current-titles)))

