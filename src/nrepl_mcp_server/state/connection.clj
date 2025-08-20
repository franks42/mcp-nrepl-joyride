(ns nrepl-mcp-server.state.connection
  "Unified connection state management for nREPL client connections - SINGLE SOURCE OF TRUTH"
  (:require [nrepl-mcp-server.utils.uuid-v7 :as uuid])
  (:import [java.net InetAddress]))

;; =============================================================================
;; Connection State Atom - SINGLE SOURCE OF TRUTH
;; =============================================================================

(def connection-state
  "Unified nREPL connection state with registry and active connection tracking.
   
   This is the SINGLE SOURCE OF TRUTH for all connection state - eliminates 
   duplication between application layer and transport layer.
   
   Connection Status values:
   - :connecting       Connection attempt in progress
   - :connected        Active connection established
   - :closing          Disconnection in progress  
   - :closed           Connection closed cleanly
   - :failed           Connection attempt failed
   
   Structure:
   {:active-connection nil                      ; Current active connection ID or nil
    :connections {\"IP:port-UUID\" {            ; Human-readable connection registry
                   :connection-id \"...\",      ; Human-readable ID
                   :hostname \"localhost\",     ; Original hostname
                   :resolved-ip \"192.168.1.10\", ; Resolved IP address
                   :port 7890,                  ; Port number
                   :socket #<Socket>,           ; Actual socket object
                   :status :connected,          ; Connection status
                   :created-at 1641234567,      ; Creation timestamp
                   :closed-at nil,              ; Close timestamp
                   :error nil}}                 ; Error information
    :nicknames {}                              ; NEW: nickname -> connection-id mapping
    :connection-counter 0}                      ; Counter for unique connection IDs"
  (atom {:active-connection nil
         :connections {}
         :nicknames {}
         :connection-counter 0}))

;; =============================================================================
;; IP Address Resolution Utilities
;; =============================================================================

(defn resolve-ip-address
  "Resolve hostname to actual IP address for human-readable connection IDs.
   Converts 'localhost' to actual local IP address."
  [hostname]
  (try
    (if (= "localhost" hostname)
      ;; Get actual local IP address instead of 127.0.0.1
      (let [local-host (InetAddress/getLocalHost)]
        (.getHostAddress local-host))
      ;; Resolve other hostnames to IP
      (let [inet-addr (InetAddress/getByName hostname)]
        (.getHostAddress inet-addr)))
    (catch Exception e
      ;; Fallback to original hostname if resolution fails
      (binding [*out* *err*]
        (println "[Connection] IP resolution failed for" hostname ":" (.getMessage e)))
      hostname)))

;; =============================================================================
;; Human-Readable Connection ID Generation
;; =============================================================================

(defn generate-human-readable-connection-id
  "Generate human-readable connection ID in format: IP:port-UUIDv7
   Example: 192.168.1.10:7890-01234567-abcd-89ef-ghij-klmnopqrstuv"
  [hostname port]
  (let [resolved-ip (resolve-ip-address hostname)
        uuid-suffix (uuid/uuid-v7-string)]
    (str resolved-ip ":" port "-" uuid-suffix)))

;; =============================================================================
;; State Query Functions
;; =============================================================================

(defn get-active-connection
  "Get current active connection details, or nil if no active connection"
  []
  (when-let [active-id (:active-connection @connection-state)]
    (get-in @connection-state [:connections active-id])))

(defn connected?
  "Check if there is an active connected connection"
  []
  (when-let [active-conn (get-active-connection)]
    (= :connected (:status active-conn))))

