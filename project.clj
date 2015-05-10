(defproject vif-navigator/vif-navigator-suite2 "0.0.1-SNAPSHOT"
            :plugins [[lein-modules "0.3.11"]]

            :profiles {:provided
                       {:dependencies [[org.jsoup/jsoup "1.8.2" :use-resources true]]}
                       }

            :modules {:inherited
                                {:license {:name "Apache Software License - v 2.0"
                                           :url  "http://www.apache.org/licenses/LICENSE-2.0"}
                                 }

                      :versions {
                                 vif-navigator-libs2 :version
                                 vif-navigator2      :version}}
            )
