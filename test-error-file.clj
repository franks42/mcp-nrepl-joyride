;; Test file that contains syntax errors for error handling validation

(println "Starting error test file...")

;; This should work fine
(def good-var 42)

;; This will cause a syntax error (unmatched parenthesis)
(defn broken-function []
  (println "This function has a syntax error"
  ;; Missing closing parenthesis will cause error

(println "This line should not be reached")