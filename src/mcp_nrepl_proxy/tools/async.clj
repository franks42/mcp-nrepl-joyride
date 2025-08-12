(ns mcp-nrepl-proxy.tools.async
  "MCP tools for async nREPL message processing"
  (:require [cheshire.core :as json]
            [mcp-nrepl-proxy.nrepl-client :as nrepl]
            [mcp-nrepl-proxy.utils :as utils]))

(defn tool-nrepl-send-message-async
  "Queue an nREPL message for async processing and return message-id immediately.
  
  This function implements the raw async interface described in the sync-async 
  queuing architecture. It puts the message on the send queue and returns 
  immediately with a message-id that can be used to fetch results later.
  
  Use nrepl-get-result-async with the returned message-id to get the result."
  [state ensure-nrepl-connection {:keys [message timeout-ms] :or {timeout-ms 30000}}]
  (let [conn-result (ensure-nrepl-connection)]
    (if (:success conn-result)
      (try
        (let [conn (:connection conn-result)
              async-result (nrepl/queue-message-async conn message :timeout-ms timeout-ms)]
          (utils/log state :debug "Queued async message:" (pr-str async-result))

          {:content [{:type "text"
                      :text (json/generate-string async-result {:pretty true})}]})
        (catch Exception e
          (utils/log state :error "Failed to queue async message:" (.getMessage e))
          {:content [{:type "text"
                      :text (str "❌ Failed to queue message: " (.getMessage e))}]
           :isError true}))
      {:content [{:type "text"
                  :text (str "❌ No nREPL connection: " (:error conn-result))}]
       :isError true})))

(defn tool-nrepl-get-result-async
  "Fetch the result of an async message by message-id.
  
  This is the companion function to nrepl-send-message-async. Use the message-id 
  returned by nrepl-send-message-async to fetch the result."
  [state ensure-nrepl-connection {:keys [message-id]}]
  (if message-id
    (try
      (let [result (nrepl/fetch-result message-id)]
        (utils/log state :debug "Fetched async result:" (pr-str result))

        (case (:status result)
          :completed
          {:content [{:type "text"
                      :text (json/generate-string
                             {:status "completed"
                              :message-id message-id
                              :result (:result result)}
                             {:pretty true})}]}

          :pending
          {:content [{:type "text"
                      :text (json/generate-string
                             {:status "pending"
                              :message-id message-id}
                             {:pretty true})}]}

          (:failed :expired)
          {:content [{:type "text"
                      :text (json/generate-string
                             {:status (:status result)
                              :message-id message-id
                              :error-info (:error-info result)}
                             {:pretty true})}]
           :isError true}

          :not-found
          {:content [{:type "text"
                      :text (str "❌ Message ID not found: " message-id)}]
           :isError true}))
      (catch Exception e
        (utils/log state :error "Failed to fetch async result:" (.getMessage e))
        {:content [{:type "text"
                    :text (str "❌ Failed to fetch result: " (.getMessage e))}]
         :isError true}))
    {:content [{:type "text"
                :text "❌ Message ID is required"}]
     :isError true}))