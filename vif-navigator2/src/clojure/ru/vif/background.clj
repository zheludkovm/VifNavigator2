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

            [ru.vif.model.api :as model-api :refer :all]
            [ru.vif.http-client :as http :refer :all]
            [ru.vif.tools :refer :all]
            [ru.vif.db-tools :refer :all]
            [ru.vif.client-data-store :refer :all]
            )
  (:import
    (ru.vif.model.records vif-xml-entry parse-data vif-tree vif-display-entry)
    (android.content SharedPreferences$OnSharedPreferenceChangeListener SharedPreferences)
    (android.widget Button ListView)
    (android.app Activity)
    (android.view View)
    (android.text Html)
    (ru.vif EllipsizingTextView)))

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

  (let [^Activity cur-root-activity @root-activity
        ^Activity cur-msg-activity @msg-activity
        [^ListView list-view items] (if (some? cur-msg-activity)
                                      [(find-view cur-msg-activity :msg-list-view) @(.state cur-msg-activity)]
                                      [(find-view cur-root-activity :main-list-view) @root-tree-items-store]
                                      )
        ^Long index (first-index #(= (:no %) long-no) items)
        ]
    (on-ui
      (if (some? index)
        (let [^View list-item (.getChildAt list-view (+ index (.getHeaderViewsCount list-view)))]
          (if (some? list-item)
            (if-let [^EllipsizingTextView message-view (find-view list-item :messageView)]
              (do
                (set-entry-visited! cur-root-activity long-no)
                (config message-view
                      :text (Html/fromHtml msg-text)
                      :visibility View/VISIBLE)))
            (log/d "list item is nil!!! index=" index "size=" (.getChildCount list-view))))))))


(defn add-async-download-msg [seq-no]
  (future
    (Thread/sleep 1000)
    (doseq [long-no seq-no]
      (let [cur-root-activity @root-activity]
        ;(println "try download!" long-no)
        (if (some? cur-root-activity)
          (try
            (let [^String msg-text (http/download-message long-no (create-auth-info cur-root-activity))]
              (set-entry-message! cur-root-activity long-no msg-text)
              (set-message-text-in-tree! long-no msg-text)
              )
            (catch Exception e
              (log/d "error on download!" e)
              )
            )
          )

        )
      )
    )
  )

  ;(def download-channel (a/chan 5000))

  ;(defn add-async-download-msg [seq-no]
  ;  (doseq [no seq-no]
  ;    (a/>!! download-channel (str no))
  ;    (log/d "add to download-queue" no)
  ;    )
  ;  )
  ;
  ;(defn download-loop []
  ;  (a/go
  ;    (loop []
  ;      (let [no (a/<! download-channel)
  ;            cur-root-activity @root-activity
  ;            long-no (Long. no)
  ;            ]
  ;        (println "try download!" no)
  ;        (if (some? cur-root-activity)
  ;          (try
  ;            (let [msg-text (http/download-message no (create-auth-info cur-root-activity))]
  ;              (set-entry-message! cur-root-activity long-no msg-text)
  ;              (set-message-text-in-tree! long-no msg-text)
  ;              )
  ;            (catch Exception e
  ;              (log/d "error on download!" e)
  ;              )
  ;            )
  ;          )
  ;        )
  ;      (recur)
  ;      )
  ;    )
  ;  )
  ;
  ;(download-loop)
