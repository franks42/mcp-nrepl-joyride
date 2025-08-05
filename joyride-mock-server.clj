#!/usr/bin/env bb

(ns joyride-mock-server
  "Enhanced nREPL server that mocks Joyride/Calva functionality for testing.
   
   Simulates VS Code API access, workspace operations, and Calva middleware
   to provide realistic testing environment for the MCP-nREPL proxy."
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [clojure.java.io :as io])
  (:import [java.net ServerSocket Socket]
           [java.io PrintWriter BufferedReader InputStreamReader]))

(def server-state
  "Enhanced server state with VS Code context"
  (atom {:sessions {}
         :default-session "joyride-session-main"
         :eval-counter 0
         :port nil
         :workspace {:root "/Users/franksiebenlist/Development/mcp-nrepl-joyride"
                    :files ["src/core.clj" "src/utils.clj" "test/core_test.clj"]
                    :current-file "src/core.clj"
                    :cursor-line 42}
         :vscode {:active-editor {:file "src/core.clj" :line 42 :column 10}
                 :open-files ["src/core.clj" "README.md"]
                 :notifications []
                 :commands-executed []}}))

(defn log [& args]
  "Log to stderr with Joyride-style prefix"
  (binding [*out* *err*]
    (println "[Joyride-Mock]" (apply str args))))

(defn generate-session-id []
  "Generate Joyride-style session ID"
  (str "joyride-session-" (java.util.UUID/randomUUID)))

;; Mock VS Code API Functions

(defn execute-vscode-command [command & args]
  "Mock VS Code command execution"
  (swap! server-state update-in [:vscode :commands-executed] conj {:command command :args args :timestamp (System/currentTimeMillis)})
  (log "Executed VS Code command:" command "with args:" args)
  
  (case command
    "workbench.action.quickOpen" {:result "Quick Open displayed"}
    "workbench.action.showCommands" {:result "Command palette opened"}  
    "editor.action.formatDocument" {:result "Document formatted"}
    "workbench.action.files.save" {:result "File saved"}
    "vscode.open" {:result (str "Opened file: " (first args))}
    "revealLine" {:result (str "Revealed line: " (:lineNumber (first args)))}
    "calva.evaluateSelection" {:result "Selection evaluated"}
    "calva.loadFile" {:result "File loaded into REPL"}
    {:result (str "Unknown command: " command)}))

(defn get-vscode-context []
  "Get current VS Code context"
  (let [vscode-state (:vscode @server-state)
        workspace (:workspace @server-state)]
    {:activeEditor (:active-editor vscode-state)
     :workspaceRoot (:root workspace)
     :openFiles (:open-files vscode-state)
     :currentFile (:current-file workspace)
     :cursorPosition {:line (:cursor-line workspace) :column 0}}))

(defn show-vscode-notification [message type]
  "Mock VS Code notification"
  (let [notification {:message message :type type :timestamp (System/currentTimeMillis)}]
    (swap! server-state update-in [:vscode :notifications] conj notification)
    (log "VS Code notification:" type "-" message)
    notification))

;; Enhanced Code Evaluation with VS Code Integration

