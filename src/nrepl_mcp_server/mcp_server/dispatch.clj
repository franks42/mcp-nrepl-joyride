(ns nrepl-mcp_server.mcp_server.dispatch
  "MCP tool routing and dispatch table"
  (:require [cheshire.core :as json]
            [nrepl-mcp_server.mcp_server.tools.debug-eval :as debug-eval]
            [nrepl-mcp_server.mcp_server.tools.debug-load-file :as debug-load-file]
            [nrepl-mcp_server.mcp_server.tools.nrepl-connect :as nrepl-connect]
            [nrepl-mcp_server.mcp_server.tools.nrepl-disconnect :as nrepl-disconnect]
            [nrepl-mcp_server.mcp_server.tools.nrepl-status :as nrepl-status]))

;; =============================================================================
;; Tool Registry
;; =============================================================================

(def tool-registry
  "Registry of all available MCP tools with metadata and implementations"
  {"debug-eval" {:handler debug-eval/handle
                 :metadata {:description "Execute Clojure code within the MCP server runtime"
                            :inputSchema {:type "object"
                                          :properties {:code {:type "string"
                                                              :description "Clojure code to evaluate"}}
                                          :required ["code"]}}}

   "debug-load-file" {:handler debug-load-file/handle
                      :metadata {:description "Load and evaluate a Clojure file in the MCP server runtime"
                                 :inputSchema {:type "object"
                                               :properties {:file-path {:type "string"
                                                                        :description "Path to Clojure file to load"}}
                                               :required ["file-path"]}}}

   "nrepl-connect" {:handler nrepl-connect/handle
                    :metadata {:description "Connect to an nREPL server"
                               :inputSchema {:type "object"
                                             :properties {:connection {:type "string"
                                                                       :description "Connection info: host:port, port, or file path"}
                                                          :timeout {:type "integer"
                                                                    :description "Connection timeout in milliseconds (default 5000)"}}
                                             :required ["connection"]}}}

   "nrepl-disconnect" {:handler nrepl-disconnect/handle
                       :metadata {:description "Disconnect from nREPL server"
                                  :inputSchema {:type "object"
                                                :properties {:timeout {:type "integer"
                                                                       :description "Disconnect timeout in milliseconds (default 5000)"}}
                                                :required []}}}

   "nrepl-status" {:handler nrepl-status/handle
                   :metadata {:description "Get nREPL connection status"
                              :inputSchema {:type "object"
                                            :properties {}
                                            :required []}}}})

;; =============================================================================
;; Dispatch Functions
;; =============================================================================

(defn list-tools
  "List all available MCP tools with their metadata"
  []
  {:tools (mapv (fn [[name {:keys [metadata]}]]
                  (assoc metadata :name name))
                tool-registry)})

(defn call-tool
  "Execute an MCP tool by name"
  [tool-name args]
  ;; Backward compatibility: map nrepl-server to specific operations
  (if (= tool-name "nrepl-server")
    (case (:op args)
      "connect" ((:handler (get tool-registry "nrepl-connect")) args)
      "disconnect" ((:handler (get tool-registry "nrepl-disconnect")) args)
      "status" ((:handler (get tool-registry "nrepl-status")) args)
      ;; Unknown operation
      {:content [{:type "text"
                  :text (json/generate-string
                         {:status "error"
                          :error (str "Unknown operation: " (:op args)
                                      ". Use 'connect', 'disconnect', or 'status'")}
                         {:pretty true})}]
       :isError true})
    ;; Not nrepl-server, use normal dispatch
    (if-let [{:keys [handler]} (get tool-registry tool-name)]
      (handler args)
      {:content [{:type "text" :text (str "❌ Unknown tool: " tool-name)}]
       :isError true})))

(defn get-available-tools
  "Get list of available tool names"
  []
  (keys tool-registry))