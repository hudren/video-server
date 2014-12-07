;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.file
  (:require [clojure.string :as str]
            [video-server.format :refer [video-dimension]])
  (:import (java.io File FilenameFilter)))

(def movie-exts #{".mkv" ".mp4" ".m4v"})
(def subtitle-exts #{".vtt" ".srt"})
(def image-exts #{".jpg" ".jpeg" ".png" ".webp"})

(defn mimetype
  "Returns a mimetype based on the file extension."
  [file]
  (condp #(.endsWith %2 %1) (.getName file)
    ".mp4" "video/mp4"
    ".m4v" "video/mp4"
    ".mkv" "video/x-matroska"
    ".vtt" "text/vtt"
    ".srt" "application/x-subrip"
    ".png" "image/png"
    ".jpg" "image/jpeg"
    ".jpeg" "image/jpeg"
    ".webp" "image/webp"
    nil))

(defn file-base
  "Returns the base filename up to but not including the first period."
  [file]
  (let [name (if (instance? File file) (.getName file) file)
        pos (.indexOf name ".")]
    (if (>= pos 0) (subs name 0 pos) name)))

(defn file-ext
  "Returns the file extension including the last period."
  [file]
  (let [name (if (instance? File file) (.getName file) file)
        pos (.lastIndexOf name ".")]
    (when (>= pos 0) (subs name pos))))

(defn replace-ext
  "Replaces the last file extension."
  [filename ext]
  (let [pos (.lastIndexOf filename ".")]
    (if (> pos -1)
      (str (subs filename 0 pos) ext)
      filename)))

(defn replace-full-ext
  "Replaces everything past the first period with the file extension."
  [filename ext]
  (let [pos (.indexOf filename ".")]
    (if (> pos -1)
      (str (subs filename 0 pos) ext)
      filename)))

(defn clean-title
  "Returns the video title based on the filename."
  [base]
  (when base
    (-> base
        (str/replace #"_t[0-9]+$" "")
        (str/replace \_ \space)
        str/trim)))

(defn title-info
  "Returns a map containing the title, season, episode number and
  title extracted from a string in the format of <title> - S01E01 -
  <episode>."
  [name]
  (let [[series program episode] (map str/trim (str/split name #" - ")) ]
    (merge {:title (clean-title series)}
           (when program
             (when-let [nums (re-find #"S(\d+)E(\d+)" program)]
               {:season (Integer/parseInt (nth nums 1))
                :episode (Integer/parseInt (nth nums 2))
                :episode-title (clean-title episode)})))))

(defn video-filename
  "Returns the filename for the video at the optional size. The
  qualifier can be used to make the filename unique."
  [video & [ext width height qual]]
  (str (->> [(:title video)
             (when (:season video) (format "S%02dE%02d" (:season video) (:episode video)))
             (:episode-title video)
             (when (#{1280 1920} width) (video-dimension width height))]
            (remove nil?)
            (str/join " - "))
       (when qual (str "." qual))
       ext))

(defn ends-with?
  "Returns whether the filename ends with one of the extensions."
  [name exts]
  (true? (some #(.endsWith name %) exts)))

(defn ext-filter
  "Returns a filename filter that matches the extensions."
  [exts]
  (reify FilenameFilter
    (accept [this dir name] (ends-with? name exts))))

(defn title-filter
  "Returns a filename filter that matches the video title and
  extensions."
  [title exts] ; TODO: should be based on video key
  (reify FilenameFilter
    (accept [this dir name]
      (and (= title (:title (title-info (file-base name)))) (ends-with? name exts)))))

(defn- file-with-ext?
  "Returns whether the file or filename ends with one of the given
  file extensions."
  [file exts]
  (let [filename (if (string? file) file (.getName file))]
    (ends-with? filename exts)))

(defn video?
  "Returns whether the file is a movie file."
  [file]
  (file-with-ext? file movie-exts))

(defn subtitle?
  "Returns whether the file is a subtitle file."
  [file]
  (file-with-ext? file subtitle-exts))

(defn image?
  "Returns whether the file is an image file."
  [file]
  (file-with-ext? file image-exts))

(defn movie-filter
  "A filter for listing movie files."
  []
  (ext-filter movie-exts))

(defn subtitle-filter
  "A filter for listing subtitle files. If a title is specified, the
  filter will only match files related to that title."
  ([] (ext-filter subtitle-exts))
  ([title] (title-filter title subtitle-exts)))

(defn image-filter
  "A filter for listing image files. If a title is specified, the
  filter will only match files related to that title."
  ([] (ext-filter image-exts))
  ([title] (title-filter title image-exts)))

(defn files-for-title
  "Returns the files related to the given title."
  [title-spec]
  ()) ; TODO: implement this

