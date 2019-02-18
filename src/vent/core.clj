(ns vent.core
  (:require
    [clojure.core.protocols]))

(defprotocol Action
  (execute [this context]))

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
  {:event-type event-type
   :handlers   handlers})

(defn determine-actions [ruleset event context]
  (let [{:keys [options rules]} ruleset
        {:keys [event-channel-fn]} options

        channel-rules (or ((event-channel-fn event) rules) [])

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
