(ns mcp-nrepl-proxy.nrepl-client
  "nREPL client implementation for Joyride using bencode protocol.
   
   Joyride's nREPL server uses bencode encoding for messages, not plain text.
   This implementation properly encodes/decodes messages using bencode."
  (:require [bencode.core :as bencode]
            [mcp-nrepl-proxy.uuid-v7 :as uuid]
            [clojure.string :as str]
            [clojure.set :as set])
  (:import [java.net Socket]
           [java.io InputStream OutputStream PushbackInputStream]))

;; Connection state management
(defonce ^:private connection-state
  (atom {:connections {}      ; Map of connection-id -> connection details
         :counter 0}))        ; Counter for generating unique connection IDs

;; Message Queue Lifecycle Management
(defonce ^:private message-queues
  (atom {:pending-messages {}     ; Map of connection-id -> #{message-ids}
         :message-records {}      ; Map of message-id -> message record
         :failure-records []}))   ; Vector of failure records (temporal ordered)

(defn- generate-connection-id
  "Generate a unique connection identifier"
  []
  (str "conn-" (:counter (swap! connection-state update :counter inc))))

(defn- track-pending-message
  "Add a message to the pending queue for a connection."
  [connection-id message-id message]
  (let [record {:message-id message-id
                :connection-id connection-id
                :message message
                :status :pending
                :created-at (System/currentTimeMillis)
                :last-updated (System/currentTimeMillis)}]
    (swap! message-queues
           (fn [queues]
             (-> queues
                 (update-in [:pending-messages connection-id]
                            (fn [pending-set]
                              (conj (or pending-set #{}) message-id)))
                 (assoc-in [:message-records message-id] record))))
    record))

(defn- update-message-status
  "Update message status and handle state transitions.
  
  Args:
    message-id - ID of message to update
    new-status - New status (:sending, :sent, :completed, :failed, :expired)
    error-info - Optional map with error details for failed messages
  
  State transitions:
    :pending -> :sending -> :sent -> :completed
    :pending/:sending/:sent -> :failed (with error-info)
    :pending/:sending/:sent -> :expired (timeout)
  
  Returns:
    Updated message record or nil if message not found"
  [message-id new-status & {:keys [error-info]}]
  (let [current-time (System/currentTimeMillis)
        result (atom nil)]
    (swap! message-queues
           (fn [queues]
             (if-let [existing-record (get-in queues [:message-records message-id])]
               (let [updated-record (cond-> (assoc existing-record
                                                   :status new-status
                                                   :last-updated current-time)
                                      error-info (assoc :error error-info))
                     connection-id (:connection-id existing-record)]
                 (reset! result updated-record)
                 ;; Update message record
                 (let [updated-queues (assoc-in queues [:message-records message-id] updated-record)]
                   ;; If message completed/failed, remove from pending
                   (if (#{:completed :failed :expired} new-status)
                     (-> updated-queues
                         (update-in [:pending-messages connection-id] disj message-id)
                         (update :failure-records
                                 (fn [failures]
                                   (if (#{:failed :expired} new-status)
                                     (conj failures {:message-id message-id
                                                     :connection-id connection-id
                                                     :status new-status
                                                     :error error-info
                                                     :failed-at current-time})
                                     failures))))
                     updated-queues)))
               ;; Message not found
               (do
                 (reset! result nil)
                 queues))))
    @result))

(defn- mark-connection-messages-failed
  "Mark all pending messages for a connection as failed.
  
  Args:
    connection-id - Connection ID to mark messages failed for
    error-type - Error type (:connection-closed, :connection-lost, :connection-reset, :timeout)
    error-message - Human-readable error message
  
  Returns:
    Number of messages marked as failed"
  [connection-id error-type error-message]
  (let [current-time (System/currentTimeMillis)
        marked-count (atom 0)]
    (swap! message-queues
           (fn [queues]
             (if-let [pending-message-ids (get-in queues [:pending-messages connection-id])]
               (let [error-info {:type error-type
                                 :message error-message
                                 :connection-id connection-id}]
                 (reset! marked-count (count pending-message-ids))
                 ;; Update all pending messages to failed status
                 (reduce
                  (fn [updated-queues message-id]
                    (let [message-record (get-in updated-queues [:message-records message-id])
                          failed-record (assoc message-record
                                               :status :failed
                                               :last-updated current-time
                                               :error error-info)]
                      (-> updated-queues
                          (assoc-in [:message-records message-id] failed-record)
                          (update :failure-records conj {:message-id message-id
                                                         :connection-id connection-id
                                                         :status :failed
                                                         :error error-info
                                                         :failed-at current-time}))))
                  (update queues :pending-messages dissoc connection-id)
                  pending-message-ids))
               ;; No pending messages for this connection
               (do
                 (reset! marked-count 0)
                 queues))))
    @marked-count))

(defn connect
  "Connect to nREPL server and return connection map with tracking"
  [host port]
  (let [socket (Socket. host port)
        out (.getOutputStream socket)
        in (PushbackInputStream. (.getInputStream socket))
        conn-id (generate-connection-id)
        conn {:socket socket
              :out out
              :in in
              :host host
              :port port
              :id conn-id
              :created-at (System/currentTimeMillis)
              :status :connected}]
    ;; Track connection in state
    (swap! connection-state assoc-in [:connections conn-id]
           {:host host
            :port port
            :created-at (:created-at conn)
            :status :connected})
    conn))

(defn close-connection
  "Close nREPL connection and update state. 
  Marks all pending messages for this connection as failed."
  [{:keys [socket id]}]
  (when socket
    (.close socket))
  ;; Mark pending messages as failed before updating connection state
  (when id
    (let [failed-count (mark-connection-messages-failed
                        id
                        :connection-closed
                        "Connection closed")]
      (when (> failed-count 0)
        (binding [*out* *err*]
          (println (str "[Queue] Marked " failed-count " pending messages as failed for connection " id)))))
    ;; Update connection state
    (swap! connection-state update-in [:connections id]
           assoc :status :closed
           :closed-at (System/currentTimeMillis))))

;; Connection state query functions
(defn get-connection-state
  "Get the current state of a connection by ID"
  [conn-id]
  (get-in @connection-state [:connections conn-id]))

(defn list-connections
  "List all tracked connections with their status"
  []
  (:connections @connection-state))

(defn active-connections
  "Get all active (connected) connections"
  []
  (into {}
        (filter (fn [[_ conn]] (= :connected (:status conn)))
                (:connections @connection-state))))

(defn cleanup-closed-connections
  "Remove closed connections from state that are older than threshold-ms"
  [& {:keys [threshold-ms] :or {threshold-ms (* 60 60 1000)}}] ; Default 1 hour
  (let [now (System/currentTimeMillis)
        cutoff (- now threshold-ms)]
    (swap! connection-state update :connections
           (fn [conns]
             (into {}
                   (remove (fn [[_ conn]]
                             (and (= :closed (:status conn))
                                  (<= (:closed-at conn 0) cutoff)))
                           conns))))))

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

(defn get-message-status
  "Get the status of a message by message-id.
  
  Returns:
    {:status :pending/:sending/:sent/:completed/:failed/:expired
     :message-id message-id
     :created-at timestamp
     :last-updated timestamp  
     :result response-data (if completed)
     :error-info error-details (if failed/expired)}
  
  Returns nil if message-id not found."
  [message-id]
  (when-let [record (get-in @message-queues [:message-records message-id])]
    record))

(defn queue-message-async
  "Queue an nREPL message for async processing.
  
  Args:
    connection - nREPL connection  
    message - nREPL message map
    timeout-ms - Optional timeout in milliseconds (default: 30000)
  
  Returns:
    {:message-id id :status :pending :timeout-ms timeout-ms}
  
  The message will be processed in the background using send-message-async.
  Use get-message-status to check completion and get results."
  [connection message & {:keys [timeout-ms] :or {timeout-ms 30000}}]
  (let [message-id (generate-id)
        msg-with-id (assoc message :id message-id)]

    ;; Track the pending message
    (when (:id connection)
      (track-pending-message (:id connection) message-id message))

    ;; Start async processing in background
    (future
      (try
        (let [result (send-message-async connection msg-with-id timeout-ms)]
          ;; Store the result in the message record and update status
          (case (:status result)
            :success
            (do
              ;; Update message status to completed and store result
              (update-message-status message-id :completed)
              (swap! message-queues assoc-in [:message-records message-id :result] (:response result)))

            (:timeout :error)
            (do
              ;; Update message status to appropriate failure state
              (update-message-status message-id
                                     (case (:status result)
                                       :timeout :expired
                                       :error :failed)
                                     :error-info (select-keys result [:status :error :timeout-ms :responses]))
              (swap! message-queues assoc-in [:message-records message-id :error-info]
                     (select-keys result [:status :error :timeout-ms :responses])))))
        (catch Exception e
          ;; Handle unexpected errors
          (update-message-status message-id :failed
                                 :error-info {:type :system-error
                                              :error (.getMessage e)
                                              :message "Unexpected error during async processing"}))))

    ;; Return immediately with message-id
    {:message-id message-id
     :status :pending
     :timeout-ms timeout-ms}))

(defn fetch-result
  "Fetch the result of an async message by message-id.
  
  Args:
    message-id - The message ID returned by queue-message-async
  
  Returns:
    {:status :pending} - Still processing
    {:status :completed :result response-data} - Successfully completed
    {:status :failed/:expired :error-info {...}} - Failed or timed out
    {:status :not-found} - Message ID not found
  
  For completed messages, :result contains the merged nREPL response.
  For failed/expired messages, :error-info contains error details."
  [message-id]
  (if-let [record (get-message-status message-id)]
    (case (:status record)
      :completed
      {:status :completed
       :result (:result record)
       :message-id message-id}

      (:failed :expired)
      {:status (:status record)
       :error-info (:error-info record)
       :message-id message-id}

      ;; Still processing (:pending, :sending, :sent)
      {:status :pending
       :message-id message-id})

    {:status :not-found
     :message-id message-id}))