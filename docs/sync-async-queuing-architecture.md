# Sync-Async Queuing Architecture for MCP-nREPL Bridge

## Executive Summary

This document describes a robust queuing architecture that bridges the synchronous Model Context Protocol (MCP) with the asynchronous nREPL protocol. The design emphasizes simplicity, using Clojure's built-in persistent data structures within atoms rather than complex async frameworks. The architecture provides graceful timeout handling, connection state management, and transparent backpressure mechanisms while maintaining a clean separation between async core operations and sync convenience facades.

## Namespace Architecture (Updated 2025-01-14)

The implementation follows a clear namespace hierarchy that separates concerns:

```
nrepl-mcp-server/              ; Top-level project namespace
  core.clj                     ; Main entry point, minimal bootstrap
  
  state/                       ; State management by domain
    connection.clj             ; Connection state atom and helpers
    messages.clj               ; Message queue state management
    results.clj                ; Result queue state management
    tool_registry.clj          ; Tool registry atom and helper functions
    register_tools.clj         ; Tool registration orchestrator
  
  mcp-server/                  ; MCP server implementation
    server.clj                 ; stdio server, JSON-RPC handling
    dispatch.clj               ; Tool routing via registry (purely generic)
    tools/                     ; One file per MCP tool function
      debug_eval.clj           ; debug-eval tool (self-registering)
      debug_load_file.clj      ; debug-load-file tool (self-registering)
      nrepl_server.clj         ; Unified nREPL operations (self-registering)
      send_message_async.clj   ; Async message sending (Phase 2b)
      get_result_async.clj     ; Async result retrieval (Phase 2b)
      send_message_sync.clj    ; Sync wrapper combining send+get (Phase 2b)
  
  nrepl-client/                ; nREPL client implementation
    connection.clj             ; TCP connection management
    protocol.clj               ; bencode encoding/decoding, message framing
    handlers.clj               ; State watchers and queue processors
    
  utils/                       ; Shared utilities
    uuid_v7.clj                ; UUID v7 generation for message IDs
    async.clj                  ; Promise and timeout utilities
```

### Design Principles

1. **One file per MCP tool** - Each tool function gets its own file for clarity
2. **Self-registering tools** - Tools register themselves when namespaces load for clean decoupling
3. **Clear separation of concerns** - MCP server vs nREPL client vs state management vs tool registry
4. **Domain-focused state** - State split by concern (connection, messages, results, tools)
5. **Reactive architecture** - State atoms are separate from handlers that react to them
6. **Client perspective** - `nrepl-client` (not `nrepl-server`) since we're the client to nREPL servers
7. **Parallel naming** - `mcp-server/*` and `nrepl-client/*` for clear architectural boundaries
8. **Unified tool interfaces** - `nrepl-server` tool with `op` parameter (connect/disconnect/status) for consistency with nREPL patterns
9. **Generic dispatch** - `dispatch.clj` contains no tool-specific knowledge, purely registry-based routing

## 1. Architecture Overview

### 1.1 Core Problem Statement

The MCP protocol expects synchronous request-response patterns, while nREPL is fundamentally asynchronous with potentially long-running operations and streaming responses. This impedance mismatch requires a sophisticated queuing and correlation system that can:

- Handle the sync/async boundary gracefully
- Prevent resource exhaustion through backpressure
- Manage connection lifecycle and failures transparently
- Provide both simple sync and powerful async interfaces

### 1.2 Three-Layer Architecture Design

```
┌─────────────────────────────────────────────────────────────────┐
│                          MCP Server                              │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    MCP Layer                             │   │
│  │  nrepl-raw-async  │  nrepl-eval  │  nrepl-doc  │ etc.   │   │
│  │  (MCP validation, │  (refactored │  (refactored │        │   │
│  │   error format)   │   wrapper)   │   wrapper)   │        │   │
│  └────────────────────┬─────────────────────────────────────┘   │
│                       │                                          │
│  ┌────────────────────▼─────────────────────────────────────┐   │
│  │                 Internal Layer                           │   │
│  │  nrepl-raw-async-int │ send-message (existing, preserved)│   │
│  │  (pure nREPL logic)  │ (for backward compatibility)     │   │
│  └────────────────────┬─────────────────────────────────────┘   │
│                       │                                          │
│  ┌────────────────────▼─────────────────────────────────────┐   │
│  │                Transport Layer                           │   │
│  │  send-message-async │  Connection Management            │   │
│  │  (async/timeout/    │  (state tracking, health)        │   │
│  │   queuing)          │                                   │   │
│  └────────────────────┬─────────────────────────────────────┘   │
└───────────────────────┼─────────────────────────────────────────┘
                        │
                   TCP Socket
                        │
                   nREPL Server
```

