(ns nrepl-mcp-server.mcp-server.tools.nrepl-eval
  "Simple nREPL eval tool using delegation to nrepl-send-message - Phase 2b.6 Refactored with Base64 Enhancement"
  (:require [nrepl-mcp-server.mcp-server.tools.tool-delegation :as delegate]
            [cheshire.core :as json]
            [clojure.edn :as edn]))

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

;; =============================================================================
;; EDN to JSON Conversion Helpers
;; =============================================================================

(defn- convert-edn-to-json
  "Convert EDN value to JSON-compatible structure.
   Handles keywords, collections, and preserves simple values."
  [edn-value]
  (cond
    ;; Simple values pass through
    (or (string? edn-value) (number? edn-value) (boolean? edn-value) (nil? edn-value))
    edn-value

    ;; Keywords become strings  
    (keyword? edn-value)
    (name edn-value)

    ;; Maps - convert keyword keys to strings
    (map? edn-value)
    (into {} (map (fn [[k v]]
                    [(if (keyword? k) (name k) (str k))
                     (convert-edn-to-json v)])
                  edn-value))

    ;; Collections - recursively convert
    (coll? edn-value)
    (mapv convert-edn-to-json edn-value)

    ;; Everything else as string representation
    :else
    (str edn-value)))

(defn- try-parse-edn
  "Attempt to parse EDN string, return nil if parsing fails."
  [value-str]
  (try
    (when (and value-str (string? value-str) (not= value-str "nil"))
      (edn/read-string value-str))
    (catch Exception _
      nil)))

;; =============================================================================
;; Response Formatting
;; =============================================================================

(defn- format-nrepl-eval-response
  "Format nrepl-send-message result as nrepl-eval response with EDN conversion and optional base64 encoding."
  [sync-result code output-base64]
  (if (delegate/is-success-result? sync-result)
    ;; Success - extract nREPL response and format
    (let [result-data (delegate/extract-result-data sync-result :result)
          nrepl-response (:response result-data)
          value-str (:value nrepl-response)
          base-response {:status "success"
                         :operation "nrepl-eval"
                         :code code
                         :value value-str
                         :ns (:ns nrepl-response)}
          ;; Add stdout/stderr if present
          response-with-output (cond-> base-response
                                 (:out nrepl-response) (assoc :out (:out nrepl-response))
                                 (:err nrepl-response) (assoc :err (:err nrepl-response)))]

      ;; Try to add EDN parsing and base64 encoding
      (let [response-with-edn (if-let [parsed-value (try-parse-edn value-str)]
                                (try
                                  (assoc response-with-output :value-parsed (convert-edn-to-json parsed-value))
                                  (catch Exception _
                                    ;; Conversion failed - return without value-parsed
                                    response-with-output))
                                ;; No EDN parsing possible - return as is
                                response-with-output)
            ;; Add base64 encoding if requested
            final-response (if output-base64
                             (cond-> response-with-edn
                               value-str (assoc :value-base64 (encode-base64 value-str))
                               (:out response-with-edn) (assoc :out-base64 (encode-base64 (:out response-with-edn)))
                               (:err response-with-edn) (assoc :err-base64 (encode-base64 (:err response-with-edn))))
                             response-with-edn)]
        {:content [{:type "text"
                    :text (json/generate-string final-response {:pretty true})}]}))

    ;; Error - update operation name and pass through
    (let [error-response (delegate/extract-result-data sync-result :error)]
      {:content [{:type "text"
                  :text (json/generate-string
                         (assoc error-response :operation "nrepl-eval")
                         {:pretty true})}]
       :isError true})))

(defn- format-error-response
  "Format a simple error response for nrepl-eval."
  [error-message]
  {:content [{:type "text"
              :text (json/generate-string
                     {:status "error"
                      :operation "nrepl-eval"
                      :error error-message}
                     {:pretty true})}]
   :isError true})

;; =============================================================================
;; Main Handler
;; =============================================================================

(defn handle
  "Evaluate Clojure code via nREPL using delegation to nrepl-send-message.
   Supports timeout recovery, connection selection, EDN-to-JSON conversion, and base64 encoding.
   NEW: Base64 support eliminates quote escaping for AI agents and complex code."
  [{:keys [code code-base64 output-base64 message-id timeout connection] :or {timeout 30000}}]

  (cond
    ;; Validation: Prevent ambiguity between code and code-base64
    (and code code-base64)
    (format-error-response "Cannot specify both 'code' and 'code-base64' parameters - use one or the other")

    ;; Validation: code or code-base64 is required for normal evaluation
    (and (empty? code) (empty? code-base64) (not message-id))
    (format-error-response "No code provided - specify either 'code' or 'code-base64' parameter")

    ;; Delegate to nrepl-send-message sync wrapper
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
                        :else nil)
          nrepl-message (when actual-code {:op "eval" :code actual-code})
          result (delegate/call-async-tool "nrepl-send-message"
                                           (cond-> {:timeout-ms timeout}
                                             connection (assoc :connection connection)
                                             message-id (assoc :message-id message-id)
                                             nrepl-message (assoc :message nrepl-message)))]
      ;; Format result as nrepl-eval response with EDN conversion and optional base64 encoding
      (format-nrepl-eval-response result actual-code output-base64))))

;; =============================================================================
;; Tool Metadata
;; =============================================================================

(def tool-name "nrepl-eval")

(def metadata
  {:description "Evaluate Clojure code via nREPL with clean delegation, timeout recovery, connection selection, EDN-to-JSON conversion, and base64 encoding. NEW: Base64 support eliminates quote escaping for AI agents and complex code. Returns both string representation (value) and parsed structure (value-parsed) for programmatic access."
   :inputSchema {:type "object"
                 :properties {:code {:type "string"
                                     :description "Clojure code to evaluate (mutually exclusive with code-base64)"}
                              :code-base64 {:type "string"
                                            :description "Clojure code as base64 string - eliminates quote escaping (mutually exclusive with code)"}
                              :output-base64 {:type "boolean"
                                              :description "Return result fields (value, out, err) as base64 encoded strings (default: false)"}
                              :connection {:type "string"
                                           :description "Connection identifier (nickname, connection-id, or host:port). Optional - uses single connection if not specified."}
                              :timeout {:type "integer"
                                        :description "Timeout in milliseconds (default: 30000)"
                                        :minimum 1000
                                        :maximum 300000}
                              :message-id {:type "string"
                                           :description "Message ID for timeout recovery - call with same code and this ID to check for delayed result"}}
                 :anyOf [{:required ["code"]}
                         {:required ["code-base64"]}
                         {:required ["message-id"]}]}})