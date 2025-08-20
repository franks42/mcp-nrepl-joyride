(ns nrepl-mcp-server.state.messages
  "Message queue state management for async nREPL operations"
  (:require [nrepl-mcp-server.utils.uuid-v7 :as uuid]
            [nrepl-mcp-server.state.results :as results]
            [nrepl-mcp-server.state.connection :as conn-state]))

;; =============================================================================
;; Per-Connection Message Queue State Atom
;; =============================================================================

(def connection-message-queues
  "Per-connection message send queues for async operations.
   
   Structure:
   {connection-id {:send-queue PersistentQueue ; FIFO queue of messages waiting to be sent
                   :pending-messages {}         ; Map of message-id -> detailed message record
                   :message-counter 0}          ; Counter for debugging/metrics
    ...}"
  (atom {}))

;; =============================================================================
;; Connection Adapter - moved from watchers for cleaner separation
;; =============================================================================

(defn adapt-connection-for-messaging
  "Convert connection state format to messaging client format.
   Wraps socket InputStream with PushbackInputStream for bencode compatibility."
  [connection]
  (when connection
    (let [socket (:socket connection)]
      {:out (.getOutputStream socket)
       :in (java.io.PushbackInputStream. (.getInputStream socket))
       :id (:connection-id connection)
       :socket socket})))

;; =============================================================================
;; Connection Queue Management
;; =============================================================================

(defn ensure-connection-queue!
  "Ensure a message queue exists for the given connection-id.
   Creates an empty queue structure if it doesn't exist."
  [connection-id]
  (swap! connection-message-queues
         (fn [queues]
           (if (contains? queues connection-id)
             queues
             (assoc queues connection-id
                    {:send-queue clojure.lang.PersistentQueue/EMPTY
                     :pending-messages {}
                     :message-counter 0}))))
  connection-id)

(defn cleanup-connection-queue!
  "Remove message queue for a connection that has been closed."
  [connection-id]
  (swap! connection-message-queues dissoc connection-id)
  (binding [*out* *err*]
    (println "[Messages] Cleaned up message queue for connection:" connection-id)))

;; =============================================================================
;; Queue Operations - Phase 4 Multi-Connection Implementation
;; =============================================================================

(defn enqueue-message!
  "Add a READY-TO-SEND message to the send queue with pre-formatted connection.
   This makes watchers simple - they just dequeue and send without any logic.
   
   Args:
     connection-id - Connection ID to enqueue message for
     message - nREPL message map to send
   
   Returns:
     message-id - UUID v7 string for tracking this message
     nil - if connection not found or connection formatting fails"
  [connection-id message]
  ;; Get and validate specific connection by connection-id
  (if-let [raw-connection (conn-state/get-connection-by-id connection-id)]
    (if-let [formatted-connection (adapt-connection-for-messaging raw-connection)]
      (do
        ;; Ensure connection queue exists
        (ensure-connection-queue! connection-id)
        (let [message-id (uuid/uuid-v7-with-tag :tag "msg")
              timestamp (System/currentTimeMillis)
              ;; Create READY-TO-SEND entry - everything formatted for watcher
              ready-to-send {:message-id message-id
                             :connection-id connection-id  ; ← Store connection-id
                             :connection formatted-connection  ; ← Pre-formatted connection 
                             :message (assoc message :id message-id)  ; ← Original message with ID
                             :timestamp timestamp
                             :attempts 0
                             :status :pending}]

          ;; Create result promise first
          (results/create-result-promise! connection-id message-id)

          ;; Add to connection-specific queue and pending messages
          (swap! connection-message-queues
                 (fn [queues]
                   (update-in queues [connection-id]
                              (fn [queue-state]
                                (-> queue-state
                                    ;; Add to FIFO send queue using PersistentQueue conj
                                    (update :send-queue conj ready-to-send)
                                    ;; Create pending entry in messages map with status
                                    (assoc-in [:pending-messages message-id]
                                              (assoc ready-to-send
                                                     :created-at timestamp
                                                     :status :pending))
                                    ;; Increment counter for metrics
                                    (update :message-counter inc))))))

          ;; Log for debugging
          (binding [*out* *err*]
            (println "[Queue] Enqueued READY-TO-SEND message:" message-id "for connection:" connection-id "status: :pending"))
          message-id))

      ;; Connection adaptation failed
      (do
        (binding [*out* *err*]
          (println "[Queue] Error: Failed to adapt connection" connection-id "for message"))
        nil))

    ;; Connection not found
    (do
      (binding [*out* *err*]
        (println "[Queue] Error: Connection not found:" connection-id))
      nil)))

