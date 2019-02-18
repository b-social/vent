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

    [vent.core :as vent]
    [vent.hal :as vent-hal]))

(defrecord TestAction [identifier event context]
  vent/Action
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
        (vent/ruleset
          (vent/from :some-event-channel
            (vent/on :some-event-type
              (capture-as :some-action))))

        event-channel "some-event-channel"
        event-payload
        {:type    "some-event-type"
         :message "An important message"}

        event
        {:channel event-channel
         :payload event-payload}

        context {:thing "thing"}

        actions (vent/determine-actions ruleset event context)]
    (is (= [(test-action
              :identifier :some-action
              :event event
              :context context)]
          actions))))

(deftest allows-multiple-actions-for-the-same-event
  (let [ruleset
        (vent/ruleset
          (vent/from :some-event-channel
            (vent/on :some-event-type
              (capture-as :action-1)
              (capture-as :action-2))))

        event-channel "some-event-channel"
        event-payload
        {:type    "some-event-type"
         :message "An important message"}

        event
        {:channel  event-channel
         :payload event-payload}

        context {}

        actions (vent/determine-actions ruleset event context)]
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
        (vent/ruleset
          (vent/from :some-event-channel
            (vent/on :some-event-type
              (capture-as :some-action))
            (vent/on :other-event-type
              (capture-as :other-action))))

        event-channel "some-event-channel"
        event-payload
        {:type    "other-event-type"
         :message "The message"}

        event
        {:channel event-channel
         :payload event-payload}

        context {}

        actions (vent/determine-actions ruleset event context)]
    (is (= [(test-action
              :identifier :other-action
              :event event
              :context context)]
          actions))))

(deftest correctly-determines-actions-when-many-event-channels-are-defined
  (let [ruleset
        (vent/ruleset
          (vent/from :first-event-channel
            (vent/on :some-event-type
              (capture-as :first-channel-action)))
          (vent/from :second-event-channel
            (vent/on :some-event-type
              (capture-as :second-channel-action))))

        event-channel "second-event-channel"
        event-payload
        {:type    "some-event-type"
         :message "The message"}
        event
        {:channel event-channel
         :payload event-payload}

        context {}

        actions (vent/determine-actions ruleset event context)]
    (is (= [(test-action
              :identifier :second-channel-action
              :event event
              :context context)]
          actions))))

(deftest allows-event-type-lookup-function-to-be-overridden
  (let [ruleset
        (vent/ruleset
          (vent/options
            :event-type-fn (vent-hal/event-type-property :type))

          (vent/from :first-event-channel
            (vent/on :some-event-type
              (capture-as :first-channel-action)))
          (vent/from :second-event-channel
            (vent/on :some-event-type
              (capture-as :second-channel-action))))

        event-channel "second-event-channel"
        event-resource
        (hal/add-properties
          (hal/new-resource (data/random-url))
          {:type    "some-event-type"
           :message "The message"})

        event
        {:channel event-channel
         :payload event-resource}

        context {}

        actions (vent/determine-actions ruleset event context)]
    (is (= [(test-action
              :identifier :second-channel-action
              :event event
              :context context)]
          actions))))

(deftest allows-event-channel-lookup-function-to-be-overridden
  (let [ruleset
        (vent/ruleset
          (vent/options
            :event-channel-fn (fn [event] (keyword (:topic event))))

          (vent/from :first-event-channel
            (vent/on :some-event-type
              (capture-as :first-channel-action)))
          (vent/from :second-event-channel
            (vent/on :some-event-type
              (capture-as :second-channel-action))))

        event-channel "second-event-channel"
        event-payload
        {:type    "some-event-type"
         :message "The message"}

        event
        {:topic event-channel
         :payload event-payload}

        context {}

        actions (vent/determine-actions ruleset event context)]
    (is (= [(test-action
              :identifier :second-channel-action
              :event event
              :context context)]
          actions))))
