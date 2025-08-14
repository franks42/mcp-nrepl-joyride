(ns mcp-nrepl-proxy.server
  "Server infrastructure for MCP-nREPL bridge.
   
   This namespace contains server-related functionality including:
   - STDIO server loop for MCP protocol over stdin/stdout
   - HTTP server and request handling
   - Main entry point and configuration
   - Server lifecycle management"
  (:require [cheshire.core :as json]
            [mcp-nrepl-proxy.utils :as utils]
            [org.httpkit.server :as httpkit]
            [babashka.nrepl.server :as nrepl-server]))

;; ============================================================================
;; STDIO Server Implementation
;; ============================================================================

(defn stdio-server-loop
  "MCP server loop for stdin/stdout transport.
   
   Args:
     state - State atom reference
     handle-request-fn - Function to handle MCP requests
     
   Side effects:
     Reads from stdin, writes to stdout, handles MCP protocol"
  [state handle-request-fn]
  (utils/log state :info "🚀 MCP-nREPL proxy server starting (stdio)")
  (utils/log state :info "📡 Listening for MCP messages on stdin")

  (try
    (loop []
      (when-let [line (read-line)]
        (try
          (let [request (json/parse-string line true)]
            (utils/log state :debug "📥 Received:" (:method request))
            (let [response (handle-request-fn request)]
              (println (json/generate-string response))
              (flush)))
          (catch Exception e
            (utils/log state :error "❌ Error processing message:" (.getMessage e))
            (try
              (let [request (json/parse-string line true)]
                (println (json/generate-string
                          {:jsonrpc "2.0"
                           :id (or (:id request) (str (System/currentTimeMillis)))
                           :error {:code -32700
                                   :message "Parse error"}})))
              (catch Exception _
                (utils/log state :error "Could not send error response")))))
        (recur)))
    (catch Exception e
      (utils/log state :error "💥 Server loop error:" (.getMessage e)))))

;; ============================================================================
;; HTTP Server Implementation
;; ============================================================================

(defn http-handler
  "HTTP handler for MCP JSON-RPC requests.
   
   Args:
     state - State atom reference
     handle-request-fn - Function to handle MCP requests
     request - HTTP request map
     
   Returns:
     HTTP response map with status, headers, and body"
  [state handle-request-fn request]
  (try
    (utils/log state :debug "📥 HTTP request:" (:request-method request) (:uri request))

    (cond
      ;; Handle MCP JSON-RPC POST requests
      (and (= (:request-method request) :post)
           (= (:uri request) "/mcp"))
      (let [body-str (slurp (:body request))
            mcp-request (json/parse-string body-str true)
            response (handle-request-fn mcp-request)]
        (utils/log state :debug "📤 HTTP response for method:" (:method mcp-request))
        {:status 200
         :headers {"Content-Type" "application/json"
                   "Access-Control-Allow-Origin" "*"
                   "Access-Control-Allow-Methods" "POST, OPTIONS"
                   "Access-Control-Allow-Headers" "Content-Type"}
         :body (json/generate-string response)})

      ;; Handle CORS preflight
      (and (= (:request-method request) :options)
           (= (:uri request) "/mcp"))
      {:status 200
       :headers {"Access-Control-Allow-Origin" "*"
                 "Access-Control-Allow-Methods" "POST, OPTIONS"
                 "Access-Control-Allow-Headers" "Content-Type"}}

      ;; Health check endpoint
      (= (:uri request) "/health")
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string {:status "ok"
                                    :nrepl-connected (not (nil? (:nrepl-conn @state)))
                                    :timestamp (System/currentTimeMillis)})}

      ;; Not found
      :else
      {:status 404
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string {:error "Not found"})})

    (catch Exception e
      (utils/log state :error "❌ HTTP handler error:" (.getMessage e))
      {:status 500
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string {:error "Internal server error"
                                    :message (.getMessage e)})})))

