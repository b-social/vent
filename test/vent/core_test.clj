(ns vent.core-test
  (:require
    [clojure.test :refer :all]

    [clj-fakes.core :as fakes]

    [halboy.resource :as hal]

    [vent.test-support.data :as data]

    [vent.core :as v]
    [vent.hal :as vhal]

    [vent.test-support.actions.capturing-action
     :refer [capture-as capturing-action]]
    [vent.test-support.actions.fake-action
     :refer [fake-action]]
    [vent.test-support.gatherers.merging-gatherer
     :refer [add-from-map merging-gatherer]]
    [vent.test-support.gatherers.recording-gatherer
     :refer [recording-gatherer]]
    [vent.test-support.selectors.greater-than-selector
     :refer [greater-than]]
    [vent.test-support.selectors.less-than-selector
     :refer [less-than]]))

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
              :steps [{:type           :action
                       :implementation (capturing-action
                                         :identifier :some-action
                                         :event event
                                         :context context)}])]
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
              :steps [{:type           :action
                       :implementation (capturing-action
                                         :identifier :action-1
                                         :event event
                                         :context context)}
                      {:type           :action
                       :implementation (capturing-action
                                         :identifier :action-2
                                         :event event
                                         :context context)}])]
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
              :steps [{:type           :action
                       :implementation (capturing-action
                                         :identifier :other-action
                                         :event event
                                         :context context)}])]
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
              :steps [{:type           :action
                       :implementation (capturing-action
                                         :identifier :second-channel-action
                                         :event event
                                         :context context)}])]
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
              :steps [{:type           :action
                       :implementation (capturing-action
                                         :identifier :second-channel-action
                                         :event event
                                         :context context)}])]
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
              :steps [{:type           :action
                       :implementation (capturing-action
                                         :identifier :second-channel-action
                                         :event event
                                         :context context)}])]
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
              :steps [{:type           :gatherer
                       :implementation (merging-gatherer
                                         :context-to-merge extra-context)}
                      {:type           :action
                       :implementation (capturing-action
                                         :identifier :action
                                         :event event
                                         :context context)}])]
          plans))))

#_(deftest allows-different-paths-to-be-chosen-based-on-event-and-context
    (let [event-channel "event-channel"
          event-payload
          {:type   "event-type"
           :amount 20}

          event
          {:channel event-channel
           :payload event-payload}

          context {:some :context}

          ruleset
          (v/create-ruleset
            (v/from :event-channel
              (v/on :event-type
                (v/choose
                  (v/option (greater-than :amount 50)
                    (v/act (capture-as :greater-than)))
                  (v/option (less-than :amount 50)
                    (v/act (capture-as :less-than)))))))

          plans (v/determine-plans ruleset event context)]
      (is (= (v/create-plan)))))

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
              :steps [{:type           :action
                       :implementation (capturing-action
                                         :identifier :action
                                         :event event
                                         :context context)}])]
          plans))))

(deftest allows-gatherers-to-be-defined-simply-with-no-args
  (let [context {:first 1
                 :other 8}

        event-channel "event-channel"
        event-payload
        {:type    "event-type"
         :message "The message"}

        event
        {:channel event-channel
         :payload event-payload}

        gather-handler (fn [event context]
                         (v/gatherer []
                           {:first  (* (:first context) 2)
                            :second (get-in event [:payload :message])}))

        gatherer (gather-handler event context)]
    (is (= (v/add-context-to gatherer context)
          {:first  2
           :second "The message"
           :other  8}))))

(deftest allows-gatherers-to-be-defined-simply-with-context-arg
  (let [context {:first 1
                 :other 8}

        event-channel "event-channel"
        event-payload
        {:type    "event-type"
         :message "The message"}

        event
        {:channel event-channel
         :payload event-payload}

        gather-handler (fn [event _]
                         (v/gatherer [context]
                           {:first  (* (:first context) 2)
                            :second (get-in event [:payload :message])}))

        gatherer (gather-handler event context)]
    (is (= (v/add-context-to gatherer context)
          {:first  2
           :second "The message"
           :other  8}))))

(deftest supports-gather-handlers-accepting-only-event-arg
  (fakes/with-fakes
    (let [context {:first 1
                   :other 8}

          fake (fakes/recorded-fake [[fakes/any] "some-result"])
          action-handler (fn [_ _]
                           (fake-action :fake fake))

          event-channel "event-channel"
          event-payload
          {:type    "event-type"
           :message "The message"}

          event
          {:channel event-channel
           :payload event-payload}

          gather-handler (fn [event]
                           (v/gatherer [context]
                             {:first  (* (:first context) 2)
                              :second (get-in event [:payload :message])}))

          ruleset
          (v/create-ruleset
            (v/from :event-channel
              (v/on :event-type
                (v/gather gather-handler)
                (v/act action-handler))))

          plans (v/determine-plans ruleset event context)

          _ (v/execute-plans plans context)]
      (is (fakes/was-called-once
            fake [{:first  2
                   :second "The message"
                   :other  8}])))))

