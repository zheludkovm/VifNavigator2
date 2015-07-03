(ns ru.vif.model.typed-libs
  (:require [clojure.core.typed :as t]
            )
  )

(t/ann clojure.core/not-empty (t/All [x]
                                     (t/IFn [t/Any -> (t/Option (t/Seq x))])
                                     ))

(t/ann clojure.zip/zipper (t/All [x]
                                 (t/IFn [
                                         [x -> Boolean]
                                         [x -> (t/U (t/Seq x) nil)]
                                         (t/Option [x (t/Seq x) -> x])
                                         x
                                         ->
                                         (t/ASeq x)
                                         ])))
(t/ann clojure.zip/end? (t/All [x]
                               (t/IFn [(t/ASeq x) -> Boolean])))
(t/ann clojure.zip/next (t/All [x]
                                (t/IFn [(t/ASeq x) -> (t/ASeq x)])))
(t/ann clojure.core/some? [t/Any -> Boolean :filters {:then (! nil 0), :else (is nil 0)}])
(t/non-nil-return java.text.DateFormat/parse :all)
(t/non-nil-return org.xmlpull.v1.XmlPullParserFactory/newInstance :all)
(t/non-nil-return org.xmlpull.v1.XmlPullParserFactory/newPullParser :all)
(t/non-nil-return org.xmlpull.v1.XmlPullParser/getAttributeName :all)

(t/ann clojure.core/update-in (t/All [x ]
                                (t/IFn [(t/ASeq x) (t/Vec t/Any) [x -> x] -> (t/ASeq x)]
                                       [(t/Map t/Any x) (t/Vec t/Any) [x -> x] -> (t/Map x)]
                                       )))