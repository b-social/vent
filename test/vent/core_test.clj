(ns vent.core-test
  (:require
    [clojure.test :refer :all]
    [clojure.string :as string]

    [clj-fakes.core :as fakes]

    [halboy.resource :as hal]

    [vent.test-support.data :as data]

    [vent.core :as v]
    [vent.hal :as vent-hal]

    [vent.test-support.actions.capturing-action
     :refer [capture-as capturing-action]]
    [vent.test-support.actions.fake-action
     :refer [fake-action invoke-fake]]
    [vent.test-support.gatherers.merging-gatherer
     :refer [add-from-map merging-gatherer]]
    [vent.test-support.gatherers.recording-gatherer
     :refer [recording-gatherer]]
    [vent.test-support.selectors.greater-than-selector
     :refer [greater-than greater-than-selector]]
    [vent.test-support.selectors.less-than-selector
     :refer [less-than less-than-selector]]))

(deftest generates-action-when-event-matched
  (let [ruleset
        (v/create-ruleset
          (v/from-channel :some-event-channel
            (v/on-type :some-event-type
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
                                         :initial-context context)}])]
          plans))))

(deftest allows-multiple-actions-for-the-same-event
  (let [ruleset
        (v/create-ruleset
          (v/from-channel :some-event-channel
            (v/on-type :some-event-type
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
                                         :initial-context context)}
                      {:type           :action
                       :implementation (capturing-action
                                         :identifier :action-2
                                         :event event
                                         :initial-context context)}])]
          plans))))

(deftest allows-multiple-channels-for-the-same-action
  (let [ruleset
        (v/create-ruleset
          (v/from-channels
            [:some-event-channel
             :second-event-channel]
            (v/on-type :some-event-type
              (v/act (capture-as :action-1)))))

        event-channel-1 "some-event-channel"
        event-channel-2 "second-event-channel"

        event-payload
        {:type    "some-event-type"
         :message "An important message"}

        event-1
        {:channel event-channel-1
         :payload event-payload}

        event-2
        {:channel event-channel-2
         :payload event-payload}

        context {}

        plans-1 (v/determine-plans ruleset event-1 context)
        plans-2 (v/determine-plans ruleset event-2 context)]
    (is (= [(v/create-plan
              :steps [{:type           :action
                       :implementation (capturing-action
                                         :identifier :action-1
                                         :event event-1
                                         :initial-context context)}])]
          plans-1))
    (is (= [(v/create-plan
              :steps [{:type           :action
                       :implementation (capturing-action
                                         :identifier :action-1
                                         :event event-2
                                         :initial-context context)}])]
          plans-2))))

(deftest correctly-determines-actions-when-many-event-types-are-defined
  (let [ruleset
        (v/create-ruleset
          (v/from-channel :some-event-channel
            (v/on-type :some-event-type
              (v/act (capture-as :some-action)))
            (v/on-type :other-event-type
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
                                         :initial-context context)}])]
          plans))))

(deftest correctly-matches-on-multiple-types
  (let [ruleset
        (v/create-ruleset
          (v/from-channel :some-event-channel
            (v/on-types [:other-event-type :some-event-type]
              (v/act (capture-as :some-action)))))

        event-channel "some-event-channel"

        event-payload-other-event-type
        {:type    "other-event-type"
         :message "The message"}

        other-event-type-event
        {:channel event-channel
         :payload event-payload-other-event-type}

        event-payload-some-event-type
        {:type    "some-event-type"
         :message "The message"}

        some-event-type-event
        {:channel event-channel
         :payload event-payload-some-event-type}

        context {}

        other-event-type-plans (v/determine-plans ruleset other-event-type-event context)
        some-event-type-plans (v/determine-plans ruleset some-event-type-event context)]
    (is (= [(v/create-plan
              :steps [{:type           :action
                       :implementation (capturing-action
                                         :identifier :some-action
                                         :event other-event-type-event
                                         :initial-context context)}])]
          other-event-type-plans))
    (is (= [(v/create-plan
              :steps [{:type           :action
                       :implementation (capturing-action
                                         :identifier :some-action
                                         :event some-event-type-event
                                         :initial-context context)}])]
          some-event-type-plans))))

