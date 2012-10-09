(ns secd.test-helpers)

(defn structure-checker [selector]
  "Returns a function used to generate higher-order functions that can act as
  composable predicates that verify attributes of some data structure.

  ex. (def stack-is (structure-checker :stack))
      ((stack-is []) {:stack []})"
  (fn [pred]
    (fn [structure]
      (if (fn? pred)
        (and (pred (selector structure)) structure)
        (and (= pred (selector structure)) structure)))))

(def stack-is (structure-checker :stack))
(def fstack-is (structure-checker (comp first :stack)))
(def env-is (structure-checker :env))
(def code-is (structure-checker :code))
(def dump-is (structure-checker :dump))
