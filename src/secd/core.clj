(ns secd.core)

;; (defrecord SECDRegisters [f-pointer stack env code dump])

(defn secd-registers
  [& {:keys [stack env code dump] :as registers}]
  (let [defaults {:stack ()
                  :env []
                  :code ()
                  :dump ()}]
    (merge defaults registers)))

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
  (assoc registers :stack (cons nil stack)))

(definstruct :ldc {:keys [stack code] :as registers}
  (assoc registers
    :stack (cons (first code) stack)
    :code (rest code)))

(defn nnth
  "(nnth coll 0 1) => (nth (nth coll 0) 1)"
  [coll n1 n2]
  (nth (nth coll n1) n2))

(definstruct :ld {:keys [stack env code] :as registers}
  (assoc registers
    :stack (cons (apply nnth env (first code)) stack)
    :code (rest code)))

;; Support for built-in functions

;; Unary functions

(defmacro defunary
  [op f]
  `(definstruct ~op registers#
     (let [stack# (:stack registers#)]
       (assoc registers#
         :stack (cons (~f (first stack#)) (rest stack#))))))

(defunary :atom (complement coll?))
(defunary :car first)
(defunary :cdr rest)

;; Binary functions

(defmacro defbinary
  [op f]
  `(definstruct ~op registers#
     (let [[a# b# & stack#] (:stack registers#)]
       (assoc registers#
         :stack (cons (~f a# b#) stack#)))))

(defbinary :cons cons)
(defbinary :add +)
(defbinary :sub -)
(defbinary :mult *)
(defbinary :div /)

;; If-Then-Else instructions

(definstruct :sel {:keys [stack code dump] :as registers}
  (let [test (first stack)
        [then else & more] code
        result (if (not (false? test)) then else)]
    (assoc registers
      :stack (rest stack)
      :code result
      :dump (cons more dump))))

(definstruct :join {:keys [code dump] :as registers}
  (assoc registers
    :code (first dump)
    :dump (rest dump)))

;; Non-recursive function instructions

(definstruct :ldf {:keys [code env stack] :as registers}
  (assoc registers
    :stack (cons [(first code) env] stack)
    :code (rest code)))

(definstruct :ap {:keys [stack env code dump]}
  (let [[closure args & more] stack
        [function context] closure]
    (secd-registers :env (cons args context)
                    :code function
                    :dump (concat [more env code] dump))))

(definstruct :rtn {:keys [stack env code dump] :as registers}
  (let [[s e c & d] dump]
    (assoc registers
      :stack (cons (first stack) s)
      :env e
      :code c
      :dump d)))

;; Recursive function instructions

(definstruct :dum {:keys [env] :as registers}
  (assoc registers :env (cons nil env)))



;; TODO: recursive apply (:rap)

(defn do-secd* [code]
  (if-let [code (and (seq code) (into () (reverse code)))]
    (loop [registers (secd-registers :code code)]
      (if-let [instruction (and (seq (:code registers))
                                (first (:code registers)))]
        (recur (doinstruct instruction (update-in registers [:code] rest)))
        registers))))
