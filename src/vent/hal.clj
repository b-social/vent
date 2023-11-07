(ns vent.hal
  (:require
    [clojure.string :as string]))

(defn- single-word [property]
  "Removes spaces from property and creates a single word joined by '-'"
  (if (string? property)
    (string/join "-" (string/split property #"(\s+)"))
    property))

(defn event-type-property [property]
  (fn [event] (keyword (single-word (get-in event [:payload :properties property])))))
