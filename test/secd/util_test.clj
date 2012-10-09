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
