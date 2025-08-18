(ns nrepl-mcp-server.mcp-server.tools.nrepl-send-message-async
  "Async nREPL message sending tool for MCP - Phase 2b.1 (READY-TO-SEND)"
  (:require [nrepl-mcp-server.state.messages :as msg-state]
            [cheshire.core :as json]
            [nrepl-mcp-server.state.tool-registry :as registry]))

;; =============================================================================
;; Main Handler
;; =============================================================================

(defn handle
  "Queue an nREPL message for async sending.
   Returns immediately with message-id for later retrieval."
  [{:keys [message]}]
  (if (empty? message)
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "error"
                        :operation "nrepl-send-message-async"
                        :error "No message provided"}
                       {:pretty true})}]
     :isError true}

    ;; Try to queue the message (includes connection check and formatting)
    (if-let [message-id (msg-state/enqueue-message! message)]
      ;; Success - message was queued with ready-to-send formatting
      {:content [{:type "text"
                  :text (json/generate-string
                         {:status "success"
                          :operation "nrepl-send-message-async"
                          :message-id message-id
                          :message "Message queued for sending (READY-TO-SEND format)"}
                         {:pretty true})}]}

      ;; Failed - connection or formatting problem
      {:content [{:type "text"
                  :text (json/generate-string
                         {:status "error"
                          :operation "nrepl-send-message-async"
                          :error "Failed to queue message - no connection or formatting error"}
                         {:pretty true})}]
       :isError true})))

;; =============================================================================
;; Self Registration
;; =============================================================================

(registry/register-tool!
 "nrepl-send-message-async"
 handle
 {:description "Queue an nREPL message for async sending"
  :inputSchema {:type "object"
                :properties {:message {:type "object"
                                       :description "nREPL message to send (e.g. {:op \"eval\" :code \"...\"})"
                                       :additionalProperties true}}
                :required ["message"]}})