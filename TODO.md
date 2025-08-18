# MCP-nREPL Project TODO List - CLEAN SLATE REFACTORING

Last updated: 2025-08-18
Phase 2c.2 completed: 2025-08-18
**Status**: ✅ **PHASE 2C COMPLETED** - All tool renaming with proper prefixes and namespacing

## 🏁 **PHASE 2B.6 COMPLETION SUMMARY**

**Achievement**: Successfully implemented enhanced nrepl-eval tool with timeout and message-id recovery functionality using the async message queue infrastructure.

**Key Features Delivered**:
- ✅ **Timeout parameter**: Configurable timeout (default 30s, range 1s-300s)
- ✅ **Message-ID recovery**: Use message-id parameter to check for delayed results
- ✅ **Smart routing**: No message-id → send-message-get-result, with message-id → get-result-async
- ✅ **Enhanced error handling**: Connection validation in underlying handlers with actionable guidance
- ✅ **Complete async architecture**: Fire-and-forget → receive → merge → response flow
- ✅ **Proper error messages**: Clear guidance instead of vague technical errors

**Technical Implementation**:
- Enhanced `nrepl-eval.clj` with cond-based routing logic for validation, recovery, and normal paths
- Moved connection validation to `send-message-get-result.clj` with helpful error messages
- Complete timeout recovery mechanism with message-id extraction from timeout responses
- Clean JSON responses with operation context and recovery instructions

**Validation Results**:
- ✅ Normal evaluation: `(+ 1 2 3)` → value: 6
- ✅ Complex evaluation: `(do (println "Hello from nREPL") (+ 10 20 30))` → value: 60, out: "Hello from nREPL\n"
- ✅ Error handling: Syntax errors properly caught and reported
- ✅ Async architecture: Complete message correlation and response merging
- ✅ Timeout support: Custom timeout parameters processed correctly
- ✅ Connection errors: Helpful guidance with actionable examples

## 🎯 **PROJECT MISSION: CLEAN ARCHITECTURE REBUILD**

**Goal**: Implement the proper 3-layer sync-async queuing architecture without the circular dependencies and architectural mess discovered in the legacy codebase.

**Strategy**: 
- ✅ Archive legacy code to `/old/` for reference
- 🏗️ Build incrementally with proper testing at each phase  
- 📝 **Update architecture doc as we implement** - living documentation
- 🧪 Test-driven development with comprehensive coverage

## 🔧 **PHASE 2C: TOOL RENAMING - LOCAL EXECUTION TOOLS**

### **Phase 2c.1: Rename debug-* tools to local-* tools** ✅ **COMPLETED**

**Goal**: Rename `debug-eval` and `debug-load-file` to `local-eval` and `local-load-file` to better reflect their purpose of executing code locally within the MCP server runtime (as opposed to remote nREPL execution).

### **Phase 2c.2: Rename nREPL tools with proper prefixes** ✅ **COMPLETED**

**Goal**: Rename nREPL tools for better namespacing and clarity:
- `nrepl-server` → `nrepl-connection` (more accurate - manages client-side connection)
- `send-message-async` → `nrepl-send-message-async` (add nrepl prefix)
- `get-result-async` → `nrepl-get-result-async` (add nrepl prefix)
- `send-message-get-result` → `nrepl-send-message` (shorter sync wrapper)

**Rationale**: 
- **Consistency**: All nREPL tools should have `nrepl-` prefix for clarity
- **Accuracy**: "connection" better describes client-side connection management vs "server"
- **Namespacing**: Clear distinction between nREPL tools and local tools
- **Simplicity**: "send-message" is shorter and clearer than "send-message-get-result"

**Completion Summary**:
- ✅ **File Renaming**: All 4 nREPL tool files renamed with proper namespaces
- ✅ **Tool Registration**: Updated tool registry with new names
- ✅ **Reference Updates**: Updated all test files, Python scripts, and documentation
- ✅ **Code Quality**: Formatted and linted all changes (1 minor warning only)
- ✅ **Functionality**: All 7 tools working correctly with new names
- ✅ **Testing**: Verified local-eval, local-load-file, nrepl-connection, nrepl-eval functionality
- ✅ **Bridge Integration**: HTTP bridge properly serving all renamed tools

**Rationale (Phase 2c.1)**: 
- **Clarity**: "local" better describes that these tools execute in the MCP server's SCI runtime
- **Distinction**: Clear separation from nREPL tools which execute remotely
- **Consistency**: Aligns with naming patterns where tool name reflects execution context

**Implementation Plan**:

#### Step 1: File and Namespace Renaming
- [ ] Rename `src/nrepl_mcp_server/mcp_server/tools/debug_eval.clj` → `local_eval.clj`
- [ ] Rename `src/nrepl_mcp_server/mcp_server/tools/debug_load_file.clj` → `local_load_file.clj`
- [ ] Update namespace declarations from `local-eval` → `local-eval`
- [ ] Update namespace declarations from `local-load-file` → `local-load-file`

#### Step 2: Tool Registration Updates
- [ ] Update tool registration calls to use new names
- [ ] Update self-registration in each tool file
- [ ] Update `register_tools.clj` require statements

#### Step 3: Test Updates
- [ ] Update all test files (JSON test definitions)
- [ ] Update Python test scripts that call these tools
- [ ] Update bash test scripts in `/scripts/`

#### Step 4: Documentation Updates
- [ ] Update CLAUDE.md references
- [ ] Update TODO.md references (this file)
- [ ] Update architecture documentation
- [ ] Update cookbook examples

#### Step 5: Settings and Config Updates
- [ ] Update `.claude/settings.local.json` auto-approval patterns
- [ ] Update any example commands in documentation

**Files to Update (Primary)**:
1. `src/nrepl_mcp_server/mcp_server/tools/debug_eval.clj` → `local_eval.clj`
2. `src/nrepl_mcp_server/mcp_server/tools/debug_load_file.clj` → `local_load_file.clj`
3. `src/nrepl_mcp_server/state/register_tools.clj`
4. All test files in `/tests/` directory
5. Python scripts: `explore_mcp.py`, `test_http_bridge.py`, etc.
6. Shell scripts in `/scripts/`
7. Documentation files in `/docs/`

**Testing Strategy**:
1. Run `./scripts/format.sh` after all changes
2. Run `clj-kondo --lint src/` to verify no issues
3. Start HTTP bridge: `./scripts/start-http-bridge.sh`
4. Test tool listing shows new names
5. Test both tools work with new names
6. Run comprehensive test suite
7. Verify all references updated with grep search

## 🚀 **CLEAN SLATE REFACTORING PHASES**

