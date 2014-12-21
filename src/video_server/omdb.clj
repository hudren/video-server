;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.omdb
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.string :as str])
  (:import (java.net URLEncoder)))

(defn retrieve-id
  "Fetches the metadata for the given id."
  [id]
  (let [resp (client/get (str "http://www.omdbapi.com/?plot=full&r=json&i=" id))]
    (when (= (:status resp) 200)
      (json/read-str (:body resp) :key-fn (comp keyword str/lower-case)))))

(defn retrieve-title
  "Fetches the metadata for the given title and year."
  [title & [year]]
  (let [resp (client/get (str "http://www.omdbapi.com/?plot=full&r=json&t=" (URLEncoder/encode title)
                              (when year (str "&y=" year))))]
    (when (= (:status resp) 200)
      (json/read-str (:body resp) :key-fn (comp keyword str/lower-case)))))

(defn omdb-metadata
  "Queries for metadata related to the given title and year."
  [title & [year]]
  (retrieve-title title year))

