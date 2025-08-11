# MCP-nREPL Project TODO List

Last updated: 2025-08-10
**Status**: Phase 1 ready to implement - tree-sitter enhanced Clojure support validated

## ✅ CRITICAL MILESTONE ACHIEVED - Async Architecture WORKING

### Phase 1: Transport Layer Foundation ✅ **COMPLETED** 
**Context**: `collect-responses` infinite loop bottleneck resolved
**Strategy**: Small steps with comprehensive testing
**CONFIRMED**: Full async support verified in Babashka runtime (promises, futures, Java concurrency)

- [x] **Step 1: Add timeout parameter to collect-responses function (minimal change)** ✅
  - [x] Test Step 1: Verify timeout parameter works with simple timeout test ✅
- [x] **Step 2: Create collect-responses-async using promise-based timeout** ✅
  - [x] Test Step 2: Unit test promise timeout behavior with `(deref promise timeout-ms :timeout)` ✅
- [x] **Step 3: Create send-message-async calling collect-responses-async** ✅
  - [x] Test Step 3: Integration test with multiple nREPL servers (real nREPL server tested) ✅
- [x] **Step 4: Add connection state management (basic atom)** ✅
  - [x] Test Step 4: Verify state tracking works correctly ✅
  - [x] **Enhancement: RFC 9562 UUID v7 with NO fallback guarantee** ✅
    - [x] Format: `<uuid-v7>-<operation>` enables perfect temporal sorting ✅
    - [x] Example: `0198974c-d9d6-7000-8001-00006c73abd1-eval` ✅
    - [x] 26-bit sequence counter = 67 million unique IDs per millisecond ✅
    - [x] NO random fallback - waits for next millisecond on overflow ✅
    - [x] Thread-safe compare-and-swap (CAS) atomic operations ✅
    - [x] Separate uuid_v7.clj module for reusability ✅

**Implementation Method**: Use Babashka's verified async capabilities: `(deref promise timeout-ms :timeout)` 
**Preserve**: Keep existing `send-message` function for backward compatibility
**Quality Gates**: `./format.sh && ./clojure-quality.sh` after each step
**Testing Strategy**: Multi-environment testing:
- **bb-nrepl-server** (SCI): `BABASHKA_CLASSPATH=src bb src/mcp_nrepl_proxy/core.clj` - Limited sync-only 
- **Full Clojure nREPL** (Managed): `./nrepl-test start` - Complete async capabilities with PID tracking
- **Production nREPL servers**: Real-world Clojure/JVM environments (Calva, CIDER, etc.)
**Commit**: `feat: implement send-message-async with timeout handling`
**Tag**: `v0.x.0-async-transport`

### Phase 2: Internal Layer Implementation ✅ **COMPLETED**
- [x] **Create nrepl-raw-async-int as pure nREPL interface** ✅ **VERIFIED WORKING**
  - [x] ✅ **COMPLETED**: UUID v7 import and generate-id function updated
  - [x] ✅ **COMPLETED**: Message queue atoms added (pending-messages, message-records, failure-records)
  - [x] ✅ **COMPLETED**: track-pending-message function with atomic queue management
  - [x] ✅ **VALIDATED**: All changes follow Clojure Quality Protocol (format.sh + clj-kondo)
  - [x] ✅ **VERIFIED**: 100% test success rate maintained (7/7 tests passing)
  - [x] ✅ **COMPLETED**: Complete queue lifecycle management implementation
    - [x] ✅ **COMPLETED**: update-message-status function for state transitions
    - [x] ✅ **COMPLETED**: mark-connection-messages-failed function for connection cleanup
    - [x] ✅ **COMPLETED**: close-connection updated to mark pending messages as failed
    - [x] ✅ **COMPLETED**: send-message-async with full queue lifecycle tracking
    - [x] ✅ **IMPLEMENTED**: Message states: `:pending`, `:sending`, `:sent`, `:completed`, `:failed`, `:expired`
    - [x] ✅ **IMPLEMENTED**: Error types: `:connection-closed`, `:connection-lost`, `:connection-reset`, `:timeout`, `:communication-error`
    - [x] ✅ **IMPLEMENTED**: Failure records with temporal ordering and detailed error context
    - [x] ✅ **IMPLEMENTED**: Atomic state management with proper message lifecycle tracking
  - [x] ✅ **COMPLETED**: Write integration tests with real nREPL server for connection lifecycle scenarios
    - [x] ✅ **COMPLETED**: Comprehensive integration test framework (`test-connection-lifecycle-simple.clj`)
    - [x] ✅ **COMPLETED**: Timeout handling tests (PASSED - verifies message expiration)
    - [x] ✅ **COMPLETED**: Error recovery tests (PASSED - verifies graceful error handling)
    - [x] ✅ **COMPLETED**: Connection closure tests (validated - pending message marking)
    - [x] ✅ **VALIDATED**: Queue lifecycle management with real nREPL servers
    - [x] ✅ **PROVEN**: Critical timeout and error handling functionality working correctly
  - **Commit**: `feat: implement comprehensive connection lifecycle integration tests`
  - **Tag**: `v0.x.0-async-internal-complete`

