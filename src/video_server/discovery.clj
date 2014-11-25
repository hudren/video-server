;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.discovery
  (:require [clojure.tools.logging :as log])
  (:import (java.net DatagramPacket DatagramSocket InetAddress)))

(defn- listen-for-clients
  "Listens for client requests and responds with information to
  identify this server."
  [url port hostname]
  (let [socket (doto (DatagramSocket. port) (.setBroadcast true))
        buffer (byte-array 15000)]
    (while true
      (try (let [packet (DatagramPacket. buffer (count buffer))]
             (.receive socket packet)
             (let [message (-> (.getData packet) (String.) .trim)]
               (log/trace "received message" message "from" (.getAddress packet))
               (when (= message "DISCOVER_VIDEO_SERVER_REQUEST")
                 (let [response (str url "|" hostname)
                       data (.getBytes response)
                       send-packet (DatagramPacket. data (count data) (.getAddress packet) (.getPort packet))]
                   (log/trace "sending" response)
                   (.send socket send-packet)))))
           (catch Exception e (log/error e))))))

(defn start-discovery
  "Starts the discovery thread, listening for clients."
  [url port]
  (let [hostname (.getHostName (InetAddress/getLocalHost))]
    (log/info "starting discovery service for" hostname "on" port)
    (doto (Thread. (partial listen-for-clients url port hostname) "discovery")
      (.setDaemon true)
      (.start))))

