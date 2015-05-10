(ns vif-navigator-libs.core-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [ru.vif.model.api :refer :all :as xml-tools]
            )
  (:import (java.io FileInputStream)
           (java.net URL)
           (ru.vif.model.records vif-xml-entry parse-data vif-tree vif-display-entry)
           ))


;(print (slurp "test-resources/tree-zero.xml"))

(deftest a-test
  (testing "parser"
    (println "test!")
    ;(let [tree-small (slurp "test-resources/tree-zero.xml" :encoding "Cp1251")
    ;(let [tree-small (slurp "test-resources/tree-small.xml" :encoding "Cp1251")
    ;      tree-small-entries (xml-tools/parse-xml-entries tree-small)
    ;      ]
    ;  (time (println (count (:entries tree-small-entries))))
    ;  (println "lastEvent=" (:lastEvent tree-small-entries))
    ;  ;(println "entries=" (:entries tree-small-entries))
    ;  )

    ;merge
    (let [
          tree-small (slurp "test-resources/tree-small.xml" :encoding "Cp1251")
          tree-small-entries (parse-xml-entries tree-small)
          void-tree (vif-tree. "-1" {} {} #{})
          merged-tree-info (merge-trees void-tree tree-small-entries)
          merged-tree-updated (set-entry-visited merged-tree-info 2665617)
          trimmed-tree (time (trim-tree-by-depth  merged-tree-updated  0 1))
          ]
      ;(println "tree-small-entries" tree-small-entries)
      (println "merged data=" merged-tree-updated)
      (println "------------")
      (println "trimmed-tree=" trimmed-tree)
      (println "trimmed-tree.count=" (count trimmed-tree))
      (println "-------")
      (println (vif-tree-child-nodes merged-tree-updated 0))
      )

    ;(let [tree-zero (slurp "test-resources/tree-zero.xml" :encoding "Cp1251")
    ;      tree-zero-entries (time (xml-tools/parse-xml-entries tree-zero))
    ;      tree-1322528 (slurp "test-resources/tree-1322528.xml" :encoding "Cp1251")
    ;      tree-1322528-entries (time (xml-tools/parse-xml-entries tree-1322528))
    ;
    ;      void-tree (vif-tree. "-1" {} {} #{})
    ;      merged-tree-info (time (model-tools/merge-trees void-tree tree-zero-entries))
    ;      merged-tree-info2 (time (model-tools/merge-trees merged-tree-info tree-1322528-entries))
    ;      ]
    ;  ;(println "tree-small-entries" tree-small-entries)
    ;  ;(println "merged data=" merged-tree-info2)
    ;  (println "merged data count=" (count (:all-entries-map merged-tree-info2)))
    ;  (println "merged data tree=" (:parent-to-child-no-map merged-tree-info2))
    ;  (println "merged data deleted=" (:entries-to-delete merged-tree-info2))
    ;
    ;  )

    )
  )