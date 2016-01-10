;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.android
  (:require [clojure.java.io :as io])
  (:import (java.io File)
           (net.dongliu.apk.parser ApkParser)))

(defn apk-filename [] "video-client-release.apk")

(defn apk-version
  "Returns the version information for the specified apk file."
  [^File file]
  (with-open [parser (ApkParser. file)]
    (let [apk (.getApkMeta parser)]
      {:label (.getLabel apk)
       :package-name (.getPackageName apk)
       :version-code (.getVersionCode apk)
       :version-name (.getVersionName apk)
       :min-sdk-version (.getMinSdkVersion apk)
       :filename (apk-filename)})))

(defn read-apk-version
  "Attemps to read the apk version from the resource found on the
  classpath, possibly copying the file contents to a temporary file."
  [path]
  (try (let [file (io/file (io/resource path))]
         (apk-version file))
       (catch IllegalArgumentException _
         (with-open [input (io/input-stream (io/resource path))]
           (let [file (File/createTempFile "video-server" nil)]
             (io/copy input file)
             (let [response (apk-version file)]
               (io/delete-file file true)
               response))))))

(def android-version (delay (read-apk-version (str "public/" (apk-filename)))))

