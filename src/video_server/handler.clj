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
            [compojure.core :refer [defroutes GET]]
            [compojure.route :refer [not-found resources]]
            [ring.middleware.defaults :refer [api-defaults wrap-defaults]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [video-server.android :refer [android-version apk-filename]]
            [video-server.html
             :refer
             [downloads-page legal-page not-found-page title-page titles-page]]
            [video-server.library
             :refer
             [current-titles library-etag title-for-id title-listing video-listing]]
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
  {:status  (or status 200)
   :headers (merge {"Content-Type" "text/html;charset=utf-8"} headers)
   :body    data})

(defn json-response
  "Generates a JSON response from the data."
  [data & [status headers]]
  {:status  (or status 200)
   :headers (merge {"Content-Type" "application/json"} headers)
   :body    (json/write-str data)})

(defn user-agent [request]
  "Returns the user agent header from the request."
  (-> request :headers (get "user-agent")))

(defn safari? [^String agent]
  "Returns whether the client browser is Safari."
  (and (.contains agent "Safari") (not (.contains agent "Chrome"))))

(defn excluded-filetypes [agent]
  "Returns a set of keyword file types, or nil if there are no
  restrictions."
  (if (safari? agent) #{:mkv}))

(defn titles
  "Returns the index or home page listing the titles."
  []
  (let [titles (sort-by :sorting (current-titles))]
    (html-response (titles-page titles @dirs))))

(defn title
  "Returns a title page, excluding the specified filetypes."
  [id season episode exclude]
  (when-let [title (title-for-id id)]
    (html-response (title-page title season episode exclude))))

(defn downloads
  "Returns the downloads page."
  []
  (html-response (downloads-page @base-url (apk-filename))))

(defn legal
  "Returns the legal page."
  []
  (html-response (legal-page)))

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

(defroutes
  app-routes
  (GET "/" [] (titles))
  (GET "/title" [id s e :as r] (title id (parse-long s) (parse-long e) (excluded-filetypes (user-agent r))))
  (GET "/api/v1/videos" [] (videos-api))
  (GET "/api/v1/titles" {:keys [headers]} (titles-api headers))
  (GET "/api/v1/android" [] (android-api))
  (GET "/downloads" [] (downloads))
  (GET "/legal" [] (legal))
  (resources "/" {:root "public"})
  (not-found (not-found-page)))

(defn app
  [url folders]
  (reset! base-url url)
  (reset! dirs (map (comp str :file) folders))
  (wrap-gzip (wrap-stacktrace (wrap-defaults app-routes api-defaults))))
