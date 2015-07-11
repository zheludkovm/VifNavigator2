(ns ru.vif.http-client
  (:require [neko.log :as log]
            [clojure.java.io :as io]
            )
  (:import android.app.Activity
           android.util.Xml
           java.io.FileNotFoundException
           java.io.StringReader
           java.net.InetAddress
           org.apache.http.client.RedirectHandler
           org.apache.http.client.entity.UrlEncodedFormEntity
           [org.apache.http.client.methods HttpGet HttpPost HttpRequestBase]
           org.apache.http.cookie.Cookie
           org.apache.http.impl.client.DefaultHttpClient
           org.apache.http.message.BasicNameValuePair
           org.apache.http.params.CoreProtocolPNames
           org.apache.http.params.HttpConnectionParams
           org.apache.http.params.HttpParams
           org.apache.http.params.BasicHttpParams
           [org.xmlpull.v1 XmlPullParser XmlPullParserFactory]

           android.content.res.AssetManager
           (android.util Base64))
  )

(def vif-url "http://vif2ne.ru/nvk/forum/0/co/tree?xml=")
(def vif-message-prefix "http://vif2ne.ru/nvk/forum/0/co/")
(def security-url "http://vif2ne.ru/nvk/forum/security")
(def preview-url "http://vif2ne.ru/nvk/forum/0/security/preview/")
(def print-url "http://vif2ne.ru/nvk/forum/0/print/")
(def htm-suffix ".htm")

(def TIMEOUT 10000)

(def FORCE_LOAD_STATUS "201")

(defrecord auth-info [^Boolean use-auth ^String login ^String password])

(def no-auth (auth-info. false nil nil))

(defn make-str-auth-info [^auth-info auth-info]
  (str (:use-auth auth-info) (:login auth-info) (:password auth-info))
  )

(defn slurp-bytes
  "Slurp the bytes from a slurpable thing"
  [is]
  (with-open [out (java.io.ByteArrayOutputStream.)]
    (clojure.java.io/copy is out)
    (.toByteArray out)))


(defn init-http-params []
  (let [httpParams (BasicHttpParams.)]
    (HttpConnectionParams/setConnectionTimeout httpParams TIMEOUT)
    (HttpConnectionParams/setSoTimeout httpParams TIMEOUT)
    httpParams
    )
  )

(def ^:private ^DefaultHttpClient get-http-client
  "Memoized function that returns an instance of HTTP client when called."
  (fn []
    (let [client (DefaultHttpClient. (init-http-params))]
      ;; Don't follow redirectsR
      (.setRedirectHandler
        client (reify RedirectHandler
                 (getLocationURI [this response context] nil)
                 (isRedirectRequested [this response context] false)))
      client)))

(def connection
  (atom {:http-client   nil
         :auth-info-str nil
         })
  )

(defn calc-basic-auth-header[^auth-info auth]
  (let [^String credentials (str (:login auth) ":" (:password auth))]
    (str "Basic " (Base64/encodeToString (.getBytes credentials) Base64/NO_WRAP))))

(defn add-basic-auth! [^HttpRequestBase request ^auth-info auth]
  (if (:use-auth auth)
    (let [base64EncodedCredentials (calc-basic-auth-header auth)]
      (log/d "auth header=" base64EncodedCredentials)
      (.addHeader request "Authorization" base64EncodedCredentials)))
  request
  )

(defn auth-headers[^auth-info auth]
  (if (:use-auth auth)
    {"Authorization" (calc-basic-auth-header auth)}
    {}
    )
  )


(defn call-http
  ([^DefaultHttpClient client ^String url ^HttpRequestBase request]
   (let [response (.execute client request)
         status (.getStatusCode (.getStatusLine response))]
     (log/d "response status=" status)
     {:status status
      :body   (slurp (.getContent (.getEntity response)) :encoding "Cp1251")})))


(defn create-get-command [^String url ^auth-info auth]
  (let [^HttpGet request (HttpGet. url)]
    (add-basic-auth! request auth)
    request
    )
  )

