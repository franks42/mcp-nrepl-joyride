#!/usr/bin/env bb

(ns test-nrepl-server
  "Simple nREPL server for testing the MCP-nREPL proxy.
   
   Provides basic eval, session management, and nREPL protocol compliance
   without the complexity of a full Clojure environment."
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [babashka.fs :as fs])
  (:import [java.net ServerSocket Socket]
           [java.io PrintWriter BufferedReader InputStreamReader]))

(def server-state
  "Global server state: sessions, evaluations, etc."
  (atom {:sessions {}
         :default-session "default-session-123"
         :eval-counter 0
         :port nil}))

(defn log [& args]
  "Log to stderr"
  (binding [*out* *err*]
    (println "[nREPL-Server]" (apply str args))))

(defn generate-session-id []
  "Generate unique session ID"
  (str "session-" (java.util.UUID/randomUUID)))

(defn eval-in-session [session-id code]
  "Evaluate code in a session context"
  (swap! server-state update :eval-counter inc)
  (let [counter (:eval-counter @server-state)]
    (log "Evaluating in session" session-id ":" code)
    
    ;; Simple evaluation - in a real nREPL this would use Clojure's eval
    (try
      (let [result (cond
                     ;; Handle some basic expressions for testing
                     (= code "(+ 1 2 3)") "6"
                     (= code "(* 2 3 4)") "24"
                     (str/starts-with? code "(println") (do
                                                          (log "Print statement executed:" code)
                                                          "nil")
                     (str/starts-with? code "(def ") (do
                                                       (log "Def statement executed:" code)
                                                       "#'user/some-var")
                     (str/starts-with? code "(+ ") (str (+ 10 20)) ; Simple addition
                     (str/starts-with? code "(str ") (str "\"" code " result\"")
                     ;; Default case
                     :else (str "=> " code " [evaluated]"))]
        {:status ["done"]
         :value result
         :ns "user"
         :session session-id})
      (catch Exception e
        {:status ["done" "error"]
         :ex (str "class " (class e))
         :root-ex (str "class " (class e))
         :session session-id}))))

(defn handle-eval [message]
  "Handle nREPL eval operation"
  (let [code (:code message)
        session (or (:session message) (:default-session @server-state))
        ns-name (or (:ns message) "user")]
    
    ;; Ensure session exists
    (when-not (get-in @server-state [:sessions session])
      (swap! server-state assoc-in [:sessions session] {:ns ns-name :created (System/currentTimeMillis)}))
    
    (merge (eval-in-session session code)
           {:id (:id message)})))

(defn handle-clone [message]
  "Handle nREPL clone operation (create new session)"
  (let [new-session (generate-session-id)]
    (swap! server-state assoc-in [:sessions new-session] {:ns "user" :created (System/currentTimeMillis)})
    (log "Created new session:" new-session)
    {:status ["done"]
     :new-session new-session
     :id (:id message)}))

(defn handle-close [message]
  "Handle nREPL close operation (close session)"
  (let [session (:session message)]
    (swap! server-state update :sessions dissoc session)
    (log "Closed session:" session)
    {:status ["done"]
     :id (:id message)}))

(defn handle-describe [message]
  "Handle nREPL describe operation (server capabilities)"
  {:status ["done"]
   :ops {:eval {}
         :clone {}
         :close {}
         :describe {}}
   :versions {:nrepl {:major 1 :minor 0}
              :clojure {:major 1 :minor 11}}
   :id (:id message)})

(defn handle-ls-sessions [message]
  "Handle nREPL ls-sessions operation"
  {:status ["done"]
   :sessions (vec (keys (:sessions @server-state)))
   :id (:id message)})

(defn process-message [message]
  "Process incoming nREPL message"
  (log "Processing message:" (:op message) "id:" (:id message))
  
  (case (:op message)
    "eval" (handle-eval message)
    "clone" (handle-clone message)
    "close" (handle-close message)
    "describe" (handle-describe message)
    "ls-sessions" (handle-ls-sessions message)
    
    ;; Unknown operation
    {:status ["done" "unknown-op"]
     :id (:id message)}))

(defn handle-client [client-socket]
  "Handle a single client connection"
  (log "Client connected from" (.getRemoteSocketAddress client-socket))
  
  (try
    (let [out (PrintWriter. (.getOutputStream client-socket) true)
          in (BufferedReader. (InputStreamReader. (.getInputStream client-socket)))]
      
      (loop []
        (when-let [line (.readLine in)]
          (try
            (let [message (read-string line)]
              (log "Received:" message)
              (let [response (process-message message)]
                (log "Sending:" response)
                (.println out (pr-str response))
                (.flush out)))
            (catch Exception e
              (log "Error processing message:" (.getMessage e))
              (.println out (pr-str {:status ["done" "error"] 
                                    :ex (str "Parse error: " (.getMessage e))}))
              (.flush out)))
          (recur))))
    (catch Exception e
      (log "Client connection error:" (.getMessage e)))
    (finally
      (.close client-socket)
      (log "Client disconnected"))))

(defn write-port-file [port]
  "Write .nrepl-port file for auto-discovery"
  (let [port-file ".nrepl-port"]
    (spit port-file (str port))
    (log "Wrote port file:" port-file "with port:" port)))

(defn start-server [port]
  "Start the nREPL server"
  (let [server-socket (ServerSocket. port)
        actual-port (.getLocalPort server-socket)]
    
    (swap! server-state assoc :port actual-port)
    (write-port-file actual-port)
    
    (log "🚀 Test nREPL server started on port:" actual-port)
    (log "📁 Wrote .nrepl-port file for auto-discovery")
    (log "🔧 Available operations: eval, clone, close, describe, ls-sessions")
    (log "📝 Try connecting with: bb -e \"(require '[mcp-nrepl-proxy.nrepl-client :as n]) (def c (n/connect \\\"localhost\\\" " actual-port "))\"")
    
    ;; Accept connections in a loop
    (try
      (while true
        (let [client-socket (.accept server-socket)]
          ;; Handle each client in a separate thread (future)
          (future (handle-client client-socket))))
      (catch Exception e
        (log "Server error:" (.getMessage e)))
      (finally
        (.close server-socket)
        (fs/delete-if-exists ".nrepl-port")
        (log "Server stopped")))))

(defn -main [& args]
  "Main entry point"
  (let [port (if (seq args)
               (Integer/parseInt (first args))
               0)] ; 0 = random available port
    
    (log "Starting test nREPL server...")
    (start-server port)))

;; Enable direct script execution
(when (= *file* (System/getProperty "babashka.file"))
  (-main))