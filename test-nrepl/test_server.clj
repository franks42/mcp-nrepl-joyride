(ns test-server
  "Simple Full Clojure nREPL Test Server"
  (:require [nrepl.server :refer [start-server stop-server]]
            [clojure.java.io :as io])
  (:gen-class))

(defn test-capabilities []
  (println "\n=== Testing Full Clojure Capabilities ===")
  (println "✅ Promises:" (let [p (promise)] (deliver p :test) (deref p)))
  (println "✅ Promise timeout:" (deref (promise) 100 :timeout))
  (println "✅ Futures:" @(future (+ 1 2 3)))
  (println "✅ Java interop:" (System/currentTimeMillis))
  (println "=== All capabilities verified! ===\n"))

(defn -main [& _args]
  (println "🚀 Starting Full Clojure nREPL Test Server...")
  (let [server (start-server :port 0)
        port (:port server)]
    (spit ".test-nrepl-server-port" port)
    (println (str "✅ Server started on port: " port))
    (test-capabilities)
    (println "Press Ctrl+C to stop")
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(do (io/delete-file ".test-nrepl-server-port" true)
                                   (stop-server server))))
    @(promise)))