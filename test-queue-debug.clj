;; Debug test for message queue

(require '[nrepl-mcp-server.state.messages :as msg])
(require '[nrepl-mcp-server.state.connection :as conn])

;; First check connection
(println "=== Connection Check ===")
(let [active-conn (conn/get-active-connection)]
  (println "Connected?" (conn/connected?))
  (println "Connection ID:" (:connection-id active-conn))
  (println "Socket open?" (when-let [s (:socket active-conn)] (not (.isClosed s)))))

;; Now check watchers
(println "\n=== Watcher Check ===")
(println "Message queue watchers:" (keys (.getWatches msg/message-queue)))

;; Check queue state
(println "\n=== Queue State ===")
(let [state @msg/message-queue]
  (println "Queue length:" (count (:send-queue state)))
  (println "Pending messages:" (count (:pending-messages state)))
  (println "Message counter:" (:message-counter state)))

"Debug info printed"