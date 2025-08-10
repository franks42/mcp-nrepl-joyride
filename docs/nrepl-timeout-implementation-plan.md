# nREPL Timeout Implementation Plan

## Overview

**Goal**: Implement robust timeout and queuing mechanisms for nREPL operations through incremental, bite-size deliverables that are individually testable.

**Strategy**: Build from simple synchronous timeout → basic async queuing → full production system

## 🚨 Critical Success Factors

1. **No Big Bang** - Each step must be fully functional and testable
2. **Backward Compatibility** - Existing MCP functions must continue working
3. **Incremental Value** - Each step solves real problems immediately  
4. **Race Condition Prevention** - Careful state management and testing
5. **Clear Rollback Strategy** - Each step can be reverted if issues arise

## 📋 Implementation Phases

### **Phase 1: Foundation - Simple Timeout (Week 1)**
*Goal: Stop infinite hangs with minimal complexity*

#### **Step 1.1: Basic Timeout Infrastructure** ⏱️ *2 hours*
```clojure
;; Add simple timeout to existing collect-responses
(defn collect-responses-with-timeout [in message-id timeout-ms]
  "Simple timeout wrapper around existing logic"
  (let [result-promise (promise)
        timeout-future (future 
                         (Thread/sleep timeout-ms)
                         (deliver result-promise :timeout))
        work-future (future 
                      (deliver result-promise 
                               (collect-responses in message-id)))]
    (let [result @result-promise]
      (future-cancel timeout-future)
      (future-cancel work-future)
      (if (= result :timeout)
        {:error "timeout" :message-id message-id}
        result))))
```

**Test Plan:**
- ✅ Normal operation completes within timeout
- ✅ Long operation times out correctly
- ✅ Resources cleaned up after timeout
- ✅ Existing MCP functions still work

**Success Criteria:**
- No more infinite hangs
- All existing tests pass
- New timeout tests pass

#### **Step 1.2: Configurable Timeouts** ⏱️ *1 hour*
```clojure
;; Add timeout parameter to all MCP functions
(defn tool-nrepl-eval [{:keys [code timeout] :or {timeout 30000}}]
  ;; Use collect-responses-with-timeout with user-specified timeout
  )
```

**Test Plan:**
- ✅ Default timeout works
- ✅ Custom timeout respected
- ✅ Invalid timeout handled gracefully

#### **Step 1.3: Connection Health Detection** ⏱️ *2 hours*
```clojure
;; Detect if connection is alive before operations
(defn connection-healthy? [conn]
  (try
    (collect-responses-with-timeout 
      (:in conn) 
      (send-describe-message (:out conn)) 
      5000)
    true
    (catch Exception _ false)))
```

**Test Plan:**
- ✅ Healthy connections detected
- ✅ Dead connections detected
- ✅ Appropriate error messages

**Deliverable**: Working timeout system that prevents hangs
**Risk**: Low - simple changes, easy to revert

---

### **Phase 2: Message Routing - Concurrent Safety (Week 2)**  
*Goal: Support multiple concurrent operations*

#### **Step 2.1: Message ID Routing Map** ⏱️ *3 hours*
```clojure
;; Add routing table for message responses
(defonce message-routes (atom {})) ; message-id -> promise

(defn register-message-route [message-id]
  (let [result-promise (promise)]
    (swap! message-routes assoc message-id result-promise)
    result-promise))

(defn deliver-message-response [message-id response]
  (when-let [result-promise (@message-routes message-id)]
    (deliver result-promise response)
    (swap! message-routes dissoc message-id)))
```

**Test Plan:**
- ✅ Single message routing works
- ✅ Multiple concurrent messages work
- ✅ Message cleanup after completion
- ✅ Memory leak prevention

#### **Step 2.2: Background Reader Thread** ⏱️ *4 hours*
```clojure
;; Single reader thread routes all responses
(defn start-message-router [input-stream]
  (async/thread
    (while @router-running
      (when-let [response (bencode/read-bencode input-stream)]
        (when-let [msg-id (:id response)]
          (deliver-message-response msg-id response))))))
```

**Test Plan:**
- ✅ Reader thread starts/stops cleanly
- ✅ Messages routed to correct handlers
- ✅ Reader survives individual message errors
- ✅ No message loss under load

#### **Step 2.3: Concurrent Operation Support** ⏱️ *2 hours*
```clojure
;; Multiple operations can be in-flight simultaneously
(defn concurrent-nrepl-eval [requests]
  "Test concurrent operations"
  (pmap tool-nrepl-eval requests))
```

**Test Plan:**
- ✅ Multiple eval operations in parallel
- ✅ No message mixing or corruption
- ✅ All operations complete successfully
- ✅ Timeout handling works under concurrency

