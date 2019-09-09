(ns persister.core-test
  (:require [clojure.test :refer :all]
            [persister.core :as p]))

(deftest diff-topics-test
  "Test the diffing of topic sets. All of these use 'with-redefs' to mock the 'get-topics' call"
  (testing "Testing diff-topic - no topics at startup"
    (with-redefs [p/get-topics (fn [] #{})]
      (let [topic-map (p/diff-topics #{} #{} #{})]
        (is (nil? (:deletes topic-map)))
        (is (nil? (:adds topic-map)))
        (is (= #{} (:all topic-map))))))
  (testing "Testing diff-topic - add first topic"
    (with-redefs [p/get-topics (fn [] #{"topic1"})]
      (let [topic-map (p/diff-topics #{} #{} #{})]
        (is (nil? (:deletes topic-map)))
        (is (= #{"topic1"} (:adds topic-map)))
        (is (= #{"topic1"} (:all topic-map))))))
  (testing "Testing diff-topic - add second topic"
    (with-redefs [p/get-topics (fn [] #{"topic1" "topic2"})]
      (let [topic-map (p/diff-topics #{"topic1"} #{} #{})]
        (is (nil? (:deletes topic-map)))
        (is (= #{"topic2"} (:adds topic-map)))
        (is (= #{"topic1" "topic2"} (:all topic-map))))))
  (testing "Testing diff-topic - delete second topic"
    (with-redefs [p/get-topics (fn [] #{"topic1"})]
      (let [topic-map (p/diff-topics #{"topic1", "topic2"} #{} #{})]
        (is (= #{"topic2"} (:deletes topic-map)))
        (is (nil? (:adds topic-map)))
        (is (= #{"topic1"} (:all topic-map))))))
  (testing "Testing diff-topic - delete first topic"
    (with-redefs [p/get-topics (fn [] #{})]
      (let [topic-map (p/diff-topics #{"topic1"} #{} #{})]
        (is (= #{"topic1"} (:deletes topic-map)))
        (is (nil? (:adds topic-map)))
        (is (= #{} (:all topic-map))))))
  (testing "Testing diff-topic - Include topic"
    (with-redefs [p/get-topics (fn [] #{})]
      (let [topic-map (p/diff-topics #{"topic1"} #{} #{"topic1"})]
        (is (nil? (:deletes topic-map)))
        (is (nil? (:adds topic-map)))
        (is (= #{"topic1"} (:all topic-map))))))
  (testing "Testing diff-topic - Exclude topic"
    (with-redefs [p/get-topics (fn [] #{})]
      (let [topic-map (p/diff-topics #{"topic1"} #{"topic1"} #{})]
        (is (= #{"topic1"} (:deletes topic-map)))
        (is (nil? (:adds topic-map)))
        (is (= #{} (:all topic-map))))))
  (testing "Testing diff-topic - Include and Exclude topic - should ignore exclude"
    (with-redefs [p/get-topics (fn [] #{})]
      (let [topic-map (p/diff-topics #{"topic1"} #{"topic1"} #{"topic1"})]
        (is (nil? (:deletes topic-map)))
        (is (nil? (:adds topic-map)))
        (is (= #{"topic1"} (:all topic-map))))))
  )
