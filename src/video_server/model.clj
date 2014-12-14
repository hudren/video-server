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

;; A folder containing videos to be served
(defrecord Folder [name file url]
  Object
  (toString [_] name))

;; Unique key indentifying a video
(defrecord VideoKey [title season episode]
  Object
  (toString [_] (str title (when (or season episode)
                             (str " - Season " season " Episode " episode)))))

;; External file containing video, audio and other tracks
(defrecord Container [filename language size bitrate width height dimension
                      video audio modified url mimetype]
  Object
  (toString [_] filename))

;; External file containing subtitles
(defrecord Subtitle [title language filename url mimetype]
  Object
  (toString [_] title))

;; Video containing one or more containers
(defrecord Video [title duration season episode episode-title
                  containers subtitles poster]
  Object
  (toString [_] title))

(defn get-record-fields
  "Returns the record fields as a vector of keywords."
  [record]
  (->> record
       .getDeclaredFields
       (remove #(-> % .getModifiers Modifier/isStatic))
       (map #(.getName %))
       (remove #(.startsWith % "__"))
       (mapv keyword)))

(defmacro make-record
  "Contructs a new record by extracting only the defined fields from
  the data map. The resulting record will not have extra fields."
  [record data]
  (let [constructor (symbol (str "map->" (name record)))
        fields (get-record-fields (resolve record))]
    `(~constructor (select-keys ~data ~fields))))

