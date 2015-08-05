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
            [clojure.core.async :refer [chan go go-loop thread <! <!! >!]]
            [video-server.file :refer [file-type subtitle? video?]]
            [video-server.encoder :refer [*fake-encode* can-source? container-to-encode encode-size encode-subtitles encode-video
                                          extract-thumbnail video-encode-spec]]
            [video-server.format :refer [video-size]]
            [video-server.library :refer [add-info add-video title-for-key video-key video-for-key]]
            [video-server.metadata :refer [read-metadata retrieve-season-metadata retrieve-metadata]]
            [video-server.title :refer [title-seasons]]
            [video-server.util :refer :all]
            [video-server.video :refer [can-cast? can-download? web-playback?]]))

(def ^:const fetch-threads 4)

(def ^:private process-chan (chan 500))
(def ^:private movie-chan (chan 500))
(def ^:private series-chan (chan 100))
(def ^:private encoder-chan (chan 100))

; Set of Files blocked from processing
(defonce blocked (ref #{}))

(defn- block-file
  "Adds the file to the list of blocked files."
  [file]
  (let [file (.getCanonicalFile (io/file file))]
    (log/trace "blocking" (str file))
    (dosync (alter blocked conj file))))

(defn- unblock-file
  "Removed the files from the list of blocked files."
  [file]
  (let [file (.getCanonicalFile (io/file file))]
    (log/trace "unblocking" (str file))
    (dosync (alter blocked disj file))))

(defn file-blocked?
  [file]
  (let [file (.getCanonicalFile (io/file file))]
    (contains? @blocked file)))

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
  [folder file]
  (let [key (video-key file)
        chan (if (:season key) series-chan movie-chan)]
    (go (>! chan [folder key]))))

(defn process-encode
  "Enqueues a spec for transcoding a video."
  [spec]
  (go (>! encoder-chan spec)))

(defn has-fmt-size?
  "Returns whether the video has a casting-compatible container
  matching the specified format and size."
  [video fmt size]
  (let [containers (filter web-playback? (:containers video))]
    (some #(and (= (file-type (:path %)) fmt) (= size (-> % :width video-size))) containers)))

(defn- next-encode-size
  "Returns the next size for the default encoding."
  [video fmt size]
  (if (has-fmt-size? video fmt size) ({:2160 :1080 :1080 :720} size) size))

(defn should-encode-video?
  "Returns whether the video should be encoded for downloading or
  casting."
  ([video]
   (not (and (can-download? video) (can-cast? video))))
  ([video fmt size]
   (and (not (has-fmt-size? video fmt size))
        (can-source? (-> video :containers (container-to-encode size) :width video-size) size))))

(defn should-encode-subtitles?
  "Returns whether the subtitles need to be transcoded for casting."
  [video]
  (and (not-any? #(= (:mimetype %) "text/vtt") (:subtitles video))
       (some #(not= (:mimetype %) "text/vtt") (:subtitles video))))

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
  (let [size (next-encode-size video fmt size)]
    (if (and size (should-encode-video? video))
      (encode folder video fmt (encode-size video size))
      (when-not *fake-encode*
        (loop [options options]
          (when (seq options)
            (let [{fmt :format size :size} (first options)
                  size (encode-size video size)]
              (log/trace "checking" fmt size "for" (:title video))
              (if (should-encode-video? video fmt size)
                (encode folder video fmt size)
                (recur (rest options))))))))))

(defn- start-fetching
  "Processes requests in the fetch channel using real threads."
  [chan threads fetch?]
  (dotimes [_ threads]
    (thread
      (loop []
        (let [[folder key] (<!! chan)
              options (:options folder)]
          (log/trace "fetching" folder key)
          (when (get options :fetch fetch?)
            (when-let [title (title-for-key key)]
              (try (when-let [info (or (read-metadata title) (retrieve-metadata title))]
                     (add-info folder title info)
                     (when (= (:type info) "series")
                       (doseq [season (remove (-> title :info :seasons keys set) (title-seasons title))]
                         (log/info "retrieving episodes for" (:title title) "Season" season)
                         (let [info (retrieve-season-metadata title season)]
                           (add-info folder title info)))))
                   (catch Exception e (log/error e "error fetching metadata" (str title)))))))
        (recur)))))

(defn- start-encoding
  "Processes requests in the encoder channel."
  []
  (go-loop []
    (let [spec (<! encoder-chan)
          output (io/file (:output spec))]
      (when (.exists (:file spec))
        (log/trace "encoding" spec)
        (block-file output)
        (let [spec (encode-video spec)]
          (when-not (:error spec)
            (unblock-file output)
            (add-video (:folder spec) output)
            (process-file (:folder spec) (:video spec))))))
    (recur)))

(defn start-processing
  "Processes enqueued files."
  [encode? fetch? fmt size]
  (log/debug "starting file processing")
  (start-fetching movie-chan fetch-threads fetch?)
  (start-fetching series-chan 1 fetch?)
  (start-encoding)
  (go-loop []
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
             (catch Exception e (log/error e "error processing video" (str video))))))
    (recur)))