### 1.3 MCP Function Interface Design

The exposed MCP functions follow a consistent interface pattern:

1. **nrepl-raw-async** - Direct async control:
   ```clojure
   {:op "nrepl-raw-async"
    :message {:op "eval" :code "(+ 1 2)"}
    :timeout-ms 30000
    :session-id "optional-session"}
   ```

2. **High-level wrappers** - Simple sync interface:
   ```clojure
   {:op "nrepl-eval" :code "(+ 1 2)"}  ; Internally uses nrepl-raw-async-int
   ```

## 2. Connection State Management

### 2.1 Atom-Based State Tracking

```clojure
(def connection-state
  (atom {:status :init
         :connections {}  ; connection-id -> connection details
         :default-id nil}))

(def connection-record
  {:id           "conn-uuid-v7"
   :host         "localhost"
   :port         7888
   :socket       <socket-obj>
   :input        <input-stream>
   :output       <output-stream>
   :created-at   <timestamp>
   :last-used    <timestamp>
   :health       :healthy})
```

### 2.2 Connection Lifecycle

```
┌──────────┐      connect      ┌────────────┐
│   Init   │ ──────────────────▶│  Connected │
└──────────┘                    └────────────┘
                                       │
                                    timeout/
                                     error
                                       ▼
                               ┌────────────┐
                               │   Failed   │
                               └────────────┘
```

## 3. Queuing Architecture

### 3.1 Dual Queue System

```clojure
(def message-queues
  (atom {:send-queue     (PersistentQueue/EMPTY)
         :result-queues  {}}))  ; message-id -> promise
```

The dual queue design separates concerns:
- **Send queue**: Buffers outgoing messages with backpressure control
- **Result queues**: Correlates responses via message-id keyed promises

### 3.2 Message Flow

```
MCP Request
    │
    ▼
[Validation & ID Generation]
    │
    ▼
Send Queue (PersistentQueue)
    │
    ▼
Worker Thread ──────▶ nREPL Server
    │                      │
    │                      ▼
    │               Response Stream
    │                      │
    ▼                      ▼
Promise <────────── Result Queue
    │
    ▼
MCP Response
```

## 4. Async Implementation with Promises

### 4.1 Promise-Based Timeout Pattern

```clojure
(defn send-message-async
  [connection message timeout-ms]
  (let [message-id (generate-message-id)
        result-promise (promise)
        enriched-msg (assoc message :id message-id)]
    
    ;; Register promise for this message
    (swap! message-queues
           assoc-in [:result-queues message-id] result-promise)
    
    ;; Queue for sending
    (swap! message-queues
           update :send-queue conj enriched-msg)
    
    ;; Wait with timeout
    (let [result (deref result-promise timeout-ms :timeout)]
      (if (= result :timeout)
        {:status :timeout
         :message-id message-id
         :timeout-ms timeout-ms}
        {:status :success
         :result result
         :message-id message-id}))))
```

### 4.2 Response Correlation

```clojure
(defn handle-nrepl-response
  [response]
  (when-let [message-id (:id response)]
    (when-let [result-promise (get-in @message-queues
                                      [:result-queues message-id])]
      ;; Deliver response to waiting promise
      (deliver result-promise response)
      ;; Clean up
      (swap! message-queues
             update :result-queues dissoc message-id))))
```

## 5. Backpressure and Flow Control

### 5.1 Queue Size Limits

```clojure
(def max-queue-size 1000)

(defn check-queue-pressure
  []
  (let [queue-size (count (:send-queue @message-queues))]
    (cond
      (> queue-size max-queue-size) :reject
      (> queue-size (* 0.8 max-queue-size)) :warning
      :else :ok)))
```

