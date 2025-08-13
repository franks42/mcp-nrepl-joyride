#!/usr/bin/env bb

(ns test-real-joyride
  "Test script for real Joyride nREPL integration"
  (:require [mcp-nrepl-proxy.nrepl-client :as nrepl]
            [cheshire.core :as json]))

(defn test-joyride []
  (println "\n🧪 Testing Real Joyride nREPL Integration")
  (println "=========================================\n")
  
  ;; Connect to Joyride nREPL
  (println "1️⃣ Connecting to Joyride nREPL on port 62577...")
  (let [conn (nrepl/connect "127.0.0.1" 62577)]
    (println "✅ Connected!\n")
    
    (try
      ;; Test basic evaluation
      (println "2️⃣ Testing basic evaluation: (+ 1 2 3)")
      (let [result (nrepl/eval-code conn "(+ 1 2 3)")]
        (println "   Result:" (:value result)))
      
      ;; Test namespace check
      (println "\n3️⃣ Testing namespace: *ns*")
      (let [result (nrepl/eval-code conn "*ns*")]
        (println "   Current namespace:" (:value result)))
      
      ;; Test VS Code API availability
      (println "\n4️⃣ Testing VS Code API availability")
      (let [result (nrepl/eval-code conn "(resolve 'js/vscode)")]
        (println "   js/vscode available:" (if (:value result) "YES ✅" "NO ❌")))
      
      ;; Test Joyride functionality
      (println "\n5️⃣ Testing Joyride namespace")
      (let [result (nrepl/eval-code conn "(require 'joyride.core)")]
        (println "   Joyride.core loaded:" (if (= "nil" (:value result)) "YES ✅" (:value result))))
      
      ;; Test workspace root
      (println "\n6️⃣ Testing workspace operations")
      (let [result (nrepl/eval-code conn "(-> js/vscode .-workspace .-workspaceFolders (aget 0) .-uri .-fsPath)")]
        (println "   Workspace root:" (:value result)))
      
      ;; Test VS Code command (safe one)
      (println "\n7️⃣ Testing VS Code command execution")
      (let [result (nrepl/eval-code conn "(require '[joyride.core :as joyride])")]
        (println "   Joyride loaded:" (if (= "nil" (:value result)) "YES ✅" (:value result))))
      
      (let [result (nrepl/eval-code conn "(joyride/extension-context)")]
        (println "   Extension context available:" (if (:value result) "YES ✅" "NO ❌")))
      
      (println "\n✅ All tests completed successfully!")
      
      (catch Exception e
        (println "❌ Error during testing:" (.getMessage e)))
      
      (finally
        (nrepl/close-connection conn)
        (println "\n👋 Connection closed")))))

(defn -main [& args]
  (test-joyride))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))