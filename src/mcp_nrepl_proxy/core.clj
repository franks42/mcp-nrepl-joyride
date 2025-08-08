#!/usr/bin/env bb

(ns mcp-nrepl-proxy.core
  "Babashka MCP server bridging Claude Code with Joyride nREPL.
   
   Pure Babashka implementation using native nREPL client capabilities.
   Supports both stdio and HTTP transports."
  (:require [cheshire.core :as json]
            [mcp-nrepl-proxy.nrepl-client :as nrepl]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [org.httpkit.server :as httpkit]
            [babashka.nrepl.server :as nrepl-server])
  (:import [java.util Base64]))

(def ^:private state
  "Server state: nREPL connections, sessions, and configuration"
  (atom {:nrepl-conn nil
         :sessions {}
         :recent-commands []
         :health-status {:connected false
                        :last-heartbeat nil
                        :heartbeat-failures 0
                        :last-test-results nil}
         :babashka-nrepl-server nil
         :config {:debug false
                  :workspace nil
                  :max-cached-commands 10
                  :heartbeat-interval-ms 45000
                  :babashka-nrepl-port 7889}}))

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
  "Discover nREPL port from .nrepl-port file in workspace or .joyride subdirectory"
  [workspace-path]
  (let [port-files [(fs/file workspace-path ".nrepl-port")
                    (fs/file workspace-path ".joyride" ".nrepl-port")]]
    (some (fn [port-file]
            (when (fs/exists? port-file)
              (try
                (let [port (Integer/parseInt (str/trim (slurp port-file)))]
                  (log :info "Found nREPL port" port "in" (str port-file))
                  port)
                (catch Exception e
                  (log :warn "Could not parse .nrepl-port file:" (.getMessage e))
                  nil))))
          port-files)))

(defn- heartbeat-test
  "Simple heartbeat test using nREPL describe operation"
  [conn]
  (try
    (let [result (nrepl/describe-server conn)]
      (contains? result :ops))
    (catch Exception e
      (log :debug "Heartbeat failed:" (.getMessage e))
      false)))

(defn- start-heartbeat-monitor
  "Start background heartbeat monitoring"
  []
  (log :info "Starting nREPL heartbeat monitor")
  (future
    (loop []
      (Thread/sleep (get-in @state [:config :heartbeat-interval-ms]))
      (when-let [conn (:nrepl-conn @state)]
        (let [heartbeat-success (heartbeat-test conn)
              now (System/currentTimeMillis)]
          (if heartbeat-success
            (do
              (swap! state update-in [:health-status] assoc
                     :connected true
                     :last-heartbeat now
                     :heartbeat-failures 0)
              (log :debug "Heartbeat successful"))
            (do
              (swap! state update-in [:health-status] 
                     (fn [health]
                       (assoc health
                              :connected false
                              :heartbeat-failures (inc (:heartbeat-failures health)))))
              (log :warn "Heartbeat failed, failure count:" 
                   (get-in @state [:health-status :heartbeat-failures]))))))
      (recur))))

(defn- connect-to-nrepl
  "Connect to nREPL server with connection pooling and heartbeat monitoring"
  [host port]
  (try
    (log :info "Connecting to nREPL at" (str host ":" port))
    (let [conn (nrepl/connect host port)]
      (swap! state assoc :nrepl-conn conn)
      (swap! state update-in [:health-status] assoc
             :connected true
             :last-heartbeat (System/currentTimeMillis)
             :heartbeat-failures 0)
      (log :info "Connected to nREPL successfully")
      {:success true :connection conn})
    (catch Exception e
      (log :error "nREPL connection failed:" (.getMessage e))
      (swap! state update-in [:health-status] assoc :connected false)
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
  (let [max-cached (or (get-in @state [:config :max-cached-commands]) 10)
        command {:code code
                 :result result
                 :timestamp (str (java.time.Instant/now))}]
    (swap! state update :recent-commands
           (fn [commands]
             (vec (take max-cached (cons command commands)))))))

;; Helper functions for Calva introspection

(defn get-server-state
  "Get current server state for introspection"
  []
  @state)

(defn get-joyride-connection
  "Get current Joyride nREPL connection details"
  []
  (when-let [conn (:nrepl-conn @state)]
    {:host (:host conn)
     :port (:port conn)
     :connected true}))

(defn get-mcp-stats
  "Get MCP server statistics"
  []
  {:sessions (count (:sessions @state))
   :recent-commands (count (:recent-commands @state))
   :health (:health-status @state)
   :transport (get-in @state [:config :transport])
   :debug (get-in @state [:config :debug])})

(defn eval-in-joyride
  "Evaluate code in connected Joyride nREPL (for Calva convenience)"
  [code]
  (if-let [conn (:nrepl-conn @state)]
    (try
      (nrepl/eval-code conn code)
      (catch Exception e
        {:error (.getMessage e)}))
    {:error "No Joyride nREPL connection"}))

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
          (log :error "Exception type:" (type e))
          (log :error "Stack trace:" (with-out-str (.printStackTrace e)))
          {:content [{:type "text"
                     :text (str "❌ Evaluation failed: " (.getMessage e) " (type: " (type e) ")")}]
           :isError true}))
      {:content [{:type "text"
                 :text (str "❌ No nREPL connection: " (:error conn-result))}]
       :isError true})))

