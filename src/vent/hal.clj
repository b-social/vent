(ns vent.hal
  (:require
    [halboy.resource :as hal]
    [clojure.string :as string]))

(defn- single-word [property]
  "Removes spaces from property and creates a single word joined by '-'"
  (string/join "-" (string/split property #"(\s+)")))

(defn event-type-property [property]
  (fn [event] (keyword (single-word (hal/get-property (:payload event) property)))))