### **Phase 1: Archive Legacy & Minimal Foundation** ✅ **COMPLETED**

- [x] **Move /src to /old/src** (readonly archive for copy/paste reference)
- [x] **Move test scripts to /old tree** (preserve for reference)
- [x] **Create new minimal project structure**
- [x] **Implement absolute minimum MCP server** (local-eval + local-load-file only)
- [x] **Create test wrapper scripts** (avoid permission prompts) - `./scripts/test-quick.sh` and `./scripts/test-comprehensive.sh`
- [x] **Write comprehensive test coverage** for minimal server - **Phase-specific test suites created**:
- [x] **Implement stdout/stderr capture** - Protocol-complete implementation using `with-out-str` approach
- [x] **Document MCP protocol findings** - stderr does NOT interfere with MCP stdio communication
- [x] **Clean up code quality** - All clj-kondo warnings resolved, tree-sitter validated
  - `./scripts/test-phase1.sh` - **19 comprehensive tests** including **MCP introspection via local-eval**:
    - Basic MCP protocol (2 tests)
    - local-eval functionality (7 tests) 
    - local-load-file functionality (3 tests)
    - **MCP introspection** (5 tests) - namespace inspection, tool metadata simulation, server state
    - Advanced integration (2 tests)
  - `./scripts/test-phase2.sh` - Future core MCP functions (nrepl-connect, send-message, get-result)
  - `./scripts/test-phase3.sh` - Future real nREPL communication tests
  - `./scripts/test-master.sh` - **Master orchestrator** with individual phase control

### **Phase 2: Three Core MCP Functions** ✅ **COMPLETED**

#### **2a: Reactive Connection Management** ✅ **COMPLETED**
- [x] **Create state namespace** (`src/mcp_server/state.clj`)
  - Connection state atom with status tracking
  - Helper functions for state updates
  - Expose state for local-eval introspection
  
- [x] **Create connection namespace** (`src/mcp_server/connection.clj`)
  - Connection parameter resolution (host:port, port, file, env)
  - TCP connection operations (connect/disconnect)
  - Connection handler watcher for reactive state changes
  
- [x] **Implement nrepl-server MCP tool** (`src/mcp_server/tools/nrepl.clj`)
  - Clean MCP interface with ops: connect, disconnect, status
  - Delegates to connection namespace for operations
  - Configurable timeout (default 5000ms)

- [x] **Refactor for clean separation** - Split overloaded namespace into three focused ones
- [x] **Comprehensive testing** - All 12 Phase 2a tests passing (100% success rate)
  
**Architecture Achievement**: Clean separation of concerns with three focused namespaces:
- `state` - Central state management
- `connection` - Connection logic and handlers  
- `tools/nrepl` - MCP tool interface only

**Test Coverage**: `./scripts/test-phase2a-simple.sh` validates all functionality

#### **2a.5: Namespace Refactoring** ✅ **COMPLETED**

**Achievement**: Successfully restructured codebase to `nrepl-mcp-server` namespace hierarchy

**Completed Structure**:
```
nrepl-mcp-server/
  core.clj                     ; Main entry, minimal bootstrap
  state.clj                    ; All state atoms, queues
  
  mcp/                         ; MCP protocol implementation
    server.clj                 ; stdio server, JSON-RPC handling
    dispatch.clj               ; Tool routing/dispatch table
    tools/                     ; One file per MCP tool
      debug_eval.clj           ; local-eval tool
      debug_load_file.clj      ; local-load-file tool
      nrepl_connect.clj        ; connect operation
      nrepl_disconnect.clj     ; disconnect operation
      nrepl_status.clj         ; status operation
  
  nrepl_client/                ; nREPL client implementation
    connection.clj             ; TCP connection management
    handlers.clj               ; State watchers, queue processors
```

**Key Achievements**:
- [x] Clean namespace hierarchy reflecting project purpose
- [x] One file per MCP tool for clarity and maintainability
- [x] Clear separation: MCP protocol vs nREPL client vs state
- [x] Backward compatibility maintained for existing tests
- [x] All tests passing (Phase 1: 19/19, Phase 2a: 12/12)
- [x] Code formatted with cljfmt
- [x] Zero linting issues with clj-kondo

#### **2a.6: Namespace Refinement** 🔧 **IN PROGRESS**

**Goal**: Further refine namespace structure for clarity and maintainability

**Changes**:
1. **Rename `mcp` → `mcp-server`** for clearer purpose
2. **Split monolithic `state.clj`** into focused state domains:
   - `state/connection.clj` - Connection state management
   - `state/messages.clj` - Message queue state  
   - `state/results.clj` - Result queue state

**Migration steps**:
- [ ] Create `state/` directory structure
- [ ] Move and rename `mcp/*` → `mcp-server/*`
- [ ] Split `state.clj` into three domain-specific files
- [ ] Update all require statements
- [ ] Update test scripts
- [ ] Run full test suite

#### **2a.7: Restore Unified nrepl-server Interface** ✅ **COMPLETED**

**Goal**: Return to clean unified `nrepl-server` tool with `op` parameter (as originally designed and expected by tests)

**Problem**: Current implementation has split into separate tools (`nrepl-connect`, `nrepl-disconnect`, `nrepl-status`) with backward compatibility hack in dispatcher, violating architectural principles

**Solution**:
1. **Create unified `nrepl-server.clj`** - Single tool handling all nREPL operations
   - Move all connect/disconnect/status logic into one file
   - Handle `{"op": "connect"}`, `{"op": "disconnect"}`, `{"op": "status"}` operations
   - Preserve all existing functionality and parameters

2. **Clean up tool registry** - Remove separate tools from dispatch table
   - Remove `nrepl-connect`, `nrepl-disconnect`, `nrepl-status` entries
   - Add single `nrepl-server` entry with unified handler

3. **Remove architectural violations** - Clean up call-tool function
   - Remove hardcoded `nrepl-server` special case logic
   - Return to pure generic dispatch pattern
   - Restore clean separation of concerns

4. **Delete obsolete tool files**
   - Remove `tools/nrepl_connect.clj`
   - Remove `tools/nrepl_disconnect.clj` 
   - Remove `tools/nrepl_status.clj`

**Benefits**:
- **Restores architectural purity** - Clean generic dispatcher
- **Matches test expectations** - No test changes needed
- **Simpler mental model** - One tool, multiple operations
- **Better separation of concerns** - Tool-specific logic in tool files
- **Consistent with nREPL patterns** - Operations as parameters, not tool names

