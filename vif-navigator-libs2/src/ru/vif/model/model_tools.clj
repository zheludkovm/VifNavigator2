(ns ru.vif.model.model-tools
  (:require
    [clojure.core.typed :as t]
    [clojure.string :refer [blank?]]
    [clojure.zip :as zip]
    [clojure.set :as set]
    [ru.vif.model.vif-xml-tools :refer :all]
    [ru.vif.model.records :refer :all]
    [ru.vif.model.typed-libs :refer :all]
    )
  (:import (clojure.lang Keyword IPersistentMap)
           (ru.vif.model.records vif-xml-entry parse-data vif-tree)
           ))





(t/ann remove-fn [Long -> [NillableLongSet -> NillableLongSet]])
(defn remove-fn
  "Возвращает функцию которая удаляет элементы равные no"
  ^{:private true}
  [^Long no]

  (t/fn [s :- NillableLongSet]
    (set (remove
           (t/fn [^Long value :- Long] :- Boolean (= no value))
           s))
    )
  )

(t/ann add-fn [Long -> [LongSet -> LongSet]])
(defn add-fn
  "Возвращает функцию которая добавляет в вектор no"
  ^{:private true}
  [^Long no]

  ;#(conj (sorted-set %) no)
  (t/fn [v :- NillableLongSet]
    (if (nil? v)
      (sorted-set no)
      (conj v no)
      )
    )
  )

(t/ann make-zipper [LongLongMap Long -> LongZipper])
(defn make-zipper
  "создает zipper по map parent -> child и номеру корневого узла"
  [^IPersistentMap parent-to-child-no-map, ^Long root-no]

  (zip/zipper
    (t/fn [_ :- Long] :-Boolean true)
    (t/fn [^Long no :- Long] :- (t/Option (t/Seq Long))
      (not-empty (seq (get parent-to-child-no-map no))))
    nil
    root-no
    )
  )

(t/ann child-nodes [LongZipper -> (t/ASeq LongZipper)])
(defn child-nodes
  "Итератор по всем узлам зиппера"
  ^{:private true}
  [loc]

  (take-while (complement zip/end?)
              (iterate zip/next loc))
  )

(t/ann tree-child-nodes [LongLongMap -> (t/ASeq Long)])
(defn tree-child-nodes
  "sequence по всем узлам дерева"
  [^IPersistentMap parent-to-child-no-map, ^Long no]
  (->> (make-zipper parent-to-child-no-map no)
        child-nodes
       (map zip/node)
       )
  )

(t/ann tree-child-nodes [vif-tree Long -> (t/ASeq Long)])
(defn vif-tree-child-nodes
  "sequence по всем узлам дерева"
  [^vif-tree vif-tree, ^Long no]
  (tree-child-nodes (:parent-to-child-no-map vif-tree) no)
  )

(t/ann get-all-visited [vif-tree Long -> (t/ASeq Long)])
(defn get-all-visited
  "список всех посещенных узлов"
  [^vif-tree vif-tree, ^Long no]
  (t/let [all-entries-map :- LongVifXmlEntryMap (:all-entries-map vif-tree)]
    (->> (vif-tree-child-nodes vif-tree no)
         (filter (fn [^Long no] (:is_visited (get all-entries-map no)))
                 ))))

(t/ann find-first-non-visited-after [vif-tree Long Long -> (t/Option Long)])
(defn find-first-non-visited-after [^vif-tree vif-tree, ^Long root ^Long after]
  (let [all-entries-map (:all-entries-map vif-tree)]
    (->> (vif-tree-child-nodes vif-tree root)
         (drop-while #(not= after %))
         (filter #(= (:is_visited (get all-entries-map %)) false))
         first
         )
    )
  )


(t/ann merge-one-entry [String vif-tree vif-xml-entry -> vif-tree])
(defn merge-one-entry
  "Производит Merge одной записи с логом изменений дерева в итоговое дерево"
  ^{:private true}
  [^String last-event, ^vif-tree current-tree, ^vif-xml-entry vif-xml-entry]

  (let [
        ^Long no (:no vif-xml-entry)
        ^String parent (:parent vif-xml-entry)

        parent-to-child-no-map (:parent-to-child-no-map current-tree)
        all-entries-map (:all-entries-map current-tree)
        entries-to-delete (:entries-to-delete current-tree)
        ]
    ;(println parent "->" no ":" (:type vif-xml-entry))

    (case (:type vif-xml-entry)
      :add (vif-tree.
             last-event
             (update-in parent-to-child-no-map [parent] (add-fn no))
             (assoc all-entries-map no vif-xml-entry)
             entries-to-delete
             )

      :del (let [child-delete-entries (tree-child-nodes parent-to-child-no-map no)]
             ;(println "child-delete-entries" child-delete-entries)
             (vif-tree.
               last-event
               (update-in parent-to-child-no-map [parent] (remove-fn no))
               (dissoc all-entries-map no)
               (set/union entries-to-delete child-delete-entries)
               )
             )

      :parent (let [current-parent-no (:parent (get all-entries-map no))]
                (vif-tree.
                  last-event
                  (-> (update-in parent-to-child-no-map [current-parent-no] (remove-fn no))
                      (update-in [parent] (add-fn no))
                      )
                  all-entries-map
                  entries-to-delete
                  )
                )
      :fix (vif-tree.
             last-event
             parent-to-child-no-map
             (assoc-in all-entries-map [no :mode] (:mode vif-xml-entry))
             entries-to-delete
             )

      )
    )
  )

(defn merge-one-entry-p [^String last-event]
  (partial merge-one-entry last-event)
  )

(defn set-entry-visited
  [^vif-tree current-tree
   ^Long no]
  (update-in current-tree [:all-entries-map no :is_visited] (constantly true))
  )

(defn set-entry-message
  [^vif-tree current-tree
   ^Long no
   ^String message
   ]
  (update-in current-tree [:all-entries-map no :message] (constantly message))
  )

(defn get-entry-message [^vif-tree current-tree ^Long no]
  (:message (get (:all-entries-map current-tree) no))
  )

(defn merge-trees
  "Производит merge списка изменений vif в дерево"
  [^vif-tree current-tree, ^parse-data new-parsed-data]

  (let [current-tree-prepared (assoc current-tree :entries-to-delete #{})]
    (reduce (merge-one-entry-p (:last-event new-parsed-data)) current-tree-prepared (:entries new-parsed-data))
    )
  )

