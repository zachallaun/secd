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

(defn reset-values!
  "Accepts two maps, one of keys -> atoms, and one of keys -> values, and reset!s
  shared keys in the first map to the values of the second."
  [atom-map updates]
  (doseq [[k v] updates]
    (when-let [item (get atom-map k)]
      (reset! item v)))
  atom-map)
