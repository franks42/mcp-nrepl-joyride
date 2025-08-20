# Multi-Connection Refactoring Plan

**Created:** 2025-01-19  
**Baseline Commit:** d7c7057 (feat: Implement nrepl-load-file tool and enhance stderr capture)  
**Estimated Effort:** 5-7 days  
**Risk Level:** High - Core architecture change  

## 🎯 Goal

Transform the current single-connection architecture to support multiple concurrent nREPL connections with per-connection queue management and no ambiguity in connection selection.

## 🏗️ Architecture Changes Overview

### Current State (Single Connection)
- One global message queue
- One global result queue  
- Single active connection
- Global watchers

### Target State (Multi-Connection)
- Per-connection message queues
- Per-connection result queues
- No "active" connection concept
- Per-connection watchers
- Connection resolution: explicit or single-only

## 📋 Implementation Phases - Interface-First Strategy

### Phase 1: Add Connection Parameter to Tool Interfaces  
**Risk:** Low | **Testing:** All existing functionality preserved with enhanced interface

#### 1.1 Implement Connection Resolution (Single Connection Logic)
**File:** `src/nrepl_mcp_server/state/connection.clj`

```clojure
(defn resolve-connection-id [user-identifier]
  "Resolve connection parameter to connection-id. Phase 1: Single connection logic."
  (let [active-conn (get-active-connection)]
    (cond
      ;; No identifier provided - use single connection rule
      (nil? user-identifier)
      (if active-conn 
        (:connection-id active-conn)
        (throw (ex-info "No connections available" {:status :no-connections})))
      
      ;; Identifier provided - for now, just validate it matches current connection
      :else
      (if active-conn
        (:connection-id active-conn) 
        (throw (ex-info "Connection not found" {:identifier user-identifier}))))))
```

**Benefits:**
- ✅ **Zero backend changes** - Uses existing single connection infrastructure
- ✅ **Interface testing** - Can test connection parameter parsing immediately
- ✅ **Safe implementation** - No complex queue changes yet

#### 1.2 Add Connection Parameter to All nREPL Tools

**Files to update:**
- `src/nrepl_mcp_server/mcp_server/tools/nrepl_eval.clj`
- `src/nrepl_mcp_server/mcp_server/tools/nrepl_load_file.clj`  
- `src/nrepl_mcp_server/mcp_server/tools/nrepl_send_message.clj`
- `src/nrepl_mcp_server/mcp_server/tools/nrepl_send_message_async.clj`
- `src/nrepl_mcp_server/mcp_server/tools/nrepl_get_result_async.clj`

**Pattern for each tool:**
```clojure
(defn handle
  [{:keys [connection ...other-params] :as args}]
  ;; Step 1: Resolve connection (Phase 1: returns single connection)
  (let [connection-id (conn/resolve-connection-id connection)]
    ;; Step 2: Use existing logic (unchanged in Phase 1)
    ...))
```

**Testing each tool update:**
```bash
# After updating each tool
./scripts/format.sh
clj-kondo --lint src/nrepl_mcp_server/mcp_server/tools/nrepl_eval.clj

# Test with connection parameter (should work with single connection)
python3 scripts/mcp_nrepl_client.py --tool nrepl-eval --args '{"code": "(+ 1 2)", "connection": "any-value"}'

# Test without connection parameter (should work with single connection)
python3 scripts/mcp_nrepl_client.py --tool nrepl-eval --args '{"code": "(+ 1 2)"}'
```

#### 1.3 Update mcp_nrepl_client.py Testing Script

**File:** `scripts/mcp_nrepl_client.py`

Add connection parameter support to testing functions:
```python
# Enhanced evaluation with connection support
def eval_with_connection(client, code, connection=None):
    args = {'code': code}
    if connection:
        args['connection'] = connection
    return client.call_tool('nrepl-eval', args)

# Add connection parameter to existing test functions
def test_basic_evaluation(client, connection=None):
    result = eval_with_connection(client, '(+ 1 2 3)', connection)
    assert result['status'] == 'success'
    assert result['value'] == 6
```

**Benefits:**
- ✅ **Early interface testing** - Validate connection parameter parsing
- ✅ **Script enhancement** - Prepare testing infrastructure for multi-connection
- ✅ **Regression testing** - Ensure single connection still works perfectly

