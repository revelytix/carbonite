# carbonite

Carbonite is a Clojure library to convert Clojure data to serialized form and back using the Kryo serialization library.  Kryo is known for being fast and producing very small serializations.  

## License

Copyright (C) 2011 Revelytix, Inc.

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

## Usage

Kryo works by creating a registry of classes to serializers/deserializers.  The serialized stream written by Kryo consists of a class identifier followed by the bytes written for the instance.  Serializers may recursively use other serializers.  

Deserialization is done in reverse - the class identifier is read and the deserializer is looked up in the registry.  The deserializer is then used to read bytes from the stream.

```clojure
;; Initialize a registry with the default serializers
(def registry (default-registry))

;; Serialize my-data (any Clojure data) into the byte[] b
(def b (write-bytes registry my-data))

;; Deserialize bytes back to Clojure data
(def c (read-bytes registry b)))
```

## Handled classes 

* Java primitives
  * all primitive and boxed types
  * java.lang.String
  * java.math.BigDecimal
  * java.math.BigInteger
  * java.util.Date
  * java.sql.Date
  * java.sql.Time
  * java.sql.Timestamp
  * java.net.URI
* Clojure primitives
  * clojure.lang.BigInt
  * clojure.lang.Keyword
  * clojure.lang.Symbol
* Collections and sequences
  * clojure.lang.Cons
  * clojure.lang.IteratorSeq 
  * clojure.lang.LazySeq
  * clojure.lang.MapEntry
  * clojure.lang.PersistentArrayMap
  * clojure.lang.PersistentHashMap
  * clojure.lang.PersistentHashSet
  * clojure.lang.PersistentList
  * clojure.lang.PersistentList$EmptyList
  * clojure.lang.PersistentStructMap
  * clojure.lang.PersistentVector
  * clojure.lang.StringSeq

## TODO - things that could be handled but are not

* ArrayChunk
* ArraySeq
* ChunkBuffer
* ChunkedCons
* EnumerationSeq
* LazilyPersistentVector
* PersistentQueue
* PersistentTreeMap (how to handle comparator?)
* PersistentTreeSet (ditto)
* Range
* Ratio
* SeqEnumeration
* SeqIterator