### 5.2 TCP Backpressure Integration

The system naturally inherits TCP flow control:
- Socket buffers provide automatic backpressure
- Slow nREPL server causes send() to block
- Queue worker respects socket blocking

## 6. Error Handling Strategy

### 6.1 Error Categories

1. **Connection Errors**: Failed socket operations
2. **Timeout Errors**: Promise timeout expiration
3. **Protocol Errors**: Malformed messages
4. **Queue Errors**: Overflow or corruption

### 6.2 Error Response Format

```clojure
{:status :error
 :error-type :timeout/:connection/:protocol/:queue
 :message "Human readable error"
 :details {:message-id "..."
          :timestamp ...
          :context ...}}
```

## 7. Implementation Phases

### Phase 1: Core Async Infrastructure ✅ COMPLETED
- [x] Basic send-message-async with promises
- [x] Timeout handling via deref
- [x] Simple correlation via message-id
- [x] Connection state management
- [x] Activity tracking and lifecycle

### Phase 2: MCP Integration (Current)
- [ ] nrepl-server tool with reactive state management
- [ ] Connection parameter resolution
- [ ] State watchers for async operations
- [ ] Debug-eval introspection

### Phase 3: Production Hardening
- [ ] Queue overflow protection
- [ ] Connection health monitoring
- [ ] Graceful degradation
- [ ] Metrics and observability

## 8. Testing Strategy

### 8.1 Anonymous Function Heartbeat

```clojure
;; Zero-setup comprehensive test
((fn [] :pong))  ; ~80 bytes response

;; Tests:
;; - Connection alive
;; - Message routing
;; - Response correlation
;; - Promise delivery
;; - Timeout handling
```

### 8.2 Layer-Specific Testing

1. **Transport Layer**: Raw socket operations
2. **Queue Layer**: Promise timeouts, correlation
3. **MCP Layer**: End-to-end with real nREPL

## 9. Performance Characteristics

### 9.1 Latency Profile

- Message queuing: ~1μs (atom swap)
- ID generation: ~10μs (UUID v7)
- Promise creation: ~1μs
- Network RTT: ~1ms (local)
- Total overhead: <0.1ms

### 9.2 Memory Profile

- Per message: ~500 bytes
- Per connection: ~10KB
- Queue capacity: 1000 messages = ~500KB
- Total footprint: <10MB typical

## 10. Configuration Parameters

```clojure
(def config
  {:default-timeout-ms 30000
   :max-queue-size 1000
   :health-check-interval-ms 5000
   :max-connections 10
   :backpressure-threshold 0.8})
```

## 11. Monitoring and Observability

### 11.1 Metrics

- Queue depth
- Message latency
- Timeout rate
- Connection health
- Error rates by type

### 11.2 Debug Endpoints

```clojure
(defn get-system-state
  []
  {:connections (count (:connections @connection-state))
   :queue-depth (count (:send-queue @message-queues))
   :pending-results (count (:result-queues @message-queues))
   :health :healthy})
```

## 12. Security Considerations

### 12.1 Connection Security

- No authentication (relies on network security)
- Local connections only by default
- No encryption (use SSH tunneling if needed)

### 12.2 Resource Protection

- Queue size limits prevent memory exhaustion
- Timeout prevents resource leaks
- Connection limits prevent socket exhaustion

## 13. Migration Path

### 13.1 Backward Compatibility

The existing `send-message` function is preserved:
```clojure
(defn send-message
  [connection message]
  ;; Existing synchronous implementation
  )
```

### 13.2 Gradual Adoption

1. Add send-message-async alongside existing code
2. Refactor high-level functions to use async internally
3. Maintain sync facades for compatibility
4. Deprecate sync-only paths over time

## 14. MCP Protocol and Output Stream Separation (January 2025)

### 14.1 Critical Discovery: stderr vs MCP Protocol

**Key Finding**: stderr does NOT interfere with MCP stdio communication
- Only stdout is used for MCP JSON-RPC protocol
- stderr remains completely separate
- stderr can be freely used for logging and debugging

### 14.2 MCP Protocol Stream Usage