(defn dequeue-message!
  "Remove and return the next message from the connection-specific send queue (FIFO).
   Returns nil if queue is empty."
  [connection-id]
  (let [result (atom nil)]
    (swap! connection-message-queues
           (fn [queues]
             (if-let [queue-state (get queues connection-id)]
               (if-let [queue-entry (peek (:send-queue queue-state))]  ; peek gets first from PersistentQueue
                 (do
                   (reset! result queue-entry)
                   (update-in queues [connection-id :send-queue] pop))  ; pop removes first from PersistentQueue
                 queues)
               queues)))
    @result))

(defn find-message-connection
  "Find which connection a message-id belongs to.
   Returns connection-id or nil if message not found."
  [message-id]
  (let [queues @connection-message-queues]
    (->> queues
         (filter (fn [[_conn-id queue-state]]
                   (contains? (:pending-messages queue-state) message-id)))
         first
         first)))

(defn get-pending-message
  "Get a pending message by ID without removing it."
  [message-id]
  (when-let [connection-id (find-message-connection message-id)]
    (get-in @connection-message-queues [connection-id :pending-messages message-id])))

(defn remove-pending-message!
  "Remove a pending message from the tracking map."
  [message-id]
  (when-let [connection-id (find-message-connection message-id)]
    (swap! connection-message-queues update-in [connection-id :pending-messages] dissoc message-id)))

(defn update-message-status!
  "Update the status of a pending message.
   Status can be :pending, :sending, :sent, :partial, :done, :failed, :timeout, :error"
  [message-id new-status & {:keys [error bencode-sent sent-at completed-at accumulated-responses]}]
  (when-let [connection-id (find-message-connection message-id)]
    (swap! connection-message-queues
           (fn [queues]
             (if-let [_msg (get-in queues [connection-id :pending-messages message-id])]
               (update-in queues [connection-id :pending-messages message-id]
                          (fn [entry]
                            (cond-> (assoc entry :status new-status)
                              error (assoc :error error)
                              bencode-sent (assoc :bencode-sent bencode-sent)
                              sent-at (assoc :sent-at sent-at)
                              completed-at (assoc :completed-at completed-at)
                              accumulated-responses (assoc :accumulated-responses accumulated-responses))))
               queues)))
    ;; Log status change
    (binding [*out* *err*]
      (println "[Queue] Message" message-id "status:" new-status))))

;; =============================================================================
;; Watcher Management
;; =============================================================================

(defn add-message-watcher
  "Add a watcher to the connection message queues atom"
  [key watch-fn]
  (add-watch connection-message-queues key watch-fn))

(defn remove-message-watcher
  "Remove a watcher from the connection message queues atom"
  [key]
  (remove-watch connection-message-queues key))

;; =============================================================================
;; Cleanup Support
;; =============================================================================

(defn clear-all-messages!
  "Clear all message queues and pending messages - used during complete system cleanup"
  []
  (reset! connection-message-queues {})
  (binding [*out* *err*]
    (println "[Messages] Cleared all connection message queues and pending messages")))

(defn clear-connection-messages!
  "Clear message queues for a specific connection"
  [connection-id]
  (swap! connection-message-queues
         (fn [queues]
           (if (contains? queues connection-id)
             (assoc-in queues [connection-id]
                       {:send-queue clojure.lang.PersistentQueue/EMPTY
                        :pending-messages {}
                        :message-counter 0})
             queues)))
  (binding [*out* *err*]
    (println "[Messages] Cleared message queue for connection:" connection-id)))

;; =============================================================================
;; Debug Support
;; =============================================================================

(defn get-message-summary
  "Get a summary of message queue state for debugging"
  []
  (let [queues @connection-message-queues]
    {:connection-count (count queues)
     :connections (into {} (map (fn [[conn-id queue-state]]
                                  [conn-id {:queue-length (count (:send-queue queue-state))
                                            :pending-count (count (:pending-messages queue-state))
                                            :message-counter (:message-counter queue-state)}])
                                queues))
     :total-pending (reduce + (map (fn [[_conn-id queue-state]]
                                     (count (:pending-messages queue-state)))
                                   queues))
     :total-queued (reduce + (map (fn [[_conn-id queue-state]]
                                    (count (:send-queue queue-state)))
                                  queues))
     :watchers (keys (.getWatches connection-message-queues))}))

;; =============================================================================
;; Backward Compatibility Support for Single Connection (Phase 4)
;; =============================================================================

(defn enqueue-message-single-connection!
  "Backward compatible function that enqueues message for active connection.
   Used during Phase 4 transition to maintain compatibility with existing callers."
  [message]
  (if-let [active-connection (conn-state/get-active-connection)]
    (enqueue-message! (:connection-id active-connection) message)
    (do
      (binding [*out* *err*]
        (println "[Queue] Error: No active connection available for message"))
      nil)))