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
           [org.apache.http.client.methods HttpGet HttpPost]
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

(def TIMEOUT 10000)

(def FORCE_LOAD_STATUS "201")

(defrecord auth-info [^Boolean use-auth ^String login ^String password])

(def no-auth (auth-info. false nil nil))

(defn make-str-auth-info [^auth-info auth-info]
  (str (:use-auth auth-info) (:login auth-info) (:password auth-info))
  )


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


(defn create-get-header [^String url ^auth-info auth]
  (let [^HttpGet request (HttpGet. url)]
    (if (:use-auth auth)
      (let [^String credentials (str (:login auth) ":" (:password auth))
            base64EncodedCredentials (Base64/encodeToString (.getBytes credentials) Base64/NO_WRAP)
            ]
        (log/d "auth header=" base64EncodedCredentials)
        (.addHeader request "Authorization" (str "Basic " base64EncodedCredentials))))
    request
    )
  )

(defn http-get
  "Sends a synchronous GET request."
  ([^String url ^auth-info auth]
   (http-get (:http-client @connection) url auth))

  ([^String url ]
   (http-get (:http-client @connection) url no-auth))

  ([^DefaultHttpClient client ^String url ^auth-info auth]
   (let [request (create-get-header url auth)
         response (.execute client request)
         status (.getStatusCode (.getStatusLine response))]
     (log/d "response status=" status)
     {:status status
      :body   (slurp (.getContent (.getEntity response)) :encoding "Cp1251")})))

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


(defn download-html [^String url ^auth-info auth]
  "Скачивает данные для url, если ответ не равен 200 то возвращает nil"
  (check-connection! auth)
  (log/d "try download html=" url)
  (time (let [{status :status body :body} (http-get url)]
          (log/d "status=" status)
          (if (= status 200)
            body
            (throw (Exception. (str status)))
            )
          )
        )
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