### Phase 2: Simple Nickname Support (Single Connection)
**Risk:** Low | **Testing:** Nickname resolution with existing infrastructure

#### 2.1 Add Basic Nickname Mapping
**File:** `src/nrepl_mcp_server/state/connection.clj`

```clojure
;; Add to existing connection state (minimal change)
{:active-connection nil
 :connections {}
 :nicknames {}          ; NEW: nickname -> connection-id mapping
 :connection-counter 0}
```

#### 2.2 Enhance Connection Resolution
```clojure
(defn resolve-connection-id [user-identifier]
  (let [state @connection-registry
        active-conn-id (:active-connection state)]
    (cond
      ;; No identifier - single connection rule
      (nil? user-identifier)
      (if active-conn-id active-conn-id
          (throw (ex-info "No connections available" {})))
      
      ;; Try nickname lookup first
      (get (:nicknames state) user-identifier)
      (get (:nicknames state) user-identifier)
      
      ;; Try connection-id directly  
      (get (:connections state) user-identifier)
      user-identifier
      
      ;; Not found
      :else
      (if active-conn-id active-conn-id ; Fallback to active connection
          (throw (ex-info "Connection not found" {:identifier user-identifier}))))))
```

#### 2.3 Enhance nrepl-connection Tool with Nickname Support
```clojure
;; Add nickname parameter to connect operation
{"op": "connect", "connection": "localhost:7890", "nickname": "browser"}
```

**Testing:**
```bash
# Connect with nickname
python3 scripts/mcp_nrepl_client.py --tool nrepl-connection --args '{"op": "connect", "connection": "localhost:56720", "nickname": "test-server"}'

# Evaluate using nickname  
python3 scripts/mcp_nrepl_client.py --tool nrepl-eval --args '{"code": "(+ 1 2)", "connection": "test-server"}'

# Evaluate without connection (should still work)
python3 scripts/mcp_nrepl_client.py --tool nrepl-eval --args '{"code": "(+ 1 2)"}'
```

### Phase 3: Enhanced nrepl-connection Tool Operations
**Risk:** Low | **Testing:** New operations with single connection

#### 3.1 Add List Operation
```clojure
;; Returns current single connection with nickname if set
{"op": "list"}
```

#### 3.2 Add Disconnect-All Operation  
```clojure
;; Disconnects the single connection (preparation for multi-connection)
{"op": "disconnect-all"}
```

**Testing:**
```bash
# Test new operations
python3 scripts/mcp_nrepl_client.py --tool nrepl-connection --args '{"op": "list"}'
python3 scripts/mcp_nrepl_client.py --tool nrepl-connection --args '{"op": "disconnect-all"}'
```

### Phase 4: Per-Connection Queue Infrastructure
**Risk:** High | **Testing:** Backend refactoring with validated frontend

#### 2.1 Refactor Message Queue State
**File:** `src/nrepl_mcp_server/state/messages.clj`

```clojure
;; FROM: Single global queue
(def message-queue (atom {...}))

;; TO: Per-connection queues
(def connection-message-queues (atom {}))

;; Add connection-aware functions
(defn ensure-connection-queue! [connection-id] ...)
(defn enqueue-message! [connection-id message] ...)
(defn dequeue-message! [connection-id] ...)
(defn cleanup-connection-queue! [connection-id] ...)
```

**Testing Checkpoint:**
```bash
# Start fresh
python3 scripts/nrepl_test_server.py restart

# Test single connection still works
python3 scripts/mcp_nrepl_client.py --tool nrepl-connection --args '{"op": "connect", "connection": "localhost:56720"}'
python3 scripts/mcp_nrepl_client.py --tool nrepl-eval --args '{"code": "(+ 1 2 3)"}'

# Verify queue state
python3 scripts/mcp_nrepl_client.py --tool local-eval --args '{"code": "@nrepl-mcp-server.state.messages/connection-message-queues"}'
```

#### 2.2 Refactor Result Queue State  
**File:** `src/nrepl_mcp_server/state/results.clj`

