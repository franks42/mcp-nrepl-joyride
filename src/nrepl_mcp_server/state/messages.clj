(ns nrepl-mcp-server.state.messages
  "Message queue state management for async nREPL operations"
  (:require [nrepl-mcp-server.utils.uuid-v7 :as uuid]
            [nrepl-mcp-server.state.results :as results]
            [nrepl-mcp-server.state.connection :as conn-state]))

;; =============================================================================
;; Message Queue State Atom
;; =============================================================================

(def message-queue
  "Message send queue for async operations.
   
   Structure:
   {:send-queue PersistentQueue ; FIFO queue of messages waiting to be sent
    :pending-messages {}         ; Map of message-id -> detailed message record
    :message-counter 0}          ; Counter for debugging/metrics"
  (atom {:send-queue clojure.lang.PersistentQueue/EMPTY
         :pending-messages {}
         :message-counter 0}))

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
;; Queue Operations - Phase 2b.1 Implementation (Enhanced for Dumb Watchers)
;; =============================================================================

(defn enqueue-message!
  "Add a READY-TO-SEND message to the send queue with pre-formatted connection.
   This makes watchers simple - they just dequeue and send without any logic.
   
   Args:
     message - nREPL message map to send
   
   Returns:
     message-id - UUID v7 string for tracking this message
     nil - if no connection available or connection formatting fails"
  [message]
  ;; Get and validate connection upfront when queuing (not when sending)
  (if-let [raw-connection (conn-state/get-active-connection)]
    (if-let [formatted-connection (adapt-connection-for-messaging raw-connection)]
      (let [message-id (uuid/uuid-v7-with-tag :tag "msg")
            timestamp (System/currentTimeMillis)
            ;; Create READY-TO-SEND entry - everything formatted for watcher
            ready-to-send {:message-id message-id
                           :connection formatted-connection  ; ← Pre-formatted connection 
                           :message (assoc message :id message-id)  ; ← Original message with ID
                           :timestamp timestamp
                           :attempts 0
                           :status :pending}]

        ;; Create result promise first
        (results/create-result-promise! message-id)

        ;; Add to queue and pending messages
        (swap! message-queue
               (fn [state]
                 (-> state
                     ;; Add to FIFO send queue using PersistentQueue conj
                     (update :send-queue conj ready-to-send)
                     ;; Create pending entry in messages map with status
                     (assoc-in [:pending-messages message-id]
                               (assoc ready-to-send
                                      :created-at timestamp
                                      :status :pending))
                     ;; Increment counter for metrics
                     (update :message-counter inc))))

        ;; Log for debugging
        (binding [*out* *err*]
          (println "[Queue] Enqueued READY-TO-SEND message:" message-id "status: :pending"))
        message-id)

      ;; Connection adaptation failed
      (do
        (binding [*out* *err*]
          (println "[Queue] Error: Failed to adapt connection for message"))
        nil))

    ;; No connection available
    (do
      (binding [*out* *err*]
        (println "[Queue] Error: No connection available for message"))
      nil)))

(defn dequeue-message!
  "Remove and return the next message from the send queue (FIFO).
   Returns nil if queue is empty."
  []
  (let [result (atom nil)]
    (swap! message-queue
           (fn [state]
             (if-let [queue-entry (peek (:send-queue state))]  ; peek gets first from PersistentQueue
               (do
                 (reset! result queue-entry)
                 (update state :send-queue pop))  ; pop removes first from PersistentQueue
               state)))
    @result))

(defn get-pending-message
  "Get a pending message by ID without removing it."
  [message-id]
  (get-in @message-queue [:pending-messages message-id]))

(defn remove-pending-message!
  "Remove a pending message from the tracking map."
  [message-id]
  (swap! message-queue update :pending-messages dissoc message-id))

(defn update-message-status!
  "Update the status of a pending message.
   Status can be :pending, :sending, :sent, :partial, :done, :failed, :timeout, :error"
  [message-id new-status & {:keys [error bencode-sent sent-at completed-at accumulated-responses]}]
  (swap! message-queue
         (fn [state]
           (if-let [_msg (get-in state [:pending-messages message-id])]
             (update-in state [:pending-messages message-id]
                        (fn [entry]
                          (cond-> (assoc entry :status new-status)
                            error (assoc :error error)
                            bencode-sent (assoc :bencode-sent bencode-sent)
                            sent-at (assoc :sent-at sent-at)
                            completed-at (assoc :completed-at completed-at)
                            accumulated-responses (assoc :accumulated-responses accumulated-responses))))
             state)))
  ;; Log status change
  (binding [*out* *err*]
    (println "[Queue] Message" message-id "status:" new-status)))

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