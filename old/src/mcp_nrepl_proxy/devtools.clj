(ns mcp-nrepl-proxy.devtools
  "Development and debugging tools for MCP-nREPL server"
  (:require [babashka.fs :as fs]
            [babashka.nrepl.server :as nrepl-server]
            [cheshire.core :as json]
            [clojure.string :as str]
            [mcp-nrepl-proxy.nrepl-client :as nrepl]
            [mcp-nrepl-proxy.utils :as utils]))

(defn run-health-test
  "Run comprehensive nREPL health tests"
  [state conn]
  (let [tests [{:name "Server Description"
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
                               (utils/log state :info "Starting Babashka nREPL server for testing...")
                              ;; Start server directly without using tool function
                               (try
                                 (let [server (binding [*ns* (find-ns 'user)]
                                                (nrepl-server/start-server! {:port 7889 :quiet true}))]
                                   (swap! state assoc
                                          :babashka-nrepl-server server
                                          :babashka-nrepl-port 7889)
                                   (utils/log state :info "✅ Babashka nREPL server started for testing"))
                                 (catch Exception e
                                   (utils/log state :warn "Failed to start Babashka server:" (.getMessage e)))))

                            ;; Test connection to Babashka nREPL
                             (if-let [bb-port (:babashka-nrepl-port @state)]
                               (try
                                 (utils/log state :info "Testing Babashka nREPL connection on port" bb-port)
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

(defn tool-nrepl-test
  "Run comprehensive nREPL health tests"
  [state _args]
  (if-let [conn (:nrepl-conn @state)]
    (try
      (utils/log state :info "Running nREPL health tests...")
      (let [start-time (System/currentTimeMillis)
            test-results (run-health-test state conn)
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
        (utils/log state :error "Health test failed:" (.getMessage e))
        {:content [{:type "text"
                    :text (str "❌ Health test failed: " (.getMessage e))}]
         :isError true}))
    {:content [{:type "text"
                :text "❌ No nREPL connection available for testing"}]
     :isError true}))

(defn tool-babashka-nrepl
  "Manage Babashka nREPL server for debugging tools"
  [state {:keys [op port port-path]}]
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
          ;; Start server with quiet option and bind *ns* to user for client sessions
          (let [server (binding [*ns* (find-ns 'user)]
                         (nrepl-server/start-server! {:port port :quiet true}))]
            (swap! state assoc
                   :babashka-nrepl-server server
                   :babashka-nrepl-port port)
            ;; Try to write port file
            (let [port-written (try
                                 (spit port-path (str port))
                                 true
                                 (catch Exception e
                                   (utils/log state :warn "Could not write port file to" port-path ":" (.getMessage e))
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
            (utils/log state :error "Failed to start Babashka nREPL server:" (.getMessage e))
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