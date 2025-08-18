;; MCP Server Introspection Test Suite
;; Use with local-load-file to test MCP server internal capabilities
;; Clean version without println statements to avoid JSON response conflicts

;; Test 1: Available namespaces
(def available-namespaces (sort (map str (all-ns))))

;; Test 2: Current namespace info
(def current-ns (str *ns*))

;; Test 3: Available functions in current namespace
(def public-functions (keys (ns-publics *ns*)))

;; Test 4: MCP tool registry simulation
(def tool-registry 
  {"local-eval" {:description "Execute Clojure code within the MCP server runtime"
                 :schema {:type "object"
                         :properties {:code {:type "string"}}
                         :required ["code"]}}
   "local-load-file" {:description "Load and evaluate a Clojure file in the MCP server runtime"
                      :schema {:type "object"
                              :properties {:file-path {:type "string"}}
                              :required ["file-path"]}}})

(def tool-names (keys tool-registry))

;; Test 5: MCP server capabilities simulation
(def server-capabilities 
  {:capabilities {:tools {}}
   :serverInfo {:name "mcp-server"
                :version "0.1.0-minimal"}})

;; Test 6: Server state management simulation
(def server-state (atom {:connected false
                        :tools tool-names
                        :sessions #{}
                        :last-activity (System/currentTimeMillis)}))

;; Update state to show mutations work
(swap! server-state assoc :connected true)
(swap! server-state update :sessions conj "session-123")

;; Test 7: Tool invocation simulation
(defn simulate-tool-call [tool-name args]
  (if (contains? tool-registry tool-name)
    {:status "success"
     :tool tool-name
     :args args
     :timestamp (System/currentTimeMillis)}
    {:status "error"
     :error (str "Unknown tool: " tool-name)}))

(def test-calls 
  [(simulate-tool-call "local-eval" {:code "(+ 1 2 3)"})
   (simulate-tool-call "local-load-file" {:file-path "test.clj"})
   (simulate-tool-call "unknown-tool" {})])

;; Test 8: Environment capabilities check
(def env-info 
  {:java-version (System/getProperty "java.version")
   :system-time (System/currentTimeMillis)
   :current-namespace current-ns
   :namespaces-available (count available-namespaces)})

;; Final summary - this is the return value
(def summary 
  {:namespaces-count (count available-namespaces)
   :current-namespace current-ns
   :functions-count (count public-functions)
   :tools-available (count tool-registry)
   :server-capabilities-defined true
   :state-management-working (= (:connected @server-state) true)
   :tool-simulations-count (count test-calls)
   :environment-info env-info})

;; Return summary as the final result
summary