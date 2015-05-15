(ns ru.vif.answer
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
    (android.app Activity)))

(neko.resource/import-all)

(def PARAM_ANSWER_TO_NO "no")
(def PARAM_ANSWER_TO_TITLE "title")
(def PARAM_ANSWER_TO_MSG "msg")

(defactivity ru.vif.AnswerActivity
             ;"Создает activity с ответом
             :key :answer
             :on-create
             (fn [^Activity this bundle]
               (let [answer-to-no (get-activity-param this PARAM_ANSWER_TO_NO)
                     answer-to-msg (get-activity-param this PARAM_ANSWER_TO_MSG)
                     answer-to-title (get-activity-param this PARAM_ANSWER_TO_TITLE)
                     ]

                 (on-ui
                   (set-content-view! this [:list-view {:id                 ::msg-list-view
                                                        ;:adapter            (make-adapter this state false)
                                                        :backgroundResource R$color/even
                                                        }
                                            ])
                   (setup-action-bar this {
                                           :title              "Ответ :"
                                           :backgroundDrawable (.. this getResources (getDrawable R$color/odd))
                                           :display-options    [:home-as-up :show-title]
                                           })
                   )
                 )
               )

             )
