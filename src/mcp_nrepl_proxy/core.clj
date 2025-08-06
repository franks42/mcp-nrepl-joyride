#!/usr/bin/env bb

(ns mcp-nrepl-proxy.core
  "Babashka MCP server bridging Claude Code with Joyride nREPL.
   
   Pure Babashka implementation using native nREPL client capabilities.
   Supports both stdio and HTTP transports."
  (:require [cheshire.core :as json]
            [mcp-nrepl-proxy.nrepl-client :as nrepl]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [org.httpkit.server :as httpkit])
  (:import [java.util Base64]))

(def ^:private state
  "Server state: nREPL connections, sessions, and configuration"
  (atom {:nrepl-conn nil
         :sessions {}
         :recent-commands []
         :config {:debug false
                  :workspace nil
                  :max-cached-commands 10}}))

(defn- log
  "Log to stderr (stdout reserved for MCP protocol)"
  [level & args]
  (when (or (= level :error) 
            (get-in @state [:config :debug]))
    (binding [*out* *err*]
      (println (str "[" (name level) "] " (apply str args))))))

(defn- base64-decode
  "Decode base64 string to regular string, or return as-is if not base64 or not a string"
  [value]
  (cond
    (nil? value) nil
    (not (string? value)) (str value)  ; Convert non-strings to strings
    :else
    (try
      (String. (.decode (Base64/getDecoder) value))
      (catch Exception e
        (log :debug "Not base64 encoded, returning as string:" value)
        value))))