```
┌─────────────────────────────────────────────┐
│           MCP stdio Transport               │
├─────────────────────────────────────────────┤
│  stdout: JSON-RPC messages (CRITICAL)       │
│  ┌─────────────────────────────────────┐   │
│  │ {"jsonrpc":"2.0","id":1,"result":...│   │
│  └─────────────────────────────────────┘   │
├─────────────────────────────────────────────┤
│  stderr: Debug/logging (SAFE TO USE)        │
│  ┌─────────────────────────────────────┐   │
│  │ 🚀 Starting server...                │   │
│  │ Debug: Processing request...         │   │
│  └─────────────────────────────────────┘   │
├─────────────────────────────────────────────┤
│  stdin: JSON-RPC requests from client       │
│  ┌─────────────────────────────────────┐   │
│  │ {"jsonrpc":"2.0","method":"tools/...│   │
│  └─────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
```

### 14.3 Implications for debug-eval Implementation

**Requirements hierarchy:**
1. **MANDATORY**: Capture stdout to prevent protocol corruption
2. **OPTIONAL**: Capture stderr for enhanced debugging
3. **BEST PRACTICE**: Return both streams in response

### 14.4 Current Implementation Status

✅ **Phase 1 Complete**: Protocol-safe debug-eval with stdout capture
```clojure
(defn debug-eval
  [{:keys [code] :as args}]
  (try
    (let [result-atom (atom nil)
          stdout-capture (with-out-str
                          (let [result (eval (read-string code))]
                            (reset! result-atom result)))
          result @result-atom]
      {:content [{:type "text"
                  :text (json/generate-string
                         {:status "success"
                         :code code
                         :result (pr-str result)
                         :stdout stdout-capture
                         :stderr ""
                         :error nil}
                         {:pretty true})}]})
    (catch Exception e ...)))
```

### 14.5 Code Example: Protocol Protection

```clojure
;; CRITICAL: Without stdout capture, this would break MCP:
(debug-eval {:code "(println \"Hello\")"})
;; stdout "Hello\n" would corrupt JSON-RPC stream

;; SAFE: With capture, protocol remains intact:
;; Response: {"stdout": "Hello\n", "result": "nil"}

;; SAFE: stderr doesn't affect protocol:
(binding [*out* *err*]
  (println "Debug info"))  ; Goes to stderr, protocol unaffected
```

### 14.6 Architecture Decision Update

**Rationale for current approach:**
- Simplicity: `with-out-str` is built-in and reliable
- Safety: Guarantees no stdout leakage
- Completeness: Captures all output during evaluation
- Extensibility: Can add stderr capture if needed

### 14.7 Testing Validation

```bash
# Test protocol integrity with stdout-producing code:
./scripts/test-phase1.sh

# All tests pass with protocol compliance:
# ✅ debug-eval with println
# ✅ debug-eval with print
# ✅ debug-eval with pr/prn
# ✅ No JSON corruption observed
# ✅ MCP protocol compliance confirmed
```

**stderr behavior confirmed:**
- stderr output visible in terminal during testing
- No interference with JSON-RPC message parsing
- MCP client successfully processes all responses

### 14.8 Future Enhancement Path

**If stderr capture is desired for debug-eval:**
```clojure
;; Could implement similar to debug-load-file approach:
(let [stderr-writer (StringWriter.)]
  (binding [*err* (PrintWriter. stderr-writer)]
    ;; ... evaluation logic
    {:stderr (str stderr-writer)}))
```

## 15. Phase 2 Reactive Connection Management Architecture

### 15.1 Design Overview
Phase 2 implements a reactive, state-driven connection management system using Clojure's atoms and watchers. This provides loose coupling between MCP tools and connection handling logic.

### 15.2 Connection State Model
```clojure
{:status :disconnected  ; States: :disconnected, :pending-connect, :connected, 
                        ;         :pending-disconnect, :failed
 :hostname nil          ; Target nREPL server hostname
 :port nil              ; Target nREPL server port
 :socket nil            ; Active TCP socket connection
 :connected-at nil      ; Timestamp of successful connection
 :error nil}            ; Error message if failed
```

