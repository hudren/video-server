;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.handler
  (:require [clojure.data.json :as json]
            [compojure.core :refer [GET defroutes]]
            [compojure.handler :refer [site]]
            [compojure.route :refer [not-found resources]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [video-server.android :refer [android-version apk-filename]]
            [video-server.html :refer [downloads-template title-page titles-template]]
            [video-server.library :refer [current-titles title-for-id title-listing video-listing]]
            [video-server.util :refer :all]))

(def ^:private base-url (atom "http://localhost"))

(defn html-response
  "Generates a HTML response from the data."
  [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "text/html;charset=utf-8"}
   :body data})

(defn json-response
  "Generates a JSON response from the data."
  [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"}
   :body (json/write-str data)})

(defn titles
  "Returns the index or home page listing the titles."
  []
  (let [titles (sort-by :sorting (current-titles))]
    (html-response (titles-template titles))))

(defn title
  "Returns a title page."
  [id season episode]
  (when-let [title (title-for-id id)]
    (html-response (title-page title season episode))))

(defn downloads
  "Returns the downloads page."
  []
  (html-response (downloads-template @base-url (apk-filename))))

(defn videos-api
  "Responds with a list of available videos."
  []
  (json-response (video-listing)))

(defn titles-api
  "Responds with a list of available titles."
  []
  (json-response (title-listing)))

(defn android-api
  "Responds with the android client version info."
  []
  (json-response @android-version))

(defroutes app-routes
  (GET "/" [] (titles))
  (GET "/title" [id s e] (title id (parse-long s) (parse-long e)))
  (GET "/downloads" [] (downloads))
  (GET "/api/v1/videos" [] (videos-api))
  (GET "/api/v1/titles" [] (titles-api))
  (GET "/api/v1/android" [] (android-api))
  (resources "/" {:root "public"})
  (not-found "Not Found"))

(defn app
  [url]
  (reset! base-url url)
  (wrap-gzip (wrap-stacktrace (site app-routes))))

