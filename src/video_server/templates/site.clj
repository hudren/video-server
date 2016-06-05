(ns video-server.templates.site
  (:require [clojure.string :as str]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [html5 include-css include-js]]))

(defn include-html [& pages]
  (for [page pages]
    [:link {:rel "import" :href page}]))

(defn head
  [title style]
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
   [:title title]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1, minimal-ui"}]
   (include-js "components/webcomponentsjs/webcomponents-lite.min.js")
   (include-html "components/polymer/polymer.html"
                 "components/iron-icons/iron-icons.html"
                 "components/paper-header-panel/paper-header-panel.html"
                 "components/paper-toolbar/paper-toolbar.html"
                 "components/paper-icon-button/paper-icon-button.html"
                 "components/paper-button/paper-button.html"
                 "components/paper-ripple/paper-ripple.html"
                 "components/paper-tabs/paper-tabs.html"
                 "components/iron-icons/iron-icons.html"
                 "custom-style.html")
   (include-css "styles.css")
   (when style (include-css style))])

(defn toolbar [title & content]
  [:paper-toolbar {:justify "justified"} [:div title] content])

(defn site-template [{:keys [title toolbar style footer]} & contents]
  (html5 {:lang "en"}
         (head title style)
         [:body {:unresolved "unresolved"}
          [:paper-header-panel
           (or toolbar (video-server.templates.site/toolbar title))
           (if footer
             [:div
              [:div {:id "content"} contents]
              [:div {:id "footer"} footer]]
             [:div {:id "content"} contents])]]))

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

(defn when-content [tag content]
  (if content [tag content]))

