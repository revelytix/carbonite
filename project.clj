(def shared
  '[[com.googlecode/kryo "1.04"]])

(defproject carbonite "1.0.0-SNAPSHOT"
  :description "Write Clojure data to and from bytes using Kryo."
  :warn-on-reflection true
  :dev-dependencies [[lein-multi "1.1.0-SNAPSHOT"]]
  :dependencies ~(conj shared '[org.clojure/clojure "1.3.0"])
  :multi-deps {"1.2" ~(conj shared '[org.clojure/clojure "1.2.1"])
               "1.4" ~(conj shared '[org.clojure/clojure "1.4.0-alpha3"])})

