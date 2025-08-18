(ns nrepl-mcp-server.mcp-server.tools.nrepl-send-message
  "Synchronous nREPL message sending tool for MCP - Phase 2b.5 (SYNC WRAPPER)"
  (:require [nrepl-mcp-server.state.messages :as msg-state]
            [nrepl-mcp-server.state.results :as results]
            [nrepl-mcp-server.state.connection :as conn]
            [cheshire.core :as json]
            [nrepl-mcp-server.state.tool-registry :as registry]))

;; =============================================================================
;; Main Handler
;; =============================================================================

(defn handle
  "Send an nREPL message synchronously.
   Combines send-message-async + get-result-async with configurable timeout."
  [{:keys [message timeout-ms]
    :or {timeout-ms 30000}}] ; Default 30 second timeout

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
    ;; Step 1: Send message asynchronously using native function
    (if-let [message-id (msg-state/enqueue-message! message)]
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

;; =============================================================================
;; Self Registration
;; =============================================================================

(registry/register-tool!
 "nrepl-send-message"
 handle
 {:description "Send an nREPL message synchronously (blocks until response received)"
  :inputSchema {:type "object"
                :properties {:message {:type "object"
                                       :description "nREPL message to send (e.g. {:op \"eval\" :code \"...\"})"
                                       :additionalProperties true}
                             :timeout-ms {:type "integer"
                                          :description "Timeout in milliseconds (default: 30000)"
                                          :minimum 1000
                                          :maximum 300000}}
                :required ["message"]}})