(ns mcp-nrepl-proxy.protocol
  "MCP protocol handling for nREPL bridge.
   
   This namespace contains MCP protocol request/response handling including:
   - Request routing and method dispatch
   - Tool execution and response formatting  
   - Resource management and content serving
   - Initialize and capability negotiation
   - Error handling and JSON-RPC compliance"
  (:require [cheshire.core :as json]
            [mcp-nrepl-proxy.utils :as utils]
            [mcp-nrepl-proxy.config :as config]))

;; ============================================================================
;; Tool Execution
;; ============================================================================

(defn- call-tool
  "Execute an MCP tool by name.
   
   Args:
     tool-call-fn - Function to handle tool calls (passed from core)
     tool-name - Name of the tool to execute
     args - Arguments for the tool
     
   Returns:
     Tool execution result or error response"
  [tool-call-fn tool-name args]
  (tool-call-fn tool-name args))

;; ============================================================================
;; MCP Protocol Handlers
;; ============================================================================

(defn handle-initialize
  "Handle MCP initialize request.
   
   Args:
     request - MCP initialize request
     
   Returns:
     MCP initialize response with server capabilities"
  [request]
  {:jsonrpc "2.0"
   :id (or (:id request) (str (System/currentTimeMillis)))
   :result {:protocolVersion config/protocol-version
            :capabilities {:tools {}
                           :resources {}}
            :serverInfo {:name config/server-name
                         :version config/server-version
                         :description "Babashka MCP server bridging Claude Code with Joyride nREPL"}}})

(defn handle-list-tools
  "Handle MCP tools/list request.
   
   Args:
     request - MCP tools/list request
     
   Returns:
     MCP response with available tool definitions"
  [request]
  {:jsonrpc "2.0"
   :id (or (:id request) (str (System/currentTimeMillis)))
   :result {:tools config/tool-definitions}})

(defn handle-call-tool
  "Handle MCP tools/call request.
   
   Args:
     state - State atom reference
     tool-call-fn - Function to handle actual tool execution
     request - MCP tools/call request
     
   Returns:
     MCP response with tool execution result or error"
  [state tool-call-fn request]
  (try
    (let [tool-name (get-in request [:params :name])
          args (get-in request [:params :arguments] {})]
      (utils/log state :debug "Calling tool:" tool-name "with args:" args)
      (let [result (call-tool tool-call-fn tool-name args)]
        {:jsonrpc "2.0"
         :id (or (:id request) (str (System/currentTimeMillis)))
         :result result}))
    (catch Exception e
      (utils/log state :error "Tool call failed:" (.getMessage e))
      {:jsonrpc "2.0"
       :id (or (:id request) (str (System/currentTimeMillis)))
       :error {:code -32603
               :message "Internal error"
               :data {:error (.getMessage e)}}})))

(defn handle-list-resources
  "Handle MCP resources/list request.
   
   Args:
     state - State atom reference
     request - MCP resources/list request
     
   Returns:
     MCP response with available resource list"
  [state request]
  (let [commands (:recent-commands @state)]
    {:jsonrpc "2.0"
     :id (or (:id request) (str (System/currentTimeMillis)))
     :result {:resources (map-indexed
                          (fn [idx cmd]
                            {:uri (str "nrepl://commands/" idx)
                             :name (str "Command: " (subs (:code cmd) 0 (min 50 (count (:code cmd)))))
                             :description (str "Executed at " (:timestamp cmd))
                             :mimeType "application/json"})
                          commands)}}))

(defn handle-read-resource
  "Handle MCP resources/read request.
   
   Args:
     state - State atom reference
     request - MCP resources/read request
     
   Returns:
     MCP response with resource content or error"
  [state request]
  (let [uri (:uri (:params request))
        commands (:recent-commands @state)]
    (if-let [match (re-matches #"nrepl://commands/(\d+)" uri)]
      (let [idx (Integer/parseInt (second match))]
        (if (< idx (count commands))
          {:jsonrpc "2.0"
           :id (or (:id request) (str (System/currentTimeMillis)))
           :result {:contents [{:uri uri
                                :mimeType "application/json"
                                :text (json/generate-string (nth commands idx) {:pretty true})}]}}
          {:jsonrpc "2.0"
           :id (or (:id request) (str (System/currentTimeMillis)))
           :error {:code -32602
                   :message "Resource not found"}}))
      {:jsonrpc "2.0"
       :id (or (:id request) (str (System/currentTimeMillis)))
       :error {:code -32602
               :message "Invalid resource URI"}})))

;; ============================================================================
;; Request Router
;; ============================================================================

(defn handle-request
  "Route MCP requests to appropriate handlers.
   
   Args:
     state - State atom reference
     tool-call-fn - Function to handle tool execution  
     request - MCP request to handle
     
   Returns:
     Appropriate MCP response based on request method"
  [state tool-call-fn request]
  (utils/log state :debug "Handling request:" (:method request))
  (case (:method request)
    "initialize" (handle-initialize request)
    "tools/list" (handle-list-tools request)
    "tools/call" (handle-call-tool state tool-call-fn request)
    "resources/list" (handle-list-resources state request)
    "resources/read" (handle-read-resource state request)
    ;; Unknown method
    {:jsonrpc "2.0"
     :id (or (:id request) (str (System/currentTimeMillis)))
     :error {:code -32601
             :message "Method not found"}}))