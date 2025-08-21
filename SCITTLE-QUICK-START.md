# 🌐 Scittle Browser nREPL Quick Start Guide

## One-Line Setup

```bash
./start-scittle-env.sh
```

## Step-by-Step Manual Process

### 1. Start Environment
```bash
./start-scittle-env.sh
```

### 2. Load Helper Functions
```clojure
local-load-file {"file-path": "scittle-setup.clj"}
```

### 3. Connect to Scittle Babashka nREPL
```clojure
nrepl-connection {"op": "connect", "connection": "7890"}
```

### 4. Start Browser Servers
```clojure
nrepl-eval {"code": "(require '[sci.nrepl.browser-server :as bp]) (bp/start! {:nrepl-port 1339 :websocket-port 1340})"}
nrepl-eval {"code": "(require '[babashka.http-server :as http]) (future (http/serve {:port 1341 :dir \"/Users/franksiebenlist/Development/scittle/doc/nrepl\"}))"}
```

### 5. Connect to Browser nREPL
```clojure
nrepl-connection {"op": "connect", "connection": "1339"}
```

### 6. Open Browser
```bash
open http://localhost:1341/
```

### 7. Test ClojureScript
```clojure
nrepl-eval {"code": "(js/alert \"🎉 Hello from ClojureScript!\")"}
```

## Demo Functions

After loading `scittle-setup.clj`, get demo code with:

```clojure
local-eval {"code": "(demo-alert \"Hello World!\")"}
local-eval {"code": "(demo-dom-manipulation \"My Title\" \"My content\")"}
local-eval {"code": "(demo-interactive-counter)"}
```

Then use the returned code with `nrepl-eval`.

## Architecture

```
Claude Code (nREPL MCP Server)
    ↓ nrepl-eval
Scittle Babashka nREPL (port 7890)
    ↓ starts
Browser nREPL (port 1339) ←→ WebSocket (port 1340) ←→ Browser
    ↑
HTTP Server (port 1341) serves browser assets
```

## Ports Used

- **7890**: Scittle Babashka nREPL server
- **1339**: Browser nREPL server  
- **1340**: WebSocket bridge
- **1341**: HTTP server for browser assets

## Cleanup

```bash
./stop-scittle-env.sh
```

## Troubleshooting

1. **Port conflicts**: Run `./stop-scittle-env.sh` first
2. **Scittle repo missing**: Clone with `git clone https://github.com/babashka/scittle.git /Users/franksiebenlist/Development/scittle`
3. **Browser not connecting**: Check that all 4 ports are running
4. **nREPL connection failed**: Ensure Babashka nREPL started successfully

## Common ClojureScript Examples

```clojure
;; Basic alert
(js/alert "Hello!")

;; DOM manipulation
(set! (.-innerHTML (.-body js/document)) "<h1>Hello from ClojureScript!</h1>")

;; Create interactive elements
(let [button (.createElement js/document "button")]
  (set! (.-innerHTML button) "Click me!")
  (set! (.-onclick button) #(js/alert "Button clicked!"))
  (.appendChild (.-body js/document) button))

;; State management with atoms
(def counter (atom 0))
(swap! counter inc)
@counter
```