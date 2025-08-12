(ns mcp-nrepl-proxy.nrepl-client
  "nREPL client implementation for Joyride using bencode protocol.
   
   Joyride's nREPL server uses bencode encoding for messages, not plain text.
   This implementation properly encodes/decodes messages using bencode."
  (:require [bencode.core :as bencode]
            [mcp-nrepl-proxy.uuid-v7 :as uuid]
            [mcp-nrepl-proxy.nrepl-connection :as nrepl-conn]
            [mcp-nrepl-proxy.state :as state]
            [clojure.string :as str]
            [clojure.set :as set])
  (:import [java.io InputStream OutputStream]))

;; Connection state management - delegate to nrepl-connection namespace
(def ^:private connection-state (nrepl-conn/get-connection-atom))

;; Message Queue Lifecycle Management - delegate to state namespace
(def ^:private message-queues (state/get-message-queues-atom))

;; Get internal state functions for delegation
(def ^:private internal-fns (state/expose-internal-functions))
(def ^:private track-pending-message (:track-pending-message internal-fns))
(def ^:private update-message-status (:update-message-status internal-fns))
(def ^:private mark-connection-messages-failed (:mark-connection-messages-failed internal-fns))

;; Connection functions delegated to nrepl-connection namespace
(def connect nrepl-conn/connect)
(def get-connection-state nrepl-conn/get-connection-state)
(def list-connections nrepl-conn/list-connections)
(def active-connections nrepl-conn/active-connections)
(def cleanup-closed-connections nrepl-conn/cleanup-closed-connections)

;; State functions delegated to state namespace
(def get-message-status state/get-message-status)
(def fetch-result state/fetch-result)

(defn close-connection
  "Close nREPL connection and update state. 
  Marks all pending messages for this connection as failed."
  [conn]
  (nrepl-conn/close-connection conn mark-connection-messages-failed))

(defn generate-id
  "Generate RFC 9562 compliant UUID v7 with operation tag suffix."
  [& {:keys [tag] :or {tag "msg"}}]
  (uuid/uuid-v7-with-tag :tag tag))

(defn- bytes-to-string
  "Convert byte array to UTF-8 string"
  [obj]
  (cond
    (instance? (Class/forName "[B") obj) (String. obj "UTF-8")
    (string? obj) obj
    :else (str obj)))

(defn- convert-bencode-response
  "Convert bencode byte arrays to strings recursively, using keyword keys for maps"
  [obj]
  (cond
    (map? obj) (into {} (map (fn [[k v]] [(keyword (bytes-to-string k)) (convert-bencode-response v)]) obj))
    (vector? obj) (mapv convert-bencode-response obj)
    (seq? obj) (map convert-bencode-response obj)
    :else (bytes-to-string obj)))

