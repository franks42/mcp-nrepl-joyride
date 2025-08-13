(ns mcp-nrepl-proxy.tools.debug
  "Debug tools for MCP server introspection and live modification"
  (:require [cheshire.core :as json]
            [clojure.edn :as edn]
            [mcp-nrepl-proxy.utils :as utils]))

(defn tool-debug-eval
  "Evaluate Clojure code within the MCP server runtime itself.
   
   This provides a REPL-like interface into the running MCP server,
   allowing introspection of state, queues, connections, and even
   live code modification.
   
   WARNING: This is a powerful debugging tool - use with caution!
   
   Examples:
   - Inspect state: (debug-eval \"@mcp-nrepl-proxy.core/server-state\")
   - Check queues: (debug-eval \"@mcp-nrepl-proxy.state/message-queues\")
   - List connections: (debug-eval \"(keys (:connections @mcp-nrepl-proxy.nrepl-connection/connections))\")
   - Modify functions: (debug-eval \"(defn my-fn [] :modified)\")
   - Reload namespace: (debug-eval \"(require 'mcp-nrepl-proxy.core :reload)\")"
  [{:keys [code]}]
  (if (empty? code)
    {:content [{:type "text"
                :text "❌ Code parameter is required"}]
     :isError true}
    (try
      ;; Evaluate the code in the server's runtime
      (let [result (eval (read-string code))
            ;; Try to convert result to a serializable format
            serializable-result (cond
                                  ;; Handle nil
                                  (nil? result) nil

                                  ;; Already a string
                                  (string? result) result

                                  ;; Numbers, keywords, booleans
                                  (or (number? result)
                                      (keyword? result)
                                      (boolean? result)) result

                                  ;; Collections - convert to EDN string for complex data
                                  (or (map? result)
                                      (vector? result)
                                      (list? result)
                                      (set? result)) (pr-str result)

                                  ;; Atoms - deref and convert
                                  (instance? clojure.lang.Atom result)
                                  (str "#atom " (pr-str @result))

                                  ;; Functions/objects - just show type
                                  :else (str "#" (.getName (class result)) " " (str result)))]

        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "success"
                            :code code
                            :result serializable-result
                            :result-type (.getName (class result))}
                           {:pretty true})}]})

      (catch Exception e
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :code code
                            :error (.getMessage e)
                            :stacktrace (mapv str (.getStackTrace e))}
                           {:pretty true})}]
         :isError true}))))

(defn tool-debug-load-file
  "Load and evaluate a Clojure file in the MCP server runtime.
   
   This loads external debugging utilities and helper functions into the
   debug-eval environment. The file is read and evaluated in the server's
   SCI context, so any defined functions or vars persist for future debug-eval calls.
   
   Examples:
   - (debug-load-file \"debug-utils.clj\")
   - (debug-load-file \"/absolute/path/to/helpers.clj\")"
  [{:keys [file-path]}]
  (if (empty? file-path)
    {:content [{:type "text"
                :text "❌ file-path parameter is required"}]
     :isError true}
    (try
      ;; Read the file content
      (let [file-content (slurp file-path)
            ;; Split into individual forms to evaluate
            forms (read-string (str "(" file-content ")"))]

        ;; Evaluate each form in the file
        (let [results (mapv (fn [form]
                              (try
                                {:form (pr-str form)
                                 :result (pr-str (eval form))
                                 :status :success}
                                (catch Exception e
                                  {:form (pr-str form)
                                   :error (.getMessage e)
                                   :status :error})))
                            forms)
              success-count (count (filter #(= (:status %) :success) results))
              total-count (count results)]

          {:content [{:type "text"
                      :text (json/generate-string
                             {:status "completed"
                              :file file-path
                              :forms-evaluated total-count
                              :successful success-count
                              :failed (- total-count success-count)
                              :results results}
                             {:pretty true})}]}))

      (catch java.io.FileNotFoundException e
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :file file-path
                            :error "File not found"
                            :message (.getMessage e)}
                           {:pretty true})}]
         :isError true})

      (catch Exception e
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :file file-path
                            :error (.getMessage e)
                            :stacktrace (mapv str (.getStackTrace e))}
                           {:pretty true})}]
         :isError true}))))

(defn tool-debug-inspect
  "Inspect a specific var or atom in the MCP server.
   
   This is a safer version of debug-eval for common inspection tasks.
   
   Examples:
   - (debug-inspect \"mcp-nrepl-proxy.core/server-state\")
   - (debug-inspect \"mcp-nrepl-proxy.state/message-queues\")
   - (debug-inspect \"mcp-nrepl-proxy.nrepl-connection/connections\")"
  [state {:keys [var-name]}]
  (if (empty? var-name)
    {:content [{:type "text"
                :text "❌ var-name parameter is required"}]
     :isError true}
    (try
      (let [ns-sym (symbol (namespace var-name))
            var-sym (symbol (name var-name))
            _ (require ns-sym)
            the-var (ns-resolve ns-sym var-sym)]
        (if the-var
          (let [value @the-var
                ;; If it's an atom, deref it
                final-value (if (instance? clojure.lang.Atom value)
                              @value
                              value)]
            {:content [{:type "text"
                        :text (json/generate-string
                               {:var var-name
                                :type (if (instance? clojure.lang.Atom value)
                                        "atom"
                                        (.getName (class value)))
                                :value (pr-str final-value)}
                               {:pretty true})}]})
          {:content [{:type "text"
                      :text (str "❌ Var not found: " var-name)}]
           :isError true}))
      (catch Exception e
        {:content [{:type "text"
                    :text (str "❌ Error inspecting var: " (.getMessage e))}]
         :isError true}))))