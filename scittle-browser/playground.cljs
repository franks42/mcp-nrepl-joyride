;; Scittle nREPL Playground
;; This ClojureScript code runs in the browser and can be evaluated via nREPL

(println "🎮 Scittle playground loaded!")

;; Basic arithmetic
(def result (+ 1 2 3))
(println (str "Math result: " result))

;; DOM manipulation example
(when-let [output-div (.getElementById js/document "output")]
  (let [p (.createElement js/document "p")]
    (set! (.-textContent p) (str "🎯 ClojureScript evaluation result: " result))
    (.appendChild output-div p)))

;; Define a simple function
(defn greet [name]
  (str "Hello, " name " from ClojureScript!"))

;; Test the function
(def greeting (greet "nREPL user"))
(println greeting)

;; Add greeting to the page
(when-let [output-div (.getElementById js/document "output")]
  (let [p (.createElement js/document "p")]
    (set! (.-textContent p) (str "💬 " greeting))
    (.appendChild output-div p)))

;; Demonstrate atom and state
(def app-state (atom {:counter 0 :messages []}))

(defn increment-counter! []
  (swap! app-state update :counter inc)
  (println (str "Counter: " (:counter @app-state))))

(defn add-message! [msg]
  (swap! app-state update :messages conj msg)
  (println (str "Messages: " (count (:messages @app-state)))))

;; Initialize some state
(increment-counter!)
(add-message! "Welcome to Scittle!")
(add-message! "nREPL connection active")

;; Show final state
(println "📊 Final app state:")
(println @app-state)

;; Browser-specific functions
(defn show-alert [message]
  (js/alert message))

(defn log-to-console [& args]
  (.log js/console (apply str args)))

;; Example usage (uncomment to test)
;; (show-alert "Hello from ClojureScript!")
(log-to-console "🚀 Scittle playground fully initialized!")

;; Export some functions to global scope for easy testing
(set! js/window.scittleGreet greet)
(set! js/window.scittleIncrement increment-counter!)
(set! js/window.scittleState (fn [] @app-state))

(println "✅ Playground setup complete!")
(println "Try these in your nREPL:")
(println "  (greet \"your-name\")")
(println "  (increment-counter!)")
(println "  (add-message! \"test message\")")
(println "  @app-state")