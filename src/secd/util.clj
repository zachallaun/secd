(ns secd.util)

(defn atom? [x]
  (instance? clojure.lang.Atom x))

(defn nnth
  "(nnth coll 0 1) => (nth (nth coll 0) 1)"
  [coll n1 n2]
  (nth (nth coll n1) n2))
