;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.format
  (:require [clojure.string :as str])
  (:import (java.util Locale)
           (java.util.concurrent TimeUnit)))

(def dimensions #{"4K" "1080p" "720p" "FUHD" "UHD" "FHD" "HD" "SD"})

(defn locale
  "Constructs a Locale object."
  ([lang] (Locale. lang))
  ([lang country] (Locale. lang country))
  ([lang country variant] (Locale. lang country variant)))

(defn lang-name
  "Returns the human-readable language from the code."
  [lang]
  (when lang (.getDisplayLanguage (apply locale (take 3 (str/split lang #"_"))))))

(defn lang-two-letter
  "Returns the human-readable language from the code."
  [lang]
  (when lang (subs (.toLanguageTag (apply locale (take 3 (str/split lang #"_")))) 0 2)))

(defn format-size
  "Returns the size in bytes as a human-readable string."
  [size]
  (let [size (if (string? size) (Long/parseLong size) size)]
    (condp #(> %2 %1) size
      1073741824 (format "%.1f GB" (double (/ size 1073741824)))
      1028196 (format "%.0f MB" (double (/ size 1028196)))
      1024 (format "%.0f KB" (double (/ size 1024)))
      (str size " B"))))

(defn format-bitrate
  "Returns the bitrate as a human-readable string."
  [rate]
  (let [rate (if (string? rate) (Long/parseLong rate) rate)]
    (condp #(> %2 %1) rate
      1028196 (format "%.1f Mb/s" (double (/ rate 1028196)))
      1024 (format "%.0f Kb/s" (double (/ rate 1024)))
      (str rate " b/s"))))

(defn format-filetype
  "Returns the file type as a human-readable string."
  [filetype]
  (when filetype (str/upper-case (name filetype))))

(defn format-mimetype
  "Returns the mimetype as a human-readable string."
  [mimetype]
  (case mimetype
    "video/x-matroska" "MKV"
    "video/mp4" "MP4"
    mimetype))

(defn format-duration
  "Returns the duration in seconds as a human-readable string."
  [duration]
  (let [duration (if (string? duration) (Double/parseDouble duration) duration)
        ms (* duration 1000)
        hours (.toHours TimeUnit/MILLISECONDS ms)
        minutes (- (.toMinutes TimeUnit/MILLISECONDS ms)
                   (.toMinutes TimeUnit/HOURS hours))
        seconds (- (.toSeconds TimeUnit/MILLISECONDS ms)
                   (.toSeconds TimeUnit/MINUTES (.toMinutes TimeUnit/MILLISECONDS ms)))]
    (format "%d:%02d:%02d" hours minutes seconds)))

(defn video-dimension
  "Returns a human-readable description of the video dimensions."
  [width height]
  (cond
    (<= 4086 width 4096) "4K"
    (<= 1910 width 1920) "1080p"
    (<= 1270 width 1280) "720p"
    (>= width 7680) "FUHD"
    (>= width 3840) "UHD"
    (>= width 1920) "FHD"
    (>= width 1280) "HD"
    (>= width 710) "SD"
    :default (str width "x" height)))

(defn video-size
  "Returns the probable video size based on the width."
  [width]
  (cond
    (> width 1920) :2160
    (> width 1280) :1080
    (> width 720) :720
    :default :480))

(defn video-width
  "Returns the video width for the given size."
  [size]
  ({:2160 3840 :1080 1920 :720 1280 :480 720} size))

(defn video-desc
  "Returns a human-readable description of the video codecs."
  [codec]
  (get {"h264" "H.264" "mpeg2video" "MPEG-2"} codec codec))

(defn audio-desc
  "Returns a human-readable description of the audio codecs."
  [codec]
  (get {"aac" "AAC" "ac3" "AC-3" "dca" "DTS"} codec codec))

(defn chan-desc
  "Returns the channel description."
  [chans]
  (case chans
    1 "Mono"
    2 "Stereo"
    6 "Surround 5.1"
    8 "Surround 7.1"
    "Surround"))

(defn audio-title
  "Returns the audio stream title."
  [codec lang chans]
  (let [audio (str/trim (str (audio-desc codec) " " (chan-desc chans)))]
    (if-let [name (lang-name lang)]
      (str name " (" audio ")")
      audio)))

(defn sequential-seqs
  "Reducer fn to group sequential ranges from a number series."
  ([] [])
  ([a] a)
  ([a b] (let [n (last (last a))]
           (if (and n (= (inc n) b))
             (update-in a [(dec (count a))] conj b)
             (conj a [b])))))

(defn find-ranges
  "Finds ranges given a collection of numbers."
  [ids]
  (let [seqs (reduce sequential-seqs [] (sort ids))]
    (map #(vector (first %) (last %)) seqs)))

(defn format-ranges
  "Formats ranges to be human readable."
  [ranges]
  (str/join "," (map (fn [[f l]] (if (or (nil? l) (= f l)) f (str f "-" l))) ranges)))

