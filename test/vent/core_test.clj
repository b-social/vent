(ns vent.core-test
  (:require
    [clojure.test :refer :all]

    [clj-fakes.core
     :refer [with-fakes
             recorded-fake
             patch!
             was-called]]

    [halboy.resource :as hal]

    [vent.test-support.data :as data]

    [vent.core
     :refer [Action
             ruleset
             from
             on
             determine-actions
             execute-action
             execute-actions
             react-to]]))

(defrecord TestAction [identifier event context]
  Action
  (execute [_ _]))

(defn test-action [& {:as options}]
  (map->TestAction options))

(defn capture-as [identifier]
  (fn [event context]
    (test-action
      :identifier identifier
      :event event
      :context context)))

(deftest generates-action-when-event-matched
  (let [ruleset
        (ruleset
          (from :some-event-channel
            (on :some-event-type
              (capture-as :some-action))))

        event-channel "some-event-channel"
        event-resource
        (hal/add-properties
          (hal/new-resource (data/random-url))
          {:type    "some-event-type"
           :message "An important message"})

        event {:channel  event-channel
               :resource event-resource}

        context {:thing "thing"}

        actions (determine-actions ruleset event context)]
    (is (= [(test-action
              :identifier :some-action
              :event event
              :context context)]
          actions))))

(deftest allows-multiple-actions-for-the-same-event
  (let [ruleset
        (ruleset
          (from :some-event-channel
            (on :some-event-type
              (capture-as :action-1)
              (capture-as :action-2))))

        event-channel "some-event-channel"
        event-resource (hal/add-properties
                         (hal/new-resource (data/random-url))
                         {:type    "some-event-type"
                          :message "An important message"})
        event {:channel event-channel :resource event-resource}

        context {}

        actions (determine-actions ruleset event context)]
    (is (= [(test-action
              :identifier :action-1
              :event event
              :context context)
            (test-action
              :identifier :action-2
              :event event
              :context context)]
          actions))))

(deftest correctly-determines-actions-when-many-event-types-are-defined
  (let [ruleset
        (ruleset
          (from :some-event-channel
            (on :some-event-type
              (capture-as :some-action))
            (on :other-event-type
              (capture-as :other-action))))

        event-channel "some-event-channel"
        event-resource (hal/add-properties
                         (hal/new-resource (data/random-url))
                         {:type    "other-event-type"
                          :message "The message"})
        event {:channel event-channel :resource event-resource}

        context {}

        actions (determine-actions ruleset event context)]
    (is (= [(test-action
              :identifier :other-action
              :event event
              :context context)]
          actions))))

(deftest correctly-determines-actions-when-many-topics-are-defined
  (let [ruleset
        (ruleset
          (from :first-event-channel
            (on :some-event-type
              (capture-as :first-channel-action)))
          (from :second-event-channel
            (on :some-event-type
              (capture-as :second-channel-action))))

        event-channel "second-event-channel"
        event-resource (hal/add-properties
                         (hal/new-resource (data/random-url))
                         {:type    "some-event-type"
                          :message "The message"})
        event {:channel event-channel :resource event-resource}

        context {}

        actions (determine-actions ruleset event context)]
    (is (= [(test-action
              :identifier :second-channel-action
              :event event
              :context context)]
          actions))))
