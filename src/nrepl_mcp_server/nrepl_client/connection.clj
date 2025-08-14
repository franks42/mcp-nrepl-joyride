(ns nrepl-mcp-server.nrepl-client.connection
  "nREPL client TCP connection management"
  (:require [nrepl-mcp-server.state.connection :as state]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [java.net Socket InetSocketAddress]))

;; =============================================================================
;; Connection Parameter Resolution
;; =============================================================================

(defn parse-host-port
  "Parse host:port or just port string"
  [s]
  (let [s (str/trim s)]
    (cond
      ;; host:port format
      (str/includes? s ":")
      (let [[host port-str] (str/split s #":" 2)]
        (try
          {:hostname host
           :port (Integer/parseInt port-str)}
          (catch NumberFormatException _
            {:error (str "Invalid port number: " port-str)})))

      ;; Just port number
      (re-matches #"\d+" s)
      (try
        {:hostname "localhost"
         :port (Integer/parseInt s)}
        (catch NumberFormatException _
          {:error (str "Invalid port number: " s)}))

      ;; Unknown format
      :else
      {:error (str "Invalid connection format: " s
                   " (expected host:port or port)")})))

(defn read-connection-file
  "Read connection info from file"
  [file-path]
  (try
    (let [content (str/trim (slurp file-path))]
      (parse-host-port content))
    (catch Exception e
      {:error (str "Cannot read file: " file-path
                   " - " (.getMessage e))})))

(defn resolve-connection-params
  "Resolve connection parameters from various sources"
  [connection-arg]
  (cond
    ;; Explicit connection string provided
    (and connection-arg (not (str/blank? connection-arg)))
    (if (.exists (io/file connection-arg))
      ;; It's a file path
      (read-connection-file connection-arg)
      ;; It's a host:port or port string
      (parse-host-port connection-arg))

    ;; No argument - check environment variable
    :else
    (if-let [env-value (System/getenv "NREPL_CONNECT")]
      (if (.exists (io/file env-value))
        (read-connection-file env-value)
        (parse-host-port env-value))
      {:error "No connection info provided. Use connection parameter or set NREPL_CONNECT environment variable"})))

;; =============================================================================
;; Connection Operations
;; =============================================================================

(defn attempt-connection!
  "Attempt to connect to nREPL server using unified state management"
  [{:keys [hostname port]}]
  (try
    (let [socket (Socket.)]
      (.connect socket (InetSocketAddress. hostname port) 5000)
      ;; Register connection in unified state management
      (let [conn-id (state/register-connection! hostname port socket)]
        (binding [*out* *err*]
          (println "[Connection] Successfully connected with ID:" conn-id))
        {:status :success :hostname hostname :port port :connection-id conn-id}))
    (catch Exception e
      (let [error-msg (str "Connection failed to " hostname ":" port
                           " - " (.getMessage e))]
        (binding [*out* *err*]
          (println "[Connection] Connection failed:" error-msg))
        {:status :failed :error error-msg}))))

(defn close-connection!
  "Close the active connection using unified state management"
  []
  (if-let [active-conn (state/get-active-connection)]
    (let [{:keys [socket connection-id]} active-conn]
      (when socket
        (try
          (.close socket)
          (binding [*out* *err*]
            (println "[Connection] Closed socket for connection:" connection-id))
          (catch Exception e
            (binding [*out* *err*]
              (println "[Connection] Error closing socket:" (.getMessage e))))))
      ;; Mark connection as closed in unified state
      (state/mark-connection-closed! connection-id :user-disconnect "User requested disconnect")
      {:status :success :connection-id connection-id})
    (do
      (binding [*out* *err*]
        (println "[Connection] No active connection to close"))
      {:status :success :message "No active connection"})))

;; =============================================================================
;; Utility Functions
;; =============================================================================

(defn wait-for-state-change
  "Wait for connection state to change from initial-status"
  [initial-status timeout-ms]
  (let [start-time (System/currentTimeMillis)
        end-time (+ start-time timeout-ms)]
    (loop []
      (let [current-status (state/get-connection-status)]
        (cond
          ;; State changed
          (not= current-status initial-status)
          current-status

          ;; Timeout
          (> (System/currentTimeMillis) end-time)
          :timeout

          ;; Keep waiting
          :else
          (do
            (Thread/sleep 50)
            (recur)))))))