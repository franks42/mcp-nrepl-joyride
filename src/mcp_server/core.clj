#!/usr/bin/env bb

(ns mcp-server.core
  "Main entry point for minimal MCP server"
  (:require [mcp-server.stdio :as stdio]))

(defn -main
  "Main entry point for minimal MCP server"
  [& _args]
  (binding [*out* *err*]
    (println "🚀 Starting minimal MCP server with debug tools only..."))
  (stdio/stdio-server-loop))

;; Enable direct script execution with shebang
(when (= *file* (System/getProperty "babashka.file"))
  (-main))