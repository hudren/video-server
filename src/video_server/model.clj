;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.model
  (:import (java.lang.reflect Modifier)))

(defrecord Folder [name file url])

(defrecord VideoKey [title season episode])

(defrecord Container [filename language size bitrate width height dimension
                      video audio modified url mimetype])

(defrecord Subtitle [title language filename url mimetype])

(defrecord Video [title duration season episode episode-title containers subtitles])

(defn get-record-fields
  "Returns the record fields as a sequence of keywords."
  [record]
  (->> record
       .getDeclaredFields
       (remove #(-> % .getModifiers Modifier/isStatic))
       (map #(.getName %))
       (remove #(.startsWith % "__"))
       (map keyword)))

(defmacro make-record
  "Contructs a new record by extracting only the defined fields from
  the data map. The resulting record will not have extra fields."
  [record data]
  (let [constructor (symbol (str "map->" (name record)))]
    `(~constructor (select-keys ~data @(delay (get-record-fields ~record))))))

