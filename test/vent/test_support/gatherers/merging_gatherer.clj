(ns vent.test-support.gatherers.merging-gatherer
  (:require [vent.core :as v]))

(defrecord MergingGatherer [context-to-merge]
  v/Gatherer
  (add-context-to [_ context]
    (merge context context-to-merge)))

(defn merging-gatherer [& {:as options}]
  (map->MergingGatherer options))

(defn add-from-map [extra-context]
  (fn [_ _]
    (merging-gatherer
      :context-to-merge extra-context)))
