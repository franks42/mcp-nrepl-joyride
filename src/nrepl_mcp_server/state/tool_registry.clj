(ns nrepl-mcp-server.state.tool-registry
  "Tool registry state management - pure data store for MCP tools")

;; =============================================================================
;; Tool Registry State
;; =============================================================================

(def tool-registry
  "Atom containing all registered MCP tools
   Format: {tool-name {:handler fn :metadata {...}}}"
  (atom {}))

;; =============================================================================
;; Registration Functions
;; =============================================================================

(defn register-tool!
  "Register a tool with its handler and metadata
   
   Args:
     tool-name   - String name of the tool (e.g., 'local-eval')
     handler     - Function that handles the tool invocation
     metadata    - Map containing tool description and JSON schema
   
   Example:
     (register-tool! 'local-eval' handle-fn {:description '...' :inputSchema {...}})"
  [tool-name handler metadata]
  (swap! tool-registry assoc tool-name {:handler handler :metadata metadata}))

(defn get-registered-tools
  "Get all registered tools as a map
   Returns: {tool-name {:handler fn :metadata {...}}}"
  []
  @tool-registry)

(defn get-tool
  "Get a specific tool by name
   Returns: {:handler fn :metadata {...}} or nil if not found"
  [tool-name]
  (get @tool-registry tool-name))

(defn list-tool-names
  "Get list of all registered tool names
   Returns: [tool-name1 tool-name2 ...]"
  []
  (keys @tool-registry))

(defn clear-registry!
  "Clear all registered tools (primarily for testing)
   Returns: empty map"
  []
  (reset! tool-registry {}))

(defn registry-size
  "Get number of registered tools
   Returns: integer count"
  []
  (count @tool-registry))