(defn- decode-nrepl-response
  "Decode base64-encoded values and byte arrays in nREPL response"
  [response]
  (when response
    (cond-> response
      (:value response) (update :value base64-decode)
      (:ns response) (update :ns base64-decode)
      (:out response) (update :out base64-decode)
      (:err response) (update :err base64-decode)
      (:status response) (update :status #(map base64-decode %)))))

(defn- discover-nrepl-port
  "Discover nREPL port from .nrepl-port file in workspace"
  [workspace-path]
  (let [port-file (fs/file workspace-path ".nrepl-port")]
    (when (fs/exists? port-file)
      (try
        (Integer/parseInt (str/trim (slurp port-file)))
        (catch Exception e
          (log :warn "Could not parse .nrepl-port file:" (.getMessage e))
          nil)))))

(defn- connect-to-nrepl
  "Connect to nREPL server with connection pooling"
  [host port]
  (try
    (log :info "Connecting to nREPL at" (str host ":" port))
    (let [conn (nrepl/connect host port)]
      (swap! state assoc :nrepl-conn conn)
      (log :info "Connected to nREPL successfully")
      {:success true :connection conn})
    (catch Exception e
      (log :error "nREPL connection failed:" (.getMessage e))
      {:success false :error (.getMessage e)})))

(defn- ensure-nrepl-connection
  "Ensure we have a valid nREPL connection"
  []
  (if-let [conn (:nrepl-conn @state)]
    {:success true :connection conn}
    (let [workspace (get-in @state [:config :workspace])]
      (if-let [port (discover-nrepl-port workspace)]
        (connect-to-nrepl "localhost" port)
        {:success false :error "No nREPL connection and could not discover port"}))))

(defn- cache-command
  "Cache a command and its result for resource access"
  [code result]
  (let [max-cached (get-in @state [:config :max-cached-commands])
        command {:code code
                 :result result
                 :timestamp (str (java.time.Instant/now))}]
    (swap! state update :recent-commands
           (fn [commands]
             (take max-cached (cons command commands))))))

;; MCP Tool Implementations

(defn- tool-nrepl-connect
  "Connect to nREPL server"
  [{:keys [host port]}]
  (let [host (or host "localhost")
        port (or port (discover-nrepl-port (get-in @state [:config :workspace])))]
    (if port
      (let [result (connect-to-nrepl host port)]
        (if (:success result)
          {:content [{:type "text"
                     :text (str "✅ Connected to nREPL at " host ":" port)}]}
          {:content [{:type "text" 
                     :text (str "❌ Connection failed: " (:error result))}]
           :isError true}))
      {:content [{:type "text"
                 :text "❌ No port specified and could not discover .nrepl-port file"}]
       :isError true})))

(defn- tool-nrepl-eval
  "Evaluate Clojure code via nREPL"
  [{:keys [code session ns]}]
  (let [conn-result (ensure-nrepl-connection)]
    (if (:success conn-result)
      (try
        (let [conn (:connection conn-result)
              result (nrepl/eval-code conn code 
                                    :session session 
                                    :ns ns)]
          (cache-command code result)
          (log :debug "nREPL result:" result)
          
          ;; Store session info if provided in response
          (when-let [response-session (:session result)]
            (swap! state assoc-in [:sessions response-session] 
                   {:created (System/currentTimeMillis)
                    :last-used (System/currentTimeMillis)}))
          
          ;; Format clean response for MCP client
          (let [value-field (:value result)
                output-field (:out result)
                has-meaningful-value (and value-field 
                                         (not= "" value-field)
                                         (not= "nil" value-field))
                has-output (and output-field (not= "" (str/trim output-field)))
                has-error (:ex result)]
            (log :debug "Result keys:" (keys result))
            (log :debug "Value field exists?" (contains? result :value))
            (log :debug "Value field content:" (pr-str value-field))
            (log :debug "Output field content:" (pr-str output-field))
            (log :debug "Response decision: has-meaningful-value=" has-meaningful-value " has-output=" has-output " has-error=" has-error)
            (cond
              ;; Error in evaluation
              has-error
              {:content [{:type "text"
                         :text (str "❌ " (:ex result))}]
               :isError true}
              
              ;; Output (prefer output over nil values)
              has-output
              {:content [{:type "text"
                         :text (str/trim (:out result))}]
               :session (:session result)
               :namespace (:ns result)}
              
              ;; Meaningful value (non-nil)
              has-meaningful-value
              {:content [{:type "text"
                         :text (str (:value result))}]
               :session (:session result)
               :namespace (:ns result)}
              
              ;; Just status or nil value
              :else
              {:content [{:type "text"
                         :text "✅ Executed successfully"}]
               :session (:session result)
               :namespace (:ns result)})))
        (catch Exception e
          (log :error "nREPL eval failed:" (.getMessage e))
          {:content [{:type "text"
                     :text (str "❌ Evaluation failed: " (.getMessage e))}]
           :isError true}))
      {:content [{:type "text"
                 :text (str "❌ No nREPL connection: " (:error conn-result))}]
       :isError true})))

(defn- tool-nrepl-status
  "Get nREPL connection and session status"
  [_args]
  (let [conn (:nrepl-conn @state)
        sessions (:sessions @state)]
    {:content [{:type "text"
               :text (json/generate-string
                      {:connected (some? conn)
                       :host "localhost"
                       :workspace (get-in @state [:config :workspace])
                       :sessions (count sessions)
                       :recent-commands (count (:recent-commands @state))}
                      {:pretty true})}]}))

(defn- tool-nrepl-new-session
  "Create new nREPL session"
  [_args]
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
          (log :error "Session creation failed:" (.getMessage e))
          {:content [{:type "text"
                     :text (str "❌ Session creation failed: " (.getMessage e))}]
           :isError true}))
      {:content [{:type "text"
                 :text (str "❌ No nREPL connection: " (:error conn-result))}]
       :isError true})))

;; MCP Protocol Handlers

(def tool-definitions
  [{:name "nrepl-connect"
    :description "Connect to Joyride nREPL server"
    :inputSchema {:type "object"
                  :properties {:host {:type "string" :description "nREPL host (default: localhost)"}
                              :port {:type "number" :description "nREPL port (auto-discovered if not provided)"}}}}
   
   {:name "nrepl-eval"
    :description "Evaluate Clojure code in nREPL session"
    :inputSchema {:type "object"
                  :properties {:code {:type "string" :description "Clojure code to evaluate"}
                              :session {:type "string" :description "Session ID (optional)"}
                              :ns {:type "string" :description "Namespace context (optional)"}}
                  :required ["code"]}}
   
   {:name "nrepl-status"
    :description "Get nREPL connection and session status"
    :inputSchema {:type "object"}}
   
   {:name "nrepl-new-session"
    :description "Create new nREPL session"
    :inputSchema {:type "object"}}])

