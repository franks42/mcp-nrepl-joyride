#!/usr/bin/env bb

(require '[mcp-nrepl-proxy.nrepl-client :as nrepl]
         '[babashka.process :as p]
         '[babashka.fs :as fs]
         '[clojure.string :as str])

;; Simple test runner that works with existing infrastructure
(defn test-queue-lifecycle-basic
  "Basic test of queue lifecycle using send-message-async"
  [conn]
  (try
    (println "  Testing basic queue lifecycle...")
    ;; Send a message and verify it completes
    (let [result (nrepl/send-message-async conn {:op "eval" :code "(+ 2 3)"} 5000)]
      (= :success (:status result)))
    (catch Exception e
      (println "  Error in basic queue test:" (.getMessage e))
      false)))

(defn test-timeout-basic
  "Basic test of timeout handling"
  [conn]
  (try
    (println "  Testing timeout handling...")
    ;; Send a message with very short timeout
    (let [result (nrepl/send-message-async conn {:op "eval" :code "(Thread/sleep 500)"} 50)]
      (= :timeout (:status result)))
    (catch Exception e
      (println "  Error in timeout test:" (.getMessage e))
      false)))

(defn test-error-handling-basic
  "Basic test of error handling"
  [conn]
  (try
    (println "  Testing error handling...")
    ;; Send invalid operation
    (let [result (nrepl/send-message-async conn {:op "invalid-op"} 3000)]
      ;; Should either succeed gracefully or error gracefully
      (not (nil? result)))
    (catch Exception e
      (println "  Error in error handling test:" (.getMessage e))
      false)))

(defn run-simple-lifecycle-tests []
  (println "🧪 Simple Connection Lifecycle Tests")
  (println "=====================================")
  
  ;; Clean any existing port files
  (fs/delete-if-exists ".nrepl-port")
  
  ;; Start test server directly
  (println "🚀 Starting test nREPL server...")
  (let [test-proc (p/process ["./nrepl-test" "start"] {:err :inherit})]
    (Thread/sleep 2000) ;; Give server time to start
    
    (let [results (atom {:passed 0 :failed 0 :tests []})]
      (try
        (if (fs/exists? ".nrepl-port")
          (let [port (Integer/parseInt (str/trim (slurp ".nrepl-port")))
                conn (nrepl/connect "localhost" port)]
            (println (str "✅ Connected to test server on port " port))
            
            ;; Run simple tests
            (doseq [[test-name test-fn] [["queue_lifecycle" test-queue-lifecycle-basic]
                                        ["timeout_handling" test-timeout-basic] 
                                        ["error_handling" test-error-handling-basic]]]
              (let [success? (test-fn conn)]
                (swap! results (fn [r] 
                                 (-> r
                                     (update (if success? :passed :failed) inc)
                                     (update :tests conj {:name test-name :result success?}))))
                (println (str (if success? "✅" "❌") " " test-name ": " 
                            (if success? "PASSED" "FAILED")))))
            
            ;; Close connection
            (nrepl/close-connection conn)
            
            ;; Print summary
            (println "\n📊 RESULTS")
            (println "===========")
            (let [r @results]
              (println (str "Passed: " (:passed r)))
              (println (str "Failed: " (:failed r)))
              (println (str "Success Rate: " 
                           (if (> (+ (:passed r) (:failed r)) 0)
                             (int (* 100 (/ (:passed r) (+ (:passed r) (:failed r)))))
                             0) "%"))
              
              (if (= (:failed r) 0)
                (do (println "\n🎉 ALL TESTS PASSED!") (System/exit 0))
                (do (println "\n⚠️  SOME TESTS FAILED") (System/exit 1)))))
          
          (do
            (println "❌ No .nrepl-port file found")
            (System/exit 1)))
        
        (catch Exception e
          (println "❌ Error in tests:" (.getMessage e))
          (System/exit 1))
        
        (finally
          (p/destroy test-proc))))))

;; Run if called directly
(when (= *file* (System/getProperty "babashka.file"))
  (run-simple-lifecycle-tests))