#!/usr/bin/env bb

(ns nrepl-mcp_server.core
  "Main entry point for nREPL-MCP server"
  (:require [nrepl-mcp_server.mcp_server.server :as mcp_server]))

(defn -main
  "Main entry point for nREPL-MCP server"
  [& _args]
  (binding [*out* *err*]
    (println "🚀 Starting nREPL-MCP server..."))
  (mcp_server/stdio-server-loop))

;; Enable direct script execution with shebang
(when (= *file* (System/getProperty "babashka.file"))
  (-main))