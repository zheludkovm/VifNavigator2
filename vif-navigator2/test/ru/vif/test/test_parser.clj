(ns ru.vif.test.test-parser
  (:use clojure.test)
  (:require clojure.java.io)
  )



(deftest test1
  (testing "Parser"
    ;(print (clojure.java.io/resource "/tree.html"))
    ;(print (slurp "test-resources/tree.html"))
    (is (= 1 1) "Bah!")
    )
  )