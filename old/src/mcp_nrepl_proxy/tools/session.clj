(ns mcp-nrepl-proxy.tools.session
  "MCP tools for nREPL session and connection management"
  (:require [cheshire.core :as json]
            [mcp-nrepl-proxy.nrepl-client :as nrepl]
            [mcp-nrepl-proxy.utils :as utils]))

(defn tool-nrepl-connect
  "Connect to nREPL server - explicit connection only (no auto-discovery)"
  [state connect-to-nrepl {:keys [host port]}]
  (let [host (or host "localhost")]
    (if port
      (let [result (connect-to-nrepl host port)]
        (if (:success result)
          {:content [{:type "text"
                      :text (str "✅ Connected to nREPL at " host ":" port)}]}
          {:content [{:type "text"
                      :text (str "❌ Connection failed: " (:error result))}]
           :isError true}))
      {:content [{:type "text"
                  :text "❌ Port is required. Use nrepl-connect({\"port\": YOUR_PORT})"}]
       :isError true})))

(defn tool-nrepl-status
  "Get nREPL connection and session status with health information"
  [state _args]
  (let [conn (:nrepl-conn @state)
        sessions (:sessions @state)
        health (:health-status @state)
        last-test (:last-test-results health)]
    {:content [{:type "text"
                :text (json/generate-string
                       {:connected (some? conn)
                        :host (when conn (:host conn))
                        :port (when conn (:port conn))
                        :workspace (get-in @state [:config :workspace])
                        :sessions (count sessions)
                        :recent-commands (count (:recent-commands @state))
                        :health {:heartbeat-connected (:connected health)
                                 :last-heartbeat (:last-heartbeat health)
                                 :heartbeat-failures (:heartbeat-failures health)
                                 :last-test-passed (when last-test (:all-passed last-test))
                                 :last-test-timestamp (when last-test (:timestamp last-test))}}
                       {:pretty true})}]}))

(defn tool-nrepl-new-session
  "Create new nREPL session"
  [state ensure-nrepl-connection _args]
  (let [conn-result (ensure-nrepl-connection)]
    (if (:success conn-result)
      (try
        (let [conn (:connection conn-result)
              response (nrepl/create-session conn)
              session-id (:new-session response)]
          (if session-id
            (do
              (swap! state assoc-in [:sessions session-id] {:created (java.time.Instant/now)})
              {:content [{:type "text"
                          :text (json/generate-string {:new-session session-id} {:pretty true})}]})
            {:content [{:type "text"
                        :text "❌ Failed to create session"}]
             :isError true}))
        (catch Exception e
          (utils/log state :error "Session creation failed:" (.getMessage e))
          {:content [{:type "text"
                      :text (str "❌ Session creation failed: " (.getMessage e))}]
           :isError true}))
      {:content [{:type "text"
                  :text (str "❌ No nREPL connection: " (:error conn-result))}]
       :isError true})))