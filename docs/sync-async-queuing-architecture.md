# Sync-Async Queuing Architecture for MCP-nREPL Bridge

## Executive Summary

This document describes a robust queuing architecture that bridges the synchronous Model Context Protocol (MCP) with the asynchronous nREPL protocol. The design emphasizes simplicity, using Clojure's built-in persistent data structures within atoms rather than complex async frameworks. The architecture provides graceful timeout handling, connection state management, and transparent backpressure mechanisms while maintaining a clean separation between async core operations and sync convenience facades.

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

The `nrepl-raw-async` MCP function will accept:

```json
{
  "name": "nrepl-raw-async",
  "arguments": {
    "message": {
      "op": "eval",
      "code": "(+ 1 2 3)",
      "session": "optional-session-id"
    },
    "timeout_ms": 60000  // Optional: default 30000ms (30s)
  }
}
```

**Timeout Use Cases for AI Assistants:**
- **Quick evaluations**: Default 30s for simple expressions
- **File loading**: 60-120s for large files or complex dependencies  
- **Long computations**: 300s+ for data processing, ML training
- **Interactive development**: 10-15s for fast feedback loops
- **Production scripts**: 600s+ for deployment or migration tasks

**Parameter Flow:**
```
MCP nrepl-raw-async(message, timeout_ms=30000)
    ↓
nrepl-raw-async-int(message, timeout_ms)  
    ↓
send-message-async(connection, message, timeout_ms)
```

### 1.4 Data Flow

```
MCP Request Flow:
─────────────────
Client Request
     ↓
[Sync Facade] ──────────┐
     ↓                  │
Generate Message ID     │
     ↓                  │
Register Promise        │ Returns immediately
     ↓                  │ with promise/handle
Queue for Send          │
     ↓                  │
Wait with Timeout ←─────┘
     ↓
Return Response or Timeout


nREPL Response Flow:
───────────────────
Socket Read
     ↓
Parse Bencode
     ↓
Extract Message ID
     ↓
Route to Promise (O(1) lookup)
     ↓
Accumulate Response
     ↓
Check "done" Status
     ↓
Deliver Promise
     ↓
Cleanup Route Entry
```

## 2. Component Design

### 2.1 Message Router

The message router is the core correlation mechanism, implemented as an atom containing a map from message-ids to response handlers.

```
Message Router (atom)
┌────────────────────────────────────────┐
│  message-id-1 → {:promise <promise>    │
│                  :created-at <timestamp>│
│                  :status :pending}      │
│                                        │
│  message-id-2 → {:promise <promise>    │
│                  :created-at <timestamp>│
│                  :status :complete}     │
│                                        │
│  message-id-3 → {:promise <promise>    │
│                  :created-at <timestamp>│
│                  :status :timeout}      │
└────────────────────────────────────────┘
```

Key characteristics:
- O(1) message routing via map lookup
- Automatic cleanup after timeout or completion
- No head-of-line blocking between messages
- Memory bounded by timeout guarantees

### 2.2 Send Queue

The send queue uses Clojure's PersistentQueue within an atom for thread-safe FIFO ordering.

```
Send Queue Flow
───────────────
                 Enqueue
                    ↓
    ┌──────────────────────────────┐
    │  PersistentQueue in Atom     │
    │  [msg1] [msg2] [msg3] ...    │
    └──────────────┬───────────────┘
                   │
                Dequeue
                   ↓
              Send Worker
                   ↓
            Socket Write
```

Key characteristics:
- Preserves message ordering when possible
- Bounded size with backpressure
- Peek without removal for retry logic
- Efficient O(1) enqueue/dequeue operations

### 2.3 Connection State Manager

```
Connection State Machine
───────────────────────

    ┌─────────────┐
    │ Disconnected│◄────────────┐
    └──────┬──────┘             │
           │                    │ Connection Lost
      Connect                   │ (cleanup queues)
           │                    │
    ┌──────▼──────┐             │
    │  Connected  ├─────────────┘
    │ (enable send)│
    └──────┬──────┘
           │
     Health Check
           │
    ┌──────▼──────┐
    │   Healthy   │
    └─────────────┘
```

Each connection receives a unique ID to track connection changes and prevent cross-connection message confusion.

### 2.4 Backpressure Mechanisms

```
Backpressure Points
──────────────────

MCP Layer           Queue Layer          TCP Layer
─────────           ───────────          ─────────
Rate Limit ────►    Send Queue ────►    Socket Buffer
                    (bounded)            (TCP window)
                         │                    │
                         ▼                    ▼
                    Queue Full           Write Blocks
                         │                    │
                         ▼                    ▼
                   Reject/Wait          Stop Reading
                                       (backpressure)
```

## 3. Control Protocol Design

### 3.1 Health Monitoring

