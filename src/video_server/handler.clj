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
            [compojure.route :refer [not-found resources]]
            [ring.middleware.defaults :refer [api-defaults wrap-defaults]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [video-server.android :refer [android-version apk-filename]]
            [video-server.html :refer [downloads-template title-page titles-page]]
            [video-server.library :refer [current-titles library-etag title-for-id title-listing video-listing]]
            [video-server.util :refer :all]))

(defonce ^:private base-url (atom "http://localhost"))
(defonce ^:private dirs (atom []))

(defn status-response
  "Generates a simple response with a status code."
  [status]
  {:status status})

(defn html-response
  "Generates a HTML response from the data."
  [data & [status headers]]
  {:status (or status 200)
   :headers (merge {"Content-Type" "text/html;charset=utf-8"} headers)
   :body data})

(defn json-response
  "Generates a JSON response from the data."
  [data & [status headers]]
  {:status (or status 200)
   :headers (merge {"Content-Type" "application/json"} headers)
   :body (json/write-str data)})

(defn titles
  "Returns the index or home page listing the titles."
  []
  (let [titles (sort-by :sorting (current-titles))]
    (html-response (titles-page titles @dirs))))

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
  [headers]
  (let [h (library-etag)]
    (if (= h (get headers "if-none-match"))
      (status-response 304)
      (json-response (title-listing) 200 {"ETag" (str h)}))))

(defn android-api
  "Responds with the android client version info."
  []
  (json-response @android-version))

(defroutes app-routes
  (GET "/" [] (titles))
  (GET "/title" [id s e] (title id (parse-long s) (parse-long e)))
  (GET "/downloads" [] (downloads))
  (GET "/api/v1/videos" [] (videos-api))
  (GET "/api/v1/titles" {:keys [headers]} (titles-api headers))
  (GET "/api/v1/android" [] (android-api))
  (resources "/" {:root "public"})
  (not-found "Not Found"))

(defn app
  [url folders]
  (reset! base-url url)
  (reset! dirs (map (comp str :file) folders))
  (wrap-gzip (wrap-stacktrace (wrap-defaults app-routes api-defaults))))

