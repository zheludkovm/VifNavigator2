(ns ru.vif.model.vif-xml-tools
  (:require [clojure.string :refer [blank?]]
            [ru.vif.model.records :refer :all]

            )
  (:import (java.util Date)
           (clojure.lang Keyword IPersistentMap IPersistentSet ISeq ASeq)
           (org.xmlpull.v1 XmlPullParser XmlPullParserFactory)
           (java.io StringReader)
           (java.text SimpleDateFormat)
           (ru.vif.model.records vif-xml-entry parse-data)
           ))

(def ^SimpleDateFormat TIME_FORMAT (SimpleDateFormat. "dd.MM.yyyy HH:mm:ss"))

(def mode-constants {"0"   :mode-fix-deleted
                     "1"   :mode-fixed
                     "256" :mode-unfixed
                     nil   :mode-not-unfixed}
  )



(defn safe-parse-date [^String str]
  (if (some? str)
    (.getTime (.parse TIME_FORMAT str))
    nil
    )
  )

(defn safe-parse-long [^String str]
  (if (some? str)
    (Long/parseLong str 16)
    nil
    )
  )

(defn non-safe-parse-long [^String str]
  (if (some? str)
    (Long/parseLong str 16)
    0
    )
  )


(defn safe-keyword [^String str]
  (if (some? str)
    (keyword str)
    nil
    )
  )



(defn create-parser
  "Создает XmlPullParser"
  ^{:private true}

  (^XmlPullParser []
   (let [factory (XmlPullParserFactory/newInstance)]
     (.setNamespaceAware factory false)
     (.newPullParser factory)
     )
    ))


(defn add-attributes
  "Добавляет в map аттрибуты тега"
  ^{:private true}
  [current-result, ^XmlPullParser parser]

  (->> (range 0 (.getAttributeCount parser))
       (reduce (fn [result
                    ^Integer counter]
                 (assoc result (.getAttributeName parser (int counter))
                               (.getAttributeValue parser (int counter))))
               current-result
               )
       )
  )

(defn parse-one-simple-entry
  "Обрабатывает одно сообщение XMlPull парсера"
  ^{:private true}
  [^XmlPullParser parser, stop-tags]

  (loop [event (.next parser)
         ^String current-tag nil
         ^IPersistentMap result {}]
    (cond
      (= event XmlPullParser/START_TAG) (let [tag-name (.getName parser)
                                              updated-result (add-attributes result parser)]
                                          (recur (.next parser) tag-name updated-result)
                                          )

      (= event XmlPullParser/TEXT) (if (nil? current-tag)
                                     (recur (.next parser) current-tag result)
                                     (let [text (.getText parser)
                                           updated-result (assoc result current-tag text)]
                                       (recur (.next parser) current-tag updated-result)))

      (= event XmlPullParser/END_TAG) (let [tag-name (.getName parser)]
                                        (if (contains? stop-tags tag-name)
                                          result
                                          (recur (.next parser) nil result)))

      (= event XmlPullParser/END_DOCUMENT) nil

      :other (recur (.next parser) current-tag result))))


(defn create-vif-xml-entry
  "Создает экземпляр record с информацией об одном событии vif xml"
  ^{:private true}
  [^IPersistentMap value-map]

  (vif-xml-entry.
    (non-safe-parse-long (get value-map "no"))
    ;(get value-map "no")
    (safe-keyword (get value-map "type"))
    (get mode-constants (get value-map "mode"))
    (safe-parse-long (get value-map "parent"))
    ;(get value-map "parent")
    (get value-map "title")
    (get value-map "author")
    (get value-map "date")
    (safe-parse-long (get value-map "size"))
    nil
    false
    )
  )

(defn take-while-some [^ISeq seq]
  (take-while some? seq)
  )


(defn parse-xml-entries
  "Разбирает xml и возврщает sequence записей vif-xml"
  [^String xml]

  (let [^XmlPullParser parser  (create-parser)
          ^StringReader reader  (StringReader. xml)
          tmp (.setInput parser reader)
          [event-entry & all-antries] (->> (repeatedly (fn [] (parse-one-simple-entry parser #{"event" "lastEvent"})))
                                           (take-while-some)
                                           )
          last-event (get event-entry "lastEvent")
          ]
         (parse-data.
           (if (nil? last-event) "-1" last-event)
           (map create-vif-xml-entry all-antries)
           )
         )
  )