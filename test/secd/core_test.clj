(ns secd.core-test
  (:use midje.sweet
        secd.test-helpers
        secd.core))

(def stack-is (structure-checker (comp deref :stack)))
(def fstack-is (structure-checker (comp first deref :stack)))
(def env-is (structure-checker (comp deref :env)))
(def code-is (structure-checker (comp deref :code)))
(def dump-is (structure-checker (comp deref :dump)))

(fact "about SECD register defaults"
      (secd-registers) => (map-similar-to {:stack (atom ()) :env (atom ())
                                           :code (atom ()) :dump (atom ())})
      (secd-registers :stack '(:a :b :c))
      => (map-similar-to {:stack (atom '(:a :b :c)) :env (atom ())
                          :code (atom ()) :dump (atom ())}))

(fact "about definstruct return value"
      ;; definstruct expects the return value of the body to be a map
      (definstruct :foo {} :foo)
      (doinstruct :foo (secd-registers)) => (throws IllegalArgumentException)

      (definstruct :bar {} {})
      (doinstruct :bar (secd-registers)) => truthy)

(fact "about :nil instruction"
      (doinstruct :nil (secd-registers)) => (fstack-is nil))

(fact "about :ldc instruction"
      (let [registers (secd-registers :code '(:a))]
        (doinstruct :ldc registers) => (fstack-is :a)))

(fact "about locate"
      (locate [[:v1]] 0 0) => :v1
      (locate [(atom [:v1])] 0 0) => :v1)

(fact "about :ld instruction"
      (doinstruct :ld (secd-registers :code '([0 0]) :env [[:v1]]))
      => (comp (fstack-is :v1) (env-is [[:v1]]))

      (doinstruct :ld (secd-registers :code '([0 0]) :env '[(:v1)]))
      => (comp (fstack-is :v1) (env-is '[(:v1)]))

      (doinstruct :ld (secd-registers :code '([1 1]) :env '[() (:v1 :v2)]))
      => (comp (fstack-is :v2) (env-is '[() (:v1 :v2)])))

(fact "about unary built-ins"
      (doinstruct :atom (secd-registers :stack '(:a)))       => (fstack-is true)
      (doinstruct :atom (secd-registers :stack '([])))       => (fstack-is false)
      (doinstruct :null (secd-registers :stack '(nil)))      => (fstack-is true)
      (doinstruct :null (secd-registers :stack '(:not-nil))) => (fstack-is false)
      (doinstruct :car (secd-registers :stack '((1))))       => (fstack-is 1)
      (doinstruct :cdr (secd-registers :stack '((1 2 3))))   => (fstack-is '(2 3)))

(fact "about binary built-ins"
      (doinstruct :cons (secd-registers :stack '(1 (2 3)))) => (fstack-is '(1 2 3))
      (doinstruct :add (secd-registers :stack '(1 1))) => (fstack-is 2)
      (doinstruct :sub (secd-registers :stack '(1 1))) => (fstack-is 0)
      (doinstruct :mty (secd-registers :stack '(2 2))) => (fstack-is 4)
      (doinstruct :div (secd-registers :stack '(4 2))) => (fstack-is 2)
      (doinstruct :eq (secd-registers :stack '(3 2))) => (fstack-is falsey)
      (doinstruct :eq (secd-registers :stack '(2 2))) => (fstack-is truthy)
      (doinstruct :gt (secd-registers :stack '(3 2))) => (fstack-is truthy)
      (doinstruct :gt (secd-registers :stack '(2 3))) => (fstack-is falsey)
      (doinstruct :lt (secd-registers :stack '(3 2))) => (fstack-is falsey)
      (doinstruct :lt (secd-registers :stack '(2 3))) => (fstack-is truthy)
      (doinstruct :gte (secd-registers :stack '(3 2))) => (fstack-is truthy)
      (doinstruct :gte (secd-registers :stack '(2 3))) => (fstack-is falsey)
      (doinstruct :gte (secd-registers :stack '(2 2))) => (fstack-is truthy)
      (doinstruct :lte (secd-registers :stack '(3 2))) => (fstack-is falsey)
      (doinstruct :lte (secd-registers :stack '(2 3))) => (fstack-is truthy)
      (doinstruct :lte (secd-registers :stack '(2 2))) => (fstack-is truthy))

(fact "about if-then-else instructions"
      (let [truthy-sel (secd-registers :stack '(:truthy)
                                       :code '(:for-true :for-false :rest))
            falsey-sel (secd-registers :stack '(false)
                                       :code '(:for-true :for-false :rest))]

        (doinstruct :sel truthy-sel) => (comp (code-is :for-true)
                                              (dump-is '((:rest))))

        (doinstruct :sel falsey-sel) => (comp (code-is :for-false)
                                              (dump-is '((:rest)))))

      (doinstruct :join (secd-registers :dump '((:dumped)))) => (code-is '(:dumped)))

(fact "about :ldf instruction"
      (let [env-atom (atom '(:context))]
        (doinstruct :ldf (secd-registers :code '(:fn-instructions) :env env-atom))
        => (fn [{:keys [stack env code dump]}]
             (and (= env-atom (-> stack deref first second))
                  (= env-atom env)
                  (not (seq @code))))))

(fact "about :ap instruction"
      (let [registers (secd-registers :stack `([:fn-instructions ~(atom '(:context))]
                                                 :args :rest))]
        (doinstruct :ap registers)
        => (map-similar-to
            (secd-registers :env '(:args :context)
                            :code :fn-instructions
                            :dump '((:rest) () ())))))

(fact "about :rtn instruction"
      (let [registers (secd-registers :stack '(:kept :discarded)
                                      :env '(:discarded)
                                      :code '(:discarded)
                                      :dump '((:rest) :env :code))]
        (doinstruct :rtn registers)
        => (map-similar-to
            (secd-registers :stack '(:kept :rest)
                            :env :env
                            :code :code
                            :dump nil))))

(fact "about :dum instruction"
      (doinstruct :dum (secd-registers)) => (env-is '(nil))
      (doinstruct :dum (secd-registers :env '(1 2 3))) => (env-is '(nil 1 2 3)))

(fact "about do-secd* termination"
      (do-secd* []) => nil?
      (do-secd* [:nil]) => (fstack-is nil?))

(fact "about do-secd* math"
      (do-secd* [:ldc 1 :ldc 2 :add]) => (fstack-is 3)
      (do-secd* [:ldc 1 :ldc 2 :sub]) => (fstack-is 1)
      (do-secd* [:ldc 5 :ldc 5 :mty]) => (fstack-is 25)
      (do-secd* [:ldc 5 :ldc 5 :div]) => (fstack-is 1))

(fact "about do-secd* consing"
      (do-secd* [:nil :ldc 1 :cons :ldc 2 :cons]) => (fstack-is '(2 1)))

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
