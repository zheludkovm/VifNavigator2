(ns ru.vif.main
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
    (ru.vif.model.records vif-xml-entry parse-data vif-tree vif-display-entry)))

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

(def EXPAND_DEPTH 5)
(def MAIN_DEPTH 1)
(def PARAM_NO "no")
(def PARAM_MSG "msg")



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

(defn show-message
  "Скачивает сообщение (с прогресс баром), разбирает его и запускает в окне просмотра"
  [^Activity activity ^TextView caption]

  (future
    (let [no (.getTag caption)
          msg (with-progress activity R$string/process_loading_message R$string/error_loading_message #(http/download-message no))
          no-long (Long. no)
          ]
      (set-entry-visited! activity no-long)
      (on-ui (launch-activity activity 'ru.vif.MsgActivity {PARAM_MSG msg PARAM_NO no}))
      )
    )
  )

(defn go-top[^Activity activity]
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

  (try
    (download-vif-content last-event)
    (catch Exception e
      (if (= (.getMessage e) FORCE_LOAD_STATUS)
        (doall
          (clean-tree this)
          (download-vif-content NOT_DEFINED_START_EVENT))
        (throw e)
        )
      ))
  )



(defn full-reload
  "Полная перегрузка всего дерева сообщений"
  [this]

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
        (reset! root-tree-items-store (calc-main-sub-tree))
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

;------------gui creation-----------------

(defn make-adapter
  "Создает адаптер для listview со списком сообщений"
  [activity data-store ^Boolean is-root]

  (let [show-message (partial show-message activity)
        ^String format-bold-black (str-res activity R$string/format_bold_black)
        ^String visited-mark (str-res activity R$string/visited_mark)
        ^String not-visited-mark (str-res activity R$string/not_visited_mark)
        ^String not-visited-count-format (str-res activity R$string/not_visited_count)
        ^String not_visited_mark_zero (str-res activity R$string/not_visited_mark_zero)
        ^String child-count-format (str-res activity R$string/child_count_format)
        ]
    (ref-adapter
      (fn [_]
        [:relative-layout {:id-holder true :layout-width :fill}
         [:text-view {:id                       ::depth
                      :layout-align-parent-left true
                      }]
         [:text-view {:id                 ::caption
                      :layout-to-right-of ::depth
                      :layout-to-left-of  ::expandButton
                      :min-lines          2
                      :on-click           show-message
                      }]
         [:button {:id                        ::expandButton
                   :layout-align-parent-right true
                   :layout-height             :wrap
                   :on-click                  show-message
                   }]
         ]
        )
      (fn [position view _ ^vif-display-entry data]
        (let [color-function (if is-root odd? even?)
              caption (find-view view ::caption)
              expandButton (find-view view ::expandButton)

              child-count (:child-count data)
              no (:no data)
              depth-value (:depth data)
              is-visited (:is_visited data)
              size (:size data)
              non-visited-child-count (:non-visited-child-count data)
              ]
          ;фон, четный и нечетный
          (config view
                  :backgroundResource (if (color-function position) R$color/odd R$color/even))
          ;отступы
          (if (> depth-value 0)
            (config (find-view view ::depth) :text (repeat_void (:depth data)))
            )
          ;текст
          (config caption
                  :text (Html/fromHtml (str (if-bold (:is-marked data) (:title data))
                                            (format format-bold-black (:author data))
                                            (if is-visited
                                              visited-mark
                                              (if (= size 0)
                                                (do
                                                  (set-entry-visited! activity no)
                                                  not_visited_mark_zero
                                                  )
                                                not-visited-mark)
                                              )
                                            )
                                       )
                  :tag (str no)
                  )
          ;кнопка
          (config expandButton
                  :visibility (if (> child-count 0)
                                View/VISIBLE
                                View/GONE
                                )
                  :text (Html/fromHtml
                          (str (if (> non-visited-child-count 0)
                                 (format not-visited-count-format non-visited-child-count)
                                 )
                               (format child-count-format child-count)
                               )
                          )
                  :tag (str no)
                  )
          )

        )
      data-store
      identity
      )
    )
  )

(defn refresh-tree-activity
  "Обновить список веток на основной форме"
  [this]
  (refresh-adapter-data
    (find-view this ::main-list-view)
    root-tree-items-store
    (calc-main-sub-tree))
  )


(defactivity ru.vif.TreeActivity
             ;"Создает корневое activity со списком сообщений"
             :key :main
             :on-create
             (fn [^Activity this bundle]
               (log/d "create tree activity")
               (on-ui
                 (set-content-view! this [:list-view {:id                 ::main-list-view
                                                      :adapter            (make-adapter this root-tree-items-store true)
                                                      :backgroundResource R$color/even
                                                      }])

                 (setup-action-bar this {
                                         :title              (str-res-html this R$string/main_title)
                                         :backgroundDrawable (.. this getResources (getDrawable R$color/odd))
                                         :display-options    :show-title
                                         })
                 )
               (full-reload this)
               )
             :on-create-options-menu
             (fn [this menu]
               (safe-for-ui
                 (menu/make-menu
                   menu [[:item {
                                 :icon           R$drawable/redraw
                                 :show-as-action :always
                                 :on-click       (fn [_] (full-reload this))}]
                         [:item {
                                 :title          R$string/button_set_all_visited
                                 :show-as-action :never
                                 :on-click       (fn [_]
                                                   (future (set-recursive-visited! this 0)
                                                           (refresh-tree-activity this)
                                                           )
                                                   )
                                 }
                          ]
                         [:item {
                                 :title          R$string/button_reset
                                 :show-as-action :never
                                 :on-click       (fn [_]
                                                   (show-confirmation-dialog this (fn []
                                                                                    (clean-tree this)
                                                                                    (full-reload this)
                                                                                    )
                                                                             )
                                                   )
                                 }
                          ]
                         ]
                   )
                 )
               )
             :on-resume
             refresh-tree-activity

             :on-stop
             (fn [this]
               (log/d "stop!")
               )
             )

(defn get-param-no [this]
  (Long. (get-activity-param this PARAM_NO)))

(defn refresh-msg-activity
  "Обновить список веток на форме сообщения"
  [this]
  (refresh-adapter-data
    (find-view this ::msg-list-view)
    (.state this)
    (calc-sub-tree (get-param-no this) EXPAND_DEPTH)
    )
  )

(defactivity ru.vif.MsgActivity
             ;"Создает activity с текстом cообщения и дочерними собщениями в дереве"
             :key :msg
             :state (atom [])
             :on-create
             (fn [^Activity this bundle]
               (let [no (get-param-no this)
                     msg (get-activity-param this PARAM_MSG)
                     tree-entry (get (:all-entries-map @tree-data-store) no)
                     state (.state this)
                     ]
                 (if (nil? tree-entry)
                   (.finish this)
                   (on-ui
                     (let [list-view-tree [:list-view {:id                 ::msg-list-view
                                                       ;:adapter            (make-adapter this state false)
                                                       :backgroundResource R$color/even
                                                       }
                                           ]]
                       (set-content-view! this list-view-tree)
                       (let [list-view (find-view this ::msg-list-view)]
                         (.addHeaderView list-view (neko.ui/make-ui this [:text-view {:text               (res-format-html this R$string/message_format
                                                                                                                           (:title tree-entry)
                                                                                                                           (:date tree-entry)
                                                                                                                           msg
                                                                                                                           )
                                                                                      :max-lines          10000
                                                                                      :backgroundResource R$color/even
                                                                                      :movement-method    (LinkMovementMethod.)
                                                                                      }
                                                                          ]))
                         (.setAdapter list-view (make-adapter this state false))
                         )

                       )

                     (setup-action-bar this {
                                             :title              (res-format-html this R$string/title_format (:author tree-entry))
                                             :backgroundDrawable (.. this getResources (getDrawable R$color/odd))
                                             :display-options    [:home-as-up :show-title]
                                             })
                     )
                   )
                 )
               )

             :on-create-options-menu
             (fn [this menu]
               (safe-for-ui
                 (menu/make-menu
                   menu [[:item {
                                 :title          R$string/button_set_all_visited
                                 :show-as-action :never
                                 :on-click       (fn [_]
                                                   (future
                                                     (set-recursive-visited! this (get-param-no this))
                                                     (refresh-msg-activity this)
                                                     )
                                                   )
                                 }
                          ]
                         ])))

             :on-options-item-selected
             (fn [^Activity this item]
               ;(.finish this)
               (go-top this)
               )
             :on-resume
             refresh-msg-activity

             :on-restart
             (fn [^Activity this]
               (let [no (get-param-no this)
                     entity (get (:all-entries-map @tree-data-store) no)
                     ]
                 (if (nil? entity)
                   (.finish this)
                   )
                 )
               )

             )

