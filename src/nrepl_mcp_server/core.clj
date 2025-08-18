#!/usr/bin/env bb

(ns nrepl-mcp-server.core
  "Main entry point for nREPL-MCP server"
  (:require [nrepl-mcp-server.mcp-server.server :as mcp-server]))

(defn -main
  "Main entry point for nREPL-MCP server"
  [& _args]
  (binding [*out* *err*]
    (println "🚀 Starting nREPL-MCP server..."))
  (mcp-server/stdio-server-loop))

;; Enable direct script execution with shebang
(when (= *file* (System/getProperty "babashka.file"))
  (-main))

;; Switch to user namespace for local-eval/local-load-file
(in-ns 'user)