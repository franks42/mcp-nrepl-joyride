(ns nrepl-mcp-server.state.watchers
  "Watchers for async message processing - Phase 2b.2&3 (DUMB WATCHERS + RECEIVE)"
  (:require [nrepl-mcp-server.state.messages :as msg-state]
            [nrepl-mcp-server.state.connection :as conn-state]
            [nrepl-mcp-server.state.results :as results]
            [nrepl-mcp-server.nrepl-client.operations :as nrepl-ops]
            [nrepl-mcp-server.nrepl-client.messaging :as messaging]
            [bencode.core]))

;; =============================================================================
;; Send Queue Watcher - Phase 2b.2
;; =============================================================================

(defn- process-send-queue!
  "DUMB WATCHER: Process one READY-TO-SEND message from queue.
   All formatting and validation was done at enqueue time.
   This just dequeues and sends - no logic, no adaptation."
  []
  (when-let [ready-to-send (msg-state/dequeue-message!)]
    (let [{:keys [message-id connection message]} ready-to-send]
      (try
        ;; Update status to sending
        (msg-state/update-message-status! message-id :sending
                                          :sent-at (System/currentTimeMillis))

        ;; Fire-and-forget send - don't wait for responses
        (binding [*out* *err*]
          (println "[Send-Watcher] Fire-and-forget sending READY message:" message-id))

        ;; Use fire-and-forget send (receive-watcher will handle responses)
        (nrepl-ops/send-message-fire-and-forget connection message)

        ;; Update status to sent (not waiting for response)
        (msg-state/update-message-status! message-id :sent
                                          :sent-at (System/currentTimeMillis))

        (binding [*out* *err*]
          (println "[Send-Watcher] READY message sent (fire-and-forget):" message-id))

        (catch Exception e
          ;; Handle fire-and-forget send errors
          (let [error-msg (str "Failed to fire-and-forget send: "
                               (or (.getMessage e)
                                   (.toString e)
                                   "Unknown error"))]
            (msg-state/update-message-status! message-id :error
                                              :error error-msg)
            (results/deliver-error! message-id error-msg)
            (binding [*out* *err*]
              (println "[Send-Watcher] Fire-and-forget error for message" message-id ":" error-msg)
              (println "[Send-Watcher] Exception details:" e))))))))

(defn- send-queue-watcher
  "Watcher function that processes the send queue when it changes.
   Called whenever message-queue atom changes."
  [_ _ old-state new-state]
  ;; Only process if send queue has grown (new messages added)
  (when (> (count (:send-queue new-state))
           (count (:send-queue old-state)))
    (binding [*out* *err*]
      (println "[Watcher] Send queue changed, processing..."))
    ;; Process all available messages in queue
    (while (> (count (:send-queue @msg-state/message-queue)) 0)
      (process-send-queue!))))

;; =============================================================================
;; Watcher Management
;; =============================================================================

(defn start-send-queue-watcher!
  "Start the send queue watcher to automatically process queued messages"
  []
  ;; Remove any existing watcher first to prevent duplicates
  (msg-state/remove-message-watcher :send-queue-watcher)
  (msg-state/add-message-watcher :send-queue-watcher send-queue-watcher)
  (binding [*out* *err*]
    (println "[Watcher] Send queue watcher started")))

(defn stop-send-queue-watcher!
  "Stop the send queue watcher"
  []
  (msg-state/remove-message-watcher :send-queue-watcher)
  (binding [*out* *err*]
    (println "[Watcher] Send queue watcher stopped")))

;; =============================================================================
;; Receive Watcher - Phase 2b.3 Implementation
;; =============================================================================

;; Forward declaration for recursive reference
(declare stop-receive-watcher!)

(defonce receive-watcher-state
  (atom {:running false
         :thread nil
         :connection nil}))

