;; Scittle nREPL Server Startup Script
;; Use with local-load-file to start Scittle browser nREPL server

(println "🌐 Starting Scittle browser nREPL server...")

;; Require the Scittle nREPL namespace
(require '[sci.nrepl.browser-server :as scittle-nrepl])

;; Start the browser nREPL server
;; This creates both an nREPL server (port 1339) and WebSocket server (port 1340)
(def scittle-server
  (scittle-nrepl/start! {:nrepl-port 1339 :websocket-port 1340}))

(println "✅ Scittle nREPL server started!")
(println "   📡 nREPL port: 1339")
(println "   🔌 WebSocket port: 1340")
(println "   🌐 Ready for browser connections")

;; Return server info
{:status "started"
 :nrepl-port 1339
 :websocket-port 1340
 :server scittle-server}