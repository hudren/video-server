;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.server
  (:require [clojure.tools.logging :as log]
            [ring.util.servlet :as servlet])
  (:import (java.util EnumSet)
           (javax.servlet DispatcherType)
           (org.eclipse.jetty.server NCSARequestLog Request Server)
           (org.eclipse.jetty.server.handler AbstractHandler HandlerList RequestLogHandler)
           (org.eclipse.jetty.servlet DefaultServlet FilterHolder ServletContextHandler ServletHolder)
           (org.eclipse.jetty.servlets CrossOriginFilter)))

(defn proxy-handler
  "Returns an Jetty Handler implementation for the given Ring handler."
  [handler]
  (proxy [AbstractHandler] []
    (handle [_ ^Request base-request request response]
      (let [request-map (servlet/build-request-map request)
            response-map (handler request-map)]
        (when response-map
          (servlet/update-servlet-response response response-map)
          (.setHandled base-request true))))))

(defn request-log
  "Returns an NCSA-compliant request logger."
  []
  (doto (NCSARequestLog.)
    (.setFilename "yyyy_mm_dd.request.log")
    (.setFilenameDateFormat "yyyy_MM_dd")
    (.setRetainDays 3)
    (.setAppend true)
    (.setExtended true)
    (.setLogCookies false)
    (.setLogTimeZone "GMT")))

(defn request-log-handler
  "Returns a handler for logging all HTTP requests."
  []
  (doto (RequestLogHandler.)
    (.setRequestLog (request-log))))

(defn cors-filter-holder
  "Returns a CORS filter required by Google cast for subtitle tracks."
  []
  (doto (FilterHolder. (CrossOriginFilter.))
    (.setInitParameter CrossOriginFilter/ALLOWED_ORIGINS_PARAM "*")
    (.setInitParameter CrossOriginFilter/ALLOWED_METHODS_PARAM "GET")
    (.setInitParameter CrossOriginFilter/ALLOWED_HEADERS_PARAM "Content-Type, Accept-Encoding, Range")))

(defn movies-servlet-holder
  "Returns a servlet that serves the movie files in the specified
  folder."
  [folder]
  (doto (ServletHolder. DefaultServlet)
    (.setInitParameter "resourceBase" (.getAbsolutePath (:file folder)))
    (.setInitParameter "useFileMappedBuffer" "true")))

(defn context
  "Returns a Jetty handler combining the movie servlet and ring
  handler."
  [handler folder]
  (doto (HandlerList.)
    #_(.addHandler (request-log-handler))
    (.addHandler (doto (ServletContextHandler. ServletContextHandler/SESSIONS)
                   (.setContextPath (str "/" (:name folder)))
                   (.addFilter (cors-filter-holder) "/*" (EnumSet/allOf DispatcherType))
                   (.addServlet (movies-servlet-holder folder) "/")))
    (.addHandler (proxy-handler handler))))

(defn create-server
  "Returns a Jetty server instance."
  [port handler folder]
  (doto (Server. port)
    (.setHandler (context handler folder))))

(defn ^Server start-server
  "Scans the movie folder and starts the Jetty server with discovery
  enabled."
  [url folder port handler]
  (log/info "starting the web server at" url)
  (doto (create-server port handler folder)
    (.start)))

