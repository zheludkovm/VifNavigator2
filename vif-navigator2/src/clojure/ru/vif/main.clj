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
            [ru.vif.answer :refer :all]
            [ru.vif.client-data :refer :all]
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
         [:button {:id                 ::expandMessage
                   :layout-to-right-of  ::depth
                   :layout-align-parent-top true
                   :layout-align-bottom ::caption
                   :layout-width       (calc-pixels activity 15)
                   :minHeight 0
                   :minWidth 0
                   :on-click           expand-collapse
                   :text ">"
                   :backgroundResource R$drawable/expand_button
                   }]
         [:text-view {:id                 ::caption
                      :layout-to-right-of ::expandMessage
                      :layout-to-left-of  ::expandButton
                      :min-lines          2
                      :on-click           show-message
                      }]
         [:button {:id                        ::expandButton
                   :layout-align-parent-right true
                   :layout-height             :wrap
                   :on-click                  show-message
                   }]


         [:text-view {:id                        :msg
                      :layout-below              ::caption
                      :layout-to-right-of        ::depth
                      :layout-align-parent-right true
                      :min-lines                 5
                      :on-click                  show-message
                      :text                      "test"
                      :visibility                View/GONE
                      }]
         ]
        )
      (fn [position view _ ^vif-display-entry data]
        (let [color-function (if is-root odd? even?)
              caption (find-view view ::caption)
              expandButton (find-view view ::expandButton)
              expandMessage (find-view view ::expandMessage)

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

          (.setPadding expandMessage 0 0 0 0)
          (config expandMessage
                  :tag (str no))
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





(defactivity ru.vif.TreeActivity
             ;"Создает корневое activity со списком сообщений"
             :key :main
             :on-create
             (fn [^Activity this bundle]
               (log/d "create tree activity")
               (on-ui
                 (set-content-view! this [:list-view {:id                 :main-list-view
                                                      :adapter            (make-adapter this root-tree-items-store true)
                                                      :backgroundResource R$color/even
                                                      }])

                 (setup-action-bar this {
                                         :title           (str-res-html this R$string/main_title)
                                         :display-options :show-title
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
                                                   (full-reset this)
                                                   )
                                 }
                          ]
                         [:item {
                                 :title          R$string/button_settings
                                 :show-as-action :never
                                 :on-click       (fn [_]
                                                   (launch-activity this 'ru.vif.SettingsActivity {})
                                                   )
                                 }
                          ]
                         [:item {
                                 :title          R$string/about_title
                                 :show-as-action :never
                                 :on-click       (fn [_]
                                                   (launch-activity this 'ru.vif.AboutActivity {})
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



(defactivity ru.vif.MsgActivity
             ;"Создает activity с текстом cообщения и дочерними собщениями в дереве"
             :key :msg
             :state (atom [])
             :on-create
             (fn [^Activity this bundle]
               (let [no (get-param-no this)
                     msg (get-activity-param this PARAM_MSG)
                     title (get-activity-param this PARAM_TITLE)
                     date (get-activity-param this PARAM_DATE)
                     tree-entry (get (:all-entries-map @tree-data-store) no)
                     state (.state this)
                     ]
                 (if (nil? tree-entry)
                   (.finish this)
                   (on-ui
                     (let [list-view-tree [:list-view {:id                 :msg-list-view
                                                       ;:adapter            (make-adapter this state false)
                                                       :backgroundResource R$color/even
                                                       }
                                           ]]
                       (set-content-view! this list-view-tree)
                       (let [list-view (find-view this :msg-list-view)]
                         (.addHeaderView list-view (neko.ui/make-ui this [:text-view {:text               (res-format-html this R$string/message_format
                                                                                                                           title
                                                                                                                           date
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
                                             :title           (res-format-html this R$string/title_format (:author tree-entry))
                                             :display-options [:home-as-up :show-title]
                                             })
                     )
                   )
                 )
               )

             :on-create-options-menu
             (fn [this menu]
               (let [has-not-visited (model-api/has-non-visited @tree-data-store (get-param-no this))
                     is-registered (get-stored-propery-boolean this IS_REGISTERED false)
                     menu-items [[:item {
                                         :icon           R$drawable/up
                                         :show-as-action :always
                                         :on-click       (fn [_] (show-up-message this (get-param-no this)))}]
                                 [:item {
                                         :icon           (if has-not-visited R$drawable/nonvisited R$drawable/nonvisited_disabled)
                                         :show-as-action :always
                                         :enabled        has-not-visited
                                         :on-click       (fn [_] (show-next-nonvisited this (get-param-no this)))}]
                                 [:item {
                                         :title          R$string/button_set_all_visited
                                         :show-as-action :never
                                         :on-click       (fn [_]
                                                           (future
                                                             (set-recursive-visited! this (get-param-no this))
                                                             (refresh-msg-activity this)
                                                             )
                                                           )}]
                                 [:item {
                                         :title          "Версия для печати"
                                         :show-as-action :never
                                         :on-click       (fn [_]
                                                           (launch-activity this 'ru.vif.PrintActivity {
                                                                                                        PARAM_PRINT_NO (get-activity-param this PARAM_NO)
                                                                                                        })
                                                           )}]

                                 ]
                     menu-items-updated (if is-registered
                                          (concat menu-items [[:item {
                                                                      :title          "Ответить"
                                                                      :show-as-action :never
                                                                      :on-click       (fn [_]
                                                                                        (launch-activity this 'ru.vif.AnswerActivity {
                                                                                                                                      PARAM_ANSWER_TO_NO    (get-activity-param this PARAM_NO)
                                                                                                                                      PARAM_ANSWER_TO_MSG   (get-activity-param this PARAM_MSG)
                                                                                                                                      PARAM_ANSWER_TO_TITLE (get-activity-param this PARAM_TITLE)
                                                                                                                                      })
                                                                                        )}]])
                                          menu-items
                                          )

                     ]
                 (safe-for-ui
                   (menu/make-menu menu menu-items-updated)))
               )

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

