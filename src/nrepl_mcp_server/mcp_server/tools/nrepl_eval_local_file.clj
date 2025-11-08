(ns nrepl-mcp-server.mcp-server.tools.nrepl-eval-local-file
  "MCP tool for evaluating local Clojure files via nREPL"
  (:require [nrepl-mcp-server.mcp-server.tools.nrepl-eval :as nrepl-eval]
            [nrepl-mcp-server.mcp-server.tools.load-file-shared :as shared]
            [nrepl-mcp-server.state.tool-registry :as registry]))

;; =============================================================================
;; Main Handler
;; =============================================================================

(defn handle
  "Evaluate a local Clojure file via nREPL by reading and sending its content as code.

  Uses the nrepl-eval tool to evaluate the file contents. This is useful for:
  - nREPL servers without filesystem access
  - Evaluating local files in remote nREPL servers
  - Testing and development workflows

  Args:
    :file-path - Absolute path to local Clojure file
    :connection - Optional nREPL connection nickname
    :ns - Optional namespace to evaluate in
    :session - Optional nREPL session ID
    :timeout - Optional timeout in milliseconds (default: 30000)"
  [{:keys [file-path connection ns session timeout] :or {timeout 30000}}]
  (try
    ;; Validate parameters using shared utilities
    (shared/validate-parameters {:file-path file-path} ["file-path"])
    (shared/validate-file-path file-path)

    ;; Read file content
    (let [file-content (slurp file-path)
          ;; Delegate to nrepl-eval with file content as code
          eval-args (cond-> {:code file-content :timeout timeout}
                      connection (assoc :connection connection)
                      ns (assoc :ns ns)
                      session (assoc :session session))
          result (nrepl-eval/handle eval-args)]

      ;; Format response using shared utilities
      (shared/format-load-file-response result "nrepl-eval-local-file" file-path))

    (catch Exception e
      (shared/handle-load-file-error e "nrepl-eval-local-file" file-path))))

;; =============================================================================
;; Tool Metadata
;; =============================================================================

(def tool-name "nrepl-eval-local-file")

(def metadata
  {:description "📂 FILE EVALUATOR: Evaluate a local Clojure file via nREPL by reading and sending its content as code. Useful for nREPL servers without filesystem access."
   :inputSchema {:type "object"
                 :properties {:file-path {:type "string"
                                          :description "Absolute path to local Clojure file"}
                              :connection {:type "string"
                                           :description "Optional nREPL connection nickname"}
                              :ns {:type "string"
                                   :description "Optional namespace to evaluate in"}
                              :session {:type "string"
                                        :description "Optional nREPL session ID"}
                              :timeout {:type "integer"
                                        :description "Timeout in milliseconds (default: 30000)"
                                        :minimum 1000
                                        :maximum 300000}}
                 :required ["file-path"]}})

;; Register tool
(registry/register-tool! tool-name handle metadata)
