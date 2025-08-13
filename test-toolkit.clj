;; Simple test toolkit for debug-load-file

(def greeting "Hello from loaded file!")

(defn add-numbers [a b]
  (+ a b))

(defn test-function []
  {:message greeting
   :result (add-numbers 10 20)
   :timestamp (System/currentTimeMillis)})

;; This will be the return value
(test-function)