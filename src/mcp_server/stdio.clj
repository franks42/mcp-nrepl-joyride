(ns mcp-server.stdio
  "stdio transport for MCP protocol"
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [mcp-server.mcp :as mcp]))

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

(defn stdio-server-loop
  "Main stdio server loop"
  []
  (loop []
    (when-let [request (read-request)]
      (let [response (mcp/handle-request request)
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