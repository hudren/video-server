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
            [ring.util.response :refer [response]]
            [video-server.android :refer [android-version]]
            [video-server.html :refer [downloads-template index-template]]
            [video-server.library :refer [current-videos]]
            [video-server.video :refer [modified]]))

(def ^:private base-url (atom "http://localhost"))

(defn json-response
  "Generates a JSON response from the data."
  [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"}
   :body (json/write-str data)})

(defn index
  "Returns the index or home page."
  []
  (let [videos (reverse (sort-by modified (current-videos)))]
    (response (index-template videos))))

(defn downloads
  "Returns the downloads page."
  []
  (response (downloads-template @base-url "video-client-release.apk")))

(defn videos-api
  "Responds with a list of available videos."
  []
  (json-response (current-videos)))

(defn android-api
  "Responds with the android client version info."
  []
  (json-response @android-version))

(defroutes app-routes
  (GET "/" [] (index))
  (GET "/downloads" [] (downloads))
  (GET "/api/v1/videos" [] (videos-api))
  (GET "/api/v1/android" [] (android-api))
  (resources "/" {:root "public"})
  (not-found "Not Found"))

(defn app
  [url]
  (reset! base-url url)
  (wrap-gzip (site app-routes)))

