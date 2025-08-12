(ns mcp-nrepl-proxy.nrepl-client
  "nREPL client implementation for Joyride using bencode protocol.
   
   Joyride's nREPL server uses bencode encoding for messages, not plain text.
   This implementation properly encodes/decodes messages using bencode."
  (:require [bencode.core :as bencode]
            [mcp-nrepl-proxy.uuid-v7 :as uuid]
            [mcp-nrepl-proxy.nrepl-connection :as nrepl-conn]
            [mcp-nrepl-proxy.state :as state]
            [mcp-nrepl-proxy.messaging :as messaging]
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

;; Messaging functions delegated to messaging namespace
(def generate-id messaging/generate-id)

;; Helper functions to update connection activity and provide delegation points
(defn- update-connection-activity
  "Update connection activity timestamp"
  [conn-id]
  (swap! connection-state update-in [:connections conn-id]
         assoc :last-activity (System/currentTimeMillis)))

(defn send-message
  "Send nREPL message using bencode and collect all response messages - delegated to messaging namespace"
  [{:keys [out in id] :as conn} message & {:keys [timeout-ms]}]
  (messaging/send-message conn message :timeout-ms timeout-ms :update-activity-fn update-connection-activity))

(defn send-message-async
  "Async version of send-message using promise-based timeout handling - delegated to messaging namespace"
  [{:keys [out in id] :as conn} message timeout-ms]
  (messaging/send-message-async conn message timeout-ms
                                :update-activity-fn update-connection-activity
                                :track-pending-message-fn track-pending-message
                                :update-message-status-fn update-message-status))

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