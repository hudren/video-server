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
            [video-server.library :refer [folders-for-title norm-title video-for-key]]
            [video-server.omdb :refer [omdb-info omdb-metadata retrieve-id]]
            [video-server.util :refer :all])
  (:import (java.io File)))

(defn retrieve-image
  "Returns the image as a byte array."
  [url]
  (let [resp (client/get url {:as :byte-array})]
    (when (= (:status resp) 200)
      (:body resp))))

(defn title-dir
  "Returns the root directory of the videos belonging to the title."
  ([title]
   (title-dir title (first (folders-for-title title))))
  ([title folder]
   (let [videos (map video-for-key (filter #(= (first %) folder) (:videos title)))
         containers (mapcat :containers videos)
         pos (-> folder :url decoded-url count inc)
         files (map #(subs (-> % :url decoded-url) pos) containers)
         dirs (distinct (map #(str/join "/" (remove nil? (drop-last 1 (str/split % #"/")))) files))]
     (if (seq dirs)
       (loop [common [] segments (apply map list (map #(str/split % #"/") dirs))]
         (if (and (seq segments) (apply = (first segments)))
           (recur (conj common (ffirst segments)) (rest segments))
           (if (re-find #"s(?:eason)? *\d+" (str/lower-case (last common)))
             (io/file (:file folder) (str/join "/" (drop-last common)))
             (io/file (:file folder) (str/join "/" common)))))
       (io/file (:file folder))))))

(defn metadata-file
  "Returns the File for the video metadata."
  ^File [title]
  (io/file (title-dir title) (str (norm-title (:title title)) ".json")))

(defn- clj-key
  "Returns a long for strings that represent a number, or a keyword."
  [k]
  (if (re-find #"^-?\d+$" k) (parse-long k) (keyword k)))

(defn read-metadata
  "Reads the stored metadata for the video."
  [title]
  (let [file (metadata-file title)]
    (when (.isFile file)
      (try (json/read-str (slurp file) :key-fn clj-key)
           (catch Exception e (log/error e "reading .json file" (str file)))))))

(defn save-metadata
  "Stores the metadata for later retrieval."
  [title info]
  (let [file (metadata-file title)]
    (log/debug "saving metadata" (str file))
    (with-open [w (io/writer file)]
      (.write w (with-out-str (json/pprint info))))))

(defn save-poster
  "Downloads the poster for the specified video."
  [title url & [overwrite]]
  (let [file (io/file (title-dir title) (str (norm-title (:title title)) (file-ext url)))]
    (when (or overwrite (not (.exists file)))
      (log/info "downloading poster for" (:title title))
      (when-let [contents (retrieve-image url)]
        (with-open [w (io/output-stream file)]
          (.write w contents))))))

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
      (let [norm (str/lower-case (str/join (re-seq #"[A-Za-z]" title)))
            norm2 (str/lower-case (str/join (re-seq #"[A-Za-z]" (first titles))))]
        (recur (if-not (= norm norm2) title (first titles)) (rest titles)))
      title)))

(defn fetch-metadata
  "Returns the movie or series metadata including the poster URL."
  [item & [fb imdb-id]]
  (let [[title year] (title-parts (:title item))
        duration (when-let [duration (:duration item)] (/ duration 60))
        series? (some? (:episode item))
        fb (or fb (freebase-metadata title series? year duration))
        db (when-let [id (or imdb-id (get-imdb-id fb))] (retrieve-id id))
        db (or db (omdb-metadata title series? year duration))]
    (merge (freebase-info fb) (omdb-info db)
           {:title (best-title title (:name fb) (:title db))})))

(defn retrieve-metadata
  "Retrieves metadata from the Internet and persists it in the folder."
  [title]
  (let [video (video-for-key (-> title :videos first))
        info (fetch-metadata (or video title))
        poster (:poster info)]
    (save-metadata title (dissoc info :poster))
    (when (and (not (:poster title)) poster)
      (save-poster title poster))
    info))

