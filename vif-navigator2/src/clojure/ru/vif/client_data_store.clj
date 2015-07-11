(ns ru.vif.client-data-store
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
            )
  (:import
    (ru.vif.model.records vif-xml-entry parse-data vif-tree vif-display-entry)
    (android.content SharedPreferences$OnSharedPreferenceChangeListener SharedPreferences)
    (android.widget Button)))

(neko.resource/import-all)


(import android.content.Intent)
(import android.text.Html)
(import android.view.View)
(import android.text.method.ScrollingMovementMethod)
(import android.text.method.LinkMovementMethod)
(import android.graphics.Color)
(import android.graphics.drawable.ColorDrawable)
(import android.app.Activity)
(import android.widget.TextView)
(import android.widget.ListView)
(import ru.vif.http_client.auth-info)


(def EXPAND_DEPTH 5)
(def MAIN_DEPTH 1)
(def PARAM_NO "no")
(def PARAM_MSG "msg")
(def PARAM_TITLE "title")
(def PARAM_DATE "date")

(def SETTINGS_DEPTH "settings_depth")



(def tree-data-store
  (atom void-tree))

(def root-tree-items-store
  (atom []))




(defn calc-sub-tree
  "Отрезает от дерева листья глубже чем EXPAND_DEPTH/MAIN_DEPTH а также считает количество дочерних сообщений"
  [^Long no ^Long depth]

  (log/d "show " no)
  (if (some? no)
    (model-api/trim-tree-by-depth @tree-data-store no depth)
    )
  )

(defn calc-main-sub-tree
  "Собирает список веток и сортирует"
  []
  (sort-tree (calc-sub-tree 0 MAIN_DEPTH))
  )

(defn set-entry-visited!
  "Помечает запись как просмотренную"
  [this ^Long no]

  (swap! tree-data-store model-api/set-entry-visited no)
  (store-visited! this no)
  )

(defn set-entry-message! [this ^Long no ^String message]
  (log/d "set entry message" no)
  (swap! tree-data-store model-api/set-entry-message no message)
  (store-message! this no message)
  message
  )



(defn set-recursive-visited!
  "Помечает запись и все ее дочерние узлы как просмотренные"
  [this ^Long no]
  (with-progress this R$string/process_tree R$string/error_process_tree
                 (fn []
                   (let [no-seq (vif-tree-child-nodes @tree-data-store no)]
                     (if (= 0 no)
                       (store-all-visited! this)
                       (store-subtree-visited! this no-seq)
                       )
                     (swap! tree-data-store model-api/set-all-visited! no-seq)
                     )))
  )
