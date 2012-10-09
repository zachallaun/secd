(ns secd.util-test
  (:use midje.sweet
        secd.util))

(fact "atom? knows atoms"
      (atom? 0)       => falsey
      (atom? :not)    => falsey
      (atom? {})      => falsey
      (atom? (ref 0)) => falsey
      (atom? (atom 0) => truthy))
