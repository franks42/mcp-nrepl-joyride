(ns nrepl-mcp-server.nrepl-client.socket-connection
  "Low-level nREPL connection lifecycle management"
  (:import [java.net Socket]
           [java.io PushbackInputStream]))

;; Connection state management
(defonce ^:private connection-state
  (atom {:connections {}      ; Map of connection-id -> connection details
         :counter 0}))        ; Counter for generating unique connection IDs

(defn- generate-connection-id
  "Generate a unique connection identifier"
  []
  (str "conn-" (:counter (swap! connection-state update :counter inc))))

(defn connect
  "Connect to nREPL server and return connection map with tracking"
  [host port]
  (let [socket (Socket. host port)
        out (.getOutputStream socket)
        in (PushbackInputStream. (.getInputStream socket))
        conn-id (generate-connection-id)
        conn {:socket socket
              :out out
              :in in
              :host host
              :port port
              :id conn-id
              :created-at (System/currentTimeMillis)
              :status :connected}]
    ;; Track connection in state
    (swap! connection-state assoc-in [:connections conn-id]
           {:host host
            :port port
            :created-at (:created-at conn)
            :status :connected})
    conn))

(defn close-connection
  "Close nREPL connection and update state. 
  Marks all pending messages for this connection as failed."
  [{:keys [socket id]} mark-connection-messages-failed-fn]
  (when socket
    (.close socket))
  ;; Mark pending messages as failed before updating connection state
  (when id
    (let [failed-count (mark-connection-messages-failed-fn
                        id
                        :connection-closed
                        "Connection closed")]
      (when (> failed-count 0)
        (binding [*out* *err*]
          (println (str "[Queue] Marked " failed-count " pending messages as failed for connection " id)))))
    ;; Update connection state
    (swap! connection-state update-in [:connections id]
           assoc :status :closed
           :closed-at (System/currentTimeMillis))))

;; Connection state query functions
(defn get-connection-state
  "Get the current state of a connection by ID"
  [conn-id]
  (get-in @connection-state [:connections conn-id]))

(defn list-connections
  "List all tracked connections with their status"
  []
  (:connections @connection-state))

(defn active-connections
  "Get all active (connected) connections"
  []
  (into {}
        (filter (fn [[_ conn]] (= :connected (:status conn)))
                (:connections @connection-state))))

(defn cleanup-closed-connections
  "Remove closed connections from state that are older than threshold-ms"
  [& {:keys [threshold-ms] :or {threshold-ms (* 60 60 1000)}}] ; Default 1 hour
  (let [now (System/currentTimeMillis)
        cutoff (- now threshold-ms)]
    (swap! connection-state update :connections
           (fn [conns]
             (into {}
                   (remove (fn [[_ conn]]
                             (and (= :closed (:status conn))
                                  (<= (:closed-at conn 0) cutoff)))
                           conns))))))

(defn get-connection-atom
  "Get access to the connection state atom for external access"
  []
  connection-state)