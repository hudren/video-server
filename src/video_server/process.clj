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
            [video-server.file :refer [subtitle? video?]]
            [video-server.encoder :refer [encode-subtitle encode-subtitles encode-video extract-thumbnail]]
            [video-server.library :refer [add-info video-key video-for-file video-for-key]]
            [video-server.metadata :refer [retrieve-metadata]])
  (:import (java.io File)))

(def process-chan (chan 100))

(defn process-file
  "Enqueues a file or video for processing."
  [folder file-or-video]
  (log/debug "queueing" (str file-or-video))
  (go (>! process-chan [folder (video-key file-or-video)])))

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
  (log/debug "processing video" (str video))
  (when (should-encode-video? video)
    (encode-video folder video fmt size))
  (when (should-encode-subtitles? video)
    (encode-subtitles folder video)))

(defn start-processing
  "Processes enqueued files."
  [encode? metadata? fmt size]
  (log/debug "starting file processing")
  (go (while true
        (let [[folder key] (<! process-chan)]
          (when-let [video (video-for-key folder key)]
            (try (when metadata?
                   (when-not (:info video)
                     (when-let [info (retrieve-metadata folder video)]
                       (add-info folder video info)))
                   (when-not (:thumb video)
                     (extract-thumbnail folder video)))
                 (when encode? (process-video folder video fmt size))
                 (catch Exception e (log/error e "error processing video" (str video)))))))))

