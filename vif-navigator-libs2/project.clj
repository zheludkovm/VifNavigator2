(defproject vif-navigator-libs2 "0.1.0-SNAPSHOT"
            :description "Parser lib for vif navigator. Version2 for internal xml api"
            :global-vars {*warn-on-reflection* true}
            :url "http://github"
            :dependencies [[org.clojure/clojure "1.6.0"]
                           [net.sf.kxml/kxml2 "2.3.0" :use-resources true]
                           ]
            :profiles {:default  [:dev]
                       :dev      {:dependencies   []
                                  :resource-paths ["test-resources"]
                                  }
                       :uberjar  {:aot :all}
                       :provided {:dependencies [
                                                 [net.sf.kxml/kxml2 "2.3.0" :use-resources true]
                                                 ]}}

            :source-paths ["src/clojure" "src"]
            :main ^:skip-aot ru.vif.model.vif-xml-tools
            :target-path "target/%s"
            :aliases {"build" ["install"]}
            )
