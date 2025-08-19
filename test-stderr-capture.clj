;; Test file for stderr capture validation
;; This file produces both stdout and stderr output for testing

(println "stdout: This message goes to standard output")

;; Force stderr output 
(binding [*out* *err*]
  (println "stderr: This message goes to standard error"))

;; Define a function that works
(defn test-function []
  (println "stdout: Function call successful")
  :test-complete)

;; Call the function
(test-function)