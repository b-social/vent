(ns vent.hal
  (:require
    [halboy.resource :as hal]))

(defn event-type-property [property]
  (fn [event] (keyword (hal/get-property (:payload event) property))))
