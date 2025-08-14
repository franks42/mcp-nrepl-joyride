;; MCP-nREPL Debug Toolkit
;; Load with: debug-load-file "debug-toolkit.clj"
;; 
;; This provides a comprehensive debugging API that bypasses SCI restrictions
;; and makes introspection of the running MCP server much easier.

;; =============================================================================
;; Core State Accessors
;; =============================================================================

(def server-state
  "Get the main server state atom"
  (fn [] @mcp-nrepl-proxy.core/state))

(def msg-queues
  "Get the message queues atom (bypasses private var restriction)"
  (fn [] @@#'mcp-nrepl-proxy.state/message-queues))

(def connection-state
  "Get current nREPL connection info"
  (fn [] (:nrepl-conn (server-state))))

;; =============================================================================
;; Quick Inspection Functions
;; =============================================================================

(def pending-msgs
  "Get all pending messages"
  (fn [] (:pending-messages (msg-queues))))

(def message-records
  "Get all message records"
  (fn [] (:message-records (msg-queues))))

(def failure-records
  "Get all failure records"
  (fn [] (:failure-records (msg-queues))))

(def recent-commands
  "Get recent commands from server state"
  (fn [] (:recent-commands (server-state))))

(def health-status
  "Get server health status"
  (fn [] (:health-status (server-state))))

;; =============================================================================
;; Analysis Functions
;; =============================================================================

(def debug-summary
  "Comprehensive system overview"
  (fn []
    {:server-state-keys (keys (server-state))
     :msg-queue-keys (keys (msg-queues))
     :pending-count (count (pending-msgs))
     :message-records-count (count (message-records))
     :failure-count (count (failure-records))
     :connected? (boolean (connection-state))
     :recent-commands-count (count (recent-commands))
     :health-connected (:connected (health-status))}))

(def architecture-analysis
  "Analyze the current architectural mess"
  (fn []
    (let [server-st (server-state)
          msg-q (msg-queues)]
      {:circular-dependencies
       {:nrepl-client-calls-state true
        :state-calls-nrepl-client true
        :tools-call-both true}
       
       :state-confusion
       {:core-state-keys (keys server-st)
        :message-queue-keys (keys msg-q)
        :both-called-state true}
       
       :queue-implementations
       {:nrepl-client-queue-async "wrapper function"
        :state-queue-async "actual implementation"
        :tools-async-queue-async "MCP wrapper"}
       
       :connection-management
       {:ensure-connection-location "core.clj adapter"
        :actual-connection-logic "connection.clj"
        :single-connection-only true}})))

(def queue-inspector
  "Deep inspection of message queue state"
  (fn []
    (let [queues (msg-queues)]
      {:pending-by-connection (:pending-messages queues)
       :total-message-records (count (:message-records queues))
       :total-failures (count (:failure-records queues))
       :message-statuses (frequencies (map :status (vals (:message-records queues))))
       :recent-failures (take 5 (reverse (:failure-records queues)))})))

;; =============================================================================
;; Debugging Utilities
;; =============================================================================

(def find-function-calls
  "Find which functions call a specific function (simple grep-like)"
  (fn [fn-name]
    ;; This would need access to source code or reflection
    ;; For now, return known architectural calls
    (case fn-name
      "queue-message-async" [:nrepl-client :state :tools-async]
      "ensure-nrepl-connection" [:all-tools :core-adapter]
      "send-message-async" [:queue-message-async :messaging]
      [:unknown])))

(def state-diff
  "Compare two state snapshots"
  (fn [state1 state2]
    {:added-keys (clojure.set/difference (set (keys state2)) (set (keys state1)))
     :removed-keys (clojure.set/difference (set (keys state1)) (set (keys state2)))
     :changed-values (into {} (for [k (keys state1)
                                    :when (and (contains? state2 k)
                                              (not= (get state1 k) (get state2 k)))]
                                [k {:old (get state1 k) :new (get state2 k)}]))}))

;; =============================================================================
;; Hot-fix Utilities
;; =============================================================================

(def hot-fix
  "Replace a function definition on the fly"
  (fn [fn-name new-impl]
    (eval `(def ~(symbol fn-name) ~new-impl))
    (str "Replaced " fn-name " with new implementation")))

(def reload-namespace
  "Reload a namespace (where possible)"
  (fn [ns-name]
    (try
      (require (symbol ns-name) :reload)
      (str "Reloaded " ns-name)
      (catch Exception e
        (str "Failed to reload " ns-name ": " (.getMessage e))))))

;; =============================================================================
;; Shortcuts
;; =============================================================================

(def ds debug-summary)
(def aa architecture-analysis)
(def qi queue-inspector)
(def ss server-state)
(def mq msg-queues)
(def pm pending-msgs)
(def cs connection-state)

;; =============================================================================
;; Usage Examples
;; =============================================================================

(def help
  "Show usage examples"
  (fn []
    ["=== MCP-nREPL Debug Toolkit ==="
     ""
     "Quick Status:"
     "  (ds)  or  (debug-summary)"
     "  (aa)  or  (architecture-analysis)" 
     "  (qi)  or  (queue-inspector)"
     ""
     "State Access:"
     "  (ss)  or  (server-state)"
     "  (mq)  or  (msg-queues)"
     "  (pm)  or  (pending-msgs)"
     "  (cs)  or  (connection-state)"
     ""
     "Advanced:"
     "  (hot-fix \"fn-name\" new-impl)"
     "  (reload-namespace \"ns-name\")"
     "  (state-diff old-state new-state)"
     ""
     "Load this toolkit:"
     "  debug-load-file \"debug-toolkit.clj\""]))

;; Print success message
"🔧 Debug toolkit loaded! Use (help) for usage examples."