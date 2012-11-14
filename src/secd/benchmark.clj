(ns secd.benchmark
  (:use [secd.machine]))

(comment

  (defn odd?-secd [n]
    (let [super-awesome-even-or-odd-abstraction
          (fn [even? idx]
            [LDC 0 LD [0 0] EQ
             SEL
             [LDC even? JOIN]
             [NIL LDC 1 LD [0 0] SUB CONS
              LD [1 idx] AP
              JOIN]
             RTN])]
      (-> (do-secd* [DUM NIL
                     LDF (super-awesome-even-or-odd-abstraction true 0)
                     CONS
                     LDF (super-awesome-even-or-odd-abstraction false 1)
                     CONS
                     LDF [NIL LDC n CONS LD [0 0] AP RTN]
                     RAP])
          :stack first)))

  ;;; Naive factorial
  (defn fact [n]
    (if (= n 0) 1 (* n (fact (- n 1)))))

  ;; ~8 msecs
  (println "Clojure (fact 10) 1e4 times")
  (dotimes [_ 5]
    (time
     (dotimes [_ 1e4]
       (fact 10))))

  (defn fact-secd [n]
    (-> (do-secd* [DUM NIL
                   LDF [LDC 0 LD [0 0] EQ ;; if (= n 0)
                        TEST
                        [LDC 1 RTN]       ;; return 1
                        LD [0 0]           ;; else load n
                        NIL
                        LDC 1 LD [0 0] SUB CONS ;; build (- n 1) args
                        LD [1 0] AP               ;; load fact and apply
                        MTY                ;; and multiply the result by n
                        RTN]
                   CONS
                   LDF [NIL LDC n CONS LD [0 0] DAP]
                   RAP])
        :stack first))

  (fact-secd 10)

  ;; ~1920 msecs
  (println "SECD (fact 10) 1e4 times")
  (dotimes [_ 5]
    (time
     (dotimes [_ 1e4]
       (fact-secd 10))))

  ;;; Tail-recursive factorial
  (defn fact-tr [n]
    (letfn [(fact [n acc]
              (if (= n 0) acc (recur (dec n) (* n acc))))]
      (fact n 1)))

  ;; ~6 msecs
  (dotimes [_ 5]
    (time
     (dotimes [_ 1e4]
       (fact-tr 10))))

  (defn fact-tr-secd [n]
    (-> (do-secd* [DUM NIL
                   LDF [LDC 0 LD [0 0] EQ
                        TEST
                        [LD [0 1] RTN]
                        NIL
                        LD [0 0] LD [0 1] MTY CONS
                        LDC 1 LD [0 0] SUB CONS
                        LD [1 0] DAP
                        RTN]
                   CONS
                   LDF [NIL LDC 1 CONS LDC n CONS
                        LD [0 0] DAP]
                   RAP])
        :stack first))

  (fact-tr-secd 10)

  ;; ~2000 msecs
  (dotimes [_ 5]
    (time
     (dotimes [_ 1e4]
       (fact-tr-secd 10))))

  ;;; Naive fibonacci
  (defn fib [n]
    (if (<= 1 n) n (+ (fib (- n 1)) (fib (- n 2)))))

  ;; ~1 msecs
  (println "Clojure (fib 5) 1e4 times")
  (dotimes [_ 5]
    (time
     (dotimes [_ 1e4]
       (fib 5))))

  (defn fib-secd [n]
    (-> (do-secd* [DUM NIL
                   LDF [LDC 1 LD [0 0] LTE
                        TEST
                        [LD [0 0] RTN]
                        NIL LDC 1 LD [0 0] SUB CONS
                        LD [1 0] AP
                        NIL LDC 2 LD [0 0] SUB CONS
                        LD [1 0] AP
                        ADD
                        RTN]
                   CONS
                   LDF [NIL LDC n CONS
                        LD [0 0] AP RTN]
                   RAP])
        :stack first))

  (fib-secd 5)

  ;; ~2475 msecs
  (println "SECD (fib 5) 1e4 times")
  (dotimes [_ 5]
    (time
     (dotimes [_ 1e4]
       (fib-secd 5))))

  )
