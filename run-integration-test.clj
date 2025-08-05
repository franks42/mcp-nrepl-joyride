#!/usr/bin/env bb

(ns run-integration-test
  "Complete integration test that starts nREPL server, tests it, then stops it"
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

(defn test-mcp-json-message [message]
  "Test sending a JSON message to MCP server"
  (println "📤 Testing MCP message:" (json/generate-string message))
  (try
    (let [proc (p/process ["bb" "-cp" "src" "src/mcp_nrepl_proxy/core.clj"] 
                         {:in (json/generate-string message)
                          :out :string
                          :err :string})]
      (let [result @proc]
        (println "📥 MCP Response:" (:out result))
        (when (not (str/blank? (:err result)))
          (println "📋 MCP Logs:" (:err result)))))
    (catch Exception e
      (println "❌ MCP test failed:" (.getMessage e)))))

(defn run-integration-test []
  "Run complete integration test"
  (println "🚀 Starting Integration Test Suite")
  (println "==================================")
  
  ;; Clean up any existing port file
  (fs/delete-if-exists ".nrepl-port")
  
  ;; Start test nREPL server
  (println "1️⃣ Starting test nREPL server...")
  (let [nrepl-proc (p/process ["./test-nrepl-server.clj"] {:err :inherit})]
    
    (try
      ;; Wait for server to start
      (println "⏳ Waiting for nREPL server to start...")
      (if-let [port (wait-for-port-file 5000)]
        (do
          (println "✅ nREPL server started on port:" port)
          
          ;; Test direct nREPL connection
          (println "\n2️⃣ Testing direct nREPL connection...")
          (let [conn (nrepl/connect "localhost" port)]
            (println "✅ Connected to nREPL server")
            
            ;; Test evaluation
            (println "📝 Testing evaluation: (+ 1 2 3)")
            (let [result (nrepl/eval-code conn "(+ 1 2 3)")]
              (println "📥 Eval result:" result))
            
            ;; Test session creation
            (println "🔧 Testing session creation")
            (let [session-result (nrepl/create-session conn)]
              (println "📥 Session result:" session-result))
            
            (nrepl/close-connection conn))
          
          ;; Test MCP proxy
          (println "\n3️⃣ Testing MCP proxy...")
          (test-mcp-json-message {:jsonrpc "2.0" :id 1 :method "initialize" :params {}})
          (test-mcp-json-message {:jsonrpc "2.0" :id 2 :method "tools/list"})
          (test-mcp-json-message {:jsonrpc "2.0" :id 3 :method "tools/call" 
                                 :params {:name "nrepl-status" :arguments {}}})
          (test-mcp-json-message {:jsonrpc "2.0" :id 4 :method "tools/call" 
                                 :params {:name "nrepl-eval" :arguments {:code "(+ 1 2 3)"}}})
          
          (println "\n🎉 Integration test completed successfully!"))
        (println "❌ Timeout waiting for nREPL server to start"))
      
      (finally
        ;; Stop nREPL server
        (println "\n4️⃣ Stopping nREPL server...")
        (p/destroy nrepl-proc)
        (fs/delete-if-exists ".nrepl-port")
        (println "✅ Cleanup completed")))))

(defn -main [& _args]
  (run-integration-test))

;; Enable direct script execution
(when (= *file* (System/getProperty "babashka.file"))
  (-main))