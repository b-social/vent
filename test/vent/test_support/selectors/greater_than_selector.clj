(ns vent.test-support.selectors.greater-than-selector
  (:require [vent.core :as v]))

(defrecord GreaterThanSelector [key value]
  v/Selector
  (selects? [_ context]
    (> (key context) value)))

(defn greater-than-selector [& {:as options}]
  (map->GreaterThanSelector options))

(defn greater-than [key value]
  (fn [_ _]
    (greater-than-selector :key key :value value)))