(defn- call-tool 
  "Execute an MCP tool by name"
  [tool-name args]
  (case tool-name
    "nrepl-connect" (tool-nrepl-connect args)
    "nrepl-eval" (tool-nrepl-eval args)
    "nrepl-status" (tool-nrepl-status args)
    "nrepl-new-session" (tool-nrepl-new-session args)
    {:content [{:type "text" :text (str "❌ Unknown tool: " tool-name)}]
     :isError true}))

(defn- handle-list-tools
  "Handle MCP tools/list request"
  [request]
  {:jsonrpc "2.0"
   :id (:id request)
   :result {:tools tool-definitions}})

(defn- handle-call-tool
  "Handle MCP tools/call request"
  [request]
  (try
    (let [tool-name (get-in request [:params :name])
          args (get-in request [:params :arguments] {})]
      (log :debug "Calling tool:" tool-name "with args:" args)
      (let [result (call-tool tool-name args)]
        {:jsonrpc "2.0"
         :id (:id request)
         :result result}))
    (catch Exception e
      (log :error "Tool call failed:" (.getMessage e))
      {:jsonrpc "2.0"
       :id (:id request)
       :error {:code -32603
               :message "Internal error"
               :data {:error (.getMessage e)}}})))

(defn- handle-initialize
  "Handle MCP initialize request"
  [request]
  {:jsonrpc "2.0"
   :id (:id request)
   :result {:protocolVersion "2024-11-05"
            :capabilities {:tools {}
                          :resources {}}
            :serverInfo {:name "mcp-nrepl-proxy"
                        :version "0.1.0"
                        :description "Babashka MCP server bridging Claude Code with Joyride nREPL"}}})

(defn- handle-list-resources
  "Handle MCP resources/list request"
  [request]
  (let [commands (:recent-commands @state)]
    {:jsonrpc "2.0"
     :id (:id request)
     :result {:resources (map-indexed
                          (fn [idx cmd]
                            {:uri (str "nrepl://commands/" idx)
                             :name (str "Command: " (subs (:code cmd) 0 (min 50 (count (:code cmd)))))
                             :description (str "Executed at " (:timestamp cmd))
                             :mimeType "application/json"})
                          commands)}}))

