(ns vent.core
  (:require
    [halboy.resource :as hal]))

(defprotocol Action
  (execute [this context]))

(defn- return-on-match [rule resource]
  (and
    (= (:event-type rule) (keyword (hal/get-property resource :type)))
    rule))

(defn ruleset [& rules]
  (apply merge rules))

(defn from [channel & event-rules]
  {(keyword channel) event-rules})

(defn on [event-type & handlers]
  {:event-type event-type
   :handlers   handlers})

(defn determine-actions [ruleset {:keys [channel resource] :as event} context]
  (let [all-rules (or ((keyword channel) ruleset) [])
        matching-rules
        (filterv
          (fn [rule] (return-on-match rule resource))
          all-rules)]
    (mapcat
      (fn [rule]
        (map #(% event context) (:handlers rule)))
      matching-rules)))

(defn execute-action [action context]
  (execute action context))

(defn execute-actions [actions context]
  (doseq [action actions]
    (execute-action action context)))

(defn react-to [ruleset event context]
  (let [actions (determine-actions ruleset event context)]
    (execute-actions actions context)))
