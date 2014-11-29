;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.ffmpeg
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [video-server.util :refer :all]))

(def ffmpeg-format {:mkv "matroska" :mp4 "mp4" :m4v "mp4"})

(defn video-info
  "Executes ffprobe to extract metadata from the video file."
  [file]
  (let [exec (exec "ffprobe" "-v" "quiet" "-print_format" "json"
                   "-show_format" "-show_streams" (.getCanonicalPath file))]
    (when (zero? (:exit exec))
      (json/read-str (:out exec) :key-fn keyword))))

(defn detect-crop
  "Performs crop detection, returning the filter argument or nil."
  [input]
  (let [output (:err (exec "ffmpeg" "-y" "-ss" 300 "-i" input "-f" "matroska"
                           "-t" 120 "-an" "-sn" "-vf" "cropdetect=24:2:0"
                           "-crf" 51 "-preset" "ultrafast" "/dev/null"))]
    (when-let [crop (-> (re-seq #"crop=[0-9:]*" output) distinct sort last)]
      (when-not (.endsWith crop ":0:0") crop))))

(defn crop
  "Returns an updated spec with crop information."
  [spec]
  (merge spec
         (when-let [crop (detect-crop (:input spec))]
           (let [[width height] (parse-ints crop)]
             {:crop crop :width width :height height}))))

(defn scale
  "Returns an updated spec with scaling information."
  [spec]
  (merge spec
         (let [width (:width spec)
               new-width (:target-width spec)]
           (when (< new-width width)
             (let [height (:height spec)
                   new-height (int (* height (/ new-width width)))
                   new-height (if (even? new-height) new-height (dec new-height))]
               {:scale (str "scale=" new-width ":" new-height) :width new-width :height new-height})))))

(defn filter-video
  "Returns an updated spec with video filters applied (crop and scale)."
  [spec]
  (-> spec crop scale))

(defn encode-video?
  "Returns whether the video stream should be transcoded."
  [video size]
  true) ; TODO: implement some logic here

(defn video-options
  "Returns the ffmpeg options for encoding / copying the video
  stream."
  [{:keys [video-stream size]}]
  (let [quality (if (= size :480) 18 19)]
    (if (encode-video? video-stream size)
      ["-map" (str "0:" (:index video-stream)) "-c:v" "libx264" "-crf" quality "-profile:v" "high" "-level" 41]
      ["-map" (str "0:" (:index video-stream)) "-c:v" "copy"])))

(defn crop-resize-options
  "Returns the ffmpeg options for cropping and/or resizing the video."
  [{:keys [crop scale]}]
  (let [args (str/join "," (remove str/blank? [crop scale]))]
    (when-not (str/blank? args)
      ["-vf" args])))

(defn audio-stream
  "Returns the audio stream for the given codec, or nil."
  [audio codec]
  (first (filter #(= codec (:codec_name %)) audio)))

(defn audio-to-encode
  "Returns the best audio stream to use for the encoding source."
  [audio]
  (first audio)) ; TODO: check bitrate and language

(defn aac-options
  "Returns the ffmpeg options for encoding / copying the aac audio
  stream."
  [audio]
  (if-let [aac (audio-stream audio "aac")]
    ["-map" (str "0:" (:index aac)) "-c:a:0" "copy"]
    ["-map" (str "0:" (:index (audio-to-encode audio))) "-c:a:0" "aac" "-q:a" 100 "-ac:a:0" 2 "-strict" "-2"]))

(defn ac3-options
  "Returns the ffmpeg options for encoding / copying the ac3 audio
  stream."
  [audio]
  (if-let [ac3 (audio-stream audio "ac3")]
    ["-map" (str "0:" (:index ac3)) "-c:a:1" "copy"]
    ["-map" (str "0:" (:index (audio-to-encode audio))) "-c:a:1" "ac3" "-b:0:1" "640k"]))

(defn audio-options
  "Returns the ffmpeg options for encoding / copying the audio
  streams."
  [{:keys [audio-streams]}]
  (into (aac-options audio-streams) (ac3-options audio-streams)))

(defn subtitle-options
  "Returns the ffmpeg options for copying the subtitle streams."
  [{:keys [format subtitle-streams]}]
  (when (= format :mkv) ; TODO: filter subtitles based on format
    (for [stream subtitle-streams]
      ["-map" (str "0:" (:index stream)) "-c:s" "copy"])))

(defn encode-cmd
  "Returns the ffmpeg command for encoding a video."
  [spec]
  ["ffmpeg" "-i" (:input spec)
   (video-options spec)
   (crop-resize-options spec)
   (audio-options spec)
   (subtitle-options spec)
   "-f" (ffmpeg-format (:format spec))
   (:output spec)])

