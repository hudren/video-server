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
            [video-server.file :as file]
            [video-server.format :refer [audio-desc video-desc video-dimension]]
            [video-server.model :refer :all]
            [video-server.util :refer :all])
  (:import (java.net URLEncoder)
           (java.util Locale)
           (video_server.model Container Video)))

(defn encoded-url
  "Returns an encoded url for the file (and folder) that can be used
  by clients to access the file."
  [url file]
  (let [filename (URLEncoder/encode (.getName file) "UTF-8")]
    (str url "/" (str/replace filename "+" "%20"))))

(defn mimetype
  "Returns a mimetype based on the file metadata or extension."
  [file & [info]]
  (condp #(.endsWith %2 %1) (.getName file)
    ".mp4" "video/mp4"
    ".m4v" "video/mp4"
    ".mkv" "video/x-matroska"
    ".vtt" "text/vtt"
    ".srt" "application/x-subrip"
    nil))

(def locales (into {} (map #(vector (.getISO3Language %) %) (map #(Locale. %) (Locale/getISOLanguages)))))

(defn modified
  "Returns the modified time of the oldest container."
  [video]
  (apply min (map :modified (:containers video))))

(defn last-modified
  "Returns the last modified time of all the containers."
  [video]
  (apply max (map :modified (:containers video))))

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
  [file info url]
  (let [video (video-stream info)
        audio (audio-streams info)
        subtitles (subtitle-streams info)
        width (parse-long (:width video))
        height (parse-long (:height video))
        url (encoded-url url file)
        fields {:filename (.getName file)
                :language (audio-language info)
                :size (parse-long (-> info :format :size))
                :bitrate (parse-long (-> info :format :bit_rate))
                :width width
                :height height
                :dimension (video-dimension width height)
                :video (video-desc (:codec_name video))
                :audio (str/join ", " (map #(audio-desc (:codec_name %)) audio))
                :modified (.lastModified file)
                :url url
                :mimetype (mimetype file info)}]
    (make-record Container fields)))

(defn video-title
  "Returns the video title based on the metadata or filename."
  [container info]
  (or (-> info :format :title)
      (file/title-info (-> container :filename file/file-base))))

(defn video-record
  "Returns a new video record with the specified container."
  [container info]
  (let [duration (parse-double (-> info :format :duration))]
    (make-record Video (merge {:duration duration :containers (list container)}
                              (video-title container info)))))

