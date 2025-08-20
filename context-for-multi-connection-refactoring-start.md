# Context for Multi-Connection Refactoring Start

**Date**: 2025-01-19  
**Purpose**: Memory compaction context for continuing multi-connection refactoring implementation  
**Baseline Commit**: d7c7057 (feat: Implement nrepl-load-file tool and enhance stderr capture)  

## 🎯 **Project Status**

**Current State**: Production-ready single-connection nREPL-MCP bridge with 100% test reliability
- ✅ **Phase 2B.6 Complete**: Enhanced nrepl-eval with timeout and message-id recovery  
- ✅ **All async architecture working**: Fire-and-forget → receive → merge → response flow
- ✅ **Comprehensive toolset**: 9 MCP tools (local-eval, local-load-file, nrepl-connection, nrepl-eval, etc.)
- ✅ **Robust connection switching**: 10/10 rapid server switching cycles successful

**Next Major Enhancement**: Multi-connection architecture to support browser + app-server + mobile nREPL scenarios simultaneously

## 🏗️ **Multi-Connection Refactoring Overview**

**Objective**: Transform single-connection architecture to support multiple concurrent nREPL connections with per-connection queue management.

**Plan Documents**:
- **Main Plan**: `multi-connection-refactoring-plan.md` (comprehensive 6-phase implementation)
- **Dead Code Analysis**: `possible-dead-code.md` (cleanup opportunities identified)
- **TODO Integration**: `TODO.md` updated with multi-connection section

