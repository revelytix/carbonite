(ns carbonite.serializer
  (:require [clojure.string :as s])
  (:import [com.esotericsoftware.kryo Kryo Serializer SerializationException]
           [com.esotericsoftware.kryo.serialize StringSerializer MapSerializer IntSerializer
            LongSerializer BigDecimalSerializer BigIntegerSerializer DateSerializer]
           [java.io ByteArrayInputStream InputStream]
           [java.nio ByteBuffer BufferOverflowException]
           [java.math BigDecimal BigInteger]
           [java.net URI]
           [java.util Date UUID]
           [java.sql Time Timestamp]
           [clojure.lang BigInt Keyword Symbol PersistentArrayMap
            PersistentHashMap MapEntry PersistentStructMap 
            PersistentVector PersistentHashSet
            Cons PersistentList PersistentList$EmptyList
            ArraySeq$ArraySeq_int LazySeq IteratorSeq StringSeq]))

(defn clj-print
  "Use the Clojure pr-str to print an object into the buffer using pr-str."
  [buffer obj]
  (StringSerializer/put buffer (pr-str obj)))

(defn clj-read
  "Use the Clojure read-string to read an object from a buffer."
  [buffer]
  (read-string (StringSerializer/get buffer)))

(def clojure-reader-serializer
  "Define a serializer that utilizes the Clojure pr-str and read-string functions
   to serialize/deserialize instances relying solely on the printer/reader.  Probably
   not the most efficient but likely to work in many cases."
  (proxy [Serializer] []  
    (writeObjectData [buffer obj] (clj-print buffer obj))
    (readObjectData [buffer type] (clj-read buffer))))

(defn clojure-coll-serializer
  "Create a collection Serializer that conj's to an initial collection."
  [^Kryo registry init-coll]
  (proxy [Serializer] []
    (writeObjectData [buffer v]
      (IntSerializer/put buffer (count v) true)
      (doseq [x v] (.writeClassAndObject registry buffer x)))
    (readObjectData [buffer type]
      (doall
       (loop [remaining (IntSerializer/get buffer true)
              data (transient init-coll)]
         (if (zero? remaining)
           (persistent! data)
           (recur (dec remaining)
                  (conj! data (.readClassAndObject registry buffer)))))))))

(defn clojure-seq-serializer
  "Create a sequence Serializer that will apply the constructor function on
   deserialization."
  [^Kryo registry constructor-fn]
  (proxy [Serializer] []
    (writeObjectData [buffer s]
      (IntSerializer/put buffer (count s) true)
      (doseq [x s] (.writeClassAndObject registry buffer x)))
    (readObjectData [buffer type]
      (let [len (IntSerializer/get buffer true)]
        (apply constructor-fn
               (repeatedly len #(.readClassAndObject registry buffer)))))))

(defn- write-map
  "Write an associative data structure to Kryo's buffer. Write entry count as
   an int, then serialize alternating key/value pairs."
  [^Kryo registry ^ByteBuffer buffer m]
  (IntSerializer/put buffer (count m) true)
  (doseq [[k v] m]
    (.writeClassAndObject registry buffer k)
    (.writeClassAndObject registry buffer v))  )

(defn- read-map
  "Read a map from Kryo's buffer.  Read entry count, then deserialize alternating
   key/value pairs.  Transients are used for performance."
  [^Kryo registry ^ByteBuffer buffer]
  (doall
   (loop [remaining (IntSerializer/get buffer true)
          data (transient {})]
     (if (zero? remaining)
       (persistent! data)
       (recur (dec remaining)
              (assoc! data (.readClassAndObject registry buffer) (.readClassAndObject registry buffer)))))))

(defn clojure-map-serializer
  "Create a Kryo serializer for an associative data structure."
  [^Kryo registry]
  (proxy [Serializer] []
    (writeObjectData [buffer m] (write-map registry buffer m))
    (readObjectData [^ByteBuffer buffer type] (read-map registry buffer))))

(def stringseq-serializer
  (proxy [Serializer] []
    (writeObjectData [buffer stringseq] (StringSerializer/put buffer (s/join stringseq)))
    (readObjectData [buffer type] (seq (StringSerializer/get buffer)))))

(def uri-serializer
  "Define a Kryo Serializer for java.net.URI."
  (proxy [Serializer] []
    (writeObjectData [buffer ^URI uri]
      (StringSerializer/put buffer (.toString uri)))
    (readObjectData [buffer type]
      (URI/create (StringSerializer/get buffer)))))

(def uuid-serializer
  "Define a Kryo Serializer for java.net.UUID."
  (proxy [Serializer] []
    (writeObjectData [buffer ^UUID uuid]
      (LongSerializer/put buffer (.getMostSignificantBits uuid) false)
      (LongSerializer/put buffer (.getLeastSignificantBits uuid) false))
    (readObjectData [buffer type]
      (UUID. (LongSerializer/get buffer false)
             (LongSerializer/get buffer false)))))

(def timestamp-serializer
  "Define a Kryo Serializer for java.sql.Timestamp"
  (proxy [Serializer] []
    (writeObjectData [buffer ^Timestamp ts]
      (LongSerializer/put buffer (.getTime ts) true)
      (LongSerializer/put buffer (.getNanos ts) true))
    (readObjectData [buffer type]
      (doto (Timestamp. (LongSerializer/get buffer true))
        (.setNanos (LongSerializer/get buffer true))))))

(defn sqldate-serializer
  "Create a java.sql.Date or java.sql.Time Kryo Serializer."
  [^Class klass]
  (proxy [Serializer] []
    (writeObjectData [buffer ^Date d]
      (LongSerializer/put buffer (.getTime d) true))
    (readObjectData [buffer type]
      (let [constructor (.getConstructor klass (into-array Class [Long/TYPE]))]
        (.newInstance constructor (object-array [ (LongSerializer/get buffer true)]))))))

(defn intern-type-serializer
  "Serialize clojure intern types"
  [value-function intern-function]
  (proxy [Serializer] []
    (writeObjectData [buffer k]
      (StringSerializer/put buffer (value-function k)))
    (readObjectData [buffer type]
      (intern-function (StringSerializer/get buffer)))))

(def clojure-primitives
  "Define a map of Clojure primitives and their serializers to install."
  {BigInt clojure-reader-serializer
   Keyword (intern-type-serializer
            #(.substring (.toString ^Keyword %) 1) ;;remove :
            #(Keyword/intern ^String %))
   Symbol (intern-type-serializer
           #(.toString ^Symbol %) #(Symbol/intern ^String %))})

(def java-primitives
  {BigDecimal (BigDecimalSerializer.)
   BigInteger (BigIntegerSerializer.)
   Date (DateSerializer.)
   Timestamp timestamp-serializer
   java.sql.Date (sqldate-serializer java.sql.Date)
   java.sql.Time (sqldate-serializer java.sql.Time)
   URI uri-serializer
   UUID uuid-serializer})

(defn clojure-collections
  [registry]
  (concat
   ;; collections where we can use transients for perf
   [[PersistentVector (clojure-coll-serializer registry [])]
    [PersistentHashSet (clojure-coll-serializer registry #{})]
    [MapEntry (clojure-coll-serializer registry [])]]

   ;; list/seq collections
   (zipmap [Cons PersistentList$EmptyList PersistentList LazySeq IteratorSeq]
           (repeat (clojure-seq-serializer registry list)))

   ;; other seqs
   [[StringSeq stringseq-serializer]]
   
   ;; maps - use transients for perf
   (map #(vector % (clojure-map-serializer registry))
        [PersistentArrayMap PersistentHashMap PersistentStructMap])))



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