Since nREPL lacks built-in heartbeat, we implement health checks using an elegant anonymous function approach:

```
Anonymous Function Heartbeat Strategy
────────────────────────────────────
                    
Every 5 seconds ──► Send "((fn [] :pong))"
                           │
                           ▼
                    Wait 2 seconds
                           │
                    ┌──────┴──────┐
                    │             │
              Response ":pong"   Timeout/Error
                    │             │
                    ▼             ▼
              Mark Healthy   Increment Failures
                              │
                              ▼
                         >3 Failures?
                              │
                              ▼
                        Mark Unhealthy
```

**Key Advantages of `((fn [] :pong))` Heartbeat:**
- **Zero Setup Required**: No function definition needed per connection
- **Comprehensive Testing**: Tests anonymous function creation, invocation, and evaluation pipeline
- **Self-Contained**: Each heartbeat is completely independent
- **Minimal Overhead**: Small code payload (~18 characters)
- **Clear Verification**: Expected response is always `:pong`
- **No State Dependencies**: Cannot be affected by namespace changes or redefinitions

**Tested Response Pattern:**
```clojure
;; Request:
{:op "eval" :code "((fn [] :pong))" :id "heartbeat-123"}

;; Expected Response:
{:id "heartbeat-123" 
 :value ":pong"
 :status ["done"]
 :session "session-456"
 :ns "user"}
```

**Validation Results:**
```bash
# Test performed: 2025-01-10
MCP_SERVER_URL=http://localhost:3000/mcp python ./mcp_nrepl_client.py --eval "((fn [] :pong))"

# Response received:
{
  "content": [
    {
      "type": "text", 
      "text": ":pong"
    }
  ],
  "session": null,
  "namespace": "user-activate"
}

# ✅ Heartbeat test PASSED
# - Anonymous function created and executed successfully
# - Expected ":pong" response received
# - No setup required
# - Response size: ~80 bytes (very lightweight)
```

### 3.2 Flow Control Extensions

While not part of standard nREPL, the architecture supports optional flow control:

```
Application-Level Flow Control
──────────────────────────────

Pending Operations Count
         │
         ▼
    Threshold Check
         │
    ┌────┴────┐
    │         │
  Below     Above
    │         │
    ▼         ▼
  Accept    Reject
  Request   with Backpressure
```

## 4. Sync-Async Bridge Design

### 4.1 Operation Lifecycle

```
Operation Lifecycle
──────────────────

     send-async()
         │
         ▼
    Generate ID ──► Register Route
         │              │
         ▼              ▼
    Queue Message    Return Handle
         │              │
         ▼              ▼
    [Background]    [Immediate]
    Send & Route    {:message-id id
                     :status :pending}
                           │
                           ▼
                    ┌──────────────┐
                    │ Caller Choice│
                    └──────────────┘
                      /     |     \
                     /      |      \
              wait-for  check-status  cancel
              (blocks)  (non-blocking) (cleanup)
```

### 4.2 Timeout Behavior

```
Timeout Handling
───────────────

Request with 30s timeout
         │
         ▼
    Wait 30 seconds
         │
    ┌────┴────┐
    │         │
Complete   Timeout
    │         │
    ▼         ▼
Return    Return Timeout
Result    Keep Operation Running
          Message-ID Still Valid
                │
                ▼
          Can check-status
          or wait-for again
```

## 5. Example Code Patterns

### 5.1 Core Data Structures

```clojure
;; Simple atoms with built-in Clojure data structures
(defonce message-routes (atom {}))  ; Map: message-id -> handler
(defonce send-queue (atom clojure.lang.PersistentQueue/EMPTY))
(defonce connection-state (atom {:status :disconnected
                                 :connection-id nil
                                 :send-enabled false}))
```

### 5.2 Message Correlation Pattern

```clojure
(defn register-message [msg-id]
  (let [handler {:promise (promise)
                :created-at (System/currentTimeMillis)
                :status :pending}]
    (swap! message-routes assoc msg-id handler)
    handler))

(defn route-response [response]
  (when-let [handler (@message-routes (:id response))]
    (deliver (:promise handler) response)
    (when (contains? (:status response) "done")
      (swap! message-routes dissoc (:id response)))))
```

### 5.3 Backpressure Implementation

```clojure
(defn queue-with-backpressure [msg timeout-ms]
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (cond
        (< (count @send-queue) MAX-QUEUE-SIZE)
        (do (swap! send-queue conj msg)
            {:status :queued})
        
        (> (System/currentTimeMillis) deadline)
        {:error :queue-full :timeout true}
        
        :else
        (do (Thread/sleep 10)
            (recur))))))
```

### 5.4 Connection State Management

