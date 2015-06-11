(ns ru.vif.background
  (:require [neko.activity :refer [defactivity set-content-view!]]
            [neko.debug :refer [*a safe-for-ui]]
            [neko.threading :refer [on-ui on-ui*]]
            [neko.resource :refer [import-all get-resource]]
            [neko.ui.menu :as menu]
            [neko.log :as log]
            [neko.notify :as notify]
            [neko.ui.adapters :refer [ref-adapter]]
            [neko.find-view :refer [find-view find-views]]
            [neko.ui :refer [config make-ui-element]]
            [neko.action-bar :refer [setup-action-bar]]
            [clojure.java.io :as io]
            [clojure.core.async :as a]

            [ru.vif.model.api :as model-api :refer :all]
            [ru.vif.http-client :as http :refer :all]
            [ru.vif.tools :refer :all]
            [ru.vif.db-tools :refer :all]
            [ru.vif.client-data :refer :all]
            )
  (:import
    (ru.vif.model.records vif-xml-entry parse-data vif-tree vif-display-entry)
    (android.content SharedPreferences$OnSharedPreferenceChangeListener SharedPreferences)
    (android.widget Button)
    (android.app Activity)))

(def root-activity (atom nil))
(def msg-activity (atom nil))

(defn set-root-activity! [^Activity activity]
  (log/d "set root activity" activity)
  (reset! root-activity activity))

(defn set-msg-activity! [^Activity activity]
  (log/d "set msg activity" activity)
  (reset! msg-activity activity))


(defn download-loop []
  (a/go
    (loop []
      (let [no (a/<! download-channel)
            cur-root-activity @root-activity
            cur-msg-activity @msg-activity
            ]
        (println "try download!" no)
        (if (some? cur-root-activity)
          (do
            (set-entry-message! cur-root-activity no (http/download-message no (create-auth-info cur-root-activity)))
            (if (some? cur-msg-activity)
              (refresh-msg-activity cur-msg-activity false)
              (refresh-tree-activity cur-root-activity)
              )
            )
          )
        )
      (recur)
      )
    )
  )

(download-loop)
