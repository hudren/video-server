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
            [video-server.file :refer [adjust-filename file-ext title-filter]]
            [video-server.library
             :refer
             [folders-for-title norm-title video-for-key]]
            [video-server.omdb :refer [omdb-info omdb-metadata retrieve-id]]
            [video-server.tmdb :refer [search-for-ids tmdb-info tmdb-metadata]]
            [video-server.tvdb :refer [tvdb-season-metadata]]
            [video-server.util :refer :all])
  (:import java.io.File))

(defn retrieve-image
  "Returns the image as a byte array."
  ^java.io.InputStream [url]
  (let [resp (client/get url {:as :stream})]
    (when (= (:status resp) 200)
      (:body resp))))

(defn title-dir
  "Returns the root directory of the videos belonging to the title."
  (^File [title]
   (title-dir title (first (folders-for-title title))))
  (^File [title folder]
   (let [videos     (map video-for-key (filter #(= (first %) folder) (:videos title)))
         containers (mapcat :containers videos)
         pos        (-> folder :url decoded-url count inc)
         files      (map #(subs (-> % :url decoded-url) pos) containers)
         dirs       (distinct (map #(str/join "/" (remove nil? (drop-last 1 (str/split % #"/")))) files))]
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
  (or (first (.listFiles (title-dir title) (title-filter title #{".json"})))
      (io/file (title-dir title) (adjust-filename (norm-title (:title title)) ".json"))))

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
      (.write w ^String (with-out-str (json/pprint info))))))

(defn save-poster
  "Downloads the poster for the specified video."
  [title url & [overwrite]]
  (let [file (io/file (title-dir title) (adjust-filename (norm-title (:title title)) (file-ext url)))]
    (when (or overwrite (not (.exists file)) (zero? (.length file)))
      (log/info "downloading poster for" (:title title))
      (when-let [stream (retrieve-image url)]
        (with-open [r stream]
          (with-open [w (io/output-stream file)]
           (io/copy r w)))))))

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
      (let [norm  (str/lower-case (str/join (re-seq #"[A-Za-z]" title)))
            norm2 (str/lower-case (str/join (re-seq #"[A-Za-z]" (first titles))))]
        (recur (if-not (= norm norm2) title (first titles)) (rest titles)))
      title)))

(defn fetch-metadata
  "Returns the movie or series metadata including the poster URL."
  [item & [imdb-id]]
  (let [[title year]              (title-parts (:title item))
        duration                  (when-let [duration (:duration item)] (/ duration 60))
        series?                   (some? (:episode item))
        {:keys [tmdb-id imdb-id]} (if imdb-id {:imdb-id imdb-id} (search-for-ids title series? year duration))
        db                        (when imdb-id (retrieve-id imdb-id))
        db                        (or db (omdb-metadata title series? year))
        md                        (tmdb-metadata (or tmdb-id imdb-id (:imdbid db)) series?)]
    (merge (tmdb-info md) (omdb-info db)
           {:title (best-title title (:title db))})))

(defn retrieve-metadata
  "Retrieves metadata from the Internet and persists it in the folder."
  [title]
  (let [video  (video-for-key (-> title :videos first))
        info   (fetch-metadata (or video title))
        poster (:poster info)]
    (save-metadata title (dissoc info :poster))
    (when (and (not (:poster title)) poster)
      (save-poster title poster))
    info))

(defn retrieve-season-metadata
  "Retrieves season metadata from the Internet and updates the
  persisted file."
  [title season]
  (let [info (read-metadata title)]
    (if-let [episodes (tvdb-season-metadata (:title title) (-> title :info :imdb-id) season)]
      (let [new-info (update-in info [:seasons season] merge episodes)]
        (save-metadata title new-info)
        new-info)
      info)))