(defn http-get
  "Sends a synchronous GET request."
  ([^String url ^auth-info auth]
   (http-get (:http-client @connection) url auth))

  ([^String url]
   (http-get (:http-client @connection) url no-auth))

  ([^DefaultHttpClient client ^String url ^auth-info auth]
   (call-http client url (create-get-command url auth))))

(defn create-post-command [^String url, ^auth-info auth, params]
  (let [^HttpPost request (HttpPost. url)
        name-value-pair-seq (for [[key value] params]
                              (BasicNameValuePair. key value))
        name-value-pair-list (java.util.ArrayList. name-value-pair-seq)
        form-entity (UrlEncodedFormEntity. name-value-pair-list "CP1251")
        ]
    (println (slurp (.getContent form-entity)))
    (add-basic-auth! request auth)
    (.setEntity request form-entity)
    request
    )
  )

(defn http-post
  "Sends a synchronous GET request."
  [^String url ^auth-info auth params]
  (call-http (:http-client @connection) url (create-post-command url auth params)))
;(create-post-command url auth params))




(defn get-http-client-try-auth
  "Проверяет корректность аутентификации и если все успешно, то возвращает клиента"
  [^auth-info auth]
  (let [http-client (get-http-client)]
    (if (:use-auth auth)
      (let [{status :status} (http-get http-client security-url auth)]
        (if (not= status 302)
          (throw (Exception. (str status)))
          http-client))
      http-client)))

(defn check-connection! [^auth-info auth-info]
  (let [auth-info-str (make-str-auth-info auth-info)
        current-info-str (:auth-info-str @connection)
        ]
    (if (not= current-info-str auth-info-str)
      (reset! connection {:http-client   (get-http-client-try-auth auth-info)
                          :auth-info-str current-info-str})

      )
    )
  )

(defn call-and-check-connection
  ([^auth-info auth, call-func]
   "Скачивает данные для url, если ответ не равен 200 то возвращает nil"
   (check-connection! auth)
   ;(log/d "try call!")
   (time (let [{status :status body :body} (call-func)]
           ;(log/d "status=" status)
           (if (= status 200)
             body
             (throw (Exception. (str status)))
             )
           )
         ))
  )

(defn download-html [^String url ^auth-info auth]
  "Скачивает данные для url, если ответ не равен 200 то возвращает nil"
  (log/d "try download html=" url)
  (call-and-check-connection auth #(http-get url))
  )

(defn send-post [^String url ^auth-info auth params]
  "Скачивает данные для url, если ответ не равен 200 то возвращает nil"
  (log/d "try send to " url "params" params)
  (call-and-check-connection auth #(http-post url auth params))
  )

(defn download-message
  "Скачивает сообщение"
  [^Long no ^auth-info auth]

  (download-html (str vif-message-prefix no ".htm?plain") auth)
  )

(defn download-vif-content "Скачивает дерево сообщений целиком"
  [^String last-event ^auth-info auth]
  (download-html (str vif-url last-event) auth)
  )

(defn conj-if [coll flag value]
  (if flag
    (conj coll value)
    coll
    )
  )

(defn replace-newline [^String s]
  (.replace s "\n" "\r\n")
  )

(defn create-params [^String theme
                     ^Boolean to-root
                     ^String msg]
  (-> [["subject" (replace-newline theme)]]
      (conj-if to-root ["toplevel" "on"])
      (into [["hello" ""]
             ["bye" ""]
             ["body" (replace-newline msg)]
             ]
            ))
  )

(defn send-answer-http [^auth-info auth
                        ^String answer-to-no
                        ^String theme
                        ^Boolean to-root
                        ^String msg]
  (let [params (create-params theme to-root msg)]
    (send-post (str "http://vif2ne.ru/nvk/forum/0/security/reply/" answer-to-no) auth params)
    )
  )

(defn prepare-preview-request [^String theme
                               ^String msg]
  (let [params (create-params theme false msg)
        ^HttpPost post-request (create-post-command "" no-auth params)
        ]
    (slurp-bytes (.getContent (.getEntity post-request)))
    )
  )