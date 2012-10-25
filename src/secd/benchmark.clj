(ns secd.benchmark
  (:use [secd.core]))

(comment

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
    (-> (do-secd* [:dum :nil
                   :ldf [:ldc 0 :ld [0 0] :eq ;; if (= n 0)
                         :sel
                         [:ldc 1 :join]       ;; return 1
                         [:ld [0 0]           ;; else load n
                          :nil
                          :ldc 1 :ld [0 0] :sub :cons ;; build (- n 1) args
                          :ld [1 0] :ap               ;; load fact and apply
                          :mty                ;; and multiply the result by n
                          :join]
                         :rtn]
                   :cons
                   :ldf [:nil :ldc n :cons :ld [0 0] :ap :rtn]
                   :rap])
        :stack deref first))

  ;; Great news everyone! It's only about 400x slower!
  ;; 3700 msecs
  (println "SECD (fact 10) 1e4 times")
  (dotimes [_ 5]
    (time
     (dotimes [_ 1e4]
       (fact-secd 10))))

  ;;; Naive fibonacci
  (defn fib [n]
    (if (<= 1 n) n (+ (fib (- n 1)) (fib (- n 2)))))

  ;; ~2 msecs
  (println "Clojure (fib 20) 1e4 times")
  (dotimes [_ 5]
    (time
     (dotimes [_ 1e4]
       (fib 20))))

  (defn fib-secd [n]
    (-> (do-secd* [:dum :nil
                   :ldf [:ldc 1 :ld [0 0] :lte
                         :sel
                         [:ld [0 0] :join]
                         [:nil :ldc 1 :ld [0 0] :sub :cons
                          :ld [1 0] :ap
                          :nil :ldc 2 :ld [0 0] :sub :cons
                          :ld [1 0] :ap
                          :add
                          :join]
                         :rtn]
                   :cons
                   :ldf [:nil :ldc 5 :cons
                         :ld [0 0] :ap :rtn]
                   :rap])
        :stack deref first))

  ;; MUAHAHAHAHAHAHA!!! Only 2500x slower!!!
  ;; 5000 msecs
  (println "SECD (fib 20) 1e4 times")
  (dotimes [_ 5]
    (time
     (dotimes [_ 1e4]
       (fib-secd 20))))

  )
