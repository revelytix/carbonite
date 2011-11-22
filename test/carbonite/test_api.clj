(ns carbonite.test-api
  (:use [carbonite.api]
        [carbonite.buffer]
        [clojure.test])
  (:import [java.nio ByteBuffer]
           [java.net URI]
           [java.util Date]
           [com.esotericsoftware.kryo Kryo SerializationException]))

(defn round-trip [item]
  (clear-context)
  (let [kryo (default-registry)
        bytes (write-bytes kryo item)]
    (read-bytes kryo bytes)))

(defstruct mystruct :a :b)

(defn new-ts [time nano]
  (doto (java.sql.Timestamp. time)
    (.setNanos nano)))

(deftest test-round-trip-kryo
  (are [obj] (is (= obj (round-trip obj)))
       nil
       1      ;; long
       5.2    ;; double
       5M     ;; BigDecimal
       1000000000000000000000000  ;; BigInt
       :foo   ;; keyword
       :a/foo ;; namespaced keyword
       'foo   ;; symbol
       'a/foo ;; namespaced symbol
       []     ;; empty vector
       [1 2]  ;; vector
       '()    ;; empty list
       '(1 2) ;; list
       #{}    ;; empty set
       #{1 2 3}  ;; set
       {}     ;; empty map
       {:a 1} ;; map
       {:a 1 :b 2} ;; map
       {:a {:b {:c [1 #{"abc"} ]}}}  ;; nested collections
       (Date.)  ;; java.util.Date
       (new-ts 991000000 123456)   ;; java.sql.Timestamp
       (java.sql.Date. 991000000)  ;; java.sql.Date
       (java.sql.Time. 3600)       ;; java.sql.Time
       (URI. "http://foo.com?bar=baz")  ;; java.net.URI
       (URI. "http://\u20AC.com")  ;; java.net.URI with unicode
       (range 50)    ;; LazySeq
       (cons 1 '())  ;; Cons
       (cons 1 '(2))
       (cons 1 '(2 3))
       (struct-map mystruct :a 1 :b 2)  ;; PersistentStructMap
       {:a 1 :b 2 :c 3 :d 4 :e 5 :f 6 :g 7 :h 8 :i 9} ;; PersistentArrayMap
       (seq "abc") ;; StringSeq
       )) 

(deftest test-roundtrip-iterator-seq
  (let [coll (java.util.ArrayList.)
        _ (.add coll "abc")
        iter (.iterator coll)
        iseq (iterator-seq iter)
        rt-item (round-trip iseq)]
    (is (= ["abc"] rt-item))))

(deftest test-context
  (put-to-context :foo)
  (is (= :foo (get-from-context))))

(deftest test-ensure-buffer
  (clear-context)
  (let [^ByteBuffer buff (ensure-buffer (Kryo.))]
    (is (not (nil? buff)))
    (is (= *initial-buffer* (.capacity buff)))
    (is (= buff (get-from-context)))))

(deftest test-write-with-cached-buffer
  (testing "exception breaking max"
    (clear-context)
    (binding [*max-buffer* 16]
      (is (thrown-with-msg?
            SerializationException
            #"Buffer limit exceeded serializing object of type: clojure.lang.LazySeq"
            (write-with-cached-buffer (default-registry) (ByteBuffer/allocate 1) (range 20))))))
  (testing "buffer gets bigger"
    (clear-context)
    (binding [*keep-buffer* 1024
              *max-buffer* 1024]
      (let [[^bytes bytes ^ByteBuffer buffer] (write-with-cached-buffer (default-registry) (ByteBuffer/allocate 1) (range 20))]
        (is (= (alength bytes) (.position buffer)))
        (is (= 64 (.capacity buffer))))))
  (testing "dont return buffer bigger than *keep-buffer*"
    (clear-context)
    (binding [*keep-buffer* 16
              *max-buffer* 1024]
      (let [[^bytes bytes buffer] (write-with-cached-buffer (default-registry) (ByteBuffer/allocate 1) (range 20))]
        (is (= 42 (alength bytes)))
        (is (nil? buffer))))))

;; Copyright 2011 Revelytix, Inc.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;; 
;;     http://www.apache.org/licenses/LICENSE-2.0
;; 
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