### 15.3 State Transitions
```
┌──────────────┐  connect   ┌─────────────────┐  success  ┌────────────┐
│ disconnected │──────────▶│ pending-connect │─────────▶│  connected │
└──────────────┘           └─────────────────┘          └────────────┘
       ▲                            │                          │
       │                         failure                  disconnect
       │                            ▼                          ▼
       │                     ┌──────────┐            ┌──────────────────┐
       └─────────────────────│  failed  │            │pending-disconnect│
                            └──────────┘            └──────────────────┘
                                                              │
                                                              ▼
                                                        (disconnected)
```

### 15.4 Reactive Architecture Components

#### Central State Atom
Located in `src/mcp_server/state.clj`, provides:
- Single source of truth for connection status
- Helper functions for state updates
- Introspection via debug-eval

#### nrepl-server MCP Tool
Single tool with operations mimicking nREPL protocol:
- `{"op": "connect", "connection": "localhost:7888", "timeout": 5000}`
- `{"op": "disconnect", "timeout": 5000}`
- `{"op": "status"}`

Connection parameter resolution (in precedence order):
1. Explicit host:port string (e.g., "localhost:7888")
2. Port only (assumes localhost, e.g., "7888")
3. File path containing connection info
4. Environment variable `NREPL_CONNECT`
5. Return error if no connection info found

#### Connection Handler Watcher
Watches state atom for pending operations:
- `:pending-connect` → Attempts TCP connection, updates to `:connected` or `:failed`
- `:pending-disconnect` → Closes socket, updates to `:disconnected`

#### Queue Cleanup Handler (Future)
Will be implemented with message queue functionality:
- Detects transition to `:disconnected`
- Marks pending requests as failed
- Cleans up resources

### 15.5 Implementation Strategy
1. Build state namespace with connection atom
2. Implement nrepl-server MCP tool with parameter resolution
3. Add connection handler watcher for reactive processing
4. Test via debug-eval introspection before adding message queues

### 15.6 Testing Approach with debug-eval

```clojure
;; Introspect connection state
(debug-eval {:code "@mcp-server.state/connection-state"})

;; Simulate state transitions
(debug-eval {:code "(swap! mcp-server.state/connection-state 
                     assoc :status :pending-connect :hostname \"localhost\" :port 7888)"})

;; Verify watcher firing
(debug-eval {:code "(Thread/sleep 100) @mcp-server.state/connection-state"})

;; Test error scenarios
(debug-eval {:code "(swap! mcp-server.state/connection-state 
                     assoc :status :failed :error \"Connection refused\")"})
```

### 15.7 Design Rationale
- **Reactive over Imperative**: Watchers decouple request handling from connection logic
- **Single Connection**: Phase 2 supports one connection (multi-connection in future)
- **Explicit Lifecycle**: Require explicit disconnect before reconnect
- **Timeout Control**: Configurable timeouts with sensible defaults (5s)
- **Testability**: All state observable via debug-eval for development
- **Incremental Development**: Can test state management before adding real TCP connections

### 15.8 Error Handling Philosophy
- Connection errors are expected and handled gracefully
- Failed state requires explicit disconnect to retry
- Timeouts return control to caller with clear status
- All errors include actionable messages for debugging

## 16. Self-Registering Tools Architecture (January 2025)

### 16.1 Design Overview
Phase 2 implements a self-registering tools pattern that eliminates tool-specific knowledge from the dispatcher, achieving clean separation of concerns through dynamic registration.

### 16.2 Architecture Components

#### Tool Registry (`state/tool_registry.clj`)
```clojure
(def tool-registry
  "Atom containing all registered MCP tools"
  (atom {}))

(defn register-tool!
  "Register a new MCP tool with handler and metadata"
  [tool-name handler metadata]
  (swap! tool-registry assoc tool-name {:handler handler :metadata metadata}))

(defn get-tool
  "Get tool by name, returns {:handler fn :metadata map} or nil"
  [tool-name]
  (get @tool-registry tool-name))
```

