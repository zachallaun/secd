(ns secd.machine-test
  (:use midje.sweet
        secd.test-helpers
        secd.magic
        secd.machine))

(def stack-is (structure-checker (comp :stack)))
(def fstack-is (structure-checker (comp first :stack)))
(def env-is (structure-checker (comp :env)))
(def code-is (structure-checker (comp :code)))
(def dump-is (structure-checker (comp :dump)))

(fact "about SECD register defaults"
      (secd-registers) => (map-similar-to {:stack (atom ()) :env (atom ())
                                           :code (atom ()) :dump (atom ())})
      (secd-registers :stack '(:a :b :c))
      => (map-similar-to {:stack (atom '(:a :b :c)) :env (atom ())
                          :code (atom ()) :dump (atom ())}))

(fact "about definstruct return value"
      (definstruct BAR {} {})
      (run BAR (secd-registers)) => truthy)

(fact "about NIL instruction"
      (run NIL (secd-registers)) => (fstack-is nil))

(fact "about LDC instruction"
      (let [registers (secd-registers :code '(:a))]
        (run LDC registers) => (fstack-is :a)))

(fact "about locate"
      (locate [[:v1]] 0 0) => :v1
      (locate [(atom [:v1])] 0 0) => :v1)

(fact "about LD instruction"
      (run LD (secd-registers :code '([0 0]) :env [[:v1]]))
      => (andfn (fstack-is :v1) (env-is [[:v1]]))

      (run LD (secd-registers :code '([0 0]) :env '[(:v1)]))
      => (andfn (fstack-is :v1) (env-is '[(:v1)]))

      (run LD (secd-registers :code '([1 1]) :env '[() (:v1 :v2)]))
      => (andfn (fstack-is :v2) (env-is '[() (:v1 :v2)])))

(fact "about unary built-ins"
      (run ATOM (secd-registers :stack '(:a)))       => (fstack-is true)
      (run ATOM (secd-registers :stack '([])))       => (fstack-is false)
      (run NULL (secd-registers :stack '(nil)))      => (fstack-is true)
      (run NULL (secd-registers :stack '([])))       => (fstack-is true)
      (run NULL (secd-registers :stack '(())))       => (fstack-is true)
      (run NULL (secd-registers :stack '([1])))      => (fstack-is false)
      (run NULL (secd-registers :stack '(:not-nil))) => (fstack-is false)
      (run CAR (secd-registers :stack '((1))))       => (fstack-is 1)
      (run CDR (secd-registers :stack '((1 2 3))))   => (fstack-is '(2 3)))

(fact "about binary built-ins"
      (run CONS (secd-registers :stack '(1 (2 3)))) => (fstack-is '(1 2 3))
      (run ADD (secd-registers :stack '(1 1))) => (fstack-is 2)
      (run SUB (secd-registers :stack '(1 1))) => (fstack-is 0)
      (run MTY (secd-registers :stack '(2 2))) => (fstack-is 4)
      (run DIV (secd-registers :stack '(4 2))) => (fstack-is 2)
      (run EQ (secd-registers :stack '(3 2))) => (fstack-is falsey)
      (run EQ (secd-registers :stack '(2 2))) => (fstack-is truthy)
      (run GT (secd-registers :stack '(3 2))) => (fstack-is truthy)
      (run GT (secd-registers :stack '(2 3))) => (fstack-is falsey)
      (run LT (secd-registers :stack '(3 2))) => (fstack-is falsey)
      (run LT (secd-registers :stack '(2 3))) => (fstack-is truthy)
      (run GTE (secd-registers :stack '(3 2))) => (fstack-is truthy)
      (run GTE (secd-registers :stack '(2 3))) => (fstack-is falsey)
      (run GTE (secd-registers :stack '(2 2))) => (fstack-is truthy)
      (run LTE (secd-registers :stack '(3 2))) => (fstack-is falsey)
      (run LTE (secd-registers :stack '(2 3))) => (fstack-is truthy)
      (run LTE (secd-registers :stack '(2 2))) => (fstack-is truthy))

(fact "about if-then-else instructions"
      (let [truthy-sel (secd-registers :stack '(:truthy)
                                       :code '(:for-true :for-false :rest))
            falsey-sel (secd-registers :stack '(false)
                                       :code '(:for-true :for-false :rest))]

        (run SEL truthy-sel) => (andfn (code-is :for-true)
                                               (dump-is '((:rest))))

        (run SEL falsey-sel) => (andfn (code-is :for-false)
                                               (dump-is '((:rest)))))

      (run JOIN (secd-registers :dump '((:dumped)))) => (code-is '(:dumped)))

(fact "about TEST instructions"
      (run TEST (secd-registers :stack '(:truthy)
                                        :code '(:true :false)))
      => (andfn (code-is :true)
                (dump-is ()))

      (run TEST (secd-registers :stack '(false)
                                       :code '(:true :false)))
      => (andfn (code-is '(:false))
                (dump-is ())))

(fact "about AA add-arguments instruction"
      (run AA (secd-registers :stack '(:args)))
      => (andfn (stack-is ())
                (env-is '(:args))))

(fact "about LDF instruction"
      (run LDF (secd-registers :code '(:fn-instructions) :env '(:context)))
      => (map-similar-to
          (secd-registers :stack '([:fn-instructions (:context)])
                          :env '(:context))))

(fact "about AP instruction"
      (let [registers (secd-registers :stack '([:fn-instructions (:context)]
                                                 :args :rest))]
        (run AP registers)
        => (map-similar-to
            (secd-registers :env '(:args :context)
                            :code :fn-instructions
                            :dump '((:rest) () ())))))

(fact "about DAP direct apply instruction"
      (let [registers (secd-registers :stack '([:fn-instructions (:context)]
                                                 :args :rest))]
        (run DAP registers)
        => (andfn (stack-is ())
                  (env-is '(:args :context))
                  (code-is :fn-instructions)
                  (dump-is ()))))

(fact "about RTN instruction"
      (let [registers (secd-registers :stack '(:kept :discarded)
                                      :env '(:discarded)
                                      :code '(:discarded)
                                      :dump '((:rest) :env :code))]
        (run RTN registers)
        => (map-similar-to
            (secd-registers :stack '(:kept :rest)
                            :env :env
                            :code :code
                            :dump nil))))

(fact "about DUM instruction"
      (run DUM (secd-registers)) => (env-is (fn [[a]]
                                                      (instance? clojure.lang.Atom a)))
      (run DUM (secd-registers :env '(1 2 3)))
      => (env-is (fn [[a & more]]
                   (and (instance? clojure.lang.Atom a)
                        (= '(1 2 3) more)))))

;; midje wants to print the actual value of the registers, and because
;; they're cyclic, you have to dynamically bind *print-level*
(binding [*print-level* 5]
  (fact "about RAP instruction"
        (let [dum (atom '())
              registers
              (secd-registers :stack [[:fn [dum]] [:arg] :rest]
                              :env [dum])]
          (run RAP registers)
          => (andfn (stack-is ())
                    (env-is [dum])
                    (code-is :fn)
                    (dump-is ['(:rest) [dum] ()])))))

(fact "about WRITEC instruction"
      (with-out-str
        (run WRITEC (secd-registers :stack [65])))
      => "A"

      (run WRITEC (secd-registers :stack [65])) => (secd-registers))

(fact "about do-secd* optional n-instructions argument"
      (do-secd* 1 [NIL NIL NIL]) => (andfn (stack-is [nil])
                                              (code-is [NIL NIL]))
      (do-secd* 2 [NIL NIL NIL]) => (andfn (stack-is [nil nil])
                                              (code-is [NIL]))
      (do-secd* 3 [NIL NIL NIL]) => (andfn (stack-is [nil nil nil])
                                              (code-is [])))

(fact "about do-secd* termination"
      (do-secd* []) => nil?
      (do-secd* [NIL]) => (fstack-is nil?))

(fact "about do-secd* math"
      (do-secd* [LDC 1 LDC 2 ADD]) => (fstack-is 3)
      (do-secd* [LDC 1 LDC 2 SUB]) => (fstack-is 1)
      (do-secd* [LDC 5 LDC 5 MTY]) => (fstack-is 25)
      (do-secd* [LDC 5 LDC 5 DIV]) => (fstack-is 1))

(fact "about do-secd* consing"
      (do-secd* [NIL LDC 1 CONS LDC 2 CONS]) => (fstack-is '(2 1)))

(fact "about do-secd* if-then-else"
      ;; 5 + (if (atom :an-atom) then 1 else 2)
      (do-secd* [LDC 5 LDC :an-atom ATOM
                 SEL
                 [LDC 1 JOIN]
                 [LDC 2 JOIN]
                 ADD])
      => (fstack-is 6)

      ;; (5 (if (atom []) then + else -) 5) + 10
      (do-secd* [LDC 5 LDC 5 LDC [] ATOM
                 SEL
                 [ADD JOIN]
                 [SUB JOIN]
                 LDC 10
                 ADD])
      => (fstack-is 10))

(fact "about do-secd* fn application"
      ;; let f(x,y)=x+y in f(2*3, 6-4)
      (do-secd* [NIL
                 LDC 4 LDC 6 SUB CONS
                 LDC 3 LDC 2 MTY CONS
                 LDF [LD [0 1]
                      LD [0 0]
                      ADD
                      RTN]
                 AP])
      => (fstack-is 8))

(fact "about do-secd* recursive fn application"
      ;; letrec f(x) = if x<1 then 0 else f(x-1) in f(3)
      (do-secd* [DUM NIL
                 LDF [LDC 1 LD [0 0] LT
                      SEL
                      [LDC 0 JOIN]
                      [NIL
                       LDC 1 LD [0 0] SUB
                       CONS
                       LD [1 0] AP JOIN]
                      RTN]
                 CONS
                 LDF [NIL LDC 3 CONS LD [0 0] AP RTN]
                 RAP]) => (fstack-is 0))
