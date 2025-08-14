#!/usr/bin/env bb

;; Simple Test nREPL Server with Full Clojure Environment
;; 
;; This creates a minimal nREPL server that runs in full JVM Clojure
;; (not SCI) for testing our MCP-nREPL async implementation.
;;
;; Requirements:
;; - Java 8+
;; - Clojure CLI tools
;;
;; Usage:
;;   bb test-clojure-nrepl-server.clj
;;   or
;;   clj -M test-clojure-nrepl-server.clj

(require '[clojure.java.shell :as shell])

(def deps-config
  {:deps {'org.clojure/clojure {:mvn/version "1.11.1"}
          'nrepl/nrepl {:mvn/version "1.3.1"}}})

(def server-code
  '(do
     (require '[nrepl.server :refer [start-server stop-server]])
     (require '[clojure.java.io :as io])
     
     (defn create-port-file [port]
       (spit ".nrepl-port" port)
       (println (str "nREPL port file created: " port)))
     
     (defn cleanup-port-file []
       (when (.exists (io/file ".nrepl-port"))
         (io/delete-file ".nrepl-port")
         (println "nREPL port file deleted")))
     
     (defn start-test-server []
       (println "Starting Full Clojure nREPL Test Server...")
       (let [server (start-server :port 0)  ; Use port 0 for auto-assignment
             port (:port server)]
         (println (str "nREPL server started on port: " port))
         (create-port-file port)
         
         ;; Test full Clojure capabilities
         (println "Testing full Clojure capabilities:")
         (println "- Promises available:" (pr-str (promise)))
         (println "- Futures available:" (pr-str (future (+ 1 2))))
         (println "- Java interop available:" (pr-str (System/currentTimeMillis)))
         
         ;; Add shutdown hook
         (.addShutdownHook (Runtime/getRuntime)
                           (Thread. (fn []
                                     (println "\nShutting down nREPL server...")
                                     (cleanup-port-file)
                                     (stop-server server))))
         
         (println "Press Ctrl+C to stop server")
         (println "Connect with: clj -M -m nrepl.cmdline --connect --port" port)
         
         ;; Keep server running
         @(promise)))
     
     (start-test-server)))

(defn write-deps-file []
  (spit "test-nrepl-deps.edn" (pr-str deps-config))
  (println "Created deps file: test-nrepl-deps.edn"))

(defn start-clojure-nrepl []
  (write-deps-file)
  (println "Starting Full Clojure nREPL server...")
  (let [result (shell/sh "clj" "-Sdeps" (pr-str deps-config) "-e" (pr-str server-code))]
    (when (not= 0 (:exit result))
      (println "Error starting nREPL server:")
      (println (:err result)))
    result))

;; Main execution
(if (= *file* (System/getProperty "babashka.file"))
  (do
    (println "=== Full Clojure nREPL Test Server ===")
    (println "This server provides complete JVM Clojure capabilities")
    (println "(promises, futures, full Java interop)")
    (println)
    (start-clojure-nrepl))
  (println "Script loaded. Call (start-clojure-nrepl) to start server."))