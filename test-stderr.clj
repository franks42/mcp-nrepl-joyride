;; Test file that writes to stderr
(println "This goes to stdout")
(binding [*err* (java.io.PrintWriter. System/err)]
  (.println *err* "This should go to stderr")
  (.flush *err*))
:stderr-test-done