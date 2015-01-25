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
            [video-server.ffmpeg :refer [encode-cmd filter-video video-info]]
            [video-server.file :refer [file-type fullpath replace-ext video-filename]]
            [video-server.format :refer [video-dimension video-size video-width]]
            [video-server.util :refer :all]
            [video-server.video :refer [audio-streams subtitle-streams video-stream web-playback?]])
  (:import (java.io File)))

(def ^:dynamic *fake-encode* false)

(defn- encode
  "Executes the specified command unless *fake-encode* is true."
  [& cmd]
  (if-not *fake-encode*
    (apply exec cmd)
    (let [args (->> cmd flatten (remove nil?) (map str))]
      (log/info "FAKE ENCODE" (str/join " " args))
      {:exit 0})))

(defn original?
  "Returns whether the container may be an original rip."
  [container]
  (not (web-playback? container)))

(defn source-container
  "Returns the source container."
  [containers]
  (last (sort-by :size containers)))

(defn container-to-encode
  "Selects the best (highest quality) container to encode."
  [containers size]
  (let [source (source-container containers)
        same (filter #(= (video-size (:width %)) size) (remove #(= % source) containers))]
    (or (first same) source)))

(defn can-source?
  "Returns true if the first size is bigger or equal to the second."
  [source-size dest-size]
  (>= (parse-long (name source-size)) (parse-long (name dest-size))))

(defn min-size
  "Returns the minimum of the two sizes."
  [s1 s2]
  (let [s2 (or s2 s1)]
    (if (< (parse-long (name s1)) (parse-long (name s2))) s1 s2)))

(defn encode-size
  "Returns the minimum of the source and target sizes."
  [video size]
  (let [container (container-to-encode (:containers video) size)]
    (min-size (-> container :width video-size) size)))

(defn encode-spec
  "Returns the specification for an encoding job."
  [folder video fmt size]
  (when-let [container (container-to-encode (:containers video) size)]
    (let [file (io/file (:file folder) (:path container))
          info (video-info file)
          source (source-container (:containers video))]
      {:format fmt
       :size size
       :width (:width container)
       :height (:height container)
       :target-width (min (:width container) (video-width size))
       :folder folder
       :file file
       :input (fullpath file)
       :info info
       :video video
       :source? (= container source)
       :original? (and (= container source) (original? container))
       :video-stream (video-stream info)
       :audio-streams (audio-streams info)
       :subtitle-streams (subtitle-streams info)})))

(defn output-file
  "Returns the File representing the encoder output."
  [{:keys [video ^File file format size scale width height] :as spec}]
  (let [ext (str "." (name format))
        filename (video-filename video ext (when scale size))
        output (io/file (.getParent file) filename)]
    (if (.exists output)
      (let [filename (video-filename video ext (when scale size) (video-dimension width height))]
        (io/file (.getParent file) filename))
      output)))

(defn output-options
  "Returns the spec with an appropriate output filename."
  [spec]
  (assoc spec :output (fullpath (output-file spec))))

(defn video-encode-spec
  "Returns a spec for encoding a video."
  [folder video fmt size]
  (when-let [spec (encode-spec folder video fmt size)]
    (-> spec filter-video output-options)))

(defn encode-video
  "Transcodes the video suitable for downloading and casting."
  [spec]
  (log/info "encoding video" (str (:video spec)))
  (let [output (:output spec)
        cmd (encode-cmd spec)]
    (log/info "encoding into" output)
    (let [exec (encode cmd)]
      (if (zero? (:exit exec))
        (do
          (log/info "encoding was successful")
          spec)
        (do
          (log/error "encoding failed:" \newline cmd \newline exec)
          (io/delete-file output true)
          (assoc spec :error {:cmd cmd :exec exec}))))))

(defn encode-subtitle
  "Encodes a single subtitle file into WebVTT format."
  [file]
  (when-not (= (file-type file) :vtt)
    (let [filename (fullpath file)]
      (log/info "encoding subtitle" filename)
      (let [cmd ["ffmpeg" "-i" filename (replace-ext filename ".vtt")]]
        (log/debug "executing" (str/join " " cmd))
        (exec cmd)))))

(defn encode-subtitles
  "Encodes the subtitle files for a particular video."
  [folder video]
  (doseq [subtitle (:subtitles video)]
    (encode-subtitle (io/file (:file folder) (:path subtitle)))))

(def mkvtools (delay (and (-> ["which" "mkvmerge"] exec :exit zero?)
                          (-> ["which" "mkvextract"] exec :exit zero?))))

(defn mkv-info
  "Returns the mkv info by calling mkvmerge."
  [file]
  (when @mkvtools
    (try (let [exec (exec ["mkvmerge" "-i" (fullpath file)])]
           (when (zero? (:exit exec))
             (:out exec)))
         (catch Exception e (log/error e "error extracting info")))))

(defn mkv-extract
  "Extracts the embedded attachment into a separate file."
  [file id output]
  (try (exec ["mkvextract" "attachments" (fullpath file) (str id ":" (fullpath output))])
       (catch Exception e (log/error e "error extracting attactment"))))

(defn extract-thumbnail
  "Extracts the thumbnail image from the matroska video."
  [folder video]
  (when-let [container (source-container (:containers video))]
    (let [file (io/file (:file folder) (:path container))]
      (when (= (file-type file) :mkv)
        (when-let [info (mkv-info file)]
          (when-let [id (second (re-find #"Attachment ID (\d+):.*file name 'cover\.jpg'" info))]
            (let [thumb (io/file (:file folder) (str (:title video) ".thumb.jpg"))]
              (mkv-extract file id thumb))))))))