(defn start-http-server
  "Start HTTP server for MCP requests.
   
   Args:
     state - State atom reference
     handle-request-fn - Function to handle MCP requests
     port - Port number to bind server to
     
   Side effects:
     Starts HTTP server, adds shutdown hook, blocks forever"
  [state handle-request-fn port]
  (utils/log state :info "🚀 MCP-nREPL proxy server starting (HTTP)")
  (utils/log state :info "📡 Listening for HTTP MCP requests on port" port)
  (utils/log state :info "🔗 MCP endpoint: http://localhost:" port "/mcp")
  (utils/log state :info "💚 Health check: http://localhost:" port "/health")

  (try
    (let [server (httpkit/run-server
                  (partial http-handler state handle-request-fn)
                  {:port port})]
      (utils/log state :info "✅ HTTP server started on port" port)
      (.addShutdownHook (Runtime/getRuntime)
                        (Thread. #(do
                                    (utils/log state :info "🛑 Shutting down HTTP server...")
                                    (server))))
      ;; Keep the main thread alive
      (loop []
        (Thread/sleep 1000)
        (recur)))
    (catch Exception e
      (utils/log state :error "💥 HTTP server error:" (.getMessage e)))))

;; ============================================================================
;; Server Configuration and Startup
;; ============================================================================

(defn- start-babashka-nrepl-server
  "Start Babashka nREPL server for Calva introspection.
   
   Args:
     state - State atom reference
     bb-nrepl-port - Port to start server on
     
   Side effects:
     Starts Babashka nREPL server, updates state atom"
  [state bb-nrepl-port]
  (try
    (utils/log state :info "🔧 Starting Babashka nREPL server on port:" bb-nrepl-port)
    (let [server (let [captured-output (atom "")
                       original-out *out*]
                   (binding [*out* (java.io.StringWriter.)]
                     (let [result (nrepl-server/start-server! {:port bb-nrepl-port})]
                       (reset! captured-output (str *out*))
                       ;; Log captured output to stderr
                       (when (seq @captured-output)
                         (binding [*out* *err*]
                           (print @captured-output)))
                       result)))]
      (swap! state assoc :babashka-nrepl-server server)
      (utils/log state :info "✅ Babashka nREPL server started - connect Calva to localhost:" bb-nrepl-port)
      (spit ".nrepl-port-babashka" bb-nrepl-port))
    (catch Exception e
      (utils/log state :warn "⚠️  Failed to start Babashka nREPL server:" (.getMessage e)))))

(defn server-main
  "Main entry point for MCP-nREPL proxy server.
   
   Args:
     state - State atom reference
     handle-request-fn - Function to handle MCP requests
     start-heartbeat-fn - Function to start heartbeat monitoring
     args - Command line arguments
     
   Side effects:
     Configures server, starts appropriate transport, blocks forever"
  [state handle-request-fn start-heartbeat-fn & args]
  (let [http-port (some->> args first Integer/parseInt)
        use-http (or http-port (System/getenv "MCP_HTTP_PORT"))
        port (when use-http
               (or http-port
                   (some->> (System/getenv "MCP_HTTP_PORT") Integer/parseInt)))
        config {:debug (= "true" (System/getenv "MCP_DEBUG"))
                :workspace (or (System/getenv "JOYRIDE_WORKSPACE")
                               (System/getProperty "user.dir"))
                :transport (if use-http :http :stdio)
                :http-port port
                :babashka-nrepl-port (or (some->> (System/getenv "BABASHKA_NREPL_PORT") Integer/parseInt)
                                         7889)}]
    (swap! state assoc :config config)

    (utils/log state :info "🔧 MCP-nREPL Proxy Configuration:")
    (utils/log state :info "   Debug mode:" (:debug config))
    (utils/log state :info "   Workspace:" (:workspace config))
    (utils/log state :info "   Transport:" (:transport config))
    (when (= :http (:transport config))
      (utils/log state :info "   HTTP port:" (:http-port config)))
    (utils/log state :info "   Babashka nREPL port:" (:babashka-nrepl-port config))

    ;; Start heartbeat monitor
    (start-heartbeat-fn)

    ;; Start Babashka nREPL server for Calva introspection
    (when false ;; TEMPORARILY DISABLED - debugging JSON output issue
      (when-let [bb-nrepl-port (:babashka-nrepl-port config)]
        (start-babashka-nrepl-server state bb-nrepl-port)))

    ;; No auto-connection - use nrepl-connect tool explicitly
    (utils/log state :info "💡 Use nrepl-connect tool to connect to an nREPL server")

    ;; Start appropriate server
    (if (= :http (:transport config))
      (start-http-server state handle-request-fn (:http-port config))
      (stdio-server-loop state handle-request-fn))))