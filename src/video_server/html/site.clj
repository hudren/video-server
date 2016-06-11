;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.html.site
  (:require [clojure.string :as str]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [html5 include-css include-js]])
  (:import (java.net URLEncoder)))

(defn head
  [title style script]
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
   [:title title]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1, minimal-ui"}]
   (include-css "material.min.css")
   (include-js "material.min.js")
   (include-css "https://fonts.googleapis.com/icon?family=Material+Icons")
   (include-css "styles.css")
   (if style (include-css style))
   (if script (include-js script))])

(defn icon [img]
  [:i.material-icons (-> img name (str/replace "-" "_"))])

(defn icon-button [img]
  [:button.mdl-button.mdl-js-button.mdl-button--icon
   (icon img)])

(defn nav-icon-button [link img]
  [:a.mdl-navigation__link {:href link} (icon-button img)])

(defn toolbar [title & content]
  [:header.mdl-layout__header.mdl-color--red-700
   [:div.mdl-layout__header-row
    [:span.mdl-layout-title title]
    (if content
      [:div.mdl-layout-spacer])
    (if content
      [:nav.mdl-navigation content])]])

(defn site-template [{:keys [title toolbar style script onload footer]} & contents]
  (html5 {:lang "en"}
         (head title style script)
         [:body {:onload onload}
          [:div.mdl-layout.mdl-js-layout.mdl-layout--fixed-header
           (or toolbar (video-server.html.site/toolbar title))
           [:main.mdl-layout__content
            [:div#content contents]
            (if footer [:div#footer footer])]]]))

(defn inline-link [url & content]
  [:a.inline {:href url} content])

(defn blank-link [url & content]
  [:a {:href url :target "_blank"} content])

(defn combine
  "Combines multiple lists, returning a comma separated String."
  [& lists]
  (if (string? (first lists))
    (let [values (combine (rest lists))]
      (when values (html [:span.label (str (first lists) ":")] (str " " values))))
    (let [values (distinct (remove nil? (flatten lists)))]
      (when (seq values)
        (str/join ", " values)))))

(defn separate
  "Separates content with a space."
  [& content]
  (interpose " " content))

(defn if-content
  "Emits the tag only when the content exists."
  [tag content]
  (if content [tag content]))

(defn url-encode
  "Encodes text appropriately for inclusion in a URL."
  [text]
  (URLEncoder/encode text "UTF-8"))

