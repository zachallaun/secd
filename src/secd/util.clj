(ns secd.util)

(defn atom? [x]
  (and (instance? clojure.lang.Atom x) x))

(defn nnth
  "(nnth coll 0 1) => (nth (nth coll 0) 1)"
  [coll n1 n2]
  (nth (nth coll n1) n2))

(defn fmap
  "Applies a function to every value in a map."
  [f m]
  (reduce (fn [init [k v]] (assoc init k (f v))) {} m))
