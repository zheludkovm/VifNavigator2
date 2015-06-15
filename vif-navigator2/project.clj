(defproject vif-navigator/vif-navigator2 "0.0.9-SNAPSHOT"
            :description "Vif navigator"
            :url "https://github.com/zheludkovm/VifNavigator2"

            :global-vars {*warn-on-reflection* true}

            :resource-paths ["res"]
            :source-paths ["src/clojure" "src"]
            :java-source-paths ["src/java"]
            :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
            :plugins [[lein-droid "0.3.5"]]

            :dependencies [[org.clojure-android/clojure "1.7.0-alpha6" :use-resources true]
                           [neko/neko "3.2.0"]
                           [org.jsoup/jsoup "1.8.2" :use-resources true]
                           [vif-navigator-libs2 "0.1.0-SNAPSHOT" :use-resources true]
                           [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                           ]
            :profiles {:default [:dev]

                       :dev
                                [:android-common :android-user
                                 {:dependencies [[org.clojure-android/tools.nrepl "0.2.6"]]
                                  :target-path  "target/debug"
                                  :android      {:aot                     :all-with-unused
                                                 :manifest-options        {:app-name "VifNavigator - debug"}
                                                 :target-version "21"
                                                 :aot-exclude-ns [cljs.core.async.macros
                                                                  cljs.core.impl-ioc-macros
                                                                  cljs.core.async.impl.ioc-macros
                                                                  clojure.core.async.lab
                                                                  clojure.core.async]
                                                 }
                                  }]
                       :release
                                [:android-common
                                 {:target-path "target/release"
                                  :android
                                               {;; Specify the path to your private keystore
                                                ;; and the the alias of the key you want to
                                                ;; sign APKs with.
                                                 :keystore-path "/home/mikl/.android/release-keysore"
                                                 :key-alias "vif-alias"

                                                :ignore-log-priority [:debug :verbose]
                                                :aot                 :all-with-unused
                                                :build-type          :release}}]

                       :lean
                                [:release
                                 {:dependencies ^:replace [[org.skummet/clojure-android "1.7.0-alpha5-r2" :use-resources true]
                                                           [neko/neko "3.2.0"]
                                                           [vif-navigator-libs "0.1.0-SNAPSHOT" :use-resources true]
                                                           ]

                                  :exclusions [[org.clojure/clojure]]
                                  :jvm-opts ["-Dclojure.compile.ignore-lean-classes=true"]
                                  :global-vars ^:replace {clojure.core/*warn-on-reflection* true}
                                  :android {:lean-compile true
                                            :skummet-skip-vars ["#'neko.init/init"
                                                                "#'neko.context/context"
                                                                "#'neko.resource/package-name"
                                                                "#'neko.-utils/keyword->static-field"
                                                                "#'neko.-utils/keyword->setter"
                                                                "#'neko.ui.traits/get-display-metrics"
                                                                "#'test.leindroid.sample.main/MainActivity-onCreate"
                                                                "#'test.leindroid.sample.main/MainActivity-init"]}}]
                       }

            :android {;; Specify the path to the Android SDK directory.
                      :sdk-path       "/home/mzheludkov/work/clojure/android-sdk-linux"
                      ;:sdk-path       "/home/mikl/distr/android-sdk-linux"

                      ;; Try increasing this value if dexer fails with
                      ;; OutOfMemoryException. Set the value according to your
                      ;; available RAM.
                      :dex-opts       ["-JXmx4096M" "--num-threads=4" "--incremental"]

                      ;; If previous option didn't work, uncomment this as well.
                      ;; :force-dex-optimize true
                      :support-libraries ["v4"]
                      :target-version "15"
                      :aot-exclude-ns ["clojure.parallel" "clojure.core.reducers"
                                       "cljs-tooling.complete" "cljs-tooling.info"
                                       "cljs-tooling.util.analysis" "cljs-tooling.util.misc"
                                       "cider.nrepl" "cider-nrepl.plugin" "ru.vif.http-client"]


                      }
            :aliases {"build" [["droid" "doall" "-e"]]}
            )
