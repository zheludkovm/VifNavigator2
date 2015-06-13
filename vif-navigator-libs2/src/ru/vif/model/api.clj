(ns ru.vif.model.api
  (:require
    [ru.vif.model.records :refer :all]
    [ru.vif.model.vif-xml-tools :as vif-xml-tools]
    [ru.vif.model.model-tools :as model-tools]
    [ru.vif.model.trim-tools :as trim-tools]
    )
  (:import (clojure.lang Keyword IPersistentMap)
           (ru.vif.model.records vif-xml-entry parse-data vif-tree)
           ))

(def ROOT 0)

(defn parse-xml-entries
  "Разбирает xml и возврщает sequence записей vif-xml"
  [^String xml]
  (vif-xml-tools/parse-xml-entries xml)
  )


(defn merge-trees
  "Производит merge списка изменений vif в дерево"
  [^vif-tree current-tree, ^parse-data new-parsed-data]
  (model-tools/merge-trees current-tree new-parsed-data)
  )

(defn trim-tree-by-depth
  "Обрезает дерево до заданной глубины"
  [^vif-tree current-vif-tree, ^Long root-no, ^long max-depth]
  (trim-tools/trim-tree-by-depth current-vif-tree root-no max-depth)
  )

(defn set-entry-visited
  [^vif-tree current-tree
   ^Long no]
  (model-tools/set-entry-visited current-tree no)
  )

(defn set-all-visited!
  [^vif-tree current-tree
   seq-no]
  (reduce #(set-entry-visited %1 %2) current-tree seq-no)
  )

(defn vif-tree-child-nodes
  "sequence по всем узлам дерева"
  [^vif-tree vif-tree, ^Long no]
  (model-tools/vif-tree-child-nodes vif-tree no)
  )

(defn find-first-non-visited-after [^vif-tree vif-tree, ^Long after]
  (model-tools/find-first-non-visited-after vif-tree ROOT after)
  )

(defn has-non-visited [^vif-tree vif-tree, ^Long root]
  (some? (model-tools/find-first-non-visited-after vif-tree root root))
  )

(def get-all-visited
  model-tools/get-all-visited
  )

(def set-entry-message model-tools/set-entry-message)

(def get-entry-message model-tools/get-entry-message)