(ns nrepl-mcp-server.mcp-server.tools.local-eval
  "Local eval tool for MCP server introspection with Base64 Enhancement"
  (:require [cheshire.core :as json]))

;; =============================================================================
;; Base64 Utilities
;; =============================================================================

(defn- decode-base64
  "Decode base64 string to UTF-8 text"
  [b64-str]
  (String. (.decode (java.util.Base64/getDecoder) b64-str) "UTF-8"))

(defn- encode-base64
  "Encode UTF-8 text to base64 string"
  [text]
  (.encodeToString (java.util.Base64/getEncoder) (.getBytes text "UTF-8")))

(defn handle
  "Evaluate Clojure code within the MCP server runtime with base64 support.
   NEW: Base64 support eliminates quote escaping for AI agents and complex code."
  [{:keys [code code-base64 output-base64]}]
  (cond
    ;; Validation: Prevent ambiguity between code and code-base64
    (and code code-base64)
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "error"
                        :error "Cannot specify both 'code' and 'code-base64' parameters - use one or the other"}
                       {:pretty true})}]
     :isError true}

    ;; Validation: code or code-base64 is required
    (and (empty? code) (empty? code-base64))
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "error"
                        :error "No code provided - specify either 'code' or 'code-base64' parameter"}
                       {:pretty true})}]
     :isError true}

    ;; Process the code
    :else
    (let [;; Determine actual code to execute
          actual-code (cond
                        code code
                        code-base64 (try
                                      (decode-base64 code-base64)
                                      (catch Exception e
                                        (throw (ex-info "Failed to decode base64 code"
                                                        {:error (.getMessage e)
                                                         :code-base64 code-base64}))))
                        :else nil)]
      (if (empty? actual-code)
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :error "Decoded code is empty"}
                           {:pretty true})}]
         :isError true}
        (try
          ;; Use with-out-str to capture stdout from println statements
          (let [result-atom (atom nil)
                stdout-capture (with-out-str
                                 (let [result (eval (read-string actual-code))]
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
                                      :else (str "#" (.getName (class result)) " " result))
                ;; Create base response
                base-response {:status "success"
                               :code actual-code
                               :result serializable-result
                               :result-type (.getName (class result))
                               :stdout stdout-capture
                               :stderr ""}  ;; TODO: stderr capture is more complex
                ;; Add base64 encoding if requested
                final-response (if output-base64
                                 (cond-> base-response
                                   serializable-result (assoc :result-base64 (encode-base64 (str serializable-result)))
                                   (not-empty stdout-capture) (assoc :stdout-base64 (encode-base64 stdout-capture)))
                                 base-response)]
            {:content [{:type "text"
                        :text (json/generate-string final-response {:pretty true})}]})
          (catch Exception e
            (let [error-response {:status "error"
                                  :code actual-code
                                  :error (.getMessage e)
                                  :stdout ""
                                  :stderr ""
                                  :stacktrace (mapv str (.getStackTrace e))}
                  final-error-response (if output-base64
                                         (assoc error-response :error-base64 (encode-base64 (.getMessage e)))
                                         error-response)]
              {:content [{:type "text"
                          :text (json/generate-string final-error-response {:pretty true})}]
               :isError true})))))))

(def tool-name "local-eval")

(def metadata
  {:description "Execute Clojure code within the MCP server runtime with base64 encoding support. NEW: Base64 support eliminates quote escaping for AI agents and complex code."
   :inputSchema {:type "object"
                 :properties {:code {:type "string"
                                     :description "Clojure code to evaluate (mutually exclusive with code-base64)"}
                              :code-base64 {:type "string"
                                            :description "Clojure code as base64 string - eliminates quote escaping (mutually exclusive with code)"}
                              :output-base64 {:type "boolean"
                                              :description "Return result fields (result, stdout, stderr, error) as base64 encoded strings (default: false)"}}
                 :anyOf [{:required ["code"]}
                         {:required ["code-base64"]}]}})