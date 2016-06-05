(ns video-server.templates.titles
  (:require [video-server.templates.site :refer :all]
            [video-server.title :refer [best-image has-seasons? season-desc]])
  (:import (java.net URLEncoder)))

(def titles-toolbar (toolbar "Videos@Home" [:a {:href "downloads"} [:paper-icon-button {:icon "file-download"}]]))

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
      [:a {:href url :target "_blank"}
       [:img {:src (or (best-image :poster title) "placeholder.png") :alt "poster"}]]]
     [:div.desc
      [:p
       [:span.title
        [:a {:href url :target "_blank"} (or (:title info) (:title title))]]
       [:span.info
        (when-let [year (:year info)] [:span.year (str year)])
        (when-let [rated (:rated info)] [:span.rated rated])
        (when-let [runtime (if (has-seasons? title) (season-desc title) (:runtime info))]
          [:span.duration runtime])]]
      (when-let [genres (combine "Genres" (:genres info))] [:p.genres genres])
      (when-let [stars (combine "Starring" (:stars info))] [:p.stars stars])]]))

(defn titles-template
  [titles]
  (site-template
    {:title "Videos@Home"
     :style "titles.css"
     :toolbar titles-toolbar
     :footer [:p "Content metadata may be provided by third parties, see " (inline-link "legal" "attribution") " for details."]}
    (map title-item titles)))

(defn no-titles-template
  [dirs]
  (site-template
    {:title "Videos@Home" :toolbar titles-toolbar}
    [:h2 "No Titles"]
    [:p "No vidoes were found in the following folders:"]
    [:ul (for [dir dirs]
           [:li dir])]))

