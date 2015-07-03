(defproject vif-navigator-libs2 "0.1.0-SNAPSHOT"
            :description "Parser lib for vif navigator. Version2 for internal xml api"
            :global-vars {*warn-on-reflection* true}
            :url "http://github"
            :dependencies [[org.clojure/clojure "1.6.0"]
                           [net.sf.kxml/kxml2 "2.3.0" :use-resources true]

                           ;[reply/reply "0.1.0-beta9"]
                           ;[org.clojure/tools.nrepl "0.2.6"]
                           ]
            :profiles {:default  [:repl]
                       :dev      {:dependencies   [
                                                   [org.clojure/core.typed.rt "0.3.0-alpha5"]
                                                   ]
                                  :resource-paths ["test-resources"]
                                  }
                       :uberjar  {:aot :all}
                       :provided {:dependencies [
                                                 [net.sf.kxml/kxml2 "2.3.0" :use-resources true]
                                                 ;[reply/reply "0.1.0-beta9"]
                                                 ;[org.clojure/tools.nrepl "0.2.6"]
                                                 ]}
                       :repl     {:dependencies   [[reply/reply "0.1.0-beta9"]
                                                   [org.clojure/tools.nrepl "0.2.6"]
                                                   [org.clojure/core.typed "0.3.0-alpha5"]
                                                   ]
                                  :resource-paths ["test-resources"]
                                  }
                       }


            :source-paths ["src/clojure" "src"]
            :main ^:skip-aot ru.vif.model.api
            :target-path "target/%s"
            :aliases {"build" ["install"]}
            )
