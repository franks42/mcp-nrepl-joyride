# nREPL Protocol Implementation Details

## Overview

This document captures our current nREPL protocol implementation choices, limitations, and potential issues. These decisions significantly impact system behavior and reliability.

## Current Implementation: Synchronous Wrapper

### Architecture

We implement a **synchronous, blocking wrapper** over nREPL's asynchronous protocol:

```clojure
(defn send-message [conn message]
  (let [msg-with-id (assoc message :id (generate-id))]  ; Add unique ID
    (bencode/write-bencode out msg-with-id)             ; Send message
    (collect-responses in (:id msg-with-id))))          ; BLOCK until done
```

**⚠️ CRITICAL: This is a sync wrapper with NO timeout that will implement default timeout + interrupt pattern when timeout mechanism is added.**

**Design Decision:** When timeout occurs:
1. **Send interrupt message** to server for the timed-out operation
2. **Wait for interrupt acknowledgment** to clear socket queue
3. **Return timeout error** to MCP caller

This prevents socket stream corruption from orphaned responses.

### Message Correlation

**How it works:**
- Each outgoing message gets a unique ID via `(generate-id)`
- `collect-responses` only collects responses with matching ID
- Responses are merged into a single result

**Example flow:**
```
→ {:op "eval" :code "(+ 1 2)" :id "abc-123"}
← {:id "abc-123" :ns "user" :value "3"}
← {:id "abc-123" :status ["done"]}
→ Returns merged: {:id "abc-123" :ns "user" :value "3" :status ["done"]}
```

## Critical Limitations

### 1. ⚠️ **NO TIMEOUT MECHANISM**

**Current behavior:**
```clojure
(defn- collect-responses [in message-id]
  (loop [responses []]
    ; This will block FOREVER if no "done" status received!
    (let [raw-response (bencode/read-bencode in)]  ; BLOCKING READ
      ...
      (if (some #(= "done" %) status)
        responses
        (recur ...)))))  ; Keep waiting indefinitely
```

**Risks:**
- Infinite hangs on: `(Thread/sleep Long/MAX_VALUE)`
- Server crashes without sending "done"
- Network disconnections
- Malformed responses missing status

**Impact:** Entire MCP server becomes unresponsive!

### 2. **No Concurrent Operations**

**Current:** Sequential message processing only
```clojure
(send-message conn msg1)  ; Blocks...
(send-message conn msg2)  ; Can't send until msg1 completes
```

**Missing:** True async multiplexing that nREPL supports
```clojure
; What we CAN'T do:
(async-send msg1)  ; Long operation
(async-send msg2)  ; Quick operation completes first
```

### 3. **No Streaming Support**

**Current:** Collect all responses before returning
```clojure
; User sees nothing until completely done:
(eval "(doseq [i (range 10)] (println i) (Thread/sleep 1000))")
; ... waits 10 seconds ...
; Returns all output at once
```

**Missing:** Progressive output as it arrives
```clojure
; What users expect:
0  ; appears immediately
1  ; after 1 second
2  ; after 2 seconds
...
```

### 4. **No Interrupt Recovery**

If a message is interrupted or fails:
- No way to skip waiting for "done"
- No cleanup of pending message state
- Connection might be left in inconsistent state

## Session vs Message Concepts

### Message IDs
- **Purpose:** Correlate request/response pairs
- **Scope:** Per-message, temporary
- **Format:** UUID string
- **Lifecycle:** Created on send, discarded after "done"

### Session IDs
- **Purpose:** Maintain REPL state across evaluations
- **Scope:** Connection-wide, persistent
- **Format:** UUID string from server
- **Lifecycle:** Created via "clone" op, closed via "close" op

**Example:**
```clojure
; Same session, different messages
{:op "eval" :code "(def x 5)" :session "sess-1" :id "msg-1"}
{:op "eval" :code "x"         :session "sess-1" :id "msg-2"}  ; Sees x = 5

; Different session, can't see x
{:op "eval" :code "x"         :session "sess-2" :id "msg-3"}  ; Error: x not found
```

## Why These Choices Work (For Now)

### Advantages

1. **Simplicity** - No callback/promise complexity
2. **MCP compatibility** - Request/response model matches
3. **Predictable** - Easy to reason about and debug
4. **Error locality** - Errors tied to specific requests

### Acceptable Trade-offs

1. **AI usage pattern** - Sequential operations typical
2. **Interactive speed** - Most operations < 1 second
3. **Limited concurrency needs** - Single user, single connection
4. **Recovery via restart** - MCP server restart clears hangs

## Required Improvements

### Priority 1: Add Timeout (CRITICAL)

```clojure
(defn- collect-responses-with-timeout [in message-id timeout-ms]
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop [responses []]
      (if (> (System/currentTimeMillis) deadline)
        (throw (ex-info "nREPL response timeout" 
                        {:message-id message-id :timeout timeout-ms}))
        ; ... existing logic
        ))))
```

### Priority 2: Interrupt Handling

```clojure
; Add ability to interrupt stuck operations
(defn interrupt-pending-operation [conn]
  (send-message conn {:op "interrupt" :session current-session}))
```

### Priority 3: Connection Health Monitoring

```clojure
; Detect dead connections before hanging
(defn connection-alive? [conn]
  (try
    (with-timeout 1000
      (send-message conn {:op "describe"}))
    true
    (catch Exception _ false)))
```

## Future Considerations

### True Async Support

For better performance and UX:
- Message queue with callbacks
- Streaming results handler
- Concurrent operation support
- Progress indicators for long operations

### Connection Pooling

For reliability:
- Multiple connections to same server
- Automatic failover
- Load distribution
- Connection recycling

## Testing Scenarios

### Timeout Testing
```clojure
; This will hang forever in current implementation!
(nrepl-eval {:code "(Thread/sleep 1000000)"})
```

### Interrupt Testing
```clojure
; Start long operation
(nrepl-eval {:code "(loop [] (recur))"})
; Try to interrupt (currently difficult)
(nrepl-interrupt {})
```

### Session Isolation Testing
```clojure
; Verify sessions are independent
(let [s1 (nrepl-new-session {})
      s2 (nrepl-new-session {})]
  (nrepl-eval {:code "(def x 1)" :session s1})
  (nrepl-eval {:code "x" :session s1})  ; Should work
  (nrepl-eval {:code "x" :session s2})) ; Should fail
```

## Conclusion

Our synchronous implementation is **simple but fragile**. The lack of timeouts is a **critical issue** that can hang the entire MCP server. While the current approach works for typical AI assistant usage patterns, production deployment requires at minimum:

1. **Timeout mechanism** (CRITICAL)
2. **Interrupt recovery** (HIGH)
3. **Connection monitoring** (MEDIUM)

The synchronous model is acceptable for now but limits performance and reliability. Future versions should consider gradual migration to async operations for improved robustness.

---

*Last updated: 2024-01-08*
*This document should be updated when implementation changes are made.*