(ns ru.vif.model.records
  (:import (java.util Date)
           (clojure.lang Keyword IPersistentMap IPersistentSet Seqable ASeq IPersistentCollection)
           )
  )




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