(ns nrepl-mcp-server.nrepl-client.socket-connection
  "Low-level nREPL connection lifecycle management - uses unified state management"
  (:require [nrepl-mcp-server.state.connection :as conn-state])
  (:import [java.net Socket]
           [java.io PushbackInputStream]))

(defn connect
  "Connect to nREPL server and return connection map with unified state tracking"
  [host port]
  (let [socket (Socket. host port)
        out (.getOutputStream socket)
        in (PushbackInputStream. (.getInputStream socket))
        ;; Register connection in unified state management
        conn-id (conn-state/register-connection! host port socket)
        conn {:socket socket
              :out out
              :in in
              :host host
              :port port
              :id conn-id
              :created-at (System/currentTimeMillis)
              :status :connected}]
    (binding [*out* *err*]
      (println "[Socket] Connected to" host ":" port "with ID" conn-id))
    conn))

(defn close-connection
  "Close nREPL connection and update unified state."
  [{:keys [socket id]}]
  (when socket
    (.close socket))
  ;; Update unified connection state
  (when id
    (conn-state/mark-connection-closed! id :connection-closed "Connection closed")
    (binding [*out* *err*]
      (println "[Socket] Closed connection" id))))

;; =============================================================================
;; Wrapper Functions for Backward Compatibility
;; =============================================================================
;; These functions delegate to the unified state management in state/connection.clj

(defn get-connection-state
  "Get the current state of a connection by ID (delegates to unified state)"
  [conn-id]
  (conn-state/get-connection-by-id conn-id))

(defn list-connections
  "List all tracked connections with their status (delegates to unified state)"
  []
  (conn-state/list-all-connections))

(defn active-connections
  "Get all active (connected) connections (delegates to unified state)"
  []
  (let [all-connections (conn-state/list-all-connections)]
    (into {}
          (filter (fn [[_ conn]] (= :connected (:status conn)))
                  all-connections))))

(defn cleanup-closed-connections
  "Remove closed connections from state that are older than threshold-ms"
  [& {:keys [threshold-ms] :or {threshold-ms (* 60 60 1000)}}]
  (conn-state/cleanup-old-connections! :threshold-ms threshold-ms))

(defn get-connection-atom
  "Get access to the unified connection state atom"
  []
  ;; Note: This now points to the unified state atom
  @#'conn-state/connection-state)