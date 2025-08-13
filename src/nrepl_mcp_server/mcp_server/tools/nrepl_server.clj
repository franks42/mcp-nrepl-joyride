(ns nrepl-mcp_server.mcp_server.tools.nrepl-server
  "Unified nREPL server tool for MCP - handles connect, disconnect, status operations"
  (:require [nrepl-mcp_server.state.connection :as state]
            [nrepl-mcp_server.nrepl_client.connection :as conn]
            [nrepl-mcp_server.nrepl_client.handlers] ;; Load handlers to install watchers
            [cheshire.core :as json]))

;; =============================================================================
;; Operation Handlers
;; =============================================================================

(defn handle-connect
  "Handle nREPL connect operation"
  [{:keys [connection timeout] :or {timeout 5000}}]
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
          (if (state/request-connect! hostname port)
            ;; Wait for connection result
            (let [result-status (conn/wait-for-state-change :pending-connect timeout)]
              (case result-status
                :connected
                {:content [{:type "text"
                            :text (json/generate-string
                                   {:status "success"
                                    :operation "connect"
                                    :hostname hostname
                                    :port port
                                    :message (str "Connected to nREPL server at "
                                                  hostname ":" port)}
                                   {:pretty true})}]}

                :failed
                {:content [{:type "text"
                            :text (json/generate-string
                                   {:status "error"
                                    :operation "connect"
                                    :hostname hostname
                                    :port port
                                    :error (:error @state/connection-state)}
                                   {:pretty true})}]
                 :isError true}

                :timeout
                {:content [{:type "text"
                            :text (json/generate-string
                                   {:status "error"
                                    :operation "connect"
                                    :hostname hostname
                                    :port port
                                    :error (str "Connection timeout after " timeout "ms")}
                                   {:pretty true})}]
                 :isError true}

                ;; Unexpected status
                {:content [{:type "text"
                            :text (json/generate-string
                                   {:status "error"
                                    :operation "connect"
                                    :error (str "Unexpected status: " result-status)}
                                   {:pretty true})}]
                 :isError true}))

            ;; Already connected or pending
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
  [{:keys [timeout] :or {timeout 5000}}]
  (if (state/request-disconnect!)
    ;; Wait for disconnection
    (let [result-status (conn/wait-for-state-change :pending-disconnect timeout)]
      (case result-status
        :disconnected
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "success"
                            :operation "disconnect"
                            :message "Disconnected from nREPL server"}
                           {:pretty true})}]}

        :timeout
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :operation "disconnect"
                            :error (str "Disconnect timeout after " timeout "ms")}
                           {:pretty true})}]
         :isError true}

        ;; Unexpected status
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :operation "disconnect"
                            :error (str "Unexpected status: " result-status)}
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
  (let [conn-state @state/connection-state]
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "success"
                        :operation "status"
                        :connection-status (:status conn-state)
                        :hostname (:hostname conn-state)
                        :port (:port conn-state)
                        :connected-at (:connected-at conn-state)
                        :error (:error conn-state)}
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