#### Registration Orchestrator (`state/register_tools.clj`)
```clojure
(ns nrepl-mcp_server.state.register-tools
  (:require
    [nrepl-mcp_server.state.tool-registry :as registry]
    ;; Explicitly require all tool namespaces (triggers self-registration)
    [nrepl-mcp_server.mcp_server.tools.debug-eval]
    [nrepl-mcp_server.mcp_server.tools.debug-load-file]
    [nrepl-mcp_server.mcp_server.tools.nrepl-server]))

(defn register-tools!
  "Make tool registration explicit and log results"
  []
  (let [tool-count (registry/registry-size)]
    (binding [*out* *err*]
      (println (str "🔧 Registered " tool-count " MCP tools: "
                   (vec (registry/list-tool-names)))))
    tool-count))
```

#### Self-Registering Tools
Each tool file includes self-registration at namespace load:
```clojure
;; At end of each tool file:
(registry/register-tool!
 "debug-eval"
 handle
 {:description "Execute Clojure code within the MCP server runtime"
  :inputSchema {:type "object"
                :properties {:code {:type "string"
                                    :description "Clojure code to evaluate"}}
                :required ["code"]}})
```

#### Generic Dispatcher (`mcp_server/dispatch.clj`)
```clojure
(ns nrepl-mcp_server.mcp_server.dispatch
  (:require [nrepl-mcp_server.state.tool-registry :as registry]
            [nrepl-mcp_server.state.register-tools :as register]))

;; Initialize tool registry (causes self-registration)
(register/register-tools!)

(defn call-tool
  "Execute an MCP tool by name - purely generic dispatch"
  [tool-name args]
  (if-let [{:keys [handler]} (registry/get-tool tool-name)]
    (handler args)
    {:content [{:type "text" :text (str "❌ Unknown tool: " tool-name)}]
     :isError true}))
```

### 16.3 Registration Flow

```
Server Startup
     │
     ▼
dispatch.clj loads
     │
     ▼
(register/register-tools!) called
     │
     ▼
register-tools.clj requires tool namespaces
     │
     ▼
Each tool namespace loads
     │
     ▼
Tool calls (registry/register-tool! ...) 
     │
     ▼
Tool added to registry atom
     │
     ▼
Registration count logged: "🔧 Registered 3 MCP tools: [...]"
     │
     ▼
MCP server ready with dynamically registered tools
```

### 16.4 Key Benefits

1. **Complete Decoupling**: Dispatcher has zero knowledge of specific tools
2. **Dynamic Discovery**: Tools can be added/removed by changing require statements
3. **Clean Architecture**: Each tool is responsible for its own registration
4. **Eager Registration**: All tools available when first `tools/list` is called
5. **Separation of Data and Orchestration**: Registry (data) separate from registration (orchestration)
6. **MCP Protocol Compliance**: Tools discoverable via standard `tools/list` endpoint

### 16.5 Implementation Patterns

#### Tool Structure Template
```clojure
(ns some.tool.namespace
  (:require [nrepl-mcp_server.state.tool-registry :as registry]
            [cheshire.core :as json]))

(defn handle
  "Tool implementation function"
  [args]
  ;; Tool logic here
  {:content [{:type "text" :text "result"}]})

;; Self-register when namespace loads
(registry/register-tool!
 "tool-name"
 handle
 {:description "Tool description"
  :inputSchema {...}})
```

#### Registry Operations
```clojure
;; Check registry status
(registry/registry-size)          ; => 3
(registry/list-tool-names)        ; => ["debug-eval" "debug-load-file" "nrepl-server"]
(registry/get-registered-tools)   ; => {"tool-name" {:handler fn :metadata map}}

;; Runtime tool lookup (used by dispatcher)
(registry/get-tool "debug-eval")  ; => {:handler fn :metadata map}
```

### 16.6 Testing and Validation

#### Server Startup Confirmation
```bash
$ BABASHKA_CLASSPATH=src bb src/nrepl_mcp_server/core.clj
🔧 Registered 3 MCP tools: ["debug-eval" "debug-load-file" "nrepl-server"]
🚀 Starting nREPL-MCP server...
```

#### Runtime Introspection via debug-eval
```clojure
;; Verify registry state
(debug-eval {:code "(count @nrepl-mcp_server.state.tool-registry/tool-registry)"})
;; => 3

;; List registered tools  
(debug-eval {:code "(keys @nrepl-mcp_server.state.tool-registry/tool-registry)"})
;; => ("debug-eval" "debug-load-file" "nrepl-server")

;; Check tool metadata
(debug-eval {:code "(:description (:metadata (get @nrepl-mcp_server.state.tool-registry/tool-registry \"debug-eval\")))"})
;; => "Execute Clojure code within the MCP server runtime"
```

