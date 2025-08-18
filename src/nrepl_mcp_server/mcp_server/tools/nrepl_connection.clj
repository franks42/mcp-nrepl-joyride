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
  "Handle nREPL connect operation"
  [{:keys [connection]}]
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
                  ;; Start receive-watcher now that we have an active connection
                  (watchers/start-receive-watcher!)
                  {:content [{:type "text"
                              :text (json/generate-string
                                     {:status "success"
                                      :operation "connect"
                                      :hostname hostname
                                      :port port
                                      :connection-id (:connection-id result)
                                      :message (str "Connected to nREPL server at "
                                                    hostname ":" port)}
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
  "Handle nREPL disconnect operation"
  [_args]
  (if (state/connected?)
    ;; Disconnect from active connection
    (let [result (conn/close-connection!)]
      (case (:status result)
        :success
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "success"
                            :operation "disconnect"
                            :connection-id (:connection-id result)
                            :message "Disconnected from nREPL server"}
                           {:pretty true})}]}

        ;; Unexpected status
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :operation "disconnect"
                            :error (str "Unexpected disconnect result: " result)}
                           {:pretty true})}]
         :isError true}))

    ;; Not connected
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "error"
                        :operation "disconnect"
                        :error (str "Not connected - current status: "
                                    (state/get-connection-status))}
                       {:pretty true})}]
     :isError true}))

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

    ;; Unknown operation
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "error"
                        :operation op
                        :error (str "Unknown operation: " op
                                    ". Use 'connect', 'disconnect', or 'status'")}
                       {:pretty true})}]
     :isError true}))

(def tool-name "nrepl-connection")

(def metadata
  {:description "nREPL connection operations: connect, disconnect, status"
   :inputSchema {:type "object"
                 :properties {:op {:type "string"
                                   :description "Operation: 'connect', 'disconnect', or 'status'"
                                   :enum ["connect" "disconnect" "status"]}
                              :connection {:type "string"
                                           :description "Connection info for connect: host:port, port, or file path"}
                              :timeout {:type "integer"
                                        :description "Timeout in milliseconds (default 5000)"}}
                 :required ["op"]}})

