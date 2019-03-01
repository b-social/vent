(ns vent.test-support.selectors.greater-than-selector
  (:require [vent.core :as v]))

(defrecord GreaterThanSelector [event initial-context key value]
  v/Selector
  (selects? [_ context]
    (> (key context) value)))

(defn greater-than-selector [& {:as options}]
  (map->GreaterThanSelector options))

(defn greater-than [key value]
  (fn [event context]
    (greater-than-selector
      :event event
      :initial-context context
      :key key
      :value value)))
