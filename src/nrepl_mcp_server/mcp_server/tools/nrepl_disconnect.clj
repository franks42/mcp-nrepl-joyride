(ns nrepl-mcp_server.mcp_server.tools.nrepl-disconnect
  "nREPL disconnect tool for MCP"
  (:require [nrepl-mcp_server.state.connection :as state]
            [nrepl-mcp_server.nrepl_client.connection :as conn]
            [cheshire.core :as json]))

(defn handle
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