```clojure
;; FROM: Single global result queue
(def result-queue (atom {...}))

;; TO: Per-connection result queues
(def connection-result-queues (atom {}))

;; Update all result functions to be connection-aware
(defn store-result! [connection-id message-id result] ...)
(defn get-result [connection-id message-id timeout-ms] ...)
```

### Phase 3: Update Watchers
**Risk:** Medium | **Testing:** Monitor message flow

#### 3.1 Implement Per-Connection Watchers
**File:** `src/nrepl_mcp_server/state/watchers.clj`

```clojure
;; Per-connection send watcher
(defn start-send-watcher! [connection-id]
  (let [watcher-future (future
                        (while (connection-active? connection-id)
                          (process-send-queue connection-id)
                          (Thread/sleep 10)))]
    (swap! connection-message-queues 
           assoc-in [connection-id :send-watcher] 
           watcher-future)))

;; Per-connection receive watcher  
(defn start-receive-watcher! [connection-id] ...)

;; Cleanup on disconnect
(defn stop-connection-watchers! [connection-id] ...)
```

**Testing:**
```bash
# Connect and verify watchers started
python3 scripts/mcp_nrepl_client.py --tool nrepl-connection --args '{"op": "connect", "connection": "localhost:56720", "nickname": "test"}'

# Check watcher state
python3 scripts/mcp_nrepl_client.py --tool local-eval --args '{"code": "(get-in @nrepl-mcp-server.state.messages/connection-message-queues [\"connection-id\" :send-watcher])"}'
```

### Phase 4: Update MCP Tools
**Risk:** High | **Testing:** Comprehensive tool testing

#### 4.1 Add Connection Parameter to All nREPL Tools

**Files to update:**
- `src/nrepl_mcp_server/mcp_server/tools/nrepl_eval.clj`
- `src/nrepl_mcp_server/mcp_server/tools/nrepl_load_file.clj`  
- `src/nrepl_mcp_server/mcp_server/tools/nrepl_send_message.clj`
- `src/nrepl_mcp_server/mcp_server/tools/nrepl_send_message_async.clj`
- `src/nrepl_mcp_server/mcp_server/tools/nrepl_get_result_async.clj`

**Pattern for each tool:**
```clojure
(defn handle
  [{:keys [connection ...other-params] :as args}]
  ;; Step 1: Resolve connection
  (let [connection-id (conn/resolve-connection-id connection)]
    ;; Step 2: Use connection-id for all operations
    ...))
```

**Testing each tool update:**
```bash
# After updating nrepl-eval
./scripts/format.sh
clj-kondo --lint src/nrepl_mcp_server/mcp_server/tools/nrepl_eval.clj

# Test with explicit connection
python3 scripts/mcp_nrepl_client.py --tool nrepl-eval --args '{"code": "(+ 1 2)", "connection": "test"}'

# Test without connection (should work with single connection)
python3 scripts/mcp_nrepl_client.py --tool nrepl-eval --args '{"code": "(+ 1 2)"}'
```

#### 4.2 Enhance nrepl-connection Tool
**File:** `src/nrepl_mcp_server/mcp_server/tools/nrepl_connection.clj`

Add new operations:
- `list` - List all connections with details
- `disconnect-all` - Disconnect all connections
- `rename` - Rename a connection nickname

### Phase 5: Multi-Connection Testing
**Risk:** Medium | **Testing:** End-to-end scenarios

#### 5.1 Create Multi-Connection Test Suite
**File:** `test_multi_connection.py`

```python
def test_multiple_connections():
    # Connect to first server
    connect_result1 = client.call_tool('nrepl-connection', 
                                      {'op': 'connect', 
                                       'connection': 'localhost:56720',
                                       'nickname': 'backend'})
    
    # Connect to second server  
    connect_result2 = client.call_tool('nrepl-connection',
                                      {'op': 'connect',
                                       'connection': 'localhost:56721', 
                                       'nickname': 'browser'})
    
    # Test evaluation on specific connections
    eval1 = client.call_tool('nrepl-eval', 
                            {'code': '(+ 1 2)', 'connection': 'backend'})
    eval2 = client.call_tool('nrepl-eval',
                            {'code': '(+ 3 4)', 'connection': 'browser'})
    
    # Test error when connection not specified with multiple
    try:
        client.call_tool('nrepl-eval', {'code': '(+ 5 6)'})
        assert False, "Should have thrown error"
    except:
        pass  # Expected
```

