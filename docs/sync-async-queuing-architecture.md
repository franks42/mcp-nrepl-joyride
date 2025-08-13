# Sync-Async Queuing Architecture for MCP-nREPL Bridge

## Executive Summary

This document describes a robust queuing architecture that bridges the synchronous Model Context Protocol (MCP) with the asynchronous nREPL protocol. The design emphasizes simplicity, using Clojure's built-in persistent data structures within atoms rather than complex async frameworks. The architecture provides graceful timeout handling, connection state management, and transparent backpressure mechanisms while maintaining a clean separation between async core operations and sync convenience facades.

## Namespace Architecture (Updated 2025-01-14)

The implementation follows a clear namespace hierarchy that separates concerns:

```
nrepl-mcp-server/              ; Top-level project namespace
  core.clj                     ; Main entry point, minimal bootstrap
  state.clj                    ; Centralized state atoms and queues
  
  mcp/                         ; MCP protocol implementation
    server.clj                 ; stdio server, JSON-RPC handling
    dispatch.clj               ; Tool routing and dispatch table
    tools/                     ; One file per MCP tool function
      debug_eval.clj           ; debug-eval tool
      debug_load_file.clj      ; debug-load-file tool
      nrepl_connect.clj        ; nREPL connect operation
      nrepl_disconnect.clj     ; nREPL disconnect operation
      nrepl_status.clj         ; nREPL status operation
      send_message_async.clj   ; Async message sending
      get_result_async.clj     ; Async result retrieval
      send_message_sync.clj    ; Sync wrapper combining send+get
  
  nrepl_client/                ; nREPL client implementation
    connection.clj             ; TCP connection management
    protocol.clj               ; bencode encoding/decoding, message framing
    handlers.clj               ; State watchers and queue processors
    
  utils/                       ; Shared utilities
    uuid_v7.clj                ; UUID v7 generation for message IDs
    async.clj                  ; Promise and timeout utilities
```

### Design Principles

1. **One file per MCP tool** - Each tool function gets its own file for clarity
2. **Clear separation of concerns** - MCP protocol vs nREPL client vs state management
3. **Reactive architecture** - State atoms are separate from handlers that react to them
4. **Client perspective** - `nrepl_client` (not `nrepl_server`) since we're the client to nREPL servers
5. **Centralized state** - All state atoms live in the top-level `state.clj` for easy introspection

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