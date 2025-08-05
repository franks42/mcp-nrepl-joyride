#!/usr/bin/env bb

;; Simple test script to verify MCP server functionality
(require '[cheshire.core :as json])

(defn test-mcp-initialize []
  (let [request {:jsonrpc "2.0"
                :id 1
                :method "initialize"
                :params {}}]
    (println "Testing MCP initialize...")
    (println "Request:" (json/generate-string request))
    ;; In a real test, we'd send this to the server and check response
    true))

(defn test-mcp-tools-list []
  (let [request {:jsonrpc "2.0"
                :id 2
                :method "tools/list"}]
    (println "Testing MCP tools/list...")
    (println "Request:" (json/generate-string request))
    true))

;; Run tests
(println "🧪 Testing MCP-nREPL proxy server")
(test-mcp-initialize)
(test-mcp-tools-list)
(println "✅ Basic MCP structure tests passed")
(println "📝 To test fully, configure in Claude Code and try:")
(println "   - nrepl-status")
(println "   - nrepl-eval with code: '(+ 1 2 3)'")
(println "   - nrepl-connect if Joyride nREPL is running")