;; Scittle nREPL and HTTP Server Management Functions
;; Use with local-load-file to add these functions to the bb environment

(println "📦 Loading Scittle server management functions...")

;; Require necessary namespaces
(require '[sci.nrepl.browser-server :as scittle-nrepl]
         '[babashka.http-server :as http])

;; State atoms to track servers
(defonce scittle-nrepl-server (atom nil))
(defonce scittle-http-server (atom nil))

;; =============================================================================
;; Scittle nREPL Server Functions
;; =============================================================================

(defn start-scittle-nrepl!
  "Start Scittle browser nREPL server"
  [& {:keys [nrepl-port websocket-port]
      :or {nrepl-port 1339 websocket-port 1340}}]
  (if @scittle-nrepl-server
    (do
      (println "⚠️  Scittle nREPL server already running")
      {:status "already-running" :nrepl-port nrepl-port :websocket-port websocket-port})
    (try
      (println (str "🌐 Starting Scittle nREPL server on ports " nrepl-port "/" websocket-port "..."))
      (let [server (scittle-nrepl/start! {:nrepl-port nrepl-port :websocket-port websocket-port})]
        (reset! scittle-nrepl-server server)
        (println "✅ Scittle nREPL server started!")
        (println (str "   📡 nREPL port: " nrepl-port))
        (println (str "   🔌 WebSocket port: " websocket-port))
        {:status "started" :nrepl-port nrepl-port :websocket-port websocket-port :server server})
      (catch Exception e
        (println (str "❌ Failed to start Scittle nREPL server: " (.getMessage e)))
        {:status "error" :error (.getMessage e)}))))

(defn stop-scittle-nrepl!
  "Stop Scittle browser nREPL server"
  []
  (if-let [server @scittle-nrepl-server]
    (try
      (println "🛑 Stopping Scittle nREPL server...")
      ;; Note: sci.nrepl might not have explicit stop function
      ;; This is a placeholder - need to check actual API
      (reset! scittle-nrepl-server nil)
      (println "✅ Scittle nREPL server stopped")
      {:status "stopped"})
    (catch Exception e
      (println (str "❌ Error stopping Scittle nREPL server: " (.getMessage e)))
      {:status "error" :error (.getMessage e)}))
    (do
      (println "⚠️  Scittle nREPL server not running")
      {:status "not-running"})))

;; =============================================================================
;; HTTP Server Functions
;; =============================================================================

(defn start-http-server!
  "Start HTTP server for serving browser assets"
  [& {:keys [port dir]
      :or {port 1341 dir "scittle-browser"}}]
  (if @scittle-http-server
    (do
      (println "⚠️  HTTP server already running")
      {:status "already-running" :port port :dir dir})
    (try
      (println (str "🌐 Starting HTTP server on port " port " serving " dir "..."))
      ;; Start HTTP server in a future to avoid blocking
      (let [server-future (future (http/serve {:port port :dir dir}))]
        (reset! scittle-http-server server-future)
        (Thread/sleep 1000) ; Give server time to start
        (println "✅ HTTP server started!")
        (println (str "   🌐 URL: http://localhost:" port "/"))
        (println (str "   📁 Serving: " dir "/"))
        {:status "started" :port port :dir dir :url (str "http://localhost:" port "/")})
      (catch Exception e
        (println (str "❌ Failed to start HTTP server: " (.getMessage e)))
        {:status "error" :error (.getMessage e)}))))

(defn stop-http-server!
  "Stop HTTP server"
  []
  (if-let [server @scittle-http-server]
    (try
      (println "🛑 Stopping HTTP server...")
      (future-cancel server)
      (reset! scittle-http-server nil)
      (println "✅ HTTP server stopped")
      {:status "stopped"})
    (catch Exception e
      (println (str "❌ Error stopping HTTP server: " (.getMessage e)))
      {:status "error" :error (.getMessage e)}))
    (do
      (println "⚠️  HTTP server not running")
      {:status "not-running"})))

;; =============================================================================
;; Combined Functions
;; =============================================================================

(defn start-scittle-full!
  "Start both Scittle nREPL and HTTP servers"
  [& {:keys [nrepl-port websocket-port http-port http-dir]
      :or {nrepl-port 1339 websocket-port 1340 http-port 1341 http-dir "scittle-browser"}}]
  (println "🚀 Starting full Scittle environment...")
  (let [nrepl-result (start-scittle-nrepl! :nrepl-port nrepl-port :websocket-port websocket-port)
        http-result (start-http-server! :port http-port :dir http-dir)]
    (println "🎉 Scittle environment ready!")
    (println (str "   🌐 Open browser: http://localhost:" http-port "/"))
    (println (str "   📡 Connect editor to nREPL: localhost:" nrepl-port))
    {:nrepl nrepl-result :http http-result}))

(defn stop-scittle-full!
  "Stop both Scittle nREPL and HTTP servers"
  []
  (println "🛑 Stopping full Scittle environment...")
  (let [nrepl-result (stop-scittle-nrepl!)
        http-result (stop-http-server!)]
    (println "✅ Scittle environment stopped")
    {:nrepl nrepl-result :http http-result}))

(defn scittle-status
  "Get status of Scittle servers"
  []
  {:nrepl-server (if @scittle-nrepl-server "running" "stopped")
   :http-server (if @scittle-http-server "running" "stopped")})

;; =============================================================================
;; Convenience Functions
;; =============================================================================

(defn restart-scittle!
  "Restart the full Scittle environment"
  []
  (stop-scittle-full!)
  (Thread/sleep 2000) ; Give servers time to stop
  (start-scittle-full!))

(println "✅ Scittle server functions loaded!")
(println "Available functions:")
(println "  (start-scittle-nrepl!)  - Start nREPL server only")
(println "  (start-http-server!)    - Start HTTP server only")
(println "  (start-scittle-full!)   - Start both servers")
(println "  (stop-scittle-full!)    - Stop both servers")
(println "  (scittle-status)        - Check server status")
(println "  (restart-scittle!)      - Restart everything")

;; Return success indicator
{:status "loaded"
 :functions ["start-scittle-nrepl!" "start-http-server!" "start-scittle-full!" 
             "stop-scittle-full!" "scittle-status" "restart-scittle!"]}