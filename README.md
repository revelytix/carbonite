# carbonite

Ever needed to freeze your Clojure data in perfect hibernation?  Ever want to deserialize your data without concealing yourself as a bounty hunter in Jabba's desert palace?

Carbonite is a Clojure library to convert Clojure data to serialized form and back using the [Kryo](http://code.google.com/p/kryo/) serialization library.  Kryo is known for being fast and producing very small serializations.  
## License

Copyright (C) 2011 Revelytix, Inc.

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

## Usage

Kryo works by creating a registry of classes to serializers/deserializers.  The serialized stream written by Kryo consists of a class identifier followed by the bytes written for the instance.  Serializers may recursively use other serializers.  

Deserialization is done in reverse - the class identifier is read and the deserializer is looked up in the registry.  The deserializer is then used to read bytes from the stream.  

The main api is contained in the carbonite.api namespace:

```clojure
;; Initialize a registry with the default serializers (covering most Java and Clojure data)
(def registry (default-registry))

;; Serialize my-data (any Clojure data) into the ByteBuffer b
(def b (new-buffer 1024))
(write-buffer registry b my-data))

;; Rewind buffer back to the beginning
(.rewind b)

;; Deserialize buffer back to Clojure data
(def c (read-buffer registry b)))
```

If you'd prefer to work in byte[] rather than ByteBuffers, there is also some possibly suspect support for that in the carbonite.buffer namespace.  

Because the Kryo API works only on ByteBuffers, we must create a ByteBuffer internally for write-bytes to work with - currently, this function uses a ThreadLocal buffer stored inside the Kryo context facility.  The buffer is initialized with size `*initial-buffer*` bytes, and serialization will retry at double the size up to `*max-buffer*`.  The buffer will only be cached for reuse if it is less than `*keep-buffer*` bytes.  

Initial values are:

* `*initial-buffer*` = 1024 bytes
* `*max-buffer*` = 512*1024*1024 = 512MB
* `*keep-buffer*` = 128*1024 = 128KB

```clojure
;; Initialize a registry with the default serializers (covering most Java and Clojure data)
(def registry (default-registry))

;; Serialize my-data (any Clojure data) into the byte[] b
(def b (write-bytes registry my-data))

;; Deserialize bytes back to Clojure data
(def c (read-bytes registry b)))
```

## Extensions

To implement your own serializer, follow the examples set out in carbonite.serializer:

```clojure
;; An elegant weapon, not as clumsy or random as a blaster.
(defrecord LightSaber [style color])

;; Create a custom serializer for the two fields of LightSaber - 
;; this is just an example, many other ways to do this
(def saber-serializer
  (proxy [Serializer] []
    (writeObjectData [buffer saber]
      (clj-print buffer (:style saber))
      (clj-print buffer (:color saber)))
    (readObjectData [buffer type]
      (LightSaber. (clj-read buffer) (clj-read buffer)))))

(deftest test-custom-serializer
  (let [registry (default-registry)]
    ;; Register LightSaber and it's serializer
    (register-serializers registry {LightSaber saber-serializer})
    (let [darth-maul (LightSaber. :double-bladed :red)
          buffer (new-buffer 1024)]
      ;; serialize the instance into the buffer
      (write-buffer registry buffer darth-maul)

      ;; Rewind the buffer for reading
      (.rewind buffer)

      ;; Deserialize the instance from the buffer
      (is (= darth-maul
             (read-buffer registry buffer))))))
```

Registering the serializers by concrete class is fine if you know the concrete class.  If you can dynamically identify something to serialize, there is a multi-method that you can hook into:

```clojure
(defmethod kryo-extend FooBar [klass registry]
  ;; called when trying to serialize FooBar - you can then dynamically 
  ;; install a FooBar serializer on the registry instance provided and it
  ;; will be immediately picked up. This is a one-time operation - future
  ;; FooBars will use the registered serializer.
)
```

## Handled classes 

* Java primitives
  * all primitive and boxed types
  * java.lang.String
  * java.math.BigDecimal
  * java.math.BigInteger
  * java.util.Date
  * java.util.UUID
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

