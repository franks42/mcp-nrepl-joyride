(ns nrepl-mcp-server.mcp-server.tools.nrepl-send-message
  "Synchronous nREPL message sending tool for MCP - Phase 2b.5 (SYNC WRAPPER)"
  (:require [nrepl-mcp-server.state.messages :as msg-state]
            [nrepl-mcp-server.state.results :as results]
            [nrepl-mcp-server.state.connection :as conn]
            [cheshire.core :as json]))

;; =============================================================================
;; Main Handler
;; =============================================================================

(defn handle
  "Send an nREPL message synchronously with connection selection.
   Combines send-message-async + get-result-async with configurable timeout.
   
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
   
   Optional parameters for most ops: \"session\", \"ns\", \"id\"
   See: https://nrepl.org/nrepl/1.1/ops.html"
  [{:keys [message timeout-ms connection]
    :or {timeout-ms 30000}}] ; Default 30 second timeout

  ;; Step 1: Resolve connection (Phase 1: returns single connection regardless)
  (try
    (let [connection-id (conn/resolve-connection-id connection)]
      ;; Step 2: Use existing logic (unchanged in Phase 1)
      (cond
        ;; Validation: message is required
        (empty? message)
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :operation "nrepl-send-message"
                            :error "No message provided"}
                           {:pretty true})}]
         :isError true}

        ;; Check if we have an active nREPL connection
        (not (conn/connected?))
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :operation "nrepl-send-message"
                            :error "No nREPL connection available"
                            :hint "Connect to an nREPL server first using the nrepl-connection tool"
                            :example "Use: nrepl-connection with {\"op\": \"connect\", \"connection\": \"localhost:7890\"}"}
                           {:pretty true})}]
         :isError true}

    ;; Process the message
        :else
    ;; Step 1: Send message asynchronously for the specific connection
        (if-let [message-id (msg-state/enqueue-message! connection-id message)]
      ;; Step 2: Wait for result using native function
          (let [result (results/get-result message-id timeout-ms)]
            (case (:status result)
              :success
              (do
            ;; Clean up the pending message
                (msg-state/remove-pending-message! message-id)
                {:content [{:type "text"
                            :text (json/generate-string
                                   {:status "success"
                                    :operation "nrepl-send-message"
                                    :message-id message-id
                                    :timeout-ms timeout-ms
                                    :result (:result result)}
                                   {:pretty true})}]})

              :timeout
              {:content [{:type "text"
                          :text (json/generate-string
                                 {:status "timeout"
                                  :operation "nrepl-send-message"
                                  :message-id message-id
                                  :timeout-ms timeout-ms
                                  :error "Result not available within timeout"}
                                 {:pretty true})}]
               :isError true}

              :error
              (do
            ;; Clean up the pending message on error
                (msg-state/remove-pending-message! message-id)
                {:content [{:type "text"
                            :text (json/generate-string
                                   {:status "error"
                                    :operation "nrepl-send-message"
                                    :message-id message-id
                                    :error (:error result)}
                                   {:pretty true})}]
                 :isError true})

          ;; Unexpected status
              {:content [{:type "text"
                          :text (json/generate-string
                                 {:status "error"
                                  :operation "nrepl-send-message"
                                  :message-id message-id
                                  :error (str "Unexpected result status: " (:status result))}
                                 {:pretty true})}]
               :isError true}))

        ;; Failed to queue message
          {:content [{:type "text"
                      :text (json/generate-string
                             {:status "error"
                              :operation "nrepl-send-message"
                              :error "Failed to queue message - no connection or formatting error"}
                             {:pretty true})}]
           :isError true})))

    ;; Step 3: Handle connection resolution errors
    (catch Exception e
      (let [error-data (ex-data e)]
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :operation "nrepl-send-message"
                            :error (.getMessage e)
                            :error-type (or (:status error-data) :connection-error)}
                           {:pretty true})}]
         :isError true}))))

(def tool-name "nrepl-send-message")

(def metadata
  {:description "Send any nREPL operation synchronously with connection selection. Supports eval, info, completions, sessions, etc. See docstring for nrepl-operations-map with examples."
   :inputSchema {:type "object"
                 :properties {:message {:type "object"
                                        :description "nREPL message map. Examples: {\"op\":\"eval\",\"code\":\"(+ 1 2 3)\"}, {\"op\":\"info\",\"symbol\":\"map\"}, {\"op\":\"completions\",\"prefix\":\"ma\"}"
                                        :additionalProperties true}
                              :connection {:type "string"
                                           :description "Connection identifier (nickname, connection-id, or host:port). Optional - uses single connection if not specified."}
                              :timeout-ms {:type "integer"
                                           :description "Timeout in milliseconds (default: 30000)"
                                           :minimum 1000
                                           :maximum 300000}}
                 :required ["message"]}})

