(ns vent.test-support.selectors.less-than-selector
  (:require
    [vent.core :as v]))

(defrecord LessThanSelector [event initial-context key value]
  v/Selector
  (selects? [_ context]
    (< (key context) value)))

(defn less-than-selector [& {:as options}]
  (map->LessThanSelector options))

(defn less-than [key value]
  (fn [event context]
    (less-than-selector
      :event event
      :initial-context context
      :key key
      :value value)))