(defn- process-nrepl-response!
  "Process a single nREPL response and deliver to the appropriate promise.
   Accumulates responses per message-id and merges them when 'done' status received."
  [response]
  (let [message-id (:id response)]
    (binding [*out* *err*]
      (println "[Receive-Watcher] Processing response for message:" message-id))

    (if message-id
      ;; Try to find the pending message
      (if-let [pending-msg (msg-state/get-pending-message message-id)]
        ;; Add this response to the accumulated responses for this message
        (let [current-responses (get pending-msg :accumulated-responses [])
              updated-responses (conj current-responses response)]

          ;; Update the pending message with accumulated responses
          (msg-state/update-message-status! message-id :partial-response
                                            :accumulated-responses updated-responses)

          ;; Check if this is a final response (has "done" status)
          (if (and (:status response)
                   (some #(= "done" %) (:status response)))
            ;; Final response - merge all accumulated responses and deliver
            (let [merged-response (#'messaging/merge-responses updated-responses)]
              (binding [*out* *err*]
                (println "[Receive-Watcher] Merging" (count updated-responses) "responses for:" message-id)
                (println "[Receive-Watcher] Merged response:" merged-response))

              (msg-state/update-message-status! message-id :completed
                                                :completed-at (System/currentTimeMillis))
              (results/deliver-result! message-id {:status :success :response merged-response})
              (msg-state/remove-pending-message! message-id)
              (binding [*out* *err*]
                (println "[Receive-Watcher] Final merged response delivered for:" message-id)))

            ;; Partial response - just log and continue accumulating
            (binding [*out* *err*]
              (println "[Receive-Watcher] Accumulated partial response for:" message-id
                       "(total:" (count updated-responses) "responses)"))))

        ;; No pending message found - orphaned response
        (binding [*out* *err*]
          (println "[Receive-Watcher] Orphaned response (no pending message):" message-id)))

      ;; No message ID in response
      (binding [*out* *err*]
        (println "[Receive-Watcher] Response missing :id field:" response)))))

(defn- receive-loop!
  "Background receive loop that listens for nREPL responses.
   
   Uses surgically extracted bencode reading logic from messaging.clj
   instead of duplicating the conversion logic."
  [connection]
  (binding [*out* *err*]
    (println "[Receive-Watcher] Starting receive loop for connection:" (:id connection)))

  (try
    (let [input-stream (:in connection)]
      (while (:running @receive-watcher-state)
        (try
          ;; USING EXTRACTED LOGIC: Use messaging/convert-bencode-response (private, but available via namespace)
          ;; Read bencode response from nREPL
          (let [raw-response (bencode.core/read-bencode input-stream)
                ;; Use the same conversion logic as the extracted result-processing-async
                converted-response (#'messaging/convert-bencode-response raw-response)]
            (binding [*out* *err*]
              (println "[Receive-Watcher] 📥 Raw response received:" converted-response))

            ;; Process the response
            (process-nrepl-response! converted-response))

          (catch java.io.EOFException _e
            (binding [*out* *err*]
              (println "[Receive-Watcher] EOF - connection closed"))
            (swap! receive-watcher-state assoc :running false))

          (catch Exception e
            (binding [*out* *err*]
              (println "[Receive-Watcher] Error reading response:" (.getMessage e))
              (println "[Receive-Watcher] Exception details:" e))
            ;; Continue the loop unless it's a fatal error
            (Thread/sleep 100)))))

    (catch Exception e
      (binding [*out* *err*]
        (println "[Receive-Watcher] Fatal error in receive loop:" (.getMessage e)))
      (swap! receive-watcher-state assoc :running false))

    (finally
      (binding [*out* *err*]
        (println "[Receive-Watcher] Receive loop terminated")))))

(defn start-receive-watcher!
  "Start the receive watcher for processing nREPL responses.
   Runs in a background thread and listens for incoming responses."
  []
  ;; Always stop any existing watcher first to ensure clean state
  (when (:running @receive-watcher-state)
    (binding [*out* *err*]
      (println "[Receive-Watcher] Stopping existing watcher before starting new one"))
    (stop-receive-watcher!))

  (if-let [raw-connection (conn-state/get-active-connection)]
    (if-let [connection (msg-state/adapt-connection-for-messaging raw-connection)]
      (do
        ;; Start the background receive thread
        (swap! receive-watcher-state assoc :running true :connection connection)
        (let [receive-thread (Thread. #(receive-loop! connection))]
          (.setDaemon receive-thread true)  ; Daemon thread for clean shutdown
          (.setName receive-thread "nREPL-Receive-Watcher")
          (.start receive-thread)
          (swap! receive-watcher-state assoc :thread receive-thread)
          (binding [*out* *err*]
            (println "[Receive-Watcher] Started background receive thread"))))

      (binding [*out* *err*]
        (println "[Receive-Watcher] Error: Failed to adapt connection")))

    (binding [*out* *err*]
      (println "[Receive-Watcher] Error: No active connection"))))

(defn stop-receive-watcher!
  "Stop the receive watcher background thread"
  []
  (when (:running @receive-watcher-state)
    (binding [*out* *err*]
      (println "[Receive-Watcher] Stopping background receive thread"))

    ;; Signal stop
    (swap! receive-watcher-state assoc :running false)

    ;; Wait for thread to finish (with timeout)
    (when-let [thread (:thread @receive-watcher-state)]
      (try
        (.join thread 5000)  ; Wait up to 5 seconds
        (when (.isAlive thread)
          (binding [*out* *err*]
            (println "[Receive-Watcher] Thread didn't stop gracefully, interrupting"))
          (.interrupt thread))
        (catch Exception e
          (binding [*out* *err*]
            (println "[Receive-Watcher] Error stopping thread:" (.getMessage e))))))

    ;; Reset state
    (swap! receive-watcher-state assoc :thread nil :connection nil)
    (binding [*out* *err*]
      (println "[Receive-Watcher] Stopped"))))

;; =============================================================================
;; Combined Watcher Management
;; =============================================================================

(defn start-all-watchers!
  "Start all message processing watchers.
   Note: Only starts send-queue-watcher immediately.
   receive-watcher is started when connection is established."
  []
  (start-send-queue-watcher!)
  ;; receive-watcher is started in nrepl-connection tool after successful connection
  )

(defn stop-all-watchers!
  "Stop all message processing watchers"
  []
  (stop-send-queue-watcher!)
  (stop-receive-watcher!))