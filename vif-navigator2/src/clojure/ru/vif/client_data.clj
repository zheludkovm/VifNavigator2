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
            [ru.vif.client-data-store :refer :all]

            [ru.vif.background :as background :refer [add-async-download-msg]]
            )
  (:import
    (ru.vif.model.records vif-xml-entry parse-data vif-tree vif-display-entry)
    (android.content SharedPreferences$OnSharedPreferenceChangeListener SharedPreferences)
    (android.widget Button)
    (ru.vif EllipsizingTextView)))

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
    (let [msg (if-let [check-msg (get-entry-message @tree-data-store no)]
                  check-msg
                  (set-entry-message! activity no
                                      (with-progress activity R$string/process_loading_message R$string/error_loading_message #(http/download-message no (create-auth-info activity))))
                )
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


(defn add-non-loaded-messages [visible-items]
  (let [non-loaded-seq-no (->> visible-items
                               (filter #(nil? (:message %)))
                               (map #(:no %))
                               )]
    (background/add-async-download-msg non-loaded-seq-no)))


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
         (if reload-tree
           (let [visible-items (calc-main-sub-tree)]
             (reset! root-tree-items-store visible-items)
             (add-non-loaded-messages visible-items))))))))

(defn refresh-adapter-data
  "перегружает данные адаптера listview и по возможности восстанавлиает положение скролбара"
  [^ListView list-view tree-items-atom-store tree-data]

  (log/d "refresh adapter data!" list-view)
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
      (add-non-loaded-messages @root-tree-items-store)
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
  (log/d "refresh tree-activity!")
  (refresh-adapter-data
    (find-view this :main-list-view)
    root-tree-items-store
    (calc-main-sub-tree)))

(defn get-param-no [this]
  (Long. (get-activity-param this PARAM_NO)))

(defn refresh-msg-activity
  "Обновить список веток на форме сообщения"
  [this check-non-load-messages]

  ;(full-reload this)
  (log/d "refresh msg activity!!!!!")
  (let [visible-items (calc-sub-tree (get-param-no this) (get-stored-propery-long this SETTINGS_DEPTH EXPAND_DEPTH))]
    (refresh-adapter-data
      (find-view this :msg-list-view)
      (.state this)
      visible-items
      )
    (if check-non-load-messages
      (add-non-loaded-messages visible-items)
      )
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

(defn check-expand [^Activity activity ^EllipsizingTextView text-view]
  (if (.isEllipsized text-view)
    (do
      (set-entry-visited! activity (Long. (.getTag text-view)))
      (config text-view :max-lines 10000))
    (show-message activity text-view)
    )
  )



