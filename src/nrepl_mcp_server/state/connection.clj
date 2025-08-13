(ns nrepl-mcp_server.state.connection
  "Connection state management for nREPL client connections")

;; =============================================================================
;; Connection State Atom
;; =============================================================================

(def connection-state
  "Single nREPL connection state.
   
   Status values:
   - :disconnected     No active connection
   - :pending-connect  Connection attempt in progress
   - :connected        Active connection established
   - :pending-disconnect Disconnection in progress
   - :failed          Connection attempt failed
   
   Structure:
   {:status :disconnected
    :hostname nil
    :port nil
    :socket nil
    :connected-at nil
    :error nil}"
  (atom {:status :disconnected
         :hostname nil
         :port nil
         :socket nil
         :connected-at nil
         :error nil}))

;; =============================================================================
;; State Query Functions
;; =============================================================================

(defn get-connection-status
  "Get current connection status"
  []
  (:status @connection-state))

(defn connected?
  "Check if currently connected"
  []
  (= :connected (get-connection-status)))

(defn can-connect?
  "Check if connection attempt is allowed"
  []
  (#{:disconnected :failed} (get-connection-status)))

;; =============================================================================
;; State Update Functions
;; =============================================================================

(defn update-connection-state!
  "Update connection state with new values"
  [updates]
  (swap! connection-state merge updates))

(defn request-connect!
  "Request connection to nREPL server"
  [hostname port]
  (if (can-connect?)
    (do
      (update-connection-state!
       {:status :pending-connect
        :hostname hostname
        :port port
        :error nil})
      true)
    false))

(defn request-disconnect!
  "Request disconnection from nREPL server"
  []
  (if (connected?)
    (do
      (update-connection-state! {:status :pending-disconnect})
      true)
    false))

(defn mark-connected!
  "Mark connection as successful"
  [socket]
  (update-connection-state!
   {:status :connected
    :socket socket
    :connected-at (System/currentTimeMillis)
    :error nil}))

(defn mark-failed!
  "Mark connection as failed"
  [error-msg]
  (update-connection-state!
   {:status :failed
    :socket nil
    :error error-msg}))

(defn mark-disconnected!
  "Mark connection as disconnected"
  []
  (update-connection-state!
   {:status :disconnected
    :hostname nil
    :port nil
    :socket nil
    :connected-at nil
    :error nil}))

;; =============================================================================
;; Watcher Management
;; =============================================================================

(defn add-connection-watcher
  "Add a watcher to the connection state atom"
  [key watch-fn]
  (add-watch connection-state key watch-fn))

(defn remove-connection-watcher
  "Remove a watcher from the connection state atom"
  [key]
  (remove-watch connection-state key))

;; =============================================================================
;; Debug Support
;; =============================================================================

(defn get-connection-summary
  "Get a summary of current connection state for debugging"
  []
  {:state @connection-state
   :watchers (keys (.getWatches connection-state))})