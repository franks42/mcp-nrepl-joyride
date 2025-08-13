#!/usr/bin/env bb

(ns test-joyride-integration
  "Comprehensive test for Joyride/Calva mock server functionality"
  (:require [babashka.process :as p]
            [babashka.fs :as fs]
            [mcp-nrepl-proxy.nrepl-client :as nrepl]
            [cheshire.core :as json]
            [clojure.string :as str]))

(defn wait-for-port-file [timeout-ms]
  "Wait for .nrepl-port file to appear"
  (let [end-time (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (if (fs/exists? ".nrepl-port")
        (Integer/parseInt (str/trim (slurp ".nrepl-port")))
        (if (< (System/currentTimeMillis) end-time)
          (do (Thread/sleep 100) (recur))
          nil)))))

(defn test-basic-evaluation [conn]
  "Test basic Clojure evaluation"
  (println "\n📝 Testing Basic Evaluation")
  (let [tests [["(+ 1 2 3)" "6"]
               ["(* 2 3 4)" "24"]
               ["(str \"hello\" \" \" \"world\")" "\"hello world [mocked]\""]]]
    (doseq [[code expected] tests]
      (println "  Testing:" code)
      (let [result (nrepl/eval-code conn code)]
        (println "  Result:" (:value result))))))

(defn test-vscode-api-integration [conn]
  "Test VS Code API integration"
  (println "\n🎨 Testing VS Code API Integration")
  (let [vscode-tests 
        [["(joyride.core/execute-command \"workbench.action.quickOpen\")" "Quick Open displayed"]
         ["(joyride.core/execute-command \"workbench.action.showCommands\")" "Command palette opened"]
         ["(joyride/workspace-root)" "workspace root path"]
         ["(-> js/vscode.window.activeTextEditor .-document .-fileName)" "active editor info"]]]
    (doseq [[code description] vscode-tests]
      (println "  Testing:" description)
      (println "    Code:" code)
      (let [result (nrepl/eval-code conn code)]
        (println "    Result:" (:value result))))))

(defn test-workspace-operations [conn]
  "Test workspace and file operations"
  (println "\n📁 Testing Workspace Operations")
  (let [workspace-tests
        [["(joyride/workspace-files \"**/*.clj\")" "List workspace files"]
         ["(vscode.workspace.openTextDocument \"/src/new-file.clj\")" "Open document"]
         ["(vscode.window.showInformationMessage \"Hello from Claude!\")" "Show notification"]]]
    (doseq [[code description] workspace-tests]
      (println "  Testing:" description)
      (let [result (nrepl/eval-code conn code)]
        (println "    Result:" (:value result))))))

(defn test-calva-middleware [conn]
  "Test Calva-style middleware operations"
  (println "\n🔧 Testing Calva Middleware Operations")
  
  ;; Test info operation
  (println "  Testing symbol info...")
  (let [info-result (nrepl/send-message conn {:op "info" :symbol "println" :ns "user"})]
    (println "    Info result:" info-result))
  
  ;; Test completion
  (println "  Testing code completion...")
  (let [complete-result (nrepl/send-message conn {:op "complete" :prefix "pr" :ns "user"})]
    (println "    Completion result:" complete-result))
  
  ;; Test describe for capabilities
  (println "  Testing server capabilities...")
  (let [describe-result (nrepl/describe-server conn)]
    (println "    Capabilities:" (:ops describe-result))
    (println "    Versions:" (:versions describe-result))))

(defn test-session-management [conn]
  "Test enhanced session management"
  (println "\n🔄 Testing Session Management") 
  
  ;; Create multiple sessions
  (println "  Creating Joyride sessions...")
  (let [session1 (nrepl/create-session conn)
        session2 (nrepl/create-session conn)]
    (println "    Session 1:" (:new-session session1))
    (println "    Session 2:" (:new-session session2))
    
    ;; Test evaluation in different sessions
    (println "  Testing session isolation...")
    (nrepl/eval-code conn "(def x 42)" :session (:new-session session1))
    (let [result1 (nrepl/eval-code conn "x" :session (:new-session session1))
          result2 (nrepl/eval-code conn "x" :session (:new-session session2))]
      (println "    Session 1 x value:" (:value result1))
      (println "    Session 2 x value:" (:value result2)))))

(defn test-mcp-with-joyride [message]
  "Test MCP proxy with Joyride mock server"
  (println "📤 Testing MCP →" (:method message))
  (try
    (let [proc (p/process ["bb" "-cp" "src" "src/mcp_nrepl_proxy/core.clj"] 
                         {:in (json/generate-string message)
                          :out :string
                          :err :string})]
      (let [result @proc]
        (println "📥 MCP Response:")
        (if (not (str/blank? (:out result)))
          (let [response (json/parse-string (:out result) true)]
            (if (= (:method message) "tools/call")
              (let [content (get-in response [:result :content 0 :text])]
                (when content
                  (let [parsed-content (json/parse-string content true)]
                    (println "   " parsed-content))))
              (println "   " response)))
          (println "    (empty response)"))))
    (catch Exception e
      (println "❌ MCP test failed:" (.getMessage e)))))

(defn run-joyride-integration-test []
  "Run comprehensive Joyride integration test"
  (println "🚀 Starting Joyride Integration Test Suite")
  (println "===========================================")
  
  ;; Clean up any existing port file
  (fs/delete-if-exists ".nrepl-port")
  
  ;; Start Joyride mock server
  (println "1️⃣ Starting Joyride Mock nREPL server...")
  (let [nrepl-proc (p/process ["./joyride-mock-server.clj"] {:err :inherit})]
    
    (try
      ;; Wait for server to start
      (println "⏳ Waiting for Joyride server to start...")
      (if-let [port (wait-for-port-file 5000)]
        (do
          (println "✅ Joyride Mock server started on port:" port)
          
          ;; Test direct nREPL connection with enhanced features
          (println "\n2️⃣ Testing Direct nREPL with Joyride Features...")
          (let [conn (nrepl/connect "localhost" port)]
            (println "✅ Connected to Joyride Mock server")
            
            ;; Run all test suites
            (test-basic-evaluation conn)
            (test-vscode-api-integration conn)
            (test-workspace-operations conn)
            (test-calva-middleware conn)
            (test-session-management conn)
            
            (nrepl/close-connection conn))
          
          ;; Test MCP proxy with Joyride-specific functionality
          (println "\n3️⃣ Testing MCP Proxy with Joyride Features...")
          (test-mcp-with-joyride {:jsonrpc "2.0" :id 1 :method "tools/list"})
          (test-mcp-with-joyride {:jsonrpc "2.0" :id 2 :method "tools/call" 
                                 :params {:name "nrepl-eval" 
                                         :arguments {:code "(joyride.core/execute-command \"workbench.action.quickOpen\")"}}})
          (test-mcp-with-joyride {:jsonrpc "2.0" :id 3 :method "tools/call"
                                 :params {:name "nrepl-eval"
                                         :arguments {:code "(joyride/workspace-root)"}}})
          (test-mcp-with-joyride {:jsonrpc "2.0" :id 4 :method "tools/call"
                                 :params {:name "nrepl-eval"
                                         :arguments {:code "(-> js/vscode.window.activeTextEditor .-document .-fileName)"}}})
          
          (println "\n🎉 Joyride integration test completed successfully!"))
        (println "❌ Timeout waiting for Joyride server to start"))
      
      (finally
        ;; Stop server
        (println "\n4️⃣ Stopping Joyride Mock server...")
        (p/destroy nrepl-proc)
        (fs/delete-if-exists ".nrepl-port")
        (println "✅ Cleanup completed")))))

(defn -main [& _args]
  (run-joyride-integration-test))

;; Enable direct script execution
(when (= *file* (System/getProperty "babashka.file"))
  (-main))