(ns carbonite.api
  (:require [clojure.string :as s])
  (:use [carbonite.serializer]
        [carbonite.buffer])
  (:import [com.esotericsoftware.kryo Kryo Serializer]
           [java.nio ByteBuffer]))

;;;; Creating a Kryo registry

(defn register-serializers
  "Register a map of Class to Kryo Serializer with a Kryo registry."
  [^Kryo registry serializers]
  (doseq [[^Class klass ^Serializer serializer] serializers]
    (.register registry klass serializer))
  registry)

;; first item *is* the class of the unregistered object type
(defmulti kryo-extend (fn [klass registry] klass))

(defn new-registry
  "Create a new Kryo registry that supports unregistered classes and defers to the
   kryo-extend multimethod if an unhandled Class serializer is requested."
  []
  (let [registry (proxy [Kryo] []
                   (handleUnregisteredClass [^Class klass]
                     (kryo-extend klass this)))]
    (doto registry
      (.setRegistrationOptional true))))

(defn default-registry
  "Create or install a set of default serializers in an existing registry.  Modifies and
   returns the registry instance."
  ([]
     (default-registry (new-registry)))
  ([registry]
     (doto registry
       (register-serializers clojure-primitives)
       (register-serializers java-primitives)
       (register-serializers (clojure-collections registry)))))

;;;; APIs to read and write objects using ByteBuffers

(defn new-buffer
  "Create a new on-heap ByteBuffer of size."
  [size]
  (ByteBuffer/allocate size))

(defn write-buffer
  "Write serialized obj into ByteBuffer using registry.  If the buffer is not big enough,
   a SerializationException will be thrown."
  [registry byte-buffer obj]
  (.writeClassAndObject registry byte-buffer obj))

(defn read-buffer
  "Read serialized object from byte-buffer using registry."
  [registry byte-buffer]
  (.readClassAndObject registry byte-buffer))

;;;; APIs to read and write objects using byte[] and cached buffers.

(defn write-bytes
  "Write obj using registry and return a byte[]."
  [registry obj]
  (let [buffer (ensure-buffer registry)
          [item-bytes new-buffer] (write-with-cached-buffer registry buffer obj)]
      (when new-buffer
        (put-to-context new-buffer))
      item-bytes))

(defn read-bytes
  "Read obj from byte[] using the registry."
  [^Kryo registry ^bytes bytes]
  (read-buffer registry (ByteBuffer/wrap bytes)))



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
