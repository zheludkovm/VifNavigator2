(ns ru.vif.answer
  (:require [neko.activity :refer [defactivity set-content-view!]]
            [neko.debug :refer [safe-for-ui]]
            [neko.threading :refer [on-ui]]
            [neko.resource :refer [import-all get-resource]]
            [neko.ui.menu :as menu]
            [neko.log :as log]
            [neko.notify :as notify]
            [neko.ui.adapters :refer [ref-adapter]]
            [neko.find-view :refer [find-view find-views]]
            [neko.ui :refer [config make-ui-element]]
            [neko.ui.mapping :as mapping :refer [set-classname!]]
            [neko.action-bar :refer [setup-action-bar]]
            [clojure.java.io :as io]
            [clojure.string :as string]

            [ru.vif.model.api :as model-api :refer :all]
            [ru.vif.http-client :as http :refer :all]
            [ru.vif.tools :refer :all]
            [ru.vif.db-tools :refer :all]
            [ru.vif.client-data :refer :all]
            )
  (:import
    (android.content Intent)
    (android.app Activity)
    (android.view Gravity)
    (android.text Html)
    (android.webkit WebView WebViewClient HttpAuthHandler)
    (android.net Uri)))

(neko.resource/import-all)

(def PARAM_ANSWER_TO_NO "no")
(def PARAM_PRINT_NO "no")
(def PARAM_ANSWER_TO_TITLE "title")
(def PARAM_ANSWER_TO_MSG "msg")

(def PARAM_PREVIEW_THEME "preview-theme")
(def PARAM_PREVIEW_MSG "preview-msg")

(def RE_PREFIX "Re: ")

(defn get-textview-text [this id]
  (.toString (.getText (find-view this id))))



(defn send-answer [this]
  (println "Answer!")
  (future
    (let [answer-to-no (get-activity-param this PARAM_ANSWER_TO_NO)
          theme (get-textview-text this ::theme)
          msg (get-textview-text this ::msg)
          to-root (.isChecked (find-view this ::to-root))
          auth (create-auth-info this)
          ]
      (http/send-answer-http auth answer-to-no theme to-root msg)
      (on-ui
        @(full-reload this)
        (.finish this)))))

(defn prepare-answer [^String msg]
  (->> (Html/fromHtml msg)
       .toString
       string/split-lines
       (map (fn [s]
              (if (not (string/blank? s))
                (str ">" s)
                s
                )
              ))
       (string/join "\n")
       )
  )

(mapping/defelement :check-box
                    :classname android.widget.CheckBox
                    :traits [:id]
                    :attributes {:text ""}
                    )