(defn- handle-read-resource
  "Handle MCP resources/read request"
  [request]
  (let [uri (:uri (:params request))
        commands (:recent-commands @state)]
    (if-let [match (re-matches #"nrepl://commands/(\d+)" uri)]
      (let [idx (Integer/parseInt (second match))]
        (if (< idx (count commands))
          {:jsonrpc "2.0"
           :id (:id request)
           :result {:contents [{:uri uri
                               :mimeType "application/json"
                               :text (json/generate-string (nth commands idx) {:pretty true})}]}}
          {:jsonrpc "2.0"
           :id (:id request)
           :error {:code -32602
                   :message "Resource not found"}}))
      {:jsonrpc "2.0"
       :id (:id request)
       :error {:code -32602
               :message "Invalid resource URI"}})))

(defn- handle-request
  "Route MCP requests to appropriate handlers"
  [request]
  (log :debug "Handling request:" (:method request))
  (case (:method request)
    "initialize" (handle-initialize request)
    "tools/list" (handle-list-tools request)
    "tools/call" (handle-call-tool request)
    "resources/list" (handle-list-resources request)
    "resources/read" (handle-read-resource request)
    ;; Unknown method
    {:jsonrpc "2.0"
     :id (:id request)
     :error {:code -32601
             :message "Method not found"}}))

(defn- stdio-server-loop
  "MCP server loop for stdin/stdout transport"
  []
  (log :info "🚀 MCP-nREPL proxy server starting (stdio)")
  (log :info "📡 Listening for MCP messages on stdin")
  
  (try
    (loop []
      (when-let [line (read-line)]
        (try
          (let [request (json/parse-string line true)]
            (log :debug "📥 Received:" (:method request))
            (let [response (handle-request request)]
              (println (json/generate-string response))
              (flush)))
          (catch Exception e
            (log :error "❌ Error processing message:" (.getMessage e))
            (try
              (let [request (json/parse-string line true)]
                (println (json/generate-string
                          {:jsonrpc "2.0"
                           :id (:id request)
                           :error {:code -32700
                                   :message "Parse error"}})))
              (catch Exception _
                (log :error "Could not send error response")))))
        (recur)))
    (catch Exception e
      (log :error "💥 Server loop error:" (.getMessage e)))))

(defn- http-handler
  "HTTP handler for MCP JSON-RPC requests"
  [request]
  (try
    (log :debug "📥 HTTP request:" (:request-method request) (:uri request))
    
    (cond
      ;; Handle MCP JSON-RPC POST requests
      (and (= (:request-method request) :post)
           (= (:uri request) "/mcp"))
      (let [body-str (slurp (:body request))
            mcp-request (json/parse-string body-str true)
            response (handle-request mcp-request)]
        (log :debug "📤 HTTP response for method:" (:method mcp-request))
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
      (log :error "❌ HTTP handler error:" (.getMessage e))
      {:status 500
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string {:error "Internal server error"
                                    :message (.getMessage e)})})))

(defn- start-http-server
  "Start HTTP server for MCP requests"
  [port]
  (log :info "🚀 MCP-nREPL proxy server starting (HTTP)")
  (log :info "📡 Listening for HTTP MCP requests on port" port)
  (log :info "🔗 MCP endpoint: http://localhost:" port "/mcp")
  (log :info "💚 Health check: http://localhost:" port "/health")
  
  (try
    (let [server (httpkit/run-server http-handler {:port port})]
      (log :info "✅ HTTP server started on port" port)
      (.addShutdownHook (Runtime/getRuntime)
                        (Thread. #(do
                                    (log :info "🛑 Shutting down HTTP server...")
                                    (server))))
      ;; Keep the main thread alive
      (loop []
        (Thread/sleep 1000)
        (recur)))
    (catch Exception e
      (log :error "💥 HTTP server error:" (.getMessage e)))))

(defn -main
  "Main entry point for Babashka MCP-nREPL proxy"
  [& args]
  (let [http-port (some->> args first Integer/parseInt)
        use-http (or http-port (System/getenv "MCP_HTTP_PORT"))
        port (or http-port 
                 (some->> (System/getenv "MCP_HTTP_PORT") Integer/parseInt)
                 3000)
        config {:debug (= "true" (System/getenv "MCP_DEBUG"))
                :workspace (or (System/getenv "JOYRIDE_WORKSPACE")
                              (System/getProperty "user.dir"))
                :transport (if use-http :http :stdio)
                :http-port port}]
    (swap! state assoc :config config)
    
    (log :info "🔧 MCP-nREPL Proxy Configuration:")
    (log :info "   Debug mode:" (:debug config))
    (log :info "   Workspace:" (:workspace config))
    (log :info "   Transport:" (:transport config))
    (when (= :http (:transport config))
      (log :info "   HTTP port:" (:http-port config)))
    
    ;; Try to auto-discover and connect to Joyride nREPL
    (when-let [nrepl-port (or (some->> (System/getenv "NREPL_PORT") Integer/parseInt)
                              (discover-nrepl-port (:workspace config)))]
      (log :info "🔍 Found nREPL port:" nrepl-port)
      (let [result (connect-to-nrepl "localhost" nrepl-port)]
        (if (:success result)
          (log :info "✅ Auto-connected to Joyride nREPL")
          (log :warn "⚠️  Auto-connection failed, use nrepl-connect tool"))))
    
    ;; Start appropriate server
    (if (= :http (:transport config))
      (start-http-server (:http-port config))
      (stdio-server-loop))))

;; Enable direct script execution with shebang
(when (= *file* (System/getProperty "babashka.file"))
  (-main))