(defn can-connect?
  "Check if a new connection attempt is allowed.
   Phase 7: Multi-connection enabled - always returns true."
  []
  true ;; Multi-connection support enabled
  #_(let [active-conn (get-active-connection)]
      (or (nil? active-conn)
          (#{:closed :failed} (:status active-conn)))))

(defn get-connection-status
  "Get current connection status - backward compatibility"
  []
  (if-let [active-conn (get-active-connection)]
    (:status active-conn)
    :disconnected))

;; =============================================================================
;; Connection Management API for Socket Layer
;; =============================================================================

(defn register-connection!
  "Register a new connection with human-readable ID and set as active.
   Returns the connection ID."
  [hostname port socket]
  (let [conn-id (generate-human-readable-connection-id hostname port)
        resolved-ip (resolve-ip-address hostname)
        connection-data {:connection-id conn-id
                         :hostname hostname
                         :resolved-ip resolved-ip
                         :port port
                         :socket socket
                         :status :connected
                         :created-at (System/currentTimeMillis)
                         :closed-at nil
                         :error nil}]
    (swap! connection-state
           (fn [state]
             (-> state
                 (assoc-in [:connections conn-id] connection-data)
                 (assoc :active-connection conn-id)
                 (update :connection-counter inc))))
    (binding [*out* *err*]
      (println "[Connection] Registered connection:" conn-id))
    conn-id))

(defn update-connection-status!
  "Update status of a specific connection"
  [connection-id new-status & {:keys [error closed-at]}]
  (when (get-in @connection-state [:connections connection-id])
    (swap! connection-state
           (fn [state]
             (-> state
                 (assoc-in [:connections connection-id :status] new-status)
                 (cond-> error (assoc-in [:connections connection-id :error] error))
                 (cond-> closed-at (assoc-in [:connections connection-id :closed-at] closed-at)))))
    (binding [*out* *err*]
      (println "[Connection] Updated" connection-id "status to" new-status))))

(defn mark-connection-closed!
  "Mark connection as closed and clear active connection if it matches"
  [connection-id error-type error-msg]
  (let [closed-at (System/currentTimeMillis)]
    (swap! connection-state
           (fn [state]
             (-> state
                 (assoc-in [:connections connection-id :status] :closed)
                 (assoc-in [:connections connection-id :closed-at] closed-at)
                 (assoc-in [:connections connection-id :error] {:type error-type :message error-msg})
                 (cond-> (= connection-id (:active-connection state))
                   (assoc :active-connection nil)))))
    (binding [*out* *err*]
      (println "[Connection] Marked" connection-id "as closed:" error-type))
    ;; Return count of failed messages for compatibility
    0))

(defn cleanup-old-connections!
  "Remove old closed connections older than threshold-ms"
  [& {:keys [threshold-ms] :or {threshold-ms (* 60 60 1000)}}] ; Default 1 hour
  (let [now (System/currentTimeMillis)
        cutoff (- now threshold-ms)]
    (swap! connection-state
           (fn [state]
             (update state :connections
                     (fn [conns]
                       (into {}
                             (remove (fn [[_ conn]]
                                       (and (#{:closed :failed} (:status conn))
                                            (<= (:closed-at conn 0) cutoff)))
                                     conns))))))
    (binding [*out* *err*]
      (println "[Connection] Cleaned up old connections older than" threshold-ms "ms"))))

;; =============================================================================
;; Backward Compatibility Functions
;; =============================================================================

(defn request-connect!
  "Request connection - backward compatibility (now just validation)"
  [_hostname _port]
  (can-connect?))

(defn mark-connected!
  "Mark connection as successful - backward compatibility"
  [_socket]
  ;; This is now handled by register-connection!
  true)

(defn mark-failed!
  "Mark connection as failed - backward compatibility"
  [error-msg]
  (when-let [active-id (:active-connection @connection-state)]
    (update-connection-status! active-id :failed :error error-msg)))

(defn mark-disconnected!
  "Mark connection as disconnected - backward compatibility"
  []
  (when-let [active-id (:active-connection @connection-state)]
    (mark-connection-closed! active-id :user-disconnect "User requested disconnect")))

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
;; Connection Resolution for Multi-Connection Interface
;; =============================================================================

(defn resolve-connection-id
  "Resolve connection parameter to connection-id. Phase 2: Nickname support with single connection.
   
   Phase 2 Implementation:
   - Returns active connection ID if no identifier provided
   - Supports nickname lookup (nickname -> connection-id)
   - Supports direct connection-id lookup
   - Fallback to active connection for compatibility
   - Throws actionable errors for no connections
   
   Future Phase 4 Implementation will handle:
   - Multiple active connections
   - Endpoint resolution (host:port)"
  [user-identifier]
  (let [state @connection-state
        active-conn-id (:active-connection state)]
    (cond
      ;; No identifier provided - use single connection rule
      (nil? user-identifier)
      (if active-conn-id
        active-conn-id
        (throw (ex-info "No nREPL connections available. Connect first using nrepl-connection tool."
                        {:status :no-connections})))

      ;; Try nickname lookup first
      (get (:nicknames state) user-identifier)
      (get (:nicknames state) user-identifier)

      ;; Try connection-id directly
      (get (:connections state) user-identifier)
      user-identifier

      ;; Not found - fallback to active connection if available (Phase 2 compatibility)
      :else
      (if active-conn-id
        active-conn-id
        (throw (ex-info "Connection not found. No active nREPL connection available."
                        {:status :connection-not-found
                         :identifier user-identifier}))))))

;; =============================================================================
;; Nickname Management
;; =============================================================================

(defn register-nickname!
  "Register a nickname for a connection-id. Overwrites existing nickname if present."
  [nickname connection-id]
  (swap! connection-state assoc-in [:nicknames nickname] connection-id)
  (binding [*out* *err*]
    (println "[Connection] Registered nickname" (pr-str nickname) "for connection" connection-id)))

(defn unregister-nickname!
  "Remove a nickname mapping"
  [nickname]
  (swap! connection-state update :nicknames dissoc nickname)
  (binding [*out* *err*]
    (println "[Connection] Unregistered nickname" (pr-str nickname))))

(defn get-nickname-for-connection
  "Get nickname for a connection-id if one exists"
  [connection-id]
  (let [nicknames (:nicknames @connection-state)]
    (->> nicknames
         (filter (fn [[_nick conn-id]] (= conn-id connection-id)))
         first
         first))) ; Returns nickname or nil

;; =============================================================================
;; Debug Support
;; =============================================================================

(defn get-connection-summary
  "Get a summary of current connection state for debugging"
  []
  (let [state @connection-state]
    {:active-connection (:active-connection state)
     :connection-count (count (:connections state))
     :connection-counter (:connection-counter state)
     :connection-ids (keys (:connections state))
     :nicknames (:nicknames state)
     :nickname-count (count (:nicknames state))
     :watchers (keys (.getWatches connection-state))}))

(defn list-all-connections
  "List all connections with their status for debugging"
  []
  (:connections @connection-state))

(defn get-connection-by-id
  "Get connection details by ID"
  [connection-id]
  (get-in @connection-state [:connections connection-id]))