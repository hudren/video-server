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
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [video-server.format :refer [dimensions]])
  (:import (java.io File FilenameFilter)))

(def movie-exts #{".mkv" ".mp4" ".m4v" ".avi"})
(def subtitle-exts #{".vtt" ".srt"})
(def image-exts #{".jpg" ".jpeg" ".png" ".webp"})
(def metadata-exts #{".json"})

(defn filename
  "Returns the filename of the File or String."
  ^String [file]
  (if (instance? File file) (.getName ^File file) (str file)))

(defn fullpath
  "Returns the canonical path of the File."
  ^String [^File file]
  (.getCanonicalPath file))

(defn descendant?
  "Returns true if the second is a descendent of the first."
  [^File parent ^File child]
  (or (= parent child)
      (.startsWith (.toPath child) (.toPath parent))))

(defn file-base
  "Returns the base filename up to but not including the first period."
  [file]
  (let [name (filename file)
        pos (.indexOf name ".")]
    (if (>= pos 0) (subs name 0 pos) name)))

(defn file-ext
  "Returns the file extension including the last period."
  [file]
  (let [name (filename file)
        pos (.lastIndexOf name ".")]
    (when (>= pos 0) (subs name pos))))

(defn replace-ext
  "Replaces the last file extension."
  [^String filename ext]
  (let [pos (.lastIndexOf filename ".")]
    (if (> pos -1)
      (str (subs filename 0 pos) ext)
      filename)))

(defn replace-full-ext
  "Replaces everything past the first period with the file extension."
  [^String filename ext]
  (let [pos (.indexOf filename ".")]
    (if (> pos -1)
      (str (subs filename 0 pos) ext)
      filename)))

(defn file-type
  "Returns the file extension as a keyword."
  [file]
  (keyword (subs (file-ext file) 1)))

(defn file-subtype
  "Returns the file subtype as a keyword."
  [file]
  (let [parts (str/split (filename file) #"\.")]
    (when (> (count parts) 2)
      (keyword (second (reverse parts))))))

(defn mimetype
  "Returns a mimetype based on the file metadata or extension."
  [file]
  ({".mp4" "video/mp4"
    ".m4v" "video/mp4"
    ".mkv" "video/x-matroska"
    ".avi" "video/x-msvideo"
    ".vtt" "text/vtt"
    ".srt" "application/x-subrip"
    ".png" "image/png"
    ".jpg" "image/jpeg"
    ".jpeg" "image/jpeg"
    ".webp" "image/webp"}
   (file-ext file)))

(defn relative-path
  "Returns the relative path of the file to the folder, or the
  absolute path if the file is not contained within a subfolder."
  [folder file]
  (let [dir (fullpath (:file folder))
        path (fullpath file)]
    (if (.startsWith path dir)
      (subs path (inc (count dir)))
      path)))

(defn hidden?
  "Returns true if the file is considered hidden."
  ([file]
   (.startsWith (filename file) "."))
  ([folder file]
   (let [segments (str/split (relative-path folder file) (re-pattern File/separator))]
     (true? (some #(.startsWith ^String % ".") segments)))))

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
  title extracted from a string in the format of `title - S01E01 -
  episode` or `title - Part 1`."
  [name]
  (let [[series program episode] (map str/trim (str/split (clean-title name) #" - "))
        episode (when-not (dimensions episode) episode)]
    (merge {:title (clean-title series)}
           (when program
             (when-let [nums (re-find #"s(\d+)e(\d+)" (str/lower-case program))]
               {:season (Integer/parseInt (nth nums 1))
                :episode (Integer/parseInt (nth nums 2))
                :episode-title (clean-title episode)}))
           (when program
             (when-let [nums (re-find #"p(?:ar)?t\s*(\d+)" (str/lower-case program))]
               {:episode (Integer/parseInt (nth nums 1))
                :episode-title (clean-title episode)})))))

(defn video-filename
  "Returns the filename for the video at the optional size. The
  qualifier can be used to make the filename unique."
  [video ext & [size qual]]
  (str (->> [(:title video)
             (if (:season video)
               (format "S%02dE%02d" (:season video) (:episode video))
               (when (:episode video) (format "PT%02d" (:episode video))))
             (:episode-title video)
             ({:1080 "1080p" :720 "720p"} size)]
            (remove nil?)
            (str/join " - "))
       (when qual (str "." qual))
       ext))

(defn dir?
  "Returns true if the file represents a directory."
  [file]
  (.isDirectory (io/file file)))

(defn file-with-ext?
  "Returns whether the filename ends with one of the extensions."
  [file exts]
  (some? (exts (file-ext file))))

(defn dir-filter
  ^FilenameFilter []
  (reify FilenameFilter
    (accept [_ dir name] (and (not (hidden? name))
                              (dir? (io/file dir name))))))

(defn ext-filter
  "Returns a filename filter that matches the extensions."
  ^FilenameFilter [exts]
  (reify FilenameFilter
    (accept [_ dir name] (and (not (hidden? name))
                              (file-with-ext? name exts)))))

(defn title-filter
  "Returns a filename filter that matches the video title and
  extensions."
  ^FilenameFilter [title exts]
  (let [title (str/lower-case (clean-title title))]
    (reify FilenameFilter
      (accept [this dir name]
        (and (not (hidden? name))
             (= title (str/lower-case (:title (title-info (file-base name)))))
             (file-with-ext? name exts))))))

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

(defn metadata?
  "Returns whether the file is a metadata file."
  [file]
  (file-with-ext? file metadata-exts))

(defn movie-filter
  "A filter for listing movie files."
  ^FilenameFilter []
  (ext-filter movie-exts))

(defn subtitle-filter
  "A filter for listing subtitle files. If a title is specified, the
  filter will only match files related to that title."
  (^FilenameFilter [] (ext-filter subtitle-exts))
  (^FilenameFilter [title] (title-filter title subtitle-exts)))

(defn image-filter
  "A filter for listing image files. If a title is specified, the
  filter will only match files related to that title."
  (^FilenameFilter [] (ext-filter image-exts))
  (^FilenameFilter [title] (title-filter title image-exts)))

