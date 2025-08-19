(require '[nrepl-mcp-server.state.messages :as msg-state])

;; Test if we can enqueue a message
(let [test-message {:op "eval" :code "(+ 1 2 3)"}
      result (msg-state/enqueue-message! test-message)]
  (println "Enqueue result:" result)
  (if result
    (do
      (println "Message successfully enqueued with ID:" result)
      ;; Check queue state
      (let [state @msg-state/message-queue]
        (println "Queue length:" (count (:send-queue state)))
        (println "Pending messages:" (count (:pending-messages state)))
        (println "Watchers active:" (keys (.getWatches msg-state/message-queue)))))
    (println "FAILED to enqueue message!")))