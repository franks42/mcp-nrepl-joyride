(ns mcp-nrepl-proxy.state
  "Message queue lifecycle and state management for nREPL async operations"
  (:require [mcp-nrepl-proxy.uuid-v7 :as uuid]))

;; Message Queue Lifecycle Management
(defonce ^:private message-queues
  (atom {:pending-messages {}     ; Map of connection-id -> #{message-ids}
         :message-records {}      ; Map of message-id -> message record
         :failure-records []}))   ; Vector of failure records (temporal ordered)

(defn- track-pending-message
  "Add a message to the pending queue for a connection."
  [connection-id message-id message]
  (let [record {:message-id message-id
                :connection-id connection-id
                :message message
                :status :pending
                :created-at (System/currentTimeMillis)
                :last-updated (System/currentTimeMillis)}]
    (swap! message-queues
           (fn [queues]
             (-> queues
                 (update-in [:pending-messages connection-id]
                            (fn [pending-set]
                              (conj (or pending-set #{}) message-id)))
                 (assoc-in [:message-records message-id] record))))
    record))

(defn- update-message-status
  "Update message status and handle state transitions.
  
  Args:
    message-id - ID of message to update
    new-status - New status (:sending, :sent, :completed, :failed, :expired)
    error-info - Optional map with error details for failed messages
  
  State transitions:
    :pending -> :sending -> :sent -> :completed
    :pending/:sending/:sent -> :failed (with error-info)
    :pending/:sending/:sent -> :expired (timeout)
  
  Returns:
    Updated message record or nil if message not found"
  [message-id new-status & {:keys [error-info]}]
  (let [current-time (System/currentTimeMillis)
        result (atom nil)]
    (swap! message-queues
           (fn [queues]
             (if-let [existing-record (get-in queues [:message-records message-id])]
               (let [updated-record (cond-> (assoc existing-record
                                                   :status new-status
                                                   :last-updated current-time)
                                      error-info (assoc :error error-info))
                     connection-id (:connection-id existing-record)]
                 (reset! result updated-record)
                 ;; Update message record
                 (let [updated-queues (assoc-in queues [:message-records message-id] updated-record)]
                   ;; If message completed/failed, remove from pending
                   (if (#{:completed :failed :expired} new-status)
                     (-> updated-queues
                         (update-in [:pending-messages connection-id] disj message-id)
                         (update :failure-records
                                 (fn [failures]
                                   (if (#{:failed :expired} new-status)
                                     (conj failures {:message-id message-id
                                                     :connection-id connection-id
                                                     :status new-status
                                                     :error error-info
                                                     :failed-at current-time})
                                     failures))))
                     updated-queues)))
               ;; Message not found
               (do
                 (reset! result nil)
                 queues))))
    @result))

(defn- mark-connection-messages-failed
  "Mark all pending messages for a connection as failed.
  
  Args:
    connection-id - Connection ID to mark messages failed for
    error-type - Error type (:connection-closed, :connection-lost, :connection-reset, :timeout)
    error-message - Human-readable error message
  
  Returns:
    Number of messages marked as failed"
  [connection-id error-type error-message]
  (let [current-time (System/currentTimeMillis)
        marked-count (atom 0)]
    (swap! message-queues
           (fn [queues]
             (if-let [pending-message-ids (get-in queues [:pending-messages connection-id])]
               (let [error-info {:type error-type
                                 :message error-message
                                 :connection-id connection-id}]
                 (reset! marked-count (count pending-message-ids))
                 ;; Update all pending messages to failed status
                 (reduce
                  (fn [updated-queues message-id]
                    (let [message-record (get-in updated-queues [:message-records message-id])
                          failed-record (assoc message-record
                                               :status :failed
                                               :last-updated current-time
                                               :error error-info)]
                      (-> updated-queues
                          (assoc-in [:message-records message-id] failed-record)
                          (update :failure-records conj {:message-id message-id
                                                         :connection-id connection-id
                                                         :status :failed
                                                         :error error-info
                                                         :failed-at current-time}))))
                  (update queues :pending-messages dissoc connection-id)
                  pending-message-ids))
               ;; No pending messages for this connection
               (do
                 (reset! marked-count 0)
                 queues))))
    @marked-count))

(defn get-message-status
  "Get the status of a message by message-id.
  
  Returns:
    {:status :pending/:sending/:sent/:completed/:failed/:expired
     :message-id message-id
     :created-at timestamp
     :last-updated timestamp  
     :result response-data (if completed)
     :error-info error-details (if failed/expired)}
  
  Returns nil if message-id not found."
  [message-id]
  (when-let [record (get-in @message-queues [:message-records message-id])]
    record))

(defn queue-message-async
  "Queue an nREPL message for async processing.
  
  Args:
    connection - nREPL connection  
    message - nREPL message map
    send-message-async-fn - Function to call for sending messages asynchronously
    timeout-ms - Optional timeout in milliseconds (default: 30000)
  
  Returns:
    {:message-id id :status :pending :timeout-ms timeout-ms}
  
  The message will be processed in the background using send-message-async.
  Use get-message-status to check completion and get results."
  [connection message send-message-async-fn & {:keys [timeout-ms] :or {timeout-ms 30000}}]
  (let [message-id (uuid/uuid-v7-with-tag :tag "msg")
        msg-with-id (assoc message :id message-id)]

    ;; Track the pending message
    (when (:id connection)
      (track-pending-message (:id connection) message-id message))

    ;; Start async processing in background
    (future
      (try
        (let [result (send-message-async-fn connection msg-with-id timeout-ms)]
          ;; Store the result in the message record and update status
          (case (:status result)
            :success
            (do
              ;; Update message status to completed and store result
              (update-message-status message-id :completed)
              (swap! message-queues assoc-in [:message-records message-id :result] (:response result)))

            (:timeout :error)
            (do
              ;; Update message status to appropriate failure state
              (update-message-status message-id
                                     (case (:status result)
                                       :timeout :expired
                                       :error :failed)
                                     :error-info (select-keys result [:status :error :timeout-ms :responses]))
              (swap! message-queues assoc-in [:message-records message-id :error-info]
                     (select-keys result [:status :error :timeout-ms :responses])))))
        (catch Exception e
          ;; Handle unexpected errors
          (update-message-status message-id :failed
                                 :error-info {:type :system-error
                                              :error (.getMessage e)
                                              :message "Unexpected error during async processing"}))))

    ;; Return immediately with message-id
    {:message-id message-id
     :status :pending
     :timeout-ms timeout-ms}))

(defn fetch-result
  "Fetch the result of an async message by message-id.
  
  Args:
    message-id - The message ID returned by queue-message-async
  
  Returns:
    {:status :pending} - Still processing
    {:status :completed :result response-data} - Successfully completed
    {:status :failed/:expired :error-info {...}} - Failed or timed out
    {:status :not-found} - Message ID not found
  
  For completed messages, :result contains the merged nREPL response.
  For failed/expired messages, :error-info contains error details."
  [message-id]
  (if-let [record (get-message-status message-id)]
    (case (:status record)
      :completed
      {:status :completed
       :result (:result record)
       :message-id message-id}

      (:failed :expired)
      {:status (:status record)
       :error-info (:error-info record)
       :message-id message-id}

      ;; Still processing (:pending, :sending, :sent)
      {:status :pending
       :message-id message-id})

    {:status :not-found
     :message-id message-id}))

(defn get-message-queues-atom
  "Get access to the message queues atom for external access"
  []
  message-queues)

(defn expose-internal-functions
  "Expose internal functions for delegation from nrepl-client"
  []
  {:track-pending-message track-pending-message
   :update-message-status update-message-status
   :mark-connection-messages-failed mark-connection-messages-failed})