**Deliverable**: Concurrent-safe message routing
**Risk**: Medium - concurrency bugs possible, needs thorough testing

---

### **Phase 3: Send Queue Protection (Week 3)**
*Goal: Prevent send operations from hanging*

#### **Step 3.1: Send Queue Infrastructure** ⏱️ *4 hours*
```clojure
;; Add send queue with backpressure protection
(defonce send-queue (async/chan 100))  ; Bounded queue
(defonce sender-running (atom true))

(defn start-send-worker [output-stream]
  (async/thread
    (while @sender-running
      (when-let [send-request (async/<!! send-queue)]
        (try
          (send-nrepl-message output-stream send-request)
          (catch Exception e
            (handle-send-error send-request e)))))))
```

**Test Plan:**
- ✅ Send queue processes messages in order
- ✅ Backpressure prevents memory explosion
- ✅ Send errors handled gracefully
- ✅ Queue drains completely on shutdown

#### **Step 3.2: Send Timeout Protection** ⏱️ *3 hours*
```clojure
;; Timeout individual send operations
(defn queue-send-with-timeout [message timeout-ms]
  (let [result-promise (promise)
        send-request {:message message 
                      :result-promise result-promise
                      :timeout timeout-ms}]
    (if (async/>!! send-queue send-request)
      (deref result-promise timeout-ms :send-timeout)
      :queue-full)))
```

**Test Plan:**
- ✅ Normal sends complete quickly
- ✅ Slow sends timeout correctly
- ✅ Queue full detection works
- ✅ Resources cleaned up after timeout

#### **Step 3.3: End-to-End Integration** ⏱️ *3 hours*
```clojure
;; Integrate send queue with message routing
(defn nrepl-raw-internal [message timeout-ms]
  "First version of the core function"
  (let [send-result (queue-send-with-timeout message (quot timeout-ms 3))
        receive-result (wait-for-response (:id message) 
                                         (- timeout-ms send-time))]
    ;; Combine results
    ))
```

**Test Plan:**
- ✅ End-to-end operation works
- ✅ Send and receive timeouts coordinate
- ✅ Error cases handled properly
- ✅ Performance acceptable

**Deliverable**: Send operations cannot hang indefinitely  
**Risk**: Medium - coordination between send/receive queues

---

### **Phase 4: Interrupt Recovery (Week 4)**
*Goal: Handle timeout situations gracefully*

#### **Step 4.1: Interrupt Message Support** ⏱️ *3 hours*
```clojure
;; Add interrupt capability
(defn send-interrupt [message-id session timeout-ms]
  (let [interrupt-msg {:op "interrupt" 
                       :session session
                       :interrupt-id message-id}]
    (nrepl-raw-internal interrupt-msg timeout-ms)))
```

**Test Plan:**
- ✅ Interrupt messages sent correctly
- ✅ Interrupt responses received
- ✅ Stuck operations actually interrupted
- ✅ Clean state after interrupt

#### **Step 4.2: Timeout Recovery Sequence** ⏱️ *4 hours*
```clojure
;; Implement timeout → interrupt → recovery pattern
(defn handle-operation-timeout [message-id session partial-responses]
  (println "Operation timed out, sending interrupt...")
  (let [interrupt-result (send-interrupt message-id session 5000)]
    (if (:success interrupt-result)
      {:error "timeout-interrupted" :responses partial-responses}
      {:error "interrupt-failed" :nuclear-recovery-required true})))
```

**Test Plan:**
- ✅ Timeout triggers interrupt sequence
- ✅ Interrupt success recovers gracefully
- ✅ Interrupt failure triggers recovery
- ✅ Nuclear recovery protects system

#### **Step 4.3: Connection Recovery** ⏱️ *3 hours*
```clojure
;; Handle completely unresponsive connections
(defn nuclear-connection-recovery [conn]
  "Last resort: restart connection"
  (close-connection conn)
  (create-fresh-connection))
```

**Test Plan:**
- ✅ Connection restart works
- ✅ All pending operations notified
- ✅ New connection fully functional
- ✅ Recovery process logged clearly

**Deliverable**: Robust timeout recovery with multiple fallback strategies
**Risk**: High - complex state management, needs extensive testing

---

### **Phase 5: Production Hardening (Week 5)**
*Goal: Production-ready reliability and monitoring*

#### **Step 5.1: Comprehensive Testing** ⏱️ *6 hours*
```clojure
;; Extensive test suite covering all failure modes
(deftest test-concurrent-timeout-recovery
  "Test multiple operations timing out simultaneously")

(deftest test-network-partition-recovery
  "Test behavior during network issues")

(deftest test-memory-leak-prevention  
  "Test long-running stability")
```

