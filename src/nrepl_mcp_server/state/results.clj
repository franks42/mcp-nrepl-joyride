(ns nrepl-mcp-server.state.results
  "Result queue state management for async nREPL responses")

;; =============================================================================
;; Result Queue State Atom
;; =============================================================================

(def result-queue
  "Result queue for async nREPL responses.
   
   Structure:
   {:result-promises {}      ; Map of message-id -> promise
    :completed-results {}    ; Map of message-id -> result data
    :error-results {}}"      ; Map of message-id -> error data
  (atom {:result-promises {}
         :completed-results {}
         :error-results {}}))

;; =============================================================================
;; Result Operations - Phase 2b.1 & 2b.4 Implementation
;; =============================================================================

(defn create-result-promise!
  "Create a promise for a message result.
   Returns the promise that will be delivered when result arrives."
  [message-id]
  (let [result-promise (promise)]
    (swap! result-queue
           assoc-in [:result-promises message-id] result-promise)
    result-promise))

(defn deliver-result!
  "Deliver a successful result for a message."
  [message-id result]
  (when-let [result-promise (get-in @result-queue [:result-promises message-id])]
    ;; Deliver to waiting promise
    (deliver result-promise {:status :success :result result})
    ;; Store in completed results
    (swap! result-queue
           (fn [state]
             (-> state
                 (assoc-in [:completed-results message-id] result)
                 (update :result-promises dissoc message-id))))
    true))

(defn deliver-error!
  "Deliver an error for a message."
  [message-id error]
  (when-let [result-promise (get-in @result-queue [:result-promises message-id])]
    ;; Deliver error to waiting promise
    (deliver result-promise {:status :error :error error})
    ;; Store in error results
    (swap! result-queue
           (fn [state]
             (-> state
                 (assoc-in [:error-results message-id] error)
                 (update :result-promises dissoc message-id))))
    true))

(defn get-result
  "Get a result for a message, waiting if necessary.
   Returns {:status :success/:error/:timeout ...}"
  [message-id timeout-ms]
  ;; Check if already completed
  (if-let [completed (get-in @result-queue [:completed-results message-id])]
    {:status :success :result completed}
    ;; Check if errored
    (if-let [error (get-in @result-queue [:error-results message-id])]
      {:status :error :error error}
      ;; Wait on promise if exists
      (if-let [result-promise (get-in @result-queue [:result-promises message-id])]
        (let [result (deref result-promise timeout-ms :timeout)]
          (if (= result :timeout)
            {:status :timeout :message-id message-id :timeout-ms timeout-ms}
            result))
        ;; No record of this message
        {:status :error :error "Unknown message-id"}))))

;; =============================================================================
;; Watcher Management
;; =============================================================================

(defn add-result-watcher
  "Add a watcher to the result queue atom"
  [key watch-fn]
  (add-watch result-queue key watch-fn))

(defn remove-result-watcher
  "Remove a watcher from the result queue atom"
  [key]
  (remove-watch result-queue key))

;; =============================================================================
;; Debug Support
;; =============================================================================

(defn get-result-summary
  "Get a summary of result queue state for debugging"
  []
  {:promise-count (count (:result-promises @result-queue))
   :completed-count (count (:completed-results @result-queue))
   :error-count (count (:error-results @result-queue))
   :watchers (keys (.getWatches result-queue))})