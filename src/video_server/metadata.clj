;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.metadata
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [video-server.file :refer [file-ext]]
            [video-server.freebase :refer [freebase-info freebase-metadata get-imdb-id]]
            [video-server.library :refer [norm-title]]
            [video-server.omdb :refer [omdb-info omdb-metadata retrieve-id]]
            [video-server.util :refer :all]))

(defn retrieve-image
  "Returns the image as a byte array."
  [url]
  (let [resp (client/get url {:as :byte-array})]
    (when (= (:status resp) 200)
      (:body resp))))

(defn- metadata-file
  "Returns the File for the video metadata."
  [folder title]
  (io/file (:file folder) (str (norm-title (:title title)) ".json")))

(defn read-metadata
  "Reads the stored metadata for the video."
  [folder title]
  (let [file (metadata-file folder title)]
    (when (.isFile file)
      (try (json/read-str (slurp file) :key-fn keyword)
           (catch Exception e (log/error e "reading .json file" (str file)))))))

(defn save-metadata
  "Stores the metadata for later retrieval."
  [folder title info]
  (let [file (metadata-file folder title)]
    (log/debug "saving metadata" (str file))
    (with-open [w (io/writer file)]
      (.write w (with-out-str (json/pprint info :key-fn name))))))

(defn save-poster
  "Downloads the poster for the specified video."
  [folder title url]
  (let [file (io/file (:file folder) (str (norm-title (:title title)) (file-ext url)))]
    (log/info "downloading poster for" (:title title))
    (when-let [contents (retrieve-image url)]
      (with-open [w (io/output-stream file)]
        (.write w contents)))))

(defn title-parts
  "Extracts the year from the title to aid in metadata lookup."
  [title]
  (if-let [year (second (re-find #" \((\d{4})\)$" title))]
    [(str/trim (subs title 0 (- (count title) 6))) (parse-long year)]
    [title]))

(defn best-title
  "Returns the latter title that improves on the previous."
  [title & titles]
  (loop [title title titles (remove nil? titles)]
    (if (seq titles)
      (let [norm (str/lower-case (apply str (re-seq #"[A-Za-z]" title)))
            norm2 (str/lower-case (apply str (re-seq #"[A-Za-z]" (first titles))))]
        (recur (if-not (= norm norm2) title (first titles)) (rest titles)))
      title)))

(defn fetch-metadata
  "Returns the pair of metadata and poster URL."
  [item]
  (let [[title year] (title-parts (:title item))
        duration (when-let [duration (:duration item)] (/ duration 60))
        series? (some? (:episode item))
        fb (freebase-metadata title series? year duration)
        db (when-let [id (get-imdb-id fb)] (retrieve-id id))
        db (or db (omdb-metadata title series? year duration))]
    (merge (freebase-info fb) (omdb-info db)
           {:title (best-title title (:name fb) (:title db))})))

(defn retrieve-metadata
  "Retrieves metadata from the Internet and persists it in the folder."
  [folder video]
  (let [info (fetch-metadata video)
        poster (:poster info)]
    (save-metadata folder video info)
    (when (and (not (:poster video)) poster)
      (save-poster folder video poster))
    info))