**Test Plan:**
- ✅ All happy path scenarios
- ✅ All timeout scenarios  
- ✅ All error scenarios
- ✅ Race condition testing
- ✅ Memory leak testing
- ✅ Performance benchmarking

#### **Step 5.2: Monitoring and Metrics** ⏱️ *4 hours*
```clojure
;; Add operational metrics
(defonce metrics (atom {:operations-total 0
                        :operations-success 0
                        :operations-timeout 0
                        :operations-error 0
                        :queue-depth 0}))
```

**Test Plan:**
- ✅ Metrics collected accurately
- ✅ Performance monitoring works
- ✅ Health status reporting
- ✅ Debug information available

#### **Step 5.3: Documentation and Migration** ⏱️ *2 hours*
```clojure
;; Migrate all existing MCP functions to use nrepl-raw-internal
(defn tool-nrepl-eval [{:keys [code timeout session] :or {timeout 30000}}]
  (nrepl-raw-internal {:op "eval" :code code} timeout session))
```

**Test Plan:**
- ✅ All existing MCP functions migrated
- ✅ Backward compatibility maintained
- ✅ Performance improved or maintained
- ✅ Documentation updated

**Deliverable**: Production-ready system with monitoring
**Risk**: Low - mostly integration and testing

---

## 🧪 Testing Strategy

### **Controllable Test Server Research Findings**

**Initial Assessment**: Investigated using Babashka nREPL server as controlled test target.

**Critical Limitations Discovered**:
- **No Interrupt Support**: GraalVM compilation removes interrupt capability completely
- **No Middleware Access**: `babashka.nrepl.middleware` not exposed in current Babashka version
- **Limited Session Isolation**: "Fake" sessions without true isolation 
- **Simplified Architecture**: Designed for development, not complex failure simulation
- **No Runtime Control**: Cannot dynamically modify server behavior during operation

**Recommendation**: **Custom Test Server Approach** (implemented in `/tmp/controllable_test_server_guide.md`)

**Custom Test Server Capabilities**:
```clojure
;; Advanced failure mode simulation
(defonce test-config 
  (atom {:delay-ms 0                    ; Response delay
         :simulate-hang false           ; Never send "done" 
         :network-partition false       ; Drop all responses
         :drop-probability 0.0          ; Random message dropping
         :corrupt-responses false       ; Send malformed data
         :memory-pressure false         ; Simulate OOM
         :connection-errors false       ; Socket errors
         :partial-responses false       ; Incomplete messages
         :cascade-failures false}))     ; Complex failure sequences

;; Complex failure scenarios
(def control-operations
  {"test-slow-death"     #(gradual-slowdown 5000 60000)      ; Gradually slower
   "test-memory-leak"    #(simulate-memory-pressure true)     ; Memory exhaustion
   "test-connection-flap" #(connection-flapping 10)          ; Intermittent failures  
   "test-cascade-failure" #(cascade-failure-sequence)        ; Multiple failure modes
   "test-recovery-stress" #(recovery-stress-test 100)})      ; Rapid failure/recovery
```

**Benefits of Custom Server**:
1. **Complete Control**: Every failure mode precisely simulated
2. **Deterministic Testing**: Same inputs → same failure patterns
3. **Complex Scenarios**: Cascading failures (slow→hang→partition→recovery)
4. **Performance Baseline**: Accurate timeout system overhead measurement
5. **Production Validation**: Test against realistic nREPL behavior patterns

### **Test Scenarios by Failure Mode**

#### **Phase 1 Testing: Basic Timeouts**
```bash
# Test normal operation
bb test-server normal && bb test-timeout-basic

# Test slow responses (should complete)
bb test-server slow-5s && bb test-timeout-slow  

# Test hanging operation (should timeout)
bb test-server hang && bb test-timeout-hang

# Test very slow (should timeout gracefully)
bb test-server slow-60s && bb test-timeout-very-slow
```

#### **Phase 2 Testing: Message Routing**  
```bash
# Test concurrent operations
bb test-server normal && bb test-concurrent-routing

# Test message routing under delay
bb test-server slow-2s && bb test-concurrent-with-delay

# Test routing with some hanging operations  
bb test-server mixed-hang && bb test-routing-partial-hang
```

#### **Phase 3 Testing: Send Queue**
```bash
# Test send queue backpressure
bb test-server normal && bb flood-test-send-queue

# Test send timeouts
bb test-server network-partition && bb test-send-timeout

# Test queue recovery after network issues
bb test-server unreliable-30% && bb test-send-recovery
```

#### **Phase 4 Testing: Interrupt Recovery**
```bash
# Test interrupt sequence
bb test-server hang && bb test-interrupt-recovery

# Test interrupt timeout
bb test-server network-partition && bb test-interrupt-timeout

# Test nuclear recovery
bb test-server total-failure && bb test-nuclear-recovery
```

