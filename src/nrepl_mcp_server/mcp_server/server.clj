(ns nrepl-mcp_server.mcp_server.server
  "MCP stdio server and JSON-RPC protocol handling"
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [nrepl-mcp_server.mcp_server.dispatch :as dispatch]))

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
  {:capabilities {:tools {}}
   :serverInfo {:name "nrepl-mcp_server"
                :version "0.2.0"}})

(defn handle-list-tools
  "List available MCP tools"
  []
  (dispatch/list-tools))

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
                       (dispatch/call-tool name arguments))
        {:error {:code -32601
                 :message (str "Method not found: " method)}}))
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
  (loop []
    (when-let [request (read-request)]
      (let [response (handle-request request)
            json-response (cond
                            ;; If response has :error, format as error response
                            (:error response)
                            (assoc response :jsonrpc "2.0" :id (:id request))

                            ;; Otherwise wrap in :result for success response
                            :else
                            {:jsonrpc "2.0"
                             :id (:id request)
                             :result response})]
        (send-response json-response))
      (recur))))