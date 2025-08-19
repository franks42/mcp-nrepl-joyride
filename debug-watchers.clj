;; Debug script to investigate watcher state after connection switching

(require '[nrepl-mcp-server.state.messages :as msg-state]
         '[nrepl-mcp-server.state.results :as results]
         '[nrepl-mcp-server.state.connection :as conn]
         '[nrepl-mcp-server.state.watchers :as watchers])

(defn watcher-status []
  "Check the status of all watchers"
  (let [watcher-state @watchers/watcher-state]
    {:watcher-state-keys (keys watcher-state)
     :send-watcher-running (:send-watcher-running watcher-state)
     :receive-watcher-running (:receive-watcher-running watcher-state)
     :send-watcher-future (:send-watcher-future watcher-state)
     :receive-watcher-future (:receive-watcher-future watcher-state)}))

(defn message-queue-status []
  "Check the message queue state"
  (let [queue @msg-state/message-queue]
    {:queue-keys (keys queue)
     :send-queue-count (count (:send-queue queue))
     :pending-messages-count (count (:pending-messages queue))}))

(defn results-queue-status []
  "Check the results queue state"
  (let [queue @results/result-queue]
    {:queue-keys (keys queue)
     :result-promises-count (count (:result-promises queue))
     :completed-results-count (count (:completed-results queue))
     :error-results-count (count (:error-results queue))}))

(defn connection-status []
  "Check connection state"
  (let [conn-state @conn/connection-state]
    {:connection-count (count (:connections conn-state))
     :active-connection (:active-connection conn-state)
     :connected? (conn/connected?)}))

(defn full-debug-status []
  "Get comprehensive debug status"
  {:watchers (watcher-status)
   :messages (message-queue-status)
   :results (results-queue-status)
   :connection (connection-status)})

(println "=== WATCHER DEBUG STATUS ===")
(clojure.pprint/pprint (full-debug-status))