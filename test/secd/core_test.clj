(ns secd.core-test
  (:use midje.sweet
        secd.core))

(defn register-checker [selector]
  (fn [pred]
    (fn [registers]
      (if (fn? pred)
        (pred (selector registers))
        (= pred (selector registers))))))

(def stack-is (register-checker :stack))
(def fstack-is (register-checker (comp first :stack)))

(fact "about SECD register defaults"
      (secd-registers) => {:stack () :env [] :code () :dump ()}
      (secd-registers :stack '(:a :b :c)) => {:stack '(:a :b :c)
                                              :env () :code () :dump ()})

(fact "about :nil instruction"
      (doinstruct :nil (secd-registers)) => (secd-registers :stack '(nil)))

(fact "about :ldc instruction"
      (let [registers (secd-registers :code '(:a))]
        (doinstruct :ldc registers) => (secd-registers :stack '(:a))))

(fact "about :ld instruction"
      (doinstruct :ld (secd-registers :code '([0 0]) :env [[:v1]]))
      => (secd-registers :stack '(:v1) :env [[:v1]])

      (doinstruct :ld (secd-registers :code '([0 0]) :env '[(:v1)]))
      => (secd-registers :stack '(:v1) :env '[(:v1)])

      (doinstruct :ld (secd-registers :code '([1 1]) :env '[() (:v1 :v2)]))
      => (secd-registers :stack '(:v2) :env '[() (:v1 :v2)]))

(fact "about unary built-ins"
      (doinstruct :atom (secd-registers :stack '(:a)))
      => (secd-registers :stack '(true))

      (doinstruct :atom (secd-registers :stack '([])))
      => (secd-registers :stack '(false))

      (doinstruct :null (secd-registers :stack '(nil)))
      => (secd-registers :stack '(true))

      (doinstruct :null (secd-registers :stack '(:not-nil)))
      => (secd-registers :stack '(false))

      (doinstruct :car (secd-registers :stack '((1))))
      => (secd-registers :stack '(1))

      (doinstruct :cdr (secd-registers :stack '((1 2 3))))
      => (secd-registers :stack '((2 3))))

(fact "about binary built-ins"
      (doinstruct :cons (secd-registers :stack '(1 (2 3))))
      => (secd-registers :stack '((1 2 3)))

      (doinstruct :add (secd-registers :stack '(1 1)))
      => (secd-registers :stack '(2))

      (doinstruct :sub (secd-registers :stack '(1 1)))
      => (secd-registers :stack '(0))

      (doinstruct :mty (secd-registers :stack '(2 2)))
      => (secd-registers :stack '(4)))

(fact "about if-then-else instructions"
      (let [truthy-sel (secd-registers :stack '(:truthy)
                                       :code '(:for-true :for-false :rest))
            falsey-sel (secd-registers :stack '(false)
                                       :code '(:for-true :for-false :rest))]

        (doinstruct :sel truthy-sel)
        => (secd-registers :code :for-true :dump '((:rest)))

        (doinstruct :sel falsey-sel)
        => (secd-registers :code :for-false :dump '((:rest))))

      (doinstruct :join (secd-registers :dump '((:dumped))))
      => (secd-registers :code '(:dumped)))

(fact "about :ldf instruction"
      (doinstruct :ldf (secd-registers :code '(:fn-instructions) :env '(:context)))
      => (secd-registers :stack '([:fn-instructions (:context)])
                         :env '(:context)))

(fact "about :ap instruction"
      (let [registers (secd-registers :stack '([:fn-instructions (:context)]
                                                 :args :rest))]
        (doinstruct :ap registers)
        => (secd-registers :env '(:args :context)
                           :code :fn-instructions
                           :dump '((:rest) () ()))))

(fact "about :rtn instruction"
      (let [registers (secd-registers :stack '(:kept :discarded)
                                      :env '(:discarded)
                                      :code '(:discarded)
                                      :dump '((:rest) :env :code))]
        (doinstruct :rtn registers)
        => (secd-registers :stack '(:kept :rest)
                           :env :env
                           :code :code
                           :dump nil)))

(fact "about :dum instruction"
      (doinstruct :dum (secd-registers)) => (secd-registers :env '(nil))
      (doinstruct :dum (secd-registers :env '(1 2 3)))
      => (secd-registers :env '(nil 1 2 3)))

(fact "about do-secd* termination"
      (do-secd* []) => nil?
      (do-secd* [:nil]) => (fstack-is nil))

(fact "about do-secd* math"
      (do-secd* [:ldc 1 :ldc 2 :add]) => (fstack-is 3)
      (do-secd* [:ldc 1 :ldc 2 :sub]) => (fstack-is 1)
      (do-secd* [:ldc 5 :ldc 5 :mty]) => (fstack-is 25)
      (do-secd* [:ldc 5 :ldc 5 :div]) => (fstack-is 1))

(fact "about do-secd* consing"
      (do-secd* [:nil
                 :ldc 1 :cons
                 :ldc 2 :cons])
      => (fstack-is '(2 1)))

(fact "about do-secd* if-then-else"
      ;; 5 + (if (atom :an-atom) then 1 else 2)
      (do-secd* [:ldc 5
                 :ldc :an-atom
                 :atom
                 :sel
                 '(:ldc 1 :join)
                 '(:ldc 2 :join)
                 :add])
      => (fstack-is 6)

      ;; (5 (if (atom []) then + else -) 5) + 10
      (do-secd* [:ldc 5
                 :ldc 5
                 :ldc []
                 :atom
                 :sel
                 '(:add :join)
                 '(:sub :join)
                 :ldc 10
                 :add])
      => (fstack-is 10))

(fact "about do-secd* fn application"
      ;; let f(x,y)=x+y in f(2*3, 6-4)
      (do-secd* [:nil
                :ldc 4 :ldc 6 :sub :cons
                :ldc 3 :ldc 2 :mty :cons
                :ldf [:ld [0 1] :ld [0 0] :add :rtn]
                :ap])
      => (fstack-is 8))
