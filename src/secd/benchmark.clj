(ns secd.benchmark
  (:use [secd.core]))

;;; Naive factorial
(defn fact [n]
  (if (= n 0) 1 (* n (fact (- n 1)))))

;; ~10 msecs
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
(dotimes [_ 5]
  (time
   (dotimes [_ 1e4]
     (fact-secd 10))))
