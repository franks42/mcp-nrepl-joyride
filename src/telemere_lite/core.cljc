(ns telemere-lite.core
  "Unified telemetry for BB and browser with lazy evaluation.

  Key features:
  - Lazy :let evaluation (3-14x faster when disabled)
  - Single CLJC file for both BB and browser
  - Three-sink browser architecture (console, atom, websocket)
  - BB handler infrastructure with async support
  - Compatible with existing Telemere-lite API"
  #?(:bb  (:require [clojure.string :as str]
                    [clojure.tools.logging :as log]
                    [taoensso.timbre :as timbre]
                    [cheshire.core :as json]
                    [clojure.java.io :as io])))

;;; ============================================================================
;;; Dynamic configuration vars
;;; ============================================================================

(def ^:dynamic *telemetry-enabled* false)

#?(:bb
   (do
     (def ^:dynamic *log-level* :info)
     (def ^:dynamic *output-file* "logs/telemetry.jsonl")

     ;; Namespace and event-id filtering with pre-compiled regexes
     (def ^:dynamic *ns-filter* {:allow-re [(re-pattern ".*")] :deny-re []})
     (def ^:dynamic *event-id-filter* {:allow-re [(re-pattern ".*")] :deny-re []})

     ;; Handler registry
     (def ^:dynamic *handlers* (atom {}))

     ;; Shutdown hook tracking
     (defonce shutdown-hook-installed? (atom false))

     ;; Error handler
     (defonce error-handler
       (atom (fn [error context]
               "Default error handler: prints full stack trace to stderr"
               (binding [*out* *err*]
                 (println "Telemetry error:" context)
                 (.printStackTrace error *err*)))))))

#?(:cljs
   (do
     ;; THREE-SINK BROWSER ARCHITECTURE

     ;; Sink 1: Console (development/debugging) - enabled by default
     (defonce console-enabled (atom true))

     ;; Sink 2: Atom (testing/programmatic access) - disabled by default
     (defonce events (atom []))
     (defonce atom-sink-enabled (atom false))

     ;; Sink 3: WebSocket to server (centralized telemetry) - disabled by default
     (defonce send-fn (atom nil))  ; Set by sente-lite client
     (defonce remote-sink-enabled (atom false))))

;;; ============================================================================
;;; Platform-specific helpers
;;; ============================================================================

(defn now
  "Current timestamp (platform-specific)"
  []
  #?(:bb  (.toString (java.time.Instant/now))
     :cljs (.toISOString (js/Date.))))

#?(:cljs
   (defn signal->json
     "Convert signal to JSON string for browser output"
     [signal]
     (js/JSON.stringify (clj->js signal))))

#?(:cljs
   (defn ->json
     "Convert data to JSON string (browser only, for backward compat)"
     [data]
     (js/JSON.stringify (clj->js data))))

#?(:bb
   (defn ->json
     "Convert data to JSON string (BB uses cheshire)"
     [data]
     (json/generate-string data)))

;;; ============================================================================
;;; BB-specific: Serialization and filtering
;;; ============================================================================

#?(:bb
   (defn- serialize-for-json
     "Convert objects to JSON-serializable form"
     [obj]
     (cond
       ;; Handle nil
       (nil? obj) nil

       ;; Handle JSON primitives (pass through)
       (or (string? obj) (number? obj) (boolean? obj)) obj

       ;; Handle exceptions
       (instance? Throwable obj)
       {:type (str (type obj))
        :message (str obj)
        :class (str (class obj))}

       ;; Handle Class objects (convert to string)
       (instance? Class obj)
       (str obj)

       ;; Handle functions (convert to string representation)
       (fn? obj) (str obj)

       ;; Handle atoms/refs/agents (get value but mark it)
       (instance? clojure.lang.IDeref obj)
       {:deref-type (str (type obj))
        :deref-value (serialize-for-json @obj)}

       ;; Handle keywords
       (keyword? obj) (str obj)

       ;; Handle symbols
       (symbol? obj) (str obj)

       ;; Handle maps (recursively serialize)
       (map? obj) (into {} (map (fn [[k v]] [k (serialize-for-json v)]) obj))

       ;; Handle sequential collections (recursively serialize)
       (sequential? obj) (mapv serialize-for-json obj)

       ;; Handle sets (convert to vector)
       (set? obj) (mapv serialize-for-json (vec obj))

       ;; Fallback: convert to string
       :else (str obj))))