**Migration steps**:
- [x] Create `tools/nrepl_server.clj` with unified handler
- [x] Update tool registry to use single `nrepl-server` entry
- [x] Clean up `call-tool` function to remove special cases
- [x] Delete obsolete individual tool files
- [x] Run full test suite to verify functionality preserved
- [x] Format and lint code

#### **2a.8: Self-Registering Tools Pattern** ✅ **COMPLETED**

**Goal**: Eliminate all tool-specific knowledge from dispatcher, achieving complete separation of concerns through dynamic tool registration.

**Problem**: Current dispatcher contains tool-specific details, violating clean architecture principles and creating coupling between dispatcher and individual tools.

**Solution**:
1. **Create tool registry system** - `state/tool_registry.clj`
   - Atom-based registry for storing tool handlers and metadata
   - Helper functions for registration, lookup, and introspection
   - Pure data management without orchestration logic

2. **Create registration orchestrator** - `state/register_tools.clj`
   - Explicit require statements for all tool namespaces
   - Single function to make registration side effects explicit
   - Centralized control over which tools are included

3. **Implement self-registration pattern** - Each tool registers itself
   - Tools call `(registry/register-tool! ...)` when namespace loads
   - Includes handler function and MCP metadata (description, inputSchema)
   - Zero coupling between tools and dispatcher

4. **Refactor dispatcher to be purely generic** - `mcp_server/dispatch.clj`
   - Remove all tool-specific knowledge and hardcoded logic
   - Use registry atom for tool lookup and execution
   - Clean separation of concerns achieved

**Architecture Benefits**:
- **Complete Decoupling**: Dispatcher has zero knowledge of specific tools
- **Dynamic Discovery**: Tools can be added/removed by changing require statements
- **Clean Architecture**: Each tool is responsible for its own registration
- **MCP Protocol Compliance**: Tools discoverable via standard `tools/list` endpoint
- **Separation of Data and Orchestration**: Registry (data) vs registration (orchestration)

**Implementation Results**:
- [x] Create `state/tool_registry.clj` with atom and helper functions
- [x] Create `state/register_tools.clj` with explicit tool requires
- [x] Update all tools to self-register at namespace load
- [x] Refactor `dispatch.clj` to be purely registry-based
- [x] Remove all tool-specific knowledge from dispatcher
- [x] Test functionality: "🔧 Registered 3 MCP tools: ['local-eval' 'local-load-file' 'nrepl-server']"
- [x] Format and lint code following best practices
- [x] Update architecture documentation with self-registering tools pattern

**Architecture Achievement**: Clean self-registering tools pattern serves as foundation for all future MCP tool development, with complete separation of concerns and zero coupling between dispatcher and tools.

#### **2a.9: Legacy nREPL Integration & Namespace Reorganization** ✅ **COMPLETED**

**Goal**: Integrate comprehensive nREPL implementation from legacy code while reorganizing into clean modular namespace structure.

**Problem**: Legacy code contained complete nREPL client implementation but was in separate `/old` directory. Need to integrate this functionality with current clean architecture.

**Solution**:
1. **Copy and integrate legacy nREPL code**
   - Move UUID v7 implementation to utils namespace
   - Integrate socket connection, messaging, and operations modules
   - Create unified nrepl-eval MCP tool exposing all nREPL operations

2. **Organize into modular namespaces**
   - `utils/uuid_v7.clj` - RFC 9562 compliant UUID generation
   - `nrepl_client/socket_connection.clj` - Low-level socket connection management
   - `nrepl_client/messaging.clj` - Bencode protocol and async message handling
   - `nrepl_client/operations.clj` - High-level nREPL operations (eval, doc, complete, etc.)
   - `mcp_server/tools/nrepl_eval.clj` - MCP tool interface for all nREPL operations

3. **Maintain clean architecture principles**
   - Self-registering tool pattern maintained
   - Reactive state integration preserved
   - Promise-based async timeout handling
   - Clean separation of concerns

**Implementation Results**:
- [x] Integrate UUID v7 implementation in utils namespace
- [x] Create socket_connection.clj with connection lifecycle management
- [x] Implement messaging.clj with bencode protocol and async handling
- [x] Build operations.clj with complete nREPL operation set
- [x] Create nrepl_eval.clj MCP tool with unified operation interface
- [x] Fix all namespace/directory mismatches (dashes vs underscores)
- [x] Update all import references to new namespace structure
- [x] Rename tcp_connection.clj to socket_connection.clj for accuracy
- [x] Test server startup: "🔧 Registered 4 MCP tools: ['local-eval' 'local-load-file' 'nrepl-server' 'nrepl-eval']"

**Architecture Achievement**: Successfully integrated complete legacy nREPL implementation while maintaining clean modular organization and self-registering tools pattern. All 4 tools register and load correctly with proper namespace separation.

#### **2a.10: Unified Connection State Management** ✅ **COMPLETED**

#### **2a.11: Code Quality and Formatting Cleanup** ✅ **COMPLETED**

**Goal**: Clean up codebase with proper formatting and resolve linting issues.

**Completed Tasks**:
- [x] Format all Clojure files using cljfmt
- [x] Lint all Clojure files using clj-kondo
- [x] Fix major linting issues:
  - Renamed `load-file` to `nrepl-load-file` to avoid clojure.core conflict
  - Updated function reference in nrepl_eval.clj
  - Fixed unused parameter bindings with underscore prefix
- [x] Re-format and re-lint to ensure clean results

**Results**:
- ✅ All code properly formatted with cljfmt
- ✅ Reduced linting warnings from 5 to 1 (remaining is a false positive)
- ✅ Clean code structure and improved maintainability

#### **2a.10: Unified Connection State Management** ✅ **COMPLETED**

**Goal**: Eliminate duplicate connection state atoms by creating single source of truth in state namespace with human-readable connection IDs.

**Problem**: Two connection state atoms maintain essentially the same information:
- `nrepl-mcp-server.state.connection/connection-state` (application layer, single connection)
- `nrepl-mcp-server.nrepl-client.socket-connection/connection-state` (transport layer, connection registry)

This violates single source of truth principle and creates synchronization risks.

**Solution**:
1. **Enhanced state atom as single source of truth**
   - Move all connection tracking to `state/connection.clj`
   - Support both single active connection and historical tracking
   - Human-readable connection IDs: `"192.168.1.10:7890-01234567-abcd-89ef-ghij-klmnopqrstuv"`
   - Real IP address resolution (localhost → actual IP)

2. **Connection registry structure**
   ```clojure
   {:active-connection nil          ; Current active connection ID
    :connections {"ip:port-uuid" {:connection-id "..."
                                  :hostname "localhost"
                                  :resolved-ip "192.168.1.10"
                                  :port 7890
                                  :socket #<Socket>
                                  :status :connected
                                  :created-at timestamp
                                  :closed-at nil
                                  :error nil}}
    :counter 0}
   ```

