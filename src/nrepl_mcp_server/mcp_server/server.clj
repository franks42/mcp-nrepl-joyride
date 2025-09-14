(ns nrepl-mcp-server.mcp-server.server
  "MCP stdio server and JSON-RPC protocol handling"
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [nrepl-mcp-server.mcp-server.dispatch :as dispatch]
            [nrepl-mcp-server.state.watchers :as watchers]))

;; =============================================================================
;; stdio Transport Implementation
;; =============================================================================

(defn read-request
  "Read a JSON-RPC request from stdin"
  []
  (when-let [line (read-line)]
    (when-not (str/blank? line)
      (try
        (json/parse-string line true)
        (catch Exception e
          (binding [*out* *err*]
            (println "Failed to parse JSON:" (.getMessage e)))
          nil)))))

(defn send-response
  "Send a JSON-RPC response to stdout"
  [response]
  (println (json/generate-string response))
  (flush))

;; =============================================================================
;; MCP Protocol Handlers
;; =============================================================================

(defn handle-initialize
  "Handle MCP initialize request"
  [_params]
  {:protocolVersion "2024-11-05"
   :capabilities {:tools {}}
   :serverInfo {:name "nrepl-mcp-server"
                :version "0.2.0"}})

(defn handle-list-tools
  "List available MCP tools"
  []
  (dispatch/list-tools))

;; =============================================================================
;; MCP Request Handling
;; =============================================================================

(defn validate-request
  "Validate basic JSON-RPC request structure"
  [request]
  (cond
    (not (map? request))
    {:error {:code -32600 :message "Invalid Request - not a map"}}
    
    (not (contains? request :method))
    {:error {:code -32600 :message "Invalid Request - missing method"}}
    
    (not (string? (:method request)))
    {:error {:code -32600 :message "Invalid Request - method must be string"}}
    
    :else nil))

(defn handle-request
  "Handle incoming MCP requests"
  [request]
  (try
    ;; First validate the request structure
    (if-let [validation-error (validate-request request)]
      validation-error
      (let [{:keys [method params]} request]
        (case method
          "initialize" (handle-initialize params)
          "tools/list" (handle-list-tools)
          "tools/call" (let [{:keys [name arguments]} params]
                         ;; Validate tool call parameters
                         (if (and name (string? name))
                           (dispatch/call-tool name arguments)
                           {:error {:code -32602
                                    :message "Invalid params - tool name required and must be string"}}))
          {:error {:code -32601
                   :message (str "Method not found: " method)}})))
    (catch Exception e
      {:error {:code -32603
               :message "Internal error"
               :data {:exception (.getMessage e)}}})))

;; =============================================================================
;; Server Loop
;; =============================================================================

(defn stdio-server-loop
  "Main stdio server loop"
  []
  ;; Start watchers before entering the loop
  (watchers/start-all-watchers!)

  ;; Switch to user namespace for local-eval/local-load-file
  (in-ns 'user)

  ;; Set up shutdown hook to stop watchers
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. (fn []
                               (binding [*out* *err*]
                                 (println "[Server] Shutting down watchers..."))
                               (watchers/stop-all-watchers!))))

  (loop []
    (when-let [request (read-request)]
      (let [response (handle-request request)
            ;; Ensure ID is always a string or number, never null
            request-id (or (:id request) "unknown")
            json-response (cond
                            ;; If response has :error, format as error response
                            (:error response)
                            (assoc response :jsonrpc "2.0" :id request-id)

                            ;; Otherwise wrap in :result for success response
                            :else
                            {:jsonrpc "2.0"
                             :id request-id
                             :result response})]
        (send-response json-response))
      (recur))))