```clojure
(defn on-connection-lost []
  ;; Immediate state update
  (swap! connection-state assoc :status :disconnected :send-enabled false)
  
  ;; Clear queues
  (reset! send-queue clojure.lang.PersistentQueue/EMPTY)
  
  ;; Fail pending operations
  (doseq [[msg-id handler] @message-routes]
    (deliver (:promise handler) {:error :connection-lost}))
  (reset! message-routes {}))
```

### 5.5 Sync Facade Pattern

```clojure
(defn send-sync 
  "Synchronous convenience wrapper for simple cases"
  [op-map timeout-ms]
  (let [msg-id (generate-id)
        handler (register-message msg-id)]
    (queue-message (assoc op-map :id msg-id))
    (let [result (deref (:promise handler) timeout-ms :timeout)]
      (if (= result :timeout)
        {:error :timeout :message-id msg-id}
        result))))

(defn send-async 
  "Asynchronous operation for complex cases"
  [op-map]
  (let [msg-id (generate-id)]
    (register-message msg-id)
    (queue-message (assoc op-map :id msg-id))
    {:message-id msg-id :status :pending}))
```

### 5.6 Anonymous Function Heartbeat Implementation

```clojure
;; Heartbeat configuration
(def HEARTBEAT-CODE "((fn [] :pong))")
(def EXPECTED-HEARTBEAT ":pong")
(def HEARTBEAT-INTERVAL-MS 5000)
(def HEARTBEAT-TIMEOUT-MS 2000)

(defn send-heartbeat [connection]
  "Send anonymous function heartbeat with minimal overhead"
  (let [msg-id (str "heartbeat-" (System/currentTimeMillis))
        handler (register-message msg-id)]
    (queue-message {:op "eval" :code HEARTBEAT-CODE :id msg-id})
    (:promise handler)))

(defn verify-heartbeat-response [response]
  "Verify heartbeat returned expected pong response"
  (and (= EXPECTED-HEARTBEAT (:value response))
       (contains? (:status response) "done")))

(defn heartbeat-worker [conn-id]
  "Background worker for continuous health monitoring"
  (future
    (while (connection-active? conn-id)
      (Thread/sleep HEARTBEAT-INTERVAL-MS)
      (let [connection (get-connection conn-id)
            heartbeat-promise (send-heartbeat connection)
            response (deref heartbeat-promise HEARTBEAT-TIMEOUT-MS :timeout)]
        
        (cond
          ;; Successful heartbeat
          (verify-heartbeat-response response)
          (mark-connection-healthy conn-id)
          
          ;; Timeout - connection may be slow/dead
          (= response :timeout)
          (handle-heartbeat-timeout conn-id)
          
          ;; Unexpected response - server issues
          :else
          (handle-heartbeat-error conn-id response))))))

(defn handle-heartbeat-timeout [conn-id]
  "Handle heartbeat timeout - increment failure count"
  (let [failures (increment-failure-count conn-id)]
    (log/warn "Heartbeat timeout" {:connection-id conn-id :failures failures})
    (when (> failures 3)
      (mark-connection-unhealthy conn-id))))
```

## 6. Implementation Strategy

### 6.1 Development Phases

#### Phase 1: Core Infrastructure (Week 1)
- Implement basic message router with atom-based map
- Add PersistentQueue-based send queue
- Create connection state manager
- **Validation**: Use Clojure-enhanced tree-sitter MCP to analyze code structure
- **Quality**: Run clj-kondo after each component

#### Phase 2: Async Core (Week 2)
- Implement send-async operation
- Add response routing logic
- Create timeout watchers
- **Validation**: Tree-sitter to verify proper atom usage patterns
- **Quality**: clj-kondo for thread-safety analysis

#### Phase 3: Sync Facade (Week 3)
- Build send-sync wrapper
- Implement wait-for-completion
- Add check-status operation
- **Validation**: Tree-sitter to ensure consistent API patterns
- **Quality**: clj-kondo for public API consistency

#### Phase 4: Backpressure & Flow Control (Week 4)
- Add queue size limits
- Implement backpressure strategies
- Create health monitoring
- **Validation**: Tree-sitter to analyze control flow
- **Quality**: clj-kondo for error handling patterns

#### Phase 5: Production Hardening (Week 5)
- Add comprehensive error handling
- Implement connection recovery
- Create monitoring and metrics
- **Validation**: Full codebase analysis with tree-sitter
- **Quality**: Complete clj-kondo scan with strict settings

### 6.2 Testing Strategy

#### Unit Testing Approach
```clojure
;; Test each component in isolation
(deftest test-message-router
  ;; Test registration, routing, cleanup
  )

(deftest test-send-queue
  ;; Test enqueue, dequeue, backpressure
  )

(deftest test-connection-state
  ;; Test state transitions, cleanup
  )
```

#### Integration Testing
- Use controllable test nREPL server (from timeout implementation plan)
- Test timeout scenarios
- Verify backpressure behavior
- Validate connection recovery