(defn- tool-nrepl-status
  "Get nREPL connection and session status with health information"
  [_args]
  (let [conn (:nrepl-conn @state)
        sessions (:sessions @state)
        health (:health-status @state)
        last-test (:last-test-results health)]
    {:content [{:type "text"
               :text (json/generate-string
                      {:connected (some? conn)
                       :host (when conn (:host conn))
                       :port (when conn (:port conn))
                       :workspace (get-in @state [:config :workspace])
                       :sessions (count sessions)
                       :recent-commands (count (:recent-commands @state))
                       :health {:heartbeat-connected (:connected health)
                               :last-heartbeat (:last-heartbeat health)
                               :heartbeat-failures (:heartbeat-failures health)
                               :last-test-passed (when last-test (:all-passed last-test))
                               :last-test-timestamp (when last-test (:timestamp last-test))}}
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

(defn- run-health-test
  "Run comprehensive nREPL health tests"
  [conn]
  (let [tests [
               {:name "Server Description"
                :test-fn (fn [] 
                          (let [result (nrepl/describe-server conn)]
                            {:success (contains? result :ops)
                             :result (if (contains? result :ops)
                                      (str "✅ Server alive with " (count (:ops result)) " operations")
                                      (str "❌ Invalid describe response: " result))}))}
               
               {:name "Basic Arithmetic"
                :test-fn (fn []
                          (let [result (nrepl/eval-code conn "(+ 2 3)")]
                            {:success (= "5" (:value result))
                             :result (if (= "5" (:value result))
                                      "✅ Basic arithmetic: (+ 2 3) → 5"
                                      (str "❌ Expected '5', got: " (:value result)))}))}
               
               {:name "String Operations"
                :test-fn (fn []
                          (let [result (nrepl/eval-code conn "(str \"hello\" \" \" \"world\")")]
                            {:success (= "\"hello world\"" (:value result))
                             :result (if (= "\"hello world\"" (:value result))
                                      "✅ String ops: (str ...) → \"hello world\""
                                      (str "❌ Expected '\"hello world\"', got: " (:value result)))}))}
               
               {:name "Data Structures"
                :test-fn (fn []
                          (let [result (nrepl/eval-code conn "(count [1 2 3 4 5])")]
                            {:success (= "5" (:value result))
                             :result (if (= "5" (:value result))
                                      "✅ Data structures: (count [1 2 3 4 5]) → 5"
                                      (str "❌ Expected '5', got: " (:value result)))}))}
               
               {:name "Output Handling"
                :test-fn (fn []
                          (let [result (nrepl/eval-code conn "(println \"test-output\")")]
                            {:success (and (:out result) (str/includes? (:out result) "test-output"))
                             :result (if (and (:out result) (str/includes? (:out result) "test-output"))
                                      "✅ Output handling: println captured correctly"
                                      (str "❌ Output not captured, got: " (:out result)))}))}
               
               {:name "Babashka nREPL Server"
                :test-fn (fn []
                          (try
                            ;; Start server if not running
                            (when-not (:babashka-nrepl-server @state)
                              (log :info "Starting Babashka nREPL server for testing...")
                              ;; Start server directly without using tool function
                              (try
                                (let [server (nrepl-server/start-server! {:port 7889 :quiet true})]
                                  (swap! state assoc 
                                         :babashka-nrepl-server server
                                         :babashka-nrepl-port 7889)
                                  (log :info "✅ Babashka nREPL server started for testing"))
                                (catch Exception e
                                  (log :warn "Failed to start Babashka server:" (.getMessage e)))))
                            
                            ;; Test connection to Babashka nREPL
                            (if-let [bb-port (:babashka-nrepl-port @state)]
                              (try
                                (log :info "Testing Babashka nREPL connection on port" bb-port)
                                (let [bb-conn (nrepl/connect "localhost" bb-port)
                                      ;; Test basic evaluation
                                      eval-result (nrepl/eval-code bb-conn "(* 7 6)")
                                      eval-success (= "42" (:value eval-result))
                                      ;; Test Babashka-specific functionality
                                      bb-check (nrepl/eval-code bb-conn "(System/getProperty \"babashka.version\")")
                                      has-bb-version (some? (:value bb-check))
                                      ;; Test self-connection capability
                                      self-test (nrepl/eval-code bb-conn "(require '[babashka.nrepl.server]) ::loaded")
                                      self-success (= ":user/loaded" (:value self-test))]
                                  ;; Test connection cleanup - simple close if possible
                                  (try (.close bb-conn) (catch Exception _))
                                  {:success (and eval-success has-bb-version self-success)
                                   :result (str "✅ Babashka server: eval=" eval-success 
                                              ", version=" has-bb-version 
                                              ", self-conn=" self-success 
                                              " (port " bb-port ")")})
                                (catch Exception e
                                  {:success false
                                   :result (str "❌ Babashka connection failed: " (.getMessage e))}))
                              {:success false
                               :result "❌ Babashka server not started"})
                            (catch Exception e
                              {:success false
                               :result (str "❌ Babashka test error: " (.getMessage e))})))}]]
    
    (reduce (fn [acc test]
              (try
                (let [start-time (System/currentTimeMillis)
                      test-result ((:test-fn test))
                      duration (- (System/currentTimeMillis) start-time)]
                  (conj acc (assoc test-result 
                                  :test-name (:name test)
                                  :duration-ms duration)))
                (catch Exception e
                  (conj acc {:test-name (:name test)
                            :success false
                            :result (str "❌ " (:name test) " failed: " (.getMessage e))
                            :duration-ms 0}))))
            [] tests)))

(defn- tool-nrepl-test
  "Run comprehensive nREPL health tests"
  [_args]
  (if-let [conn (:nrepl-conn @state)]
    (try
      (log :info "Running nREPL health tests...")
      (let [start-time (System/currentTimeMillis)
            test-results (run-health-test conn)
            total-duration (- (System/currentTimeMillis) start-time)
            passed-tests (count (filter :success test-results))
            total-tests (count test-results)
            all-passed (= passed-tests total-tests)]
        
        ;; Store results in state
        (swap! state assoc-in [:health-status :last-test-results] 
               {:timestamp (System/currentTimeMillis)
                :passed passed-tests
                :total total-tests
                :all-passed all-passed
                :duration-ms total-duration})
        
        (let [summary (str (if all-passed "✅" "❌") " Health Test Results: " 
                          passed-tests "/" total-tests " tests passed"
                          " (took " total-duration "ms)")
              details (str/join "\n" (map :result test-results))]
          {:content [{:type "text"
                     :text (str summary "\n\n" details)}]
           :isError (not all-passed)}))
      (catch Exception e
        (log :error "Health test failed:" (.getMessage e))
        {:content [{:type "text"
                   :text (str "❌ Health test failed: " (.getMessage e))}]
         :isError true}))
    {:content [{:type "text"
               :text "❌ No nREPL connection available for testing"}]
     :isError true}))

(defn- tool-nrepl-load-file
  "Load a Clojure file into the nREPL session"
  [{:keys [file-path session ns]}]
  (let [conn-result (ensure-nrepl-connection)]
    (if (:success conn-result)
      (try
        ;; Validate file exists and is readable
        (when-not (and file-path (.exists (java.io.File. file-path)))
          (throw (Exception. (str "File not found: " file-path))))
        
        (let [conn (:connection conn-result)
              result (nrepl/load-file conn file-path
                                    :session session
                                    :ns ns)]
          (log :debug "Load-file result:" result)
          
          ;; Store session info if provided in response
          (when-let [response-session (:session result)]
            (swap! state assoc-in [:sessions response-session] 
                   {:created (System/currentTimeMillis)
                    :last-used (System/currentTimeMillis)}))
          
          ;; Format response similar to eval
          (let [has-error (:ex result)
                has-output (and (:out result) (not= "" (str/trim (:out result))))]
            (cond
              has-error
              {:content [{:type "text"
                         :text (str "❌ Load failed: " (:ex result))}]
               :isError true}
              
              has-output
              {:content [{:type "text"
                         :text (str "✅ File loaded: " file-path "\n" (:out result))}]}
              
              :else
              {:content [{:type "text"
                         :text (str "✅ File loaded successfully: " file-path)}]})))
        
        (catch Exception e
          (log :error "Load-file failed:" (.getMessage e))
          {:content [{:type "text"
                     :text (str "❌ Load failed: " (.getMessage e))}]
           :isError true}))
      {:content [{:type "text"
                 :text "❌ No nREPL connection available. Use nrepl-connect first."}]
       :isError true})))

(defn- tool-nrepl-doc
  "Get documentation for a Clojure symbol"
  [{:keys [symbol session ns]}]
  (let [conn-result (ensure-nrepl-connection)]
    (if (:success conn-result)
      (try
        (let [conn (:connection conn-result)
              result (nrepl/doc conn symbol :session session :ns ns)
              doc-text (:doc result)
              arglists (:arglists result)]
          (if (or doc-text arglists)
            {:content [{:type "text"
                       :text (str "📖 Documentation for " symbol "\n\n"
                                (when arglists (str "Usage: " arglists "\n\n"))
                                (or doc-text "No documentation available."))}]}
            {:content [{:type "text"
                       :text (str "❌ No documentation found for: " symbol)}]
             :isError true}))
        (catch Exception e
          (log :error "Doc lookup failed:" (.getMessage e))
          {:content [{:type "text"
                     :text (str "❌ Doc lookup failed: " (.getMessage e))}]
           :isError true}))
      {:content [{:type "text"
                 :text "❌ No nREPL connection available. Use nrepl-connect first."}]
       :isError true})))

(defn- tool-nrepl-source
  "Get source code for a Clojure symbol"
  [{:keys [symbol session ns]}]
  (let [conn-result (ensure-nrepl-connection)]
    (if (:success conn-result)
      (try
        (let [conn (:connection conn-result)
              result (nrepl/source conn symbol :session session :ns ns)
              source-text (:source result)
              file (:file result)]
          (if source-text
            {:content [{:type "text"
                       :text (str "📄 Source code for " symbol
                                (when file (str " from " file)) "\n\n"
                                "```clojure\n" source-text "\n```")}]}
            {:content [{:type "text"
                       :text (str "❌ No source code found for: " symbol)}]
             :isError true}))
        (catch Exception e
          (log :error "Source lookup failed:" (.getMessage e))
          {:content [{:type "text"
                     :text (str "❌ Source lookup failed: " (.getMessage e))}]
           :isError true}))
      {:content [{:type "text"
                 :text "❌ No nREPL connection available. Use nrepl-connect first."}]
       :isError true})))

(defn- tool-nrepl-complete
  "Get symbol completions for a prefix"
  [{:keys [prefix session ns context]}]
  (let [conn-result (ensure-nrepl-connection)]
    (if (:success conn-result)
      (try
        (let [conn (:connection conn-result)
              result (nrepl/complete conn prefix :session session :ns ns :context context)
              completions (:completions result)]
          (if (and completions (seq completions))
            {:content [{:type "text"
                       :text (str "🔍 Completions for \"" prefix "\":\n\n"
                                (->> completions
                                     (take 20) ; Limit to first 20 results
                                     (map-indexed (fn [i completion]
                                                   (str (inc i) ". " completion)))
                                     (str/join "\n")))}]}
            {:content [{:type "text"
                       :text (str "❌ No completions found for: " prefix)}]
             :isError true}))
        (catch Exception e
          (log :error "Completion failed:" (.getMessage e))
          {:content [{:type "text"
                     :text (str "❌ Completion failed: " (.getMessage e))}]
           :isError true}))
      {:content [{:type "text"
                 :text "❌ No nREPL connection available. Use nrepl-connect first."}]
       :isError true})))

(defn- tool-nrepl-apropos
  "Find symbols matching a pattern"
  [{:keys [query session ns search-ns privates? case-sensitive?]}]
  (let [conn-result (ensure-nrepl-connection)]
    (if (:success conn-result)
      (try
        (let [conn (:connection conn-result)
              result (nrepl/apropos conn query 
                                  :session session 
                                  :ns ns
                                  :search-ns search-ns
                                  :privates? privates?
                                  :case-sensitive? case-sensitive?)
              symbols (:apropos-matches result)]
          (if (and symbols (seq symbols))
            {:content [{:type "text"
                       :text (str "🔍 Symbols matching \"" query "\":\n\n"
                                (->> symbols
                                     (take 30) ; Limit to first 30 results
                                     (map-indexed (fn [i sym]
                                                   (str (inc i) ". " sym)))
                                     (str/join "\n")))}]}
            {:content [{:type "text"
                       :text (str "❌ No symbols found matching: " query)}]
             :isError true}))
        (catch Exception e
          (log :error "Apropos search failed:" (.getMessage e))
          {:content [{:type "text"
                     :text (str "❌ Apropos search failed: " (.getMessage e))}]
           :isError true}))
      {:content [{:type "text"
                 :text "❌ No nREPL connection available. Use nrepl-connect first."}]
       :isError true})))

(defn- tool-nrepl-require
  "Require/load a namespace"
  [{:keys [namespace session as refer reload]}]
  (let [conn-result (ensure-nrepl-connection)]
    (if (:success conn-result)
      (try
        (let [conn (:connection conn-result)
              result (nrepl/require-ns conn (symbol namespace)
                                     :session session
                                     :as (when as (symbol as))
                                     :refer refer
                                     :reload reload)]
          (if (:ex result)
            {:content [{:type "text"
                       :text (str "❌ Require failed: " (:ex result))}]
             :isError true}
            {:content [{:type "text"
                       :text (str "✅ Successfully required " namespace
                                (when as (str " as " as))
                                (when refer (str " referring " refer))
                                (when reload " (with reload)"))}]}))
        (catch Exception e
          (log :error "Require failed:" (.getMessage e))
          {:content [{:type "text"
                     :text (str "❌ Require failed: " (.getMessage e))}]
           :isError true}))
      {:content [{:type "text"
                 :text "❌ No nREPL connection available. Use nrepl-connect first."}]
       :isError true})))

(defn- tool-nrepl-interrupt
  "Interrupt running evaluation"
  [{:keys [session interrupt-id]}]
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
          (log :error "Interrupt failed:" (.getMessage e))
          {:content [{:type "text"
                     :text (str "❌ Interrupt failed: " (.getMessage e))}]
           :isError true}))
      {:content [{:type "text"
                 :text "❌ No nREPL connection available. Use nrepl-connect first."}]
       :isError true})))

(defn- tool-nrepl-stacktrace
  "Get stacktrace for the last exception"
  [{:keys [session]}]
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
          (log :error "Stacktrace lookup failed:" (.getMessage e))
          {:content [{:type "text"
                     :text (str "❌ Stacktrace lookup failed: " (.getMessage e))}]
           :isError true}))
      {:content [{:type "text"
                 :text "❌ No nREPL connection available. Use nrepl-connect first."}]
       :isError true})))

