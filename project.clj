(defproject vent "0.1.0-SNAPSHOT"
  :description "Rule based event processing engine."
  :url "https://github.com/b-social/vent"
  :license {:name "The MIT License"
            :url  "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [halboy "4.0.1"]]
  :deploy-repositories [["releases" {:url   "https://clojars.org/repo/"
                                     :creds :gpg}]])
