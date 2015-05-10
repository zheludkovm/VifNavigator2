(ns ru.vif.db-tools
  (:require [neko.log :as log]
            [neko.data.sqlite :as db]
            [clojure.java.io :as io]
            [ru.vif.tools :as tools]

            [ru.vif.model.api :as model-api :refer :all]
            [clojure.stacktrace :as stacktrace :refer :all]
            )
  (:import
    (ru.vif.model.records vif-xml-entry parse-data vif-tree vif-display-entry)
    (android.content Context)
    (neko.data.sqlite TaggedDatabase)
    (android.database.sqlite SQLiteDatabase)))

(def keyword-columns [:type :mode])

(def void-tree (vif-tree. tools/NOT_DEFINED_START_EVENT
                          {}
                          {}
                          #{}))


(def schema
  (db/make-schema
    :name "local.db"
    :version 1
    :tables {:tree_entries
             {:columns {:id         "integer primary key AUTOINCREMENT"
                        :no         "long"
                        :type       "text"
                        :mode       "text"
                        :parent     "long"
                        :title      "text"
                        :author     "text"
                        :date       "text"
                        :size       "long"
                        :is_visited "boolean"
                        }}}))

(def get-db-helper
  (memoize
    (fn [^Context context]
      (db/create-helper (.getApplicationContext context) schema))))

(defn get-db [this]
  (db/get-database (get-db-helper this) :write)
  )

(defn convert-entry [entry func]
  (reduce #(assoc %1 %2 (func (get %1 %2)))
          entry
          keyword-columns
          )
  )

(defn delete
  "Удаляет все записи относящиеся к записи с номером no"
  [^TaggedDatabase tagged-db table-name column value]

  (.delete ^SQLiteDatabase (.db tagged-db) (name table-name) (str (name column) "=" value) nil)
  )

(defn delete-all
  "Полная очистка базы"
  [this]
  (let [tagged-db (get-db this)]
    (.delete ^SQLiteDatabase (.db tagged-db) (name :tree_entries) nil nil)
    )
  )

(defn store-persistant-data!
  "Сохраняет записи в базу, удаляет помеченные на удаление"
  [this
   ^vif-tree merged-tree
   ^parse-data new-entries]

  (let [db (get-db this)]
    (db/transact db
                 (doseq [^vif-xml-entry entry (:entries new-entries)]
                   (if (not= (:type entry) :del)
                     ;(println "add" entry)
                     (tools/with-try #(db/insert db :tree_entries (convert-entry entry name))))
                   )

                 (doseq [^Long delete-no (:entries-to-delete merged-tree)]
                   (log/d "delete " delete-no)
                   (tools/with-try #(delete db :tree_entries :no delete-no))
                   )
                 )
    )
  (log/d "store last event=" (:last-event new-entries))
  (tools/set-shared-pref-value! this tools/LAST_EVENT (:last-event new-entries))
  )

(defn store-visited!
  "Помечает запись в базе как просмотренную"
  ([this no]
   (let [db (get-db this)]
     (store-visited! this db no)
     ))

  ([this db no]
   (tools/with-try #(db/update db :tree_entries {:is_visited true} {:no no}))
    )
  )

(defn store-subtree-visited!
  "Помечает список записей в базе как просмотренные"
  [this no-seq]
  (log/d "mark subtree visited!")
  (let [db (get-db this)]
    (db/transact db
                 (doseq [no no-seq]
                   (store-visited! this db no)
                   )
                 )
    )
  )

(defn store-all-visited!
  "Помечает все записи в базе как просмотренные"
  [this]
  (log/d "mark all visited!")
  (let [db (get-db this)]
    (tools/with-try #(db/update db :tree_entries {:is_visited true} nil))
    )
  )


(defn load-stored-vif-tree
  "Загрузка записей из базы"
  [this]
  (log/d "Load stored tree!")
  (let [db (get-db this)
        last-event (tools/get-shared-pref-value this tools/LAST_EVENT tools/NOT_DEFINED_START_EVENT)
        entries (tools/with-try #(db/query-seq db :tree_entries {}))
        converted-entries (map #(convert-entry % keyword) entries)
        sorted-entries (sort-by #(get % "id") converted-entries)
        merged-tree-store (model-api/merge-trees void-tree (parse-data. last-event sorted-entries))
        ]
    (log/d "loaded-tree" parse-xml-entries)
    merged-tree-store
    )

  )


