(ns nrepl-mcp-server.nrepl-client.operations
  "nREPL operation functions - pure nREPL protocol implementations integrated with reactive state"
  (:require [nrepl-mcp-server.nrepl-client.messaging :as msg]))

(defn eval-code
  "Evaluate code in nREPL session"
  [conn code & {:keys [session ns timeout-ms] :or {timeout-ms 5000}}]
  (let [message (cond-> {:op "eval" :code code}
                  session (assoc :session session)
                  ns (assoc :ns ns))]
    (msg/send-message-async conn message timeout-ms)))

;; High-level convenience functions

(defn eval-with-timeout
  "Convenience function to evaluate code with reasonable defaults"
  [conn code & {:keys [timeout-ms] :or {timeout-ms 10000}}]
  (eval-code conn code :timeout-ms timeout-ms))

(defn health-check
  "Perform basic health check on nREPL connection"
  [conn & {:keys [timeout-ms] :or {timeout-ms 3000}}]
  (eval-code conn "(+ 1 2 3)" :timeout-ms timeout-ms))

(defn send-message
  "Send arbitrary nREPL message - generic wrapper for watcher usage"
  [conn message & {:keys [timeout-ms] :or {timeout-ms 30000}}]
  (msg/send-message-async conn message timeout-ms))

(defn send-message-fire-and-forget
  "Send arbitrary nREPL message without waiting for response.
   Used by send-queue-watcher for fire-and-forget messaging."
  [conn message]
  (msg/send-message-fire-and-forget conn message))