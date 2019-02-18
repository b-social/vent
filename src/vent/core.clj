(ns vent.core
  (:require
    [clojure.core.protocols]))

(defprotocol Action
  (execute [this context]))

(def ^:private default-event-type-fn
  (fn [event] (keyword (get-in event [:payload :type]))))

(defn- having-key [seq key]
  (filterv #(contains? % key) seq))

(defn- merge-over [seq-of-maps]
  (apply merge seq-of-maps))

(defn- extract-from
  ([fragments key] (extract-from fragments key {}))
  ([fragments key defaults]
    (let [relevant (concat [{key defaults}] (having-key fragments key))
           extracted {key (merge-over (mapv key relevant))}]
      extracted)))

(defn- return-on-match [rule event {:keys [event-type-fn]}]
  (and
    (= (:event-type rule) (event-type-fn event))
    rule))

(defn ruleset [& fragments]
  (let [option-defaults {:event-type-fn default-event-type-fn}
        options (extract-from fragments :options option-defaults)

        rules (extract-from fragments :rules)]
    (merge options rules)))

(defn options [& {:as options}]
  {:options options})

(defn from [channel & event-rules]
  {:rules {(keyword channel) event-rules}})

(defn on [event-type & handlers]
  {:event-type event-type
   :handlers   handlers})

(defn determine-actions [ruleset event context]
  (let [{:keys [options rules]} ruleset
        {:keys [channel]} event

        channel-rules (or ((keyword channel) rules) [])

        event-rules
        (filterv
          (fn [rule] (return-on-match rule event options))
          channel-rules)

        event-actions
        (mapcat
          (fn [rule]
            (map #(% event context) (:handlers rule)))
          event-rules)]
    event-actions))


; Needs testing
(defn execute-action [action context]
  (execute action context))

(defn execute-actions [actions context]
  (doseq [action actions]
    (execute-action action context)))

(defn react-to [ruleset event context]
  (let [actions (determine-actions ruleset event context)]
    (execute-actions actions context)))