(deftest generates-action-when-on-type-complement-matched
  (let [ruleset
        (v/create-ruleset
          (v/from-channel :some-event-channel
            (v/on-complement-of
              (v/on-type :some-event-type
                         (v/act (capture-as :some-action))))))

        event-channel "some-event-channel"
        matching-event-payload
        {:type    "some-other-event-type"
         :message "An important message"}

        matching-event
        {:channel event-channel
         :payload matching-event-payload}

        non-matching-event-payload
        {:type    "some-event-type"
         :message "An important message"}

        non-matching-event
        {:channel event-channel
         :payload non-matching-event-payload}

        context {:thing "thing"}

        matching-plans (v/determine-plans ruleset matching-event context)
        non-matching-plans (v/determine-plans
                             ruleset non-matching-event context)]
    (is (= [(v/create-plan
              :steps [{:type           :action
                       :implementation (capturing-action
                                         :identifier :some-action
                                         :event matching-event
                                         :initial-context context)}])]
           matching-plans))
    (is (= [] non-matching-plans))))

(deftest generates-action-when-on-types-complement-matched
  (let [ruleset
        (v/create-ruleset
          (v/from-channel :some-event-channel
            (v/on-complement-of
              (v/on-types [:some-event-type :some-other-event-type]
                         (v/act (capture-as :some-action))))))

        event-channel "some-event-channel"
        matching-event-payload
        {:type    "some-third-event-type"
         :message "An important message"}

        matching-event
        {:channel event-channel
         :payload matching-event-payload}

        non-matching-event-payload
        {:type    "some-event-type"
         :message "An important message"}

        non-matching-event
        {:channel event-channel
         :payload non-matching-event-payload}

        context {:thing "thing"}

        matching-plans (v/determine-plans ruleset matching-event context)
        non-matching-plans (v/determine-plans
                             ruleset non-matching-event context)]
    (is (= [(v/create-plan
              :steps [{:type           :action
                       :implementation (capturing-action
                                         :identifier :some-action
                                         :event matching-event
                                         :initial-context context)}])]
           matching-plans))
    (is (= [] non-matching-plans))))

(deftest generates-action-when-on-complement-matched
  (let [ruleset
        (v/create-ruleset
          (v/from-channel :some-event-channel
            (v/on-complement-of
              (v/on :do-not-process
                    (v/act (capture-as :some-action))))))

        event-channel "some-event-channel"
        event-payload
        {:type    "some-event-type"
         :message "An important message"}

        matching-event
        {:channel event-channel
         :payload event-payload
         :do-not-process false}

        non-matching-event
        {:channel event-channel
         :payload event-payload
         :do-not-process true}

        context {:thing "thing"}

        matching-plans (v/determine-plans ruleset matching-event context)
        non-matching-plans (v/determine-plans
                             ruleset non-matching-event context)]
    (is (= [(v/create-plan
              :steps [{:type           :action
                       :implementation (capturing-action
                                         :identifier :some-action
                                         :event matching-event
                                         :initial-context context)}])]
           matching-plans))
    (is (= [] non-matching-plans))))

(deftest correctly-determines-actions-when-many-event-channels-are-defined
  (let [ruleset
        (v/create-ruleset
          (v/from-channel :first-event-channel
            (v/on-type :some-event-type
              (v/act (capture-as :first-channel-action))))
          (v/from-channel :second-event-channel
            (v/on-type :some-event-type
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
                                         :initial-context context)}])]
          plans))))

(deftest allows-event-type-lookup-function-to-be-overridden
  (let [ruleset
        (v/create-ruleset
          (v/options
            :event-type-fn (vent-hal/event-type-property :type))

          (v/from-channel :first-event-channel
            (v/on-type :some-event-type
              (v/act (capture-as :first-channel-action))))
          (v/from-channel :second-event-channel
            (v/on-type :some-event-type
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
                                         :initial-context context)}])]
          plans))))

(deftest allows-event-channel-lookup-function-to-be-overridden
  (let [ruleset
        (v/create-ruleset
          (v/options
            :event-channel-fn (fn [event] (keyword (:topic event))))

          (v/from-channel :first-event-channel
            (v/on-type :some-event-type
              (v/act (capture-as :first-channel-action))))
          (v/from-channel :second-event-channel
            (v/on-type :some-event-type
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
                                         :initial-context context)}])]
          plans))))

