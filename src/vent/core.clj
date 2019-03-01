(ns vent.core
  (:require
    [vent.util
     :refer [invoke-highest-arity
             deep-merge]]))

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

(defn- return-on-match [rule event {:keys [event-type-fn]}]
  (and
    (= (:event-type rule) (event-type-fn event))
    rule))

(defn- resolve-steps [handlers event context]
  (map
    (fn [handler]
      {:type           (:type handler)
       :implementation (invoke-highest-arity
                         (:handler handler) event context)})
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

(defn from [channel & event-rules]
  {:rules {(keyword channel) event-rules}})

(defn on [event-type & handlers]
  {:event-type event-type
   :handlers   handlers})

(defn choose [& options]
  {:type    :choice
   :options options})

(defn option [& handlers]
  {:type     :option
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
    (fn [{:keys [context] :as accumulator} {:keys [type implementation]}]
      (cond
        (= type :gatherer)
        (assoc accumulator
          :context (add-context-to implementation context))

        (= type :action)
        (update-in accumulator
          [:outputs] conj (execute implementation context))))
    {:context context :outputs []}
    (:steps plan)))

(defn execute-plans [plans context]
  (mapv #(execute-plan % context) plans))

(defn react-to [ruleset event context]
  (let [plans (determine-plans ruleset event context)]
    (execute-plans plans context)))
