(ns secd.util-test
  (:use midje.sweet
        secd.util))

(fact "atom? knows atoms"
      (atom? 0)       => falsey
      (atom? :not)    => falsey
      (atom? {})      => falsey
      (atom? (ref 0)) => falsey
      (atom? (atom 0) => truthy))

(fact "about nnth"
      (nnth '(()) 0 0)     => (throws java.lang.IndexOutOfBoundsException)
      (nnth [[]] 0 0)      => (throws java.lang.IndexOutOfBoundsException)
      (nnth '((1)) 0 0)    => 1
      (nnth [[1]] 0 0)     => 1
      (nnth '(() (1)) 1 0) => 1
      (nnth [[] [1]] 1 0)  => 1)

(fact "about fmap"
      (fmap rest {:k [1 2]}) => {:k [2]})

(fact "about reset-values!"
      (let [m {:k (atom :v)}]
        (reset-values! m {:k :v2})
        @(:k m) => :v2

        (reset-values! m {:k2 :v2})
        (:k2 m) => nil?))
