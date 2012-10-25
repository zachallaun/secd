(ns secd.core
  (:require [secd.util :as util]))

(defn secd-registers
  [& {:keys [stack env code dump] :as registers}]
  (let [defaults (zipmap [:stack :env :code :dump] (repeat 4 ()))]
    (merge defaults registers)))

(defmulti doinstruct
  (fn [op registers] op))

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
  [op register-binding & body]
  (let [registers (or (:as register-binding) (gensym "registers"))
        register-binding (assoc register-binding :as registers)]
    `(defmethod doinstruct ~op [op# ~register-binding]
       (let [updates# (do ~@body)]
         (merge ~registers updates#)))))

;; s e (NIL.c) d => (nil.s) e c d
(definstruct :nil {:keys [stack]}
  {:stack (cons nil stack)})

;; s e (LDC x.c) d => (x.s) e c d
(definstruct :ldc {:keys [stack code]}
  {:stack (cons (first code) stack)
   :code (rest code)})

(defn locate
  [env x y]
  (let [inner (nth env x)
        derefed (if (util/atom? inner)
                  @inner inner)]
    (nth derefed y)))

;; s e (LD [i j].c) d => ((locate e i j).s) e c d
(definstruct :ld {:keys [stack env code]}
  {:stack (cons (apply locate env (first code)) stack)
   :code (rest code)})

;; (x.s) e (OP.c) d => ((OP x).s) e c d
(defmacro defunary
  [op f]
  `(definstruct ~op {:keys [~'stack]}
     {:stack (cons (~f (first ~'stack)) (rest ~'stack))}))

(defunary :atom (complement coll?))
(defunary :null nil?)
(defunary :car first)
(defunary :cdr rest)

;; (x y.s) e (OP.c) d => ((OP x y).s) e c d
(defmacro defbinary
  [op f]
  `(definstruct ~op {stack-a# :stack}
     (let [[a# b# & stack#] stack-a#]
       {:stack (cons (~f a# b#) stack#)})))

(defbinary :cons cons)
(defbinary :add +)
(defbinary :sub -)
(defbinary :mty *)
(defbinary :div /)
(defbinary :eq =)
(defbinary :gt >)
(defbinary :lt <)
(defbinary :gte >=)
(defbinary :lte <=)

;; (x.s) e (SEL then else.c) d => s e c? (c.d)
;; where c? is (if x then else)
(definstruct :sel {:keys [stack code dump]}
  (let [test (first stack)
        [then else & more] code
        result (if-not (false? test) then else)]
    {:stack (rest stack)
     :code result
     :dump (cons more dump)}))

;; s e (JOIN.c) (cr.d) => s e cr d
(definstruct :join {:keys [code dump]}
  {:code (first dump)
   :dump (rest dump)})

;; s e (LDF f.c) d => ([f e].s) e c d
(definstruct :ldf {:keys [code env stack]}
  {:stack (cons [(first code) env] stack)
   :code (rest code)})

;; ([f e'] v.s) e (AP.c) d => nil (v.e') f (s e c.d)
(definstruct :ap {:keys [stack env code dump]}
  (let [[closure args & more] stack
        [function context] closure]
    {:stack ()
     :env (cons args context)
     :code function
     :dump (concat [more env code] dump)}))

;; (x.z) e' (RTN.q) (s e c.d) => (x.s) e c d
(definstruct :rtn {:keys [stack env code dump]}
  (let [[s e c & d] dump]
    {:stack (cons (first stack) s)
     :env e :code c :dump d}))

;; s e (DUM.c) d => s ((atom nil).e) c d
(definstruct :dum {:keys [env]}
  {:env (cons (atom ()) env)})

;; ([f (nil.e)] v.s) (nil.e) (RAP.c) d =>
;; nil (rplaca((nil.e), v).e) f (s e c.d)
(definstruct :rap {:keys [stack env code dump]}
  (let [[closure args & more] stack
        [function context] closure
        old-env env]
    (reset! (first env) args)
    {:stack ()
     :code function
     :dump (concat [more old-env code] dump)}))

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
           (recur (dec n) (doinstruct instruction (update-in registers
                                                             [:code] rest)))
           registers)))))
