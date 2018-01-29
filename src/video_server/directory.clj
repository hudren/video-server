;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.directory
  (:require [clojure.set :as set]
            [clojure.tools.logging :as log])
  (:import java.io.File
           [java.nio.file ClosedWatchServiceException FileSystems StandardWatchEventKinds WatchEvent WatchKey WatchService]))

(def ^:private events
  {StandardWatchEventKinds/ENTRY_CREATE :create
   StandardWatchEventKinds/ENTRY_DELETE :delete
   StandardWatchEventKinds/ENTRY_MODIFY :modify})

(def ^:private fs-events
  (into-array (type StandardWatchEventKinds/ENTRY_CREATE) (keys events)))

(defn hidden?
  "Returns whether the file or directory is hidden using common file
  naming conventions."
  [^File file]
  (or (.isHidden file)
      (some #(.startsWith (.getName file) %) ["." "$" "@"])))

(defn- register
  "Recursively registers a directory, calling the dir-fn with a
  :create event for each one that is registered. A depth-first
  traversal is performed (subject to change)."
  [^WatchService ws dir dir-fn]
  (let [dirs (atom #{})]
    (letfn [(register [^File dir]
              (.register (.toPath dir) ws fs-events)
              (swap! dirs conj (.toPath dir))
              (doseq [subdir (.listFiles dir)]
                (when (and (.isDirectory subdir) (not (hidden? subdir)))
                  (register subdir)))
              (when dir-fn (dir-fn :create dir)))]
      (register dir)
      @dirs)))

(defn process-events
  "Processes file system events calling the appropriate callback."
  [^WatchService ws dir dirs file-fn dir-fn]
  (let [dirs (atom dirs)]
    (try
      (loop []
        (let [^WatchKey k (.take ws)]
          (when (.isValid k)
            (doseq [^WatchEvent ev (.pollEvents k)]
              (let [event (events (.kind ev))
                    path  (.resolve (.watchable k) (.context ev))
                    file  (.toFile path)]
                (when-not (.isHidden file)
                  (cond
                    (.isFile file)    (file-fn event file)
                    (= event :create) (swap! dirs set/union (register ws file dir-fn))
                    (= event :delete) (if (contains? @dirs path)
                                        (when dir-fn (dir-fn :delete file))
                                        (file-fn event file))))))
            (when-not (.reset k) (.cancel k)))
          (when-not (.isValid k)
            (swap! dirs disj (.watchable k))))
        (recur))
      (catch ClosedWatchServiceException _ (log/info "watch service closed for" (str dir)))
      (catch Exception e (log/error e "error watching folder" (str dir))))))

(defn watch-directory
  "Watches the dir for file system changes, calling the file-fn for
  file changes and the optional dir-fn for directory additions and
  removals. The initial scan will invoke the dir-fn for each dir."
  [dir file-fn & [dir-fn]]
  (log/debug "watching" (str dir))
  (let [ws   (.newWatchService (FileSystems/getDefault))
        dirs (register ws dir dir-fn)]
    (future (process-events ws dir dirs file-fn dir-fn))
    (fn [] (.close ws))))
