(ns nrepl-mcp_server.state.register-tools
  "Tool registration orchestrator - manages explicit tool loading and registration
   
   This namespace is responsible for:
   1. Explicitly requiring all tool namespaces (causing self-registration)  
   2. Providing a function to make the registration side effects explicit
   3. Being the single point of truth for which tools are included"
  (:require
    ;; Import the tool registry for access
   [nrepl-mcp_server.state.tool-registry :as registry]

    ;; Explicitly require all tool namespaces
    ;; Each will self-register when loaded (side effect)
   [nrepl-mcp_server.mcp_server.tools.debug-eval]
   [nrepl-mcp_server.mcp_server.tools.debug-load-file]
   [nrepl-mcp_server.mcp_server.tools.nrepl-server]))

;; =============================================================================
;; Registration Orchestration  
;; =============================================================================

(defn register-tools!
  "Explicitly trigger tool registration by requiring tool namespaces
   
   This function doesn't need to do much work since the real registration
   happens as a side effect of requiring the tool namespaces above.
   
   However, calling this function makes the registration explicit and intentional
   rather than just relying on implicit require side effects.
   
   Returns: Number of registered tools"
  []
  (let [tool-count (registry/registry-size)]
    ;; Log registration for debugging
    (binding [*out* *err*]
      (println (str "🔧 Registered " tool-count " MCP tools: "
                    (vec (registry/list-tool-names)))))
    tool-count))

(defn get-registry-status
  "Get current registry status for debugging
   Returns: {:tool-count N :tool-names [...]}"
  []
  {:tool-count (registry/registry-size)
   :tool-names (vec (registry/list-tool-names))})