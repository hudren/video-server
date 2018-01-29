(ns video-server.model-test
  (:require [clojure.test :refer :all]
            [video-server.model :refer :all])
  (:import video_server.model.Video))

(deftest make-records
  (let [rec (make-record Video {:title "title" :part "part"})]
    (is (= "title" (:title rec)))
    (is (nil? (:part rec)))))
