(ns nrepl-mcp-server.mcp-server.tools.nrepl-load-file
  "nREPL load-file tool - Load and evaluate Clojure files in connected nREPL server runtime"
  (:require [nrepl-mcp-server.mcp-server.tools.nrepl-eval :as nrepl-eval]
            [nrepl-mcp-server.mcp-server.tools.load-file-shared :as shared]))

(defn handle
  "Load and evaluate a Clojure file in the connected nREPL server's runtime environment.
   
   Uses Clojure's built-in (load-file \"path\") function executed within the nREPL server.
   This is NOT the nREPL protocol's :op \"load-file\" operation (which is IDE-specific).
   
   The file is loaded and evaluated in whatever runtime the nREPL server is running:
   - Clojure JVM
   - ClojureScript (Node.js/browser)  
   - Babashka (SCI)
   - Or any other Clojure-compatible runtime
   
   Use absolute file paths for reliability as the nREPL server's working directory may vary."
  [{:keys [file-path timeout connection] :or {timeout 30000}}]
  (try
    ;; Validate parameters using shared utilities
    (shared/validate-parameters {:file-path file-path} ["file-path"])
    (shared/validate-file-path file-path)

    ;; Execute load-file via nrepl-eval delegation
    (let [escaped-path (shared/escape-file-path file-path)
          code (str "(load-file \"" escaped-path "\")")
          result (nrepl-eval/handle {:code code :timeout timeout :connection connection})]

      ;; Format response using shared utilities
      (shared/format-load-file-response result "nrepl-load-file" file-path))

    (catch Exception e
      (shared/handle-load-file-error e "nrepl-load-file" file-path))))

(def tool-name "nrepl-load-file")

(def metadata
  {:description "📁 NREPL FILE LOADER: Load and evaluate Clojure files in connected nREPL server's runtime using Clojure's built-in load-file function. Executes in the nREPL server's environment (Clojure JVM, ClojureScript, Babashka SCI, etc.). Use for loading application code, namespaces, and project files. Recommend absolute file paths for reliability."
   :inputSchema {:type "object"
                 :properties {:file-path {:type "string"
                                          :description "Path to Clojure file to load (recommend absolute paths)"}
                              :connection {:type "string"
                                           :description "Connection identifier (nickname, connection-id, or host:port). Optional - uses single connection if not specified."}
                              :timeout {:type "integer"
                                        :description "Timeout in milliseconds (default: 30000)"
                                        :minimum 1000
                                        :maximum 300000}}
                 :required ["file-path"]}})