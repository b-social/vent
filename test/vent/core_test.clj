(ns vent.core-test
  (:require
    [clojure.test :refer :all]

    [clj-fakes.core :as fakes]

    [halboy.resource :as hal]

    [vent.test-support.data :as data]

    [vent.core :as v]
    [vent.hal :as vhal]))

(defrecord CapturingAction [identifier event context]
  v/Action
  (execute [_ _]))

(defn capturing-action [& {:as options}]
  (map->CapturingAction options))

(defn capture-as [identifier]
  (fn [event context]
    (capturing-action
      :identifier identifier
      :event event
      :context context)))

(defrecord FakeAction [fake]
  v/Action
  (execute [_ context]
    (fake context)))

(defn fake-action [& {:as options}]
  (map->FakeAction options))

(defrecord MergingContextualiser [context-to-merge]
  v/Contextualiser
  (add-context-to [_ context]
    (merge context context-to-merge)))

(defn merging-contextualiser [& {:as options}]
  (map->MergingContextualiser options))

(defn add-from-map [extra-context]
  (fn [_ _]
    (merging-contextualiser
      :context-to-merge extra-context)))

(deftest generates-action-when-event-matched
  (let [ruleset
        (v/ruleset
          (v/from :some-event-channel
            (v/on :some-event-type
              (v/act (capture-as :some-action)))))

        event-channel "some-event-channel"
        event-payload
        {:type    "some-event-type"
         :message "An important message"}

        event
        {:channel event-channel
         :payload event-payload}

        context {:thing "thing"}

        plans (v/determine-plans ruleset event context)]
    (is (= [(v/plan
              :actions [(capturing-action
                          :identifier :some-action
                          :event event
                          :context context)])]
          plans))))

(deftest allows-multiple-actions-for-the-same-event
  (let [ruleset
        (v/ruleset
          (v/from :some-event-channel
            (v/on :some-event-type
              (v/act (capture-as :action-1))
              (v/act (capture-as :action-2)))))

        event-channel "some-event-channel"
        event-payload
        {:type    "some-event-type"
         :message "An important message"}

        event
        {:channel event-channel
         :payload event-payload}

        context {}

        plans (v/determine-plans ruleset event context)]
    (is (= [(v/plan
              :actions [(capturing-action
                          :identifier :action-1
                          :event event
                          :context context)
                        (capturing-action
                          :identifier :action-2
                          :event event
                          :context context)])]
          plans))))

(deftest correctly-determines-actions-when-many-event-types-are-defined
  (let [ruleset
        (v/ruleset
          (v/from :some-event-channel
            (v/on :some-event-type
              (v/act (capture-as :some-action)))
            (v/on :other-event-type
              (v/act (capture-as :other-action)))))

        event-channel "some-event-channel"
        event-payload
        {:type    "other-event-type"
         :message "The message"}

        event
        {:channel event-channel
         :payload event-payload}

        context {}

        plans (v/determine-plans ruleset event context)]
    (is (= [(v/plan
              :actions [(capturing-action
                          :identifier :other-action
                          :event event
                          :context context)])]
          plans))))

(deftest correctly-determines-actions-when-many-event-channels-are-defined
  (let [ruleset
        (v/ruleset
          (v/from :first-event-channel
            (v/on :some-event-type
              (v/act (capture-as :first-channel-action))))
          (v/from :second-event-channel
            (v/on :some-event-type
              (v/act (capture-as :second-channel-action)))))

        event-channel "second-event-channel"
        event-payload
        {:type    "some-event-type"
         :message "The message"}
        event
        {:channel event-channel
         :payload event-payload}

        context {}

        plans (v/determine-plans ruleset event context)]
    (is (= [(v/plan
              :actions [(capturing-action
                          :identifier :second-channel-action
                          :event event
                          :context context)])]
          plans))))

(deftest allows-event-type-lookup-function-to-be-overridden
  (let [ruleset
        (v/ruleset
          (v/options
            :event-type-fn (vhal/event-type-property :type))

          (v/from :first-event-channel
            (v/on :some-event-type
              (v/act (capture-as :first-channel-action))))
          (v/from :second-event-channel
            (v/on :some-event-type
              (v/act (capture-as :second-channel-action)))))

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

        plans (v/determine-plans ruleset event context)]
    (is (= [(v/plan
              :actions [(capturing-action
                          :identifier :second-channel-action
                          :event event
                          :context context)])]
          plans))))

(deftest allows-event-channel-lookup-function-to-be-overridden
  (let [ruleset
        (v/ruleset
          (v/options
            :event-channel-fn (fn [event] (keyword (:topic event))))

          (v/from :first-event-channel
            (v/on :some-event-type
              (v/act (capture-as :first-channel-action))))
          (v/from :second-event-channel
            (v/on :some-event-type
              (v/act (capture-as :second-channel-action)))))

        event-channel "second-event-channel"
        event-payload
        {:type    "some-event-type"
         :message "The message"}

        event
        {:topic   event-channel
         :payload event-payload}

        context {}

        plans (v/determine-plans ruleset event context)]
    (is (= [(v/plan
              :actions [(capturing-action
                          :identifier :second-channel-action
                          :event event
                          :context context)])]
          plans))))

(deftest allows-event-specific-context-to-be-generated
  (let [context {:first  1
                 :second 2}
        extra-context {:second 3
                       :third 4}

        ruleset
        (v/ruleset
          (v/from :event-channel
            (v/on :event-type
              (v/contextualise (add-from-map extra-context))
              (v/act (capture-as :action)))))

        event-channel "event-channel"
        event-payload
        {:type    "event-type"
         :message "The message"}

        event
        {:channel event-channel
         :payload event-payload}

        plans (v/determine-plans ruleset event context)]
    (is (= [(v/plan
              :contextualisers [(merging-contextualiser
                                  :context-to-merge extra-context)]
              :actions [(capturing-action
                          :identifier :action
                          :event event
                          :context context)])]
          plans))))

(deftest executes-the-provided-action-in-the-provided-context
  (fakes/with-fakes
    (let [fake (fakes/recorded-fake [[fakes/any] "some-result"])
          action (fake-action :fake fake)
          plan (v/plan :actions [action])
          context {:important :value}

          result (v/execute-plan plan context)]
      (is (fakes/was-called-once fake [context]))
      (is (= result ["some-result"])))))

(deftest executes-all-provided-actions-in-the-provided-context
  (fakes/with-fakes
    (let [fake1 (fakes/recorded-fake [[fakes/any] "first-result"])
          fake2 (fakes/recorded-fake [[fakes/any] "second-result"])

          action1 (fake-action :fake fake1)
          action2 (fake-action :fake fake2)

          plan1 (v/plan :actions [action1])
          plan2 (v/plan :actions [action2])

          context {:important :value}

          results (v/execute-plans [plan1 plan2] context)]
      (is (fakes/was-called-once fake1 [context]))
      (is (fakes/was-called-once fake2 [context]))
      (is (= results [["first-result"] ["second-result"]])))))