#### Quality Gates
1. **Every Change**: Run clj-kondo immediately
2. **Every Component**: Analyze with tree-sitter MCP
3. **Every Commit**: Full test suite
4. **Every Phase**: Integration tests with real nREPL

### 6.3 Incremental Development Process

```
Development Cycle
────────────────

1. Write Component
       ↓
2. Tree-sitter Analysis
   - Verify structure
   - Check patterns
       ↓
3. Run clj-kondo
   - Fix warnings
   - Ensure quality
       ↓
4. Write Tests
       ↓
5. Run Test Suite
       ↓
6. Integration Test
       ↓
7. Document Changes
       ↓
8. Commit
```

### 6.4 Risk Mitigation

#### Technical Risks
- **Race Conditions**: Careful atom update patterns, extensive concurrent testing
- **Memory Leaks**: Timeout-based cleanup, monitoring of map sizes
- **Deadlocks**: No circular dependencies, timeout all operations
- **Connection Issues**: Robust state management, clear error reporting

#### Process Risks
- **Complexity Creep**: Stay minimal, resist adding features
- **Performance Issues**: Profile early, benchmark each phase
- **Integration Problems**: Test against multiple nREPL implementations

## 7. Success Criteria

### 7.1 Functional Requirements
- ✅ Zero infinite hangs
- ✅ Graceful timeout handling
- ✅ Connection recovery without message loss notification
- ✅ Backpressure prevents resource exhaustion
- ✅ Both sync and async interfaces work correctly

### 7.2 Performance Requirements
- ⚡ < 1ms overhead for message routing
- 📊 Support 100+ concurrent operations
- 💾 Memory usage bounded and predictable
- 🔄 No performance degradation over time

### 7.3 Quality Requirements
- 🧪 100% unit test coverage of core components
- ✅ Zero clj-kondo warnings
- 📖 Comprehensive documentation
- 🔍 Tree-sitter analyzable code structure

## 8. Step-by-Step Implementation Plan

### 8.1 Phase 1: Transport Layer Foundation
**Goal**: Implement `send-message-async` with timeout and queuing

**Deliverables**:
- `send-message-async` function with configurable timeouts
- Async response collection with promise-based handling
- Basic connection state management
- Unit tests for timeout scenarios

**Test Criteria**:
- Timeout after configurable delay (passed as parameter)
- Proper cleanup of pending operations on timeout
- Handles concurrent message sending
- Graceful error reporting

**Commit Milestone**: `feat: implement send-message-async with timeout handling`

### 8.2 Phase 2: Internal Layer Implementation  
**Goal**: Create `nrepl-raw-async-int` as pure nREPL interface

**Deliverables**:
- `nrepl-raw-async-int` function using `send-message-async`
- Message validation and error handling
- Response format standardization
- Integration tests with real nREPL server

**Test Criteria**:
- All nREPL operations work (`eval`, `doc`, `load-file`, etc.)
- Proper error messages for invalid operations
- Response format consistency
- Performance parity with existing `send-message`

**Commit Milestone**: `feat: implement nrepl-raw-async-int foundation`

### 8.3 Phase 3: MCP Layer Implementation
**Goal**: Create `nrepl-raw-async` MCP function with full protocol compliance

**Deliverables**:
- `nrepl-raw-async` MCP function with validation
- Optional timeout parameter (default: 30s) for AI control of long-running operations
- MCP-compliant error formatting
- Comprehensive parameter validation
- MCP integration tests

**Test Criteria**:
- MCP protocol compliance (validated with Claude Desktop)
- Input sanitization and validation
- Timeout parameter properly passed through layers
- Proper error messages in MCP format
- Full nREPL operation coverage

**Commit Milestone**: `feat: implement nrepl-raw-async MCP function`

### 8.4 Phase 4: Migration and Testing
**Goal**: Demonstrate backward compatibility and performance

**Deliverables**:
- Side-by-side comparison tests
- Performance benchmarks
- Migration strategy documentation  
- Rollback procedures

**Test Criteria**:
- All existing MCP functions continue working
- `nrepl-raw-async` provides equivalent functionality
- Performance within 10% of current implementation
- Memory usage stays bounded

**Commit Milestone**: `feat: complete async architecture with migration path`

### 8.5 Testing Strategy

**Unit Tests**:
- Timeout handling edge cases
- Message correlation correctness  
- Connection failure scenarios
- Memory leak prevention

**Integration Tests**:
- Real nREPL server interactions
- Claude Desktop MCP validation
- Concurrent operation handling
- Long-running operation management

**Performance Tests**:
- Response time distribution
- Memory usage over time
- Connection pool behavior
- Backpressure effectiveness

**Commit Tags**:
- `v0.x.0-async-transport` (Phase 1)
- `v0.x.0-async-internal` (Phase 2) 
- `v0.x.0-async-mcp` (Phase 3)
- `v0.x.0-async-complete` (Phase 4)

