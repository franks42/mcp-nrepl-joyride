(ns nrepl-mcp-server.mcp-server.tools.local-nrepl-server
  "MCP tool for babashka nREPL server lifecycle management"
  (:require [nrepl-mcp-server.state.local-nrepl-server :as nrepl-state]
            [cheshire.core :as json]))

;; =============================================================================
;; Operation Handlers
;; =============================================================================

(defn handle-start
  "Handle nREPL server start operation"
  [{:keys [port host]}]
  (try
    (let [config (cond-> {}
                   port (assoc :port port)
                   host (assoc :host host))
          result (nrepl-state/start-server! config)]
      {:content [{:type "text"
                  :text (json/generate-string
                         {:status "success"
                          :operation "start"
                          :server-status "running"
                          :host (:host result)
                          :port (:port result)
                          :connection (:connection result)
                          :started-at (:started-at result)
                          :message (str "nREPL server started on " (:connection result))}
                         {:pretty true})}]})
    (catch Exception e
      {:content [{:type "text"
                  :text (json/generate-string
                         {:status "error"
                          :operation "start"
                          :error (.getMessage e)}
                         {:pretty true})}]
       :isError true})))

(defn handle-stop
  "Handle nREPL server stop operation"
  [_args]
  (try
    (let [result (nrepl-state/stop-server!)]
      {:content [{:type "text"
                  :text (json/generate-string
                         {:status "success"
                          :operation "stop"
                          :server-status "stopped"
                          :host (:host result)
                          :port (:port result)
                          :connection (:connection result)
                          :stopped-at (:stopped-at result)
                          :message (str "nREPL server stopped (was running on " (:connection result) ")")}
                         {:pretty true})}]})
    (catch Exception e
      {:content [{:type "text"
                  :text (json/generate-string
                         {:status "error"
                          :operation "stop"
                          :error (.getMessage e)}
                         {:pretty true})}]
       :isError true})))

(defn handle-status
  "Handle nREPL server status operation"
  [_args]
  (let [info (nrepl-state/get-connection-info)
        uptime (nrepl-state/get-uptime-ms)]
    {:content [{:type "text"
                :text (json/generate-string
                       (cond-> {:status "success"
                                :operation "status"
                                :server-status (:status info)
                                :host (:host info)
                                :port (:port info)
                                :connection (:connection info)
                                :started-at (:started-at info)
                                :stopped-at (:stopped-at info)}
                         uptime (assoc :uptime-ms uptime)
                         (:error info) (assoc :error (:error info))
                         (= :running (:status info))
                         (assoc :message (str "nREPL server running on " (:connection info)))
                         (= :stopped (:status info))
                         (assoc :message "nREPL server is stopped")
                         (= :error (:status info))
                         (assoc :message (str "nREPL server error: " (:error info))))
                       {:pretty true})}]}))

(defn handle-restart
  "Handle nREPL server restart operation"
  [{:keys [port host]}]
  (try
    (let [config (cond-> {}
                   port (assoc :port port)
                   host (assoc :host host))
          result (nrepl-state/restart-server! config)]
      {:content [{:type "text"
                  :text (json/generate-string
                         {:status "success"
                          :operation "restart"
                          :server-status "running"
                          :host (:host result)
                          :port (:port result)
                          :connection (:connection result)
                          :started-at (:started-at result)
                          :message (str "nREPL server restarted on " (:connection result))}
                         {:pretty true})}]})
    (catch Exception e
      {:content [{:type "text"
                  :text (json/generate-string
                         {:status "error"
                          :operation "restart"
                          :error (.getMessage e)}
                         {:pretty true})}]
       :isError true})))

;; =============================================================================
;; Main Handler
;; =============================================================================

(defn handle
  "Handle local nREPL server operations based on op parameter"
  [{:keys [op] :as args}]
  (case op
    "start" (handle-start args)
    "stop" (handle-stop args)
    "status" (handle-status args)
    "restart" (handle-restart args)

    ;; Unknown operation
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "error"
                        :operation op
                        :error (str "Unknown operation: " op
                                    ". Use 'start', 'stop', 'status', or 'restart'")}
                       {:pretty true})}]
     :isError true}))

;; =============================================================================
;; Tool Metadata
;; =============================================================================

(def tool-name "local-nrepl-server")

(def metadata
  {:description "Babashka nREPL server lifecycle management: start, stop, status, restart"
   :inputSchema {:type "object"
                 :properties {:op {:type "string"
                                   :description "Operation: 'start', 'stop', 'status', or 'restart'"
                                   :enum ["start" "stop" "status" "restart"]}
                              :port {:type "integer"
                                     :description "Port to start server on (optional, auto-assign if not provided)"
                                     :minimum 0
                                     :maximum 65535}
                              :host {:type "string"
                                     :description "Host to bind server to (optional, defaults to localhost)"}}
                 :required ["op"]}})