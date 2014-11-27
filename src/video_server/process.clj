;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.process
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :refer [chan go <! >!]]
            [video-server.file :as file]
            [video-server.encoder :as encoder]
            [video-server.library :as library])
  (:import (java.io File)))

(def process-chan (chan 100))

(defn process-file
  "Enqueues a file or video for processing."
  [folder file-or-video]
  (log/trace "queueing" (if (instance? File file-or-video) (str file-or-video) (:title file-or-video)))
  (go (>! process-chan [folder file-or-video])))

(defn can-download?
  "Returns whether the file is small enough for downloading."
  [video]
  (some #(< (:size %) 4187593114) (:containers video)))

(defn can-cast?
  "Returns whether the video is compatible for casting."
  [video]
  (some #(and (.contains (:video %) "H.264") (.contains (:audio %) "AAC")) (:containers video)))

(defn should-encode-video?
  "Returns whether the video should be encoded for downloading or
  casting."
  [video]
  (not (and (can-download? video) (can-cast? video))))

(defn should-encode-subtitles?
  "Returns whether the subtitles need to be transcoded for casting."
  [video]
  (and (not-any? #(= (:mimetype %) "text/vtt") (:subtitles video))
       (some #(not= (:mimetype %) "text/vtt") (:subtitles video))))

(defn process-video
  "Conditionally encodes the video and/or subtitles."
  [folder video fmt size]
  (log/debug "processing video" (:title video))
  (when (should-encode-video? video)
    (encoder/encode-video folder video fmt size))
  (when (should-encode-subtitles? video)
    (encoder/encode-subtitles video)))

(defn process-subtitle
  "Conditionally encodes the subtitle file."
  [folder file]
  (log/debug "processing subtitle" (str file))
  (let [video (library/video-for-file folder file)]
    (when (should-encode-subtitles? video)
      (encoder/encode-subtitle file))))

(defn start-processing
  "Processes enqueued files."
  [encode? fmt size]
  (log/debug "starting file processing")
  (go (while true
        (let [[folder file-or-video] (<! process-chan)]
          (log/trace "processing" (str file-or-video))
          (try (when encode?
                 (if (instance? File file-or-video)
                   (cond
                     (file/video? file-or-video) (process-video folder (library/video-for-file folder file-or-video) fmt size)
                     (file/subtitles? file-or-video) (process-subtitle folder file-or-video))
                   (process-video folder file-or-video fmt size)))
               (catch Exception e (log/error e "error processing video" (str file-or-video))))))))

