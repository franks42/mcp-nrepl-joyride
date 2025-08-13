(ns nrepl-mcp_server.mcp_server.tools.nrepl-connect
  "nREPL connect tool for MCP"
  (:require [nrepl-mcp_server.state.connection :as state]
            [nrepl-mcp_server.nrepl_client.connection :as conn]
            [nrepl-mcp_server.nrepl_client.handlers] ;; Load handlers to install watchers
            [cheshire.core :as json]))

(defn handle
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