(defn- tool-babashka-nrepl
  "Manage Babashka nREPL server for debugging tools"
  [{:keys [op port port-path]}]
  (let [op (keyword op)
        port (or port 7889)
        port-path (or port-path 
                     (if (fs/writable? ".")
                       ".babashka-nrepl-port"
                       (str (System/getProperty "java.io.tmpdir") "/babashka-nrepl-port")))
        log-path (str (System/getProperty "java.io.tmpdir") "/babashka-nrepl.log")
        workspace (get-in @state [:config :workspace])]
    (case op
      :start
      (if (:babashka-nrepl-server @state)
        {:content [{:type "text"
                   :text (json/generate-string
                          {:status "already-running"
                           :port (:babashka-nrepl-port @state)
                           :port-file (fs/absolutize port-path)
                           :log-file (fs/absolutize log-path)
                           :message "Babashka nREPL server is already running"}
                          {:pretty true})}]}
        (try
          ;; Start server with quiet option to suppress stdout
          (let [server (nrepl-server/start-server! {:port port :quiet true})]
            (swap! state assoc 
                   :babashka-nrepl-server server
                   :babashka-nrepl-port port)
            ;; Try to write port file
            (let [port-written (try
                                (spit port-path (str port))
                                true
                                (catch Exception e
                                  (log :warn "Could not write port file to" port-path ":" (.getMessage e))
                                  false))]
              {:content [{:type "text"
                         :text (json/generate-string
                                {:status "started"
                                 :port port
                                 :port-file (if port-written
                                             (str (fs/absolutize port-path))
                                             nil)
                                 :port-file-writable port-written
                                 :log-file (str (fs/absolutize log-path))
                                 :message (str "✅ Babashka nREPL server started on port " port
                                             "\nConnect Calva to: localhost:" port
                                             (when-not port-written 
                                               "\n⚠️  Could not write port file"))}
                                {:pretty true})}]}))
          (catch Exception e
            (log :error "Failed to start Babashka nREPL server:" (.getMessage e))
            {:content [{:type "text"
                       :text (json/generate-string
                              {:status "error"
                               :error (.getMessage e)
                               :message (str "❌ Failed to start server: " (.getMessage e))}
                              {:pretty true})}]
             :isError true})))
      
      :stop
      (if-let [server (:babashka-nrepl-server @state)]
        (try
          (.close server)
          (swap! state dissoc :babashka-nrepl-server :babashka-nrepl-port)
          ;; Try to remove port file
          (try (fs/delete port-path) (catch Exception _))
          {:content [{:type "text"
                     :text (json/generate-string
                            {:status "stopped"
                             :message "✅ Babashka nREPL server stopped"}
                            {:pretty true})}]}
          (catch Exception e
            {:content [{:type "text"
                       :text (json/generate-string
                              {:status "error"
                               :error (.getMessage e)
                               :message (str "❌ Error stopping server: " (.getMessage e))}
                              {:pretty true})}]
             :isError true}))
        {:content [{:type "text"
                   :text (json/generate-string
                          {:status "not-running"
                           :message "No Babashka nREPL server is running"}
                          {:pretty true})}]})
      
      :status
      (let [running (boolean (:babashka-nrepl-server @state))
            port (:babashka-nrepl-port @state)]
        {:content [{:type "text"
                   :text (json/generate-string
                          {:status (if running "running" "stopped")
                           :running running
                           :port (when running port)
                           :port-file (when running (str (fs/absolutize port-path)))
                           :log-file (when running (str (fs/absolutize log-path)))
                           :message (if running
                                     (str "✅ Babashka nREPL server running on port " port)
                                     "⚠️  Babashka nREPL server is not running")}
                          {:pretty true})}]})
      
      ;; Invalid operation
      {:content [{:type "text"
                 :text (json/generate-string
                        {:status "error"
                         :error "Invalid operation"
                         :message (str "❌ Invalid operation: " op ". Use 'start', 'stop', or 'status'")}
                        {:pretty true})}]
       :isError true})))

