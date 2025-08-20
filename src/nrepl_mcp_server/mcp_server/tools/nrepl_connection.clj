(ns nrepl-mcp-server.mcp-server.tools.nrepl-connection
  "Unified nREPL connection tool for MCP - handles connect, disconnect, status operations"
  (:require [nrepl-mcp-server.state.connection :as state]
            [nrepl-mcp-server.nrepl-client.connection :as conn]
            [nrepl-mcp-server.nrepl-client.handlers] ;; Load handlers to install watchers
            [cheshire.core :as json]
            [nrepl-mcp-server.state.watchers :as watchers]))

;; =============================================================================
;; Operation Handlers
;; =============================================================================

(defn handle-connect
  "Handle nREPL connect operation with optional nickname support"
  [{:keys [connection nickname]}]
  (if (empty? connection)
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "error"
                        :operation "connect"
                        :error "No connection info provided"}
                       {:pretty true})}]
     :isError true}
    (let [params (conn/resolve-connection-params connection)]
      (if (:error params)
        ;; Parameter resolution failed
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :operation "connect"
                            :error (:error params)}
                           {:pretty true})}]
         :isError true}

        ;; Try to connect
        (let [{:keys [hostname port]} params]
          (if (state/can-connect?)
            ;; Attempt connection directly
            (let [result (conn/attempt-connection! params)]
              (case (:status result)
                :success
                (do
                  ;; Multi-connection mode: Only start watchers for this specific connection
                  ;; Don't stop existing watchers for other connections!
                  (watchers/start-connection-watchers! (:connection-id result))
                  ;; Ensure global send-queue-watcher is running (idempotent)
                  (watchers/start-all-watchers!)
                  ;; Handle nickname registration if provided
                  (when (and nickname (seq nickname))
                    (state/register-nickname! nickname (:connection-id result)))
                  {:content [{:type "text"
                              :text (json/generate-string
                                     (cond-> {:status "success"
                                              :operation "connect"
                                              :hostname hostname
                                              :port port
                                              :connection-id (:connection-id result)
                                              :message (str "Connected to nREPL server at "
                                                            hostname ":" port)}
                                       nickname (assoc :nickname nickname
                                                       :message (str "Connected to nREPL server at "
                                                                     hostname ":" port
                                                                     " with nickname '" nickname "'")))
                                     {:pretty true})}]})

                :failed
                {:content [{:type "text"
                            :text (json/generate-string
                                   {:status "error"
                                    :operation "connect"
                                    :hostname hostname
                                    :port port
                                    :error (:error result)}
                                   {:pretty true})}]
                 :isError true}

                ;; Unexpected status
                {:content [{:type "text"
                            :text (json/generate-string
                                   {:status "error"
                                    :operation "connect"
                                    :error (str "Unexpected connection result: " result)}
                                   {:pretty true})}]
                 :isError true}))

            ;; Already connected
            {:content [{:type "text"
                        :text (json/generate-string
                               {:status "error"
                                :operation "connect"
                                :error (str "Cannot connect - current status: "
                                            (state/get-connection-status)
                                            ". Disconnect first.")}
                               {:pretty true})}]
             :isError true}))))))

(defn handle-disconnect
  "Handle nREPL disconnect operation with connection parameter support"
  [{:keys [connection] :as _args}]
  (try
    ;; Resolve connection ID - this will handle nicknames, connection-ids, or default to active
    (let [connection-id (state/resolve-connection-id connection)
          conn-details (state/get-connection-by-id connection-id)]

      (if conn-details
        ;; Connection exists - close it
        (let [result (conn/close-specific-connection! connection-id)]
          (case (:status result)
            :success
            {:content [{:type "text"
                        :text (json/generate-string
                               {:status "success"
                                :operation "disconnect"
                                :connection-id connection-id
                                :hostname (:hostname conn-details)
                                :port (:port conn-details)
                                :message (str "Disconnected from nREPL server "
                                              (:hostname conn-details) ":" (:port conn-details)
                                              (when connection (str " (connection: " connection ")")))}
                               {:pretty true})}]}

            ;; Unexpected status
            {:content [{:type "text"
                        :text (json/generate-string
                               {:status "error"
                                :operation "disconnect"
                                :connection-id connection-id
                                :error (str "Unexpected disconnect result: " result)}
                               {:pretty true})}]
             :isError true}))

        ;; Connection not found (shouldn't happen due to resolve-connection-id throwing)
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :operation "disconnect"
                            :error "Connection not found"}
                           {:pretty true})}]
         :isError true}))

    ;; Handle connection resolution errors (no connections, connection not found)
    (catch Exception e
      {:content [{:type "text"
                  :text (json/generate-string
                         {:status "error"
                          :operation "disconnect"
                          :error (.getMessage e)
                          :connection connection}
                         {:pretty true})}]
       :isError true})))

