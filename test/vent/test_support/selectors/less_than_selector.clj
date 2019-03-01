(ns vent.test-support.selectors.less-than-selector
  (:require
    [vent.core :as v]))

(defrecord LessThanSelector [key value]
  v/Selector
  (selects? [_ context]
    (< (key context) value)))

(defn less-than-selector [& {:as options}]
  (map->LessThanSelector options))

(defn less-than [key value]
  (fn [_ _]
    (less-than-selector :key key :value value)))