(defn- tool-get-mcp-nrepl-context
  "Get comprehensive context document for AI assistants"
  [_args]
  (try
    (let [context-file "AI-CONTEXT.md"
          context-content (slurp context-file)]
      {:content [{:type "text"
                 :text context-content}]})
    (catch Exception e
      (log :error "Failed to read context document:" (.getMessage e))
      {:content [{:type "text"
                 :text (str "# MCP-nREPL Server Context\n\n"
                           "## Overview\n\n"
                           "This MCP server bridges AI assistants with Clojure/ClojureScript development environments "
                           "through the nREPL protocol. It provides 15 MCP functions for executing Clojure code, "
                           "controlling VS Code through Joyride, exploring codebases, and building interactive applications.\n\n"
                           "## Essential First Steps\n\n"
                           "1. **Always start with `nrepl-health-check()`** to understand your environment (if no nREPL server connected, use `babashka-nrepl({op: 'start'})` first)\n"
                           "2. **Check current namespace** with `nrepl-eval({code: \"*ns*\"})`\n"
                           "3. **Discover available functions** with `nrepl-apropos({query: \"keyword\"})`\n"
                           "4. **Get documentation** with `nrepl-doc({symbol: \"function-name\"})`\n\n"
                           "## Core Functions\n\n"
                           "- **nrepl-eval**: Execute Clojure code (primary tool)\n"
                           "- **nrepl-health-check**: Environment diagnostics\n"
                           "- **nrepl-doc/source/apropos**: Code exploration\n"
                           "- **nrepl-require**: Load namespaces\n"
                           "- **nrepl-load-file**: Load Clojure files\n\n"
                           "## Remember\n\n"
                           "Start simple, test incrementally, and use the health check to understand your environment!")}]})))