#?(:bb
   (defn- wildcard->regex
     "Convert wildcard pattern to compiled regex (e.g., 'foo.*' => #\"foo\\..*\")"
     [pattern]
     (re-pattern (str/replace (str pattern) "*" ".*"))))

#?(:bb
   (defn- ns-allowed?
     "Check if namespace is allowed by current filter (using pre-compiled regexes)"
     [ns-str]
     (let [{:keys [allow-re deny-re]} *ns-filter*
           denied? (some #(re-matches % ns-str) deny-re)
           allowed? (some #(re-matches % ns-str) allow-re)]
       (and (not denied?) allowed?))))

#?(:bb
   (defn- event-id-allowed?
     "Check if event-id is allowed by current filter (using pre-compiled regexes)"
     [event-id]
     (if (nil? event-id)
       true  ; Allow signals without event-id
       (let [{:keys [allow-re deny-re]} *event-id-filter*
             event-id-str (str event-id)
             denied? (some #(re-matches % event-id-str) deny-re)
             allowed? (some #(re-matches % event-id-str) allow-re)]
         (and (not denied?) allowed?)))))

;;; ============================================================================
;;; Signal dispatch (platform-specific)
;;; ============================================================================

#?(:bb
   (defn- dispatch-signal!
     "Dispatch signal to BB handlers"
     [signal]
     (let [custom-handlers @*handlers*]
       ;; Send to configured handlers (async-capable)
       (doseq [[handler-id handler-info] custom-handlers]
         (when (get handler-info :enabled?)
           (try
             ((:dispatch-fn (:handler handler-info)) signal)
             (catch Exception e
               (binding [*out* *err*]
                 (println "Handler" handler-id "failed:" (.getMessage e)))))))
       ;; Only send to Timbre if NO custom handlers configured (backwards compatibility)
       (when (empty? custom-handlers)
         (let [level (:level signal :info)
               msg-vec (:msg signal ["" {}])
               ;; Extract message string and context from msg vector [message context]
               message (first msg-vec)
               msg-context (second msg-vec)]
           (case level
             :debug (timbre/debug message msg-context)
             :info  (timbre/info message msg-context)
             :warn  (timbre/warn message msg-context)
             :error (timbre/error message msg-context)
             (timbre/info message msg-context)))))))

#?(:cljs
   (defn dispatch-signal!
     "Dispatch signal to browser sinks (THREE-SINK ARCHITECTURE)"
     [signal]
     ;; Sink 1: Console (development/debugging)
     (when @console-enabled
       (let [level (get signal :level :info)
             msg (get signal :msg "")]
         (js/console.log (str "[" level "] " msg " " (signal->json signal)))))

     ;; Sink 2: Atom (testing/programmatic access)
     (when @atom-sink-enabled
       (swap! events conj signal))

     ;; Sink 3: WebSocket (centralized telemetry to server)
     (when (and @remote-sink-enabled @send-fn)
       (@send-fn [:telemetry/event (assoc signal :source :browser)]))))

;;; ============================================================================
;;; CORE LAZY SIGNAL! MACRO - The Key Innovation
;;; ============================================================================

#_{:clj-kondo/ignore [:unresolved-symbol]}
(defmacro signal!
  "Core signal macro with LAZY :let evaluation (Telemere pattern).
   NOW TROVE-COMPATIBLE: Accepts both :id (Trove) and :event-id (ours).

  Three-stage lazy evaluation:
  1. Filter FIRST (check *telemetry-enabled*, ns-filter, event-id-filter)
  2. Wrap in delay (all expensive work deferred)
  3. Evaluate :let bindings INSIDE delay (only if filter passes)

  Performance when disabled: ~60-120ns (just filtering check!)
  Performance when enabled: ~180-350ns (same as before)

  Usage:
    ;; Our convention:
    (signal! {:level :info
              :event-id ::my-event
              :let [x (expensive-fn)]  ; ← Only evaluated if signal passes filtering!
              :data {:result x}})

    ;; Trove convention:
    (signal! {:level :debug
              :id :namespace/event-name
              :data {:result x}})"
  [opts]
  (let [;; Extract compile-time values
        level     (get opts :level :info)
        ;; TROVE COMPATIBILITY: Accept both :id (Trove) and :event-id (ours)
        event-id  (or (get opts :id) (get opts :event-id))
        ns-str    (str *ns*)
        file      (str *file*)
        line      (:line (meta &form))

        ;; Extract forms that need lazy evaluation
        let-bindings (get opts :let [])
        data-form    (get opts :data)
        msg-form     (get opts :msg "Event")

        ;; Build filtering check (OUTSIDE delay - always runs, but cheap!)
        should-signal?
        `(and *telemetry-enabled*
              #?(:bb (and (ns-allowed? ~ns-str)
                          (event-id-allowed? ~event-id))
                 :cljs true))]  ; Browser: simpler filtering (for now)

    ;; CRITICAL: Check filter BEFORE evaluating :let/:data/:msg
    `(when ~should-signal?
       ;; Wrap everything in delay for lazy evaluation
       (let [signal-delay#
             (delay
               ;; INSIDE DELAY: Evaluate :let bindings FIRST
               (let [~@let-bindings

                     ;; Build context map with location metadata and data
                     context#
                     (cond-> {:location {:file ~file
                                         :line ~line
                                         :ns   ~ns-str}}
                       ~data-form (assoc :data ~data-form))

                     ;; Build signal map with msg as [message context] vector
                     signal#
                     {:timestamp (now)
                      :level     ~level
                      :ns        ~ns-str
                      :msg       [~msg-form context#]}]  ; ← msg is now [message, context]!

                 ;; Add event-id if present
                 (cond-> signal#
                   ~event-id (assoc :event-id ~event-id))))]

         ;; Force delay and dispatch to handlers
         (dispatch-signal! @signal-delay#)))))

;;; ============================================================================
;;; High-level logging macros (with lazy evaluation support)
;;; ============================================================================

(defmacro event!
  "Event logging macro with lazy evaluation support.

  Three arities:
  1. Event ID only: (event! ::user-login)
  2. Event ID + data (OLD STYLE - eager): (event! ::login {:user-id 123})
  3. Event ID + :let + data (NEW STYLE - lazy): (event! ::login [id user-id] {:id id})

  The 3-arg form uses lazy evaluation - expensive computations in :let bindings
  are only evaluated if telemetry is enabled."
  ([event-id]
   ;; Event ID only: (event! ::user-login)
   `(signal! {:level :info
              :event-id ~event-id
              :msg "Event"}))

  ([event-id data-or-let-bindings]
   ;; Detect if second arg is :let bindings (vector) or data map
   (if (vector? data-or-let-bindings)
     ;; ERROR: 2-arg with vector is ambiguous - require 3-arg form
     (throw (ex-info "event! with :let bindings requires 3-arg form: (event! event-id [bindings] data-map)"
                     {:event-id event-id :let-bindings data-or-let-bindings}))
     ;; OLD STYLE (backward compatible, but eager)
     `(signal! {:level :info
                :event-id ~event-id
                :msg "Event"
                :data ~data-or-let-bindings})))

  ([event-id let-bindings data-map]
   ;; NEW STYLE (lazy evaluation via :let)
   `(signal! {:level :info
              :event-id ~event-id
              :msg "Event"
              :let ~let-bindings
              :data ~data-map})))

(defmacro log!
  "Logging macro built on signal! (matching official Telemere API).
   NOW TROVE-COMPATIBLE: Accepts {:level :id :data} format.

  Supports lazy evaluation via :let bindings in opts map.

  Usage:
    ;; Trove-style (recommended going forward):
    (log! {:level :debug :id :sente-lite.server/connection-added :data {:conn-id id}})

    ;; Our legacy style (still supported):
    (log! {:level :info :event-id ::my-event :msg \"Message\" :data {...}})
    (log! :info \"Message\")
    (log! :info \"Message\" {:key \"value\"})"
  ([opts-or-level]
   `(if (map? ~opts-or-level)
      ;; Full options map: (log! {:level :info :id :my/event :data {...}})
      (signal! ~opts-or-level)
      ;; Level only: (log! :info)
      (signal! {:level ~opts-or-level})))
  ([level msg]
   ;; Level + message: (log! :info "Message")
   `(signal! {:level ~level :msg ~msg}))
  ([level msg data]
   ;; Level + message + data: (log! :info "Message" {:key "value"})
   `(signal! {:level ~level :msg ~msg :data ~data})))

(defmacro debug!
  "Debug logging macro"
  ([msg]
   `(signal! {:level :debug :msg ~msg}))
  ([msg data]
   `(signal! {:level :debug :msg ~msg :data ~data})))

(defmacro info!
  "Info logging macro"
  ([msg]
   `(signal! {:level :info :msg ~msg}))
  ([msg data]
   `(signal! {:level :info :msg ~msg :data ~data})))

(defmacro warn!
  "Warn logging macro"
  ([msg]
   `(signal! {:level :warn :msg ~msg}))
  ([msg data]
   `(signal! {:level :warn :msg ~msg :data ~data})))

(defmacro error!
  "Error logging macro"
  ([error-or-opts]
   `(if (map? ~error-or-opts)
      ;; Full options map: (error! {:msg "Error" :error ex})
      (signal! (assoc ~error-or-opts :level :error))
      ;; Exception/Throwable: (error! (Exception. "Something went wrong"))
      (signal! {:level :error
                :msg (str ~error-or-opts)
                :error ~error-or-opts})))
  ([msg error-or-data]
   ;; Message + error/data: (error! "Failed to connect" ex)
   `(signal! {:level :error
              :msg ~msg
              :error ~error-or-data})))

;;; ============================================================================
;;; Browser-specific: THREE-SINK control functions
;;; ============================================================================

#?(:cljs
   (do
     (defn enable-console-sink!
       "Enable console sink (development/debugging)"
       []
       (reset! console-enabled true)
       ;; Meta-telemetry: Log sink change
       (signal! {:level :info
                 :event-id ::console-sink-enabled
                 :msg "Console sink enabled"}))

     (defn disable-console-sink!
       "Disable console sink"
       []
       (reset! console-enabled false)
       ;; Meta-telemetry: Log sink change
       (signal! {:level :info
                 :event-id ::console-sink-disabled
                 :msg "Console sink disabled"}))

     (defn enable-atom-sink!
       "Enable atom sink (testing/programmatic access)"
       []
       (reset! atom-sink-enabled true)
       ;; Meta-telemetry: Log sink change
       (signal! {:level :info
                 :event-id ::atom-sink-enabled
                 :msg "Atom sink enabled"}))

     (defn disable-atom-sink!
       "Disable atom sink"
       []
       (reset! atom-sink-enabled false)
       ;; Meta-telemetry: Log sink change
       (signal! {:level :info
                 :event-id ::atom-sink-disabled
                 :msg "Atom sink disabled"}))

     (defn get-events
       "Get all collected events from atom sink"
       []
       @events)

     (defn clear-events!
       "Clear all collected events from atom sink"
       []
       (let [count (count @events)]
         (reset! events [])
         ;; Meta-telemetry: Log events cleared
         (signal! {:level :info
                   :event-id ::atom-events-cleared
                   :msg "Atom sink events cleared"
                   :data {:count count}})))

     (defn enable-remote-sink!
       "Enable remote sink (centralized telemetry to server).
       send-fn: function that sends events to server via WebSocket"
       [send-fn-arg]
       (reset! send-fn send-fn-arg)
       (reset! remote-sink-enabled true)
       ;; Meta-telemetry: Log sink change
       (signal! {:level :info
                 :event-id ::remote-sink-enabled
                 :msg "Remote sink enabled"}))

     (defn disable-remote-sink!
       "Disable remote sink"
       []
       (reset! remote-sink-enabled false)
       ;; Meta-telemetry: Log sink change
       (signal! {:level :info
                 :event-id ::remote-sink-disabled
                 :msg "Remote sink disabled"}))))

;;; ============================================================================
;;; BB-specific: Handler infrastructure (copied from old core.cljc)
;;; ============================================================================

#?(:bb
   (do
     (defn configure-timbre!
       "Configure Timbre for structured JSON logging in BB"
       []
       (timbre/merge-config!
        {:level :debug
         :appenders {:file {:enabled? true
                            :fn (fn [data]
                                  (let [output-str (json/generate-string
                                                    {:timestamp (:instant data)
                                                     :level (:level data)
                                                     :ns (:?ns-str data)
                                                     :msg (:vargs data)
                                                     :context (:context data)})]
                                    (spit *output-file* (str output-str "\n") :append true)))}}}))

     (defn ensure-log-dir!
       "Ensure logs directory exists"
       []
       (let [log-dir (io/file "logs")]
         (when-not (.exists log-dir)
           (.mkdirs log-dir))))

     (defn set-error-handler!
       "Set custom error handler for telemetry errors"
       [handler-fn]
       (reset! error-handler handler-fn))

     (defn- handle-telemetry-error!
       "Handle telemetry error using configured error handler"
       [error context]
       (try
         (@error-handler error context)
         (catch Exception e
           ;; Fallback if error handler itself fails
           (binding [*out* *err*]
             (println "Error handler failed:" (.getMessage e))
             (.printStackTrace error *err*)))))

     (defn set-enabled!
       "Enable/disable all telemetry (cross-platform)"
       [enabled?]
       (let [old-value *telemetry-enabled*]
         (alter-var-root #'*telemetry-enabled* (constantly enabled?))
         ;; Meta-telemetry: Log configuration change (after change so it's captured if enabling)
         (when enabled?
           (signal! {:level :info
                     :event-id ::telemetry-enabled-changed
                     :msg "Telemetry configuration changed"
                     :data {:old old-value :new enabled?}}))))

     (defn get-enabled?
       "Check if telemetry is enabled (cross-platform)"
       []
       *telemetry-enabled*)

     (defn set-ns-filter!
       "Set namespace-based signal filtering with pre-compiled regexes"
       [{:keys [allow disallow]}]
       (let [allow-patterns (if (coll? allow) allow [allow])
             disallow-patterns (if (coll? disallow) disallow [disallow])
             allow-re (mapv wildcard->regex allow-patterns)
             deny-re (mapv wildcard->regex disallow-patterns)]
         (alter-var-root #'*ns-filter*
                         (constantly {:allow-re allow-re
                                      :deny-re deny-re}))
         ;; Meta-telemetry: Log filter change (exclude compiled regexes)
         (signal! {:level :info
                   :event-id ::ns-filter-changed
                   :msg "Namespace filter changed"
                   :data {:allow allow-patterns
                          :disallow disallow-patterns}})))

     (defn set-id-filter!
       "Set event-id-based signal filtering with pre-compiled regexes"
       [{:keys [allow disallow]}]
       (let [allow-patterns (if (coll? allow) allow [allow])
             disallow-patterns (if (coll? disallow) disallow [disallow])
             allow-re (mapv wildcard->regex allow-patterns)
             deny-re (mapv wildcard->regex disallow-patterns)]
         (alter-var-root #'*event-id-filter*
                         (constantly {:allow-re allow-re
                                      :deny-re deny-re}))
         ;; Meta-telemetry: Log filter change (exclude compiled regexes)
         (signal! {:level :info
                   :event-id ::event-id-filter-changed
                   :msg "Event ID filter changed"
                   :data {:allow allow-patterns
                          :disallow disallow-patterns}})))

     (defn get-filters
       "Get current filter configuration"
       []
       {:min-level (get timbre/*config* :min-level :debug)
        :ns-filter *ns-filter*
        :event-id-filter *event-id-filter*
        :ns-min-levels (get timbre/*config* :ns-min-level {})
        :enabled? *telemetry-enabled*})

     (defn clear-filters!
       "Clear all filters, reverting to defaults"
       []
       (timbre/set-min-level! :debug)
       (alter-var-root #'*ns-filter* (constantly {:allow #{"*"} :deny #{}}))
       (alter-var-root #'*event-id-filter* (constantly {:allow #{"*"} :deny #{}}))
       (alter-var-root #'*telemetry-enabled* (constantly true))
       ;; Meta-telemetry: Log filter clear
       (signal! {:level :info
                 :event-id ::filters-cleared
                 :msg "All filters cleared - telemetry enabled"}))))

;;; ============================================================================
;;; BB-specific: Async handler infrastructure
;;; ============================================================================

#?(:bb
   (do
     (defn- file-handler
       "Handler that writes to file in JSON Lines format"
       [file-path]
       (fn [signal]
         (try
           (let [output-str (json/generate-string signal)]
             (spit file-path (str output-str "\n") :append true))
           (catch Exception e
             (binding [*out* *err*]
               (println "Error writing to log file:" (.getMessage e)))))))

     (defn- stdout-handler
       "Handler that writes to stdout"
       []
       (fn [signal]
         (try
           (println (json/generate-string signal))
           (catch Exception e
             (binding [*out* *err*]
               (println "Error writing to stdout:" (.getMessage e)))))))

     (defn- stderr-handler
       "Handler that writes to stderr"
       []
       (fn [signal]
         (try
           (binding [*out* *err*]
             (println (json/generate-string signal)))
           (catch Exception e
             (binding [*out* *err*]
               (println "Error writing to stderr:" (.getMessage e)))))))

     (defn- async-handler-wrapper
       "Wrap any handler function with async dispatch"
       [handler-fn {:keys [mode buffer-size n-threads]
                    :or {mode :dropping buffer-size 1024 n-threads 1}}]
       (let [queue (java.util.concurrent.LinkedBlockingQueue. buffer-size)
             executor (java.util.concurrent.Executors/newFixedThreadPool n-threads)
             stats (atom {:queued 0 :processed 0 :dropped 0 :errors 0})
             running? (atom true)]

         ;; Background processing threads
         (dotimes [_ n-threads]
           (.submit executor
                    (fn []
                      (try
                        (while @running?
                          (when-let [signal (.poll queue 1000 java.util.concurrent.TimeUnit/MILLISECONDS)]
                            (try
                              (handler-fn signal)
                              (swap! stats update :processed inc)
                              (catch Exception e
                                (swap! stats update :errors inc)
                                (binding [*out* *err*]
                                  (println "Async handler error:" (.getMessage e)))))))
                        (catch InterruptedException _)
                        (catch Exception e
                          (binding [*out* *err*]
                            (println "Async handler thread error:" (.getMessage e))))))))

         ;; Return handler interface
         {:dispatch-fn
          (fn [signal]
            (when @running?
              (case mode
                :dropping
                (if (.offer queue signal)
                  (swap! stats update :queued inc)
                  (swap! stats update :dropped inc))

                :blocking
                (try
                  (.put queue signal)
                  (swap! stats update :queued inc)
                  (catch InterruptedException _
                    (swap! stats update :dropped inc)))

                :sliding
                (do
                  (when (= (.size queue) buffer-size)
                    (.poll queue)
                    (swap! stats update :dropped inc))
                  (.offer queue signal)
                  (swap! stats update :queued inc)))))

          :stats-fn #(assoc @stats :queue-size (.size queue) :mode mode)
          :shutdown-fn
          (fn []
            (reset! running? false)
            (.shutdown executor)
            (try
              (.awaitTermination executor 5 java.util.concurrent.TimeUnit/SECONDS)
              (catch InterruptedException _)))}))

     (declare ensure-shutdown-hook!)

     (defn add-handler!
       "Add a telemetry handler with optional async dispatch"
       ([handler-id handler-fn]
        (add-handler! handler-id handler-fn {}))
       ([handler-id handler-fn opts]
        (let [final-handler (if (:async opts)
                              (async-handler-wrapper handler-fn (:async opts))
                              {:dispatch-fn handler-fn
                               :stats-fn (fn [] {:mode :sync :processed 0 :queued 0 :dropped 0 :errors 0})
                               :shutdown-fn (fn [] nil)})]
          (when (:async opts)
            (ensure-shutdown-hook!))
          (swap! *handlers* assoc handler-id
                 {:handler final-handler
                  :opts opts
                  :enabled? true})
          ;; Meta-telemetry: Log handler addition
          (signal! {:level :info
                    :event-id ::handler-added
                    :msg "Telemetry handler added"
                    :data {:handler-id handler-id
                           :async (boolean (:async opts))
                           :opts opts}}))))

     (defn remove-handler!
       "Remove a telemetry handler with proper async cleanup"
       [handler-id]
       (when-let [handler-info (get @*handlers* handler-id)]
         (try
           ((:shutdown-fn (:handler handler-info)))
           (catch Exception e
             (binding [*out* *err*]
               (println "Error shutting down handler" handler-id ":" (.getMessage e)))))
         (swap! *handlers* dissoc handler-id)
         ;; Meta-telemetry: Log handler removal
         (signal! {:level :info
                   :event-id ::handler-removed
                   :msg "Telemetry handler removed"
                   :data {:handler-id handler-id}})))

     (defn get-handlers
       "Get current handlers"
       []
       @*handlers*)

     (defn clear-handlers!
       "Clear all handlers with proper async cleanup"
       []
       (let [handler-count (count @*handlers*)]
         (doseq [[handler-id handler-info] @*handlers*]
           (try
             ((:shutdown-fn (:handler handler-info)))
             (catch Exception e
               (binding [*out* *err*]
                 (println "Error shutting down handler" handler-id ":" (.getMessage e))))))
         (reset! *handlers* {})
         ;; Meta-telemetry: Log handlers cleared
         (signal! {:level :info
                   :event-id ::handlers-cleared
                   :msg "All telemetry handlers cleared"
                   :data {:count handler-count}})))

     (defn get-handler-stats
       "Get performance statistics for all handlers"
       []
       (into {}
             (map (fn [[handler-id handler-info]]
                    [handler-id ((:stats-fn (:handler handler-info)))])
                  @*handlers*)))

     (defn get-handler-health
       "Check handler health and queue status"
       []
       (let [stats (get-handler-stats)]
         {:healthy? (every? #(< (get % :errors 0) 10) (vals stats))
          :total-queued (reduce + 0 (map :queued (vals stats)))
          :total-dropped (reduce + 0 (map :dropped (vals stats)))
          :total-processed (reduce + 0 (map :processed (vals stats)))
          :handlers (count stats)
          :details stats}))

     (defn shutdown-telemetry!
       "Gracefully shutdown all async handlers"
       []
       (doseq [[handler-id handler-info] @*handlers*]
         (try
           ((:shutdown-fn (:handler handler-info)))
           (catch Exception e
             (handle-telemetry-error! e {:type :handler-shutdown
                                         :handler-id handler-id}))))
       (reset! *handlers* {}))

     (defn- ensure-shutdown-hook!
       "Add JVM shutdown hook to cleanup async handlers on exit"
       []
       (when (compare-and-set! shutdown-hook-installed? false true)
         (.addShutdownHook (Runtime/getRuntime)
                           (Thread. ^Runnable shutdown-telemetry!))))

     (defn add-file-handler!
       "Add file output handler with async support"
       ([file-path]
        (add-file-handler! :file file-path {}))
       ([handler-id file-path]
        (add-file-handler! handler-id file-path {}))
       ([handler-id file-path opts]
        (let [default-opts {:async {:mode :dropping :buffer-size 1024 :n-threads 1}}
              final-opts (if (contains? opts :sync)
                           (dissoc opts :sync)
                           (merge default-opts opts))]
          (add-handler! handler-id (file-handler file-path) final-opts))))

     (defn add-stdout-handler!
       "Add stdout output handler with async support"
       ([]
        (add-stdout-handler! :stdout {}))
       ([handler-id]
        (add-stdout-handler! handler-id {}))
       ([handler-id opts]
        (let [default-opts {:async {:mode :dropping :buffer-size 512 :n-threads 1}}
              final-opts (if (contains? opts :sync)
                           (dissoc opts :sync)
                           (merge default-opts opts))]
          (add-handler! handler-id (stdout-handler) final-opts))))

     (defn add-stderr-handler!
       "Add stderr output handler with async support"
       ([]
        (add-stderr-handler! :stderr {}))
       ([handler-id]
        (add-stderr-handler! handler-id {}))
       ([handler-id opts]
        (let [default-opts {:async {:mode :dropping :buffer-size 256 :n-threads 1}}
              final-opts (if (contains? opts :sync)
                           (dissoc opts :sync)
                           (merge default-opts opts))]
          (add-handler! handler-id (stderr-handler) final-opts))))))

;;; ============================================================================
;;; Startup and utility functions
;;; ============================================================================

(defn startup!
  "Log startup with system info"
  []
  #?(:bb
     (do
       (ensure-log-dir!)
       (configure-timbre!)
       (log! :info "BB startup initiated"
             {:bb-version (System/getProperty "babashka.version")
              :java-version (System/getProperty "java.version")
              :os-name (System/getProperty "os.name")
              :user-dir (System/getProperty "user.dir")}))
     :cljs
     (log! :info "Scittle startup initiated"
           {:user-agent (.-userAgent js/navigator)
            :location (.-href js/location)})))

(defn module-load!
  "Log module loading with timing"
  [module-name]
  (log! :info "Module loading" {:module module-name :timestamp (now)}))

(defn module-loaded!
  "Log module loaded successfully"
  [module-name duration-ms]
  (log! :info "Module loaded" {:module module-name :duration-ms duration-ms}))

(defmacro performance!
  "Performance logging macro"
  ([operation duration-ms]
   `(signal! {:level :info
              :msg "Performance metric"
              :operation ~operation
              :duration-ms ~duration-ms}))
  ([operation duration-ms context]
   `(signal! (merge {:level :info
                     :msg "Performance metric"
                     :operation ~operation
                     :duration-ms ~duration-ms}
                    ~context))))

(defmacro with-timing
  "Execute body and log timing"
  [operation & body]
  `(let [start# #?(:bb (System/currentTimeMillis)
                   :cljs (.getTime (js/Date.)))
         result# (do ~@body)
         duration# (- #?(:bb (System/currentTimeMillis)
                         :cljs (.getTime (js/Date.)))
                      start#)]
     (performance! ~operation duration#)
     result#))

;;; ============================================================================
;;; Cross-platform API functions
;;; ============================================================================

#?(:cljs
   (do
     (defn set-enabled!
       "Enable/disable all telemetry (browser)"
       [enabled?]
       (let [old-value *telemetry-enabled*]
         (set! *telemetry-enabled* enabled?)
         ;; Meta-telemetry: Log configuration change (after change so it's captured if enabling)
         (when enabled?
           (signal! {:level :info
                     :event-id ::telemetry-enabled-changed
                     :msg "Telemetry configuration changed"
                     :data {:old old-value :new enabled?}}))))

     (defn get-enabled?
       "Check if telemetry is enabled (browser)"
       []
       *telemetry-enabled*)))
