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
    (android.widget Button ListView)
    (android.app Activity)
    (android.view View)
    (android.text Html)))

(def root-activity (atom nil))
(def msg-activity (atom nil))

(defn set-root-activity! [^Activity activity]
  (log/d "set root activity" activity)
  (reset! root-activity activity))

(defn set-msg-activity! [^Activity activity]
  (log/d "set msg activity" activity)
  (reset! msg-activity activity))

(defn first-index [pred coll]
  (first (keep-indexed #(when (pred %2) %1) coll)))

(defn set-message-text-in-tree!
  [^Long long-no ^String msg-text]

  (let [cur-root-activity @root-activity
        cur-msg-activity @msg-activity
        [^ListView list-view items] (if (some? cur-msg-activity)
                                      [(find-view cur-msg-activity :msg-list-view) @(.state cur-msg-activity)]
                                      [(find-view cur-root-activity :main-list-view) @root-tree-items-store]
                                      )
        index (first-index #(= (:no %) long-no) items)
        ]
    (on-ui
      (if (some? index)
        (let [list-item (.getChildAt list-view (+ index (.getHeaderViewsCount list-view)))]
          (if (some? list-item)
            (do
              (log/d "list-item=" list-item)
              (config (find-view list-item :messageView)
                      :text (Html/fromHtml msg-text)
                      :visibility View/VISIBLE
                      )
              )
            (log/d "list item is nil!!! index=" index "size=" (.getChildCount list-view))))))))


(defn download-loop []
  (a/go
    (loop []
      (let [no (a/<! download-channel)
            cur-root-activity @root-activity
            long-no (Long. no)
            ]
        (println "try download!" no)
        (if (some? cur-root-activity)
          (do
            (let [msg-text (http/download-message no (create-auth-info cur-root-activity))]
              (set-entry-message! cur-root-activity long-no msg-text)
              (set-message-text-in-tree! long-no msg-text)
              )
            )
          )
        )
      (recur)
      )
    )
  )

(download-loop)
