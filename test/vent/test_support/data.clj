(ns vent.test-support.data
  (:require
    [clojure.string :refer [join]]

    [faker.lorem :as lorem])
  (:import
    [java.util UUID]))

(def url-template "https://%s.com/%s/%s")

(defn random-uuid []
  (str (UUID/randomUUID)))

(defn random-url
  ([] (random-url (random-uuid)))
  ([id]
    (let [words (take 2 (lorem/words))]
      (format url-template (first words) (last words) id))))
