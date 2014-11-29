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
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [video-server.ffmpeg :as ffmpeg]
            [video-server.file :as file]
            [video-server.format :as format]
            [video-server.util :refer :all]
            [video-server.video :as video]))

(def ^:dynamic *fake-encode* false)

(defn- encode
  "Executes the specified command unless *fake-encode* is true."
  [& cmd]
  (if-not *fake-encode*
    (apply exec cmd)
    (let [args (->> cmd flatten (remove nil?) (map str))]
      (log/info "FAKE ENCODE" (str/join " " args))
      {:exit 0})))

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

(defn encode-spec
  "Returns the specification for an encoding job."
  [folder video fmt size]
  (let [container (container-to-encode (:containers video))
        file (io/file (:file folder) (:filename container))
        info (ffmpeg/video-info file)
        size (or (smaller-size (smallest-encoded-size video)) size)]
    {:format fmt
     :size size
     :width (:width container)
     :height (:height container)
     :target-width (width-for-size size)
     :file file
     :input (.getCanonicalPath file)
     :info info
     :video video
     :video-stream (video/video-stream info)
     :audio-streams (video/audio-streams info)
     :subtitle-streams (video/subtitle-streams info)}))

(defn output-file
  "Returns the File representing the encoder output."
  [{:keys [video file format width height] :as spec}]
  (let [ext (str "." (name format))
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

(defn output-options
  "Returns the spec with an appropriate output filename."
  [spec]
  (assoc spec :output (.getCanonicalPath (output-file spec))))

(defn encode-video
  "Transcodes the video suitable for downloading and casting."
  [folder video fmt size]
  (log/info "encoding video" (:title video))
  (let [spec (encode-spec folder video fmt size)
        spec (ffmpeg/filter-video spec)
        spec (output-options spec)
        output (:output spec)
        cmd (ffmpeg/encode-cmd spec)]
    (log/info "encoding" (:title video) "into" output)
    (let [exec (encode cmd)]
      (if (zero? (:exit exec))
        (log/info "encoding was successful")
        (do
          (log/error "encoding failed:" \newline cmd \newline exec)
          (io/delete-file output true))))))

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

