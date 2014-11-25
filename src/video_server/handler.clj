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
            [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.util.response :refer [response]]
            [video-server.html :as html]
            [video-server.library :as library]
            [video-server.video :as video]))

(defn json-response
  "Generates a JSON response from the data."
  [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"}
   :body (json/write-str data)})

(defn index
  "Returns the index or home page."
  []
  (let [videos (reverse (sort-by video/last-modified (library/current-videos)))]
    (response (html/main-template videos))))

(defn videos-api
  "Responds with a list of available vidoes."
  []
  (json-response (library/current-videos)))

(defroutes app-routes
  (GET "/" [] (index))
  (GET "/api/v1/videos" [] (videos-api))
  (route/files "/" {:root "resources/public"})
  (route/not-found "Not Found"))

(def app
  (wrap-gzip (handler/site app-routes)))