(defn- collect-responses
  "Collect multiple nREPL response messages until 'done' status.
  
  Args:
    in - Input stream for reading nREPL responses
    message-id - Message ID to collect responses for
    timeout-ms - Optional timeout in milliseconds (default: no timeout)
  
  Returns:
    Vector of response messages collected until 'done' status"
  [in message-id & {:keys [timeout-ms]}]
  (loop [responses []]
    (let [read-result (try
                        (let [raw-response (bencode/read-bencode in)
                              converted-response (convert-bencode-response raw-response)]
                          (binding [*out* *err*]
                            (println "[nREPL] 📥 Received response:" converted-response))
                          {:success true :response converted-response})
                        (catch Exception e
                          (binding [*out* *err*]
                            (println "[nREPL] ❌ Error reading response:" (.getMessage e)))
                          {:success false :error e}))]
      (if (:success read-result)
        (let [response (:response read-result)
              new-responses (conj responses response)
              status (:status response)]
          ;; Continue reading until we get a "done" status
          (if (and status (some #(= "done" %) status))
            new-responses
            (recur new-responses)))
        ;; Error case - return what we have so far
        responses))))

(defn- collect-responses-async
  "Async version of collect-responses with promise-based timeout handling.
  
  Args:
    in - Input stream for reading nREPL responses
    message-id - Message ID to collect responses for
    timeout-ms - Timeout in milliseconds (required for async version)
  
  Returns:
    {:status :success :responses [...]} on success
    {:status :timeout :responses [...]} on timeout
    {:status :error :responses [...] :error exception} on error
  
  Implementation:
    Uses promise-based timeout with (deref promise timeout-ms :timeout) pattern
    as verified working in Babashka runtime environment."
  [in message-id timeout-ms]
  (let [result-promise (promise)
        worker-future (future
                        (try
                          (let [responses (loop [responses []]
                                            (let [read-result (try
                                                                (let [raw-response (bencode/read-bencode in)
                                                                      converted-response (convert-bencode-response raw-response)]
                                                                  (binding [*out* *err*]
                                                                    (println "[nREPL] 📥 Async received response:" converted-response))
                                                                  {:success true :response converted-response})
                                                                (catch Exception e
                                                                  (binding [*out* *err*]
                                                                    (println "[nREPL] ❌ Async error reading response:" (.getMessage e)))
                                                                  {:success false :error e}))]
                                              (if (:success read-result)
                                                (let [response (:response read-result)
                                                      new-responses (conj responses response)
                                                      status (:status response)]
                                                  ;; Continue reading until we get a "done" status
                                                  (if (and status (some #(= "done" %) status))
                                                    new-responses
                                                    (recur new-responses)))
                                                ;; Error case - return what we have so far with error
                                                (do
                                                  (deliver result-promise {:status :error
                                                                           :responses responses
                                                                           :error (:error read-result)})
                                                  responses))))]
                            (deliver result-promise {:status :success :responses responses}))
                          (catch Exception e
                            (binding [*out* *err*]
                              (println "[nREPL] ❌ Async worker error:" (.getMessage e)))
                            (deliver result-promise {:status :error :responses [] :error e}))))]

    ;; Use promise-based timeout as verified in Babashka
    (let [result (deref result-promise timeout-ms :timeout)]
      (if (= result :timeout)
        (do
          ;; Cancel the worker future and return timeout result
          (future-cancel worker-future)
          (binding [*out* *err*]
            (println "[nREPL] ⏰ Async timeout after" timeout-ms "ms"))
          {:status :timeout :responses [] :timeout-ms timeout-ms})
        result))))

(defn- merge-responses
  "Merge multiple nREPL responses into a single response, preserving ALL fields.
  
  Special handling:
  - :out, :err - concatenated from all responses
  - :value, :ex, :ns, :session - take last non-nil value
  - :status - take from last response
  - All other fields - use first non-nil value (most operations return single response)
  
  This ensures no nREPL response data is lost."
  [responses]
  (if (empty? responses)
    {}
    (let [;; Special concatenation fields
          all-out (apply str (keep :out responses))
          all-err (apply str (keep :err responses))

          ;; Fields that should use the last non-nil value
          final-value (last (keep :value responses))
          final-ex (last (keep :ex responses))
          final-ns (last (keep :ns responses))
          final-session (last (keep :session responses))
          final-status (:status (last responses))

          ;; Get all unique keys from all responses
          all-keys (->> responses
                        (mapcat keys)
                        (into #{}))

          ;; Fields with special handling (don't process with generic logic)
          special-fields #{:out :err :value :ex :ns :session :status}

          ;; Generic fields - use first non-nil value
          generic-fields (set/difference all-keys special-fields)

          ;; Build merged response starting with special fields
          base-merged (cond-> {}
                        (not-empty all-out) (assoc :out all-out)
                        (not-empty all-err) (assoc :err all-err)
                        final-value (assoc :value final-value)
                        final-ex (assoc :ex final-ex)
                        final-ns (assoc :ns final-ns)
                        final-session (assoc :session final-session)
                        final-status (assoc :status final-status))

          ;; Add all generic fields using first non-nil value
          full-merged (reduce
                       (fn [merged-map field-key]
                         (if-let [field-value (some field-key responses)]
                           (assoc merged-map field-key field-value)
                           merged-map))
                       base-merged
                       generic-fields)]

      ;; Log any unknown fields for debugging (only in debug mode)
      (when (and (System/getenv "MCP_DEBUG")
                 (not-empty generic-fields))
        (binding [*out* *err*]
          (println "[nREPL] 🔍 Merged response contains fields:"
                   (str/join ", " (map name (sort (keys full-merged)))))))

      full-merged)))

(defn send-message
  "Send nREPL message using bencode and collect all response messages.
  
  Args:
    connection - Map with :out and :in streams
    message - nREPL message to send
    timeout-ms - Optional timeout in milliseconds (default: no timeout)
  
  Returns:
    Merged response from nREPL server"
  [{:keys [out in id] :as conn} message & {:keys [timeout-ms]}]
  (let [msg-with-id (assoc message :id (generate-id))]
    ;; Update last activity timestamp
    (when id
      (swap! connection-state update-in [:connections id]
             assoc :last-activity (System/currentTimeMillis)))

    ;; Log outgoing message
    (binding [*out* *err*]
      (println "[nREPL] 📤 Sending:" (pr-str msg-with-id)))

    ;; Send bencode-encoded message
    (bencode/write-bencode out msg-with-id)
    (.flush out)

    ;; Collect all response messages until "done"
    (let [responses (collect-responses in (:id msg-with-id) :timeout-ms timeout-ms)
          merged-response (merge-responses responses)]
      (binding [*out* *err*]
        (println "[nREPL] 📥 Final merged response:" merged-response))
      merged-response)))

(defn send-message-async
  "Async version of send-message using promise-based timeout handling.
  
  Args:
    connection - Map with :out and :in streams
    message - nREPL message to send  
    timeout-ms - Timeout in milliseconds (required for async version)
  
  Returns:
    {:status :success :response merged-response} on success
    {:status :timeout :responses [...] :timeout-ms timeout-ms} on timeout
    {:status :error :responses [...] :error exception} on error
  
  Implementation:
    Uses send-message-async -> collect-responses-async pipeline
    for full async message handling with timeout support."
  [{:keys [out in id] :as conn} message timeout-ms]
  (let [msg-with-id (assoc message :id (generate-id))
        message-id (:id msg-with-id)]
    ;; Update last activity timestamp
    (when id
      (swap! connection-state update-in [:connections id]
             assoc :last-activity (System/currentTimeMillis)))

    ;; Track pending message in queue
    (when id
      (track-pending-message id message-id message)
      (update-message-status message-id :sending))

    ;; Log outgoing message
    (binding [*out* *err*]
      (println "[nREPL] 📤 Async sending:" (pr-str msg-with-id)))

    ;; Send bencode-encoded message
    (bencode/write-bencode out msg-with-id)
    (.flush out)

    ;; Update message status to sent
    (when id
      (update-message-status message-id :sent))

    ;; Use async collection with timeout
    (let [async-result (collect-responses-async in message-id timeout-ms)]
      (case (:status async-result)
        :success
        (let [merged-response (merge-responses (:responses async-result))]
          ;; Mark message as completed
          (when id
            (update-message-status message-id :completed))
          (binding [*out* *err*]
            (println "[nREPL] 📥 Async final merged response:" merged-response))
          {:status :success :response merged-response})

        :timeout
        (do
          ;; Mark message as expired due to timeout
          (when id
            (update-message-status message-id :expired
                                   :error-info {:type :timeout
                                                :timeout-ms timeout-ms
                                                :message "Message timed out waiting for response"}))
          (binding [*out* *err*]
            (println "[nREPL] ⏰ Async send-message timeout after" timeout-ms "ms"))
          async-result)

        :error
        (do
          ;; Mark message as failed due to error
          (when id
            (update-message-status message-id :failed
                                   :error-info {:type :communication-error
                                                :error (:error async-result)
                                                :message "Message failed due to communication error"}))
          (binding [*out* *err*]
            (println "[nREPL] ❌ Async send-message error:" (pr-str (:error async-result))))
          async-result)))))

(defn eval-code
  "Evaluate code in nREPL session"
  [conn code & {:keys [session ns]}]
  (let [message (cond-> {:op "eval" :code code}
                  session (assoc :session session)
                  ns (assoc :ns ns))]
    (send-message conn message)))

(defn create-session
  "Create new nREPL session"
  [conn]
  (send-message conn {:op "clone"}))

(defn close-session
  "Close nREPL session"
  [conn session-id]
  (send-message conn {:op "close" :session session-id}))

(defn describe-server
  "Get server description/capabilities"
  [conn]
  (send-message conn {:op "describe"}))

(defn load-file
  "Load a file into the nREPL session"
  [conn file-path & {:keys [session ns]}]
  (let [file-content (slurp file-path)
        msg (cond-> {:op "load-file"
                     :file file-content
                     :file-path file-path
                     :file-name (.getName (java.io.File. file-path))}
              session (assoc :session session)
              ns (assoc :ns ns))]
    (send-message conn msg)))

(defn doc
  "Get documentation for a symbol"
  [conn symbol & {:keys [session ns]}]
  (let [msg (cond-> {:op "info" :symbol symbol}
              session (assoc :session session)
              ns (assoc :ns ns))]
    (send-message conn msg)))

(defn source
  "Get source code for a symbol"
  [conn symbol & {:keys [session ns]}]
  (let [msg (cond-> {:op "info" :symbol symbol}
              session (assoc :session session)
              ns (assoc :ns ns))]
    (send-message conn msg)))

(defn complete
  "Get completions for a symbol prefix"
  [conn prefix & {:keys [session ns context]}]
  (let [msg (cond-> {:op "completions" :prefix prefix}
              session (assoc :session session)
              ns (assoc :ns ns)
              context (assoc :context context))]
    (send-message conn msg)))

(defn apropos
  "Find symbols matching query"
  [conn query & {:keys [session ns search-ns privates? case-sensitive?]}]
  (let [msg (cond-> {:op "apropos" :query query}
              session (assoc :session session)
              ns (assoc :ns ns)
              search-ns (assoc :search-ns search-ns)
              (some? privates?) (assoc :privates? privates?)
              (some? case-sensitive?) (assoc :case-sensitive? case-sensitive?))]
    (send-message conn msg)))

(defn require-ns
  "Require/load a namespace"
  [conn ns-symbol & {:keys [session as refer reload]}]
  (let [require-form (cond
                       ;; Simple require without options
                       (and (not as) (not refer) (not reload))
                       (list 'require (list 'quote ns-symbol))

                       ;; Require with options - build vector form
                       :else
                       (let [ns-vector (cond-> [ns-symbol]
                                         as (conj :as (symbol as))
                                         refer (conj :refer refer)
                                         reload (conj :reload reload))]
                         (list 'require ns-vector)))
        code (pr-str require-form)
        msg (cond-> {:op "eval" :code code}
              session (assoc :session session))]
    (send-message conn msg)))

(defn interrupt
  "Interrupt evaluation"
  [conn & {:keys [session interrupt-id]}]
  (let [msg (cond-> {:op "interrupt"}
              session (assoc :session session)
              interrupt-id (assoc :interrupt-id interrupt-id))]
    (send-message conn msg)))

(defn stacktrace
  "Get stacktrace for the last exception"
  [conn & {:keys [session]}]
  (let [msg (cond-> {:op "stacktrace"}
              session (assoc :session session))]
    (send-message conn msg)))

;; === ASYNC QUEUE MANAGEMENT ===

(defn queue-message-async
  "Queue an nREPL message for async processing - wrapper for state namespace function"
  [connection message & {:keys [timeout-ms] :or {timeout-ms 30000}}]
  (state/queue-message-async connection message send-message-async :timeout-ms timeout-ms))