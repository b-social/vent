(ns vent.test-support.gatherers.recording-gatherer
  (:require
    [vent.core :as v]))

(defrecord RecordingGatherer [fake]
  v/Gatherer
  (add-context-to [_ context]
    (do
      (fake context)
      context)))

(defn recording-gatherer [& {:as options}]
  (map->RecordingGatherer options))

(defn record-call [fake]
  (fn [_ _]
    (recording-gatherer :fake fake)))