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

(defn receive-request
  "Blocks waiting for a client to broadcast a discovery message."
  [socket]
  (let [buffer (byte-array 15000)
        packet (DatagramPacket. buffer (count buffer))]
    (.receive socket packet)
    (log/trace "received request from" (.getAddress packet))
    packet))

(defn request-message
  "Returns the message from the request packet."
  [packet]
  (-> (.getData packet) (String.) .trim))

(defn send-response
  "Sends the response back to the requester."
  [socket request url hostname]
  (let [response (str url "|" hostname)
        data (.getBytes response)
        packet (DatagramPacket. data (count data) (.getAddress request) (.getPort request))]
    (log/trace "sending" response)
    (.send socket packet)))

(defn listen-for-clients
  "Listens for client requests and responds with information to
  identify this server."
  [url port hostname]
  (let [socket (doto (DatagramSocket. port) (.setBroadcast true))]
    (while true
      (try (let [request (receive-request socket)
                 message (request-message request)]
             (when (= message "DISCOVER_VIDEO_SERVER_REQUEST")
               (send-response socket request url hostname)))
           (catch Exception e (log/error e))))))

(defn start-discovery
  "Starts the discovery thread, listening for clients."
  [url port hostname]
  (log/info "starting discovery service for" hostname "on" port)
  (doto (Thread. (partial listen-for-clients url port hostname) "discovery")
    (.setDaemon true)
    (.start)))

