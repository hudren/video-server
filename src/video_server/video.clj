;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.video
  (:require [clojure.string :as str]
            [video-server.file :refer [file-type filename mimetype relative-path title-info]]
            [video-server.format :refer [audio-desc video-desc video-dimension]]
            [video-server.model :refer :all]
            [video-server.util :refer :all])
  (:import (java.util Locale UUID)
           (video_server.model Container Video)
           (java.io File)))

(def locales (into {} (map #(vector (.getISO3Language %) %) (map #(Locale. %) (Locale/getISOLanguages)))))

(defn quality
  "Comparator for sorting containers with highest quality first."
  [c1 c2]
  (let [c (compare (:width c2) (:width c1))]
    (if-not (zero? c) c (compare (:size c2) (:size c1)))))

(defn modified
  "Returns the modified time of the oldest container."
  [video]
  (apply min (map :modified (:containers video))))

(defn last-modified
  "Returns the last modified time of all the containers."
  [video]
  (apply max (map :modified (:containers video))))

(defn container-track
  "Returns the stream corresponding to the track index."
  [info track]
  (first (filter #(= (:index %) track) (:streams info))))

(defn video-stream
  "Returns the video stream metadata."
  [info]
  (first (filter #(= "video" (:codec_type %)) (:streams info))))

(defn audio-streams
  "Returns a sequence of audio stream metadata."
  [info]
  (filter #(= "audio" (:codec_type %)) (:streams info)))

(defn subtitle-streams
  "Returns a sequence of subtitle metadata."
  [info]
  (filter #(= "subtitle" (:codec_type %)) (:streams info)))

(defn audio-language
  "Returns human-readable string for the languages of the audio
  streams."
  [info]
  (let [audio (map #(-> % :tags :language) (audio-streams info))
        audio (keep locales audio)
        audio (apply sorted-set (map #(.getDisplayLanguage %) audio))]
    (str/join ", " audio)))

(defn video-container
  "Returns a container record for the specified file."
  [folder ^File file info]
  (let [path (relative-path folder file)
        video (video-stream info)
        audio (audio-streams info)
        width (parse-long (:width video))
        height (parse-long (:height video))
        fields {:path path
                :filename (filename file)
                :filetype (file-type file)
                :language (audio-language info)
                :size (parse-long (-> info :format :size))
                :bitrate (parse-long (-> info :format :bit_rate))
                :width width
                :height height
                :dimension (video-dimension width height)
                :video (video-desc (:codec_name video))
                :audio (str/join ", " (distinct (map #(audio-desc (:codec_name %)) audio)))
                :modified (.lastModified file)
                :url (encoded-url (:url folder) path)
                :mimetype (mimetype file)}]
    (make-record Container fields)))

(defn rank-containers
  "Returns the ranked containers for the video."
  [video]
  (reverse (sort-by :size (:containers video))))

(defn web-playback?
  "Returns whether the container is compatible for web playback."
  [container]
  (and (.contains (:video container) "H.264") (.contains (:audio container) "AAC")))

(defn can-cast?
  "Returns whether the video is compatible for casting."
  [video]
  (some web-playback? (:containers video)))

(defn can-download?
  "Returns whether the file is small enough for downloading."
  [video]
  (some #(< (:size %) 4187593114) (:containers video)))

(defn video-title
  "Returns the video title based on the metadata or filename."
  [container info]
  (title-info (or (-> info :format :title)
                  (:filename container))))

(defn sorting-title
  "Returns a title suitable for sorting."
  [title]
  (str/trim
    (condp #(.startsWith %2 %1) title
      "The " (subs title 4)
      "An " (subs title 3)
      "A " (subs title 2)
      title title)))

(defn video-record
  "Returns a new video record with the specified container."
  [container info]
  (let [duration (parse-double (-> info :format :duration))
        title (video-title container info)]
    (make-record Video (merge {:id (str (UUID/randomUUID))
                               :sorting (sorting-title (:title title))
                               :duration duration
                               :containers (list container)}
                              title))))