(deftest allows-matching-events-by-function
  (let [ruleset
        (v/create-ruleset
          (v/from-channel :some-event-channel
            (v/on (fn [event] (string/includes?
                                (get-in event [:payload :message])
                                "important"))
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
                                         :initial-context context)}])]
          plans))))

(deftest allows-matching-on-every-event
  (let [ruleset
        (v/create-ruleset
          (v/from-channel :some-event-channel
            (v/on-every
              (v/act (capture-as :some-action)))))

        event-channel "some-event-channel"
        event-payload (data/random-payload)

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
                                         :initial-context context)}])]
          plans))))

(deftest allows-event-specific-context-to-be-generated
  (let [context {:first  1
                 :second 2}
        extra-context {:second 3
                       :third  4}

        ruleset
        (v/create-ruleset
          (v/from-channel :event-channel
            (v/on-type :event-type
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
                                         :initial-context context)}])]
          plans))))

(deftest allows-different-paths-to-be-chosen-based-on-event-and-context
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
          (v/from-channel :event-channel
            (v/on-type :event-type
              (v/choose
                (v/option (greater-than :amount 50)
                  (v/act (capture-as :greater-than)))
                (v/option (less-than :amount 50)
                  (v/act (capture-as :less-than)))))))

        plans (v/determine-plans ruleset event context)]
    (is (= [(v/create-plan
              :steps
              [{:type
                :choice

                :options
                [{:selector
                  (greater-than-selector
                    :event event
                    :initial-context context
                    :key :amount
                    :value 50)

                  :plan
                  (v/create-plan
                    :steps [{:type
                             :action

                             :implementation
                             (capturing-action
                               :identifier :greater-than
                               :event event
                               :initial-context context)}])}
                 {:selector
                  (less-than-selector
                    :event event
                    :initial-context context
                    :key :amount
                    :value 50)

                  :plan
                  (v/create-plan
                    :steps [{:type
                             :action

                             :implementation
                             (capturing-action
                               :identifier :less-than
                               :event event
                               :initial-context context)}])}]}])]
          plans))))

(v/defruleset all
  (v/from-channel :event-channel
    (v/on-type :event-type
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
                                         :initial-context context)}])]
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
            (v/from-channel :event-channel
              (v/on-type :event-type
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
            (v/from-channel :event-channel
              (v/on-type :event-type
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
            (v/from-channel :event-channel
              (v/on-type :event-type
                (v/act action-handler))))

          plans (v/determine-plans ruleset event context)

          _ (v/execute-plans plans context)]
      (is (fakes/was-called-once
            fake [event context])))))

(deftest allows-selectors-to-be-defined-simply-with-no-args
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

          selector-handler (fn [event context]
                             (v/selector []
                               (fake event context)
                               true))

          selector (selector-handler event context)

          _ (v/selects? selector context)]
      (is (fakes/was-called-once fake [event context])))))

(deftest allows-selectors-to-be-defined-simply-with-context-arg
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

          selector-handler (fn [event _]
                             (v/selector [context]
                               (fake event context)
                               true))

          selector (selector-handler event context)

          _ (v/selects? selector context)]
      (is (fakes/was-called-once fake [event context])))))

(deftest supports-selector-handlers-accepting-only-event-arg
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

          selector-handler (fn [event]
                             (v/selector [context]
                               (fake event context)
                               true))

          ruleset
          (v/create-ruleset
            (v/from-channel :event-channel
              (v/on-type :event-type
                (v/choose
                  (v/option selector-handler
                    (v/act (capture-as :selected)))))))

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
                     :outputs ["some-result"]
                     :nested  []})))))

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
                       :outputs ["first-first-result" "first-second-result"]
                       :nested  []}
                      {:context context
                       :outputs ["second-first-result"]
                       :nested  []}])))))

(deftest merges-the-action-output-with-the-context-when-the-output-is-a-map
  (fakes/with-fakes
    (let [action-output {:output-key "output value"}
          fake (fakes/recorded-fake [[fakes/any] action-output])
          action (fake-action :fake fake)
          plan (v/create-plan :steps [{:type           :action
                                       :implementation action}])
          context {:important :value}

          result (v/execute-plan plan context)]
      (is (fakes/was-called-once fake [context]))
      (is (= result {:context (merge context action-output)
                     :outputs [action-output]
                     :nested  []})))))