(deftest deep-merges-the-results-of-many-gatherers
  (fakes/with-fakes
    (let [context {:parent1 {:child1 1 :child2 2}
                   :other   8}

          fake (fakes/recorded-fake [[fakes/any] "some-result"])
          action-handler (fn [_ _]
                           (fake-action :fake fake))

          event-channel "event-channel"
          event-payload
          {:type    "event-type"
           :message "The message"}

          event
          {:channel event-channel
           :payload event-payload}

          gather-handler1
          (fn []
            (v/gatherer []
              {:parent1 {:child1 10}
               :parent2 {:child1 5 :child2 6}}))
          gather-handler2
          (fn []
            (v/gatherer []
              {:parent2 {:child1 8 :child3 10}}))

          ruleset
          (v/create-ruleset
            (v/from :event-channel
              (v/on :event-type
                (v/gather gather-handler1)
                (v/gather gather-handler2)
                (v/act action-handler))))

          plans (v/determine-plans ruleset event context)

          _ (v/execute-plans plans context)]
      (is (fakes/was-called-once
            fake [{:parent1 {:child1 10 :child2 2}
                   :parent2 {:child1 8 :child2 6 :child3 10}
                   :other   8}])))))

(deftest allows-actions-to-be-defined-simply-with-no-args
  (fakes/with-fakes
    (let [context {:first 1
                   :other 8}

          fake (fakes/recorded-fake [[fakes/any fakes/any] "some-result"])

          event-channel "event-channel"
          event-payload
          {:type    "event-type"
           :message "The message"}

          event
          {:channel event-channel
           :payload event-payload}

          action-handler (fn [event context]
                           (v/action []
                             (fake event context)))

          action (action-handler event context)

          _ (v/execute action context)]
      (is (fakes/was-called-once fake [event context])))))

(deftest allows-actions-to-be-defined-simply-with-context-arg
  (fakes/with-fakes
    (let [context {:first 1
                   :other 8}

          fake (fakes/recorded-fake [[fakes/any fakes/any] "some-result"])

          event-channel "event-channel"
          event-payload
          {:type    "event-type"
           :message "The message"}

          event
          {:channel event-channel
           :payload event-payload}

          action-handler (fn [event _]
                           (v/action [context]
                             (fake event context)))

          action (action-handler event context)

          _ (v/execute action context)]
      (is (fakes/was-called-once fake [event context])))))

(deftest supports-action-handlers-accepting-only-event-arg
  (fakes/with-fakes
    (let [context {:first 1
                   :other 8}

          fake (fakes/recorded-fake [[fakes/any fakes/any] "some-result"])

          event-channel "event-channel"
          event-payload
          {:type    "event-type"
           :message "The message"}

          event
          {:channel event-channel
           :payload event-payload}

          action-handler (fn [event]
                           (v/action [context]
                             (fake event context)))

          ruleset
          (v/create-ruleset
            (v/from :event-channel
              (v/on :event-type
                (v/act action-handler))))

          plans (v/determine-plans ruleset event context)

          _ (v/execute-plans plans context)]
      (is (fakes/was-called-once
            fake [event context])))))

(deftest executes-the-action-in-the-plan-with-the-provided-context
  (fakes/with-fakes
    (let [fake (fakes/recorded-fake [[fakes/any] "some-result"])
          action (fake-action :fake fake)
          plan (v/create-plan :steps [{:type           :action
                                       :implementation action}])
          context {:important :value}

          result (v/execute-plan plan context)]
      (is (fakes/was-called-once fake [context]))
      (is (= result {:context context
                     :outputs ["some-result"]})))))

(deftest executes-the-actions-in-all-plans-with-the-provided-context
  (fakes/with-fakes
    (let [fake1 (fakes/recorded-fake [[fakes/any] "first-first-result"])
          fake2 (fakes/recorded-fake [[fakes/any] "first-second-result"])
          fake3 (fakes/recorded-fake [[fakes/any] "second-first-result"])

          action1 (fake-action :fake fake1)
          action2 (fake-action :fake fake2)
          action3 (fake-action :fake fake3)

          plan1 (v/create-plan :steps [{:type           :action
                                        :implementation action1}
                                       {:type           :action
                                        :implementation action2}])
          plan2 (v/create-plan :steps [{:type           :action
                                        :implementation action3}])

          context {:important :value}

          results (v/execute-plans [plan1 plan2] context)]
      (is (fakes/was-called-once fake1 [context]))
      (is (fakes/was-called-once fake2 [context]))
      (is (fakes/was-called-once fake3 [context]))
      (is (= results [{:context context
                       :outputs ["first-first-result" "first-second-result"]}
                      {:context context
                       :outputs ["second-first-result"]}])))))

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
                 :steps [{:type           :gatherer
                          :implementation gatherer1}
                         {:type           :gatherer
                          :implementation gatherer2}
                         {:type           :action
                          :implementation action1}
                         {:type           :action
                          :implementation action2}])

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
      (is (= results {:context {:first  :initial
                                :second :updated
                                :third  :overwritten
                                :fourth :new}
                      :outputs ["first-result" "second-result"]})))))

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
                 :steps [{:type           :gatherer
                          :implementation gatherer1}
                         {:type           :action
                          :implementation action1}
                         {:type           :action
                          :implementation action2}])

          results (v/execute-plan plan context)]
      (is (fakes/was-called-once fake1 [context]))
      (is (fakes/was-called-once fake2 [context]))
      (is (fakes/was-called-once fake3 [context]))
      (is (= results {:context context
                      :outputs ["first-result" "second-result"]})))))