## 9. Conclusion

## 10. RUNTIME ENVIRONMENT CLARIFICATION (2025-01-10)

**IMPORTANT DISTINCTION**: This architecture document applies to the **MCP server runtime environment**, not test server environments.

### 10.1 Two Distinct Runtime Environments

**1. MCP Server Runtime (Babashka)** - **FULL ASYNC SUPPORT** ✅
- **Environment**: Main MCP server running in Babashka
- **Capabilities**: Complete Clojure async primitives available
- **Verified Available**:
  - `promise` and `deliver` - Promise coordination
  - `(deref promise timeout-ms :timeout)` - Promise timeouts  
  - `future` and `future-done?` - Background execution
  - `Thread/sleep` - Blocking operations
  - Java concurrency (`java.util.concurrent.*`)
  - `reify` with Java interfaces (`clojure.lang.IDeref`)

**2. Babashka nREPL Test Server (SCI Sandbox)** - **LIMITED SUPPORT** ❌
- **Environment**: Test nREPL server running in SCI sandbox
- **Limitations**: Restricted to basic synchronous operations
- **Missing**: `promise`, `future`, complex Java interop
- **Note**: This is only our **test server** - production nREPL servers have full capabilities

### 10.2 Verified Babashka Runtime Capabilities

**Test Results (2025-01-10)**:
```bash
# Promise coordination - WORKS
bb -e "(let [p (promise)] (deliver p :hello) (deref p))"
# => :hello

# Promise timeout - WORKS  
bb -e "(deref (promise) 100 :timeout)"
# => :timeout

# Future execution - WORKS
bb -e "(future (+ 1 2 3))" 
# => #object[clojure.core$future_call$reify__8578 ...]

# Future timeout - WORKS
bb -e "(let [f (future (Thread/sleep 100) :done)] (deref f 50 :timeout))"
# => :timeout

# Java concurrency - WORKS
bb -e "(import 'java.util.concurrent.CountDownLatch) (def latch (CountDownLatch. 1)) (.await latch 100 java.util.concurrent.TimeUnit/MILLISECONDS)"
# => true
```

### 10.3 Architectural Validation

**CONCLUSION**: The sophisticated async architecture proposed in sections 1-9 **IS VIABLE** for the MCP server implementation.

**Implementation approach**:
1. **MCP Server**: Use full async patterns (promises, futures, complex coordination)
2. **Test Strategy**: Use multiple nREPL servers, not just bb-nrepl-server
3. **Production**: Target full-featured nREPL servers (Clojure/JVM, Basilisp, etc.)

### 10.4 Testing Strategy Update

**Multi-Environment Testing**:
- **Unit tests**: Against Babashka nREPL (acknowledge SCI limitations)
- **Integration tests**: Against full Clojure nREPL servers
- **Production validation**: With real development environments

---

## 11. Conclusion (Reaffirmed)

This architecture provides a robust, simple, and maintainable solution to the sync-async impedance mismatch between MCP and nREPL. By leveraging Clojure's built-in persistent data structures and atoms, we achieve thread-safety without complex async frameworks. The design supports both simple synchronous usage patterns and sophisticated asynchronous operations, while maintaining clear separation of concerns and graceful degradation under load.

**The Babashka runtime provides full async capabilities**, enabling the complete implementation of the proposed architecture. The step-by-step implementation strategy, combined with continuous validation using tree-sitter and clj-kondo, ensures high code quality throughout development. This architecture forms the foundation for reliable, production-ready MCP-nREPL integration.

**Development should proceed with**:
1. Full async implementation using promises and futures
2. Comprehensive timeout handling with `(deref promise timeout-ms :timeout)`
3. Complex connection state management with atomic coordination
4. Multi-environment testing strategy

---

## 12. nREPL Session Architecture (January 2025)

### 12.1 Session Model Fundamentals

The nREPL protocol implements a unique session model that's critical to understand for proper async queue management:

#### The Default/Implicit Session
- **Always exists** - Created when the nREPL server starts
- **Has no ID** - Represented by omitting the session field or using nil/null
- **Shared state** - All connections without explicit session ID share this namespace
- **Cannot be closed** - Permanent for the server lifetime
- **Good for** - Quick REPL interactions, simple testing, shared utilities

#### Named Sessions (Created via Clone)
- **Created only via clone** - The `clone` operation is the ONLY way to create new sessions
- **Server-generated IDs** - The nREPL server creates and manages all session IDs
- **Isolated state** - Each session has independent namespaces and variables
- **Can be closed** - Using `{:op "close" :session "session-id"}`
- **Good for** - Parallel evaluations, user isolation, testing isolation

### 12.2 Session Creation Flow

