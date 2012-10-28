(ns secd.benchmark
  (:use [secd.core]))

(comment

  (defn odd?-secd [n]
    (let [super-awesome-even-or-odd-abstraction
          (fn [even? idx]
            [:ldc 0 :ld [0 0] :eq
             :sel
             [:ldc even? :join]
             [:nil :ldc 1 :ld [0 0] :sub :cons
              :ld [1 idx] :ap
              :join]
             :rtn])]
      (-> (do-secd* [:dum :nil
                     :ldf (super-awesome-even-or-odd-abstraction true 0)
                     :cons
                     :ldf (super-awesome-even-or-odd-abstraction false 1)
                     :cons
                     :ldf [:nil :ldc n :cons :ld [0 0] :ap :rtn]
                     :rap])
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
    (-> (do-secd* [:dum :nil
                   :ldf [:ldc 0 :ld [0 0] :eq ;; if (= n 0)
                         :test
                         [:ldc 1 :rtn]       ;; return 1
                         :ld [0 0]           ;; else load n
                         :nil
                         :ldc 1 :ld [0 0] :sub :cons ;; build (- n 1) args
                         :ld [1 0] :ap               ;; load fact and apply
                         :mty                ;; and multiply the result by n
                         :rtn]
                   :cons
                   :ldf [:nil :ldc n :cons :ld [0 0] :ap :rtn]
                   :rap])
        :stack first))

  (fact-secd 10)

  ;; 4700 msecs
  (println "SECD (fact 10) 1e4 times")
  (dotimes [_ 5]
    (time
     (dotimes [_ 1e4]
       (fact-secd 10))))

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
    (-> (do-secd* [:dum :nil
                   :ldf [:ldc 1 :ld [0 0] :lte
                         :test
                         [:ld [0 0] :rtn]
                         :nil :ldc 1 :ld [0 0] :sub :cons
                         :ld [1 0] :ap
                         :nil :ldc 2 :ld [0 0] :sub :cons
                         :ld [1 0] :ap
                         :add
                         :rtn]
                   :cons
                   :ldf [:nil :ldc n :cons
                         :ld [0 0] :ap :rtn]
                   :rap])
        :stack first))

  ;; 6025 msecs
  (println "SECD (fib 5) 1e4 times")
  (dotimes [_ 5]
    (time
     (dotimes [_ 1e4]
       (fib-secd 5))))

  )
