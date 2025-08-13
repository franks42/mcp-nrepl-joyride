(ns mcp-nrepl-proxy.nrepl-client
  "nREPL client implementation for Joyride using bencode protocol.
   
   Joyride's nREPL server uses bencode encoding for messages, not plain text.
   This implementation properly encodes/decodes messages using bencode."
  (:require [bencode.core :as bencode]
            [mcp-nrepl-proxy.uuid-v7 :as uuid]
            [mcp-nrepl-proxy.nrepl-connection :as nrepl-conn]
            [mcp-nrepl-proxy.state :as state]
            [mcp-nrepl-proxy.messaging :as messaging]
            [mcp-nrepl-proxy.operations :as ops]
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

;; nREPL operation functions - wrappers that inject send-message function
(defn eval-code
  "Evaluate code in nREPL session"
  [conn code & {:keys [session ns]}]
  (ops/eval-code conn code send-message :session session :ns ns))

(defn create-session
  "Create new nREPL session"
  [conn]
  (ops/create-session conn send-message))

(defn close-session
  "Close nREPL session"
  [conn session-id]
  (ops/close-session conn session-id send-message))

(defn describe-server
  "Get server description/capabilities"
  [conn]
  (ops/describe-server conn send-message))

(defn load-file
  "Load a file into the nREPL session"
  [conn file-path & {:keys [session ns]}]
  (ops/load-file conn file-path send-message :session session :ns ns))

(defn doc
  "Get documentation for a symbol"
  [conn symbol & {:keys [session ns]}]
  (ops/doc conn symbol send-message :session session :ns ns))

(defn source
  "Get source code for a symbol"
  [conn symbol & {:keys [session ns]}]
  (ops/source conn symbol send-message :session session :ns ns))

(defn complete
  "Get completions for a symbol prefix"
  [conn prefix & {:keys [session ns context]}]
  (ops/complete conn prefix send-message :session session :ns ns :context context))

(defn apropos
  "Find symbols matching query"
  [conn query & {:keys [session ns search-ns privates? case-sensitive?]}]
  (ops/apropos conn query send-message :session session :ns ns :search-ns search-ns :privates? privates? :case-sensitive? case-sensitive?))

(defn require-ns
  "Require/load a namespace"
  [conn ns-symbol & {:keys [session as refer reload]}]
  (ops/require-ns conn ns-symbol send-message :session session :as as :refer refer :reload reload))

(defn interrupt
  "Interrupt evaluation"
  [conn & {:keys [session interrupt-id]}]
  (ops/interrupt conn send-message :session session :interrupt-id interrupt-id))

(defn stacktrace
  "Get stacktrace for the last exception"
  [conn & {:keys [session]}]
  (ops/stacktrace conn send-message :session session))

;; === ASYNC QUEUE MANAGEMENT ===

(defn queue-message-async
  "Queue an nREPL message for async processing - wrapper for state namespace function"
  [connection message & {:keys [timeout-ms] :or {timeout-ms 30000}}]
  (state/queue-message-async connection message send-message-async :timeout-ms timeout-ms))