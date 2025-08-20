(ns nrepl-mcp-server.state.results
  "Result queue state management for async nREPL responses"
  (:require [clojure.set]))

;; =============================================================================
;; Per-Connection Result Queue State Atom
;; =============================================================================

(def connection-result-queues
  "Per-connection result queues for async nREPL responses.
   
   Structure:
   {connection-id {:result-promises {}      ; Map of message-id -> promise
                   :completed-results {}    ; Map of message-id -> {:result data}
                   :error-results {}}       ; Map of message-id -> {:error data}
    ...}"
  (atom {}))

;; =============================================================================
;; Connection Result Queue Management
;; =============================================================================

(defn ensure-connection-result-queue!
  "Ensure a result queue exists for the given connection-id.
   Creates an empty queue structure if it doesn't exist."
  [connection-id]
  (swap! connection-result-queues
         (fn [queues]
           (if (contains? queues connection-id)
             queues
             (assoc queues connection-id
                    {:result-promises {}
                     :completed-results {}
                     :error-results {}}))))
  connection-id)

(defn cleanup-connection-result-queue!
  "Remove result queue for a connection that has been closed."
  [connection-id]
  (swap! connection-result-queues dissoc connection-id)
  (binding [*out* *err*]
    (println "[Results] Cleaned up result queue for connection:" connection-id)))

(defn find-message-result-connection
  "Find which connection a message-id belongs to for results.
   Returns connection-id or nil if message not found."
  [message-id]
  (let [queues @connection-result-queues]
    (->> queues
         (filter (fn [[_conn-id queue-state]]
                   (or (contains? (:result-promises queue-state) message-id)
                       (contains? (:completed-results queue-state) message-id)
                       (contains? (:error-results queue-state) message-id))))
         first
         first)))

;; =============================================================================
;; Result Operations - Phase 4 Multi-Connection Implementation
;; =============================================================================

(defn create-result-promise!
  "Create a promise for a message result.
   Returns the promise that will be delivered when result arrives."
  [connection-id message-id]
  (ensure-connection-result-queue! connection-id)
  (let [result-promise (promise)]
    (swap! connection-result-queues
           assoc-in [connection-id :result-promises message-id] result-promise)
    result-promise))

(defn deliver-result!
  "Deliver a successful result for a message."
  [message-id result]
  (when-let [connection-id (find-message-result-connection message-id)]
    (when-let [result-promise (get-in @connection-result-queues [connection-id :result-promises message-id])]
      ;; Deliver to waiting promise
      (deliver result-promise {:status :success :result result})
      ;; Store in completed results and remove promise
      (swap! connection-result-queues
             (fn [queues]
               (-> queues
                   (assoc-in [connection-id :completed-results message-id]
                             {:result result})
                   (update-in [connection-id :result-promises] dissoc message-id))))
      true)))

(defn deliver-error!
  "Deliver an error for a message."
  [message-id error]
  (when-let [connection-id (find-message-result-connection message-id)]
    (when-let [result-promise (get-in @connection-result-queues [connection-id :result-promises message-id])]
      ;; Deliver error to waiting promise
      (deliver result-promise {:status :error :error error})
      ;; Store in error results and remove promise
      (swap! connection-result-queues
             (fn [queues]
               (-> queues
                   (assoc-in [connection-id :error-results message-id]
                             {:error error})
                   (update-in [connection-id :result-promises] dissoc message-id))))
      true)))

(defn get-result
  "Get a result for a message, waiting if necessary.
   Returns {:status :success/:error/:timeout ...}"
  [message-id timeout-ms]
  (if-let [connection-id (find-message-result-connection message-id)]
    (let [queue-state (get @connection-result-queues connection-id)]
      ;; Check if already completed
      (if-let [completed-record (get-in queue-state [:completed-results message-id])]
        {:status :success :result (:result completed-record)}
        ;; Check if errored
        (if-let [error-record (get-in queue-state [:error-results message-id])]
          {:status :error :error (:error error-record)}
          ;; Wait on promise if exists
          (if-let [result-promise (get-in queue-state [:result-promises message-id])]
            (let [result (deref result-promise timeout-ms :timeout)]
              (if (= result :timeout)
                {:status :timeout :message-id message-id :timeout-ms timeout-ms}
                result))
            ;; No record of this message
            {:status :error :error "Unknown message-id"}))))
    ;; Connection not found for this message
    {:status :error :error "Unknown message-id"}))

;; =============================================================================
;; Watcher Management
;; =============================================================================

(defn add-result-watcher
  "Add a watcher to the connection result queues atom"
  [key watch-fn]
  (add-watch connection-result-queues key watch-fn))

(defn remove-result-watcher
  "Remove a watcher from the connection result queues atom"
  [key]
  (remove-watch connection-result-queues key))

;; =============================================================================
;; Cleanup Support  
;; =============================================================================

(defn clear-results-for-connection!
  "Clear all results associated with a specific connection-id.
   Removes the entire connection entry from result queues."
  [connection-id]
  (let [queue-state (get @connection-result-queues connection-id)
        cleanup-count (if queue-state
                        (+ (count (:completed-results queue-state))
                           (count (:error-results queue-state))
                           (count (:result-promises queue-state)))
                        0)]
    (swap! connection-result-queues dissoc connection-id)
    (binding [*out* *err*]
      (println "[Results] Cleaned up" cleanup-count "results for connection" connection-id))))

(defn clear-all-results!
  "Clear all result queues and promises - used during complete shutdown cleanup"
  []
  (reset! connection-result-queues {})
  (binding [*out* *err*]
    (println "[Results] Cleared all connection result queues and promises")))

;; =============================================================================
;; Debug Support
;; =============================================================================

(defn get-result-summary
  "Get a summary of result queue state for debugging"
  []
  (let [queues @connection-result-queues]
    {:connection-count (count queues)
     :connections (into {} (map (fn [[conn-id queue-state]]
                                  [conn-id {:promise-count (count (:result-promises queue-state))
                                            :completed-count (count (:completed-results queue-state))
                                            :error-count (count (:error-results queue-state))}])
                                queues))
     :total-promises (reduce + (map (fn [[_conn-id queue-state]]
                                      (count (:result-promises queue-state)))
                                    queues))
     :total-completed (reduce + (map (fn [[_conn-id queue-state]]
                                       (count (:completed-results queue-state)))
                                     queues))
     :total-errors (reduce + (map (fn [[_conn-id queue-state]]
                                    (count (:error-results queue-state)))
                                  queues))
     :watchers (keys (.getWatches connection-result-queues))}))