(deftest does-not-modify-the-context-when-the-output-is-NOT-a-map
  (fakes/with-fakes
    (let [action-output "important result"
          fake (fakes/recorded-fake [[fakes/any] action-output])
          action (fake-action :fake fake)
          plan (v/create-plan :steps [{:type           :action
                                       :implementation action}])
          context {:important :value}

          result (v/execute-plan plan context)]
      (is (fakes/was-called-once fake [context]))
      (is (= result {:context context
                     :outputs [action-output]
                     :nested  []})))))

(deftest does-not-modify-the-context-when-output-map-has-ignored-metadata
  (fakes/with-fakes
    (let [action-output ^:ignore {:output-key "output value"}
          fake (fakes/recorded-fake [[fakes/any] action-output])
          action (fake-action :fake fake)
          plan (v/create-plan :steps [{:type           :action
                                       :implementation action}])
          context {:important :value}

          result (v/execute-plan plan context)]
      (is (fakes/was-called-once fake [context]))
      (is (= result {:context context
                     :outputs [action-output]
                     :nested  []})))))

(deftest executes-actions-and-propagates-additional-context-as-expected
  (fakes/with-fakes
    (let [action-output1 {:a "1"}
          action-output2 {:a "2" :b "2"}
          action-output3 ^:ignore {:a "3" :b "3" :c "3"}
          action-output4 {:b "4"}

          fake1 (fakes/recorded-fake [[fakes/any] action-output1])
          fake2 (fakes/recorded-fake [[fakes/any] action-output2])
          fake3 (fakes/recorded-fake [[fakes/any] action-output3])
          fake4 (fakes/recorded-fake [[fakes/any] action-output4])

          action1 (fake-action :fake fake1)
          action2 (fake-action :fake fake2)
          action3 (fake-action :fake fake3)
          action4 (fake-action :fake fake4)

          plan (v/create-plan
                 :steps [{:type           :action
                          :implementation action1}
                         {:type           :action
                          :implementation action2}
                         {:type           :action
                          :implementation action3}
                         {:type           :action
                          :implementation action4}])

          context {:a "0" :b "0" :c "0"}

          results (v/execute-plan plan context)]
      (is (fakes/was-called-once fake1 [context]))
      (is (fakes/was-called-once fake2 [(merge context action-output1)]))
      (is (fakes/was-called-once fake3 [(merge context action-output2)]))
      (is (fakes/was-called-once fake4 [(merge context action-output2)]))
      (is (= results {:context {:a "2"
                                :b "4"
                                :c "0"}
                      :outputs [action-output1
                                action-output2
                                action-output3
                                action-output4]
                      :nested  []})))))

(deftest executes-gatherers-and-passes-additional-context-to-actions
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
                      :outputs ["first-result" "second-result"]
                      :nested  []})))))

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
                      :outputs ["first-result" "second-result"]
                      :nested  []})))))

(deftest executes-first-selected-branch-in-a-choice
  (fakes/with-fakes
    (let [event-channel "event-channel"
          event-payload
          {:type    "event-type"
           :message "The message"}

          event
          {:channel event-channel
           :payload event-payload}

          context {:amount 50}

          fake1 (fakes/recorded-fake [[fakes/any] "first-result"])
          fake2 (fakes/recorded-fake [[fakes/any] "second-result"])
          fake3 (fakes/recorded-fake [[fakes/any] "third-result"])

          ruleset (v/create-ruleset
                    (v/from-channel :event-channel
                      (v/on-type :event-type
                        (v/choose
                          (v/option (greater-than :amount 30)
                            (v/act (invoke-fake fake1)))
                          (v/option (greater-than :amount 20)
                            (v/act (invoke-fake fake2)))
                          (v/option (less-than :amount 10)
                            (v/act (invoke-fake fake3)))))))

          plans (v/determine-plans ruleset event context)

          results (v/execute-plan (first plans) context)]
      (is (fakes/was-called-once fake1 [context]))
      (is (fakes/was-not-called fake2))
      (is (fakes/was-not-called fake3))
      (is (= results {:context context
                      :outputs []
                      :nested  [{:context context
                                 :outputs ["first-result"]
                                 :nested  []}]})))))
