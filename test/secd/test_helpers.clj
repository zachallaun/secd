(ns secd.test-helpers
  (:use midje.sweet
        [secd.util :only [atom?]]))

(defn structure-checker
  "Returns a function used to generate higher-order functions that can act as
  composable predicates that verify attributes of some data structure.

  ex. (def stack-is (structure-checker :stack))
      ((stack-is []) {:stack []})"
  [selector]
  (fn [pred]
    (fn [structure]
      (if (fn? pred)
        (and (pred (selector structure)) structure)
        (and (= pred (selector structure)) structure)))))

(defn map-similar-to
  "Returns a function that assesses similarity. Assumes coll and coll2 to be
  an associative structure with values that may be atoms. Two atoms are similar
  if their dereferenced values are the same."
  [coll]
  {:pre [(map? coll)]}
  (fn [coll2]
    {:pre [(map? coll2)]}
    (and (every? (set (keys coll)) (keys coll2))
         (every? (fn [[k a]]
                   (let [v (get coll2 k)
                         v (if (atom? v) @v v)
                         a (if (atom? a) @a a)]
                     (= a v)))
                 coll))))

(fact "about map-similar-to"
      (map-similar-to []) => (throws AssertionError)
      ((map-similar-to {}) []) => (throws AssertionError)

      ((map-similar-to {})             {}) => truthy
      ((map-similar-to {:k :v})        {:k :v}) => truthy
      ((map-similar-to {:k (atom :v)}) {:k (atom :v)}) => truthy
      ((map-similar-to {:k :v})        {:k (atom :v)}) => truthy
      ((map-similar-to {:k :v})        {:k :v :k2 :v2}) => falsey)
