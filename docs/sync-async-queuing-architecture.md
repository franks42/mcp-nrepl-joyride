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

This architecture provides a robust, simple, and maintainable solution to the sync-async impedance mismatch between MCP and nREPL. By leveraging Clojure's built-in persistent data structures and atoms, we achieve thread-safety without complex async frameworks. The design supports both simple synchronous usage patterns and sophisticated asynchronous operations, while maintaining clear separation of concerns and graceful degradation under load.

The step-by-step implementation strategy, combined with continuous validation using tree-sitter and clj-kondo, ensures high code quality throughout development. This architecture forms the foundation for reliable, production-ready MCP-nREPL integration.