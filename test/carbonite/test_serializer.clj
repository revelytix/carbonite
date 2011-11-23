(ns carbonite.test-serializer
  (:use [clojure.test]
        [carbonite.api]
        [carbonite.serializer])
  (:import [com.esotericsoftware.kryo Serializer]
           [java.nio ByteBuffer]))

;; An elegant weapon, not as clumsy or random as a blaster.
(defrecord LightSaber [style color])

;; Create a custom serializer for the two fields of LightSaber
(def saber-serializer
  (proxy [Serializer] []
    (writeObjectData [buffer saber]
      (clj-print buffer (:style saber))
      (clj-print buffer (:color saber)))
    (readObjectData [buffer type]
      (LightSaber. (clj-read buffer) (clj-read buffer)))))

(deftest test-custom-serializer
  (let [registry (default-registry)]
    (register-serializers registry {LightSaber saber-serializer})
    (let [darth-maul (LightSaber. :double-bladed :red)
          ^ByteBuffer buffer (new-buffer 1024)]
      (write-buffer registry buffer darth-maul)
      (.rewind buffer)
      (is (= darth-maul
             (read-buffer registry buffer))))))
