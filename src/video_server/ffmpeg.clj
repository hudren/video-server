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
            [video-server.file :refer [fullpath]]
            [video-server.format :refer [audio-title]]
            [video-server.util :refer :all]))

(def ffmpeg-format {:mkv "matroska" :mp4 "mp4" :m4v "mp4"})

(defn video-info
  "Executes ffprobe to extract metadata from the video file."
  [file]
  (let [exec (exec "ffprobe" "-v" "quiet" "-print_format" "json"
                   "-show_format" "-show_streams" (fullpath file))]
    (when (zero? (:exit exec))
      (json/read-str (:out exec) :key-fn (comp keyword str/lower-case)))))

(defn detect-interlace
  "Performs interlace detection, returning true if present."
  [input]
  (let [output (:err (exec "ffmpeg" "-y" "-ss" 300 "-i" input "-f" "matroska"
                           "-t" 120 "-an" "-sn" "-vf" "idet" "/dev/null"))]
    (when-let [frames (re-find #"Multi frame detection:.*" output)]
      (let [[top bottom prog und] (parse-ints frames)]
        (> (+ top bottom) (/ (+ top bottom prog und) 10))))))

(defn detect-crop-section
  "Performs crop detection for the specified period."
  [input start duration]
  (let [output (:err (exec "ffmpeg" "-y" "-ss" start "-i" input "-f" "matroska"
                           "-t" duration "-an" "-sn" "-vf" "cropdetect=24:2:0"
                           "-crf" 51 "-preset" "ultrafast" "/dev/null"))]
    (distinct (re-seq #"crop=[0-9:]+" output))))

(defn- crop-size
  "Returns the number of pixels in the crop detection."
  [crop]
  (let [[width height] (parse-ints crop)]
    (* width height)))

(defn detect-crop
  "Performs crop detection, returning the filter argument or nil."
  [input]
  (let [crops (for [start [300 600 900 1200]] (detect-crop-section input start 30))]
    (when-let [^String crop (->> crops flatten distinct (sort-by crop-size) last)]
      (when-not (.endsWith crop ":0:0") crop))))

(defn deinterlace
  "Returns an updated spec with option to deinterlace the video."
  [spec]
  (merge spec
         (when (detect-interlace (:input spec))
           {:deinterlace "pullup,dejudder,idet,yadif=deint=interlaced:mode=1"})))

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
  (if (:source? spec)
    (-> spec deinterlace crop scale)
    (scale spec)))

(defn encode-video?
  "Returns whether the video stream should be transcoded."
  [{:keys [video-stream source? crop scale]}]
  (or source? crop scale (not= (:codec_name video-stream) "h264")))

(defn video-options
  "Returns the ffmpeg options for encoding / copying the video
  stream."
  [{:keys [video-stream source? size crop scale] :as spec}]
  (let [quality (if (= size :480) 18 19)]
    (if (encode-video? spec)
      ["-map" (str "0:" (:index video-stream)) "-c:v" "libx264" "-crf" quality "-profile:v" "high" "-level" 41]
      ["-map" (str "0:" (:index video-stream)) "-c:v" "copy"])))

(defn video-filter-options
  "Returns the ffmpeg options for the video filter (de-interlace,
  crop, resize)."
  [{:keys [deinterlace crop scale]}]
  (let [args (str/join "," (remove str/blank? [deinterlace crop scale]))]
    (when-not (str/blank? args)
      ["-vf" args])))

(defn audio-to-encode
  "Returns the best audio stream to use for the target codec."
  [audio-streams codec]
  (let [audio (reverse (sort-by #(parse-long (:bit_rate %)) audio-streams))]
    (or (first (filter #(= (-> % :disposition :default) 1) audio))
        (first audio))))

(defn audio-codec-options
  "Returns the encoding options for the specified codec."
  [index codec]
  (case codec
    "aac" ["-q:a" 100 (str "-ac:a:" index) 2 "-strict" "-2"]
    "ac3" [(str "-b:0:" index) "640k"]
    nil))

(defn audio-title-options
  "Returns the encoding options for adding a title to an audio track."
  [index codec lang chans]
  [(str "-metadata:s:a:" index) (str "title=" (audio-title codec lang chans))])

(defn audio-encoder-options
  "Returns the ffmpeg options for encoding / copying the aac audio
  stream."
  [audio-streams index codec]
  (let [audio (audio-to-encode audio-streams codec)
        exists (= (:codec_name audio) codec)]
    (conj ["-map" (str "0:" (:index audio))
           (str "-c:a:" index) (if exists "copy" codec)]
          (when-not exists (audio-codec-options index codec))
          (audio-title-options index codec (-> audio :tags :language) (if (= codec "aac") 2 (:channels audio))))))

(defn audio-options
  "Returns the ffmpeg options for encoding / copying the audio
  streams."
  [{:keys [audio-streams]}]
  (into (audio-encoder-options audio-streams 0 "aac")
        (when-let [audio (audio-to-encode audio-streams "ac3")]
          (when (> (:channels audio) 2)
            (audio-encoder-options audio-streams 1 "ac3")))))

(defn subtitle-options
  "Returns the ffmpeg options for copying the subtitle streams."
  [{:keys [format subtitle-streams]}]
  (when (= format :mkv) ; TODO: filter subtitles based on format
    (for [stream subtitle-streams]
      ["-map" (str "0:" (:index stream)) "-c:s" "copy"])))

(defn format-options
  "Returns the ffmpeg options for the type of output file."
  [{:keys [format]}]
  (when (#{:m4v :mp4} format)
    ["-movflags" "faststart"]))

(defn encode-cmd
  "Returns the ffmpeg command for encoding a video."
  [spec]
  ["ffmpeg" "-i" (:input spec)
   (video-options spec)
   (video-filter-options spec)
   (audio-options spec)
   (subtitle-options spec)
   "-f" (ffmpeg-format (:format spec))
   (format-options spec)
   "-threads" 0
   (:output spec)])

