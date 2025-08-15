(defn fibonacci [n]
  "Calculate fibonacci number with memoization"
  (let [memo (atom {})]
    (letfn [(fib [x]
              (if-let [cached (@memo x)]
                cached
                (let [result (if (<= x 1)
                               x
                               (+ (fib (- x 1)) (fib (- x 2))))]
                  (swap! memo assoc x result)
                  result)))]
      (fib n))))

(defn process-sequence [nums]
  "Process a sequence of numbers with fibonacci"
  (map (fn [n] 
         {:input n 
          :fibonacci (fibonacci n)
          :message (str "fib(" n ") = " (fibonacci n))}) 
       nums))

;; Test the functions
(process-sequence [5 8 10])