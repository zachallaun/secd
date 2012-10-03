(ns secd.core)

(defn atom? [x]
  (instance? clojure.lang.Atom x))

(defn atom-if-not
  "Returns x as an Atom, unless it already is."
  [x]
  (if (atom? x) x (atom x)))

(defrecord SECDRegisters [f-pointer stack env code dump])

(defn secd-registers
  [& {:keys [f-pointer stack env code dump] :as registers}]
  (let [registers (reduce (fn [acc [k v]]
                            (assoc acc k (atom-if-not v)))
                          {} registers)
        defaults {:f-pointer (atom 0)
                  :stack (atom [])
                  :env (atom [])
                  :code (atom [])
                  :dump (atom [])}]
    (merge defaults registers)))

;; SECD Basic Instruction Set

;; Access objects and push values to the stack:
;; NIL ::  s e (NIL.c) d      => (nil.s) e c d
;; LDC ::  s e (LDC x.c) d    => (x.s) e c d
;; LD  ::  s e (LD (i.j).c) d => ((locate (i.j) e).s) e c d

;; (definstruct :nil {:keys [stack] :as registers}
;;   (assoc registers :stack (cons nil stack)))
