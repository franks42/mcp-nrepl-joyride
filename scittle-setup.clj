;; Scittle Browser nREPL Setup Functions
;; Load with: local-load-file {"file-path": "scittle-setup.clj"}

(println "🌐 Loading Scittle setup functions...")

;; Configuration
(def ^:private scittle-config
  {:scittle-dir "/Users/franksiebenlist/Development/scittle"
   :nrepl-port 7890
   :browser-nrepl-port 1339
   :websocket-port 1340
   :http-port 1341})

;; State tracking
(defonce scittle-state (atom {:status :stopped
                              :connections {}
                              :servers {}}))

;; =============================================================================
;; Core Functions
;; =============================================================================

(defn connect-to-scittle-bb!
  "Connect to the Scittle Babashka nREPL server"
  []
  (println "🔗 Connecting to Scittle Babashka nREPL...")
  ;; This will be called via nrepl-connection tool
  {:port (:nrepl-port scittle-config)
   :status :ready-to-connect})

(defn start-scittle-servers!
  "Start Scittle browser nREPL and HTTP servers via the connected BB nREPL"
  []
  (println "🚀 Starting Scittle browser servers...")
  ;; These will be called via nrepl-eval tool
  {:browser-nrepl-code "(require '[sci.nrepl.browser-server :as bp]) (bp/start! {:nrepl-port 1339 :websocket-port 1340})"
   :http-server-code "(require '[babashka.http-server :as http]) (future (http/serve {:port 1341 :dir \"/Users/franksiebenlist/Development/scittle/doc/nrepl\"}))"
   :status :ready-to-start})

(defn connect-to-browser-nrepl!
  "Connect to the Scittle browser nREPL server"
  []
  (println "🌐 Connecting to Scittle browser nREPL...")
  ;; This will be called via nrepl-connection tool
  {:port (:browser-nrepl-port scittle-config)
   :status :ready-to-connect})

(defn open-browser!
  "Open browser to Scittle environment"
  []
  (println "🌍 Opening browser to Scittle environment...")
  ;; This will be called via bash tool
  {:url (str "http://localhost:" (:http-port scittle-config) "/")
   :command (str "open http://localhost:" (:http-port scittle-config) "/")
   :status :ready-to-open})

;; =============================================================================
;; Complete Setup Function
;; =============================================================================

(defn setup-instructions
  "Return complete setup instructions"
  []
  {:instructions
   ["1. Connect to Scittle BB nREPL:"
    "   nrepl-connection {\"op\": \"connect\", \"connection\": \"7890\"}"
    ""
    "2. Start browser servers via nrepl-eval:"
    "   (require '[sci.nrepl.browser-server :as bp])"
    "   (bp/start! {:nrepl-port 1339 :websocket-port 1340})"
    "   (require '[babashka.http-server :as http])"
    "   (future (http/serve {:port 1341 :dir \"/Users/franksiebenlist/Development/scittle/doc/nrepl\"}))"
    ""
    "3. Connect to browser nREPL:"
    "   nrepl-connection {\"op\": \"connect\", \"connection\": \"1339\"}"
    ""
    "4. Open browser:"
    "   open http://localhost:1341/"
    ""
    "5. Test ClojureScript evaluation:"
    "   nrepl-eval {\"code\": \"(js/alert \\\"Hello from ClojureScript!\\\")\"}"]
   :config scittle-config})

;; =============================================================================
;; Helper Functions for Development
;; =============================================================================

(defn demo-alert
  "Demo function - show alert in browser"
  [message]
  {:code (str "(js/alert \"" message "\")")
   :description "Use with nrepl-eval to show browser alert"})

(defn demo-dom-manipulation
  "Demo function - add element to browser DOM"
  [title content]
  {:code (str "(let [div (.createElement js/document \"div\")]"
              "  (set! (.-innerHTML div) \"<h3>" title "</h3><p>" content "</p>\")"
              "  (set! (.-style.background div) \"lightgreen\")"
              "  (set! (.-style.padding div) \"15px\")"
              "  (set! (.-style.margin div) \"10px\")"
              "  (set! (.-style.borderRadius div) \"8px\")"
              "  (.appendChild (.-body js/document) div))")
   :description "Use with nrepl-eval to add styled div to browser"})

(defn demo-interactive-counter
  "Demo function - create interactive counter in browser"
  []
  {:code "(do
            (def counter (atom 0))
            (let [container (.createElement js/document \"div\")
                  display (.createElement js/document \"h2\")
                  button (.createElement js/document \"button\")]
              (set! (.-innerHTML display) (str \"Count: \" @counter))
              (set! (.-innerHTML button) \"Click me!\")
              (set! (.-onclick button) 
                    (fn [] 
                      (swap! counter inc)
                      (set! (.-innerHTML display) (str \"Count: \" @counter))))
              (set! (.-style.background container) \"lightyellow\")
              (set! (.-style.padding container) \"20px\")
              (set! (.-style.margin container) \"10px\")
              (set! (.-style.borderRadius container) \"10px\")
              (.appendChild container display)
              (.appendChild container button)
              (.appendChild (.-body js/document) container)))"
   :description "Use with nrepl-eval to create interactive counter widget"})

;; =============================================================================
;; Status and Info
;; =============================================================================

(defn scittle-info
  "Get current Scittle environment information"
  []
  {:config scittle-config
   :state @scittle-state
   :functions ["setup-instructions" "demo-alert" "demo-dom-manipulation" 
               "demo-interactive-counter" "scittle-info"]})

;; Print available functions
(println "✅ Scittle setup functions loaded!")
(println "📋 Available functions:")
(println "  (setup-instructions)     - Complete setup guide")
(println "  (demo-alert \"message\")    - Browser alert demo")
(println "  (demo-dom-manipulation)  - DOM manipulation demo")
(println "  (demo-interactive-counter) - Interactive widget demo")
(println "  (scittle-info)           - Environment information")
(println "")
(println "🚀 Quick start: (setup-instructions)")

;; Return success
{:status "loaded"
 :functions-loaded 5
 :config scittle-config}