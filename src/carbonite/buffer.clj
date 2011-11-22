(ns carbonite.buffer
  (:use [carbonite.api :only (read-buffer)])
  (:import [com.esotericsoftware.kryo Kryo Serializer SerializationException]
           [java.io ByteArrayInputStream InputStream]
           [java.nio ByteBuffer BufferOverflowException]))

;;;; stuff to access the Kryo context object's ThreadLocal storage

(def context-key "kryobuffer")
(defn get-from-context
  "Get a thread-local object from Kryo."
  []
  (.get (Kryo/getContext) context-key))
(defn put-to-context
  "Put a thread-local object to Kryo."
  [value]
  (.put (Kryo/getContext) context-key value))
(defn clear-context
  "Clear the thread-local object in Kryo."
  []
  (put-to-context nil))

;;;; cached buffer settings

(def ^:dynamic *initial-buffer* 1024)
(def ^:dynamic *max-buffer* (* 512 1024 1024))  ;; max item can be 512MB
(def ^:dynamic *keep-buffer* (* 128 1024)) ;; never keep a buffer bigger than 128KB

(defn ensure-buffer
  "Create or return a Thread-specific scratch buffer for a kryo registry"
  [registry]
  (if-let [buffer (get-from-context)]
    buffer
    (let [new-buffer (ByteBuffer/allocate *initial-buffer*)]
      (put-to-context new-buffer)
      new-buffer)))

(defn- extract-bytes
  "Read the bytes from a ByteBuffer into a byte[] starting at offset for size."
  [^ByteBuffer buffer offset size]
  (let [^ByteBuffer read-buffer (.slice (doto (.duplicate buffer)
                                          (.position offset)
                                          (.limit (+ offset size))))
        bytes (byte-array size)]
    (.get read-buffer bytes)
    bytes))

(defn write-with-cached-buffer
  "Write the item into the buffer using the kryo registry.  If
   start-buffer is not big enough, double it up to *max-buffer*.  If it
   still won't fit, throw a SerializationException.  Return the byte[]
   and the buffer to cache for next time."
  [^Kryo registry ^ByteBuffer buffer item]
  (.clear buffer)
  (try
    (.writeClassAndObject registry buffer item)
    [(extract-bytes buffer 0 (.position buffer))
     (if (<= (.capacity buffer) *keep-buffer*) buffer nil)]
    (catch SerializationException ex
      (when-not (.causedBy ex BufferOverflowException) (throw ex))
      (if (>= (.capacity buffer) *max-buffer*)
        (throw (SerializationException. (str "Buffer limit exceeded serializing object of type: " (.getName (class item)))))
        (do
          (.reset (Kryo/getContext))
          (write-with-cached-buffer registry (ByteBuffer/allocate (* 2 (.capacity buffer))) item))))))

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
