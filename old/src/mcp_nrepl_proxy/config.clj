(ns mcp-nrepl-proxy.config
  "Configuration and tool definitions for MCP-nREPL bridge.
   
   This namespace contains all static configuration including:
   - Tool definitions with names and descriptions
   - Resource definitions
   - Protocol versions and constants
   - Default settings")

;; ============================================================================
;; Protocol Configuration
;; ============================================================================

(def protocol-version "2024-11-05")
(def server-name "mcp-nrepl-proxy")
(def server-version "1.3.0")
(def server-description "Babashka MCP server bridging Claude Desktop with nREPL servers")

;; ============================================================================
;; Tool Definitions
;; ============================================================================

(def tool-definitions
  "MCP tool definitions for nREPL operations.
   Each tool has a name, description, and input schema."
  [{:name "nrepl-connect"
    :description "EXPLICIT CONNECTION: Connect to an nREPL server by specifying the exact port. No auto-discovery - you must provide the port explicitly. Read the port from server's port file (e.g., .test-nrepl-server-port, .bb-nrepl-server-port). RETURNS: Success message with connection details or error message."
    :inputSchema {:type "object"
                  :properties {:port {:type "number"
                                      :description "nREPL server port (required)"}}
                  :required ["port"]}}

   {:name "nrepl-eval"
    :description "PRIMARY TOOL: Execute any Clojure code in the connected nREPL session. Use this for: running calculations, calling VS Code functions, defining variables, requiring namespaces, or any Clojure expression. For VS Code automation, use expressions like (vscode/window.showInformationMessage \"Hello\"). RETURNS: Evaluation result (numbers, strings, data structures) or error details with stack trace."
    :inputSchema {:type "object"
                  :properties {:code {:type "string"
                                      :description "Clojure code to evaluate"}
                               :session {:type "string"
                                         :description "Optional session ID for isolated execution"}
                               :ns {:type "string"
                                    :description "Optional namespace for evaluation"}}
                  :required ["code"]}}

   {:name "nrepl-status"
    :description "DIAGNOSTIC: Check if nREPL connection is active and get current session information. Use this to verify connection before other operations or when troubleshooting. RETURNS: Connection status, active sessions list, server info, and recent command history."
    :inputSchema {:type "object"
                  :properties {}}}

   {:name "nrepl-new-session"
    :description "SESSION MANAGEMENT: Create isolated evaluation context for complex workflows. Use when you need variable isolation or want to run parallel evaluations without interference. Each session maintains separate namespace and variable state. RETURNS: New session ID string for use in other function calls."
    :inputSchema {:type "object"
                  :properties {}}}

   {:name "nrepl-test"
    :description "QUICK VALIDATION: Run basic nREPL functionality tests to verify the connection works properly. Use this after connecting or when experiencing issues. Tests basic evaluation, session management, and server communication. RETURNS: Test results summary with pass/fail status for each test."
    :inputSchema {:type "object"
                  :properties {}}}

   {:name "nrepl-health-check"
    :description "COMPREHENSIVE DIAGNOSTICS: Run detailed system health analysis across 6 categories - environment, connectivity, functionality, integration, performance, and configuration. Use this for troubleshooting, performance analysis, or when starting work in a new environment. Essential first step for AI assistants. TIP: If no nREPL server is connected, start the built-in Babashka nREPL server first with babashka-nrepl({op: 'start'}) for testing. RETURNS: Color-coded diagnostic report with detailed status, timing, and recommendations."
    :inputSchema {:type "object"
                  :properties {:verbose {:type "boolean"
                                         :description "Include detailed diagnostic output"}
                               :include-performance {:type "boolean"
                                                     :description "Run performance benchmarks (slower)"}}}}

   {:name "nrepl-load-file"
    :description "FILE OPERATIONS: Load and evaluate a complete Clojure source file into the session. Use this to load utility functions, configuration, or library code from files. The file content is evaluated as if typed directly. Essential for loading reusable code modules. RETURNS: Success confirmation with namespace info or detailed error with line numbers."
    :inputSchema {:type "object"
                  :properties {:file-path {:type "string"
                                           :description "Path to Clojure source file"}
                               :session {:type "string"
                                         :description "Optional session ID"}
                               :ns {:type "string"
                                    :description "Optional namespace"}}
                  :required ["file-path"]}}

   {:name "nrepl-doc"
    :description "SYMBOL DOCUMENTATION: Get detailed documentation for any Clojure symbol or function. Use this to understand function parameters, usage examples, and behavior before using unfamiliar functions. Works with built-in functions (map, reduce), your own functions, and VS Code API functions. RETURNS: Formatted documentation with parameters, description, and examples, or 'No documentation found' message."
    :inputSchema {:type "object"
                  :properties {:symbol {:type "string"
                                        :description "Symbol to get documentation for"}
                               :session {:type "string"
                                         :description "Optional session ID"}
                               :ns {:type "string"
                                    :description "Optional namespace context"}}
                  :required ["symbol"]}}

   {:name "nrepl-source"
    :description "SOURCE CODE INSPECTION: View the actual source code implementation of Clojure functions. Use this to understand how functions work internally, learn implementation patterns, or debug issues. Particularly useful for exploring custom functions and macros. RETURNS: Source code with line numbers and file location, or 'Source not found' for built-in functions."
    :inputSchema {:type "object"
                  :properties {:symbol {:type "string"
                                        :description "Symbol to get source code for"}
                               :session {:type "string"
                                         :description "Optional session ID"}
                               :ns {:type "string"
                                    :description "Optional namespace context"}}
                  :required ["symbol"]}}

   {:name "nrepl-complete"
    :description "AUTO-COMPLETION: Get available symbol completions for partial input. Use this when you know part of a function name and want to see all possible completions. Helpful for discovering VS Code API functions, exploring namespaces, or finding the right function name. Essential for interactive development. RETURNS: List of matching symbols with brief descriptions or empty list if no matches."
    :inputSchema {:type "object"
                  :properties {:prefix {:type "string"
                                        :description "Symbol prefix to complete"}
                               :session {:type "string"
                                         :description "Optional session ID"}
                               :ns {:type "string"
                                    :description "Optional namespace"}
                               :context {:type "string"
                                         :description "Optional context for smarter completions"}}
                  :required ["prefix"]}}

   {:name "nrepl-apropos"
    :description "SYMBOL DISCOVERY: Search for functions and symbols by name pattern or keywords. Use this when you don't know exact function names but remember part of the name or functionality. Great for exploring available functions, finding utilities, or rediscovering forgotten function names. RETURNS: List of matching symbols with their namespaces and brief descriptions."
    :inputSchema {:type "object"
                  :properties {:query {:type "string"
                                       :description "Search pattern or keyword"}
                               :session {:type "string"
                                         :description "Optional session ID"}
                               :ns {:type "string"
                                    :description "Optional namespace to search in"}
                               :search-ns {:type "string"
                                           :description "Specific namespace to search"}
                               :privates? {:type "boolean"
                                           :description "Include private vars"}
                               :case-sensitive? {:type "boolean"
                                                 :description "Case-sensitive search"}}
                  :required ["query"]}}

   {:name "nrepl-require"
    :description "NAMESPACE MANAGEMENT: Load additional Clojure namespaces and libraries into the current session. Use this to access external functions, load utility libraries, or import VS Code-specific namespaces. Essential for working with modular code and accessing extended functionality. RETURNS: Success confirmation or detailed error if namespace not found."
    :inputSchema {:type "object"
                  :properties {:namespace {:type "string"
                                           :description "Namespace to require"}
                               :as {:type "string"
                                    :description "Optional alias for namespace"}
                               :refer {:type "array"
                                       :items {:type "string"}
                                       :description "Symbols to refer from namespace"}
                               :reload {:type "boolean"
                                        :description "Force reload of namespace"}
                               :session {:type "string"
                                         :description "Optional session ID"}}
                  :required ["namespace"]}}

   {:name "nrepl-interrupt"
    :description "EMERGENCY STOP: Interrupt long-running or stuck evaluations. Use this when code is taking too long, appears frozen, or you need to stop an infinite loop. Essential safety tool for interactive development. Does not affect the session state or variables. RETURNS: Confirmation that interrupt signal was sent."
    :inputSchema {:type "object"
                  :properties {:session {:type "string"
                                         :description "Optional session to interrupt"}
                               :interrupt-id {:type "string"
                                              :description "Optional specific evaluation to interrupt"}}}}

   {:name "nrepl-stacktrace"
    :description "ERROR DEBUGGING: Get detailed error information and stack trace for the most recent exception. Use this immediately after an error occurs to understand what went wrong, where it happened, and how to fix it. Provides file locations, line numbers, and call chain. RETURNS: Formatted stack trace with error details and source locations."
    :inputSchema {:type "object"
                  :properties {:session {:type "string"
                                         :description "Optional session ID"}}}}

   {:name "nrepl-send-message-async"
    :description "🚀 ASYNC MESSAGE SENDING: Send an nREPL message asynchronously and return immediately with a message-id. This queues the message for background processing using the complete async transport layer with timeout handling. Use nrepl-get-result-async with the returned message-id to retrieve results. Perfect for long-running operations where you don't want to block. RETURNS: JSON with message-id, status (pending), and timeout-ms."
    :inputSchema {:type "object"
                  :properties {:message {:type "object"
                                         :description "nREPL message to send"}
                               :timeout-ms {:type "number"
                                            :description "Timeout in milliseconds (default: 30000)"}}
                  :required ["message"]}}

   {:name "nrepl-get-result-async"
    :description "📥 ASYNC RESULT RETRIEVAL: Get the result of an async message by message-id. This is the companion function to nrepl-send-message-async. Use the message-id returned by nrepl-send-message-async to check completion status and retrieve results. Supports polling pattern for long-running operations. RETURNS: JSON with status (pending/completed/failed/expired), message-id, and result data or error info."
    :inputSchema {:type "object"
                  :properties {:message-id {:type "string"
                                            :description "Message ID from send-message-async"}}
                  :required ["message-id"]}}

   {:name "get-mcp-nrepl-context"
    :description "MCP-nREPL BRIDGE CONTEXT: Get comprehensive information about the MCP-nREPL bridge environment, capabilities, and current state. Essential first tool for AI assistants to understand the working environment. RETURNS: Detailed context including server info, connection status, available operations, and usage guidance."
    :inputSchema {:type "object"
                  :properties {}}}

   {:name "babashka-nrepl"
    :description "BUILT-IN SERVER: Control the built-in Babashka nREPL server for testing and standalone operation. Operations: {op: 'start'} to start server, {op: 'stop'} to stop, {op: 'status'} to check. Use this when no external nREPL server is available. RETURNS: Operation result with server details."
    :inputSchema {:type "object"
                  :properties {:op {:type "string"
                                    :description "Operation: start, stop, or status"}}
                  :required ["op"]}}

   {:name "debug-eval"
    :description "DEBUG TOOL: Evaluate Clojure code within the MCP server runtime itself. This provides REPL-like access to the running server for introspection and debugging. Can inspect atoms, check queues, modify functions, reload namespaces. WARNING: Powerful tool - use with caution! Examples: '@mcp-nrepl-proxy.core/server-state', '(keys @mcp-nrepl-proxy.state/message-queues)'"
    :inputSchema {:type "object"
                  :properties {:code {:type "string"
                                      :description "Clojure code to evaluate in the server runtime"}}
                  :required ["code"]}}

   {:name "debug-load-file"
    :description "DEBUG TOOL: Load and evaluate a Clojure file in the MCP server runtime. This loads external debugging utilities and helper functions into the debug-eval environment. The file is evaluated in the server's SCI context, so defined functions persist for future debug-eval calls. Use to build debugging toolkits."
    :inputSchema {:type "object"
                  :properties {:file-path {:type "string"
                                           :description "Path to Clojure file to load and evaluate"}}
                  :required ["file-path"]}}])

;; ============================================================================
;; Resource Definitions
;; ============================================================================

(def resource-definitions
  "MCP resource definitions - currently empty but ready for future expansion."
  [])

;; ============================================================================
;; Default Settings
;; ============================================================================

(def default-timeout-ms 30000)
(def default-heartbeat-interval-ms 30000)
(def max-recent-commands 10)
(def max-message-queue-size 100)