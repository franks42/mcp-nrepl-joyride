(ns nrepl-mcp-server.mcp-server.tools.local-eval
  "Local eval tool for MCP server introspection"
  (:require [cheshire.core :as json]
            [nrepl-mcp-server.state.tool-registry :as registry]))

(defn handle
  "Evaluate Clojure code within the MCP server runtime"
  [{:keys [code]}]
  (if (empty? code)
    {:content [{:type "text"
                :text "❌ Code parameter is required"}]
     :isError true}
    (try
      ;; Use with-out-str to capture stdout from println statements
      (let [result-atom (atom nil)
            stdout-capture (with-out-str
                             (let [result (eval (read-string code))]
                               ;; Store result in atom so we can access it
                               (reset! result-atom result)))
            ;; Get the result from the atom
            result @result-atom
            ;; Convert result to serializable format
            serializable-result (cond
                                  (nil? result) nil
                                  (string? result) result
                                  (or (number? result)
                                      (keyword? result)
                                      (boolean? result)) result
                                  (or (map? result)
                                      (vector? result)
                                      (list? result)
                                      (set? result)) (pr-str result)
                                  (instance? clojure.lang.Atom result)
                                  (str "#atom " (pr-str @result))
                                  :else (str "#" (.getName (class result)) " " result))]
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "success"
                            :code code
                            :result serializable-result
                            :result-type (.getName (class result))
                            :stdout stdout-capture
                            :stderr ""}  ;; TODO: stderr capture is more complex
                           {:pretty true})}]})
      (catch Exception e
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :code code
                            :error (.getMessage e)
                            :stdout ""
                            :stderr ""
                            :stacktrace (mapv str (.getStackTrace e))}
                           {:pretty true})}]
         :isError true}))))

;; =============================================================================
;; Self Registration
;; =============================================================================

;; Self-register this tool when namespace loads
(registry/register-tool!
 "local-eval"
 handle
 {:description "Execute Clojure code within the MCP server runtime"
  :inputSchema {:type "object"
                :properties {:code {:type "string"
                                    :description "Clojure code to evaluate"}}
                :required ["code"]}})