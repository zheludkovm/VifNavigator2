(ns ru.vif.model.records
  (:require [clojure.core.typed :as t]
            )
  (:import (java.util Date)
           (clojure.lang Keyword IPersistentMap IPersistentSet)
           )
  )

(t/defalias NillableString (t/U nil String))
(t/defalias NillableLong (t/U nil Long))
(t/defalias NillableBoolean (t/U nil Boolean))
(t/defalias NillableKeyword (t/U nil Keyword))
(t/defalias AttrMap (IPersistentMap String NillableString))
(t/defalias NillableAttrMap (t/U AttrMap nil))
(t/defalias StringSet (IPersistentSet String))


(t/ann-record vif-xml-entry [no :- NillableLong
                             type :- NillableKeyword
                             mode :- NillableKeyword
                             parent :- NillableLong
                             title :- NillableString
                             author :- NillableString
                             date :- NillableString
                             size :- NillableLong
                             message :- NillableString
                             ;восстанавливается из базы, по умолчанию false
                             is_visited :- Boolean
                             ])
(defrecord vif-xml-entry [^Long no
                          ^Keyword type                     ; :add :del :parent :fix
                          ^Keyword mode                     ; mode-constants
                          ^Long parent
                          ^String title
                          ^String author
                          ^String date
                          ^Long size
                          ^String message
                          ;восстанавливается из базы, по умолчанию false
                          ^Boolean is_visited
                          ])

(t/ann-record parse-data [last-event :- NillableString
                          entries :- Seq
                          ] )
(defrecord parse-data [^String last-event
                       entries
                       ])


(defrecord vif-tree [^String last-event
                     ^IPersistentMap parent-to-child-no-map ; parent-no -> #{child-no1, child-no2 ...}
                     ^IPersistentMap all-entries-map        ; no -> vif-xml-entry
                     entries-to-delete
                     ]
  )

(defrecord vif-display-entry [^Long no
                              ^String title,
                              ^String author,
                              ^String date
                              ^Long size
                              ^String message
                              ;calc fields
                              ^Boolean is_visited           ; просматривали ли запись
                              ^Boolean is-top-fixed         ;является ли запись закрепленной вверху
                              ^Boolean is-marked            ; является ли запись отмеченной (mode-fixed или mode-unfixed)
                              ^Long non-visited-child-count ; количество не посещенных дочерних записей
                              ^Long child-count             ; количество дочерних записей
                              ^Long max-child-no            ; максимальный номер дочерней записи (нужен для сортировки)
                              ^Long depth
                              ])