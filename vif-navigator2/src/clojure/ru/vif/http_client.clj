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
           )
  )

(def vif-url "http://vif2ne.ru/nvk/forum/0/co/tree?xml=")
(def vif-message-prefix "http://vif2ne.ru/nvk/forum/0/co/")
(def TIMEOUT 10000)

(def FORCE_LOAD_STATUS "201")

(defn init-http-params []
  (let [httpParams (BasicHttpParams.)]
    (HttpConnectionParams/setConnectionTimeout httpParams TIMEOUT)
    (HttpConnectionParams/setSoTimeout httpParams TIMEOUT)
    httpParams
    )
  )

(def ^:private ^DefaultHttpClient get-http-client
  "Memoized function that returns an instance of HTTP client when called."
  (memoize (fn []
             (let [client (DefaultHttpClient. (init-http-params))]
               ;; Don't follow redirectsR
               (.setRedirectHandler
                 client (reify RedirectHandler
                          (getLocationURI [this response context] nil)
                          (isRedirectRequested [this response context] false)))
               client))))

(defn http-get
  "Sends a synchronous GET request."
  ([url]
   (http-get (get-http-client) url))
  ([^DefaultHttpClient client ^String url]
   (let [request (HttpGet. url)
         response (.execute client request)]
     {:status (.getStatusCode (.getStatusLine response))
      :body   (slurp (.getContent (.getEntity response)) :encoding "Cp1251")})))

(defn download-html [url]
  "Скачивает данные для url, если ответ не равен 200 то возвращает nil"
  (println "try download html=" url)
  (time (let [{status :status body :body} (http-get url)]
          (log/d "status=" status)
          (if (= status 200)
            body
            (throw (Exception. (str status)))
            )
          )
        )
  )

(defn download-message [^Long no]
  "Скачивает сообщение"
  (download-html (str vif-message-prefix no ".htm?plain"))
  )

(defn download-vif-content [^String last-event]
  "Скачивает дерево сообщений целиком"
  (download-html (str vif-url last-event))
  )
