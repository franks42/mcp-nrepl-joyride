(ns nrepl-mcp-server.mcp-server.tools.nrepl-send-message
  "Synchronous nREPL message sending tool for MCP - Phase 2b.5 (SYNC WRAPPER)"
  (:require [nrepl-mcp-server.mcp-server.tools.tool-delegation :as delegate]
            [cheshire.core :as json]))

;; =============================================================================
;; Main Handler
;; =============================================================================

(defn handle
  "Send an nREPL message synchronously with connection selection and timeout recovery.
   Clean sync wrapper around nrepl-send-message-async + nrepl-get-result-async.
   
   nrepl-operations-map:
   Common nREPL operations you can send via the 'message' parameter:
   
   Session Management:
   - Create session:    {\"op\": \"clone\"}
   - Close session:     {\"op\": \"close\", \"session\": \"session-id\"}
   
   Code Operations:
   - Evaluate code:     {\"op\": \"eval\", \"code\": \"(+ 1 2 3)\"}
   - Load file:         {\"op\": \"load-file\", \"file\": \"file-content\", \"file-path\": \"/path/to/file.clj\"}
   
   Introspection:
   - Server info:       {\"op\": \"describe\"}
   - Symbol docs:       {\"op\": \"info\", \"symbol\": \"map\"}
   - Symbol source:     {\"op\": \"info\", \"symbol\": \"defn\"} 
   - Completions:       {\"op\": \"completions\", \"prefix\": \"ma\"}
   - Find symbols:      {\"op\": \"apropos\", \"query\": \"string\"}
   
   Control Operations:
   - Interrupt eval:    {\"op\": \"interrupt\"}
   - Get stacktrace:    {\"op\": \"stacktrace\"}
   
   Advanced:
   - List sessions:     {\"op\": \"ls-sessions\"}
   - Clone session:     {\"op\": \"clone\", \"session\": \"existing-session-id\"}
   - Require namespace: {\"op\": \"eval\", \"code\": \"(require 'clojure.string)\"}
   
   Timeout Recovery:
   - On timeout:        Returns timeout error with message-id
   - Retry with:        {\"message-id\": \"uuid-v7\", \"timeout-ms\": 60000}
   - Recovery skips:    Send phase, only checks for delayed result
   
   Optional parameters for most ops: \"session\", \"ns\", \"id\"
   See: https://nrepl.org/nrepl/1.1/ops.html"
  [{:keys [message timeout-ms connection message-id]
    :or {timeout-ms 30000}}] ; Default 30 second timeout

  (cond
    ;; Recovery path - check for delayed result
    message-id
    (delegate/call-async-tool "nrepl-get-result-async"
                              {:message-id message-id :timeout timeout-ms})

    ;; Validation - message required for normal path
    (empty? message)
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "error"
                        :operation "nrepl-send-message"
                        :error "No message provided"}
                       {:pretty true})}]
     :isError true}

    ;; Normal path - send then wait
    :else
    (let [send-result (delegate/call-async-tool "nrepl-send-message-async"
                                                {:message message :connection connection})]
      (if (delegate/is-success-result? send-result)
        (let [msg-id (delegate/extract-result-data send-result :message-id)]
          (delegate/call-async-tool "nrepl-get-result-async"
                                    {:message-id msg-id :timeout timeout-ms}))
        send-result)))) ; Propagate send error

(def tool-name "nrepl-send-message")

(def metadata
  {:description "Send any nREPL operation synchronously with connection selection and timeout recovery. Supports eval, info, completions, sessions, etc. Use message-id parameter to recover delayed results after timeout. See docstring for nrepl-operations-map with examples."
   :inputSchema {:type "object"
                 :properties {:message {:type "object"
                                        :description "nREPL message map. Examples: {\"op\":\"eval\",\"code\":\"(+ 1 2 3)\"}, {\"op\":\"info\",\"symbol\":\"map\"}, {\"op\":\"completions\",\"prefix\":\"ma\"}"
                                        :additionalProperties true}
                              :connection {:type "string"
                                           :description "Connection identifier (nickname, connection-id, or host:port). Optional - uses single connection if not specified."}
                              :message-id {:type "string"
                                           :description "Message ID for timeout recovery. Use this to check for delayed results after timeout. Optional - omit for normal send+wait operation."}
                              :timeout-ms {:type "integer"
                                           :description "Timeout in milliseconds (default: 30000)"
                                           :minimum 1000
                                           :maximum 300000}}
                 :required []}})

