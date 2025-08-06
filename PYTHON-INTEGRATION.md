# Python Integration via Basilisp nREPL

This demonstrates how our MCP-nREPL bridge can connect to **any** nREPL server, including Python processes running Basilisp.

## Architecture

```
AI Assistant (Claude)
    ↓
MCP-nREPL Bridge (Babashka)
    ↓
[Generic nREPL Client]
    ↓
Basilisp nREPL Server (Python)
    ↓
Python Runtime & Objects
```

## The Power of nREPL Protocol

Our MCP server doesn't need Python-specific code! The nREPL protocol is **universal**:

- **Same protocol**: Bencode messaging
- **Same operations**: eval, describe, clone, etc.
- **Same tools**: nrepl-connect, nrepl-eval, nrepl-status
- **Language agnostic**: Works with any nREPL implementation

## Setup

### 1. Install Basilisp

```bash
pip install basilisp basilisp-nrepl-async
```

### 2. Start Python Server with nREPL

```bash
python3 test-basilisp-server.py 7890
```

This starts:
- Python server with live data
- Basilisp nREPL on port 7890
- Background worker thread

### 3. Connect via MCP

Use the existing `nrepl-connect` tool:

```json
{
  "method": "tools/call",
  "params": {
    "name": "nrepl-connect",
    "arguments": {
      "host": "localhost",
      "port": 7890
    }
  }
}
```

### 4. Introspect Python from Clojure

Now you can evaluate Clojure code that accesses Python objects:

```clojure
;; Get Python server status
(python-bridge/server-status)

;; Increment Python counter
(python-bridge/increment-counter)

;; Get Python runtime info
(python-bridge/python-info)

;; List loaded Python modules
(python-bridge/python-modules)

;; Direct Python object access
(.-counter python-bridge/py-server)
```

## The Complete Stack

You can now have **multiple nREPL connections** simultaneously:

1. **Joyride** (port 62577) → ClojureScript → VS Code APIs
2. **Babashka** (port 7889) → Clojure → MCP server internals  
3. **Basilisp** (port 7890) → Python → Python runtime & libraries

All controlled through the **same MCP interface**!

## Example Session

```bash
# Terminal 1: Start Python server
python3 test-basilisp-server.py

# Terminal 2: Connect and introspect
curl -X POST http://localhost:3000/mcp \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "nrepl-eval",
      "arguments": {
        "code": "(python-bridge/python-info)"
      }
    }
  }'
```

## Key Insights

1. **Protocol Power**: The nREPL protocol enables polyglot programming
2. **No Changes Needed**: Our MCP server works with any nREPL implementation
3. **Language Bridge**: Write Clojure, execute in Python, get results back
4. **Full Introspection**: Access Python objects, modules, environment
5. **Unified Interface**: Same MCP tools for all languages

## The Inception Stack Extended

```
Human
  ↓
AI (Claude)
  ↓
MCP Bridge (Babashka/Clojure)
  ↓
├── Joyride (ClojureScript) → JavaScript/VS Code
├── Babashka (Clojure) → JVM/Native
└── Basilisp (Clojure syntax) → Python Runtime
```

Each environment can:
- Be controlled by AI
- Introspect its runtime
- Execute code in its language
- Share data via nREPL protocol

This demonstrates the true power of nREPL as a **universal REPL protocol** for polyglot development!