(ns nrepl-mcp-server.mcp.tools.nrepl-status
  "nREPL status tool for MCP"
  (:require [nrepl-mcp-server.state :as state]
            [cheshire.core :as json]))

(defn handle
  "Handle nREPL status operation"
  [_args]
  (let [state @state/connection-state]
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "success"
                        :operation "status"
                        :connection-status (:status state)
                        :hostname (:hostname state)
                        :port (:port state)
                        :connected-at (:connected-at state)
                        :error (:error state)}
                       {:pretty true})}]}))