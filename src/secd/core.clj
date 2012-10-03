(ns secd.core)

(defrecord SECDRegisters [f-pointer stack env code dump])

(defn secd-registers
  [& {:keys [stack env code dump] :as registers}]
  (let [defaults (zipmap [:stack :env :code :dump]
                         (repeat 4 []))]
    (merge defaults registers)))

;; SECD Basic Instruction Set

(defmulti doinstruct
  (fn [op registers] op))

(defmacro definstruct
  [op register-binding & body]
  `(defmethod doinstruct ~op [op# ~register-binding]
     ~@body))

;; Access objects and push values to the stack:
;; NIL ::  s e (NIL.c) d      => (nil.s) e c d
;; LDC ::  s e (LDC x.c) d    => (x.s) e c d
;; LD  ::  s e (LD (i.j).c) d => ((locate (i.j) e).s) e c d

(definstruct :nil {:keys [stack] :as registers}
  (assoc registers :stack (cons nil stack)))
