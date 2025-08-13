(ns mcp-nrepl-proxy.tools.control
  "MCP tools for runtime control and debugging"
  (:require [mcp-nrepl-proxy.nrepl-client :as nrepl]
            [mcp-nrepl-proxy.utils :as utils]))

(defn tool-nrepl-interrupt
  "Interrupt running evaluation"
  [state ensure-nrepl-connection {:keys [session interrupt-id]}]
  (let [conn-result (ensure-nrepl-connection)]
    (if (:success conn-result)
      (try
        (let [conn (:connection conn-result)
              result (nrepl/interrupt conn :session session :interrupt-id interrupt-id)]
          {:content [{:type "text"
                      :text (str "🛑 Interrupt signal sent"
                                 (when session (str " to session " session))
                                 (when interrupt-id (str " for evaluation " interrupt-id)))}]})
        (catch Exception e
          (utils/log state :error "Interrupt failed:" (.getMessage e))
          {:content [{:type "text"
                      :text (str "❌ Interrupt failed: " (.getMessage e))}]
           :isError true}))
      {:content [{:type "text"
                  :text "❌ No nREPL connection available. Use nrepl-connect first."}]
       :isError true})))

(defn tool-nrepl-stacktrace
  "Get stacktrace for the last exception"
  [state ensure-nrepl-connection {:keys [session]}]
  (let [conn-result (ensure-nrepl-connection)]
    (if (:success conn-result)
      (try
        (let [conn (:connection conn-result)
              result (nrepl/stacktrace conn :session session)
              stacktrace (:stacktrace result)]
          (if stacktrace
            {:content [{:type "text"
                        :text (str "🔍 Stacktrace:\n\n" stacktrace)}]}
            {:content [{:type "text"
                        :text "❌ No stacktrace available"}]
             :isError true}))
        (catch Exception e
          (utils/log state :error "Stacktrace lookup failed:" (.getMessage e))
          {:content [{:type "text"
                      :text (str "❌ Stacktrace lookup failed: " (.getMessage e))}]
           :isError true}))
      {:content [{:type "text"
                  :text "❌ No nREPL connection available. Use nrepl-connect first."}]
       :isError true})))