3. **Access/management API for socket-connection layer**
   - `register-connection!` - Create new connection with human-readable ID
   - `update-connection-status!` - Update connection state
   - `get-active-connection` - Get current active connection details  
   - `mark-connection-closed!` - Mark connection as closed with cleanup
   - `cleanup-old-connections!` - Remove old closed connections

**Implementation Results**:
- [x] **FIX NAMESPACE NAMES FIRST** - Convert all underscores to hyphens in namespace declarations (ZERO TOLERANCE rule) ✅
- [x] Design enhanced connection state structure with registry + active tracking ✅
- [x] Implement human-readable connection ID generation (IP:port-UUIDv7) ✅
- [x] Add IP address resolution utilities (localhost → real IP) ✅
- [x] Create management API functions for socket-connection layer ✅
- [x] Update socket-connection.clj to use state namespace functions ✅
- [x] Remove duplicate connection-state atom from socket-connection ✅
- [x] Update all references to use unified state management ✅
- [x] Add missing uuid-v7-string function for compatibility ✅
- [x] Fix function signatures and remove legacy queue references ✅
- [x] **Test unified connection state management** ✅
- [x] **Fix nrepl-eval connection detection** ✅
- [ ] Clean up dead code and legacy backward compatibility functions (use tree-sitter analysis) 🔄 **DEFERRED**
- [ ] Update architecture documentation 🔄 **DEFERRED**

**Validation Results**:
- ✅ **nrepl-server connection establishment** - Working with human-readable IDs
- ✅ **Connection state persistence** - Maintains state across stateless HTTP requests
- ✅ **nrepl-eval connection detection** - Fixed to work with unified state structure
- ✅ **nREPL message sending** - Initiated successfully (logs confirm async sending)
- ✅ **API operations** - connect, status, disconnect all functional

**Benefits Achieved**:
- ✅ **Single Source of Truth** - No state duplication or synchronization issues
- ✅ **Human-Readable IDs** - Easy to identify connections in logs (`127.0.0.1:56720-<UUID>`)
- ✅ **Historical Tracking** - Maintain connection history for debugging
- ✅ **Real IP Resolution** - Better logging and monitoring (localhost → 127.0.0.1)
- ✅ **Clean API** - Socket layer uses well-defined management functions
- ✅ **Cross-Request Persistence** - State survives stateless HTTP mode

**Phase 2a.10 Complete!** Unified connection state management serves as solid foundation for Phase 2b async message queuing.

#### **2b: Message Queue Infrastructure** ✅ **COMPLETED**

**Architecture**: 4-phase reactive message queue with introspectable checkpoints at each phase

##### **Phase 2b.1: Basic send-message-async Implementation** ✅ **COMPLETED**
- [x] **Implement send-message-async MCP tool**
  - Check connection status (fail if not connected)
  - Generate UUID v7 message-id (becomes nREPL `id` field)
  - Add message to send FIFO queue with timestamp
  - Create result-queue entry with status `:pending`
  - Return message-id immediately to caller
  
- [x] **Testing & Validation**:
  - Use local-eval to introspect `@send-queue` - verify message added
  - Use local-eval to check `@result-queue` - verify `:pending` entry created
  - Verify UUID v7 format and uniqueness
  - Test connection check (should fail when disconnected)
  - **TEST FUNCTION**: `(validate-phase1-queuing)`

##### **Phase 2b.2: Send Queue Watcher Implementation** ✅ **COMPLETED**
- [x] **Implement send-queue-watcher**
  - Process FIFO queue (preserve message order!)
  - Take message from queue
  - Send to nREPL server via socket
  - Store actual bencode message sent (for debugging/replay)
  - Update result-queue status to `:sending` then `:sent`
  - Handle send failures → status `:failed`
  
