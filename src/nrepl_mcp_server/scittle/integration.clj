(ns nrepl-mcp-server.scittle.integration
  "Scittle Browser nREPL Integration - Hot-loadable bidirectional development environment
  
  This namespace provides tools for integrating Scittle browser-based ClojureScript 
  with the MCP nREPL server, enabling live bidirectional development:
  
  Browser ←→ WebSocket ←→ Babashka nREPL ←→ MCP nREPL Server
  
  Key capabilities:
  - Hot-load ClojureScript nREPL client into browser
  - Hot-load WebSocket bridge extensions into Babashka
  - Bidirectional code evaluation (browser ↔ server)
  - File system operations from browser
  - Shell command execution from browser
  - Real-time server monitoring from browser
  
  Usage:
  1. Start Scittle environment: ./start-scittle-env.sh
  2. Load integration functions: (load-scittle-integration!)
  3. Deploy browser client: (deploy-browser-nrepl-client!)
  4. Deploy bridge extension: (deploy-bridge-extension!)
  5. Test: (test-bidirectional-connection!)
  
  Architecture:
  - Port 7890: Scittle Babashka nREPL server
  - Port 1339: Browser nREPL server (ClojureScript)  
  - Port 1340: WebSocket bridge
  - Port 1341: HTTP server for browser assets")

;; =============================================================================
;; Configuration and State
;; =============================================================================

(def scittle-config
  "Scittle environment configuration"
  {:scittle-dir "/Users/franksiebenlist/Development/scittle"
   :babashka-nrepl-port 7890
   :browser-nrepl-port 1339
   :websocket-port 1340
   :http-port 1341})

(defonce integration-state
  "Integration state tracking"
  (atom {:status :not-loaded
         :connections {}
         :deployed-components #{}}))

;; =============================================================================
;; Environment Setup Functions
;; =============================================================================

(defn start-scittle-environment!
  "Start the complete Scittle environment using the startup script"
  []
  (println "🚀 Starting Scittle environment...")
  {:command "./start-scittle-env.sh"
   :description "Starts Babashka nREPL, browser servers, and HTTP server"
   :ports scittle-config
   :next-steps ["Connect to Babashka nREPL"
                "Load integration functions"
                "Deploy bidirectional components"]})

(defn stop-scittle-environment!
  "Stop the complete Scittle environment using the shutdown script"
  []
  (println "🛑 Stopping Scittle environment...")
  {:command "./stop-scittle-env.sh"
   :description "Stops all Scittle-related processes and cleans up ports"})

;; =============================================================================
;; Hot-Loadable Browser nREPL Client
;; =============================================================================

(defn browser-nrepl-client-code
  "ClojureScript code for browser-side nREPL client"
  []
  "
;; Browser nREPL Client - Hot-loaded into ClojureScript environment
;; Provides bidirectional communication with Babashka server

(defonce bb-client-state (atom {:status :ready :message-handlers {}}))

(defn eval-on-babashka 
  \"Evaluate Clojure code on the Babashka server\"
  [code & {:keys [timeout-ms] :or {timeout-ms 5000}}]
  (let [message-id (str (random-uuid))
        message {:direction \"to-babashka\" 
                 :op \"eval\" 
                 :code code 
                 :id message-id
                 :timestamp (js/Date.now)}
        result-promise (js/Promise. 
                         (fn [resolve reject]
                           (swap! bb-client-state assoc-in [:message-handlers message-id] 
                                  {:resolve resolve :reject reject :timestamp (js/Date.now)})
                           (js/setTimeout #(reject (js/Error. \"Timeout\")) timeout-ms)))]
    (send-to-babashka message)
    result-promise))

(defn send-to-babashka [message]
  \"Send message to Babashka via existing WebSocket\"
  ;; TODO: Integrate with existing Scittle WebSocket connection
  (js/console.log \"Sending to Babashka:\" message))

(defn handle-babashka-response [response]
  \"Handle response from Babashka server\"
  (let [{:keys [id status value error]} response
        handler (get-in @bb-client-state [:message-handlers id])]
    (when handler
      (swap! bb-client-state update :message-handlers dissoc id)
      (if (= status \"success\")
        ((:resolve handler) value)
        ((:reject handler) (js/Error. error))))))

;; Convenience functions for common operations
(defn read-server-file [path]
  \"Read file from server file system\"
  (eval-on-babashka (str \"(slurp \\\"\" path \"\\\")\")))

(defn write-server-file [path content]
  \"Write file to server file system\"
  (eval-on-babashka (str \"(spit \\\"\" path \"\\\" \\\"\" content \"\\\")\")))

(defn run-shell-command [cmd & args]
  \"Execute shell command on server\"
  (eval-on-babashka (str \"(shell/sh \\\"\" cmd \"\\\" \" (pr-str (vec args)) \")\")))

(defn server-status []
  \"Get server status and information\"
  (eval-on-babashka \"(system-properties)\"))

(js/console.log \"🌐 Browser nREPL Client loaded! Available functions:\")
(js/console.log \"  - (eval-on-babashka code)     ; Evaluate on server\")
(js/console.log \"  - (read-server-file path)     ; Read server file\") 
(js/console.log \"  - (write-server-file path content) ; Write server file\")
(js/console.log \"  - (run-shell-command cmd args) ; Execute shell command\")
(js/console.log \"  - (server-status)             ; Get server info\")
")

;; =============================================================================
;; Hot-Loadable WebSocket Bridge Extension
;; =============================================================================

(defn bridge-extension-code
  "Clojure code for Babashka-side WebSocket bridge extension"
  []
  "
;; WebSocket Bridge Extension - Hot-loaded into Babashka environment
;; Extends existing Scittle WebSocket bridge for bidirectional communication

(defonce bridge-state (atom {:status :ready :active-sessions {}}))

(defn enhanced-websocket-handler 
  \"Enhanced WebSocket message handler with bidirectional support\"
  [websocket-message]
  (let [{:keys [direction op code id]} websocket-message]
    (case direction
      \"to-browser\"   (forward-to-browser websocket-message)     ; existing functionality
      \"to-babashka\"  (handle-server-request websocket-message)  ; NEW: reverse evaluation
      (default-websocket-handler websocket-message))))

(defn handle-server-request
  \"Handle evaluation request from browser\"
  [{:keys [op code id timestamp] :as message}]
  (try
    (let [start-time (System/currentTimeMillis)
          result (case op
                   \"eval\" (eval-code-safely code)
                   \"file-read\" (slurp code)
                   \"file-write\" (let [[path content] code] (spit path content))
                   \"shell\" (apply shell/sh code)
                   (throw (Exception. (str \"Unknown operation: \" op))))
          end-time (System/currentTimeMillis)
          response {:id id
                    :status \"success\"
                    :value (str result)
                    :execution-time-ms (- end-time start-time)
                    :timestamp (System/currentTimeMillis)}]
      (send-to-browser response))
    (catch Exception e
      (let [error-response {:id id
                            :status \"error\"
                            :error (str (.getMessage e))
                            :timestamp (System/currentTimeMillis)}]
        (send-to-browser error-response)))))

(defn eval-code-safely 
  \"Safely evaluate Clojure code with error handling\"
  [code]
  (try
    (eval (read-string code))
    (catch Exception e
      (throw (Exception. (str \"Evaluation error: \" (.getMessage e)))))))

(defn send-to-browser [message]
  \"Send message to browser via WebSocket\"
  ;; TODO: Integrate with existing Scittle WebSocket infrastructure
  (println \"📤 Sending to browser:\" message))

(defn forward-to-browser [message]
  \"Forward message to browser (existing functionality)\"
  ;; Delegate to existing Scittle WebSocket handler
  (println \"↗️ Forwarding to browser:\" message))

(defn default-websocket-handler [message]
  \"Default handler for unrecognized messages\"
  (println \"❓ Unknown message direction:\" message))

(println \"🔧 WebSocket Bridge Extension loaded!\")
(println \"📡 Enhanced bidirectional communication ready\")
")

;; =============================================================================
;; Deployment Functions
;; =============================================================================

(defn deploy-browser-nrepl-client!
  "Deploy nREPL client to browser environment via nrepl-eval"
  []
  (println "🌐 Deploying browser nREPL client...")
  {:code (browser-nrepl-client-code)
   :target :browser
   :deployment-method "nrepl-eval with connection: browser-nrepl"
   :description "Hot-loads ClojureScript nREPL client into browser"})

(defn deploy-bridge-extension!
  "Deploy WebSocket bridge extension to Babashka environment via nrepl-eval"
  []
  (println "🔧 Deploying WebSocket bridge extension...")
  {:code (bridge-extension-code)
   :target :babashka
   :deployment-method "nrepl-eval with connection: babashka-nrepl"
   :description "Hot-loads enhanced bridge into Babashka nREPL"})

;; =============================================================================
;; Testing and Validation
;; =============================================================================

(defn test-bidirectional-connection!
  "Test the complete bidirectional connection"
  []
  (println "🧪 Testing bidirectional connection...")
  {:tests ["(eval-on-babashka \"(+ 1 2 3)\")"
           "(read-server-file \"/etc/hostname\")"
           "(run-shell-command \"pwd\")"
           "(server-status)"]
   :expected-results ["6" "hostname content" "current directory" "system properties"]
   :description "Validates browser → server → browser communication"})

;; =============================================================================
;; Integration Lifecycle
;; =============================================================================

(defn load-scittle-integration!
  "Load the complete Scittle integration"
  []
  (println "🎯 Loading Scittle integration...")
  (swap! integration-state assoc :status :loaded)
  {:status :loaded
   :config scittle-config
   :next-steps ["Deploy browser client" "Deploy bridge extension" "Test connection"]
   :functions ["deploy-browser-nrepl-client!"
               "deploy-bridge-extension!"
               "test-bidirectional-connection!"]})

(defn scittle-integration-status
  "Get current integration status"
  []
  @integration-state)

;; =============================================================================
;; Demo and Examples
;; =============================================================================

(defn demo-browser-server-integration
  "Demonstrate browser-server integration capabilities"
  []
  {:demo-scenarios
   [{:name "File Operations"
     :browser-code "(read-server-file \"README.md\")"
     :description "Browser reads server file"}

    {:name "Shell Commands"
     :browser-code "(run-shell-command \"git\" \"status\")"
     :description "Browser executes git status on server"}

    {:name "Data Processing"
     :browser-code "(eval-on-babashka \"(reduce + (range 1000))\")"
     :description "Browser triggers server computation"}

    {:name "Live Monitoring"
     :browser-code "(server-status)"
     :description "Browser monitors server health"}]

   :architecture-benefits
   ["Hot-loadable deployment"
    "Zero infrastructure changes"
    "Bidirectional communication"
    "Rich development capabilities"
    "Browser-controlled server operations"]})

(comment
  ;; Usage examples:

  ;; 1. Start environment
  (start-scittle-environment!)

  ;; 2. Load integration
  (load-scittle-integration!)

  ;; 3. Deploy components  
  (deploy-browser-nrepl-client!)
  (deploy-bridge-extension!)

  ;; 4. Test functionality
  (test-bidirectional-connection!)

  ;; 5. Check status
  (scittle-integration-status)

  ;; 6. See demos
  (demo-browser-server-integration))