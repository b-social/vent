(defproject b-social/vent "0.6.1-SNAPSHOT"
  :description "Rule based event processing engine."
  :url "https://github.com/b-social/vent"
  :license {:name "The MIT License"
            :url  "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [medley "1.0.0"]
                 [halboy "4.0.1"]]
  :plugins [[lein-eftest "0.5.3"]]
  :profiles {:shared {:dependencies [[faker "0.3.2"]
                                     [clj-fakes "0.11.0"]
                                     [eftest "0.5.3"]]}
             :dev    [:shared]
             :test   [:shared]}
  :eftest {:multithread? false}
  :deploy-repositories {"releases" {:url "https://repo.clojars.org"
                                    :creds :gpg}})