**Implementation Strategy**: **Interface-First Approach** (user's key insight)
1. Add connection parameters to tool interfaces first (test with single connection)
2. Add nickname support with minimal backend changes
3. Enhance nrepl-connection tool operations
4. Refactor to per-connection queues (high-risk phase last)

## 📋 **Implementation Phases - Interface-First Strategy**

### **Phase 1: Add Connection Parameter to Tool Interfaces** (1 day)
**Risk**: Low | **Status**: Ready to start

**Key Tasks**:
1. **Implement connection resolution** (`src/nrepl_mcp_server/state/connection.clj`)
   ```clojure
   (defn resolve-connection-id [user-identifier]
     ;; Phase 1: Simple logic - returns single connection regardless of identifier
     ;; Phase 4: Complex logic - handles nicknames, multiple connections, etc.
   ```

2. **Add connection parameter to all nREPL tools**:
   - `nrepl-eval.clj`
   - `nrepl-load-file.clj` 
   - `nrepl-send-message.clj`
   - `nrepl-send-message-async.clj`
   - `nrepl-get-result-async.clj`

3. **Update mcp_nrepl_client.py testing script**:
   - Add connection parameter support to evaluation functions
   - Prepare testing infrastructure for multi-connection scenarios

**Testing Strategy**:
```bash
# Test with connection parameter (should work with single connection)
python3 scripts/mcp_nrepl_client.py --tool nrepl-eval --args '{"code": "(+ 1 2)", "connection": "any-value"}'

# Test without connection parameter (should still work)
python3 scripts/mcp_nrepl_client.py --tool nrepl-eval --args '{"code": "(+ 1 2)"}'
```

### **Phase 2: Simple Nickname Support** (0.5 days)
**Risk**: Low

**Key Changes**:
- Add `:nicknames {}` to connection state (minimal change)
- Enhance connection resolution to try nickname lookup first
- Add nickname parameter to nrepl-connection connect operation

### **Phase 3: Enhanced nrepl-connection Tool** (0.5 days) 
**Risk**: Low

**New Operations**:
- `{"op": "list"}` - List current single connection with nickname
- `{"op": "disconnect-all"}` - Disconnect single connection (preparation for multi-connection)

### **Phase 4: Per-Connection Queue Infrastructure** (2 days)
**Risk**: High - Core architecture change

**Major Changes**:
- Per-connection message queues: `{:connection-abc123 {:send-queue ...}}`
- Per-connection result queues: `{:connection-abc123 {:result-promises ...}}`
- Per-connection watchers with isolated cleanup
- Remove "active-connection" concept - explicit or single-only rule

## 🔧 **Current Architecture Context**

### **Current Single-Connection Architecture**
```clojure
;; Global message queue (src/nrepl_mcp_server/state/messages.clj)
(def message-queue (atom {:send-queue PersistentQueue/EMPTY
                          :pending-messages {}
                          :message-counter 0}))

;; Global result queue (src/nrepl_mcp_server/state/results.clj)  
(def result-queue (atom {:result-promises {}
                         :completed-results {}
                         :error-results {}}))

;; Single connection state (src/nrepl_mcp_server/state/connection.clj)
{:active-connection nil          ; Current connection or nil
 :connections {}                 ; connection-id -> details registry  
 :connection-counter 0}
```

### **Target Multi-Connection Architecture**
```clojure
;; Per-connection message queues
(def connection-message-queues (atom {}))
{:connection-abc123 {:send-queue PersistentQueue/EMPTY
                     :pending-messages {}
                     :message-counter 0
                     :watchers #{}}
 :connection-def456 {...}}

;; Per-connection result queues
(def connection-result-queues (atom {}))
{:connection-abc123 {:result-promises {}
                     :completed-results {}
                     :error-results {}}}

;; Enhanced connection registry (no active-connection!)
{:connections {}         ; connection-id -> details
 :nicknames {}          ; nickname -> connection-id mapping
 :endpoints {}          ; "host:port" -> connection-id  
 :connection-counter 0}
```

## 🔧 **Key Technical Components**

### **Connection Resolution Logic** (Phase 1 vs Phase 4)
```clojure
;; Phase 1: Simple (single connection)
(defn resolve-connection-id [user-identifier]
  (if-let [active-conn (get-active-connection)]
    (:connection-id active-conn)
    (throw (ex-info "No connections available"))))

;; Phase 4: Complex (multi-connection)  
(defn resolve-connection-id [user-identifier]
  (cond
    (nil? user-identifier) 
    ;; No ambiguity rule: single connection only
    (case (count connected-connections)
      0 (throw (ex-info "No connections available"))
      1 (:connection-id (first connected-connections))
      (throw (ex-info "Multiple connections - specify which one")))
    
    ;; Try nickname → connection-id → endpoint resolution
    :else (resolve-from-nicknames-or-ids user-identifier)))
```

### **Tool Interface Pattern**
```clojure
;; Every nREPL tool gets this pattern
(defn handle [{:keys [connection ...other-params]}]
  ;; Step 1: Resolve connection
  (let [connection-id (conn/resolve-connection-id connection)]
    ;; Step 2: Use connection-specific queues/logic
    ...))
```

## 📊 **Testing Infrastructure**

### **Current Test Environment**
- **HTTP Bridge**: `./scripts/start-http-bridge.sh` for stateful testing
- **nREPL Test Server**: `python3 scripts/nrepl_test_server.py start` for external Clojure
- **Local Babashka nREPL**: `local-nrepl-server` tool for embedded server
- **MCP Client**: `python3 scripts/mcp_nrepl_client.py` (enhanced with connection support)

### **Multi-Connection Test Scenarios**
```python
# Phase 1 testing: Connection parameter with single connection
def test_connection_parameter_single_connection():
    # Should work with connection parameter
    result1 = client.call_tool('nrepl-eval', {'code': '(+ 1 2)', 'connection': 'any-value'})
    # Should work without connection parameter  
    result2 = client.call_tool('nrepl-eval', {'code': '(+ 3 4)'})

# Phase 4 testing: True multi-connection
def test_multiple_connections():
    # Connect to different servers with nicknames
    client.call_tool('nrepl-connection', {'op': 'connect', 'connection': 'localhost:7890', 'nickname': 'browser'})
    client.call_tool('nrepl-connection', {'op': 'connect', 'connection': 'localhost:7891', 'nickname': 'backend'})
    
    # Evaluate on specific connections
    browser_result = client.call_tool('nrepl-eval', {'code': '(js/alert "hi")', 'connection': 'browser'})
    backend_result = client.call_tool('nrepl-eval', {'code': '(println "server")', 'connection': 'backend'})
```

## 🧹 **Code Quality Requirements**

**After every change**:
```bash
# Format and lint
./scripts/format.sh
clj-kondo --lint src/

# Test basic functionality still works
python3 scripts/mcp_nrepl_client.py --tool nrepl-connection --args '{"op": "status"}'
python3 scripts/mcp_nrepl_client.py --tool nrepl-eval --args '{"code": "(+ 1 2 3)"}'
```

**Critical naming rules**:
- **Clojure namespaces**: HYPHENS (`nrepl-mcp-server.state.connection`)
- **File/directory names**: UNDERSCORES (`src/nrepl_mcp_server/state/connection.clj`)
- **Never mix** - causes hard-to-find bugs

## ⚠️ **Risk Mitigation**

**Rollback Points**:
- **Phase 1 Complete**: Interface changes working with single connection
- **Phase 2 Complete**: Nickname resolution working  
- **Phase 3 Complete**: Enhanced tool operations working
- **Phase 4 Issues**: Rollback to Phase 3 completion

**Safety Measures**:
- **Baseline preserved**: d7c7057 commit is stable rollback point
- **Incremental commits**: After each phase completion
- **Comprehensive testing**: Single connection must work perfectly throughout
- **Quality gates**: Format/lint after every change

## 🎯 **Success Criteria**

**Phase 1 Success**:
- ✅ All nREPL tools accept connection parameter
- ✅ Tools work with connection parameter specified
- ✅ Tools work without connection parameter (backward compatibility)
- ✅ mcp_nrepl_client.py enhanced with connection support
- ✅ Zero regression in existing functionality

**Final Success**:
- ✅ Multiple concurrent nREPL connections supported
- ✅ Connection nicknames working ("browser", "backend", "mobile")
- ✅ No ambiguity - explicit connection or single-only rule
- ✅ Clean per-connection queue isolation and cleanup
- ✅ Enhanced tooling (list, disconnect-all operations)

## 🚀 **Ready to Start Phase 1**

**First Steps**:
1. Create `resolve-connection-id` function in `connection.clj`
2. Add connection parameter to `nrepl-eval.clj` first (test one tool)
3. Update `mcp_nrepl_client.py` with connection support
4. Test thoroughly before proceeding to other tools

**Implementation ready - interface-first strategy validated and planned!**