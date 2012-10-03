(ns secd.core)

;; (defrecord SECDRegisters [f-pointer stack env code dump])

(defn secd-registers
  [& {:keys [stack env code dump] :as registers}]
  (let [defaults {:stack ()
                  :env ()
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
;; NIL ::  s e (NIL.c) d      => (nil.s) e c d
;; LDC ::  s e (LDC x.c) d    => (x.s) e c d
;; LD  ::  s e (LD (i.j).c) d => ((locate (i.j) e).s) e c d

(definstruct :nil {:keys [stack] :as registers}
  (assoc registers :stack (cons nil stack)))

(definstruct :ldc {:keys [stack code] :as registers}
  (assoc registers
    :stack (cons (peek code) stack)
    :code (pop code)))

(definstruct :ld {:keys [stack env code] :as registers}
  (assoc registers
    :stack (cons (get env (peek code) ::unbound) stack)
    :code (pop code)))

;; Support for built-in functions

;; Unary functions

(defmacro defunary
  [op f]
  `(definstruct ~op registers#
     (let [stack# (:stack registers#)]
       (assoc registers#
         :stack (cons (~f (peek stack#)) (pop stack#))))))

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

;; If-Then-Else instructions

(definstruct :sel {:keys [stack code dump] :as registers}
  (let [test (peek stack)
        [then else & rest] code
        result (if (not (false? test)) then else)]
    (assoc registers
      :stack (pop stack)
      :code result
      :dump (cons rest dump))))

(definstruct :join {:keys [code dump] :as registers}
  (assoc registers
    :code (peek dump)
    :dump (pop dump)))

;; Non-recursive function instructions

(definstruct :ldf {:keys [code env stack] :as registers}
  (assoc registers
    :stack (cons [(peek code) env] stack)
    :code (pop code)))

(definstruct :ap {:keys [stack env code dump]}
  (let [[closure args & rest] stack
        [function context] closure]
    (secd-registers :env (cons args context)
                    :code function
                    :dump (concat [rest env code] dump))))

(definstruct :rtn {:keys [stack env code dump] :as registers}
  (let [[s e c & d] dump]
    (assoc registers
      :stack (cons (peek stack) s)
      :env e
      :code c
      :dump d)))

;; Recursive function instructions

(definstruct :dum {:keys [env] :as registers}
  (assoc registers :env (cons nil env)))

;; TODO: recursive apply (:rap)
