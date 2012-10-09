(ns secd.core
  (:require [secd.util :as util]))

(def register (constantly ()))

(defn atomify [x]
  (or (util/atom? x) (atom x)))

(defn secd-registers
  [& {:keys [stack env code dump] :as registers}]
  (let [defaults (zipmap [:stack :env :code :dump] (repeatedly 4 register))]
    (util/fmap atomify (merge defaults registers))))

;; SECD Basic Instruction Set

(defmulti doinstruct
  (fn [op registers] op))

(defmacro definstruct
  [op register-binding & body]
  `(defmethod doinstruct ~op [op# ~register-binding]
     ~@body))

;; Access objects and push values to the stack:
;; NIL  ::  s e (NIL.c) d      => (nil.s) e c d
;; LDC  ::  s e (LDC x.c) d    => (x.s) e c d
;; LD   ::  s e (LD (i.j).c) d => ((locate (i.j) e).s) e c d

(definstruct :nil {:keys [stack] :as registers}
  (swap! stack #(cons nil %))
  registers)

(definstruct :ldc {:keys [stack code] :as registers}
  (swap! stack #(cons (first @code) %))
  (swap! code rest)
  registers)

(definstruct :ld {:keys [stack env code] :as registers}
  (swap! stack #(cons (apply util/nnth @env (first @code)) %))
  (swap! code rest)
  registers)

;; Support for built-in functions

;; Unary functions

(defmacro defunary
  [op f]
  `(definstruct ~op registers#
     (let [stack# (:stack registers#)]
       (swap! stack# #(cons (~f (first %)) (rest %)))
       registers#)))

(defunary :atom (complement coll?))
(defunary :null nil?)
(defunary :car first)
(defunary :cdr rest)

;; Binary functions

(defmacro defbinary
  [op f]
  `(definstruct ~op registers#
     (let [[a# b# & stack#] @(:stack registers#)]
       (reset! (:stack registers#) (cons (~f a# b#) stack#))
       registers#)))

(defbinary :cons cons)
(defbinary :add +)
(defbinary :sub -)
(defbinary :mty *)
(defbinary :div /)

;; If-Then-Else instructions

(definstruct :sel {:keys [stack code dump] :as registers}
  (let [test (first @stack)
        [then else & more] @code
        result (if (not (false? test)) then else)]
    (swap! stack rest)
    (reset! code result)
    (swap! dump #(cons more %))
    registers))

(definstruct :join {:keys [code dump] :as registers}
  (reset! code (first @dump))
  (swap! dump rest)
  registers)

;; Non-recursive function instructions

(definstruct :ldf {:keys [code env stack] :as registers}
  (swap! stack #(cons [(first @code) @env] %))
  (swap! code rest)
  registers)

(definstruct :ap {:keys [stack env code dump] :as registers}
  (let [[closure args & more] @stack
        [function context] closure]
    (swap! dump #(concat [more @env @code] %))
    (reset! env (cons args context))
    (reset! code function)
    (reset! stack (register))
    registers))

(definstruct :rtn {:keys [stack env code dump] :as registers}
  (let [[s e c & d] @dump]
    (swap! stack #(cons (first %) s))
    (reset! env e)
    (reset! code c)
    (reset! dump d)
    registers))

;; Recursive function instructions
;; DUM :: s e (DUM.c) d => s (nil.e) c d
;; RAP :: ((f.(nil.e)) v.s) (nil.e) (RAP.c) d
;;     => nil (rplaca((nil.e),v).e) f (s e c.d)

(definstruct :dum {:keys [env] :as registers}
  (swap! env #(cons nil %))
  registers)

;; TODO: RAP instruction, which requires mutable registers (rplaca)

(defn do-secd* [code]
  (if-let [code (and (seq code) (into () (reverse code)))]
    (loop [registers (secd-registers :code code)]
      (if-let [instruction (and (seq @(:code registers))
                                (first @(:code registers)))]
        (do (swap! (:code registers) rest)
            (recur (doinstruct instruction registers)))
        registers))))
