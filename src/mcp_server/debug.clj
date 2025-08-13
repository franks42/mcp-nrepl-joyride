(ns mcp-server.debug
  "Debug tools for MCP server introspection and live modification"
  (:require [cheshire.core :as json])
  (:import [java.io StringWriter PrintWriter]))

(defn debug-eval
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

(defn debug-load-file
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