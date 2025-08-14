(ns mcp-nrepl-proxy.tools.evaluation
  "MCP tools for code evaluation and namespace management"
  (:require [clojure.string :as str]
            [mcp-nrepl-proxy.nrepl-client :as nrepl]
            [mcp-nrepl-proxy.utils :as utils]))

; State and connection management will be passed as parameters
; to maintain functional design

(defn eval-in-joyride
  "Evaluate code in connected Joyride nREPL (for Calva convenience)"
  [state code]
  (if-let [conn (:nrepl-conn @state)]
    (try
      (nrepl/eval-code conn code)
      (catch Exception e
        {:error (.getMessage e)}))
    {:error "No Joyride nREPL connection"}))

(defn tool-nrepl-eval
  "Evaluate Clojure code via nREPL"
  [state ensure-nrepl-connection {:keys [code session ns]}]
  (let [conn-result (ensure-nrepl-connection)]
    (if (:success conn-result)
      (try
        (let [conn (:connection conn-result)
              result (nrepl/eval-code conn code
                                      :session session
                                      :ns ns)]
          (utils/cache-command state code result)
          (utils/log state :debug "nREPL result:" result)

          ;; Store session info if provided in response
          (when-let [response-session (:session result)]
            (swap! state assoc-in [:sessions response-session]
                   {:created (System/currentTimeMillis)
                    :last-used (System/currentTimeMillis)}))

          ;; Format clean response for MCP client
          (let [value-field (:value result)
                output-field (:out result)
                has-meaningful-value (and value-field
                                          (not= "" value-field)
                                          (not= "nil" value-field))
                has-output (and output-field (not= "" (str/trim output-field)))
                has-error (:ex result)]
            (utils/log state :debug "Result keys:" (keys result))
            (utils/log state :debug "Value field exists?" (contains? result :value))
            (utils/log state :debug "Value field content:" (pr-str value-field))
            (utils/log state :debug "Output field content:" (pr-str output-field))
            (utils/log state :debug "Response decision: has-meaningful-value=" has-meaningful-value " has-output=" has-output " has-error=" has-error)
            (cond
              ;; Error in evaluation
              has-error
              {:content [{:type "text"
                          :text (str "❌ " (:ex result))}]
               :isError true}

              ;; Output (prefer output over nil values)
              has-output
              {:content [{:type "text"
                          :text (str/trim (:out result))}]
               :session (:session result)
               :namespace (:ns result)}

              ;; Meaningful value (non-nil)
              has-meaningful-value
              {:content [{:type "text"
                          :text (str (:value result))}]
               :session (:session result)
               :namespace (:ns result)}

              ;; Just status or nil value
              :else
              {:content [{:type "text"
                          :text "✅ Executed successfully"}]
               :session (:session result)
               :namespace (:ns result)})))
        (catch Exception e
          (utils/log state :error "nREPL eval failed:" (.getMessage e))
          (utils/log state :error "Exception type:" (type e))
          (utils/log state :error "Stack trace:" (with-out-str (.printStackTrace e)))
          {:content [{:type "text"
                      :text (str "❌ Evaluation failed: " (.getMessage e) " (type: " (type e) ")")}]
           :isError true}))
      {:content [{:type "text"
                  :text (str "❌ No nREPL connection: " (:error conn-result))}]
       :isError true})))

(defn tool-nrepl-load-file
  "Load a Clojure file into the nREPL session"
  [state ensure-nrepl-connection {:keys [file-path session ns]}]
  (let [conn-result (ensure-nrepl-connection)]
    (if (:success conn-result)
      (try
        ;; Validate file exists and is readable
        (when-not (and file-path (.exists (java.io.File. file-path)))
          (throw (Exception. (str "File not found: " file-path))))

        (let [conn (:connection conn-result)
              result (nrepl/load-file conn file-path
                                      :session session
                                      :ns ns)]
          (utils/log state :debug "Load-file result:" result)

          ;; Store session info if provided in response
          (when-let [response-session (:session result)]
            (swap! state assoc-in [:sessions response-session]
                   {:created (System/currentTimeMillis)
                    :last-used (System/currentTimeMillis)}))

          ;; Format response similar to eval
          (let [has-error (:ex result)
                has-output (and (:out result) (not= "" (str/trim (:out result))))]
            (cond
              has-error
              {:content [{:type "text"
                          :text (str "❌ Load failed: " (:ex result))}]
               :isError true}

              has-output
              {:content [{:type "text"
                          :text (str "✅ File loaded: " file-path "\n" (:out result))}]}

              :else
              {:content [{:type "text"
                          :text (str "✅ File loaded successfully: " file-path)}]})))

        (catch Exception e
          (utils/log state :error "Load-file failed:" (.getMessage e))
          {:content [{:type "text"
                      :text (str "❌ Load failed: " (.getMessage e))}]
           :isError true}))
      {:content [{:type "text"
                  :text "❌ No nREPL connection available. Use nrepl-connect first."}]
       :isError true})))

(defn tool-nrepl-require
  "Require/load a namespace"
  [state ensure-nrepl-connection {:keys [namespace session as refer reload]}]
  (let [conn-result (ensure-nrepl-connection)]
    (if (:success conn-result)
      (try
        (let [conn (:connection conn-result)
              result (nrepl/require-ns conn (symbol namespace)
                                       :session session
                                       :as (when as (symbol as))
                                       :refer refer
                                       :reload reload)]
          (if (:ex result)
            {:content [{:type "text"
                        :text (str "❌ Require failed: " (:ex result))}]
             :isError true}
            {:content [{:type "text"
                        :text (str "✅ Successfully required " namespace
                                   (when as (str " as " as))
                                   (when refer (str " referring " refer))
                                   (when reload " (with reload)"))}]}))
        (catch Exception e
          (utils/log state :error "Require failed:" (.getMessage e))
          {:content [{:type "text"
                      :text (str "❌ Require failed: " (.getMessage e))}]
           :isError true}))
      {:content [{:type "text"
                  :text "❌ No nREPL connection available. Use nrepl-connect first."}]
       :isError true})))