### **Test Server Control Commands**

```bash
# Start controllable test server
bb start-test-nrepl-server --port 7890 --control-port 7891

# Control test scenarios via HTTP
curl -X POST http://localhost:7891/scenario/slow-response
curl -X POST http://localhost:7891/scenario/hanging-operation  
curl -X POST http://localhost:7891/scenario/network-partition
curl -X POST http://localhost:7891/scenario/reset

# Or via nREPL commands
bb test-control --scenario slow-5s
bb test-control --scenario hang  
bb test-control --scenario reset
```

### **Per-Phase Testing Requirements**

Each phase has **specific test requirements** that must pass before proceeding:

1. **Unit Tests** - Individual functions work correctly
2. **Integration Tests** - Components work together  
3. **Failure Mode Tests** - Controlled server failure scenarios
4. **Load Tests** - Performance under stress
5. **Race Condition Tests** - Concurrency safety

### **Controlled Failure Testing Matrix**

| Test Scenario | Server Behavior | Expected Client Behavior |
|---------------|-----------------|---------------------------|
| `normal` | Standard responses | All operations succeed |
| `slow-2s` | 2s delay per response | Operations complete slower |
| `slow-30s` | 30s delay per response | Operations timeout gracefully |
| `hang` | Never send "done" status | Timeout → interrupt → recovery |
| `network-partition` | No responses | Send timeout → connection error |
| `unreliable-30%` | 30% message drop rate | Retry → eventual success |
| `corrupt-responses` | Malformed responses | Parse error → connection reset |
| `mixed-issues` | Multiple failure modes | Complex recovery scenarios |

### **Test Isolation and Automation**

Each step can be tested independently with controlled failure modes:
```bash
# Automated test suite with server control
bb test-phase-1 --scenarios normal,slow-5s,hang,network-partition
bb test-phase-2 --scenarios normal,slow-2s,mixed-issues
bb test-phase-3 --scenarios normal,network-partition,unreliable-30%
bb test-phase-4 --scenarios hang,network-partition,total-failure

# Individual scenario testing  
bb test-timeout-basic --server-scenario slow-30s
bb test-message-routing --server-scenario unreliable-30%
bb test-send-queue --server-scenario network-partition
bb test-interrupt-recovery --server-scenario hang
```

### **Benefits of Controlled Test Server**
1. **Deterministic Testing** - Exact control over failure scenarios
2. **Reproducible Bugs** - Same failure mode every time  
3. **Edge Case Coverage** - Test rare but critical failure modes
4. **Performance Baseline** - Measure overhead of timeout system
5. **Integration Validation** - Test against real nREPL server behavior

## 🛡️ Risk Mitigation

### **High Risk Items**
1. **Phase 4: Interrupt Recovery** - Complex state management
   - *Mitigation*: Extensive testing, simple fallback strategies
   
2. **Phase 2: Message Routing** - Concurrency bugs
   - *Mitigation*: Thread-safe data structures, thorough race condition testing

### **Rollback Strategy**
Each phase can be reverted independently:
```clojure
;; Feature flags for gradual rollout
(def enable-send-queue? (get-env "ENABLE_SEND_QUEUE" false))
(def enable-interrupt-recovery? (get-env "ENABLE_INTERRUPT" false))
```

### **Progressive Deployment**
- Deploy behind feature flags
- Test with subset of operations first
- Monitor metrics closely
- Gradual rollout to all operations

## 📊 Success Metrics

### **Phase Completion Criteria**
- [ ] **Phase 1**: No infinite hangs, configurable timeouts
- [ ] **Phase 2**: Concurrent operations work safely  
- [ ] **Phase 3**: Send operations cannot hang
- [ ] **Phase 4**: Graceful timeout recovery
- [ ] **Phase 5**: Production monitoring and reliability

### **Overall Success**
- Zero infinite hangs in production
- <100ms latency overhead from timeout system
- >99% operation success rate  
- Complete backward compatibility
- Comprehensive monitoring and alerting

## 🚀 Implementation Timeline

**Week 1**: Phase 1 - Foundation (7 hours)
**Week 2**: Phase 2 - Message Routing (9 hours)  
**Week 3**: Phase 3 - Send Queue (10 hours)
**Week 4**: Phase 4 - Interrupt Recovery (10 hours)
**Week 5**: Phase 5 - Production Hardening (12 hours)

**Total**: ~48 hours over 5 weeks

## 🎯 Next Steps

1. **Get approval** for this incremental approach
2. **Start Phase 1** with simple timeout implementation
3. **Test thoroughly** at each step
4. **Document lessons learned** for each phase
5. **Adjust plan** based on real implementation experience

**Key Principle**: Each step must provide immediate value while building toward the complete solution. No step should be so complex that it can't be completed and tested in isolation.