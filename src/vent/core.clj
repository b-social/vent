(ns vent.core
  (:require
    [medley.core :refer [find-first]]
    [vent.util
     :refer [invoke-highest-arity
             deep-merge]]))

(declare rule->plan)

(defprotocol Action
  (execute [this context]))

(defprotocol Gatherer
  (add-context-to [this context]))

(defprotocol Selector
  (selects? [this context]))

(defrecord MergingFunctionBackedGatherer [f merger]
  Gatherer
  (add-context-to [_ context]
    (let [merger (or merger deep-merge)
          additional (invoke-highest-arity f context)]
      (merger context additional))))

(defmacro gatherer [bindings & rest]
  `(map->MergingFunctionBackedGatherer
     {:f (fn ~bindings ~@rest)}))

(defrecord FunctionBackedAction [f]
  Action
  (execute [_ context]
    (invoke-highest-arity f context)))

(defmacro action [bindings & rest]
  `(->FunctionBackedAction (fn ~bindings ~@rest)))

(defrecord FunctionBackedSelector [f]
  Selector
  (selects? [_ context]
    (invoke-highest-arity f context)))

(defmacro selector [bindings & rest]
  `(->FunctionBackedSelector (fn ~bindings ~@rest)))

(def ^:private default-event-type-fn
  (fn [event] (keyword (get-in event [:payload :type]))))
(def ^:private default-event-channel-fn
  (fn [event] (keyword (get-in event [:channel]))))

(defn- having-key [seq key]
  (filterv #(contains? % key) seq))

(defn- merge-over [seq-of-maps]
  (apply merge seq-of-maps))

(defn- collect-from
  ([fragments key] (collect-from fragments key :defaults {}))
  ([fragments key & {:keys [defaults]}]
   (let [relevant (concat [{key defaults}] (having-key fragments key))
         extracted {key (merge-over (mapv key relevant))}]
     extracted)))

(defn- return-on-match [rule event options]
  (let [rule-matching-fn (:rule-matching-fn rule)]
    (when (invoke-highest-arity rule-matching-fn event options)
      rule)))

(defn- resolve-steps [handlers event context]
  (map
    (fn [{:keys [type] :as handler}]
      (cond
        (#{:action :gatherer} type)
        {:type           type
         :implementation (invoke-highest-arity
                           (:handler handler) event context)}

        (= :choice type)
        {:type
         type

         :options
         (mapv (fn [option]
                 {:selector (invoke-highest-arity
                              (:selector option) event context)
                  :plan     ((rule->plan event context) option)})
           (:options handler))}))
    handlers))

(defn create-ruleset [& fragments]
  (let [options (collect-from fragments :options
                  :defaults {:event-channel-fn default-event-channel-fn
                             :event-type-fn    default-event-type-fn})
        rules (collect-from fragments :rules)]
    (merge options rules)))

(defmacro defruleset [name & forms]
  `(def ~name
     (create-ruleset ~@forms)))

(defn create-plan [& {:keys [steps] :or {steps []}}]
  {:steps (into [] steps)})

(defn options [& {:as options}]
  {:options options})

(defn from-channels [event-channels & event-rules]
  {:rules
   (into {}
     (for [event-channel event-channels]
       [(keyword event-channel) event-rules]))})

(defn from-channel [event-channel & event-rules]
  {:rules {(keyword event-channel) event-rules}})

(defn on [rule-matching-fn & handlers]
  {:rule-matching-fn rule-matching-fn
   :handlers         handlers})

(defn on-type [event-type & handlers]
  {:rule-matching-fn (fn [event {:keys [event-type-fn]}]
                       (= event-type (event-type-fn event)))
   :handlers         handlers})

(defn on-types [event-types & handlers]
  {:rule-matching-fn (fn [event {:keys [event-type-fn]}]
                       (let [event-types (into #{} event-types)]
                         (event-types (event-type-fn event))))
   :handlers         handlers})

(defn on-every [& handlers]
  {:rule-matching-fn (constantly true)
   :handlers         handlers})

(defn choose [& options]
  {:type    :choice
   :options options})

(defn option [selector & handlers]
  {:type     :option
   :selector selector
   :handlers handlers})

(defn act [act-handler]
  {:type    :action
   :handler act-handler})

(defn gather [gather-handler]
  {:type    :gatherer
   :handler gather-handler})

(defn- rule->plan [event context]
  (fn [rule]
    (let [steps (resolve-steps (:handlers rule) event context)]
      (create-plan :steps steps))))

(defn- rule-for-event? [event options]
  (fn [rule] (return-on-match rule event options)))

(defn determine-plans [ruleset event context]
  (let [{:keys [options rules]} ruleset
        {:keys [event-channel-fn]} options

        channel-rules (or ((event-channel-fn event) rules) [])

        event-rules
        (filterv (rule-for-event? event options) channel-rules)

        event-plans
        (mapv (rule->plan event context) event-rules)]
    event-plans))

(defn execute-plan [plan context]
  (reduce
    (fn [{:keys [context] :as accumulator}
         {:keys [type implementation options]}]
      (cond
        (= type :gatherer)
        (assoc accumulator
          :context (add-context-to implementation context))

        (= type :action)
        (update-in accumulator
          [:outputs] conj (execute implementation context))

        (= type :choice)
        (if-let [selected-option
                 (find-first
                   (fn [option] (selects? (:selector option) context))
                   options)]
          (update-in accumulator
            [:nested] conj (execute-plan
                             (:plan selected-option) context))
          accumulator)))
    {:context context :outputs [] :nested []}
    (:steps plan)))

(defn execute-plans [plans context]
  (mapv #(execute-plan % context) plans))

(defn react-to [ruleset event context]
  (let [plans (determine-plans ruleset event context)]
    (execute-plans plans context)))
