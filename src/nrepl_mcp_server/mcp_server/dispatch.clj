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

(defn validate-tool-response
  "Validate tool response has required MCP structure"
  [response]
  (cond
    (not (map? response))
    {:content [{:type "text" :text "❌ Tool returned invalid response - not a map"}]
     :isError true}
    
    (not (contains? response :content))
    {:content [{:type "text" :text "❌ Tool response missing required 'content' field"}]
     :isError true}
    
    (not (vector? (:content response)))
    {:content [{:type "text" :text "❌ Tool response 'content' must be a vector"}]
     :isError true}
    
    :else response))

(defn call-tool
  "Execute an MCP tool by name"
  [tool-name args]
  (if-let [{:keys [handler]} (registry/get-tool tool-name)]
    (try
      (let [result (handler args)]
        (validate-tool-response result))
      (catch Exception e
        {:content [{:type "text" :text (str "❌ Tool execution failed: " (.getMessage e))}]
         :isError true}))
    {:content [{:type "text" :text (str "❌ Unknown tool: " tool-name)}]
     :isError true}))

(defn get-available-tools
  "Get list of available tool names"
  []
  (registry/list-tool-names))