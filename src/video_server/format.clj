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
  (:import (java.util.concurrent TimeUnit)))

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
    (= width 1920) "1080p"
    (= width 1280) "720p"
    (= width 4096) "4K"
    (> width 7680) "FUHD"
    (> width 3840) "UHD"
    (> width 1920) "FHD"
    (> width 1280) "HD"
    (>= width 720) "SD"
    :default (str width "x" height)))

(defn video-desc
  "Returns a human-readable description of the video codecs."
  [codec]
  (get {"h264" "H.264" "mpeg2video" "MPEG-2"} codec codec))

(defn audio-desc
  "Returns a human-readable description of the audio codecs."
  [codec]
  (get {"aac" "AAC" "ac3" "AC-3" "dca" "DTS"} codec codec))

