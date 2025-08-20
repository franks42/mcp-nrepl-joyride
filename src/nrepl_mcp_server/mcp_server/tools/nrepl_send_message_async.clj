(ns nrepl-mcp-server.mcp-server.tools.nrepl-send-message-async
  "Async nREPL message sending tool for MCP - Phase 2b.1 (READY-TO-SEND)"
  (:require [nrepl-mcp-server.state.messages :as msg-state]
            [nrepl-mcp-server.state.connection :as conn]
            [cheshire.core :as json]))

;; =============================================================================
;; Main Handler
;; =============================================================================

(defn handle
  "Queue an nREPL message for async sending with connection selection.
   Returns immediately with message-id for later retrieval."
  [{:keys [message connection]}]

  ;; Step 1: Resolve connection (Phase 1: returns single connection regardless)
  (try
    (let [connection-id (conn/resolve-connection-id connection)]
      ;; Step 2: Use connection-id for per-connection queueing
      (if (empty? message)
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :operation "nrepl-send-message-async"
                            :error "No message provided"}
                           {:pretty true})}]
         :isError true}

        ;; Try to queue the message for the specific connection
        (if-let [message-id (msg-state/enqueue-message! connection-id message)]
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

    ;; Step 3: Handle connection resolution errors
    (catch Exception e
      (let [error-data (ex-data e)]
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :operation "nrepl-send-message-async"
                            :error (.getMessage e)
                            :error-type (or (:status error-data) :connection-error)}
                           {:pretty true})}]
         :isError true}))))

(def tool-name "nrepl-send-message-async")

(def metadata
  {:description "⚠️ INTERNAL TOOL: Low-level async message sending used internally by other tools. DO NOT USE DIRECTLY - use nrepl-eval for code evaluation or nrepl-send-message for other nREPL operations. This tool is part of the async infrastructure and requires manual result retrieval."
   :inputSchema {:type "object"
                 :properties {:message {:type "object"
                                        :description "nREPL message to send (e.g. {:op \"eval\" :code \"...\"})"
                                        :additionalProperties true}
                              :connection {:type "string"
                                           :description "Connection identifier (nickname, connection-id, or host:port). Optional - uses single connection if not specified."}}
                 :required ["message"]}})

