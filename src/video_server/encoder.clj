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
            [video-server.ffmpeg :refer [encode-cmd filter-video metadata? video-info]]
            [video-server.file :refer [file-type fullpath replace-ext video-filename]]
            [video-server.format :refer [video-dimension video-size video-width]]
            [video-server.util :refer :all]
            [video-server.video :refer [audio-streams container-track subtitle-streams video-stream web-playback?]])
  (:import (java.io File)))

(def ^:dynamic *fake-encode* false)

(defn installed?
  "Returns whether the encoder is minimally installed."
  []
  @metadata?)

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
          source (source-container (:containers video))
          vs (video-stream info)
          as (audio-streams info)]
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
       :video-stream vs
       :audio-streams as
       :subtitle-streams (subtitle-streams info)
       :fps (ratio (:avg_frame_rate vs))
       :sar (ratio (:sample_aspect_ratio vs))
       :dar (ratio (:display_aspect_ratio vs))
       :square? (= (:sample_aspect_ratio vs) "1:1")
       :foreign? (not= (:language container) "English")})))

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

(def mkvpropedit (delay (exec? "mkvpropedit")))

(defn relative-track-index
  "Returns the relative track index (for the type) starting with 1."
  ([streams track]
   (loop [index 1 streams streams]
     (when-let [stream (first streams)]
       (if (= (:index stream) track)
         index
         (recur (inc index) (next streams))))))
  ([streams codec_type track]
   (relative-track-index (filter #(= (:codec_type %) codec_type) streams) track)))

(defn default-audio
  "Returns the default audio stream."
  [audio-streams]
  (or (first (filter #(= (-> % :disposition :default) 1) audio-streams))
      (first audio-streams)))

(defn clear-subtitles
  "Clears the default flag on same language, non-forced subtitles."
  [file]
  (when (and (= (file-type file) :mkv) @mkvpropedit)
    (log/debug "clearing subtitles")
    (let [info (video-info (io/file file))
          subtitles (subtitle-streams info)
          lang (-> (default-audio (audio-streams info)) :tags :language)
          actions (atom [])]
      (doseq [subtitle subtitles]
        (when (and (= (-> subtitle :disposition :default) 1)
                   (= (-> subtitle :disposition :forced) 0)
                   (= (-> subtitle :tags :language) lang))
          (log/trace "clearing default flag on subtitle track" (:index subtitle))
          (swap! actions conj
                 "--edit" (str "track:s" (relative-track-index subtitles (:index subtitle)))
                 "--set" "flag-default=0")))
      (when (seq @actions)
        (let [cmd ["mkvpropedit" file @actions]
              exec (exec cmd)]
          (when-not (zero? (:exit exec))
            (log/warn "clearing subtitle tracks failed:" \newline cmd \newline exec)))))))

(defn set-default-track
  "Sets the default video, audio or subtitle track."
  [file track]
  (when (and (= (file-type file) :mkv) @mkvpropedit)
    (let [info (video-info (io/file file))
          streams (:streams info)
          default (container-track info track)
          codec (:codec_type default)
          actions (atom [])]
      (log/debug "setting default" codec "track" track)
      (when (get-in default [:disposition :default])
        (doseq [[index stream] (map-indexed vector streams)]
          (let [value (if (= index track) 1 0)]
            (when (and (= (:codec_type stream) codec)
                       (not= (get-in stream [:disposition :default]) value))
              (log/trace "changing default flag on track" (:index stream) "to" value)
              (swap! actions conj
                     "--edit" (str "track:" (first codec) (relative-track-index streams codec (:index stream)))
                     "--set" (str "flag-default=" value))))))
      (when (seq @actions)
        (let [cmd ["mkvpropedit" file @actions]
              exec (exec cmd)]
          (when-not (zero? (:exit exec))
            (log/warn "changing default track failed:" \newline cmd \newline exec)))))))

(def mkclean (delay (exec? "mkclean")))

(defn clean
  "Cleans the encoded output file."
  [spec]
  (let [output (:output spec)]
    (when (and (= (file-type output) :mkv) @mkclean)
      (log/debug "cleaning" output)
      (let [out (io/file output)
            temp (io/file (replace-ext output ".tmp"))]
        (.renameTo out temp)
        (let [cmd ["mkclean" "--optimize" temp out]
              exec (exec cmd)]
          (if (zero? (:exit exec))
            (io/delete-file temp false)
            (do (log/warn "cleaning failed:" \newline cmd \newline exec)
                (io/delete-file out false)
                (.renameTo temp out))))))))

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
          (clear-subtitles (:output spec))
          (clean spec)
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

(def mkvtools (delay (and (exec? "mkvmerge") (exec? "mkvextract"))))

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

