#!/usr/bin/env bb

(ns nrepl-mcp-server.core
  "Main entry point for nREPL-MCP server"
  (:require [nrepl-mcp-server.mcp-server.server :as mcp-server]
            [clojure.java.io :as io]))

(def startup-file ".mcp-startup.clj")

(defn load-startup-file!
  "Load project-local startup file if it exists.
   This allows projects to pre-register tools before MCP connections."
  []
  (let [file (io/file startup-file)]
    (when (.exists file)
      (binding [*out* *err*]
        (println (str "📦 Loading startup file: " startup-file)))
      (try
        (load-file (.getAbsolutePath file))
        (binding [*out* *err*]
          (println "✅ Startup file loaded successfully"))
        (catch Exception e
          (binding [*out* *err*]
            (println (str "❌ Failed to load startup file: " (.getMessage e)))))))))

(defn -main
  "Main entry point for nREPL-MCP server"
  [& _args]
  (binding [*out* *err*]
    (println "🚀 Starting nREPL-MCP server..."))

  ;; Load project-local startup file before accepting connections
  (load-startup-file!)

  (mcp-server/stdio-server-loop))

;; Enable direct script execution with shebang
(when (= *file* (System/getProperty "babashka.file"))
  (-main))

;; Switch to user namespace for local-eval/local-load-file
;; (in-ns 'user)
;;(eval (read-string "(in-ns 'user)"))