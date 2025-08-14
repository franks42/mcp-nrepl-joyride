(ns nrepl-mcp-server.nrepl-client.operations
  "nREPL operation functions - pure nREPL protocol implementations integrated with reactive state"
  (:require [nrepl-mcp-server.nrepl-client.messaging :as msg]
            [nrepl-mcp-server.nrepl-client.socket-connection :as conn]))

(defn eval-code
  "Evaluate code in nREPL session"
  [conn code & {:keys [session ns timeout-ms] :or {timeout-ms 5000}}]
  (let [message (cond-> {:op "eval" :code code}
                  session (assoc :session session)
                  ns (assoc :ns ns))]
    (msg/send-message-async conn message timeout-ms)))

(defn create-session
  "Create new nREPL session"
  [conn & {:keys [timeout-ms] :or {timeout-ms 5000}}]
  (msg/send-message-async conn {:op "clone"} timeout-ms))

(defn close-session
  "Close nREPL session"
  [conn session-id & {:keys [timeout-ms] :or {timeout-ms 5000}}]
  (msg/send-message-async conn {:op "close" :session session-id} timeout-ms))

(defn describe-server
  "Get server description/capabilities"
  [conn & {:keys [timeout-ms] :or {timeout-ms 5000}}]
  (msg/send-message-async conn {:op "describe"} timeout-ms))

(defn load-file
  "Load a file into the nREPL session"
  [conn file-path & {:keys [session ns timeout-ms] :or {timeout-ms 10000}}]
  (let [file-content (slurp file-path)
        msg (cond-> {:op "load-file"
                     :file file-content
                     :file-path file-path
                     :file-name (.getName (java.io.File. file-path))}
              session (assoc :session session)
              ns (assoc :ns ns))]
    (msg/send-message-async conn msg timeout-ms)))

(defn doc
  "Get documentation for a symbol"
  [conn symbol & {:keys [session ns timeout-ms] :or {timeout-ms 5000}}]
  (let [msg (cond-> {:op "info" :symbol symbol}
              session (assoc :session session)
              ns (assoc :ns ns))]
    (msg/send-message-async conn msg timeout-ms)))

(defn source
  "Get source code for a symbol"
  [conn symbol & {:keys [session ns timeout-ms] :or {timeout-ms 5000}}]
  (let [msg (cond-> {:op "info" :symbol symbol}
              session (assoc :session session)
              ns (assoc :ns ns))]
    (msg/send-message-async conn msg timeout-ms)))

(defn complete
  "Get completions for a symbol prefix"
  [conn prefix & {:keys [session ns context timeout-ms] :or {timeout-ms 5000}}]
  (let [msg (cond-> {:op "completions" :prefix prefix}
              session (assoc :session session)
              ns (assoc :ns ns)
              context (assoc :context context))]
    (msg/send-message-async conn msg timeout-ms)))

(defn apropos
  "Find symbols matching query"
  [conn query & {:keys [session ns search-ns privates? case-sensitive? timeout-ms] :or {timeout-ms 5000}}]
  (let [msg (cond-> {:op "apropos" :query query}
              session (assoc :session session)
              ns (assoc :ns ns)
              search-ns (assoc :search-ns search-ns)
              (some? privates?) (assoc :privates? privates?)
              (some? case-sensitive?) (assoc :case-sensitive? case-sensitive?))]
    (msg/send-message-async conn msg timeout-ms)))

(defn require-ns
  "Require/load a namespace"
  [conn ns-symbol & {:keys [session as refer reload timeout-ms] :or {timeout-ms 5000}}]
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
    (msg/send-message-async conn msg timeout-ms)))

(defn interrupt
  "Interrupt evaluation"
  [conn & {:keys [session interrupt-id timeout-ms] :or {timeout-ms 5000}}]
  (let [msg (cond-> {:op "interrupt"}
              session (assoc :session session)
              interrupt-id (assoc :interrupt-id interrupt-id))]
    (msg/send-message-async conn msg timeout-ms)))

(defn stacktrace
  "Get stacktrace for the last exception"
  [conn & {:keys [session timeout-ms] :or {timeout-ms 5000}}]
  (let [msg (cond-> {:op "stacktrace"}
              session (assoc :session session))]
    (msg/send-message-async conn msg timeout-ms)))

;; High-level convenience functions

(defn connect-to-nrepl
  "Connect to nREPL server and return connection map"
  [host port]
  (conn/connect host port))

(defn disconnect-from-nrepl
  "Disconnect from nREPL server with proper cleanup"
  [connection]
  (conn/close-connection connection
                         (fn [conn-id error-type error-msg]
      ;; Simplified cleanup (logging for now)
                           (binding [*out* *err*]
                             (println "[Cleanup] Connection" conn-id "closed due to" error-type ":" error-msg))
                           0))) ;; Return 0 as failed count

(defn eval-with-timeout
  "Convenience function to evaluate code with reasonable defaults"
  [conn code & {:keys [timeout-ms] :or {timeout-ms 10000}}]
  (eval-code conn code :timeout-ms timeout-ms))

(defn health-check
  "Perform basic health check on nREPL connection"
  [conn & {:keys [timeout-ms] :or {timeout-ms 3000}}]
  (eval-code conn "(+ 1 2 3)" :timeout-ms timeout-ms))