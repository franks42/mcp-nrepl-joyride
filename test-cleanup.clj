;; Test manual cleanup and status

(results/clear-all-results!)
(println "After manual cleanup:")
(clojure.pprint/pprint (results-queue-status))