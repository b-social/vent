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

(defrecord MergingGatherer [context-to-merge]
  v/Gatherer
  (add-context-to [_ context]
    (merge context context-to-merge)))

(defn merging-gatherer [& {:as options}]
  (map->MergingGatherer options))

(defn add-from-map [extra-context]
  (fn [_ _]
    (merging-gatherer
      :context-to-merge extra-context)))

(defrecord RecordingGatherer [fake]
  v/Gatherer
  (add-context-to [_ context]
    (do
      (fake context)
      context)))

(defn recording-gatherer [& {:as options}]
  (map->RecordingGatherer options))

(defn record-call [fake]
  (fn [_ _]
    (recording-gatherer :fake fake)))

(deftest generates-action-when-event-matched
  (let [ruleset
        (v/create-ruleset
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
    (is (= [(v/create-plan
              :actions [(capturing-action
                          :identifier :some-action
                          :event event
                          :context context)])]
          plans))))

(deftest allows-multiple-actions-for-the-same-event
  (let [ruleset
        (v/create-ruleset
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
    (is (= [(v/create-plan
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
        (v/create-ruleset
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
    (is (= [(v/create-plan
              :actions [(capturing-action
                          :identifier :other-action
                          :event event
                          :context context)])]
          plans))))

(deftest correctly-determines-actions-when-many-event-channels-are-defined
  (let [ruleset
        (v/create-ruleset
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
    (is (= [(v/create-plan
              :actions [(capturing-action
                          :identifier :second-channel-action
                          :event event
                          :context context)])]
          plans))))

(deftest allows-event-type-lookup-function-to-be-overridden
  (let [ruleset
        (v/create-ruleset
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
    (is (= [(v/create-plan
              :actions [(capturing-action
                          :identifier :second-channel-action
                          :event event
                          :context context)])]
          plans))))

(deftest allows-event-channel-lookup-function-to-be-overridden
  (let [ruleset
        (v/create-ruleset
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
    (is (= [(v/create-plan
              :actions [(capturing-action
                          :identifier :second-channel-action
                          :event event
                          :context context)])]
          plans))))

(deftest allows-event-specific-context-to-be-generated
  (let [context {:first  1
                 :second 2}
        extra-context {:second 3
                       :third  4}

        ruleset
        (v/create-ruleset
          (v/from :event-channel
            (v/on :event-type
              (v/gather (add-from-map extra-context))
              (v/act (capture-as :action)))))

        event-channel "event-channel"
        event-payload
        {:type    "event-type"
         :message "The message"}

        event
        {:channel event-channel
         :payload event-payload}

        plans (v/determine-plans ruleset event context)]
    (is (= [(v/create-plan
              :gatherers [(merging-gatherer
                            :context-to-merge extra-context)]
              :actions [(capturing-action
                          :identifier :action
                          :event event
                          :context context)])]
          plans))))

(v/defruleset all
  (v/from :event-channel
    (v/on :event-type
      (v/act (capture-as :action)))))

(deftest allows-ruleset-to-be-defined-in-a-namespace
  (let [event-channel "event-channel"
        event-payload
        {:type    "event-type"
         :message "The message"}

        event
        {:channel event-channel
         :payload event-payload}

        context {}

        plans (v/determine-plans all event context)]
    (is (= [(v/create-plan
              :actions [(capturing-action
                          :identifier :action
                          :event event
                          :context context)])]
          plans))))

(deftest executes-the-action-in-the-plan-with-the-provided-context
  (fakes/with-fakes
    (let [fake (fakes/recorded-fake [[fakes/any] "some-result"])
          action (fake-action :fake fake)
          plan (v/create-plan :actions [action])
          context {:important :value}

          result (v/execute-plan plan context)]
      (is (fakes/was-called-once fake [context]))
      (is (= result ["some-result"])))))

(deftest executes-the-actions-in-all-plans-with-the-provided-context
  (fakes/with-fakes
    (let [fake1 (fakes/recorded-fake [[fakes/any] "first-first-result"])
          fake2 (fakes/recorded-fake [[fakes/any] "first-second-result"])
          fake3 (fakes/recorded-fake [[fakes/any] "second-first-result"])

          action1 (fake-action :fake fake1)
          action2 (fake-action :fake fake2)
          action3 (fake-action :fake fake3)

          plan1 (v/create-plan :actions [action1 action2])
          plan2 (v/create-plan :actions [action3])

          context {:important :value}

          results (v/execute-plans [plan1 plan2] context)]
      (is (fakes/was-called-once fake1 [context]))
      (is (fakes/was-called-once fake2 [context]))
      (is (fakes/was-called-once fake3 [context]))
      (is (= results [["first-first-result" "first-second-result"]
                      ["second-first-result"]])))))

(deftest executes-gatherers-before-actions-and-passes-additional-context
  (fakes/with-fakes
    (let [fake1 (fakes/recorded-fake [[fakes/any] "first-result"])
          fake2 (fakes/recorded-fake [[fakes/any] "second-result"])

          action1 (fake-action :fake fake1)
          action2 (fake-action :fake fake2)

          gatherer1 (merging-gatherer
                      :context-to-merge {:second :updated
                                         :third  :new})
          gatherer2 (merging-gatherer
                      :context-to-merge {:third  :overwritten
                                         :fourth :new})

          plan (v/create-plan
                 :gatherers [gatherer1 gatherer2]
                 :actions [action1 action2])

          context {:first  :initial
                   :second :overwrite-me}

          results (v/execute-plan plan context)]
      (is (fakes/was-called-once fake1 [{:first  :initial
                                         :second :updated
                                         :third  :overwritten
                                         :fourth :new}]))
      (is (fakes/was-called-once fake2 [{:first  :initial
                                         :second :updated
                                         :third  :overwritten
                                         :fourth :new}]))
      (is (= results ["first-result" "second-result"])))))

(deftest executes-gatherers-once-only
  (fakes/with-fakes
    (let [context {:first  1
                   :second 2}

          fake1 (fakes/recorded-fake [[fakes/any] "first-result"])
          fake2 (fakes/recorded-fake [[fakes/any] "second-result"])
          fake3 (fakes/recorded-fake [[fakes/any] context])

          action1 (fake-action :fake fake1)
          action2 (fake-action :fake fake2)

          gatherer1 (recording-gatherer :fake fake3)

          plan (v/create-plan
                 :gatherers [gatherer1]
                 :actions [action1 action2])

          results (v/execute-plan plan context)]
      (is (fakes/was-called-once fake1 [context]))
      (is (fakes/was-called-once fake2 [context]))
      (is (fakes/was-called-once fake3 [context]))
      (is (= results ["first-result" "second-result"])))))
