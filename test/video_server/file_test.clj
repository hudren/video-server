(ns video-server.file-test
  (:require [clojure.test :refer :all]
            [video-server.file :refer :all]))

(deftest clean-titles
  (is (= "Trust Me" (clean-title "Trust_Me_t00"))))

(deftest title
  (let [spec (title-info "movie title - 720p")]
    (is (= "movie title" (:title spec)))
    (is (nil? (:season spec)))
    (is (nil? (:episode spec)))
    (is (nil? (:episode-title spec)))))

(deftest title-with-part
  (let [spec (title-info "Non-Stop - part1")]
    (is (= "Non-Stop" (:title spec)))
    (is (nil? (:season spec)))
    (is (nil? (:episode spec)))
    (is (nil? (:episode-title spec)))))

(deftest title-makemkv
  (let [spec (title-info "The_Railway_Man_t00")]
    (is (= "The Railway Man" (:title spec)))
    (is (nil? (:season spec)))
    (is (nil? (:episode spec)))
    (is (nil? (:episode-title spec)))))

(deftest title-with-episode
  (let [spec (title-info "series - S03E02")]
    (is (= "series" (:title spec)))
    (is (= 3 (:season spec)))
    (is (= 2 (:episode spec)))
    (is (nil? (:episode-title spec)))))

(deftest title-with-episode-and-title
  (let [spec (title-info "series - S01E02 - episode")]
    (is (= "series" (:title spec)))
    (is (= 1 (:season spec)))
    (is (= 2 (:episode spec)))
    (is (= "episode" (:episode-title spec)))))

(deftest title-with-episodes-in-parts
  (let [spec (title-info "series - S02E01 - episode - part1")]
    (is (= "series" (:title spec)))
    (is (= 2 (:season spec)))
    (is (= 1 (:episode spec)))
    (is (= "episode" (:episode-title spec)))))
