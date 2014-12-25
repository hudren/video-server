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
            [video-server.omdb :refer [omdb-metadata]]
            [video-server.util :refer :all]))

(defn retrieve-image
  "Returns the image as a byte array."
  [url]
  (let [resp (client/get url {:as :byte-array})]
    (when (= (:status resp) 200)
      (:body resp))))

(defn- multi
  "Returns the comma separated values as a sequence."
  [s]
  (when-not (str/blank? s)
    (map str/trim (str/split s #","))))

(defn info
  "Extracts fields for storage from the metadata"
  [omdb]
  (let [md (merge (select-keys omdb [:title :runtime :director :rated :plot :type])
                  {:imdb (:imdbid omdb)
                   :year (first (parse-ints (:year omdb)))
                   :genres (multi (:genre omdb))
                   :actors (multi (:actors omdb))
                   :languages (multi (:language omdb))})]
    (into {} (remove (comp nil? second) md))))

(defn- metadata-file
  "Returns the File for the video metadata."
  [folder video]
  (io/file (:file folder) (str (:title video) ".json")))

(defn read-metadata
  "Reads the stored metadata for the video."
  [folder video]
  (let [file (metadata-file folder video)]
    (when (.isFile file)
      (try (json/read-str (slurp file) :key-fn keyword)
           (catch Exception e (log/error e "reading .info file" (str file)))))))

(defn save-metadata
  "Stores the metadata for later retrieval."
  [folder video info]
  (let [file (metadata-file folder video)]
    (log/debug "saving metadata" (str file))
    (with-open [w (io/writer file)]
      (.write w (with-out-str (json/pprint info :key-fn name))))))

(defn save-poster
  "Downloads the poster for the specified video."
  [folder video url]
  (let [file (io/file (:file folder) (str (:title video) (file-ext url)))]
    (log/info "downloading poster for" (:title video))
    (when-let [contents (retrieve-image url)]
      (with-open [w (io/output-stream file)]
        (.write w contents)))))

(defn title-parts
  "Extracts the year from the title to aid in metadata lookup."
  [video]
  (let [title (:title video)]
    (if-let [year (second (re-find #" \((\d{4})\)$" title))]
      [(str/trim (subs title 0 (- (count title) 6))) (parse-long year)]
      [title])))

(defn retrieve-metadata
  "Retrieves metadata from the Internet and persists it in the folder."
  [folder video]
  (let [[title year] (title-parts video)]
    (when-let [omdb (omdb-metadata title year)]
      (let [info (info omdb)]
        (save-metadata folder video info)
        (when (and (not (:poster video)) (:poster omdb))
          (save-poster folder video (:poster omdb)))
        info))))

