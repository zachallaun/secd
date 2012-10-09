(ns secd.util)

(defn atom? [x]
  (instance? clojure.lang.Atom x))