### 16.7 Design Rationale

**Why Self-Registration?**
- **Eliminates coupling**: Dispatcher doesn't need to know about specific tools
- **Enables modularity**: Tools can be added/removed independently
- **Follows Single Responsibility**: Each tool manages its own lifecycle
- **Supports extension**: New tools just need to follow the pattern

**Why Separate Registry and Orchestration?**
- **Data vs Logic**: `tool_registry.clj` is pure data, `register_tools.clj` is orchestration
- **Clear Dependencies**: Registry has no dependencies, orchestrator depends on tools
- **Testing**: Can test registry operations independently
- **Maintenance**: Changes to registration don't affect data structure

**Why Eager Registration?**
- **MCP Compliance**: Tools must be discoverable on first `tools/list` call
- **Client Compatibility**: MCP clients may not handle dynamic tool changes well
- **Predictable Behavior**: All tools available from server start
- **Simplified Logic**: No lazy loading complexity

### 16.8 Architecture Achievement

The self-registering tools pattern completes the namespace refactoring by:

1. **Achieving Clean Architecture**: No circular dependencies or architectural violations
2. **Enabling Pure Generic Dispatch**: `dispatch.clj` is completely tool-agnostic
3. **Providing Dynamic Extensibility**: New tools register automatically when required
4. **Maintaining Backward Compatibility**: All existing functionality preserved
5. **Following MCP Best Practices**: Tools discoverable and metadata-rich

This pattern serves as the foundation for all future MCP tool development in the system.

## 17. Cross-Namespace Introspection Capabilities (January 2025)

### 17.1 Discovery Summary
Phase 1 testing revealed powerful cross-namespace introspection capabilities within the SCI (Small Clojure Interpreter) runtime environment. The `debug-eval` tool can access and manipulate state across all loaded namespaces, providing comprehensive runtime visibility.

### 17.2 Introspection Scope

#### Available Namespace Operations
```clojure
;; List all loaded namespaces
(mapv ns-name (all-ns))
;; => [nrepl-mcp_server.core nrepl-mcp_server.state.tool-registry ...]

;; Access current execution namespace
*ns*
;; => #namespace[nrepl-mcp_server.core]

;; Find specific namespace
(find-ns (symbol "nrepl-mcp_server.state.tool-registry"))
;; => #namespace[nrepl-mcp_server.state.tool-registry]
```

#### Cross-Namespace Variable Access
```clojure
;; Access public vars from other namespaces
@nrepl-mcp_server.state.tool-registry/tool-registry
;; => {"debug-eval" {:handler ...} "debug-load-file" {:handler ...} ...}

;; List public vars in any namespace
(keys (ns-publics (find-ns (symbol "nrepl-mcp_server.state.connection"))))
;; => (connect! disconnect! get-connection ...)
```

### 17.3 Tool Registry Introspection
The tool registry atom can be fully accessed and modified:

```clojure
;; Get all tool names
(keys @nrepl-mcp_server.state.tool-registry/tool-registry)
;; => ("debug-eval" "debug-load-file" "nrepl-server")

;; Inspect tool metadata
(get-in @nrepl-mcp_server.state.tool-registry/tool-registry 
        ["debug-eval" :metadata :description])
;; => "Execute Clojure code within the MCP server runtime"

;; Count registered tools
(count @nrepl-mcp_server.state.tool-registry/tool-registry)
;; => 3
```

### 17.4 Runtime State Monitoring
All server state is accessible for debugging and monitoring:

```clojure
;; Monitor connection state
@nrepl-mcp_server.state.connection/connection-state

;; Inspect message queues (when implemented)
@nrepl-mcp_server.state.messages/message-queues

;; Check system health
{:namespaces (count (all-ns))
 :tools (count @nrepl-mcp_server.state.tool-registry/tool-registry)
 :runtime-type (type *ns*)}
```

### 17.5 Architectural Implications

