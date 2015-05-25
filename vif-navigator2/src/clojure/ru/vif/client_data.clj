(ns ru.vif.client-data
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
    (android.content SharedPreferences$OnSharedPreferenceChangeListener SharedPreferences)))

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

(defn check-store-loaded!
  "Проверяет были ли загружены записи из базы и если нет, то загружает"
  [this]
  (let [last-event (:last-event @tree-data-store)]
    (if (= last-event NOT_DEFINED_START_EVENT)
      (reset! tree-data-store (with-progress this R$string/init_tree R$string/error_init_tree #(load-stored-vif-tree this)))
      )
    )
  )

(defn show-message-by-no
  "Скачивает сообщение (с прогресс баром), разбирает его и запускает в окне просмотра"
  [^Activity activity ^Long no]

  (future
    (let [msg (with-progress activity R$string/process_loading_message R$string/error_loading_message #(http/download-message no (create-auth-info activity)))
          tree-entry (get (:all-entries-map @tree-data-store) no)
          title (:title tree-entry)
          date (:date tree-entry)
          ]
      (set-entry-visited! activity no)
      (on-ui (launch-activity activity 'ru.vif.MsgActivity {PARAM_MSG   msg
                                                            PARAM_NO    (str no)
                                                            PARAM_TITLE title
                                                            PARAM_DATE  date
                                                            }))
      )
    )
  )

(defn show-message
  "Скачивает сообщение (с прогресс баром), разбирает его и запускает в окне просмотра"
  [^Activity activity ^TextView caption]

  (show-message-by-no activity (Long. (.getTag caption)))
  )



(defn go-top
  "перейти в корень"
  [^Activity activity]
  (on-ui (launch-root-activity activity 'ru.vif.TreeActivity))
  )

(defn clean-tree
  "Сброс текущих записей и базы"
  [this]
  (reset! tree-data-store void-tree)
  (delete-all this)
  )



(defn load-tree
  "Загрузка xml обновления, в случае 201 ошибки, полная перегрузка данных"
  [this last-event]

  (let [auth (create-auth-info this)]
    (try
      (download-vif-content last-event auth)
      (catch Exception e
        (if (= (.getMessage e) FORCE_LOAD_STATUS)
          (doall
            (clean-tree this)
            (download-vif-content NOT_DEFINED_START_EVENT auth))
          (throw e)
          )
        ))))



(defn full-reload
  "Полная перегрузка всего дерева сообщений"
  ([this]
   (full-reload this true))


  ([this reload-tree]
   (future
     (check-store-loaded! this)
     (let [body (with-progress this
                               R$string/process_loading_tree
                               R$string/error_loading_tree
                               #(load-tree this (:last-event @tree-data-store)))
           ^vif-tree new-tree-data-store (with-progress this R$string/process_tree R$string/error_process_tree
                                                        (fn []
                                                          (let [xml-entries (model-api/parse-xml-entries body)
                                                                merged-tree-store (model-api/merge-trees @tree-data-store xml-entries)]
                                                            (store-persistant-data! this merged-tree-store xml-entries)
                                                            merged-tree-store
                                                            )))
           ]
       (on-ui
         (reset! tree-data-store new-tree-data-store)
         (if reload-tree (reset! root-tree-items-store (calc-main-sub-tree)))
         )
       )
     )
    )
  )

(defn refresh-adapter-data
  "перегружает данные адаптера listview и по возможности восстанавлиает положение скролбара"
  [list-view tree-items-atom-store tree-data]

  (let [visible-position (.getFirstVisiblePosition list-view)]
    (on-ui
      (reset! tree-items-atom-store tree-data)
      (.setSelection list-view visible-position)
      )
    )
  )


(defn show-next-nonvisited
  "Показать следующее непосещенное сообщение"
  [this no]
  (if-let [not-visited-no (model-api/find-first-non-visited-after @tree-data-store no)]
    (show-message-by-no this not-visited-no)
    )
  )

(defn full-reset [this]
  (let [visited-seq (model-api/get-all-visited @tree-data-store ROOT)]
    (clean-tree this)
    (future
      @(full-reload this false)
      (store-subtree-visited! this visited-seq)
      (swap! tree-data-store model-api/set-all-visited! visited-seq)
      (reset! root-tree-items-store (calc-main-sub-tree))
      )
    )
  )

;(defn create-settings-change-listener [this]
;  (proxy [SharedPreferences$OnSharedPreferenceChangeListener] []
;    (onSharedPreferenceChanged [^SharedPreferences sharedPreferences ^String key]
;      (if (not= key SETTINGS_DEPTH)
;        (full-reset this)
;        )
;      )))
;(def settings-change-listener (atom nil))

(defn refresh-tree-activity
  "Обновить список веток на основной форме"
  [^Activity this]
  (refresh-adapter-data
    (find-view this :main-list-view)
    root-tree-items-store
    (calc-main-sub-tree)))

(defn get-param-no [this]
  (Long. (get-activity-param this PARAM_NO)))

(defn refresh-msg-activity
  "Обновить список веток на форме сообщения"
  [this]

  ;(full-reload this)
  (refresh-adapter-data
    (find-view this :msg-list-view)
    (.state this)
    (calc-sub-tree (get-param-no this) (get-stored-propery-long this SETTINGS_DEPTH EXPAND_DEPTH))
    )
  )

(defn show-up-message
  "Перейти на уровень выше"
  [^Activity this ^Long no]
  (let [parent-no (-> @tree-data-store
                      :all-entries-map
                      (get no)
                      :parent)]
    (if (= 0 parent-no)
      (go-top this)
      (show-message-by-no this parent-no)
      )
    )
  )