#!/usr/bin/env bb

(ns mcp-nrepl-proxy.core
  "Babashka MCP server bridging Claude Code with Joyride nREPL.
   
   Pure Babashka implementation using native nREPL client capabilities.
   Supports both stdio and HTTP transports."
  (:require [mcp-nrepl-proxy.nrepl-client :as nrepl]
            [mcp-nrepl-proxy.config :as config]
            [mcp-nrepl-proxy.utils :as utils]
            [mcp-nrepl-proxy.server :as server]
            [mcp-nrepl-proxy.protocol :as protocol]
            [mcp-nrepl-proxy.tools.evaluation :as evaluation-tools]
            [mcp-nrepl-proxy.tools.introspection :as introspection-tools]
            [mcp-nrepl-proxy.tools.session :as session-tools]
            [mcp-nrepl-proxy.tools.control :as control-tools]
            [mcp-nrepl-proxy.tools.async :as async-tools]
            [mcp-nrepl-proxy.tools.debug :as debug-tools]
            [mcp-nrepl-proxy.connection :as connection]
            [mcp-nrepl-proxy.monitoring :as monitoring]
            [mcp-nrepl-proxy.context :as context]
            [mcp-nrepl-proxy.devtools :as devtools]
            [clojure.string :as str]))

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

(defn- heartbeat-test
  "Simple heartbeat test using nREPL describe operation"
  [conn]
  (try
    (let [result (nrepl/describe-server conn)]
      (contains? result :ops))
    (catch Exception e
      (utils/log state :debug "Heartbeat failed:" (.getMessage e))
      false)))

(defn- start-heartbeat-monitor
  "Start background heartbeat monitoring"
  []
  (utils/log state :info "Starting nREPL heartbeat monitor")
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
              (utils/log state :debug "Heartbeat successful"))
            (do
              (swap! state update-in [:health-status]
                     (fn [health]
                       (assoc health
                              :connected false
                              :heartbeat-failures (inc (:heartbeat-failures health)))))
              (utils/log state :warn "Heartbeat failed, failure count:"
                         (get-in @state [:health-status :heartbeat-failures]))))))
      (recur))))

;; Connection adapter functions
(defn- ensure-nrepl-connection
  "Adapter function for connection namespace"
  []
  (connection/ensure-nrepl-connection state))

(defn- connect-to-nrepl
  "Adapter function for connection namespace"
  [host port]
  (connection/connect-to-nrepl state host port))

;; MCP Tool Implementations

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
          core-tests [{:name "Basic Arithmetic"
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
                       :expect-fn #(or (str/includes? % "user")
                                       (str/includes? % "mcp-nrepl-proxy.core")
                                       (str/includes? % "test.example"))}]]
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
            tool-tests [{:name "Session Creation"
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
            perf-tests [{:name "Simple Expression"
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
      (utils/log state :info "Running comprehensive health check...")
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
        (utils/log state :error "Comprehensive health check failed:" (.getMessage e))
        {:content [{:type "text"
                    :text (str "❌ Comprehensive health check failed: " (.getMessage e))}]
         :isError true}))
    {:content [{:type "text"
                :text "❌ No nREPL connection available. Use nrepl-connect first."}]
     :isError true}))

;; MCP Protocol Handlers

;; Tool definitions moved to config namespace
(def tool-definitions config/tool-definitions)

(defn- call-tool
  "Execute an MCP tool by name"
  [tool-name args]
  (case tool-name
    "nrepl-connect" (session-tools/tool-nrepl-connect state connect-to-nrepl args)
    "nrepl-eval" (evaluation-tools/tool-nrepl-eval state ensure-nrepl-connection args)
    "nrepl-status" (session-tools/tool-nrepl-status state args)
    "nrepl-new-session" (session-tools/tool-nrepl-new-session state ensure-nrepl-connection args)
    "nrepl-test" (devtools/tool-nrepl-test state args)
    "nrepl-load-file" (evaluation-tools/tool-nrepl-load-file state ensure-nrepl-connection args)
    "nrepl-doc" (introspection-tools/tool-nrepl-doc state ensure-nrepl-connection args)
    "nrepl-source" (introspection-tools/tool-nrepl-source state ensure-nrepl-connection args)
    "nrepl-complete" (introspection-tools/tool-nrepl-complete state ensure-nrepl-connection args)
    "nrepl-apropos" (introspection-tools/tool-nrepl-apropos state ensure-nrepl-connection args)
    "nrepl-require" (evaluation-tools/tool-nrepl-require state ensure-nrepl-connection args)
    "nrepl-interrupt" (control-tools/tool-nrepl-interrupt state ensure-nrepl-connection args)
    "nrepl-stacktrace" (control-tools/tool-nrepl-stacktrace state ensure-nrepl-connection args)
    "nrepl-health-check" (monitoring/tool-nrepl-health-check state ensure-nrepl-connection args)
    "get-mcp-nrepl-context" (context/tool-get-mcp-nrepl-context state args)
    "nrepl-send-message-async" (async-tools/tool-nrepl-send-message-async state ensure-nrepl-connection args)
    "nrepl-get-result-async" (async-tools/tool-nrepl-get-result-async state ensure-nrepl-connection args)
    "babashka-nrepl" (devtools/tool-babashka-nrepl state args)
    "debug-eval" (debug-tools/tool-debug-eval state args)
    {:content [{:type "text" :text (str "❌ Unknown tool: " tool-name)}]
     :isError true}))

(defn -main
  "Main entry point for Babashka MCP-nREPL proxy"
  [& args]
  (let [handle-request (partial protocol/handle-request state call-tool)]
    (apply server/server-main state handle-request start-heartbeat-monitor args)))

;; Enable direct script execution with shebang
(when (= *file* (System/getProperty "babashka.file"))
  (-main))