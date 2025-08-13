#!/usr/bin/env bb

(require '[mcp-nrepl-proxy.nrepl-client :as nrepl]
         '[babashka.process :as p]
         '[babashka.fs :as fs]
         '[clojure.string :as str])

;; Access private vars for testing
(def message-queues #'nrepl/message-queues)
(def track-pending-message #'nrepl/track-pending-message)

(def ^:dynamic *test-results* (atom {}))
(def ^:dynamic *test-count* (atom 0))

(defn test-result [test-name result details]
  (swap! *test-count* inc)
  (swap! *test-results* assoc test-name {:result result :details details})
  (let [status (if result "✅" "❌")
        result-text (if result "SUCCESS" "FAILED")]
    (println (str status " " test-name ": " result-text))
    (when details
      (println (str "   Details: " details)))
    result))

(defn wait-for-port-file 
  "Wait for .nrepl-port file to appear and return port number"
  [timeout-ms]
  (let [start-time (System/currentTimeMillis)]
    (loop []
      (if (>= (- (System/currentTimeMillis) start-time) timeout-ms)
        nil
        (if (fs/exists? ".nrepl-port")
          (let [parse-result (try
                               (let [port-str (str/trim (slurp ".nrepl-port"))]
                                 {:success true :port (Integer/parseInt port-str)})
                               (catch Exception e
                                 {:success false}))]
            (if (:success parse-result)
              (:port parse-result)
              (do
                (Thread/sleep 100)
                (recur))))
          (do
            (Thread/sleep 100)
            (recur)))))))

(defn test-message-queue-lifecycle
  "Test complete message queue lifecycle with successful completion"
  [conn]
  (try
    ;; Get initial queue state
    (let [initial-pending (-> @message-queues :pending-messages)
          initial-records (-> @message-queues :message-records)
          
          ;; Send a message asynchronously
          result (nrepl/send-message-async conn {:op "eval" :code "(+ 2 3)"} 5000)
          
          ;; Check final queue state
          final-pending (-> @message-queues :pending-messages)
          final-records (-> @message-queues :message-records)
          
          ;; Verify message completed successfully
          completed-messages (filter #(= :completed (:status %)) (vals final-records))
          pending-count (count (get final-pending (:id conn) #{}))]
      
      (and (= :success (:status result))
           (> (count completed-messages) (count (filter #(= :completed (:status %)) (vals initial-records))))
           (= 0 pending-count)))
    (catch Exception e
      (println "❌ Message queue lifecycle test error:" (.getMessage e))
      false)))

(defn test-connection-closure-cleanup
  "Test that pending messages are marked as failed when connection closes"
  []
  (try
    ;; Start a temporary nREPL server for this test
    (fs/delete-if-exists ".nrepl-port-temp")
    (let [temp-server (p/process ["bb" "test-nrepl-server" "--port-file" ".nrepl-port-temp"] 
                                {:err :inherit})
          port (wait-for-port-file 3000)]
      
      (if port
        (let [conn (nrepl/connect "localhost" port)
              conn-id (:id conn)
              
              ;; Track a message but don't send it (simulate pending state)
              message-id (nrepl/generate-id)
              _ (track-pending-message conn-id message-id {:op "eval" :code "(+ 1 1)"})
              
              ;; Get initial pending count
              initial-pending (count (get-in @message-queues [:pending-messages conn-id] #{}))
              initial-failures (count (:failure-records @message-queues))
              
              ;; Close connection (should mark pending messages as failed)
              _ (nrepl/close-connection conn)
              
              ;; Check that pending messages were marked as failed
              final-pending (count (get-in @message-queues [:pending-messages conn-id] #{}))
              final-failures (count (:failure-records @message-queues))
              
              ;; Cleanup
              _ (p/destroy temp-server)
              _ (fs/delete-if-exists ".nrepl-port-temp")]
          
          (and (> initial-pending 0)
               (= 0 final-pending)  ; All pending messages cleared
               (> final-failures initial-failures))) ; New failure records added
        false))
    (catch Exception e
      (println "❌ Connection closure cleanup test error:" (.getMessage e))
      false)))

(defn test-timeout-handling
  "Test that messages are properly marked as expired on timeout"
  [conn]
  (try
    (let [initial-expired (count (filter #(= :expired (:status %)) 
                                        (vals (:message-records @message-queues))))
          
          ;; Send a message that will timeout (very short timeout)
          result (nrepl/send-message-async conn {:op "eval" :code "(Thread/sleep 2000)"} 100)
          
          final-expired (count (filter #(= :expired (:status %)) 
                                      (vals (:message-records @message-queues))))]
      
      (and (= :timeout (:status result))
           (> final-expired initial-expired)))
    (catch Exception e
      (println "❌ Timeout handling test error:" (.getMessage e))
      false)))

(defn test-error-recovery
  "Test error handling and recovery scenarios"
  [conn]
  (try
    (let [initial-failed (count (filter #(= :failed (:status %)) 
                                       (vals (:message-records @message-queues))))
          
          ;; Send an invalid operation (should cause error)
          result (nrepl/send-message-async conn {:op "invalid-operation"} 3000)
          
          final-failed (count (filter #(= :failed (:status %)) 
                                     (vals (:message-records @message-queues))))]
      
      ;; The message should either succeed (server handles invalid ops) or fail gracefully
      ;; Either way, the system should remain stable
      (and (not (nil? result))
           (contains? #{:success :error :failed} (:status result))))
    (catch Exception e
      (println "❌ Error recovery test error:" (.getMessage e))
      false)))

(defn test-concurrent-message-handling
  "Test handling multiple concurrent messages"
  [conn]
  (try
    (let [initial-completed (count (filter #(= :completed (:status %)) 
                                          (vals (:message-records @message-queues))))
          
          ;; Send multiple messages concurrently
          futures (mapv #(future (nrepl/send-message-async conn 
                                                          {:op "eval" :code (str "(+ " % " " % ")")} 
                                                          3000))
                       (range 5))
          
          ;; Wait for all to complete
          results (mapv deref futures)
          
          final-completed (count (filter #(= :completed (:status %)) 
                                        (vals (:message-records @message-queues))))]
      
      (and (every? #(= :success (:status %)) results)
           (>= (- final-completed initial-completed) 5)))
    (catch Exception e
      (println "❌ Concurrent message handling test error:" (.getMessage e))
      false)))

(defn test-queue-state-consistency
  "Test that queue state remains consistent across operations"
  [conn]
  (try
    (let [initial-state @message-queues
          
          ;; Perform a series of operations
          _ (nrepl/send-message-async conn {:op "eval" :code "(+ 1 2)"} 3000)
          _ (nrepl/send-message-async conn {:op "describe"} 3000)
          
          final-state @message-queues
          
          ;; Check state consistency
          pending-consistent? (every? set? (vals (:pending-messages final-state)))
          records-consistent? (every? map? (vals (:message-records final-state)))
          failures-consistent? (vector? (:failure-records final-state))]
      
      (and pending-consistent? records-consistent? failures-consistent?))
    (catch Exception e
      (println "❌ Queue state consistency test error:" (.getMessage e))
      false)))

(defn run-connection-lifecycle-tests 
  "Run comprehensive connection lifecycle integration tests"
  []
  (println "🧪 Starting Connection Lifecycle Integration Tests")
  (println "================================================")
  
  ;; Clean up any existing port files
  (fs/delete-if-exists ".nrepl-port")
  
  ;; Start test nREPL server
  (println "🚀 Starting test nREPL server...")
  (let [nrepl-proc (p/process ["bb" "test-nrepl-server"] {:err :inherit})]
    
    (try
      ;; Wait for server to start
      (if-let [port (wait-for-port-file 5000)]
        (do
          (println (str "✅ Test nREPL server started on port: " port))
          
          ;; Create connection for testing
          (let [conn (nrepl/connect "localhost" port)]
            (println "✅ Connected to test nREPL server")
            
            ;; Run all lifecycle tests
            (println "\n🧪 Running lifecycle tests...")
            
            (test-result "message_queue_lifecycle" 
                        (test-message-queue-lifecycle conn)
                        "Tests message states: pending -> sending -> sent -> completed")
            
            (test-result "timeout_handling" 
                        (test-timeout-handling conn)
                        "Tests message timeout and expiration handling")
            
            (test-result "error_recovery" 
                        (test-error-recovery conn)
                        "Tests error handling and system stability")
            
            (test-result "concurrent_message_handling" 
                        (test-concurrent-message-handling conn)
                        "Tests multiple simultaneous message processing")
            
            (test-result "queue_state_consistency" 
                        (test-queue-state-consistency conn)
                        "Tests queue data structure consistency")
            
            ;; Close connection and test cleanup
            (nrepl/close-connection conn)
            
            ;; Test connection closure cleanup separately
            (test-result "connection_closure_cleanup" 
                        (test-connection-closure-cleanup)
                        "Tests pending message cleanup on connection closure")
            
            ;; Print summary
            (println "\n📊 CONNECTION LIFECYCLE TEST RESULTS")
            (println "====================================")
            (let [results @*test-results*
                  total-tests @*test-count*
                  passed-tests (count (filter #(true? (:result (val %))) results))
                  failed-tests (- total-tests passed-tests)
                  success-rate (if (> total-tests 0) 
                                (/ (* passed-tests 100.0) total-tests) 
                                0)]
              
              (println (str "Total Tests: " total-tests))
              (println (str "Passed: " passed-tests " ✅"))
              (println (str "Failed: " failed-tests " ❌"))
              (println (str "Success Rate: " (format "%.1f%%" success-rate)))
              
              (println "\n📋 DETAILED RESULTS:")
              (doseq [[test-name {:keys [result details]}] results]
                (let [status (if result "✅ PASSED" "❌ FAILED")]
                  (println (str "  • " (name test-name) ": " status))
                  (when details
                    (println (str "    " details))))
              
              (if (= failed-tests 0)
                (do
                  (println "\n🎉 ALL CONNECTION LIFECYCLE TESTS PASSED!")
                  (System/exit 0))
                (do
                  (println "\n⚠️  SOME CONNECTION LIFECYCLE TESTS FAILED")
                  (System/exit 1))))))
        
        (do
          (println "❌ Failed to start test nREPL server")
          (System/exit 1)))
      
      (catch Exception e
        (println "❌ Error in lifecycle tests:" (.getMessage e))
        (System/exit 1))
      
      (finally
        ;; Cleanup
        (p/destroy nrepl-proc)
        (fs/delete-if-exists ".nrepl-port")
        (fs/delete-if-exists ".nrepl-port-temp"))))))

;; Run tests if called directly
(when (= *file* (System/getProperty "babashka.file"))
  (run-connection-lifecycle-tests))