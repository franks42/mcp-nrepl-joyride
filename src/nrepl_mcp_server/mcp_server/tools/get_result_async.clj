(ns nrepl-mcp-server.mcp-server.tools.get-result-async
  "Async result retrieval tool for MCP - Phase 2b.4"
  (:require [nrepl-mcp-server.state.results :as results]
            [nrepl-mcp-server.state.messages :as msg-state]
            [cheshire.core :as json]
            [nrepl-mcp-server.state.tool-registry :as registry]))

;; =============================================================================
;; Main Handler
;; =============================================================================

(defn handle
  "Retrieve the result of an async nREPL message.
   Waits if the result is not yet available."
  [{:keys [message-id timeout] :or {timeout 30000}}]
  (if (empty? message-id)
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "error"
                        :operation "get-result-async"
                        :error "No message-id provided"}
                       {:pretty true})}]
     :isError true}

    ;; Try to get the result
    (let [result (results/get-result message-id timeout)]
      (case (:status result)
        :success
        (do
          ;; Clean up the pending message
          (msg-state/remove-pending-message! message-id)
          {:content [{:type "text"
                      :text (json/generate-string
                             {:status "success"
                              :operation "get-result-async"
                              :message-id message-id
                              :result (:result result)}
                             {:pretty true})}]})

        :timeout
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "timeout"
                            :operation "get-result-async"
                            :message-id message-id
                            :timeout-ms timeout
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
                              :operation "get-result-async"
                              :message-id message-id
                              :error (:error result)}
                             {:pretty true})}]
           :isError true})

        ;; Unexpected status
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :operation "get-result-async"
                            :message-id message-id
                            :error (str "Unexpected result status: " (:status result))}
                           {:pretty true})}]
         :isError true}))))

;; =============================================================================
;; Self Registration
;; =============================================================================

(registry/register-tool!
 "get-result-async"
 handle
 {:description "Retrieve the result of an async nREPL message"
  :inputSchema {:type "object"
                :properties {:message-id {:type "string"
                                          :description "Message ID returned from send-message-async"}
                             :timeout {:type "integer"
                                       :description "Timeout in milliseconds (default 30000)"}}
                :required ["message-id"]}})