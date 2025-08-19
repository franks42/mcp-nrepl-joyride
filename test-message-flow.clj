;; Test to monitor message dequeue and send behavior

(require '[nrepl-mcp-server.state.messages :as msg])
(require '[nrepl-mcp-server.state.connection :as conn])

(println "=== Pre-Test State ===")
(let [state @msg/message-queue]
  (println "Queue length:" (count (:send-queue state)))
  (println "Pending messages:" (count (:pending-messages state)))
  (println "Message counter:" (:message-counter state)))

(println "\n=== Connection and Watchers ===")
(println "Connected?" (conn/connected?))
(println "Watchers:" (keys (.getWatches msg/message-queue)))

;; Enqueue a test message
(println "\n=== Enqueuing Message ===")
(let [test-message {:op "eval" :code "(+ 10 20 30)"}
      message-id (msg/enqueue-message! test-message)]
  (println "Enqueue result:" message-id)
  
  ;; Wait a moment for watcher to process
  (Thread/sleep 1000)
  
  ;; Check state after potential processing
  (println "\n=== Post-Enqueue State ===")
  (let [state @msg/message-queue]
    (println "Queue length:" (count (:send-queue state)))
    (println "Pending messages:" (count (:pending-messages state)))
    (println "Message counter:" (:message-counter state)))
  
  ;; Show pending message details
  (when message-id
    (println "\n=== Pending Message Details ===")
    (let [pending (msg/get-pending-message message-id)]
      (println "Status:" (:status pending))
      (println "Attempts:" (:attempts pending))
      (println "Timestamp:" (:timestamp pending)))))

"Message flow test completed"