```clojure
;; Step 1: Clone from default session (no session specified)
{:op "clone"
 :id "msg-123"}

;; Step 2: Server creates and returns new session
{:id "msg-123"
 :new-session "d3f4a2b1-8c9e-4f5a-b6e7-1a2b3c4d5e6f"  ; Server-generated UUID
 :status ["done"]}

;; Step 3: Use the new session for isolated evaluation
{:op "eval"
 :code "(def x 42)"
 :session "d3f4a2b1-8c9e-4f5a-b6e7-1a2b3c4d5e6f"
 :id "msg-124"}
```

### 12.3 Session ID Behavior Rules

#### Request Rules (Client → Server)
- **Omitting `:session`** → Uses default session
- **`:session nil`** → Also uses default session  
- **`:session "id"`** → Uses specific named session

#### Response Rules (Server → Client)
- **No `:session` field** → Operation executed in default session
- **`:session "id"` present** → Operation executed in that named session
- **Session ID always echoed** → If you send a session ID, it's returned in response

### 12.4 Session Isolation Example

```clojure
;; Default session - shared state
{:op "eval" :code "(def shared-var 1)" :id "1"}
;; → shared-var = 1 in default session

;; Create isolated session
{:op "clone" :id "2"}
;; → {:new-session "session-abc"}

;; Isolated session - no access to shared-var
{:op "eval" :code "shared-var" :session "session-abc" :id "3"}
;; → Error: Unable to resolve symbol: shared-var

;; Default session still has shared-var
{:op "eval" :code "shared-var" :id "4"}  ; No session = default
;; → Returns "1"

;; Different sessions can have same var names with different values
{:op "eval" :code "(def shared-var 99)" :session "session-abc" :id "5"}
;; → shared-var = 99 in session-abc, still 1 in default
```

### 12.5 Implications for Async Queue Architecture

The session model has important implications for our async message handling:

1. **Message Routing** - Must preserve session ID through entire async pipeline
2. **State Isolation** - Each session's state is independent, no cross-contamination
3. **Default Session Handling** - Absence of session ID is valid and means "use default"
4. **Session Lifecycle** - Must handle session closure gracefully in queued messages
5. **Error Handling** - Invalid session IDs will cause errors that need proper handling

### 12.6 Session ID Formats by Implementation

Different nREPL servers generate different session ID formats:
- **Clojure/JVM nREPL**: UUID strings like `"d3f4a2b1-8c9e-4f5a-b6e7-1a2b3c4d5e6f"`
- **Babashka nREPL**: Similar UUID format
- **CIDER nREPL**: May include additional metadata in the ID
- **Custom implementations**: May use any unique string format

Our architecture must handle any string format without assumptions about structure.

### 12.7 Best Practices for Session Management

1. **Use named sessions for isolation** - Create new sessions for independent work
2. **Clean up sessions** - Close sessions when done to free resources
3. **Track session ownership** - Know which operations belong to which session
4. **Handle session errors** - Gracefully handle "unknown session" errors
5. **Don't assume session format** - Treat session IDs as opaque strings

---

## 13. Data Format Mapping: JSON ↔ EDN ↔ Bencode (January 2025)

### 13.1 Three-Layer Data Transformation

Our MCP-nREPL bridge handles data transformations across three distinct formats:

1. **MCP Layer**: JSON (for MCP protocol compliance)
2. **Internal Layer**: EDN/Clojure data structures (for processing)
3. **nREPL Layer**: Bencode (wire protocol) containing EDN structures

### 13.2 Basic Type Mappings

| EDN (Clojure) | JSON | Notes |
|---------------|------|-------|
| `nil` | `null` | Represents absence/default |
| `true`/`false` | `true`/`false` | Boolean values |
| `42` | `42` | Numbers unchanged |
| `3.14` | `3.14` | Floats unchanged |
| `"hello"` | `"hello"` | Strings unchanged |
| `:keyword` | `"keyword"` | Keywords → string keys |
| `symbol` | `"symbol"` | Symbols → strings |

### 13.3 Collection Mappings

| EDN (Clojure) | JSON | Information Loss |
|---------------|------|------------------|
| `[1 2 3]` | `[1, 2, 3]` | None (vectors → arrays) |
| `{:a 1 :b 2}` | `{"a": 1, "b": 2}` | Keywords become strings |
| `#{1 2 3}` | `[1, 2, 3]` | Set uniqueness lost |
| `'(1 2 3)` | `[1, 2, 3]` | List type lost |

### 13.4 Complete Round-Trip Example

```javascript
// Step 1: MCP Client sends (JSON)
{
  "name": "nrepl-send-message-async",
  "arguments": {
    "message": {
      "op": "eval",
      "code": "(+ 1 2 3)",
      "session": "abc-123"
    },
    "timeout-ms": 30000
  }
}
```

