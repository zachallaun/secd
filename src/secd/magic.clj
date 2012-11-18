(ns secd.magic
  (:require [clojure.walk :as walk]))

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
           Instruction
           (run [_# ~register-bindings]
             (~body
              ~@bindings)))
         (def ~op (~(constructor op))))))