### Phase 3: MCP Layer Implementation  
- [ ] **Create nrepl-raw-async MCP function with full protocol compliance**
  - [ ] Implement `nrepl-raw-async` MCP function with validation
  - [ ] Add optional timeout_ms parameter (default 30000ms) for AI control
  - [ ] Add MCP-compliant error formatting
  - [ ] Comprehensive parameter validation
  - [ ] Write MCP integration tests
  - [ ] Test: MCP protocol compliance, timeout parameter flow, error formatting
  - **Commit**: `feat: implement nrepl-raw-async MCP function`
  - **Tag**: `v0.x.0-async-mcp`

### Phase 4: Migration and Testing
- [ ] **Demonstrate backward compatibility and performance**
  - [ ] Create side-by-side comparison tests
  - [ ] Run performance benchmarks
  - [ ] Document migration strategy and rollback procedures
  - [ ] Test: existing functions work, equivalent functionality, performance <10% overhead
  - **Commit**: `feat: complete async architecture with migration path`
  - **Tag**: `v0.x.0-async-complete`

## High Priority

## Medium Priority

- [ ] **Add nrepl-session-clone tool** for creating parallel evaluation contexts
- [ ] **Improve error handling** with better context and suggestions
- [ ] **Add file watching capability** to auto-reload changed Clojure files
- [ ] **Add bulk operation support** for loading multiple files at once

## Lower Priority

- [ ] **Add logging and monitoring dashboard**
- [ ] **Create deployment script for production**
- [ ] **Add configuration management system**
- [ ] **Investigate git MCP server applicability** for enhanced local git workflow integration

## Future Considerations (Low Priority)

- [ ] **Consider daemon-based persistent MCP connections**
- [ ] **Consider unified MCP/nREPL client with direct nREPL support**

---

## 🎯 BREAKTHROUGH RESOLUTION (2025-08-11) ✅

**CRITICAL DISCOVERY**: The "failing queue lifecycle tests" were caused by a fake nREPL server with hardcoded wrong responses, NOT actual async queue issues!

**Root Cause**: Previous Claude instance created `test-nrepl-server.clj` with fake hardcoded responses:
- `(+ 2 3)` matched pattern `"(+ "` and returned hardcoded `"30"` instead of calculating `5`
- Tests timed out because they expected real evaluation, not fake responses

**Resolution**:
- ✅ **Deleted fake server** - Removed `test-nrepl-server.clj` with hardcoded responses
- ✅ **Updated all references** - Changed bb.edn, test files to use real nREPL server (`./nrepl-test`)  
- ✅ **Disabled port 3000 conflicts** - Removed bb-nrepl-server interference
- ✅ **Verified correct architecture**: 
  - Real nREPL server runs independently (port 61910)
  - MCP proxy runs independently in stdio mode  
  - `nrepl-connect` function connects them explicitly
  - `nrepl-eval "(+ 2 3)"` correctly returns `5` ✅

**ASYNC QUEUE SYSTEM WORKING PERFECTLY**: All timeout handling, promise-based async, and queue lifecycle management verified working with real nREPL server.

## Recently Completed ✅

- [x] **FIXED: Broken pipe errors in test infrastructure** - ROOT CAUSE: Port conflicts with bb-nrepl-server (both using port 3000)
  - **Solution**: Implemented dynamic port allocation with `port_utils.py` 
  - **Result**: 100% test success rate (was 81.8% with broken pipe errors)
  - **Infrastructure**: Updated test lifecycle and debug scripts with smart port detection
  - **Key Insight**: bb-nrepl-server defaults to port 3000, tests need dynamic ports to avoid conflicts
- [x] **Created comprehensive nREPL testing infrastructure**
  - **Test Server Manager**: `./nrepl-test [start|stop|restart|status]` - Full Clojure capabilities
  - **Lifecycle Test Suite**: `./test-lifecycle [quick|server]` - Complete functionality testing
  - **Features**: Auto-assigns ports, PID tracking, cleanup, comprehensive validation
  - **Test Coverage**: Server lifecycle, MCP integration, Clojure capabilities (promises, futures, Java interop)
  - **Verified**: All server lifecycle tests pass (100% success rate in 5.9s)
  - **Ready**: Complete testing infrastructure for Phase 1 async implementation
- [x] **Verified Babashka runtime async capabilities** - promises, futures, Java concurrency all work
- [x] **Distinguished two runtime environments**: MCP server (full async) vs bb-nrepl-server (SCI limited)
- [x] **Updated architecture documents** with corrected environment understanding
- [x] **Test Python linters to determine which detect blank lines between decorators and functions**
- [x] **Create reusable quality scripts for Python and Clojure instead of one-off CLI commands**
- [x] **Add cljfmt code formatting to project - configure and document workflow**
- [x] **Create comprehensive sync-async queuing architecture design document**
- [x] **Add nrepl-apropos tool for symbol discovery and exploration**
- [x] **Add nrepl-stacktrace tool for better error debugging**
- [x] **Create AI Assistant MCP Function Cookbook for Claude and other AI agents**
- [x] **Create comprehensive VS Code Joyride MCP cookbook for human developers and AI assistants**
- [x] **Implement additional nREPL tools: interrupt, doc, source, complete**
- [x] **Implement nrepl-load-file tool for loading Clojure source files**
- [x] **Add nrepl-require tool for dynamically loading namespaces/libraries**

---

*This TODO list is managed through the TodoWrite tool and should be updated whenever tasks are started, completed, or priorities change.*