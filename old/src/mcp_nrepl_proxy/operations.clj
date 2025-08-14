(ns mcp-nrepl-proxy.operations
  "nREPL operation functions - pure nREPL protocol implementations")

(defn eval-code
  "Evaluate code in nREPL session"
  [conn code send-message-fn & {:keys [session ns]}]
  (let [message (cond-> {:op "eval" :code code}
                  session (assoc :session session)
                  ns (assoc :ns ns))]
    (send-message-fn conn message)))

(defn create-session
  "Create new nREPL session"
  [conn send-message-fn]
  (send-message-fn conn {:op "clone"}))

(defn close-session
  "Close nREPL session"
  [conn session-id send-message-fn]
  (send-message-fn conn {:op "close" :session session-id}))

(defn describe-server
  "Get server description/capabilities"
  [conn send-message-fn]
  (send-message-fn conn {:op "describe"}))

(defn load-file
  "Load a file into the nREPL session"
  [conn file-path send-message-fn & {:keys [session ns]}]
  (let [file-content (slurp file-path)
        msg (cond-> {:op "load-file"
                     :file file-content
                     :file-path file-path
                     :file-name (.getName (java.io.File. file-path))}
              session (assoc :session session)
              ns (assoc :ns ns))]
    (send-message-fn conn msg)))

(defn doc
  "Get documentation for a symbol"
  [conn symbol send-message-fn & {:keys [session ns]}]
  (let [msg (cond-> {:op "info" :symbol symbol}
              session (assoc :session session)
              ns (assoc :ns ns))]
    (send-message-fn conn msg)))

(defn source
  "Get source code for a symbol"
  [conn symbol send-message-fn & {:keys [session ns]}]
  (let [msg (cond-> {:op "info" :symbol symbol}
              session (assoc :session session)
              ns (assoc :ns ns))]
    (send-message-fn conn msg)))

(defn complete
  "Get completions for a symbol prefix"
  [conn prefix send-message-fn & {:keys [session ns context]}]
  (let [msg (cond-> {:op "completions" :prefix prefix}
              session (assoc :session session)
              ns (assoc :ns ns)
              context (assoc :context context))]
    (send-message-fn conn msg)))

(defn apropos
  "Find symbols matching query"
  [conn query send-message-fn & {:keys [session ns search-ns privates? case-sensitive?]}]
  (let [msg (cond-> {:op "apropos" :query query}
              session (assoc :session session)
              ns (assoc :ns ns)
              search-ns (assoc :search-ns search-ns)
              (some? privates?) (assoc :privates? privates?)
              (some? case-sensitive?) (assoc :case-sensitive? case-sensitive?))]
    (send-message-fn conn msg)))

(defn require-ns
  "Require/load a namespace"
  [conn ns-symbol send-message-fn & {:keys [session as refer reload]}]
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
    (send-message-fn conn msg)))

(defn interrupt
  "Interrupt evaluation"
  [conn send-message-fn & {:keys [session interrupt-id]}]
  (let [msg (cond-> {:op "interrupt"}
              session (assoc :session session)
              interrupt-id (assoc :interrupt-id interrupt-id))]
    (send-message-fn conn msg)))

(defn stacktrace
  "Get stacktrace for the last exception"
  [conn send-message-fn & {:keys [session]}]
  (let [msg (cond-> {:op "stacktrace"}
              session (assoc :session session))]
    (send-message-fn conn msg)))