(ns nrepl-mcp-server.mcp-server.tools.nrepl-eval
  "nREPL code evaluation tool for MCP - handles eval, load-file, complete, doc operations"
  (:require [nrepl-mcp-server.state.connection :as state]
            [nrepl-mcp-server.nrepl-client.operations :as nrepl-ops]
            [cheshire.core :as json]
            [nrepl-mcp-server.state.tool-registry :as registry]))

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn- get-active-connection
  "Get active nREPL connection or return error"
  []
  (let [conn-state @state/connection-state]
    (if (and (= :connected (:status conn-state))
             (:connection conn-state))
      {:success true :connection (:connection conn-state)}
      {:success false
       :error (str "Not connected to nREPL server. Current status: "
                   (:status conn-state)
                   ". Use nrepl-server tool with 'connect' operation first.")})))

(defn- format-nrepl-response
  "Format nREPL async response for MCP"
  [async-result operation]
  (case (:status async-result)
    :success
    (let [response (:response async-result)]
      {:content [{:type "text"
                  :text (json/generate-string
                         {:status "success"
                          :operation operation
                          :result response}
                         {:pretty true})}]})

    :timeout
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "error"
                        :operation operation
                        :error "Operation timed out"
                        :timeout-ms (:timeout-ms async-result)}
                       {:pretty true})}]
     :isError true}

    :error
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "error"
                        :operation operation
                        :error "Communication error"
                        :details (str (:error async-result))}
                       {:pretty true})}]
     :isError true}))

;; =============================================================================
;; Operation Handlers
;; =============================================================================

(defn handle-eval
  "Handle code evaluation"
  [{:keys [code session ns timeout] :or {timeout 10000}}]
  (if (empty? code)
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "error"
                        :operation "eval"
                        :error "No code provided"}
                       {:pretty true})}]
     :isError true}
    (let [conn-result (get-active-connection)]
      (if (:success conn-result)
        (let [async-result (nrepl-ops/eval-code
                            (:connection conn-result)
                            code
                            :session session
                            :ns ns
                            :timeout-ms timeout)]
          (format-nrepl-response async-result "eval"))
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :operation "eval"
                            :error (:error conn-result)}
                           {:pretty true})}]
         :isError true}))))

(defn handle-load-file
  "Handle file loading"
  [{:keys [file-path session ns timeout] :or {timeout 15000}}]
  (if (empty? file-path)
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "error"
                        :operation "load-file"
                        :error "No file path provided"}
                       {:pretty true})}]
     :isError true}
    (let [conn-result (get-active-connection)]
      (if (:success conn-result)
        (try
          (let [async-result (nrepl-ops/load-file
                              (:connection conn-result)
                              file-path
                              :session session
                              :ns ns
                              :timeout-ms timeout)]
            (format-nrepl-response async-result "load-file"))
          (catch Exception e
            {:content [{:type "text"
                        :text (json/generate-string
                               {:status "error"
                                :operation "load-file"
                                :file-path file-path
                                :error (str "File error: " (.getMessage e))}
                               {:pretty true})}]
             :isError true}))
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :operation "load-file"
                            :error (:error conn-result)}
                           {:pretty true})}]
         :isError true}))))

(defn handle-complete
  "Handle code completion"
  [{:keys [prefix session ns context timeout] :or {timeout 5000}}]
  (if (empty? prefix)
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "error"
                        :operation "complete"
                        :error "No prefix provided"}
                       {:pretty true})}]
     :isError true}
    (let [conn-result (get-active-connection)]
      (if (:success conn-result)
        (let [async-result (nrepl-ops/complete
                            (:connection conn-result)
                            prefix
                            :session session
                            :ns ns
                            :context context
                            :timeout-ms timeout)]
          (format-nrepl-response async-result "complete"))
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :operation "complete"
                            :error (:error conn-result)}
                           {:pretty true})}]
         :isError true}))))

