(ns ru.vif.model.typed-libs
  (:require [clojure.core.typed :as t]
            )
  (:import (clojure.lang Keyword IPersistentMap IPersistentSet Counted IFn)))

(t/defalias NillableString (t/U nil String))
(t/defalias NillableLong (t/U nil Long))
(t/defalias NillableBoolean (t/U nil Boolean))
(t/defalias NillableKeyword (t/U nil Keyword))
(t/defalias AttrMap (IPersistentMap String NillableString))
(t/defalias NillableAttrMap (t/U AttrMap nil))
(t/defalias StringSet (IPersistentSet String))
(t/defalias LongSet (IPersistentSet Long))
(t/defalias NillableLongSet (t/U LongSet nil))

(t/defalias Zipper (t/All [x] (t/Seq x)))
(t/defalias LongZipper (t/ASeq Long))
(t/defalias LongLongMap (IPersistentMap Long NillableLongSet))
(t/defalias LongInteger (t/U Integer Long))
(t/defalias NoWithDepth (t/HVec [Long Long]))

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
(t/ann clojure.zip/node (t/All [x]
                               (t/IFn [(t/ASeq x) -> x])))
(t/ann clojure.zip/path (t/All [x]
                               (t/IFn [x -> (t/ASeq x)])))



(t/ann clojure.core/some? [t/Any -> Boolean :filters {:then (! nil 0), :else (is nil 0)}])
(t/non-nil-return java.text.DateFormat/parse :all)
(t/non-nil-return org.xmlpull.v1.XmlPullParserFactory/newInstance :all)
(t/non-nil-return org.xmlpull.v1.XmlPullParserFactory/newPullParser :all)
(t/non-nil-return org.xmlpull.v1.XmlPullParser/getAttributeName :all)

(t/ann clojure.core/update-in
       (t/All [x y]
              (t/IFn
                ;[(t/ASeq x) (t/Vec t/Any) [x -> x] -> (t/ASeq x)]
                ;[(t/Map y x) (t/Vec t/Any) [x -> x] -> (t/Map y x)]
                [y (t/Vec t/Any) IFn -> y]
                [y (t/Vec t/Any) IFn t/Any * -> y]
                ))
       )

(t/ann clojure.core/assoc-in (t/All [x y]
                                    (t/IFn [(t/Map y x) (t/Vec t/Any) t/Any -> (t/Map y x)]
                                           )))

(t/ann clojure.core/assoc (t/All [b c d]
                                 (t/IFn [(t/Map b c) b c -> (t/Map b c)]
                                        [(t/Vec d) t/AnyInteger d -> (t/Vec d)]
                                        [d t/Any t/Any -> d]
                                        )))

(t/ann ^:no-check long-count [(t/U (t/Seqable t/Any) Counted nil) -> Long :object {:path [Count], :id 0}])
(def long-count count)