#### 5.2 Test Connection Lifecycle
```python
def test_connection_lifecycle():
    # List connections (should be empty)
    # Connect to server
    # List connections (should show one)
    # Disconnect
    # List connections (should be empty)
    # Test cleanup verification
```

### Phase 6: Cleanup & Documentation
**Risk:** Low | **Testing:** Documentation review

#### 6.1 Remove Dead Code
Based on `possible-dead-code.md`:
- Remove `register-tools!` function
- Remove unused summary functions
- Clean up redundant registration

#### 6.2 Update Documentation
- Update CLAUDE.md with multi-connection patterns
- Update README.md with examples
- Create migration guide for existing users

## 🧪 Testing Strategy

### Unit Testing Approach
After each function change:
1. Format code: `./scripts/format.sh`
2. Lint check: `clj-kondo --lint <changed-file>`
3. Test function in isolation using `local-eval`
4. Verify state changes

### Integration Testing Checkpoints
After each phase:
1. Run existing test suite: `python3 test_nrepl_lifecycle.py`
2. Manual testing with single connection (backward compatibility)
3. Manual testing with multiple connections (new functionality)
4. Verify no memory leaks or hanging watchers

### Rollback Points
- Phase 1 complete: Connection registry works
- Phase 2 complete: Queue infrastructure works
- Phase 3 complete: Watchers work
- Phase 4 complete: Tools work
- Phase 5 complete: Multi-connection works

## 🚨 Risk Mitigation

### High-Risk Changes
1. **Queue restructuring** - Test extensively with message flow
2. **Watcher management** - Ensure proper cleanup on disconnect
3. **Tool updates** - Maintain backward compatibility

### Rollback Strategy
```bash
# If things go wrong at any phase
git status
git diff
git stash  # Save WIP if valuable

# Return to baseline
git checkout d7c7057 -- .
git status

# Or full reset
git reset --hard d7c7057
```

### Safety Checks
Before each commit:
```bash
# Quality checks
./scripts/format.sh
clj-kondo --lint src/
python3 test_nrepl_lifecycle.py --quick

# State verification  
python3 scripts/mcp_nrepl_client.py --tool local-eval --args '{"code": "(keys @nrepl-mcp-server.state.connection/connection-registry)"}'
python3 scripts/mcp_nrepl_client.py --tool local-eval --args '{"code": "(count @nrepl-mcp-server.state.messages/connection-message-queues)"}'
```

## 📊 Success Criteria

### Functional Requirements
- [ ] Single connection works exactly as before (backward compatible)
- [ ] Multiple connections can be established
- [ ] Each connection has isolated queues
- [ ] Connection resolution is unambiguous
- [ ] Clean disconnect removes all resources
- [ ] No memory leaks or hanging threads

### Performance Requirements  
- [ ] No performance degradation for single connection
- [ ] Linear scaling with number of connections
- [ ] Watcher CPU usage < 1% per connection when idle

### Code Quality
- [ ] All code formatted with cljfmt
- [ ] Zero clj-kondo warnings
- [ ] All tests passing
- [ ] Dead code removed

## 📅 Implementation Schedule

**Day 1:** Phase 1 - Connection Registry (4 hours)
**Day 2:** Phase 2 - Queue Infrastructure (6 hours)  
**Day 3:** Phase 3 - Watchers + Testing (4 hours)
**Day 4:** Phase 4 - Update Tools (6 hours)
**Day 5:** Phase 5 - Multi-Connection Testing (4 hours)
**Day 6:** Phase 6 - Cleanup & Documentation (3 hours)
**Day 7:** Buffer for issues and final testing

## 🎯 Commit Strategy

Commit after each successful phase:
```bash
# Phase 1 complete
git add -A
git commit -m "refactor: Phase 1 - Connection registry with nickname support"

# Phase 2 complete  
git add -A
git commit -m "refactor: Phase 2 - Per-connection queue infrastructure"

# And so on...
```

This incremental approach ensures we always have working checkpoints to return to if needed.