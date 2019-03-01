(ns vent.test-support.actions.fake-action
  (:require [vent.core :as v]))

(defrecord FakeAction [fake]
  v/Action
  (execute [_ context]
    (fake context)))

(defn fake-action [& {:as options}]
  (map->FakeAction options))

(defn invoke-fake [fake]
  (fn [_ _]
    (fake-action :fake fake)))
