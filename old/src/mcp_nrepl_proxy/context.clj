(ns mcp-nrepl-proxy.context
  "Context and metadata information for MCP-nREPL server"
  (:require [mcp-nrepl-proxy.utils :as utils]))

(defn tool-get-mcp-nrepl-context
  "Get comprehensive context document for AI assistants"
  [state _args]
  (try
    (let [context-file "AI-CONTEXT.md"
          context-content (slurp context-file)]
      {:content [{:type "text"
                  :text context-content}]})
    (catch Exception e
      (utils/log state :error "Failed to read context document:" (.getMessage e))
      {:content [{:type "text"
                  :text (str "# MCP-nREPL Server Context\n\n"
                             "## Overview\n\n"
                             "This MCP server bridges AI assistants with Clojure/ClojureScript development environments "
                             "through the nREPL protocol. It provides 15 MCP functions for executing Clojure code, "
                             "controlling VS Code through Joyride, exploring codebases, and building interactive applications.\n\n"
                             "## Essential First Steps\n\n"
                             "1. **Always start with `nrepl-health-check()`** to understand your environment (if no nREPL server connected, use `babashka-nrepl({op: 'start'})` first)\n"
                             "2. **Check current namespace** with `nrepl-eval({code: \"*ns*\"})`\n"
                             "3. **Discover available functions** with `nrepl-apropos({query: \"keyword\"})`\n"
                             "4. **Get documentation** with `nrepl-doc({symbol: \"function-name\"})`\n\n"
                             "## Core Functions\n\n"
                             "- **nrepl-eval**: Execute Clojure code (primary tool)\n"
                             "- **nrepl-health-check**: Environment diagnostics\n"
                             "- **nrepl-doc/source/apropos**: Code exploration\n"
                             "- **nrepl-require**: Load namespaces\n"
                             "- **nrepl-load-file**: Load Clojure files\n\n"
                             "## Remember\n\n"
                             "Start simple, test incrementally, and use the health check to understand your environment!")}]})))