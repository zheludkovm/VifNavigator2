(ns ru.vif.model.trim-tools
  (:require
    [clojure.string :refer [blank?]]
    [clojure.zip :as zip]
    [clojure.set :as set]
    [ru.vif.model.vif-xml-tools :refer :all]
    [ru.vif.model.model-tools :refer :all]
    [ru.vif.model.records :refer :all]
    [clojure.core.typed :as t]
    [ru.vif.model.typed-libs :refer :all]
    )
  (:import (clojure.lang Keyword IPersistentMap Counted)
           (ru.vif.model.records vif-xml-entry parse-data vif-tree vif-display-entry)
           ))


(t/ann child-nodes-with-depth [LongLongMap Long -> (t/ASeq NoWithDepth)])
(defn child-nodes-with-depth
  "Возвращает sequence дочерних элементов узла элементов с глубиной"
  ^{:private true}
  [^IPersistentMap parent-to-child-no-map, ^Long no]

  (->> (make-zipper parent-to-child-no-map no)
       child-nodes
       (map
         (t/fn [loc :- LongZipper] :- NoWithDepth
           [(long-zipper-node loc) (long-count (zip/path loc))]
           )
         )
       )
  )

(t/ann find-entry-in-tree [Long vif-tree -> (t/Option vif-xml-entry)])
(defn find-entry-in-tree
  [^Long no, ^vif-tree current-vif-tree]
  (get (:all-entries-map current-vif-tree) no)
  )

(t/ann init-display-entry [Long vif-tree Long -> (t/Option vif-display-entry)])
(defn init-display-entry
  "создает record с данными одного изменения vif дерева"
  ^{:private true}
  [^Long no, ^vif-tree current-vif-tree, ^Long depth]

  (if-let [^vif-xml-entry xml-entry (find-entry-in-tree no current-vif-tree)]
    (vif-display-entry.
      no
      (:title xml-entry)
      (:author xml-entry)
      (:date xml-entry)
      (:size xml-entry)
      (:message xml-entry)
      (:is_visited xml-entry)
      (= (:mode xml-entry) :mode-fixed)
      (contains? #{:mode-fixed :mode-unfixed} (:mode xml-entry))
      0
      0
      no
      depth
      )
    )
  )

(t/ann update-in-if (t/All [y]
                           (t/IFn [y Boolean (t/Vec t/Any) [t/Any -> t/Any] -> y] )
                           ))
(defn update-in-if
  "update-in с условием"
  ^{:private true}
  [coll, ^Boolean if-value, update-path, func]

  (if if-value
    (update-in coll update-path func)
    coll
    )
  )

(t/ann update-prev-entry [vif-display-entry Long vif-tree -> vif-display-entry])
(defn update-prev-entry
  "Изменяет предыдущую запись в линейном списке записей, увеличивает количество дочерних записей, не посещенных, максимальный номер дочерней записи"
  ^{:private true}
  [^vif-display-entry update-entry
   ^Long no
   ^vif-tree current-vif-tree]

  (t/let [^vif-xml-entry xml-entry :- (t/Option vif-xml-entry) (find-entry-in-tree no current-vif-tree)
          ^Boolean is-visited :- (t/Option Boolean) (:is_visited xml-entry)
          updated1 :- vif-display-entry (update-in update-entry [:child-count] inc)
          ]
    (-> updated1
        (update-in-if (not is-visited) [:non-visited-child-count] inc)
        (update-in [:max-child-no] max no)
        )
    )
  )


(defn trim-tree-by-depth
  "Обрезает дерево до заданной глубины"
  [^vif-tree current-vif-tree, ^Long root-no, ^long max-depth]

  (let [nodes-with-depth (child-nodes-with-depth (:parent-to-child-no-map current-vif-tree) root-no)
        drop1-nodes (drop 1 nodes-with-depth)               ; убираем первый узел
        ]
    (drop 1 (reverse (reduce
                       (fn [[^vif-display-entry prev-entry & rest]
                            [^Long no ^Long current-depth]]
                         ;(println no current-depth max-depth)
                         (if (> current-depth max-depth)
                           (conj rest (update-prev-entry prev-entry no current-vif-tree))
                           (conj rest prev-entry (init-display-entry no current-vif-tree current-depth))
                           )
                         )
                       [nil]
                       drop1-nodes
                       )
                     ))
    )
  )