(defn eval-joyride-code [session-id code]
  "Evaluate code with Joyride/VS Code context"
  (swap! server-state update :eval-counter inc)
  (let [counter (:eval-counter @server-state)]
    (log "Joyride eval in session" session-id ":" code)
    
    (try
      (let [result (cond
                     ;; Basic arithmetic
                     (= code "(+ 1 2 3)") "6"
                     (= code "(* 2 3 4)") "24"
                     
                     ;; VS Code API calls
                     (str/includes? code "joyride.core/execute-command")
                     (let [command (re-find #"\"([^\"]+)\"" code)]
                       (if command
                         (str (:result (execute-vscode-command (second command))))
                         "Command executed"))
                     
                     (str/includes? code "js/vscode.window.activeTextEditor")
                     (json/generate-string (:active-editor (:vscode @server-state)))
                     
                     (str/includes? code "joyride/workspace-root")
                     (str "\"" (get-in @server-state [:workspace :root]) "\"")
                     
                     (str/includes? code "joyride/workspace-files")
                     (json/generate-string (get-in @server-state [:workspace :files]))
                     
                     ;; VS Code window operations
                     (str/includes? code "vscode.window.showInformationMessage")
                     (let [message (re-find #"\"([^\"]+)\"" code)]
                       (when message
                         (show-vscode-notification (second message) "info"))
                       "nil")
                     
                     ;; File operations
                     (str/includes? code "vscode.workspace.openTextDocument")
                     (let [file (re-find #"\"([^\"]+)\"" code)]
                       (when file
                         (swap! server-state assoc-in [:workspace :current-file] (second file)))
                       "{:uri \"file:///" (or (second file) "unknown") "\"}")
                     
                     ;; Namespace operations
                     (str/starts-with? code "(ns-publics")
                     "{clojure.core/+ #'clojure.core/+, clojure.core/- #'clojure.core/-, sample-fn #'user/sample-fn}"
                     
                     (str/starts-with? code "(all-ns)")
                     "[#namespace[user] #namespace[clojure.core] #namespace[joyride.core]]"
                     
                     ;; Clojure def/defn
                     (str/starts-with? code "(def ")
                     (do (log "Variable defined:" code) "#'user/some-var")
                     
                     (str/starts-with? code "(defn ")
                     (do (log "Function defined:" code) "#'user/some-fn")
                     
                     ;; REPL utilities  
                     (= code "*1") "6"  ; Previous result
                     (= code "*2") "nil"
                     (= code "*3") "nil"
                     
                     ;; Default evaluation
                     :else (str "=> " code " [mocked]"))]
        
        {:status ["done"]
         :value result
         :ns "user"
         :session session-id})
      
      (catch Exception e
        {:status ["done" "error"]
         :ex (str "class " (class e))
         :root-ex (str "class " (class e))  
         :session session-id}))))

;; Enhanced nREPL Operation Handlers

(defn handle-eval [message]
  "Enhanced eval handler with Joyride context"
  (let [code (:code message)
        session (or (:session message) (:default-session @server-state))
        ns-name (or (:ns message) "user")]
    
    (when-not (get-in @server-state [:sessions session])
      (swap! server-state assoc-in [:sessions session] 
             {:ns ns-name 
              :created (System/currentTimeMillis)
              :type "joyride"
              :bindings {"*1" "nil" "*2" "nil" "*3" "nil"}}))
    
    (merge (eval-joyride-code session code)
           {:id (:id message)})))

(defn handle-info [message]
  "Handle Calva-style info operation (symbol documentation)"
  (let [symbol (:symbol message)
        ns-name (or (:ns message) "user")]
    (log "Info request for symbol:" symbol "in ns:" ns-name)
    
    {:status ["done"]
     :info {:doc (str "Documentation for " symbol)
            :arglists [["x"] ["x" "y"]]  
            :file "user.clj"
            :line 42
            :name symbol
            :ns ns-name}
     :id (:id message)}))

(defn handle-complete [message]
  "Handle Calva-style completion operation"
  (let [prefix (:prefix message)
        ns-name (or (:ns message) "user")
        context (:context message)]
    (log "Completion request for prefix:" prefix "in ns:" ns-name)
    
    {:status ["done"]
     :completions [{:candidate "println" :type "function"}
                   {:candidate "print" :type "function"}
                   {:candidate "prn" :type "function"}
                   {:candidate "str" :type "function"}
                   {:candidate "+", :type "function"}
                   {:candidate "defn" :type "macro"}
                   {:candidate "def" :type "macro"}]
     :id (:id message)}))

(defn handle-load-file [message]
  "Handle Calva-style load-file operation"
  (let [file-path (:file-path message)
        file-content (:file message)]
    (log "Loading file:" file-path)
    
    ;; Mock file loading
    (swap! server-state update-in [:workspace :files] conj file-path)
    
    {:status ["done"]
     :value (str "Loaded file: " file-path)
     :id (:id message)}))

(defn handle-describe [message]
  "Enhanced describe with Joyride/Calva capabilities"
  {:status ["done"]
   :ops {:eval {}
         :clone {}
         :close {}
         :describe {}
         :info {}
         :complete {}
         :load-file {}
         :ls-sessions {}
         :joyride/execute-command {}
         :calva/get-context {}}
   :versions {:nrepl {:major 1 :minor 0}
              :clojure {:major 1 :minor 11}
              :joyride {:version "0.0.37"}
              :calva {:version "2.0.123"}}
   :id (:id message)})

(defn handle-joyride-command [message]
  "Handle Joyride-specific command execution"
  (let [command (:command message)
        args (:args message)]
    (log "Joyride command:" command "args:" args)
    
    {:status ["done"]
     :result (execute-vscode-command command args)
     :id (:id message)}))

(defn handle-calva-context [message]
  "Handle Calva context request"
  {:status ["done"]
   :context (get-vscode-context)
   :id (:id message)})

;; Session management functions (must be defined before process-message)
(defn handle-clone [message]
  "Handle nREPL clone operation (create new session)"
  (let [new-session (generate-session-id)]
    (swap! server-state assoc-in [:sessions new-session] 
           {:ns "user" 
            :created (System/currentTimeMillis)
            :type "joyride"
            :bindings {"*1" "nil" "*2" "nil" "*3" "nil"}})
    (log "Created new Joyride session:" new-session)
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

(defn handle-ls-sessions [message]
  "Handle nREPL ls-sessions operation"
  {:status ["done"]
   :sessions (vec (keys (:sessions @server-state)))
   :id (:id message)})

(defn process-message [message]
  "Enhanced message processing with Joyride/Calva operations"
  (log "Processing message:" (:op message) "id:" (:id message))
  
  (case (:op message)
    "eval" (handle-eval message)
    "clone" (handle-clone message)
    "close" (handle-close message)
    "describe" (handle-describe message)
    "info" (handle-info message)
    "complete" (handle-complete message)
    "load-file" (handle-load-file message)
    "ls-sessions" (handle-ls-sessions message)
    "joyride/execute-command" (handle-joyride-command message)  
    "calva/get-context" (handle-calva-context message)
    
    ;; Unknown operation
    {:status ["done" "unknown-op"]
     :id (:id message)}))

(defn handle-client [client-socket]
  "Handle a single client connection"
  (log "Joyride client connected from" (.getRemoteSocketAddress client-socket))
  
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
      (log "Joyride client disconnected"))))

(defn write-port-file [port]
  "Write .nrepl-port file for auto-discovery"
  (let [port-file ".nrepl-port"]
    (spit port-file (str port))
    (log "Wrote Joyride port file:" port-file "with port:" port)))

(defn start-server [port]
  "Start the enhanced Joyride-mock nREPL server"
  (let [server-socket (ServerSocket. port)
        actual-port (.getLocalPort server-socket)]
    
    (swap! server-state assoc :port actual-port)
    (write-port-file actual-port)
    
    (log "🚀 Joyride Mock nREPL server started on port:" actual-port)
    (log "📁 Wrote .nrepl-port file for auto-discovery")
    (log "🎯 VS Code workspace:" (get-in @server-state [:workspace :root]))
    (log "📝 Current file:" (get-in @server-state [:workspace :current-file]))
    (log "🔧 Enhanced operations: eval, info, complete, load-file, joyride/execute-command")
    (log "🎨 Try: (joyride.core/execute-command \"workbench.action.quickOpen\")")
    (log "🏠 Try: (joyride/workspace-root)")
    (log "📄 Try: (-> js/vscode.window.activeTextEditor .-document .-fileName)")
    
    (try
      (while true
        (let [client-socket (.accept server-socket)]
          (future (handle-client client-socket))))
      (catch Exception e
        (log "Server error:" (.getMessage e)))
      (finally
        (.close server-socket)
        (fs/delete-if-exists ".nrepl-port")
        (log "Joyride Mock server stopped")))))

(defn -main [& args]
  "Main entry point"
  (let [port (if (seq args)
               (Integer/parseInt (first args))
               0)]
    
    (log "Starting Joyride Mock nREPL server...")
    (start-server port)))

;; Enable direct script execution
(when (= *file* (System/getProperty "babashka.file"))
  (-main))