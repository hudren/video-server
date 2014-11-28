;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.encoder
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [video-server.file :as file]
            [video-server.format :as format]
            [video-server.util :refer :all]))

(def ^:dynamic *fake-encode* false)

(def ffmpeg-format {:mkv "matroska" :mp4 "mp4" :m4v "mp4"})

(defn encode
  "Executes the specified command unless *fake-encode* is true."
  [& cmd]
  (if-not *fake-encode*
    (apply exec cmd)
    (let [args (->> cmd flatten (remove nil?) (map str))]
      (log/info "FAKE ENCODE" (str/join " " args))
      {:exit 0})))

(defn video-info
  "Executes ffprobe to extract metadata from the video file."
  [file]
  (let [exec (exec "ffprobe" "-v" "quiet" "-print_format" "json"
                   "-show_format" "-show_streams" (.getCanonicalPath file))]
    (when (zero? (:exit exec))
      (json/read-str (:out exec) :key-fn keyword))))

(defn video-stream
  "Returns the video stream metadata."
  [info]
  (first (filter #(= "video" (:codec_type %)) (:streams info))))

(defn audio-streams
  "Returns a sequence of audio stream metadata."
  [info]
  (filter #(= "audio" (:codec_type %)) (:streams info)))

(defn container-to-encode
  "Selects the best (highest quality) container to encode."
  [containers]
  (last (sort-by :size containers)))

(defn smallest-encoded-size
  "Returns the size of the smallest (last encoded) container."
  [video]
  (when-let [container (second (reverse (sort-by :size (:containers video))))]
    ({1920 :1080 1280 :720 640 :480} (:width container))))

(defn width-for-size
  "Returns the video width for the given size."
  [size]
  ({:1080 1920 :720 1280 :480 640} size))

(defn smaller-size
  "Returns the next smaller size."
  [size]
  ({:1080 :720 :720 :480} size))

(defn crop
  "Performs crop detection, returning the filter argument or nil."
  [file]
  (let [output (:err (exec "ffmpeg" "-y" "-ss" 300 "-i" (.getCanonicalPath file) "-f" "matroska"
                           "-t" 120 "-an" "-sn" "-vf" "cropdetect=24:2:0"
                           "-crf" 51 "-preset" "ultrafast" "/dev/null"))]
    (when-let [crop (-> (re-seq #"crop=[0-9:]*" output) distinct sort last)]
      (when-not (.endsWith crop ":0:0") crop))))

(defn scale
  "Returns a filter argument for scaling down to the preferred size, or nil."
  [container size crop]
  (let [new-width (width-for-size size)]
    (when (< new-width (:width container))
      (let [[width height] (if crop
                             (parse-ints crop)
                             [(:width container) (:height container)])
            new-height (int (* height (/ new-width width)))
            new-height (if (even? new-height) new-height (dec new-height))]
        (str "scale=" new-width ":" new-height)))))

(defn encode-video?
  "Returns whether the video stream should be transcoded."
  [video size]
  true) ; TODO: implement some logic here

(defn video-options
  "Returns the ffmpeg options for encoding / copying the video
  stream."
  [info size]
  (let [video (video-stream info)
        quality (if (= size :480) 18 19)]
    (if (encode-video? video size)
      ["-map" (str "0:" (:index video)) "-c:v" "libx264" "-crf" quality "-profile:v" "high" "-level" 41]
      ["-map" (str "0:" (:index video)) "-c:v" "copy"])))

(defn crop-resize-options
  "Returns the ffmpeg options for cropping and/or resizing the video."
  [file container size]
  (let [crop (crop file)
        size (scale container size crop)
        args (str/join "," (remove str/blank? (list crop size)))]
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
  [info]
  (let [audio (audio-streams info)]
    (into (aac-options audio) (ac3-options audio))))

(defn subtitle-options
  "Returns the ffmpeg options for copying the subtitle streams."
  [info fmt]
  (when (= fmt :mkv) ; TODO: filter subtitles based on format
    (flatten
      (for [stream (:streams info)]
        (when (= "subtitle" (:codec_type stream))
          ["-map" (str "0:" (:index stream)) "-c:s" "copy"])))))

(defn output-size
  "Returns the [width height] of the container based on the video
  filter options."
  [container options]
  (cond
    (str/blank? options) [(:width container) (:height container)]
    (.contains options "scale=") (find-ints #"scale=(\d+):(\d+)" options)
    (.contains options "crop=") (find-ints #"crop=(\d+):(\d+)" options)
    :default [(:width container) (:height container)]))

(defn output-file
  "Returns the File representing the encoder output."
  [video file fmt width height]
  (let [ext (str "." (name fmt))
        filename (file/video-filename video ext)
        output (io/file (.getParent file) filename)]
    (if (.exists output)
      (let [filename (file/video-filename video ext width height)
            output (io/file (.getParent file) filename)]
        (if (.exists output)
          (let [filename (file/video-filename video ext width height (format/video-dimension width height))]
            (io/file (.getParent file) filename))
          output))
      output)))

(defn encode-video
  "Transcodes the video suitable for downloading and casting."
  [folder video fmt size]
  (log/info "encoding video" (:title video))
  (let [size (or (smaller-size (smallest-encoded-size video)) size)
        container (container-to-encode (:containers video))
        file (io/file (:file folder) (:filename container))
        info (video-info file)
        crop-resize (crop-resize-options file container size)
        [width height] (output-size container (last crop-resize))
        output (output-file video file fmt width height)
        cmd ["ffmpeg" "-i" (.getCanonicalPath file)
             (video-options info size) crop-resize
             (audio-options info) (subtitle-options info fmt)
             "-f" (ffmpeg-format fmt) (.getCanonicalPath output)]]
    (log/info "encoding" (:title video) "into" (.getName output))
    (let [exec (encode cmd)]
      (if (zero? (:exit exec))
        (log/info "encoding was successful")
        (do
          (log/error "encoding failed:" \newline cmd \newline exec)
          (when (.exists output) (.delete output)))))))

(defn encode-subtitle
  "Encodes a single subtitle file into WebVTT format."
  [file]
  (let [filename (.getCanonicalPath file)]
    (when-not (.endsWith filename ".vtt")
      (log/info "encoding subtitle" filename)
      (let [cmd ["ffmpeg" "-i" filename (file/replace-ext filename ".vtt")]]
        (log/debug "executing" (str/join " " cmd))
        (exec cmd)))))

(defn encode-subtitles
  "Encodes the subtitle files for a particular video."
  [folder video]
  (doseq [subtitle (:subtitles video)]
    (encode-subtitle (io/file folder (:filename subtitle)))))