(defn handle-doc
  "Handle documentation lookup"
  [{:keys [symbol session ns timeout] :or {timeout 5000}}]
  (if (empty? symbol)
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "error"
                        :operation "doc"
                        :error "No symbol provided"}
                       {:pretty true})}]
     :isError true}
    (let [conn-result (get-active-connection)]
      (if (:success conn-result)
        (let [async-result (nrepl-ops/doc
                            (:connection conn-result)
                            symbol
                            :session session
                            :ns ns
                            :timeout-ms timeout)]
          (format-nrepl-response async-result "doc"))
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :operation "doc"
                            :error (:error conn-result)}
                           {:pretty true})}]
         :isError true}))))

(defn handle-session-create
  "Handle session creation"
  [{:keys [timeout] :or {timeout 5000}}]
  (let [conn-result (get-active-connection)]
    (if (:success conn-result)
      (let [async-result (nrepl-ops/create-session
                          (:connection conn-result)
                          :timeout-ms timeout)]
        (format-nrepl-response async-result "session-create"))
      {:content [{:type "text"
                  :text (json/generate-string
                         {:status "error"
                          :operation "session-create"
                          :error (:error conn-result)}
                         {:pretty true})}]
       :isError true})))

(defn handle-session-close
  "Handle session close"
  [{:keys [session timeout] :or {timeout 5000}}]
  (if (empty? session)
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "error"
                        :operation "session-close"
                        :error "No session ID provided"}
                       {:pretty true})}]
     :isError true}
    (let [conn-result (get-active-connection)]
      (if (:success conn-result)
        (let [async-result (nrepl-ops/close-session
                            (:connection conn-result)
                            session
                            :timeout-ms timeout)]
          (format-nrepl-response async-result "session-close"))
        {:content [{:type "text"
                    :text (json/generate-string
                           {:status "error"
                            :operation "session-close"
                            :error (:error conn-result)}
                           {:pretty true})}]
         :isError true}))))

(defn handle-health-check
  "Handle nREPL connection health check"
  [{:keys [timeout] :or {timeout 3000}}]
  (let [conn-result (get-active-connection)]
    (if (:success conn-result)
      (let [async-result (nrepl-ops/health-check
                          (:connection conn-result)
                          :timeout-ms timeout)]
        (format-nrepl-response async-result "health-check"))
      {:content [{:type "text"
                  :text (json/generate-string
                         {:status "error"
                          :operation "health-check"
                          :error (:error conn-result)}
                         {:pretty true})}]
       :isError true})))

;; =============================================================================
;; Main Handler
;; =============================================================================

(defn handle
  "Handle nREPL evaluation operations based on op parameter"
  [{:keys [op] :as args}]
  (case op
    "eval" (handle-eval args)
    "load-file" (handle-load-file args)
    "complete" (handle-complete args)
    "doc" (handle-doc args)
    "session-create" (handle-session-create args)
    "session-close" (handle-session-close args)
    "health-check" (handle-health-check args)

    ;; Unknown operation
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "error"
                        :operation op
                        :error (str "Unknown operation: " op
                                    ". Available: eval, load-file, complete, doc, session-create, session-close, health-check")}
                       {:pretty true})}]
     :isError true}))

;; =============================================================================
;; Self Registration
;; =============================================================================

;; Self-register this tool when namespace loads
(registry/register-tool!
 "nrepl-eval"
 handle
 {:description "nREPL code evaluation and IDE operations"
  :inputSchema {:type "object"
                :properties {:op {:type "string"
                                  :description "Operation: eval, load-file, complete, doc, session-create, session-close, health-check"
                                  :enum ["eval" "load-file" "complete" "doc" "session-create" "session-close" "health-check"]}
                             :code {:type "string"
                                    :description "Clojure code to evaluate (for eval operation)"}
                             :file-path {:type "string"
                                         :description "Path to file to load (for load-file operation)"}
                             :prefix {:type "string"
                                      :description "Symbol prefix for completion (for complete operation)"}
                             :symbol {:type "string"
                                      :description "Symbol name for documentation (for doc operation)"}
                             :session {:type "string"
                                       :description "nREPL session ID (optional)"}
                             :ns {:type "string"
                                  :description "Namespace for evaluation (optional)"}
                             :context {:type "string"
                                       :description "Context for completion (optional)"}
                             :timeout {:type "integer"
                                       :description "Timeout in milliseconds"}}
                :required ["op"]}})