(defn handle-status
  "Handle nREPL status operation"
  [_args]
  (let [active-conn (state/get-active-connection)
        summary (state/get-connection-summary)]
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "success"
                        :operation "status"
                        :connection-status (if active-conn (:status active-conn) :disconnected)
                        :active-connection (:active-connection summary)
                        :connection-count (:connection-count summary)
                        :hostname (when active-conn (:hostname active-conn))
                        :port (when active-conn (:port active-conn))
                        :resolved-ip (when active-conn (:resolved-ip active-conn))
                        :created-at (when active-conn (:created-at active-conn))
                        :closed-at (when active-conn (:closed-at active-conn))
                        :error (when active-conn (:error active-conn))}
                       {:pretty true})}]}))

(defn handle-list
  "Handle nREPL list connections operation - shows ALL connections in multi-connection mode"
  [_args]
  (let [all-connections (state/list-all-connections)
        summary (state/get-connection-summary)
        active-conn-id (:active-connection summary)]
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "success"
                        :operation "list"
                        :connection-count (:connection-count summary)
                        :active-connection active-conn-id
                        :connections (vec (map (fn [[conn-id conn-details]]
                                                 (let [nickname (state/get-nickname-for-connection conn-id)]
                                                   {:connection-id conn-id
                                                    :hostname (:hostname conn-details)
                                                    :port (:port conn-details)
                                                    :resolved-ip (:resolved-ip conn-details)
                                                    :status (:status conn-details)
                                                    :nickname nickname
                                                    :created-at (:created-at conn-details)
                                                    :closed-at (:closed-at conn-details)
                                                    :is-active (= conn-id active-conn-id)
                                                    :connection (str (:hostname conn-details) ":" (:port conn-details))}))
                                               all-connections))
                        :nicknames (:nicknames summary)}
                       {:pretty true})}]}))

(defn handle-disconnect-all
  "Handle nREPL disconnect-all operation (disconnects the single connection)"
  [_args]
  (if (state/connected?)
    ;; Disconnect from active connection
    (let [result (conn/close-connection!)]
      (case (:status result)
        :success
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "success"
                            :operation "disconnect-all"
                            :connection-id (:connection-id result)
                            :disconnected-count 1
                            :message "Disconnected from nREPL server"}
                           {:pretty true})}]}

        ;; Unexpected status
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :operation "disconnect-all"
                            :error (str "Unexpected disconnect result: " result)}
                           {:pretty true})}]
         :isError true}))

    ;; Not connected
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "success"
                        :operation "disconnect-all"
                        :connection-id nil
                        :disconnected-count 0
                        :message "No connections to disconnect"}
                       {:pretty true})}]}))

;; =============================================================================
;; Main Handler
;; =============================================================================

(defn handle
  "Handle nREPL server operations based on op parameter"
  [{:keys [op] :as args}]
  (case op
    "connect" (handle-connect args)
    "disconnect" (handle-disconnect args)
    "status" (handle-status args)
    "list" (handle-list args)
    "disconnect-all" (handle-disconnect-all args)

    ;; Unknown operation
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "error"
                        :operation op
                        :error (str "Unknown operation: " op
                                    ". Use 'connect', 'disconnect', 'status', 'list', or 'disconnect-all'")}
                       {:pretty true})}]
     :isError true}))

(def tool-name "nrepl-connection")

(def metadata
  {:description "nREPL connection operations: connect, disconnect, status, list, disconnect-all"
   :inputSchema {:type "object"
                 :properties {:op {:type "string"
                                   :description "Operation: 'connect', 'disconnect', 'status', 'list', or 'disconnect-all'"
                                   :enum ["connect" "disconnect" "status" "list" "disconnect-all"]}
                              :connection {:type "string"
                                           :description "Connection identifier - for connect: host:port, port, or file path; for disconnect: nickname, connection-id, or host:port (optional - defaults to active connection)"}
                              :nickname {:type "string"
                                         :description "Optional nickname for connection (for connect operation)"}
                              :timeout {:type "integer"
                                        :description "Timeout in milliseconds (default 5000)"}}
                 :required ["op"]}})

