(ns secd.linkedlistnyc
  (:use [secd.core])
  (:gen-class))

(defn -main []
  (do
    (do-secd* [:nil :dum
               :ldf [:ld [0 0] :null
                     :sel
                     [:join]
                     [:ld [0 0] :car :writec
                      :nil :ld [0 0] :cdr :cons
                      :ld [1 0] :ap :join]
                     :rtn]
               :cons
               :ldf [:nil
                     :ldc [76 105 110 107 101 100
                           76 105 115 116
                           32
                           78 89 67
                           10]
                     :cons
                     :ld [0 0] :ap :rtn]
               :rap])
    nil))