(defactivity ru.vif.AnswerActivity
             ;"Создает activity с ответом
             :key :answer
             :on-create
             (fn [^Activity this bundle]
               (let [answer-to-no (get-activity-param this PARAM_ANSWER_TO_NO)
                     answer-to-msg (get-activity-param this PARAM_ANSWER_TO_MSG)
                     answer-to-title (get-activity-param this PARAM_ANSWER_TO_TITLE)
                     even-color (.. this getResources (getDrawable R$color/even))
                     odd-color (.. this getResources (getDrawable R$color/odd))
                     ]

                 (on-ui
                   (set-content-view! this [:scroll-view {:fillViewport true}
                                            [:linear-layout {:orientation        :vertical
                                                             :backgroundDrawable odd-color}
                                             [:edit-text {:id                 ::theme
                                                          :hint               R$string/hint_theme
                                                          :layout-width       :fill
                                                          :backgroundDrawable even-color
                                                          :text               (str RE_PREFIX answer-to-title)
                                                          :min-lines          3
                                                          :gravity            Gravity/TOP
                                                          }]
                                             [:check-box {:id                 ::to-root
                                                          :text               R$string/check_box_to_root
                                                          :layout-width       :fill
                                                          :backgroundDrawable odd-color
                                                          }]
                                             [:edit-text {:id                          ::msg
                                                          :hint                        R$string/hint_msg
                                                          :text                        (prepare-answer answer-to-msg)
                                                          :min-lines                   8
                                                          :vertical-scroll-bar-enabled true
                                                          :layout-width                :fill
                                                          :layout-height               :fill
                                                          :backgroundDrawable          even-color
                                                          :gravity                     Gravity/TOP
                                                          }]
                                             ]])
                   (setup-action-bar this {
                                           :title           R$string/answer_title
                                           :display-options :show-title
                                           })
                   )
                 )
               )
             :on-create-options-menu
             (fn [this menu]
               (safe-for-ui
                 (menu/make-menu
                   menu [[:item {:icon           R$drawable/ok
                                 :show-as-action :always
                                 :on-click       (fn [_] (show-confirmation-dialog this #(send-answer this)))}]
                         [:item {:icon           R$drawable/cancel
                                 :show-as-action :always
                                 :on-click       (fn [_] (.finish this))}]

                         [:item {
                                 :title          R$string/preview_title
                                 :show-as-action :never
                                 :on-click       (fn [_] (launch-activity this 'ru.vif.PreviewActivity {
                                                                                                        PARAM_ANSWER_TO_NO  (get-activity-param this PARAM_ANSWER_TO_NO)
                                                                                                        PARAM_PREVIEW_THEME (get-textview-text this ::theme)
                                                                                                        PARAM_PREVIEW_MSG   (get-textview-text this ::msg)}))}]
                         [:item {
                                 :title          R$string/button_clean
                                 :show-as-action :never
                                 :on-click       (fn [_]
                                                   (let [msg-edit-text (find-view this ::msg)]
                                                     (.setText msg-edit-text "")
                                                     )
                                                   )}]
                         ]
                   )
                 )
               )

             )

(defn simple-auth-client [^String login ^String password]
  (proxy [WebViewClient] []
    (onReceivedHttpAuthRequest [^WebView view
                                ^HttpAuthHandler handler
                                ^String host
                                ^String realm
                                ]
      (.proceed handler login password)
      )
    (shouldOverrideUrlLoading [^WebView view, ^String url]
      true
      )
    ))

(defn external-web-client [^String login ^String password]
  (proxy [WebViewClient] []
    (onReceivedHttpAuthRequest [^WebView view
                                ^HttpAuthHandler handler
                                ^String host
                                ^String realm
                                ]
      (.proceed handler login password)
      )
    (shouldOverrideUrlLoading [^WebView view, ^String url]
      (.startActivity (.getContext view) (Intent. Intent/ACTION_VIEW, (Uri/parse url)))
      true
      )
    ))


(defn create-web-view [title call-http-func auth-client]
  (fn create-web-view [^Activity this bundle]
    (let [
          even-color (.. this getResources (getDrawable R$color/even))
          odd-color (.. this getResources (getDrawable R$color/odd))
          auth (create-auth-info this)
          ]

      (on-ui
        (set-content-view! this [:web-view {:id                 ::preview
                                            :backgroundDrawable odd-color
                                            :web-view-client    (auth-client (:login auth) (:password auth))}])
        (setup-action-bar this {:title              title
                                :backgroundDrawable even-color
                                :display-options    :show-title})
        (call-http-func this (find-view this ::preview))))))


(defactivity ru.vif.PreviewActivity
             ;"Создает activity с ответом
             :key :preview
             :on-create
             (create-web-view
               R$string/preview_title
               (fn [this web-view]
                 (.postUrl web-view
                           (str http/preview-url)
                           (http/prepare-preview-request
                             (get-activity-param this PARAM_ANSWER_TO_NO)
                             (get-activity-param this PARAM_PREVIEW_MSG))
                           )
                 )
               simple-auth-client
               )
             )

(defactivity ru.vif.PrintActivity
             ;"Создает activity с версией для печати
             :key :pruint
             :on-create
             (create-web-view
               R$string/print_title
               (fn [this ^WebView web-view]
                 (.loadUrl web-view
                           (str http/print-url (get-activity-param this PARAM_PRINT_NO) http/htm-suffix)
                           (http/auth-headers (create-auth-info this))
                           ))
               external-web-client
               ))
