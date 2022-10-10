(ns vent.hal-test
  (:require
    [clojure.test :refer :all]

    [vent.hal :refer [event-type-property]]
    [halboy.resource :as hal]))

(defn- new-event [type]
  (hal/add-properties (hal/new-resource) {:type type}))

(deftest test-event-type-property
  (testing "joins a string with multiple words and single spaces"
    (is (= :some-event-type (apply #(event-type-property :type) (new-event "some event type"))))))

  ;(testing "joins a string with multiple words and different number of spaces"
  ;  (is (= 1 (highest-arity-of #(+ % 32)))))
  ;
  ;(testing "keeps the string as it is if it's only one word"
  ;  (is (= 1 (highest-arity-of #(+ % 32))))))