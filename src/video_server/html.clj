;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.html
  (:require [video-server.library :refer [video-for-key]]
            [video-server.html.pages :refer [downloads-template legal-template not-found-template]]
            [video-server.html.title :refer [episode-titles title-template]]
            [video-server.html.titles :refer [no-titles-template titles-template]]
            [video-server.title :refer [best-containers best-video episode-title season-titles]]))

;;; Titles

(defn titles-page
  [titles dirs]
  (if (seq titles) (titles-template titles) (no-titles-template dirs)))

(defn title-page
  "Returns the page displaying the title w/episodes and parts."
  [title season episode exclude]
  (let [season (or season (ffirst (season-titles title)))
        episode (or episode (ffirst (episode-titles title season)))
        video (video-for-key (best-video (:videos title) season episode))
        playable (remove #(get exclude (:filetype %)) (best-containers video))]
    (title-template title (:info title) video playable season episode)))

;;; Download

(defn downloads-page
  "Returns the downloads page with a link to the Android client app."
  [host apk]
  (downloads-template host apk))

;;; Static pages

(defn legal-page
  "Returns the legal page."
  []
  (legal-template))

(defn not-found-page
  "Returns the not found (404) page."
  []
  (not-found-template))

