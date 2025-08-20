(ns nrepl-mcp-server.mcp-server.tools.nrepl-eval
  "Simple nREPL eval tool using delegation to nrepl-send-message - Phase 2b.6 Refactored"
  (:require [nrepl-mcp-server.mcp-server.tools.tool-delegation :as delegate]
            [cheshire.core :as json]
            [clojure.edn :as edn]))

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
  "Format nrepl-send-message result as nrepl-eval response with EDN conversion."
  [sync-result code]
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

      ;; Try to add EDN parsing and wrap in proper MCP format
      (let [final-response (if-let [parsed-value (try-parse-edn value-str)]
                             (try
                               (assoc response-with-output :value-parsed (convert-edn-to-json parsed-value))
                               (catch Exception _
                                 ;; Conversion failed - return without value-parsed
                                 response-with-output))
                             ;; No EDN parsing possible - return as is
                             response-with-output)]
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
   Supports timeout recovery, connection selection, and EDN-to-JSON conversion."
  [{:keys [code message-id timeout connection] :or {timeout 30000}}]

  (cond
    ;; Validation: code is required for normal evaluation
    (and (empty? code) (not message-id))
    (format-error-response "No code provided")

    ;; Delegate to nrepl-send-message sync wrapper
    :else
    (let [nrepl-message (when code {:op "eval" :code code})
          result (delegate/call-async-tool "nrepl-send-message"
                                           (cond-> {:timeout-ms timeout}
                                             connection (assoc :connection connection)
                                             message-id (assoc :message-id message-id)
                                             nrepl-message (assoc :message nrepl-message)))]
      ;; Format result as nrepl-eval response with EDN conversion
      (format-nrepl-eval-response result code))))

;; =============================================================================
;; Tool Metadata
;; =============================================================================

(def tool-name "nrepl-eval")

(def metadata
  {:description "Evaluate Clojure code via nREPL with clean delegation, timeout recovery, connection selection, and EDN-to-JSON conversion. Returns both string representation (value) and parsed structure (value-parsed) for programmatic access."
   :inputSchema {:type "object"
                 :properties {:code {:type "string"
                                     :description "Clojure code to evaluate"}
                              :connection {:type "string"
                                           :description "Connection identifier (nickname, connection-id, or host:port). Optional - uses single connection if not specified."}
                              :timeout {:type "integer"
                                        :description "Timeout in milliseconds (default: 30000)"
                                        :minimum 1000
                                        :maximum 300000}
                              :message-id {:type "string"
                                           :description "Message ID for timeout recovery - call with same code and this ID to check for delayed result"}}
                 :required ["code"]}})