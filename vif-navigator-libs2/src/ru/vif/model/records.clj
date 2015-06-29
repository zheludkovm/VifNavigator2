(ns ru.vif.model.records
  (:require [clojure.core.typed :as t]
            )
  (:import (java.util Date)
           (clojure.lang Keyword IPersistentMap IPersistentSet Seqable ASeq IPersistentCollection)
           )
  )

(t/defalias NillableString (t/U nil String))
(t/defalias NillableLong (t/U nil Long))
(t/defalias NillableBoolean (t/U nil Boolean))
(t/defalias NillableKeyword (t/U nil Keyword))
(t/defalias AttrMap (IPersistentMap String NillableString))
(t/defalias NillableAttrMap (t/U AttrMap nil))
(t/defalias StringSet (IPersistentSet String))
(t/defalias LongSet (IPersistentSet Long))
(t/defalias NillableLongSet (t/U LongSet nil))

(t/defalias LongZipper (t/ASeq Long))
(t/defalias LongLongMap (IPersistentMap Long NillableLongSet))


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
                          entries :- (t/Seq vif-xml-entry)
                          ])
(defrecord parse-data [^String last-event
                       entries
                       ])

(t/ann-record vif-tree [
                        last-event :- String
                        parent-to-child-no-map :- (IPersistentMap Long LongSet)
                        all-entries-map :- (IPersistentMap Long vif-xml-entry)
                        entries-to-delete :- (t/Seq vif-xml-entry)
                        ])
(defrecord vif-tree [^String last-event
                     ^IPersistentMap parent-to-child-no-map ; parent-no -> #{child-no1, child-no2 ...}
                     ^IPersistentMap all-entries-map        ; no -> vif-xml-entry
                     entries-to-delete
                     ]
  )
(t/ann-record vif-display-entry [
                                 no :- NillableLong
                                 title :- NillableString
                                 author :- NillableString
                                 date :- NillableString
                                 size :- NillableLong
                                 message :- NillableString
                                 ;calc fields
                                 is_visited :- Boolean      ; просматривали ли запись
                                 is-top-fixed :- Boolean    ;является ли запись закрепленной вверху
                                 is-marked :- Boolean       ; является ли запись отмеченной (mode-fixed или mode-unfixed)
                                 non-visited-child-count :- NillableLong    ; количество не посещенных дочерних записей
                                 child-count :- NillableLong ; количество дочерних записей
                                 max-child-no :- NillableLong ; максимальный номер дочерней записи (нужен для сортировки)
                                 depth :- NillableLong
                                 ])
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