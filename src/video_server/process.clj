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
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [chan go thread <! <!! >!]]
            [video-server.file :refer [file-type subtitle? video?]]
            [video-server.encoder :refer [*fake-encode* can-source? container-size container-to-encode encode-size encode-subtitles encode-video extract-thumbnail video-encode-spec]]
            [video-server.library :refer [add-info add-video title-for-key video-key video-for-key]]
            [video-server.metadata :refer [retrieve-metadata]]
            [video-server.util :refer :all]
            [video-server.video :refer [can-cast? can-download? web-playback?]]))

(def ^:const fetch-threads 4)

(def ^:private process-chan (chan 500))
(def ^:private title-chan (chan 500))
(def ^:private encoder-chan (chan 100))

(defn should-process-file?
  "Determines whether the file or video should be queued for
  processing."
  [file-or-video]
  (or (record? file-or-video)
      ((some-fn video? subtitle?) file-or-video)))

(defn process-file
  "Enqueues a file or video for processing."
  [folder file-or-video]
  (when (should-process-file? file-or-video)
    (log/debug "queueing" (str file-or-video))
    (go (>! process-chan [folder (video-key file-or-video)]))))

(defn process-title
  "Enqueues the title for processing."
  [folder title]
  (go (>! title-chan [folder (video-key title)])))

(defn process-encode
  "Enqueues a spec for transcoding a video."
  [spec]
  (go (>! encoder-chan spec)))

(defn has-fmt-size?
  "Returns whether the video has a casting-compatible container
  matching the specified format and size."
  [video fmt size]
  (let [containers (filter web-playback? (:containers video))]
    (some #(and (= (file-type (:path %)) fmt) (= size (container-size %))) containers)))

(defn should-encode-video?
  "Returns whether the video should be encoded for downloading or
  casting."
  ([video]
   (not (and (can-download? video) (can-cast? video))))
  ([video fmt size]
   (and (not (has-fmt-size? video fmt size))
        (can-source? (container-size (container-to-encode (:containers video))) size))))

(defn should-encode-subtitles?
  "Returns whether the subtitles need to be transcoded for casting."
  [video]
  (and (not-any? #(= (:mimetype %) "text/vtt") (:subtitles video))
       (some #(not= (:mimetype %) "text/vtt") (:subtitles video))))

(defn fetch-info
  "Fetches metadata for the title and extracts thumbnails from the
  video."
  [folder title]
  )

(defn- encode
  "Creates an encoding spec and queues it for processing."
  [folder video fmt size]
  (when-let [spec (video-encode-spec folder video fmt size)]
    (process-encode spec)))

(defn process-video
  "Conditionally encodes the video and/or subtitles."
  [folder video fmt size options]
  (log/debug "processing video" (str video))
  (when (should-encode-subtitles? video)
    (encode-subtitles folder video))
  (if (should-encode-video? video)
    (encode folder video fmt (encode-size video size))
    (when-not *fake-encode*
      (loop [options options]
        (when (seq options)
          (let [{fmt :format size :size} (first options)
                size (encode-size video size)]
            (log/trace "checking" fmt size "for" (:title video))
            (if (should-encode-video? video fmt size)
              (encode folder video fmt size)
              (recur (rest options)))))))))

(defn start-fetching
  [fetch?]
  (dotimes [_ fetch-threads]
    (thread
      (while true
        (let [[folder key] (<!! title-chan)
              options (:options folder)]
          (log/trace "fetching" folder key)
          (when (get options :fetch fetch?)
            (when-let [title (title-for-key key)]
              (try (when-let [info (retrieve-metadata title)]
                     (add-info folder title info))
                   (catch Exception e (log/error e "error fetching metadata" (str title)))))))))))

(defn start-encoding
  "Processes requests in the encoder channel."
  []
  (go
    (while true
      (let [spec (<! encoder-chan)]
        (log/trace "encoding" spec)
        (encode-video spec)
        (add-video (:folder spec) (io/file (:output spec)))
        (process-file (:folder spec) (:video spec))))))

(defn start-processing
  "Processes enqueued files."
  [encode? fetch? fmt size]
  (log/debug "starting file processing")
  (start-fetching fetch?)
  (start-encoding)
  (go
    (while true
      (let [[folder key] (<! process-chan)
            options (:options folder)]
        (log/trace "processing" folder key)
        (when-let [video (video-for-key folder key)]
          (try (when (get options :fetch fetch?)
                 (when-let [title (title-for-key key)]
                   (when-not (:thumb title)
                     (extract-thumbnail folder video))))
               (when (get options :encode encode?)
                 (process-video folder video
                                (get options :format fmt) (get options :size size)
                                (-> folder :options :encoder :containers)))
               (catch Exception e (log/error e "error processing video" (str video)))))))))

