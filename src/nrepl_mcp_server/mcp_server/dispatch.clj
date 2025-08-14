(ns nrepl-mcp-server.mcp-server.dispatch
  "MCP tool routing and dispatch table"
  (:require [nrepl-mcp-server.state.tool-registry :as registry]
            [nrepl-mcp-server.state.register-tools :as register]))

;; =============================================================================
;; Tool Registry Initialization
;; =============================================================================

;; Initialize tool registry by triggering self-registration
;; This causes all tool namespaces to load and register themselves
(register/register-tools!)

;; =============================================================================
;; Dispatch Functions
;; =============================================================================

(defn list-tools
  "List all available MCP tools with their metadata"
  []
  {:tools (mapv (fn [[name {:keys [metadata]}]]
                  (assoc metadata :name name))
                (registry/get-registered-tools))})

(defn call-tool
  "Execute an MCP tool by name"
  [tool-name args]
  (if-let [{:keys [handler]} (registry/get-tool tool-name)]
    (handler args)
    {:content [{:type "text" :text (str "❌ Unknown tool: " tool-name)}]
     :isError true}))

(defn get-available-tools
  "Get list of available tool names"
  []
  (registry/list-tool-names))