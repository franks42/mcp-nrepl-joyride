(ns nrepl-mcp_server.mcp_server.dispatch
  "MCP tool routing and dispatch table"
  (:require [nrepl-mcp_server.mcp_server.tools.debug-eval :as debug-eval]
            [nrepl-mcp_server.mcp_server.tools.debug-load-file :as debug-load-file]
            [nrepl-mcp_server.mcp_server.tools.nrepl-server :as nrepl-server]))

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

   "nrepl-server" {:handler nrepl-server/handle
                   :metadata {:description "nREPL server operations: connect, disconnect, status"
                              :inputSchema {:type "object"
                                            :properties {:op {:type "string"
                                                              :description "Operation: 'connect', 'disconnect', or 'status'"
                                                              :enum ["connect" "disconnect" "status"]}
                                                         :connection {:type "string"
                                                                      :description "Connection info for connect: host:port, port, or file path"}
                                                         :timeout {:type "integer"
                                                                   :description "Timeout in milliseconds (default 5000)"}}
                                            :required ["op"]}}}})

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
  (if-let [{:keys [handler]} (get tool-registry tool-name)]
    (handler args)
    {:content [{:type "text" :text (str "❌ Unknown tool: " tool-name)}]
     :isError true}))

(defn get-available-tools
  "Get list of available tool names"
  []
  (keys tool-registry))