- [x] **Testing & Validation**:
  - Mock nREPL send (don't need real server yet)
  - Use local-eval to verify queue processing
  - Check status transitions: `:pending` → `:sending` → `:sent`
  - Verify FIFO order preservation
  - **TEST FUNCTION**: `(validate-phase2-sending)`

##### **Phase 2b.3: Receive Watcher Implementation** ✅ **COMPLETED**
- [x] **Implement receive-watcher**
  - Read responses from nREPL socket
  - Match response to message-id (correlation)
  - Handle partial/streaming responses (status `:partial`)
  - Accumulate multi-part responses
  - Update result-queue with complete response
  - Set status to `:done` when complete
  - Handle nREPL error responses → status `:error`
  
- [x] **Testing & Validation**:
  - Simulate received messages via local-eval
  - Test multi-part response handling
  - Verify message correlation by id
  - Check status transitions: `:sent` → `:partial` → `:done`
  - **TEST FUNCTION**: `(validate-phase3-receiving)`

##### **Phase 2b.4: Get-Result-Async Implementation** ✅ **COMPLETED**
- [x] **Implement get-result-async MCP tool**
  - Lookup message-id in result-queue
  - If status `:done` - remove entry and return result
  - If status `:pending`/`:sending`/`:sent` - wait on promise
  - Implement promise-based waiting with timeout
  - Return timeout error if exceeds limit
  - Clean up entry after returning
  
- [x] **Testing & Validation**:
  - Test immediate return for completed messages
  - Test waiting on pending messages
  - Test timeout behavior
  - Verify entry cleanup after retrieval
  - **TEST FUNCTION**: `(validate-phase4-retrieval)`

##### **Phase 2b.5: Send-Message-Get-Result Sync-Wrapper** ✅ **COMPLETED**
- [x] **Implement send-message-get-result wrapper**
  - Combines send-message-async + get-result-async
  - Single synchronous call for simple use cases
  - Pass through timeout parameter
  - Return result or error directly
  
- [x] **Testing & Validation**:
  - End-to-end test with real nREPL server
  - Test timeout propagation
  - Compare with direct nREPL operations
  - **TEST FUNCTION**: `(validate-phase5-send-message-get-result)`

##### **Phase 2b.6: nrepl-eval Implementation Using Async Queue** ✅ **COMPLETED**
- [x] **Rename existing nrepl_eval.clj to nrepl_eval_old.clj**
  - Preserve old implementation for reference
  - Ensure it doesn't break existing functionality
  - Update register_tools.clj to remove old require
  
- [x] **Update register_tools.clj**
  - Remove require for old nrepl-eval-old (if accidentally included)
  - Ensure new nrepl-eval IS required: `[nrepl-mcp-server.mcp-server.tools.nrepl-eval]`
  - Verify tool self-registers properly on namespace load
  
- [x] **Create new nrepl_eval.clj**
  - Enhanced wrapper around send-message-get-result with timeout and message-id recovery
  - Parameters: `code` (required), `timeout` (optional, default 30000ms), `message-id` (optional, for recovery)
  - Construct nREPL eval message: `{:op "eval" :code code}`
  - Delegate to send-message-get-result handler or get-result-async for recovery
  - Extract and return eval result from response with proper error formatting
  
- [x] **Enhanced Implementation with Timeout Recovery**:
  ```clojure
  (defn handle
    "Evaluate Clojure code via nREPL using async message queue.
     Supports timeout recovery via message-id."
    [{:keys [code message-id timeout] :or {timeout 30000}}]
    (cond
      ;; Recovery path: check existing message for delayed result
      message-id (let [result (gra/handle {:message-id message-id :timeout timeout})] ...)
      ;; Normal path: send new message and wait for result  
      :else (let [message {:op "eval" :code code}
                  result (smgr/handle {:message message :timeout-ms timeout})] ...)))
  ```
  
- [x] **Testing & Validation**:
  - Test simple expressions: `(+ 1 2 3)` ✅
  - Test namespace operations: `*ns*` ✅  
  - Test side effects: `(def x 42)` ✅
  - Test timeout parameter: Custom timeout values work ✅
  - Test message-id recovery: Enhanced architecture supports recovery ✅
  - Verify result extraction and formatting ✅
  
- [x] **Benefits Achieved**:
  - Leverages proven async infrastructure ✅
  - Consistent with Phase 2b architecture ✅
  - Enhanced timeout recovery mechanism ✅
  - Clean abstraction with proper error handling ✅
  - No duplicate code or logic ✅
  - **Helpful connection error messages** with actionable guidance ✅

##### **Phase 2b.7: Comprehensive Tests & Error Handling & Timeouts** 🔄 **RENAMED**
- [ ] **Comprehensive Testing**
  - Handbook/manual for starting bridge & MCP & nREPL servers
  - Handbook/manual for explore and testing tools
  - Collect and organize test scripts for different components

- [ ] **Implement comprehensive error handling**
  - Connection lost during operation
  - Malformed messages
  - Queue overflow protection
  - Timeout at each phase
  - Dead letter queue for failed messages
  
- [ ] **Queue State Structure**:
  ```clojure
  ;; Send queue entry
  {:message-id "uuid-v7"
   :message {...}
   :timestamp (System/currentTimeMillis)
   :attempts 0}
  
  ;; Result queue entry  
  {:message-id "uuid-v7"
   :status :pending|:sending|:sent|:partial|:done|:failed|:timeout|:error
   :response nil|{...}
   :error nil|"error message"
   :created-at timestamp
   :sent-at nil|timestamp
   :completed-at nil|timestamp
   :bencode-sent nil|"raw bencode"}
  ```

##### **Testing Strategy**
- Use HTTP bridge for stateful testing across phases
- local-eval for queue introspection at each checkpoint
- Incremental testing - each phase can be tested independently
- Mock nREPL responses for phases 2-3 testing
- Real nREPL server for phase 5 integration testing

#### **2c: Connection Resilience & Monitoring** 🔄 **FUTURE PHASE**
- [ ] **Implement timeout/recovery handler**
  - Monitor pending states (`:pending-connect`, `:pending-disconnect`)
  - Reset to `:disconnected` after configurable timeout (default 10s)
  - Cleanup stuck futures and monitoring agents
  
- [ ] **Implement basic socket health monitoring**
  - Periodic socket status checks (`.isClosed()`, `.isConnected()`)
  - Detect remote disconnection via socket state
  - Update connection state accordingly
  
- [ ] **Advanced heartbeat monitoring** ⚠️ **COMPLEX IN ASYNC**
  - nREPL application-level heartbeat (simple eval operations)
  - Coordinate with async message queues
  - Handle heartbeat failures and recovery
  - **Note**: Adds significant complexity in async environment - defer until core queuing is stable

- [ ] **nREPL message monitoring integration** 🔍 **RESEARCH COMPLETE**
  - **Option 1**: Use `lambdaisland/nrepl-proxy` for message interception
  - **Option 2**: Custom nREPL middleware for server-side message logging  
  - **Option 3**: Built-in client logging (CIDER *nrepl-messages*, Calva output channel)
  - **Benefits**: Real-time nREPL traffic inspection, debug message flow, detect disconnections
  - **Implementation**: Add proxy layer or middleware to monitor message success/failure

**Testing Strategy**: Use Phase H3's unified testing framework with HTTP bridge to validate queue implementation. The stateful nature of HTTP mode allows testing queue persistence and async behavior across multiple operations.

### **Phase 3: Real nREPL Communication Layer**

- [ ] **4a: Connection Management**
  - Connect/disconnect/reconnect operations
  - Connection state tracking and health monitoring
- [ ] **4b: Send Queue Processing** 
  - Pick up messages from send-queue
  - Send to nREPL server
  - Update result queue with status
  - **Monitor with local-eval functions**
- [ ] **4c: Receive Handling**
  - Receive replies from nREPL server
  - Dispatch on errors and failures  
  - Wrap results in promises → result map-queue
  - Handle all possible nREPL reply types
  - Special handlers for complex responses

## 🏗️ **ARCHITECTURE EVOLUTION DOCUMENTATION**

**CRITICAL**: Update `docs/sync-async-queuing-architecture.md` as we implement to reflect:
- **Actual design decisions** and rationale
- **Real layer boundaries** and namespace organization
- **Implementation patterns** that emerge
- **Trade-offs** and alternatives considered
- **Testing strategies** that prove effective

## 🧪 **BEST PRACTICES FROM LEGACY PROJECT**

### 🚦 **Development Workflow Protocol**

**For EVERY implementation phase, follow this exact sequence:**

1. **Design & Plan** - Update architecture doc with approach
2. **Implement** - Write minimal working code
3. **Format code** - `./scripts/format.sh` 
4. **Lint code** - `./scripts/clojure-quality.sh` (includes format + lint)
5. **Tree-sitter validation** - Analyze code structure
6. **Comprehensive testing** - Complete test cycle (see below)
7. **Update documentation** - Reflect actual implementation
8. **Commit & Push** - `git add . && git commit && git push`
9. **Tag milestone** - `git tag v0.x.0-<phase> && git push --tags`

### 🧪 **Complete Testing Process**

**CRITICAL**: Start from clean slate every time to avoid port conflicts and state issues

**Stop All Servers:**
```bash
# Stop test nREPL server  
uv run python nrepl_test_server.py stop

# Stop any MCP proxy processes
pkill -f "mcp_nrepl_proxy/core.clj" || true

# Stop any Babashka nREPL processes  
pkill -f "bb.*nrepl" || true

# Verify clean state
ps aux | grep -E "(nrepl|bb.*src)"
```

**Test Success Criteria:**
- ✅ **100% pass rate required** - All tests must pass
- ✅ **Exit code 0** - No failures allowed
- ✅ **No server startup errors** - Clean server lifecycle
- ✅ **MCP integration working** - All implemented tools functional

**Test Failure Protocol:**
- 🛑 **STOP implementation process** - Do not commit/push
- 🔍 **Analyze failure logs** - Check server startup, MCP proxy, tool routing
- 🔧 **Fix issues** - Repair broken functionality
- 🔄 **Re-run testing process** - Start from clean slate again

### 🚨 **STOP CONDITIONS**
- If tests fail (not 100%) → Fix issues before proceeding
- If linting fails → Fix code quality issues
- If tree-sitter shows structural problems → Review implementation
- If architecture doc is outdated → Update documentation first

### 🚨 **WORKFLOW RULE: TODO PROGRESSION**

**CRITICAL REQUIREMENT**: Do not go to next todo without explicit user confirmation!

1. **Complete current task** - Mark as completed when finished
2. **Report completion** - Summarize what was accomplished  
3. **Wait for explicit confirmation** - User must explicitly approve moving to next task
4. **No automatic progression** - Never assume user wants to continue to next todo

### 📋 **TODO MANAGEMENT RULE**

**CRITICAL**: Always use TODO.md to manage/update/change/add todos

1. **NEVER use TodoWrite tool** - This is only for internal progress tracking
2. **ALWAYS update TODO.md document** - This is the single source of truth
3. **Edit TODO.md directly** when adding, changing, or updating todos
4. **Keep TODO.md synchronized** with actual project status

### 🎨 **CLOJURE CODE QUALITY & FORMATTING**

**CRITICAL REQUIREMENTS**:
1. Run `./format.sh` (cljfmt) BEFORE clj-kondo - formatting first!
2. After EVERY code change: format → lint → fix issues → format → lint again
3. cljfmt auto-fixes formatting, clj-kondo only reports issues

```bash
# After ANY Clojure code change:
./format.sh              # 1. Format code first
clj-kondo --lint src/    # 2. Lint to find issues

# If linting shows issues:
# 3. Fix the issues
./format.sh              # 4. Format again (fixes may need formatting)
clj-kondo --lint src/    # 5. Lint again to verify clean

# Only proceed when both tools show clean results!
```

### 🐍 **PYTHON CODE QUALITY & UV USAGE**

**CRITICAL REQUIREMENTS**: 
1. For every Python code change, run black and flake8!
2. Use UV wherever possible for Python package management and tool execution!

```bash
# After editing Python files (UV-first approach)
uv run black mcp_server_manager.py
uv run flake8 mcp_server_manager.py

# Install packages with UV
uv add black flake8

# Run Python scripts with UV
uv run python mcp_server_manager.py --help
```

### 🌳 **Tree-sitter Integration for Enhanced Coding**

**IMPORTANT**: Always leverage tree-sitter tools for semantic code analysis before making changes!

**Instead of just reading files:**
```python
# ✅ New approach - Semantic analysis first
symbols = mcp__tree_sitter__get_symbols(
    project="mcp-nrepl-joyride",
    file_path="new_implementation.clj", 
    symbol_types=["functions", "classes"]
)

# Find usage patterns
usage = mcp__tree_sitter__find_usage(
    project="mcp-nrepl-joyride",
    symbol="send-message",
    language="clojure"
)
```

### 📅 **IMPORTANT: Current Date Context**

**Today is 2025** - Always search for current 2025 information, not 2024!

- ✅ Use "2025" for current information
- ✅ Omit years entirely for general technical searches  
- ❌ Don't use "2024" unless specifically referencing historical information

### 🍎 **macOS Environment Notes**

**CRITICAL**: User runs macOS - certain Linux commands are NOT available:

- ❌ **NO `timeout` command** - Linux utility not available on macOS
- ❌ **NO `gtimeout` command** - GNU coreutils version not installed
- ✅ **Use direct commands** - No need for timeout wrappers in most cases
- ✅ **Built-in timeouts** - Many tools have `--timeout` parameters

**Common mistake pattern**: `echo 'command' | timeout 10 script` → Just use `echo 'command' | script`

### 🔤 **CRITICAL: Clojure Naming Convention - NO MIXING ALLOWED!**

**🚨 ABSOLUTE REQUIREMENT**: We had nasty, hard-to-find bugs from mixing hyphens and underscores in namespace names. **ZERO TOLERANCE** for violations.

**CLOJURE NAMING RULES (NEVER MIX!):**
- ✅ **Namespace names**: ONLY HYPHENS (`-`) - `nrepl-mcp-server.mcp-server.dispatch`
- ✅ **Variable names**: ONLY HYPHENS (`-`) - `local-eval`, `send-message-async`
- ✅ **Function names**: ONLY HYPHENS (`-`) - `get-active-connection`, `mark-connected!`
- ✅ **File/Directory names**: ONLY UNDERSCORES (`_`) - `mcp_server/dispatch.clj`

**EXAMPLES:**
```clojure
;; ✅ CORRECT - File: mcp_server/tools/debug_eval.clj
(ns nrepl-mcp-server.mcp-server.tools.local-eval)  ; HYPHENS in namespace

;; ❌ WRONG - Causes hard-to-find bugs
(ns nrepl-mcp-server.mcp_server.tools.debug_eval)  ; MIXED - NO!
```

**WHY THIS MATTERS:**
- **File system mapping**: `mcp_server/debug_eval.clj` → `nrepl-mcp-server.mcp-server.local-eval`
- **Clojure convention**: Namespace segments use hyphens, files use underscores
- **Bug prevention**: Mixing causes namespace resolution failures
- **clj-kondo compliance**: Eliminates "avoid underscore" warnings

**PYTHON (completely different rules):**
- ✅ **Everything**: ONLY UNDERSCORES (`_`) - `explore_mcp.py`, `extract_content()`
- ❌ **NEVER hyphens** - Python treats `-` as minus operator

**CRITICAL RULE**: 
- **Clojure namespaces**: HYPHENS ONLY (`-`)
- **File/Directory paths**: UNDERSCORES ONLY (`_`)  
- **Python everything**: UNDERSCORES ONLY (`_`)
- **NEVER MIX** - Causes bugs!

## 🛠️ **Reusable Testing Infrastructure**

**Preserve and enhance these working tools:**

### **stdio MCP Test Client** 
```bash
# Test any stdio MCP server
uv run python stdio_mcp_client.py \
  --server-cmd "bb -cp src src/new_mcp_server/core.clj" \
  --test-basic

# Call specific tools
uv run python stdio_mcp_client.py \
  --server-cmd "bb -cp src src/new_mcp_server/core.clj" \
  --tool local-eval --args '{"code": "(+ 1 2 3)"}'
```

### **Real nREPL Test Server**
```bash
# Managed test server with full capabilities
./nrepl-test start   # Auto-assigns port, tracks PID
./nrepl-test status  # Check connection info
./nrepl-test stop    # Clean shutdown
```

## 🎯 **SUCCESS METRICS**

**Phase completion criteria:**
- ✅ **All tests pass** (100% success rate)
- ✅ **Clean code quality** (no linting errors)
- ✅ **Architecture doc updated** (reflects actual implementation)
- ✅ **Proper layer separation** (no circular dependencies)
- ✅ **Comprehensive test coverage** (local-eval testable)

**Final success:**
- 🎯 **Proper 3-layer architecture** implemented
- 🎯 **Clean separation of concerns** (no architectural mess)
- 🎯 **Robust async queue system** with timeout handling
- 🎯 **Comprehensive testing** at all layers
- 🎯 **Living documentation** that matches implementation

---

## 📖 **Legacy Reference**

**Legacy code preserved in:**
- `/old/src/` - All original implementation for copy/paste reference
- `/old/tests/` - Original test scripts and approaches
- `TODO-old.md` - Complete history of previous implementation phases
- `CLAUDE.md` - All architectural insights and lessons learned

## 📁 **Project Organization**

**Clean directory structure:**
- `/src/mcp_server/` - 🆕 NEW - Clean implementation
- `/scripts/` - All executable scripts and utilities
- `/logs/` - Server and test log files
- `/old/` - Archived legacy code for reference
- `/docs/` - Architecture and design documentation

**Key patterns to reuse:**
- UUID v7 generation (`uuid_v7.clj`)
- Promise-based timeout handling (`(deref promise timeout-ms :timeout)`)
- Debug toolkit patterns (`debug-toolkit.clj`)
- MCP protocol compliance patterns
- Connection state management atoms

---

## 🌐 **HTTP-to-STDIO BRIDGE FOR STATEFUL TESTING** 

### **Problem Statement**

Current testing architecture using `stdio_mcp_client.py` creates fresh server instances for each test, preventing stateful testing needed for nREPL workflows. Need persistent server with HTTP interface for stateful nREPL connection testing.

### **Solution: mcp-proxy Bridge**

Use existing `mcp-proxy` tool to bridge HTTP requests to persistent stdio MCP server.

**Architecture**: `HTTP Client → mcp-proxy (port 3000) → stdio → nREPL MCP Server`

### **Phase H1: Setup HTTP-to-stdio Bridge** ✅ **COMPLETED**

- [x] **Install mcp-proxy tool**
  - ✅ Installed via `uv add mcp-proxy`
  - ✅ Verified compatibility with current nREPL MCP server
  - ✅ Tested basic proxy functionality with `--stateless` flag for true HTTP mode

- [x] **Create bridge startup script** (`./scripts/start-http-bridge.sh`)
  - ✅ Start mcp-proxy with correct server command and `--stateless` directive
  - ✅ Configure port 3000 for HTTP endpoint
  - ✅ Include health check and graceful shutdown via PID tracking
  - ✅ Log bridge and server startup to `logs/http-bridge.log`

- [x] **Test basic bridge functionality**
  - ✅ Verify HTTP endpoint responding at `localhost:3000/mcp/` (StreamableHTTP)
  - ✅ Test MCP tool listing via HTTP with proper headers
  - ✅ Confirm stdio server stays persistent across requests (stateful testing)
  - ✅ Validate error handling and connection recovery

**Key Technical Findings**:
- ✅ **StreamableHTTP vs SSE**: StreamableHTTP works without persistent connections, SSE requires them
- ✅ **Endpoint Discovery**: Correct endpoint is `/mcp/` (with trailing slash) 
- ✅ **Stateless Mode**: `--stateless` flag enables true HTTP request/response pattern
- ✅ **Header Requirements**: Must include `Accept: application/json, text/event-stream`
- ✅ **Bridge Scripts**: Complete lifecycle management (start/stop/status)

### **Phase H2: HTTP Client Testing Infrastructure** ✅ **COMPLETED**

- [x] **Setup HTTP MCP client** 
  - ✅ Created comprehensive `scripts/test_http_bridge.py` test suite
  - ✅ Configure to connect to bridge at `localhost:3000/mcp/`
  - ✅ Verify compatibility with bridge protocol (StreamableHTTP)
  - ✅ Test basic tool calling functionality via HTTP

- [x] **Create stateful test framework**
  - ✅ Design test framework for persistent server testing with HTTPBridgeTestSuite class
  - ✅ Implement connection state tracking via HTTP requests
  - ✅ Create async HTTP client with proper timeout handling
  - ✅ Build structured test result reporting and validation

- [x] **Write comprehensive stateful tests**
  - ✅ Variable persistence testing (define in one request, access in another)
  - ✅ Function persistence testing (define function, call in separate request)
  - ✅ Multi-step evaluations with state persistence validation
  - ✅ Cross-namespace introspection across multiple HTTP calls
  - ✅ Registry consistency testing (internal state vs MCP protocol)
  - ✅ Error handling and graceful failure modes

**Test Suite Achievements**:
- ✅ **18 comprehensive tests** (was 17, added registry consistency test)
- ✅ **100% pass rate** across all test modes (basic, quick, full, performance)
- ✅ **Stateful validation**: Variables and functions persist across HTTP requests
- ✅ **Registry consistency**: Internal tool registry matches MCP tools/list
- ✅ **Cross-namespace introspection**: Can access `@nrepl-mcp_server.state.tool-registry/tool-registry`
- ✅ **Multiple test modes**: `--basic`, `--quick`, `--performance` for different scenarios

### **Phase H3: Stateful Testing for Phase 2b Implementation** 🔄 **INTEGRATED**

**Strategy**: Use H3's stateful testing infrastructure to develop and validate Phase 2b's message queue implementation.

- [ ] **Test Phase 2b Message Queue via Unified Tester**
  - Use HTTP bridge for stateful queue testing
  - Test send-message-async → get message-id flow
  - Validate get-result-async with promise waiting
  - Use local-eval to inspect queue state between operations
  - Verify queue persistence across multiple HTTP requests

- [ ] **Async Queue Testing Scenarios**
  - Send message → verify queue contains it → get result
  - Multiple messages with unique IDs and correlation
  - Timeout handling and error conditions
  - Queue overflow and backpressure testing
  - Race conditions and concurrent access

- [ ] **Integration Testing Pattern**
  - Implement Phase 2b incrementally
  - Test each component with unified tester
  - Use stateful HTTP mode to validate queue behavior
  - Debug-eval for real-time queue introspection
  - Build comprehensive test suite as we implement

### **Phase H4: Production Testing Setup**

- [ ] **Create testing utilities**
  - Bridge health monitoring scripts
  - Automated test suite runner
  - Performance benchmarking tools
  - Error reporting and diagnostics

- [ ] **Documentation and guides**
  - Bridge setup and configuration guide
  - Stateful testing best practices
  - Troubleshooting common issues
  - Developer workflow integration

- [ ] **CI/CD Integration**
  - Add bridge testing to automated test suite
  - Include stateful tests in Phase 2/3 validation
  - Performance regression testing
  - Cross-platform compatibility testing

### **Benefits of Bridge Approach**

✅ **Persistent Server**: Single server instance across multiple tests
✅ **Stateful Testing**: Variables/functions persist between requests
✅ **HTTP Convenience**: Use existing HTTP tooling (curl, Postman, etc.)
✅ **stdio Compatibility**: Tests stdio interface Claude Desktop will use
✅ **No Custom Code**: Uses existing `mcp-proxy` tool
✅ **Development Friendly**: Easy debugging and exploration
✅ **Production Testing**: Tests real async nREPL workflows

### **Success Criteria**

- [ ] Bridge successfully proxies HTTP → stdio → nREPL MCP server
- [ ] Multiple HTTP requests maintain server state persistence
- [ ] Can define variables in one request, use in subsequent requests
- [ ] nREPL connections remain active across bridge requests
- [ ] All Phase 2b/3 functionality testable via HTTP interface
- [ ] Bridge setup takes <5 minutes from clean environment

---

## 🧹 **CODE CLEANUP TASKS**

### **Immediate Cleanup** (2025-08-18) ✅ **COMPLETED**

- [x] **Remove unused nrepl_eval_old.clj** ✅ COMPLETED
  - File: `src/nrepl_mcp_server/mcp_server/tools/nrepl_eval_old.clj`
  - Status: NOT registered in tool registry, NOT imported anywhere
  - Action: Delete file completely
  - Rationale: Replaced by simpler `nrepl-eval.clj` using async message queue

- [x] **Remove 13 unused functions from operations.clj** ✅ COMPLETED
  - **Dead code removed**: `close-session`, `nrepl-load-file`, `create-session`, `describe-server`, `doc`, `source`, `complete`, `apropos`, `require-ns`, `disconnect-from-nrepl`, `interrupt`, `connect-to-nrepl`, `stacktrace`
  - **Kept used functions**: `eval-code`, `eval-with-timeout`, `health-check`, `send-message`, `send-message-fire-and-forget`
  - **Tree-sitter analysis**: Confirmed identical results between pre/post analysis - no differences found
  - **Replacement**: All removed functions can be replicated using enhanced `nrepl-send-message` tool with comprehensive operation examples
  - **Code size reduction**: ~300 lines of dead code removed from operations.clj

- [x] **Run code quality checks after cleanup** ✅ COMPLETED
  - Run `./scripts/clojure-quality.sh` to format and lint
  - Fixed unused namespace import warning
  - Result: Clean code with only 1 intentional warning (redundant let)
  - Functionality verified: All tools working correctly after cleanup

### **Explicit Registration Refactoring** (2025-08-18) 🔄 **IN PROGRESS**

**Objective**: Convert all MCP tools from implicit self-registration to explicit centralized registration pattern.

#### **Phase 1: Analysis and Planning** ✅ **COMPLETED**
- [x] **Analyze current tool registration patterns across all MCP tools**
- [x] **Create comprehensive plan for explicit registration refactoring**
- [x] **Establish testing strategy for each phase**

#### **Phase 2: Tool-by-Tool Conversion** 🔄 **IN PROGRESS**
- [ ] **Extract tool metadata from local-eval tool**
- [ ] **Extract tool metadata from local-load-file tool**  
- [ ] **Extract tool metadata from nrepl-connection tool**
- [ ] **Extract tool metadata from nrepl-eval tool**
- [ ] **Extract tool metadata from nrepl-send-message-async tool**
- [ ] **Extract tool metadata from nrepl-get-result-async tool**
- [x] **Extract tool metadata from nrepl-send-message tool** ✅ COMPLETED

#### **Phase 3: Central Registration Update**
- [ ] **Update register-all-tools function with all tool registrations**
- [ ] **Test all tools work correctly after explicit registration**

#### **Phase 4: Cleanup**
- [ ] **Remove commented registration code from all tool files**
- [ ] **Remove tool-registry dependencies from tool files where possible**
- [ ] **Run comprehensive testing suite**
- [ ] **Format and lint all modified Clojure code**

**Implementation Pattern (Per Tool)**:
1. Extract metadata as separate `def` statements
2. Comment out self-registration calls
3. Add to central `register-all-tools` function
4. Test functionality preserved
5. Clean up when all tools converted

**Benefits**:
- Single point of control for all tool registrations
- Pure tool files (no side effects during namespace loading)
- Better testability (tools can be tested in isolation)
- Enhanced introspection and maintainability

### **Future Cleanup Opportunities**

- [ ] **Analyze unused private functions**
  - Use tree-sitter to identify potentially unused `defn-` functions
  - Review each for actual usage before removing
  - Some may be called dynamically or via reflection

- [ ] **Review legacy backward compatibility code**
  - Check for functions added during Phase 2a.10 migration
  - Remove any temporary compatibility shims no longer needed
  - Document any intentionally preserved legacy interfaces

## 🔍 **RESEARCH ITEMS**

- [ ] **Investigate ls-sessions scope** - Determine if `ls-sessions` lists ALL server sessions or only current connection's sessions via testing or nREPL documentation

- [x] **Investigate HTTP MCP server option** - ✅ COMPLETED - Found `mcp-proxy` bridge solution
  - **Result**: Use existing `mcp-proxy` tool for HTTP-to-stdio bridging
  - **Implementation**: Added Phase H1-H4 plan above

- [ ] **Investigate auto-registration of MCP tools** - Research if tool functions could self-register when required by dispatch.clj (e.g., using macros or metadata). For now, manual registration in dispatch.clj is fine.

---

*This TODO list follows the living documentation principle - updated as implementation proceeds to reflect actual decisions and patterns that emerge.*