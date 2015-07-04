(ns ru.vif.model.vif-xml-tools
  (:require [clojure.string :refer [blank?]]
            [ru.vif.model.records :refer :all]
            [ru.vif.model.typed-libs :refer :all]
            [clojure.core.typed :as t]
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



(t/ann safe-parse-date [NillableString -> NillableLong])
(defn safe-parse-date [^String str]
  (if (some? str)
    (.getTime (.parse TIME_FORMAT str))
    nil
    )
  )

(t/ann safe-parse-long [NillableString -> NillableLong])
(defn safe-parse-long [^String str]
  (if (some? str)
    (Long/parseLong str 16)
    nil
    )
  )

(t/ann non-safe-parse-long [NillableString -> Long])
(defn non-safe-parse-long [^String str]
  (if (some? str)
    (Long/parseLong str 16)
    0
    )
  )

(t/ann safe-keyword [NillableString -> (t/U Keyword nil)])
(defn safe-keyword [^String str]
  (if (some? str)
    (keyword str)
    nil
    )
  )


(t/ann create-parser [-> XmlPullParser])
(defn create-parser
  "Создает XmlPullParser"
  ^{:private true}

  (^XmlPullParser []
   (let [factory (XmlPullParserFactory/newInstance)]
     (.setNamespaceAware factory false)
     (.newPullParser factory)
     )
    ))

(t/ann add-attributes [AttrMap XmlPullParser -> AttrMap])
(defn add-attributes
  "Добавляет в map аттрибуты тега"
  ^{:private true}
  [current-result, ^XmlPullParser parser]

  (->> (range 0 (.getAttributeCount parser))
       (reduce (t/fn [result :- AttrMap
                      ^Integer counter :- t/AnyInteger] :- AttrMap
                 (assoc result (.getAttributeName parser (int counter))
                               (.getAttributeValue parser (int counter))))
               current-result
               )
       )
  )

(t/ann parse-one-simple-entry [XmlPullParser StringSet -> NillableAttrMap])
(defn parse-one-simple-entry
  "Обрабатывает одно сообщение XMlPull парсера"
  ^{:private true}
  [^XmlPullParser parser, stop-tags]

  (t/loop [event :- Integer (.next parser)
           ^String current-tag :- NillableString nil
           ^IPersistentMap result :- AttrMap {}]
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


(t/ann create-vif-xml-entry [AttrMap -> vif-xml-entry])
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

(t/ann ^:no-check take-while-some  (t/All [x]
                              (t/IFn [(t/ASeq (t/U x nil))  -> (t/ASeq x )])
                              ))
(defn take-while-some [^ISeq seq]
  (take-while some? seq)
  )

(t/ann parse-xml-entries [String -> parse-data])
(defn parse-xml-entries
  "Разбирает xml и возврщает sequence записей vif-xml"
  [^String xml]

  (t/let [^XmlPullParser parser :- XmlPullParser (create-parser)
          ^StringReader reader :- StringReader (StringReader. xml)
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