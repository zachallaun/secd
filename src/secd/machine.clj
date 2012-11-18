(ns secd.machine
  (:require [secd.util :as util]
            [clojure.walk :as walk]))

(defrecord Registers [stack env code dump])

(defn secd-registers
  [& {:keys [stack env code dump] :as registers}]
  (let [default (apply ->Registers (repeat 4 ()))]
    (merge default registers)))

(defprotocol Instruction
  (run [this registers] "Run the instruction and return a new set of registers."))

(defn- record-name [sym] (symbol (str (name sym) "-I")))
(defn- constructor [sym] (symbol (str "->" (name sym) "-I")))

(defn expand-concats [forms]
  (for [form forms]
    (if (and (coll? form) (some #{'.} form))
      (let [[xs _ [more]] (partition-by #{'.} form)]
        `(concat ~(vec xs) ~more))
      form)))

(defn desugar-body [b]
  (let [[body _ where] (partition-by #{:where} b)
        [before _ after] (partition-by #(and (symbol? %) (= "=>" (name %))) body)
        before (walk/prewalk-replace {'. '&} before)
        after `(->Registers ~@(expand-concats after))]
    `(fn ~(vec before)
        ~(if where
           `(let ~(first where)
              ~after)
           after))))

(defmacro definstruct
  "Defines a register transformation."
  [op & body]
  (let [bindings (repeatedly 4 gensym)
        register-bindings (zipmap bindings
                                  [:stack :env :code :dump])
        body (desugar-body (vec body))]
    `(do (defrecord ~(record-name op) []
           ~'Instruction
           (~'run [_# ~register-bindings]
             (~body
              ~@bindings)))
         (def ~op (~(constructor op))))))

(definstruct NIL
  s e c d => [nil . s] e c d)

(definstruct LDC
  s e [x . c] d => [x . s] e c d)

(defn locate
  [env x y]
  (let [inner (nth env x)
        derefed (if (util/atom? inner)
                  @inner inner)]
    (nth derefed y)))

(definstruct LD
  s e [[i j] . c] d => (x . s) e c d
  :where [x (locate e i j)])

(defmacro defunary
  [op f]
  `(definstruct ~op
     [x# . s#] e# c# d# => [x'# . s#] e# c# d#
     :where [x'# (~f x#)]))

(defunary ATOM (complement coll?))
(defunary NULL #(if (coll? %) (empty? %) (nil? %)))
(defunary CAR first)
(defunary CDR rest)

(defmacro defbinary
  [op f]
  `(definstruct ~op
     [x# y# . s#] e# c# d# => [z# . s#] e# c# d#
     :where [z# (~f x# y#)]))

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

(definstruct SEL
  [x . s] e [ct cf . c] d => s e c? [c . d]
  :where [c? (if-not (false? x) ct cf)])

(definstruct JOIN
  s e c [cr . d] => s e cr d)

(definstruct TEST
  [x . s] e [ct . c] d => s e c? d
  :where [c? (if-not (false? x) ct c)])

(definstruct AA
  [v . s] e c d => s [v . e] c d)

(definstruct LDF
  s e [f . c] d => [[f e] . s] e c d)

(definstruct AP
  [[f e'] v . s] e c d => nil [v . e'] f [s e c . d])

(definstruct DAP
  [[f e'] v . s] e c d => nil [v . e'] f d)

(definstruct RTN
  [x . z] e' q [s e c . d] => [x . s] e c d)

(definstruct DUM
  s e c d => s [dum . e] c d
  :where [dum (atom ())])

(definstruct RAP
  [[f context] v . s] e c d => nil e f [s e c . d]
  :where [_ (reset! (first e) v)])

(definstruct WRITEC
  [x . s] e c d => s e c d
  :where [_ (print (char x))])

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

(defn do-secd
  [code]
  (-> (do-secd* code)
      :stack
      first))
