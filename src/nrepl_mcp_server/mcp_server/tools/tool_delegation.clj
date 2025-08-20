(ns nrepl-mcp-server.mcp-server.tools.tool-delegation
  "Tool delegation helper - enables calling MCP tools from within other tools"
  (:require [cheshire.core :as json]
            [clojure.string]))

;; =============================================================================
;; Tool Delegation Infrastructure
;; =============================================================================

(defn call-async-tool
  "Call another MCP tool and return its result - enables tool delegation.
   
   Example:
   (call-async-tool 'nrepl-send-message-async' {:message {:op 'eval' :code '(+ 1 2)'}})
   
   Returns the full MCP tool response with :content, :isError, etc."
  [tool-name args]
  (let [tool-ns (str "nrepl-mcp-server.mcp-server.tools." tool-name)
        handle-fn (resolve (symbol tool-ns "handle"))]
    (if handle-fn
      (handle-fn args)
      {:content [{:type "text"
                  :text (json/generate-string
                         {:status "error"
                          :operation "tool-delegation"
                          :error (str "Tool not found: " tool-name)
                          :tool-name tool-name
                          :namespace tool-ns}
                         {:pretty true})}]
       :isError true})))

(defn extract-result-status
  "Extract status from MCP tool result for decision making.
   
   Example:
   (extract-result-status tool-result) => 'success' | 'error' | 'timeout'"
  [tool-result]
  (try
    (let [text-content (get-in tool-result [:content 0 :text])
          parsed (if (string? text-content)
                   (json/parse-string text-content true)
                   text-content)]
      (:status parsed))
    (catch Exception _
      "error")))

(defn extract-result-data
  "Extract data field from successful MCP tool result.
   
   Example:
   (extract-result-data tool-result :message-id) => 'uuid-v7-string'
   (extract-result-data tool-result :result) => {...nrepl-response...}"
  [tool-result key]
  (try
    (let [text-content (get-in tool-result [:content 0 :text])
          parsed (if (string? text-content)
                   (json/parse-string text-content true)
                   text-content)]
      (get parsed key))
    (catch Exception _
      nil)))

(defn is-error-result?
  "Check if MCP tool result indicates an error condition."
  [tool-result]
  (or (:isError tool-result)
      (= (extract-result-status tool-result) "error")))

(defn is-success-result?
  "Check if MCP tool result indicates success."
  [tool-result]
  (and (not (:isError tool-result))
       (= (extract-result-status tool-result) "success")))