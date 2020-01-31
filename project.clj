(defproject b-social/vent "0.6.5"
  :description "Rule based event processing engine."
  :url "https://github.com/b-social/vent"
  :license {:name "The MIT License"
            :url  "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [medley "1.0.0"]
                 [halboy "4.0.1"]]
  :plugins [[lein-eftest "0.5.3"]
            [lein-changelog "0.3.2"]
            [lein-shell "0.5.0"]
            [lein-codox "0.10.7"]]
  :profiles {:shared {:dependencies [[faker "0.3.2"]
                                     [clj-fakes "0.11.0"]
                                     [eftest "0.5.3"]]}
             :dev    [:shared]
             :test   [:shared]}

  :codox
  {:namespaces  [#"^vent\."]
   :output-path "docs"
   :source-uri  "https://github.com/b-social/vent/blob/{version}/{filepath}#L{line}"}

  :eftest {:multithread? false}
  :deploy-repositories {"releases" {:url "https://repo.clojars.org"
                                    :creds :gpg}}
  :release-tasks
  [["shell" "git" "diff" "--exit-code"]
   ["change" "version" "leiningen.release/bump-version" "release"]
   ["codox"]
   ["changelog" "release"]
   ["shell" "sed" "-E" "-i" "" "s/\"[0-9]+\\.[0-9]+\\.[0-9]+\"/\"${:version}\"/g" "README.md"]
   ["shell" "git" "add" "."]
   ["vcs" "commit"]
   ["vcs" "tag"]
   ["deploy"]
   ["change" "version" "leiningen.release/bump-version"]
   ["vcs" "commit"]
   ["vcs" "tag"]
   ["vcs" "push"]])
