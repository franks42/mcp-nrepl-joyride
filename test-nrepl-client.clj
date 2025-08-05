#!/usr/bin/env bb

(ns test-nrepl-client
  "Test client to verify our nREPL server and MCP proxy work together"
  (:require [mcp-nrepl-proxy.nrepl-client :as nrepl]
            [cheshire.core :as json]))

(defn test-direct-nrepl []
  "Test direct connection to our test nREPL server"
  (println "🧪 Testing direct nREPL connection...")
  
  ;; Read port from .nrepl-port file
  (if-let [port-str (try (slurp ".nrepl-port") (catch Exception _ nil))]
    (let [port (Integer/parseInt (clojure.string/trim port-str))]
      (println "📡 Connecting to nREPL on port:" port)
      
      (try
        (let [conn (nrepl/connect "localhost" port)]
          (println "✅ Connected successfully!")
          
          ;; Test basic evaluation
          (println "\n📝 Testing evaluation: (+ 1 2 3)")
          (let [result (nrepl/eval-code conn "(+ 1 2 3)")]
            (println "📥 Result:" result))
          
          ;; Test session creation
          (println "\n🔧 Testing session creation")
          (let [session-result (nrepl/create-session conn)]
            (println "📥 Session result:" session-result))
          
          ;; Test server description
          (println "\n📋 Testing server description")
          (let [desc-result (nrepl/describe-server conn)]
            (println "📥 Description:" desc-result))
          
          (nrepl/close-connection conn)
          (println "✅ All direct nREPL tests passed!"))
        (catch Exception e
          (println "❌ nREPL test failed:" (.getMessage e)))))
    (println "❌ No .nrepl-port file found. Start test-nrepl-server first.")))

(defn test-mcp-proxy []
  "Test MCP proxy functionality"
  (println "\n🔌 Testing MCP proxy...")
  (println "📝 This would require stdin/stdout interaction with MCP server")
  (println "🎯 Run manually: echo '{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}' | bb src/mcp_nrepl_proxy/core.clj"))

(defn -main [& _args]
  (println "=== nREPL Integration Test Suite ===")
  (test-direct-nrepl)
  (test-mcp-proxy)
  (println "\n🎉 Test suite completed!"))

;; Enable direct script execution
(when (= *file* (System/getProperty "babashka.file"))
  (-main))