#### Development Benefits
1. **Live Debugging**: Can inspect and modify server state without restart
2. **Runtime Validation**: Verify architectural assumptions during development  
3. **State Consistency**: Compare internal state with external behavior
4. **Performance Monitoring**: Track resource usage and identify bottlenecks

#### Production Considerations
1. **Security**: Full introspection provides powerful debugging but requires secure access
2. **Stability**: Runtime modifications can affect server behavior
3. **Observability**: Enables sophisticated monitoring and alerting
4. **Debugging**: Simplifies troubleshooting complex state issues

### 17.6 Testing Integration

#### Registry Consistency Test
The registry consistency test validates that internal tool registry matches the MCP protocol's exposed tools:

```python
async def test_registry_consistency(self) -> bool:
    """Test that internal tool registry matches exposed MCP tools list."""
    
    # Get tools from MCP protocol
    mcp_result = await self.client.list_tools()
    mcp_tool_names = sorted([tool["name"] for tool in mcp_result["tools"]])
    
    # Get tools from internal registry via introspection
    registry_result = await self.client.call_tool("debug-eval", {
        "code": "(sort (keys @nrepl-mcp_server.state.tool-registry/tool-registry))"
    })
    
    # Compare for consistency
    assert mcp_tool_names == registry_tool_names
```

This test ensures:
- No tools are missing from the MCP interface
- No tools are exposed that aren't in the registry  
- Internal state matches external API
- Cross-namespace introspection works correctly

### 17.7 Debug Toolkit Extensions

#### Helper Functions for Common Operations
```clojure
;; Define convenience functions for frequent introspection
(defn system-summary []
  {:namespaces (count (all-ns))
   :tools (keys @nrepl-mcp_server.state.tool-registry/tool-registry)
   :current-ns (str *ns*)
   :registry-size (count @nrepl-mcp_server.state.tool-registry/tool-registry)})

;; Architecture analysis
(defn architecture-analysis []
  {:tool-registry-consistency 
   (= (set (keys @nrepl-mcp_server.state.tool-registry/tool-registry))
      (set ["debug-eval" "debug-load-file" "nrepl-server"]))
   :namespace-health 
   (> (count (all-ns)) 50)})
```

### 17.8 HTTP Bridge Testing
The Phase H1 HTTP-to-stdio bridge testing validates these introspection capabilities work across transport layers:

- **Stateful Testing**: Variables and functions persist across HTTP requests
- **Cross-Transport Consistency**: Same introspection works via HTTP as stdio
- **Registry Validation**: HTTP bridge test suite includes registry consistency test
- **Performance Validation**: Rapid HTTP requests maintain state consistency

### 17.9 Design Validation
Cross-namespace introspection confirms several architectural decisions:

1. **State Separation**: Different concerns properly isolated in separate namespaces
2. **Registry Design**: Tool registry accessible and consistent with MCP protocol  
3. **SCI Capabilities**: Runtime provides full introspection without JVM limitations
4. **Transport Independence**: Introspection works regardless of MCP transport layer

### 17.10 Future Enhancement Opportunities

#### Monitoring Dashboard
```clojure
(defn monitoring-dashboard []
  {:server-uptime (- (System/currentTimeMillis) start-time)
   :active-tools (keys @nrepl-mcp_server.state.tool-registry/tool-registry)
   :namespace-count (count (all-ns))
   :memory-usage (System/getProperty "java.runtime.totalMemory")})
```

#### Health Check Integration
The registry consistency test could be extended for comprehensive health monitoring:
- Tool registration completeness
- Namespace loading verification
- State atom accessibility
- Cross-namespace communication validation

### 17.11 Key Achievements

1. **Complete Visibility**: All server internals accessible via debug-eval
2. **Architecture Validation**: Can verify design assumptions at runtime
3. **Testing Enhancement**: Registry consistency test ensures API accuracy
4. **Transport Independence**: Introspection works via stdio, HTTP, and future transports
5. **Development Efficiency**: Live debugging without server restarts

This introspection capability transforms development and testing workflows by providing unprecedented visibility into the running MCP server state across all architectural layers.

## Appendix 1 - nrepl's handlers middleware

Review NREPL's "Custom Middleware":
https://lambdaisland.com/guides/clojure-repls/clojure-repls
