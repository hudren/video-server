;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.html.titles
  (:require [video-server.html.site :refer :all]
            [video-server.title :refer [best-image has-seasons? season-desc]])
  (:import (java.net URLEncoder)))

(def titles-toolbar (toolbar "Videos@Home" (nav-icon-button "downloads" :file-download)))

(defn title-url
  "Returns the URL for the given title."
  [title]
  (str "title?id=" (URLEncoder/encode (:id title) "UTF-8")))

(defn title-item
  [title]
  (let [info (:info title)
        url (title-url title)]
    [:div.video
     [:div.poster.small
      [:a {:href url}
       [:img {:src (or (best-image :poster title) "placeholder.png") :alt "poster"}]]]
     [:div.desc
      [:p
       [:span.title
        [:a {:href url} (or (:title info) (:title title))]]
       " "
       [:span.info
        (if-let [year (:year info)] [:span.year (str year)])
        (if-let [rated (:rated info)] [:span.rated rated])
        (if-let [runtime (if (has-seasons? title) (season-desc title) (:runtime info))]
          [:span.duration runtime])]]
      (if-let [genres (combine "Genres" (:genres info))] [:p.genres genres])
      (if-let [stars (combine "Starring" (:stars info))] [:p.stars stars])]]))

(defn titles-template
  [titles]
  (site-template
    {:title   "Videos@Home"
     :style   "titles.css"
     :toolbar titles-toolbar
     :footer  [:p "Content metadata may be provided by third parties, see " (inline-link "legal" "attribution") " for details."]}
    (map title-item titles)))

(defn no-titles-template
  [dirs]
  (site-template
    {:title "Videos@Home" :toolbar titles-toolbar}
    [:h2 "No Titles"]
    [:p "No vidoes were found in the following folders:"]
    [:ul (for [dir dirs]
           [:li dir])]))

