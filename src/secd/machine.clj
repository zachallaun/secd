(ns secd.machine
  (:require [secd.util :as util])
  (:use [secd.magic :only [definstruct secd-registers run]]))

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
