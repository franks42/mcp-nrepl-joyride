(ns nrepl-mcp-server.state.local-nrepl-server
  "State management for embedded local babashka nREPL server lifecycle"
  (:require [babashka.nrepl.server :as nrepl-server]))

;; =============================================================================
;; nREPL Server State Atom
;; =============================================================================

(def nrepl-server-state
  "State atom for babashka nREPL server lifecycle management.
   
   Structure:
   {:server-map nil          ; Complete map from babashka.nrepl.server/start-server!
    :status :stopped         ; :starting, :running, :stopping, :stopped, :error
    :host \"localhost\"      ; Host server is bound to
    :port nil               ; Port server is running on
    :connection nil         ; \"host:port\" string for easy use
    :started-at nil         ; Start timestamp (System/currentTimeMillis)
    :stopped-at nil         ; Stop timestamp
    :config {}              ; Server configuration used for start
    :error nil}             ; Last error message if any"
  (atom {:server-map nil
         :status :stopped
         :host "localhost"
         :port nil
         :connection nil
         :started-at nil
         :stopped-at nil
         :config {}
         :error nil}))

;; =============================================================================
;; Port Extraction Utilities
;; =============================================================================

(defn extract-server-port
  "Extract actual bound port from server socket.
   Handles auto-assigned ports (port 0) properly."
  [server-map]
  (when-let [socket (:socket server-map)]
    (.getLocalPort socket)))

(defn extract-server-host
  "Extract host from server configuration or default to localhost"
  [config]
  (get config :host "localhost"))

(defn format-connection
  "Format host:port connection string"
  [host port]
  (str host ":" port))

;; =============================================================================
;; State Query Functions
;; =============================================================================

(defn running?
  "Check if nREPL server is currently running"
  []
  (= :running (:status @nrepl-server-state)))

(defn get-status
  "Get current server status"
  []
  (:status @nrepl-server-state))

(defn get-server-map
  "Get stored server map (contains :socket and :future)"
  []
  (:server-map @nrepl-server-state))

(defn get-connection-info
  "Get current connection information as map"
  []
  (let [state @nrepl-server-state]
    {:host (:host state)
     :port (:port state)
     :connection (:connection state)
     :status (:status state)
     :started-at (:started-at state)
     :stopped-at (:stopped-at state)
     :error (:error state)}))

(defn get-uptime-ms
  "Get server uptime in milliseconds, or nil if not running"
  []
  (when-let [started-at (:started-at @nrepl-server-state)]
    (when (running?)
      (- (System/currentTimeMillis) started-at))))

;; =============================================================================
;; State Management Functions
;; =============================================================================

(defn start-server!
  "Start babashka nREPL server and update state.
   Config options:
   - :port (optional) - Port to bind to, 0 for auto-assign
   - :host (optional) - Host to bind to, defaults to localhost"
  [config]
  (try
    ;; Check if already running
    (when (running?)
      (throw (ex-info "nREPL server already running"
                      {:status (:status @nrepl-server-state)
                       :port (:port @nrepl-server-state)})))

    ;; Set starting status
    (swap! nrepl-server-state assoc
           :status :starting
           :config config
           :error nil)

    ;; Start server
    (let [server-map (nrepl-server/start-server! config)
          host (extract-server-host config)
          port (extract-server-port server-map)
          connection (format-connection host port)
          started-at (System/currentTimeMillis)]

      ;; Update state with running server
      (swap! nrepl-server-state assoc
             :server-map server-map
             :status :running
             :host host
             :port port
             :connection connection
             :started-at started-at
             :stopped-at nil
             :error nil)

      ;; Return connection info
      {:status :running
       :host host
       :port port
       :connection connection
       :started-at started-at})

    (catch Exception e
      ;; Update state with error
      (swap! nrepl-server-state assoc
             :status :error
             :error (.getMessage e))
      (throw e))))

(defn stop-server!
  "Stop babashka nREPL server and update state"
  []
  (try
    ;; Check if running
    (when-not (running?)
      (throw (ex-info "nREPL server not running"
                      {:status (:status @nrepl-server-state)})))

    ;; Get server map before setting stopping status
    (let [server-map (get-server-map)
          connection-info (get-connection-info)]

      ;; Set stopping status
      (swap! nrepl-server-state assoc :status :stopping)

      ;; Stop server using stored server map
      (nrepl-server/stop-server! server-map)

      ;; Update state to stopped
      (let [stopped-at (System/currentTimeMillis)]
        (swap! nrepl-server-state assoc
               :server-map nil
               :status :stopped
               :stopped-at stopped-at
               :error nil)

        ;; Return info about stopped server
        (assoc connection-info
               :status :stopped
               :stopped-at stopped-at)))

    (catch Exception e
      ;; Update state with error
      (swap! nrepl-server-state assoc
             :status :error
             :error (.getMessage e))
      (throw e))))

(defn restart-server!
  "Restart nREPL server with given config"
  [config]
  (when (running?)
    (stop-server!))
  (start-server! config))

;; =============================================================================
;; Debug Support
;; =============================================================================

(defn get-full-state
  "Get complete state for debugging"
  []
  @nrepl-server-state)

(defn reset-state!
  "Reset state to initial values (for testing)"
  []
  (reset! nrepl-server-state
          {:server-map nil
           :status :stopped
           :host "localhost"
           :port nil
           :connection nil
           :started-at nil
           :stopped-at nil
           :config {}
           :error nil}))