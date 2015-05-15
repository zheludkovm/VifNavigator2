(ns ru.vif.tools
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
            [neko.data :as data]
            [clojure.stacktrace :as stacktrace :refer :all]

            [ru.vif.http-client :as http :refer :all]
            [ru.vif.model.api :as model-api :refer :all]
            )
  (:import
    (ru.vif.model.records vif-display-entry)
    (android.app AlertDialog$Builder)
    (android.content DialogInterface$OnClickListener SharedPreferences)
    )
  )

(neko.resource/import-all)

(import android.content.Intent)
(import android.text.Html)
(import android.view.View)
(import android.text.method.ScrollingMovementMethod)
(import android.text.method.LinkMovementMethod)
(import android.graphics.Color)
(import android.graphics.drawable.ColorDrawable)
(import android.app.Activity)
(import android.content.Context)
(import ru.vif.R)
(import android.preference.PreferenceManager)

(def NOT_DEFINED_START_EVENT "-1")

(def LAST_EVENT "last-event")

(defn str-res
  (^String [^Activity this ^Integer res]
   "Загружает ресурсы android приолжения"
   (-> this
       .getResources
       (.getText res)
       )
    )
  )

(defn from-html [^String html]
  "Генерирует spinner"
  (Html/fromHtml html)
  )

(def str-res-html
  "загружает ресурс и генерирует spinner"
  (comp from-html str-res)
  )

(defn res-format
  (^String [this res & args]
   "загружает ресурс и применяет в качестве формата"
   (apply format (cons (str-res this res) args))
    )
  )

(def res-format-html
  "загружает ресурс, форматирует и преобразует результат как html"
  (comp from-html res-format)
  )

(defn repeat_void [depth]
  "генерит нужное количество отступов"
  (apply str (repeat (* 3 depth) " "))
  )


(defn launch-activity [a launch-activity params]
  "Запуск activity с набором параметров"
  (let [^Intent intent (Intent. a (resolve launch-activity))]
    (doseq [[^String key ^String value] params]
      (.putExtra intent key value)
      )
    (.startActivity a intent)
    )
  )

(defn launch-root-activity [a launch-activity]
  "Запуск activity с набором параметров"
  (let [^Intent intent (Intent. a (resolve launch-activity))]
    ;(.addFlags intent (bit-or Intent/FLAG_ACTIVITY_CLEAR_TOP Intent/FLAG_ACTIVITY_NEW_TASK)) ;
    (.addFlags intent (bit-or Intent/FLAG_ACTIVITY_CLEAR_TOP Intent/FLAG_ACTIVITY_SINGLE_TOP)) ;
    (.startActivity a intent)
    (.finish a)
    )
  )


(defn get-activity-param [activity paramName]
  "получить именованый параметр activity"
  (.. activity getIntent (getStringExtra paramName))
  )

(defn with-progress [this message-res error-message-res function]
  "Отображает диалог с сообщением пока идет обработка в фооне, в случае возникновения ошибки - выдает длинный toast"
  (let [^String message (str-res this message-res)
        ^String error-message (str-res this error-message-res)
        ^String error-auth (str-res this R$string/error_auth)
        dialog (atom nil)]
    (on-ui (let [pb (neko.ui/make-ui this [:progress-dialog
                                           {:progress-style :spinner
                                            :message        message}
                                           ])
                 ]
             (.show pb)
             (reset! dialog pb)
             )
           )
    (try
      (let [result (function)]
        (on-ui (.hide @dialog)
               (.dismiss @dialog)
               )
        result
        )
      (catch Exception e
        (log/d (str "caught exception: " (.getMessage e)))
        (on-ui
          (.hide @dialog)
          (notify/toast this
                        (if (= (.getMessage e) "401")
                          error-auth
                          error-message
                          )
                        :long)
          )
        (throw e)
        )
      )
    )
  )

(defn if-bold [is-marked title]
  (if is-marked (str "<b>" title "<b>") title)
  )

(defn calc-post-weight [^vif-display-entry entry]
  "Расчет веса сообщения для сортировки, используется максимальны номер сообщения их ветки"
  (let [^Boolean is-top-fixed (:is-top-fixed entry)
        ^Long max-child-no (:max-child-no entry)
        ]
    (if is-top-fixed
      (- (+ max-child-no 1000000000))
      (- max-child-no)
      )
    )
  )

(defn sort-tree
  "Отсортировать дерево"
  [tree]
  (sort-by #(calc-post-weight %) tree)
  )

(defn prefs [this]
  (data/get-shared-preferences this "local" :private))

(defn set-shared-pref-value!
  "Сохраненнить данные приложения"
  [this key value]

  (-> this
      prefs
      .edit
      (data/assoc! key value)
      .commit
      )
  )

(defn get-shared-pref-value
  "Возвращает сохраненные данные приложения"
  [this key default-value]
  (-> this
      prefs
      (.getString key default-value)
      )
  )

(defn get-shared-preferencies [this]
  (PreferenceManager/getDefaultSharedPreferences this)
  )

(defn get-stored-propery-long [this name default]
  (Long. (.getString (get-shared-preferencies this) name (str default)))
  )

(defn get-stored-propery-boolean [this name default]
  (.getBoolean (get-shared-preferencies this) name default)
  )

(defn get-stored-propery-string [this name default]
  (.getString (get-shared-preferencies this) name default)
  )

(defn with-try
  "Обернуть с распечаткой исключения"
  [func]
  (try
    (func)
    (catch Exception e
      (log/d (str "caught exception: " (.getMessage e)))
      (stacktrace/print-cause-trace e)
      )
    )
  )


(defn show-confirmation-dialog [this positive-func]
  "Показать диалог Вы уверены ?"
  (safe-for-ui
    (-> (AlertDialog$Builder. this)
        (.setMessage "Вы уверены?")
        (.setCancelable true)
        (.setPositiveButton "Да" (reify DialogInterface$OnClickListener
                                   (onClick [_ dialog id]
                                     (.dismiss dialog)
                                     (positive-func))))
        (.setNegativeButton "Нет" (reify DialogInterface$OnClickListener
                                    (onClick [_ dialog id]
                                      (.cancel dialog))))
        .create
        .show)))

(defactivity ru.vif.SettingsActivity
             :key :settings
             :extends android.preference.PreferenceActivity
             :on-create
             (fn [this bundle]
               (.setTitle this  ru.vif.R$string/settings_title)
               (.addPreferencesFromResource this ru.vif.R$xml/preferences) ;
               )
             )

