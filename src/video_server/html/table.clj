;;;; Copyright (c) Jeff Hudren. All rights reserved.
;;;;
;;;; Use and distribution of this software are covered by the
;;;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;;;
;;;; By using this software in any fashion, you are agreeing to be bound by
;;;; the terms of this license.
;;;;
;;;; You must not remove this notice, or any other, from this software.

(ns video-server.html.table
  (:require [clojure.string :as str]))

(defn num-columns
  "Returns the number of columns in the table."
  [t]
  (reduce max 0 (map count t)))

(defn empty-row?
  "Returns true if the row is empty."
  [r]
  (not-any? (comp not str/blank? str) r))

(defn empty-column?
  [t c]
  (empty-row? (map #(nth % c nil) t)))

(defn remove-empty-columns
  "Returns the table with the specified column removed."
  [t]
  (let [ec (map (partial empty-column? t) (range (num-columns t)))]
    (for [r t] (map second (remove first (map vector ec r))))))

(defn remove-empty-rows
  "Returns the table with empty rows removed."
  [t]
  (remove empty-row? t))

(defn condense-table
  "Returns a table without empty columns and rows."
  [t]
  (-> t remove-empty-columns remove-empty-rows))

(defn html-table
  "Returns the table as a :tbody."
  [t]
  [:tbody (map (fn [r] (vector :tr (map (fn [c] (vector :td c)) r))) t)])

