(ns mcp-server.mcp
  "MCP protocol handlers and routing"
  (:require [mcp-server.debug :as debug]
            [mcp-server.tools.nrepl :as nrepl]))

;; =============================================================================
;; MCP Protocol Handlers
;; =============================================================================

(defn handle-initialize
  "Handle MCP initialize request"
  [_params]
  {:capabilities {:tools {}}
   :serverInfo {:name "mcp-server"
                :version "0.1.0-minimal"}})

(defn handle-list-tools
  "List available MCP tools"
  []
  ;; NOTE: Tool metadata is defined here for MCP protocol compliance.
  ;; The dispatch table (mcp-tool-dispatch) handles tool execution.
  ;; Future enhancement: Could extract tool metadata to make this DRY.
  {:tools [{:name "debug-eval"
            :description "Execute Clojure code within the MCP server runtime"
            :inputSchema {:type "object"
                          :properties {:code {:type "string"
                                              :description "Clojure code to evaluate"}}
                          :required ["code"]}}
           {:name "debug-load-file"
            :description "Load and evaluate a Clojure file in the MCP server runtime"
            :inputSchema {:type "object"
                          :properties {:file-path {:type "string"
                                                   :description "Path to Clojure file to load"}}
                          :required ["file-path"]}}
           {:name "nrepl-server"
            :description "Manage nREPL server connection"
            :inputSchema {:type "object"
                          :properties {:op {:type "string"
                                            :description "Operation: connect, disconnect, or status"
                                            :enum ["connect" "disconnect" "status"]}
                                       :connection {:type "string"
                                                    :description "Connection info: host:port, port, or file path (for connect)"}
                                       :timeout {:type "integer"
                                                 :description "Operation timeout in milliseconds (default 5000)"}}
                          :required ["op"]}}]})

;; =============================================================================
;; Tool Routing/Dispatch
;; =============================================================================

(def mcp-tool-dispatch
  "Dispatch table mapping MCP tool names to their implementation functions"
  {"debug-eval" debug/debug-eval
   "debug-load-file" debug/debug-load-file
   "nrepl-server" nrepl/nrepl-server})

(defn get-available-tools
  "Get list of available tool names from dispatch table"
  []
  (keys mcp-tool-dispatch))

(defn call-tool
  "Execute an MCP tool by name using the dispatch table"
  [tool-name args]
  (if-let [tool-fn (get mcp-tool-dispatch tool-name)]
    (tool-fn args)
    {:content [{:type "text" :text (str "❌ Unknown tool: " tool-name)}]
     :isError true}))

;; =============================================================================
;; MCP Request Handling
;; =============================================================================

(defn handle-request
  "Handle incoming MCP requests"
  [request]
  (try
    (let [{:keys [method params]} request]
      (case method
        "initialize" (handle-initialize params)
        "tools/list" (handle-list-tools)
        "tools/call" (let [{:keys [name arguments]} params]
                       (call-tool name arguments))
        {:error {:code -32601
                 :message (str "Method not found: " method)}}))
    (catch Exception e
      {:error {:code -32603
               :message "Internal error"
               :data {:exception (.getMessage e)}}})))