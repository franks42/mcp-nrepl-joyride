(ns mcp-nrepl-proxy.utils
  "Utility functions for the MCP-nREPL bridge.
   
   This namespace contains generic utility functions including:
   - Logging utilities
   - Data encoding/decoding 
   - Command caching
   - State introspection helpers"
  (:import [java.util Base64]
           [java.time Instant]))

;; ============================================================================
;; State Reference (imported from core)
;; ============================================================================

;; Note: The state atom is defined in core.clj and will be passed as parameter
;; or accessed via require to avoid circular dependencies

;; ============================================================================
;; Logging Utilities
;; ============================================================================

(defn log
  "Log to stderr (stdout reserved for MCP protocol).
   
   Args:
     level - :debug, :info, :warn, :error
     args - Messages to log
     
   Requires state atom with :config :debug setting"
  [state level & args]
  (when (or (= level :error)
            (get-in @state [:config :debug]))
    (binding [*out* *err*]
      (println (str "[" (name level) "] " (apply str args))))))

;; ============================================================================
;; Data Encoding/Decoding Utilities  
;; ============================================================================

(defn base64-decode
  "Decode base64 string to regular string, or return as-is if not base64 or not a string.
   
   Args:
     value - String value to decode (may or may not be base64)
     
   Returns:
     Decoded string or original value if not valid base64"
  [value]
  (cond
    (nil? value) nil
    (not (string? value)) (str value)  ; Convert non-strings to strings
    :else
    (try
      (String. (.decode (Base64/getDecoder) value))
      (catch Exception _e
        ; Return original value if not valid base64
        value))))

(defn decode-nrepl-response
  "Decode base64-encoded values and byte arrays in nREPL response.
   
   Args:
     response - nREPL response map
     
   Returns:
     Response map with decoded string values"
  [response]
  (when response
    (cond-> response
      (:value response) (update :value base64-decode)
      (:ns response) (update :ns base64-decode)
      (:out response) (update :out base64-decode)
      (:err response) (update :err base64-decode)
      (:status response) (update :status #(map base64-decode %)))))

;; ============================================================================
;; Command Caching
;; ============================================================================

(defn cache-command
  "Cache a command and its result for resource access.
   
   Args:
     state - State atom reference
     code - Command code that was executed
     result - Result of the command execution
     
   Side effects:
     Updates the state atom's :recent-commands with the new command"
  [state code result]
  (let [max-cached (or (get-in @state [:config :max-cached-commands]) 10)
        command {:code code
                 :result result
                 :timestamp (str (Instant/now))}]
    (swap! state update :recent-commands
           (fn [commands]
             (vec (take max-cached (cons command commands)))))))

;; ============================================================================
;; State Introspection Helpers
;; ============================================================================

(defn get-server-state
  "Get current server state for introspection.
   
   Args:
     state - State atom reference
     
   Returns:
     Complete server state map"
  [state]
  @state)

(defn get-mcp-stats
  "Get MCP server statistics.
   
   Args:
     state - State atom reference
     
   Returns:
     Map with server statistics including sessions, commands, health, etc."
  [state]
  {:sessions (count (:sessions @state))
   :recent-commands (count (:recent-commands @state))
   :health (:health-status @state)
   :transport (get-in @state [:config :transport])
   :debug (get-in @state [:config :debug])})