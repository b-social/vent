(ns vent.core
  (:require
    [clojure.core.protocols]))

(defprotocol Action
  (execute [this context]))

(defprotocol Contextualiser
  (add-context-to [this context]))

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

(defn- resolve-handlers [rule handlers-key event context]
  (map #((:handler %) event context) (handlers-key rule)))

(defn ruleset [& fragments]
  (let [options (collect-from fragments :options
                  :defaults {:event-channel-fn default-event-channel-fn
                             :event-type-fn    default-event-type-fn})
        rules (collect-from fragments :rules)]
    (merge options rules)))

(defn options [& {:as options}]
  {:options options})

(defn from [channel & event-rules]
  {:rules {(keyword channel) event-rules}})

(defn on [event-type & handlers]
  {:event-type              event-type
   :contextualiser-handlers (filterv #(= (:type %) :contextualiser) handlers)
   :action-handlers         (filterv #(= (:type %) :action) handlers)})

(defn act [act-handler]
  {:type    :action
   :handler act-handler})

(defn contextualise [contextualise-handler]
  {:type    :contextualiser
   :handler contextualise-handler})

(defn plan [& {:keys [contextualisers actions]
               :or   {contextualisers []
                      actions         []}}]
  {:contextualisers contextualisers
   :actions         actions})

(defn- rule->plan [event context]
  (fn [rule]
    (let [contextualisers
          (resolve-handlers
            rule :contextualiser-handlers
            event context)

          actions
          (resolve-handlers
            rule :action-handlers
            event context)]
      (plan
        :contextualisers contextualisers
        :actions actions))))

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

(defn execute-action [action context]
  (execute action context))

(defn execute-plan [plan context]
  (mapv #(execute-action % context) (:actions plan)))

(defn execute-plans [plans context]
  (mapv #(
           execute-plan % context) plans))

(defn react-to [ruleset event context]
  (let [plans (determine-plans ruleset event context)]
    (execute-plans plans context)))
