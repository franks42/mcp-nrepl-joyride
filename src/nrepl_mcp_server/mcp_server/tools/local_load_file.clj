(ns nrepl-mcp-server.mcp-server.tools.local-load-file
  "Local load-file tool - Load and evaluate Clojure files in MCP server's runtime (Babashka SCI)"
  (:require [nrepl-mcp-server.mcp-server.tools.load-file-shared :as shared])
  (:import [java.io StringWriter PrintWriter]))

(defn handle
  "Load and evaluate a Clojure file in the MCP server's runtime environment (Babashka SCI).
   
   Uses Clojure's built-in load-file function to load and evaluate the specified file
   within the MCP server's own Babashka SCI runtime. This is useful for:
   - Loading development scripts and toolkits into the MCP server
   - Extending MCP server functionality with additional Clojure code
   - Testing code in the SCI environment
   
   NOTE: This executes in SCI (limited Clojure subset), not a full Clojure environment.
   For loading code into connected nREPL servers, use nrepl-load-file instead."
  [{:keys [file-path]}]
  (try
    ;; Validate parameters using shared utilities
    (shared/validate-parameters {:file-path file-path} ["file-path"])
    (shared/validate-file-path file-path)

    ;; Execute load-file with output capture
    (let [stdout-writer (StringWriter.)
          stderr-writer (StringWriter.)]
      (binding [*out* (PrintWriter. stdout-writer)
                *err* (PrintWriter. stderr-writer)]
        (let [result (load-file file-path)
              captured-stdout (str stdout-writer)
              captured-stderr (str stderr-writer)]
          (shared/format-load-file-response
           {:value (pr-str result)
            :out captured-stdout
            :err captured-stderr}
           "local-load-file"
           file-path))))

    (catch Exception e
      (shared/handle-load-file-error e "local-load-file" file-path))))

(def tool-name "local-load-file")

(def metadata
  {:description "📂 MCP SERVER FILE LOADER: Load and evaluate Clojure files in MCP server's runtime (Babashka SCI) using Clojure's built-in load-file function. Use for loading scripts, toolkits, and extensions into the MCP server itself. Limited to SCI-compatible Clojure subset. For loading code into connected nREPL servers, use nrepl-load-file instead."
   :inputSchema {:type "object"
                 :properties {:file-path {:type "string"
                                          :description "Path to Clojure file to load"}}
                 :required ["file-path"]}})

