;; Complex test file for nrepl-load-file functionality
;; Tests path escaping, complex code structures, and output capture

(println "Loading complex test file...")

;; Define a complex data structure
(def test-data 
  {:name "Test Data"
   :values [1 2 3 4 5]
   :nested {:deep {:very-deep "value with \"quotes\" and \\backslashes\\"}}})

;; Function with complex logic
(defn process-data [data]
  (println "Processing data:" (:name data))
  (let [processed (->> (:values data)
                       (map #(* % 2))
                       (filter even?))]
    (println "Processed values:" processed)
    processed))

;; Multi-line computation with side effects
(let [result (process-data test-data)
      sum (reduce + result)]
  (println "Sum of processed values:" sum)
  (def final-result sum))

;; Test string with complex characters
(def complex-string "This string has \"quotes\", \\backslashes\\, and 'apostrophes'")
(println "Complex string:" complex-string)

;; Return final result
(println "File loading complete. Final result:" final-result)
:load-file-test-complete