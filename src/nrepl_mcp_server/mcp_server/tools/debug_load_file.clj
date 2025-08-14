(ns nrepl-mcp-server.mcp-server.tools.debug-load-file
  "Debug load-file tool for loading Clojure files in MCP server runtime"
  (:require [cheshire.core :as json]
            [nrepl-mcp-server.state.tool-registry :as registry])
  (:import [java.io StringWriter PrintWriter]))

(defn handle
  "Load and evaluate a Clojure file in the MCP server runtime"
  [{:keys [file-path]}]
  (if (empty? file-path)
    {:content [{:type "text"
                :text "❌ file-path parameter is required"}]
     :isError true}
    (try
      (let [file-content (slurp file-path)
            ;; Split into individual forms
            forms (read-string (str "(" file-content ")"))
            ;; Evaluate each form with output capture
            stdout-writer (StringWriter.)
            stderr-writer (StringWriter.)]
        (binding [*out* (PrintWriter. stdout-writer)
                  *err* (PrintWriter. stderr-writer)]
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
                total-count (count results)
                captured-stdout (str stdout-writer)
                captured-stderr (str stderr-writer)]
            {:content [{:type "text"
                        :text (json/generate-string
                               {:status "completed"
                                :file file-path
                                :forms-evaluated total-count
                                :successful success-count
                                :failed (- total-count success-count)
                                :results results
                                :stdout captured-stdout
                                :stderr captured-stderr}
                               {:pretty true})}]})))
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

;; =============================================================================
;; Self Registration
;; =============================================================================

;; Self-register this tool when namespace loads
(registry/register-tool!
 "debug-load-file"
 handle
 {:description "Load and evaluate a Clojure file in the MCP server runtime"
  :inputSchema {:type "object"
                :properties {:file-path {:type "string"
                                         :description "Path to Clojure file to load"}}
                :required ["file-path"]}})