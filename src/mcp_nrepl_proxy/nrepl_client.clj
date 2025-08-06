(ns mcp-nrepl-proxy.nrepl-client
  "nREPL client implementation for Joyride using bencode protocol.
   
   Joyride's nREPL server uses bencode encoding for messages, not plain text.
   This implementation properly encodes/decodes messages using bencode."
  (:require [bencode.core :as bencode]
            [clojure.string :as str])
  (:import [java.net Socket]
           [java.io InputStream OutputStream PushbackInputStream]))

(defn connect
  "Connect to nREPL server and return connection map"
  [host port]
  (let [socket (Socket. host port)
        out (.getOutputStream socket)
        in (PushbackInputStream. (.getInputStream socket))]
    {:socket socket
     :out out
     :in in
     :host host
     :port port}))

(defn close-connection
  "Close nREPL connection"
  [{:keys [socket]}]
  (.close socket))

(defn generate-id
  "Generate unique message ID"
  []
  (str (java.util.UUID/randomUUID)))

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
  "Collect multiple nREPL response messages until 'done' status"
  [in message-id]
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

(defn- merge-responses
  "Merge multiple nREPL responses into a single response, concatenating output fields"
  [responses]
  (let [;; Concatenate all output fields
        all-out (apply str (keep :out responses))
        all-err (apply str (keep :err responses))
        ;; Take the last non-nil value for these fields
        final-value (last (keep :value responses))
        final-ex (last (keep :ex responses))
        final-ns (last (keep :ns responses))
        final-session (last (keep :session responses))
        final-status (:status (last responses))
        
        ;; Build the merged response
        merged (cond-> {}
                 (not-empty all-out) (assoc :out all-out)
                 (not-empty all-err) (assoc :err all-err)
                 final-value (assoc :value final-value)
                 final-ex (assoc :ex final-ex)
                 final-ns (assoc :ns final-ns)
                 final-session (assoc :session final-session)
                 final-status (assoc :status final-status))]
    merged))

(defn send-message
  "Send nREPL message using bencode and collect all response messages"
  [{:keys [out in]} message]
  (let [msg-with-id (assoc message :id (generate-id))]
    ;; Log outgoing message
    (binding [*out* *err*]
      (println "[nREPL] 📤 Sending:" (pr-str msg-with-id)))
    
    ;; Send bencode-encoded message
    (bencode/write-bencode out msg-with-id)
    (.flush out)
    
    ;; Collect all response messages until "done"
    (let [responses (collect-responses in (:id msg-with-id))
          merged-response (merge-responses responses)]
      (binding [*out* *err*]
        (println "[nREPL] 📥 Final merged response:" merged-response))
      merged-response)))

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