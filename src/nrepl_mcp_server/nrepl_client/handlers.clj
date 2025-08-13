(ns nrepl-mcp-server.nrepl-client.handlers
  "State watchers and reactive handlers for nREPL client"
  (:require [nrepl-mcp-server.state :as state]
            [nrepl-mcp-server.nrepl-client.connection :as conn]))

;; =============================================================================
;; Connection State Handlers
;; =============================================================================

(defn connection-handler
  "Watcher function for connection state changes"
  [_key _ref old-state new-state]
  (let [old-status (:status old-state)
        new-status (:status new-state)]
    (when (not= old-status new-status)
      (case new-status
        :pending-connect
        (future
          (conn/attempt-connection! (select-keys new-state [:hostname :port])))

        :pending-disconnect
        (future
          (conn/close-connection!))

        nil))))

;; =============================================================================
;; Install Watchers
;; =============================================================================

;; Install the connection handler watcher
(state/add-connection-watcher :connection-handler connection-handler)

;; =============================================================================
;; Queue Handlers (Placeholder for Phase 2b)
;; =============================================================================

;; Queue handlers will be added here in Phase 2b
;; - Send queue processor
;; - Result queue manager
;; - Cleanup handlers for disconnection