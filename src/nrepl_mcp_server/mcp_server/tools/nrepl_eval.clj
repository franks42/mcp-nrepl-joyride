(ns nrepl-mcp-server.mcp-server.tools.nrepl-eval
  "Simple nREPL eval tool using async message queue - Phase 2b.6"
  (:require [nrepl-mcp-server.mcp-server.tools.nrepl-send-message :as smgr]
            [nrepl-mcp-server.mcp-server.tools.nrepl-get-result-async :as gra]
            [nrepl-mcp-server.state.connection :as conn]
            [cheshire.core :as json]))

;; =============================================================================
;; Main Handler
;; =============================================================================

(defn- format-timeout-response
  "Format a timeout response with recovery instructions"
  [code message-id timeout-ms]
  {:content [{:type "text"
              :text (json/generate-string
                     {:status "timeout"
                      :operation "nrepl-eval"
                      :code code
                      :timeout-ms timeout-ms
                      :message-id message-id
                      :error (str "Code evaluation timed out after " timeout-ms "ms")
                      :recovery "Call nrepl-eval again with same code and this message-id to check for delayed result"}
                     {:pretty true})}]
   :isError true})

(defn- format-nrepl-response
  "Format a successful nREPL response in nrepl-eval format"
  [result code]
  (let [response-text (-> result :content first :text)
        response-data (json/parse-string response-text true)
        nrepl-result (get-in response-data [:result :response])]

    ;; Check if we got an nREPL error
    (if (contains? nrepl-result :err)
      {:content [{:type "text"
                  :text (json/generate-string
                         {:status "error"
                          :operation "nrepl-eval"
                          :code code
                          :error (:err nrepl-result)
                          :ns (:ns nrepl-result)
                          :ex (:ex nrepl-result)}
                         {:pretty true})}]
       :isError true}

      ;; Success - return the eval result
      {:content [{:type "text"
                  :text (json/generate-string
                         (cond-> {:status "success"
                                  :operation "nrepl-eval"
                                  :code code
                                  :value (or (:value nrepl-result)
                                             (str nrepl-result))
                                  :ns (:ns nrepl-result)}
                           (:out nrepl-result) (assoc :out (:out nrepl-result))
                           (:err nrepl-result) (assoc :err (:err nrepl-result)))
                         {:pretty true})}]})))

(defn handle
  "Evaluate Clojure code via nREPL using the async message queue.
   Supports timeout recovery via message-id and connection selection."
  [{:keys [code message-id timeout connection] :or {timeout 30000}}]

  ;; Step 1: Resolve connection (Phase 1: returns single connection regardless)
  (try
    (let [_connection-id (conn/resolve-connection-id connection)]
      ;; Step 2: Use existing logic (unchanged in Phase 1)
      (cond
        ;; Validation: code is required
        (empty? code)
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :operation "nrepl-eval"
                            :error "No code provided"}
                           {:pretty true})}]
         :isError true}

        ;; Recovery path: check existing message for delayed result
        message-id
        (let [result (gra/handle {:message-id message-id :timeout timeout})]
          (if (:isError result)
            ;; Still timeout or error - return with same recovery info
            (let [response-text (-> result :content first :text)
                  response-data (json/parse-string response-text true)]
              (if (= "timeout" (:status response-data))
                (format-timeout-response code message-id timeout)
                ;; Other error - pass through but fix operation name
                {:content [{:type "text"
                            :text (json/generate-string
                                   (assoc response-data :operation "nrepl-eval")
                                   {:pretty true})}]
                 :isError true}))
            ;; Success - format as nrepl-eval response
            (format-nrepl-response result code)))

        ;; Normal path: send new message and wait for result
        :else
        (let [message {:op "eval" :code code}
              result (smgr/handle {:message message :timeout-ms timeout})]

          (if (:isError result)
            ;; Check if this is a timeout - if so, provide recovery info
            (let [response-text (-> result :content first :text)
                  response-data (json/parse-string response-text true)]
              (if (= "timeout" (:status response-data))
                ;; Extract message-id for recovery
                (if-let [msg-id (:message-id response-data)]
                  (format-timeout-response code msg-id timeout)
                  ;; Fallback if no message-id available
                  {:content [{:type "text"
                              :text (json/generate-string
                                     (assoc response-data :operation "nrepl-eval")
                                     {:pretty true})}]
                   :isError true})
                ;; Other error - pass through but fix operation name
                {:content [{:type "text"
                            :text (json/generate-string
                                   (assoc response-data :operation "nrepl-eval")
                                   {:pretty true})}]
                 :isError true}))

            ;; Success - format as nrepl-eval response
            (format-nrepl-response result code)))))

    ;; Step 3: Handle connection resolution errors
    (catch Exception e
      (let [error-data (ex-data e)]
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :operation "nrepl-eval"
                            :error (.getMessage e)
                            :error-type (or (:status error-data) :connection-error)}
                           {:pretty true})}]
         :isError true}))))

(def tool-name "nrepl-eval")

(def metadata
  {:description "Evaluate Clojure code via nREPL using async message queue with timeout recovery and connection selection"
   :inputSchema {:type "object"
                 :properties {:code {:type "string"
                                     :description "Clojure code to evaluate"}
                              :connection {:type "string"
                                           :description "Connection identifier (nickname, connection-id, or host:port). Optional - uses single connection if not specified."}
                              :timeout {:type "integer"
                                        :description "Timeout in milliseconds (default: 30000)"
                                        :minimum 1000
                                        :maximum 300000}
                              :message-id {:type "string"
                                           :description "Message ID for timeout recovery - call with same code and this ID to check for delayed result"}}
                 :required ["code"]}})