```clojure
;; Step 2: Parse to Clojure/EDN (internal processing)
{:name "nrepl-send-message-async"
 :arguments {:message {:op "eval"
                       :code "(+ 1 2 3)"
                       :session "abc-123"}
             :timeout-ms 30000}}

;; Step 3: Extract and prepare nREPL message
{:op "eval"
 :code "(+ 1 2 3)"
 :session "abc-123"
 :id "019899d8-65bc-7000-8000-000046568110-msg"}  ; Add UUID

;; Step 4: Send to nREPL (EDN → Bencode wire format)
;; Bencode encoding of the above EDN structure
"d2:op4:eval4:code9:(+ 1 2 3)7:session7:abc-1232:id36:019899d8...e"

;; Step 5: Receive from nREPL (Bencode → EDN)
{:id "019899d8-65bc-7000-8000-000046568110-msg"
 :session "abc-123"
 :value "6"
 :ns "user"
 :status ["done"]}
```

```javascript
// Step 6: Convert to JSON for MCP response
{
  "id": "019899d8-65bc-7000-8000-000046568110-msg",
  "session": "abc-123",
  "value": "6",
  "ns": "user",
  "status": ["done"]
}
```

### 13.5 Code Implementation

```clojure
;; JSON → EDN (MCP input handling)
(defn parse-mcp-request [json-str]
  (json/parse-string json-str true))  ; true = keywordize keys
  ;; {"op": "eval"} becomes {:op "eval"}

;; EDN → Bencode (nREPL communication)
(defn send-to-nrepl [connection edn-message]
  (bencode/write-bencode (:out connection) edn-message))
  ;; {:op "eval" :code "(+ 1 2)"} → bencoded bytes

;; Bencode → EDN (nREPL response)
(defn read-from-nrepl [connection]
  (bencode/read-bencode (:in connection)))
  ;; bencoded bytes → {:value "3" :status ["done"]}

;; EDN → JSON (MCP output)
(defn format-mcp-response [edn-data]
  (json/generate-string edn-data {:pretty true}))
  ;; {:value "3"} becomes {"value": "3"}
```

### 13.6 Critical Conversion Points

#### Keywords vs Strings
- **EDN keywords** (`:session`) are symbolic identifiers in Clojure
- **JSON string keys** (`"session"`) are required by JSON spec
- **Cheshire library** handles this with `:keywordize` option
- **Direction matters**: JSON→EDN keywordizes, EDN→JSON stringifies

#### Nil/Null Session Handling
```clojure
;; All these represent "use default session":
{:op "eval" :code "(+ 1 2)"}           ; EDN - no :session key
{:op "eval" :code "(+ 1 2)" :session nil}  ; EDN - explicit nil
```
```javascript
{"op": "eval", "code": "(+ 1 2)"}      // JSON - no session key
{"op": "eval", "code": "(+ 1 2)", "session": null}  // JSON - explicit null
```

### 13.7 Layer-Specific Representations

#### MCP Layer (JSON)
- String keys required: `"op"`, `"code"`, `"session"`
- Arrays for sequences: `["done"]`
- Null for nil: `null`

#### Internal Processing (EDN/Clojure)
- Keyword keys preferred: `:op`, `:code`, `:session`
- Vectors for sequences: `["done"]`
- Nil for absence: `nil`

#### nREPL Protocol (EDN in Bencode)
- Keywords required: `:op`, `:code`, `:session`
- EDN structures preserved
- Bencode handles serialization

### 13.8 Common Pitfalls

1. **Mixing representations** - Be clear which layer you're documenting
2. **Assuming keyword preservation** - JSON has no keyword type
3. **Set semantics** - JSON arrays don't preserve set uniqueness
4. **Symbol handling** - Symbols become strings in JSON
5. **Metadata loss** - Clojure metadata not preserved in JSON

### 13.9 Best Practices

1. **Document the layer** - Always specify if showing JSON, EDN, or Bencode
2. **Use appropriate format** - JSON for MCP docs, EDN for nREPL docs
3. **Preserve types when possible** - But accept information loss
4. **Test round-trips** - Ensure data survives transformations
5. **Handle edge cases** - Nil/null, empty collections, special characters

---

## Appendix A: Dynamic Application Orchestration Pattern

### A.1 Revolutionary Discovery: Beyond Debugging

The implementation of `debug-load-file` and `debug-eval` tools has revealed a **transformative pattern** that extends far beyond debugging capabilities. This pattern enables **dynamic application orchestration** through live loading of domain-specific Clojure toolkits.

### A.2 The Dynamic Loading Pattern

**Core Mechanism:**
```clojure
;; Load specialized toolkit at runtime
(debug-load-file "vscode-automation-toolkit.clj")

;; Access specialized functions immediately
(vs/setup-development-workspace project-config)
(calva/jack-in-with-deps deps-map)
(vs/run-full-test-cycle)
```

