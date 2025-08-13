;; Test file to verify stdout/stderr capture

(println "Hello from stdout!")
(binding [*out* *err*] 
  (println "Warning from stderr!"))

(def result (+ 10 20))
(println "The result is:" result)

(prn {:data "test" :numbers [1 2 3]})

result