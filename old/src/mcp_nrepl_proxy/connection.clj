(ns mcp-nrepl-proxy.connection
  "nREPL connection management and discovery"
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [mcp-nrepl-proxy.nrepl-client :as nrepl]
            [mcp-nrepl-proxy.utils :as utils]))

(defn discover-nrepl-port
  "Discover nREPL port from .nrepl-port file in workspace or .joyride subdirectory"
  [workspace-path]
  (let [port-files [(fs/file workspace-path ".nrepl-port")
                    (fs/file workspace-path ".joyride" ".nrepl-port")]]
    (some (fn [port-file]
            (when (fs/exists? port-file)
              (try
                (let [port (Integer/parseInt (str/trim (slurp port-file)))]
                  (utils/log nil :info "Found nREPL port" port "in" (str port-file))
                  port)
                (catch Exception e
                  (utils/log nil :warn "Could not parse .nrepl-port file:" (.getMessage e))
                  nil))))
          port-files)))

(defn connect-to-nrepl
  "Connect to nREPL server with connection pooling and heartbeat monitoring"
  [state host port]
  (try
    (utils/log state :info "Connecting to nREPL at" (str host ":" port))
    (let [conn (nrepl/connect host port)]
      (swap! state assoc :nrepl-conn conn)
      (swap! state update-in [:health-status] assoc
             :connected true
             :last-heartbeat (System/currentTimeMillis)
             :heartbeat-failures 0)
      (utils/log state :info "Connected to nREPL successfully")
      {:success true :connection conn})
    (catch Exception e
      (utils/log state :error "nREPL connection failed:" (.getMessage e))
      (swap! state update-in [:health-status] assoc :connected false)
      {:success false :error (.getMessage e)})))

(defn ensure-nrepl-connection
  "Ensure we have a valid nREPL connection"
  [state]
  (if-let [conn (:nrepl-conn @state)]
    {:success true :connection conn}
    (let [workspace (get-in @state [:config :workspace])]
      (if-let [port (discover-nrepl-port workspace)]
        (connect-to-nrepl state "localhost" port)
        {:success false :error "No nREPL connection and could not discover port"}))))

(defn get-joyride-connection
  "Get current Joyride nREPL connection details"
  [state]
  (when-let [conn (:nrepl-conn @state)]
    {:host (:host conn)
     :port (:port conn)
     :connected true}))