**Key Innovation:** SCI's `slurp` and `eval` capabilities allow loading external Clojure code that persists in the debugging environment, creating **live, extensible control interfaces**.

### A.3 Universal Application Conductor Architecture

```
┌─────────────────────────────────────────┐
│           Claude Code (AI)              │
│  ┌─────────────────────────────────┐    │
│  │     MCP-nREPL Bridge            │    │
│  │  - debug-load-file             │    │  Dynamic
│  │  - debug-eval                  │    │  Toolkit
│  │  - Persistent SCI environment  │    │  Loading
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
                   │
                   │ nREPL Protocol  
                   ▼
┌─────────────────────────────────────────┐
│     Target Application (e.g. VS Code)  │
│  ┌─────────────────────────────────┐    │
│  │  Dynamic Toolkit Library        │    │
│  │  - workspace-management.clj     │    │
│  │  - development-workflows.clj    │    │
│  │  - deployment-automation.clj    │    │
│  │  - integration-orchestration.clj│    │
│  └─────────────────────────────────┘    │
│  ┌─────────────────────────────────┐    │
│  │  Application API Integration    │    │
│  │  - VS Code API (via Joyride)   │    │
│  │  - Git operations              │    │
│  │  - Docker management           │    │
│  │  - Cloud deployment APIs       │    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
```

### A.4 Transformative Use Cases

#### A.4.1 VS Code + Joyride Orchestration
```clojure
;; vscode-project-automation.clj
(defn setup-clojure-project [project-name]
  (vs/create-workspace project-name)
  (vs/install-extensions ["betterthantomorrow.calva"])
  (calva/configure-repl {:aliases [:dev :test]})
  (git/init-repo-with-gitignore)
  (vs/open-terminal-and-run "lein new app " project-name))

(defn full-development-cycle []
  (calva/jack-in)
  (vs/run-all-tests)
  (when (tests-passed?)
    (git/commit-and-push)
    (docker/build-and-deploy)
    (vs/show-notification "✅ Deployed to staging!")))
```

#### A.4.2 Multi-Application Orchestration
```clojure
;; deployment-pipeline.clj
(defn deploy-microservice [service-config]
  (git/checkout (:branch service-config))
  (vs/run-build-for-service service-config)
  (docker/build-image service-config)
  (k8s/deploy-to-cluster service-config)
  (monitoring/setup-alerts service-config)
  (slack/notify-team (deployment-success-message service-config)))
```

### A.5 Architectural Implications

#### A.5.1 From Static to Dynamic
**Traditional MCP:**
- Static tool definitions
- Fixed functionality
- Server restart required for changes

**Dynamic MCP-nREPL:**
- **Live toolkit loading** - Add capabilities without restart
- **Domain-specific libraries** - Specialized function collections
- **Iterative development** - Test and refine in real-time

#### A.5.2 AI-Driven Application Management
This pattern enables Claude Code to become a **universal application conductor** that can:

1. **Learn application patterns** by loading domain-specific toolkits
2. **Execute complex workflows** impossible with static tools
3. **Iterate and improve** automation scripts based on results
4. **Compose multi-application orchestrations** (IDE + Git + Docker + Cloud)

### A.6 Toolkit Library Ecosystem

**Proposed Structure:**
```
dynamic-toolkits/
├── editors/
│   ├── vscode-automation.clj
│   ├── emacs-integration.clj  
│   └── intellij-workflows.clj
├── development/
│   ├── git-workflows.clj
│   ├── testing-automation.clj
│   └── ci-cd-pipelines.clj
├── infrastructure/
│   ├── docker-orchestration.clj
│   ├── kubernetes-management.clj
│   └── cloud-deployment.clj
├── project-types/
│   ├── clojure-project-setup.clj
│   ├── react-spa-automation.clj
│   └── microservice-templates.clj
└── integrations/
    ├── slack-notifications.clj
    ├── jira-automation.clj
    └── monitoring-setup.clj
```

### A.7 Revolutionary Impact

This pattern transforms MCP from **static tool calling** to **dynamic application programming**, creating:

- **Live coding interfaces** for any nREPL-enabled application
- **AI-assisted application orchestration** with domain expertise
- **Extensible automation frameworks** that evolve with needs
- **Composable workflow libraries** for complex multi-step operations

### A.8 Future Research Directions

1. **Toolkit standardization** - Common patterns for application control
2. **Security frameworks** - Safe execution of dynamic code
3. **Toolkit discovery** - Automatic loading based on application context
4. **Cross-application protocols** - Standardized orchestration patterns
5. **AI learning loops** - Claude Code improving toolkits based on usage

This discovery positions the MCP-nREPL bridge as a **foundational technology** for AI-driven application ecosystem management, where intelligent agents can dynamically adapt and extend their capabilities through live code loading.