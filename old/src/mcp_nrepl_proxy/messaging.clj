(ns mcp-nrepl-proxy.messaging
  "nREPL message handling and bencode protocol implementation"
  (:require [bencode.core :as bencode]
            [mcp-nrepl-proxy.uuid-v7 :as uuid]
            [clojure.string :as str]
            [clojure.set :as set]))

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
  [{:keys [out in id] :as conn} message & {:keys [timeout-ms update-activity-fn]}]
  (let [msg-with-id (assoc message :id (generate-id))]
    ;; Update last activity timestamp (via injected function)
    (when (and id update-activity-fn)
      (update-activity-fn id))

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
    update-activity-fn - Function to update connection activity timestamp
    track-pending-message-fn - Function to track pending messages
    update-message-status-fn - Function to update message status
  
  Returns:
    {:status :success :response merged-response} on success
    {:status :timeout :responses [...] :timeout-ms timeout-ms} on timeout
    {:status :error :responses [...] :error exception} on error
  
  Implementation:
    Uses send-message-async -> collect-responses-async pipeline
    for full async message handling with timeout support."
  [{:keys [out in id] :as conn} message timeout-ms
   & {:keys [update-activity-fn track-pending-message-fn update-message-status-fn]}]
  (let [msg-with-id (assoc message :id (generate-id))
        message-id (:id msg-with-id)]
    ;; Update last activity timestamp
    (when (and id update-activity-fn)
      (update-activity-fn id))

    ;; Track pending message in queue
    (when (and id track-pending-message-fn update-message-status-fn)
      (track-pending-message-fn id message-id message)
      (update-message-status-fn message-id :sending))

    ;; Log outgoing message
    (binding [*out* *err*]
      (println "[nREPL] 📤 Async sending:" (pr-str msg-with-id)))

    ;; Send bencode-encoded message
    (bencode/write-bencode out msg-with-id)
    (.flush out)

    ;; Update message status to sent
    (when (and id update-message-status-fn)
      (update-message-status-fn message-id :sent))

    ;; Use async collection with timeout
    (let [async-result (collect-responses-async in message-id timeout-ms)]
      (case (:status async-result)
        :success
        (let [merged-response (merge-responses (:responses async-result))]
          ;; Mark message as completed
          (when (and id update-message-status-fn)
            (update-message-status-fn message-id :completed))
          (binding [*out* *err*]
            (println "[nREPL] 📥 Async final merged response:" merged-response))
          {:status :success :response merged-response})

        :timeout
        (do
          ;; Mark message as expired due to timeout
          (when (and id update-message-status-fn)
            (update-message-status-fn message-id :expired
                                      :error-info {:type :timeout
                                                   :timeout-ms timeout-ms
                                                   :message "Message timed out waiting for response"}))
          (binding [*out* *err*]
            (println "[nREPL] ⏰ Async send-message timeout after" timeout-ms "ms"))
          async-result)

        :error
        (do
          ;; Mark message as failed due to error
          (when (and id update-message-status-fn)
            (update-message-status-fn message-id :failed
                                      :error-info {:type :communication-error
                                                   :error (:error async-result)
                                                   :message "Message failed due to communication error"}))
          (binding [*out* *err*]
            (println "[nREPL] ❌ Async send-message error:" (pr-str (:error async-result))))
          async-result)))))