(ns vent.test-support.actions.capturing-action
  (:require
    [vent.core :as v]))

(defrecord CapturingAction [identifier event initial-context]
  v/Action
  (execute [_ _]))

(defn capturing-action [& {:as options}]
  (map->CapturingAction options))

(defn capture-as [identifier]
  (fn [event context]
    (capturing-action
      :identifier identifier
      :event event
      :initial-context context)))
