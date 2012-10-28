(ns secd.core
  (:require [secd.util :as util]))

(defrecord Registers [stack env code dump])

(defn secd-registers
  [& {:keys [stack env code dump] :as registers}]
  (let [default (apply ->Registers (repeat 4 ()))]
    (merge default registers)))

(defprotocol Instruction
  (run [this registers] "Run the instruction and return a new set of registers."))

(defn- record-name [sym] (symbol (str sym "-I")))
(defn- constructor [sym] (symbol (str "->" sym "-I")))

(defmacro definstruct
  "Defines an instruction that executes change on SECD registers.

  A single binding for the registers is expected. Destructured values will be
  bound to the actual register atoms.

  The body of a definstruct is expected to return a map of register-names to
  update values that will be used to update the registers. The full set of
  registers will always be returned.

  ex.
    (definstruct :nil {:keys [stack] :as regs}
      {:stack (cons nil @stack)})"
  [op register-bindings & body]
  (let [register-bindings (zipmap register-bindings [:stack :env :code :dump])]
    `(do (defrecord ~(record-name op) []
           ~'Instruction
           (~'run [_# ~register-bindings]
             ~@body))
         (def ~op (~(constructor op))))))

;; s e (NIL.c) d => (nil.s) e c d
(definstruct NIL [s e c d]
  (->Registers (cons nil s) e c d))

;; s e (LDC x.c) d => (x.s) e c d
(definstruct LDC [s e c d]
  (->Registers (cons (first c) s) e (rest c) d))

(defn locate
  [env x y]
  (let [inner (nth env x)
        derefed (if (util/atom? inner)
                  @inner inner)]
    (nth derefed y)))

;; s e (LD [i j].c) d => ((locate e i j).s) e c d
(definstruct LD [s e c d]
  (->Registers (cons (apply locate e (first c)) s) e (rest c) d))

;; (x.s) e (OP.c) d => ((OP x).s) e c d
(defmacro defunary
  [op f]
  `(definstruct ~op [~'stack ~'env ~'code ~'dump]
     (~'->Registers (cons (~f (first ~'stack)) (rest ~'stack))
                    ~'env ~'code ~'dump)))

(defunary ATOM (complement coll?))
(defunary NULL #(if (coll? %) (empty? %) (nil? %)))
(defunary CAR first)
(defunary CDR rest)

;; (x y.s) e (OP.c) d => ((OP x y).s) e c d
(defmacro defbinary
  [op f]
  `(definstruct ~op [~'stack ~'env ~'code ~'dump]
     (let [[a# b# & stack#] ~'stack]
       (~'->Registers (cons (~f a# b#) stack#)
                      ~'env ~'code ~'dump))))

(defbinary CONS cons)
(defbinary ADD +)
(defbinary SUB -)
(defbinary MTY *)
(defbinary DIV /)
(defbinary EQ =)
(defbinary GT >)
(defbinary LT <)
(defbinary GTE >=)
(defbinary LTE <=)

;; (x.s) e (SEL then else.c) d => s e c? (c.d)
;; where c? is (if x then else)
(definstruct SEL [s e c d]
  (let [test (first s)
        [then else & more] c
        result (if-not (false? test) then else)]
    (->Registers (rest s) e result (cons more d))))

;; s e (JOIN.c) (cr.d) => s e cr d
(definstruct JOIN [s e c d]
  (->Registers s e (first d) (rest d)))

;; (x.s) e (TEST ct.c) d => s e c? d
;; where c? is (if x ct c)
(definstruct TEST [s e c d]
  (let [test (first s)
        [then & else] c
        result (if-not (false? test) then else)]
    (->Registers (rest s) e result d)))

;; (v.s) e (AA.c) d => s (v.e) c d
(definstruct AA [s e c d]
  (->Registers (rest s) (cons (first s) e) c d))

;; s e (LDF f.c) d => ([f e].s) e c d
(definstruct LDF [s e c d]
  (->Registers (cons [(first c) e] s) e (rest c) d))

;; ([f e'] v.s) e (AP.c) d => nil (v.e') f (s e c.d)
(definstruct AP [s e c d]
  (let [[closure args & more] s
        [function context] closure]
    (->Registers () (cons args context) function (concat [more e c] d))))

;; DAP :: Direct APply leaves dump alone
;; ([f e'] v.s) e (DAP.c) d => nil (v.e') f d
(definstruct DAP [s e c d]
  (let [[closure args & more] s
        [function context] closure]
    (->Registers () (cons args context) function d)))

;; (x.z) e' (RTN.q) (s e c.d) => (x.s) e c d
(definstruct RTN [stack env code dump]
  (let [[s e c & d] dump]
    (->Registers (cons (first stack) s) e c d)))

;; s e (DUM.c) d => s ((atom nil).e) c d
(definstruct DUM [s e c d]
  (->Registers s (cons (atom ()) e) c d))

;; ([f (nil.e)] v.s) (nil.e) (RAP.c) d =>
;; nil (rplaca((nil.e), v).e) f (s e c.d)
(definstruct RAP [s e c d]
  (let [[closure args & more] s
        [function context] closure
        old-e e]
    (reset! (first e) args)
    (->Registers () e function (concat [more old-e c] d))))

;; (x.s) e (WRITEC.c d => s e c d, where x is a char printed to output
(definstruct WRITEC [s e c d]
  (print (char (first s)))
  (->Registers (rest s) e c d))

(defn do-secd*
  ([code]
     ;; This is a pretty ugly kludge to just "go till :code is empty"
     (do-secd* -1 code))
  ([n code]
     (if-let [code (and (seq code) (into () (reverse code)))]
       (loop [n n registers (secd-registers :code code)]
         (if-let [instruction (and (not= n 0)
                                   (seq (:code registers))
                                   (first (:code registers)))]
           (recur (dec n) (run instruction (update-in registers [:code] rest)))
           registers)))))
