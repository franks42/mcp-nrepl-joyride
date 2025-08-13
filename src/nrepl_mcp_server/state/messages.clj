(ns nrepl-mcp_server.state.messages
  "Message queue state management for async nREPL operations")

;; =============================================================================
;; Message Queue State Atom
;; =============================================================================

(def message-queue
  "Message send queue for async operations.
   
   Structure:
   {:send-queue []           ; Queue of messages waiting to be sent
    :pending-messages {}     ; Map of message-id -> message data
    :message-counter 0}      ; Counter for generating message IDs (temp until UUID)"
  (atom {:send-queue []
         :pending-messages {}
         :message-counter 0}))

;; =============================================================================
;; Queue Operations (Placeholder for Phase 2b)
;; =============================================================================

(defn enqueue-message!
  "Add a message to the send queue - Phase 2b implementation"
  [message]
  ;; TODO: Phase 2b implementation
  (throw (ex-info "Not implemented - Phase 2b" {:phase "2b"})))

(defn dequeue-message!
  "Remove and return the next message from the send queue - Phase 2b"
  []
  ;; TODO: Phase 2b implementation
  (throw (ex-info "Not implemented - Phase 2b" {:phase "2b"})))

(defn get-pending-message
  "Get a pending message by ID - Phase 2b"
  [message-id]
  ;; TODO: Phase 2b implementation
  nil)

;; =============================================================================
;; Watcher Management
;; =============================================================================

(defn add-message-watcher
  "Add a watcher to the message queue atom"
  [key watch-fn]
  (add-watch message-queue key watch-fn))

(defn remove-message-watcher
  "Remove a watcher from the message queue atom"
  [key]
  (remove-watch message-queue key))

;; =============================================================================
;; Debug Support
;; =============================================================================

(defn get-message-summary
  "Get a summary of message queue state for debugging"
  []
  {:queue-length (count (:send-queue @message-queue))
   :pending-count (count (:pending-messages @message-queue))
   :watchers (keys (.getWatches message-queue))})