(defn- run-comprehensive-health-check
  "Run comprehensive system health check with detailed diagnostics"
  [conn & {:keys [include-performance include-integration verbose]
           :or {include-performance true include-integration true verbose false}}]
  (let [start-time (System/currentTimeMillis)
        results (atom {:sections [] :overall-health :unknown})]
    
    ;; 1. Environment Diagnostics
    (let [env-start (System/currentTimeMillis)]
      (try
        (let [java-version (System/getProperty "java.version")
              os-name (System/getProperty "os.name")
              os-arch (System/getProperty "os.arch")
              bb-version (try 
                           (let [bb-proc (ProcessBuilder. ["bb" "--version"])]
                             (-> bb-proc .start .getInputStream slurp str/trim))
                           (catch Exception _ "Unknown"))
              memory-info (let [rt (Runtime/getRuntime)]
                           {:total (.totalMemory rt)
                            :free (.freeMemory rt)
                            :max (.maxMemory rt)
                            :used (- (.totalMemory rt) (.freeMemory rt))})
              env-duration (- (System/currentTimeMillis) env-start)]
          (swap! results update :sections conj 
                 {:name "🔧 Environment Diagnostics"
                  :status :success
                  :duration-ms env-duration
                  :details [(str "✅ Java Version: " java-version)
                           (str "✅ OS: " os-name " (" os-arch ")")
                           (str "✅ Babashka Version: " (str/trim bb-version))
                           (str "✅ Memory: " (long (/ (:used memory-info) 1024 1024)) "MB used / " 
                                (long (/ (:max memory-info) 1024 1024)) "MB max")
                           (when verbose
                             (str "📊 Detailed Memory: Total=" (long (/ (:total memory-info) 1024 1024)) 
                                  "MB, Free=" (long (/ (:free memory-info) 1024 1024)) "MB"))]}))
        (catch Exception e
          (swap! results update :sections conj
                 {:name "🔧 Environment Diagnostics"
                  :status :error
                  :duration-ms (- (System/currentTimeMillis) env-start)
                  :details [(str "❌ Environment check failed: " (.getMessage e))]}))))
    
    ;; 2. Connection Health
    (let [conn-start (System/currentTimeMillis)]
      (try
        (let [server-desc (nrepl/describe-server conn)
              ops-count (count (:ops server-desc))
              versions (:versions server-desc)
              conn-duration (- (System/currentTimeMillis) conn-start)]
          (swap! results update :sections conj
                 {:name "🔌 Connection Health"
                  :status :success
                  :duration-ms conn-duration
                  :details [(str "✅ nREPL Server Connected")
                           (str "✅ Operations Available: " ops-count)
                           (str "✅ nREPL Version: " (get versions "nrepl" "unknown"))
                           (str "✅ Clojure Version: " (get versions "clojure" "unknown"))
                           (when verbose
                             (str "📋 Available Operations: " (str/join ", " (take 10 (keys (:ops server-desc)))) 
                                  (when (> ops-count 10) (str " (+" (- ops-count 10) " more)"))))]}))
        (catch Exception e
          (swap! results update :sections conj
                 {:name "🔌 Connection Health"
                  :status :error
                  :duration-ms (- (System/currentTimeMillis) conn-start)
                  :details [(str "❌ Connection test failed: " (.getMessage e))]}))))

    ;; 3. Core Functionality Tests  
    (let [func-start (System/currentTimeMillis)
          core-tests [
                      {:name "Basic Arithmetic" 
                       :test #(nrepl/eval-code conn "(+ 2 3)")
                       :expect "5"}
                      {:name "String Operations"
                       :test #(nrepl/eval-code conn "(str \"hello\" \" \" \"world\")")
                       :expect "\"hello world\""}
                      {:name "Data Structures"
                       :test #(nrepl/eval-code conn "(count [1 2 3 4 5])")
                       :expect "5"}
                      {:name "Symbol Resolution"
                       :test #(nrepl/eval-code conn "(resolve 'map)")
                       :expect-fn #(or (str/includes? % "function") 
                                      (str/includes? % "clojure.core/map")
                                      (str/includes? % "#'")
                                      (not (str/includes? % "nil")))}
                      {:name "Namespace Operations"
                       :test #(nrepl/eval-code conn "(str *ns*)")
                       :expect-fn #(str/includes? % "user")}]]
      (let [test-results (mapv (fn [{:keys [name test expect expect-fn]}]
                                (try
                                  (let [test-start (System/currentTimeMillis)
                                        result (test)
                                        duration (- (System/currentTimeMillis) test-start)
                                        success (cond
                                                 expect (= expect (:value result))
                                                 expect-fn (expect-fn (str (:value result)))
                                                 :else false)]
                                    {:name name
                                     :success success
                                     :duration-ms duration
                                     :result (if success
                                              (str "✅ " name ": " (:value result))
                                              (str "❌ " name " failed - Expected: " (or expect "custom check") 
                                                   ", Got: " (:value result)))})
                                  (catch Exception e
                                    {:name name
                                     :success false
                                     :duration-ms 0
                                     :result (str "❌ " name " error: " (.getMessage e))})))
                              core-tests)
            func-duration (- (System/currentTimeMillis) func-start)
            passed (count (filter :success test-results))
            total (count test-results)]
        (swap! results update :sections conj
               {:name "⚙️ Core Functionality"
                :status (if (= passed total) :success :partial)
                :duration-ms func-duration
                :details (conj (mapv :result test-results)
                              (str "📊 Summary: " passed "/" total " core tests passed"))})))

    ;; 4. Tool Integration Tests
    (when include-integration
      (let [integration-start (System/currentTimeMillis)
            tool-tests [
                        {:name "Session Creation"
                         :test #(nrepl/create-session conn)}
                        {:name "Symbol Documentation"
                         :test #(nrepl/doc conn "map")}
                        {:name "Code Completion"
                         :test #(nrepl/complete conn "ma")}
                        {:name "Symbol Search"
                         :test #(nrepl/apropos conn "map")}]]
        (let [integration-results (mapv (fn [{:keys [name test]}]
                                          (try
                                            (let [test-start (System/currentTimeMillis)
                                                  result (test)
                                                  duration (- (System/currentTimeMillis) test-start)
                                                  success (and result (not (:ex result)))]
                                              {:name name
                                               :success success
                                               :duration-ms duration
                                               :result (if success
                                                        (str "✅ " name " working")
                                                        (str "❌ " name " failed: " (or (:ex result) "No response")))})
                                            (catch Exception e
                                              {:name name
                                               :success false
                                               :duration-ms 0
                                               :result (str "❌ " name " error: " (.getMessage e))})))
                                        tool-tests)
              integration-duration (- (System/currentTimeMillis) integration-start)
              passed (count (filter :success integration-results))
              total (count integration-results)]
          (swap! results update :sections conj
                 {:name "🔗 Tool Integration"
                  :status (if (= passed total) :success :partial)
                  :duration-ms integration-duration
                  :details (conj (mapv :result integration-results)
                                (str "📊 Summary: " passed "/" total " integration tests passed"))}))))

    ;; 5. Performance Benchmarks
    (when include-performance
      (let [perf-start (System/currentTimeMillis)
            perf-tests [
                        {:name "Simple Expression"
                         :test #(nrepl/eval-code conn "(+ 1 1)")
                         :iterations 10}
                        {:name "Collection Processing"
                         :test #(nrepl/eval-code conn "(reduce + (range 100))")
                         :iterations 5}
                        {:name "String Manipulation"
                         :test #(nrepl/eval-code conn "(str/join \", \" (map str (range 10)))")
                         :iterations 5}]]
        (let [perf-results (mapv (fn [{:keys [name test iterations]}]
                                   (try
                                     (let [times (repeatedly iterations 
                                                            (fn []
                                                              (let [start (System/currentTimeMillis)]
                                                                (test)
                                                                (- (System/currentTimeMillis) start))))
                                           avg-time (/ (reduce + times) (count times))
                                           min-time (apply min times)
                                           max-time (apply max times)]
                                       {:name name
                                        :success true
                                        :avg-ms avg-time
                                        :min-ms min-time
                                        :max-ms max-time
                                        :result (str "✅ " name ": avg=" (long avg-time) "ms, "
                                                    "min=" (long min-time) "ms, "
                                                    "max=" (long max-time) "ms")})
                                     (catch Exception e
                                       {:name name
                                        :success false
                                        :result (str "❌ " name " benchmark failed: " (.getMessage e))})))
                                 perf-tests)
              perf-duration (- (System/currentTimeMillis) perf-start)
              passed (count (filter :success perf-results))
              total (count perf-results)]
          (swap! results update :sections conj
                 {:name "⚡ Performance Benchmarks"
                  :status (if (= passed total) :success :partial)
                  :duration-ms perf-duration
                  :details (conj (mapv :result perf-results)
                                (str "📊 Summary: " passed "/" total " benchmarks completed"))}))))

    ;; 6. Configuration Validation
    (let [config-start (System/currentTimeMillis)]
      (try
        (let [config (:config @state)
              port-check (if-let [conn (:nrepl-conn @state)]
                          "✅ nREPL connection active"
                          "❌ nREPL connection inactive")
              bb-nrepl-status (if (:babashka-nrepl-server @state)
                               "✅ Babashka nREPL server running"
                               "⚠️ Babashka nREPL server not started")
              config-duration (- (System/currentTimeMillis) config-start)]
          (swap! results update :sections conj
                 {:name "⚙️ Configuration Status"
                  :status :success
                  :duration-ms config-duration
                  :details [(str port-check)
                           (str bb-nrepl-status)
                           (str "✅ Debug Mode: " (:debug config))
                           (str "✅ Max Cached Commands: " (:max-cached-commands config))
                           (str "✅ Heartbeat Interval: " (:heartbeat-interval-ms config) "ms")
                           (when verbose
                             (str "📋 Full Config: " (pr-str (dissoc config :workspace))))]}))
        (catch Exception e
          (swap! results update :sections conj
                 {:name "⚙️ Configuration Status"
                  :status :error
                  :duration-ms (- (System/currentTimeMillis) config-start)
                  :details [(str "❌ Configuration check failed: " (.getMessage e))]}))))

    ;; Calculate overall health
    (let [total-duration (- (System/currentTimeMillis) start-time)
          final-results @results
          section-statuses (map :status (:sections final-results))
          overall-status (cond
                          (every? #(= :success %) section-statuses) :healthy
                          (some #(= :error %) section-statuses) :unhealthy
                          :else :degraded)]
      (assoc final-results
             :overall-health overall-status
             :total-duration-ms total-duration
             :timestamp (System/currentTimeMillis)))))

(defn- format-health-check-report
  "Format comprehensive health check results into readable report"
  [health-check-results verbose]
  (let [{:keys [sections overall-health total-duration-ms timestamp]} health-check-results
        status-icon (case overall-health
                     :healthy "🟢"
                     :degraded "🟡" 
                     :unhealthy "🔴"
                     "⚪")
        header (str status-icon " Comprehensive Health Check Report")
        summary (str "Overall Status: " (name overall-health) 
                    " | Total Duration: " total-duration-ms "ms"
                    " | Timestamp: " (java.util.Date. timestamp))
        section-reports (map (fn [{:keys [name status duration-ms details]}]
                              (let [section-icon (case status
                                                  :success "✅"
                                                  :partial "⚠️"
                                                  :error "❌"
                                                  "⚪")]
                                (str section-icon " " name " (" duration-ms "ms)\n"
                                     (str/join "\n" (map #(str "  " %) details)))))
                            sections)
        report (str/join "\n\n" (concat [header summary] section-reports))]
    report))

(defn- tool-nrepl-health-check
  "Run comprehensive system health check"
  [{:keys [include-performance include-integration verbose] 
    :or {include-performance true include-integration true verbose false}}]
  (if-let [conn (:nrepl-conn @state)]
    (try
      (log :info "Running comprehensive health check...")
      (let [health-results (run-comprehensive-health-check conn
                                                           :include-performance include-performance
                                                           :include-integration include-integration
                                                           :verbose verbose)
            report (format-health-check-report health-results verbose)
            is-healthy (= :healthy (:overall-health health-results))]
        
        ;; Store results in state for future reference
        (swap! state assoc-in [:health-status :comprehensive-check] health-results)
        
        {:content [{:type "text" :text report}]
         :isError (not is-healthy)})
      (catch Exception e
        (log :error "Comprehensive health check failed:" (.getMessage e))
        {:content [{:type "text"
                   :text (str "❌ Comprehensive health check failed: " (.getMessage e))}]
         :isError true}))
    {:content [{:type "text"
               :text "❌ No nREPL connection available. Use nrepl-connect first."}]
     :isError true}))

;; MCP Protocol Handlers

(def tool-definitions
  [{:name "nrepl-connect"
    :description "FIRST-TIME SETUP: Connect to a Joyride nREPL server running in VS Code. Use this when starting a new session or if you get connection errors. Auto-discovers the port from .nrepl-port file, but you can specify custom host/port. RETURNS: Success message with connection details or error message."
    :inputSchema {:type "object"
                  :properties {:host {:type "string" :description "nREPL host (default: localhost)"}
                              :port {:type "number" :description "nREPL port (auto-discovered if not provided)"}}}}
   
   {:name "nrepl-eval"
    :description "PRIMARY TOOL: Execute any Clojure code in the connected nREPL session. Use this for: running calculations, calling VS Code functions, defining variables, requiring namespaces, or any Clojure expression. For VS Code automation, use expressions like (vscode/window.showInformationMessage \"Hello\"). RETURNS: Evaluation result (numbers, strings, data structures) or error details with stack trace."
    :inputSchema {:type "object"
                  :properties {:code {:type "string" :description "Clojure code to evaluate"}
                              :session {:type "string" :description "Session ID (optional)"}
                              :ns {:type "string" :description "Namespace context (optional)"}}
                  :required ["code"]}}
   
   {:name "nrepl-status"
    :description "DIAGNOSTIC: Check if nREPL connection is active and get current session information. Use this to verify connection before other operations or when troubleshooting. RETURNS: Connection status, active sessions list, server info, and recent command history."
    :inputSchema {:type "object"}}
   
   {:name "nrepl-new-session"
    :description "SESSION MANAGEMENT: Create isolated evaluation context for complex workflows. Use when you need variable isolation or want to run parallel evaluations without interference. Each session maintains separate namespace and variable state. RETURNS: New session ID string for use in other function calls."
    :inputSchema {:type "object"}}

   {:name "nrepl-test"
    :description "QUICK VALIDATION: Run basic nREPL functionality tests to verify the connection works properly. Use this after connecting or when experiencing issues. Tests basic evaluation, session management, and server communication. RETURNS: Test results summary with pass/fail status for each test."
    :inputSchema {:type "object"}}

   {:name "nrepl-health-check"
    :description "COMPREHENSIVE DIAGNOSTICS: Run detailed system health analysis across 6 categories - environment, connectivity, functionality, integration, performance, and configuration. Use this for troubleshooting, performance analysis, or when starting work in a new environment. Essential first step for AI assistants. TIP: If no nREPL server is connected, start the built-in Babashka nREPL server first with babashka-nrepl({op: 'start'}) for testing. RETURNS: Color-coded diagnostic report with detailed status, timing, and recommendations."
    :inputSchema {:type "object"
                  :properties {:include-performance {:type "boolean" :description "Include performance benchmarks (default: true)"}
                              :include-integration {:type "boolean" :description "Include integration tests (default: true)"}
                              :verbose {:type "boolean" :description "Include detailed diagnostic information (default: false)"}}}}

   {:name "nrepl-load-file"
    :description "FILE OPERATIONS: Load and evaluate a complete Clojure source file into the session. Use this to load utility functions, configuration, or library code from files. The file content is evaluated as if typed directly. Essential for loading reusable code modules. RETURNS: Success confirmation with namespace info or detailed error with line numbers."
    :inputSchema {:type "object"
                  :properties {:file-path {:type "string" :description "Path to the Clojure file to load"}
                              :session {:type "string" :description "Session ID (optional)"}
                              :ns {:type "string" :description "Namespace context (optional)"}}
                  :required ["file-path"]}}
                  
   {:name "nrepl-doc"
    :description "SYMBOL DOCUMENTATION: Get detailed documentation for any Clojure symbol or function. Use this to understand function parameters, usage examples, and behavior before using unfamiliar functions. Works with built-in functions (map, reduce), your own functions, and VS Code API functions. RETURNS: Formatted documentation with parameters, description, and examples, or 'No documentation found' message."
    :inputSchema {:type "object"
                  :properties {:symbol {:type "string" :description "Symbol to get documentation for"}
                              :session {:type "string" :description "Session ID (optional)"}
                              :ns {:type "string" :description "Namespace context (optional)"}}
                  :required ["symbol"]}}
                  
   {:name "nrepl-source"
    :description "SOURCE CODE INSPECTION: View the actual source code implementation of Clojure functions. Use this to understand how functions work internally, learn implementation patterns, or debug issues. Particularly useful for exploring custom functions and macros. RETURNS: Source code with line numbers and file location, or 'Source not found' for built-in functions."
    :inputSchema {:type "object"
                  :properties {:symbol {:type "string" :description "Symbol to get source code for"}
                              :session {:type "string" :description "Session ID (optional)"}
                              :ns {:type "string" :description "Namespace context (optional)"}}
                  :required ["symbol"]}}
                  
   {:name "nrepl-complete"
    :description "AUTO-COMPLETION: Get available symbol completions for partial input. Use this when you know part of a function name and want to see all possible completions. Helpful for discovering VS Code API functions, exploring namespaces, or finding the right function name. Essential for interactive development. RETURNS: List of matching symbols with brief descriptions or empty list if no matches."
    :inputSchema {:type "object"
                  :properties {:prefix {:type "string" :description "Symbol prefix to complete"}
                              :session {:type "string" :description "Session ID (optional)"}
                              :ns {:type "string" :description "Namespace context (optional)"}
                              :context {:type "string" :description "Completion context (optional)"}}
                  :required ["prefix"]}}
                  
   {:name "nrepl-apropos"
    :description "SYMBOL DISCOVERY: Search for functions and symbols by name pattern or keywords. Use this when you don't know exact function names but remember part of the name or functionality. Great for exploring available functions, finding utilities, or rediscovering forgotten function names. RETURNS: List of matching symbols with their namespaces and brief descriptions."
    :inputSchema {:type "object"
                  :properties {:query {:type "string" :description "Query pattern to search for"}
                              :session {:type "string" :description "Session ID (optional)"}
                              :ns {:type "string" :description "Namespace context (optional)"}
                              :search-ns {:type "string" :description "Namespace to search in (optional)"}
                              :privates? {:type "boolean" :description "Include private symbols (default: false)"}
                              :case-sensitive? {:type "boolean" :description "Case-sensitive search (default: false)"}}
                  :required ["query"]}}
                  
   {:name "nrepl-require"
    :description "NAMESPACE MANAGEMENT: Load additional Clojure namespaces and libraries into the current session. Use this to access external functions, load utility libraries, or import VS Code-specific namespaces. Essential for working with modular code and accessing extended functionality. RETURNS: Success confirmation or detailed error if namespace not found."
    :inputSchema {:type "object"
                  :properties {:namespace {:type "string" :description "Namespace symbol to require"}
                              :session {:type "string" :description "Session ID (optional)"}
                              :as {:type "string" :description "Alias for the namespace (optional)"}
                              :refer {:type "array" :description "Symbols to refer (optional)"}
                              :reload {:type "boolean" :description "Force reload (default: false)"}}
                  :required ["namespace"]}}
                  
   {:name "nrepl-interrupt"
    :description "EMERGENCY STOP: Interrupt long-running or stuck evaluations. Use this when code is taking too long, appears frozen, or you need to stop an infinite loop. Essential safety tool for interactive development. Does not affect the session state or variables. RETURNS: Confirmation that interrupt signal was sent."
    :inputSchema {:type "object"
                  :properties {:session {:type "string" :description "Session ID (optional)"}
                              :interrupt-id {:type "string" :description "Specific evaluation ID to interrupt (optional)"}}}}
                              
   {:name "nrepl-stacktrace"
    :description "ERROR DEBUGGING: Get detailed error information and stack trace for the most recent exception. Use this immediately after an error occurs to understand what went wrong, where it happened, and how to fix it. Provides file locations, line numbers, and call chain. RETURNS: Formatted stack trace with error details and source locations."
    :inputSchema {:type "object"
                  :properties {:session {:type "string" :description "Session ID (optional)"}}}}
                  
   {:name "get-mcp-nrepl-context"
    :description "🚨 MANDATORY FIRST STEP: Get comprehensive context document that explains the MCP-nREPL server's purpose, architecture, and workflows. AI assistants MUST read this context before using any other MCP functions to understand what this server does, how the 15 functions work together, and essential patterns for success. CRITICAL: This provides the roadmap for effective usage. RETURNS: Complete markdown context document with examples, use cases, and best practices."
    :inputSchema {:type "object"}}
                  
   {:name "babashka-nrepl"
    :description "DEVELOPMENT TOOLS: Manage Babashka nREPL server for debugging and introspection. Start/stop/status operations for the integrated Babashka server that enables Calva and other tools to connect for development. Use 'start' to launch server, 'stop' to shut down, 'status' to check current state. RETURNS: JSON status with port, files, and connection details."
    :inputSchema {:type "object"
                  :properties {:op {:type "string" :enum ["start" "stop" "status"] :description "Operation: start, stop, or status"}
                              :port {:type "number" :description "Port number (default: 7889)"}
                              :port-path {:type "string" :description "Path to port file (default: .babashka-nrepl-port)"}}
                  :required ["op"]}}])

(defn- call-tool 
  "Execute an MCP tool by name"
  [tool-name args]
  (case tool-name
    "nrepl-connect" (tool-nrepl-connect args)
    "nrepl-eval" (tool-nrepl-eval args)
    "nrepl-status" (tool-nrepl-status args)
    "nrepl-new-session" (tool-nrepl-new-session args)
    "nrepl-test" (tool-nrepl-test args)
    "nrepl-load-file" (tool-nrepl-load-file args)
    "nrepl-doc" (tool-nrepl-doc args)
    "nrepl-source" (tool-nrepl-source args)
    "nrepl-complete" (tool-nrepl-complete args)
    "nrepl-apropos" (tool-nrepl-apropos args)
    "nrepl-require" (tool-nrepl-require args)
    "nrepl-interrupt" (tool-nrepl-interrupt args)
    "nrepl-stacktrace" (tool-nrepl-stacktrace args)
    "nrepl-health-check" (tool-nrepl-health-check args)
    "get-mcp-nrepl-context" (tool-get-mcp-nrepl-context args)
    "babashka-nrepl" (tool-babashka-nrepl args)
    {:content [{:type "text" :text (str "❌ Unknown tool: " tool-name)}]
     :isError true}))

(defn- handle-list-tools
  "Handle MCP tools/list request"
  [request]
  {:jsonrpc "2.0"
   :id (or (:id request) (str (System/currentTimeMillis)))
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
         :id (or (:id request) (str (System/currentTimeMillis)))
         :result result}))
    (catch Exception e
      (log :error "Tool call failed:" (.getMessage e))
      {:jsonrpc "2.0"
       :id (or (:id request) (str (System/currentTimeMillis)))
       :error {:code -32603
               :message "Internal error"
               :data {:error (.getMessage e)}}})))

(defn- handle-initialize
  "Handle MCP initialize request"
  [request]
  {:jsonrpc "2.0"
   :id (or (:id request) (str (System/currentTimeMillis)))
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
     :id (or (:id request) (str (System/currentTimeMillis)))
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
           :id (or (:id request) (str (System/currentTimeMillis)))
           :result {:contents [{:uri uri
                               :mimeType "application/json"
                               :text (json/generate-string (nth commands idx) {:pretty true})}]}}
          {:jsonrpc "2.0"
           :id (or (:id request) (str (System/currentTimeMillis)))
           :error {:code -32602
                   :message "Resource not found"}}))
      {:jsonrpc "2.0"
       :id (or (:id request) (str (System/currentTimeMillis)))
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
     :id (or (:id request) (str (System/currentTimeMillis)))
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
                           :id (or (:id request) (str (System/currentTimeMillis)))
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
                :http-port port
                :babashka-nrepl-port (or (some->> (System/getenv "BABASHKA_NREPL_PORT") Integer/parseInt)
                                        7889)}]
    (swap! state assoc :config config)
    
    (log :info "🔧 MCP-nREPL Proxy Configuration:")
    (log :info "   Debug mode:" (:debug config))
    (log :info "   Workspace:" (:workspace config))
    (log :info "   Transport:" (:transport config))
    (when (= :http (:transport config))
      (log :info "   HTTP port:" (:http-port config)))
    (log :info "   Babashka nREPL port:" (:babashka-nrepl-port config))
    
    ;; Start heartbeat monitor
    (start-heartbeat-monitor)
    
    ;; Start Babashka nREPL server for Calva introspection
    (when false ;; TEMPORARILY DISABLED - debugging JSON output issue
    (when-let [bb-nrepl-port (:babashka-nrepl-port config)]
      (try
        (log :info "🔧 Starting Babashka nREPL server on port:" bb-nrepl-port)
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
          (log :info "✅ Babashka nREPL server started - connect Calva to localhost:" bb-nrepl-port)
          (spit ".nrepl-port-babashka" bb-nrepl-port))
        (catch Exception e
          (log :warn "⚠️  Failed to start Babashka nREPL server:" (.getMessage e))))))
    
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