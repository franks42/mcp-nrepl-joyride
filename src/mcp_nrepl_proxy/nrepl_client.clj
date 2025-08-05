(ns mcp-nrepl-proxy.nrepl-client
  "Simple nREPL client implementation for Babashka.
   
   Since full nREPL clients have JVM-specific dependencies, this provides
   a minimal implementation using basic socket communication."
  (:require [cheshire.core :as json]
            [clojure.string :as str])
  (:import [java.net Socket]
           [java.io PrintWriter BufferedReader InputStreamReader]))

(defn connect
  "Connect to nREPL server and return connection map"
  [host port]
  (let [socket (Socket. host port)
        out (PrintWriter. (.getOutputStream socket) true)
        in (BufferedReader. (InputStreamReader. (.getInputStream socket)))]
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

(defn send-message
  "Send nREPL message and return response"
  [{:keys [out in]} message]
  (let [msg-with-id (assoc message :id (generate-id))]
    ;; Send message
    (.println out (pr-str msg-with-id))
    (.flush out)
    
    ;; Read response
    (let [response-line (.readLine in)]
      (when response-line
        (read-string response-line)))))

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