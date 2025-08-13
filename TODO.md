# MCP-nREPL Project TODO List - CLEAN SLATE REFACTORING

Last updated: 2025-08-13
**Status**: 🚀 **CLEAN SLATE REFACTORING** - Building proper 3-layer architecture from scratch

## 🎯 **PROJECT MISSION: CLEAN ARCHITECTURE REBUILD**

**Goal**: Implement the proper 3-layer sync-async queuing architecture without the circular dependencies and architectural mess discovered in the legacy codebase.

**Strategy**: 
- ✅ Archive legacy code to `/old/` for reference
- 🏗️ Build incrementally with proper testing at each phase  
- 📝 **Update architecture doc as we implement** - living documentation
- 🧪 Test-driven development with comprehensive coverage

## 🚀 **CLEAN SLATE REFACTORING PHASES**

### **Phase 1: Archive Legacy & Minimal Foundation** ✅ **COMPLETED**

- [x] **Move /src to /old/src** (readonly archive for copy/paste reference)
- [x] **Move test scripts to /old tree** (preserve for reference)
- [x] **Create new minimal project structure**
- [x] **Implement absolute minimum MCP server** (debug-eval + debug-load-file only)
- [x] **Create test wrapper scripts** (avoid permission prompts) - `./scripts/test-quick.sh` and `./scripts/test-comprehensive.sh`
- [x] **Write comprehensive test coverage** for minimal server - **Phase-specific test suites created**:
- [x] **Implement stdout/stderr capture** - Protocol-complete implementation using `with-out-str` approach
- [x] **Document MCP protocol findings** - stderr does NOT interfere with MCP stdio communication
- [x] **Clean up code quality** - All clj-kondo warnings resolved, tree-sitter validated
  - `./scripts/test-phase1.sh` - **19 comprehensive tests** including **MCP introspection via debug-eval**:
    - Basic MCP protocol (2 tests)
    - debug-eval functionality (7 tests) 
    - debug-load-file functionality (3 tests)
    - **MCP introspection** (5 tests) - namespace inspection, tool metadata simulation, server state
    - Advanced integration (2 tests)
  - `./scripts/test-phase2.sh` - Future core MCP functions (nrepl-connect, send-message, get-result)
  - `./scripts/test-phase3.sh` - Future real nREPL communication tests
  - `./scripts/test-master.sh` - **Master orchestrator** with individual phase control

### **Phase 2: Three Core MCP Functions** 🏗️ **IN PROGRESS**

#### **2a: Reactive Connection Management** ✅ **COMPLETED**
- [x] **Create state namespace** (`src/mcp_server/state.clj`)
  - Connection state atom with status tracking
  - Helper functions for state updates
  - Expose state for debug-eval introspection
  
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
      debug_eval.clj           ; debug-eval tool
      debug_load_file.clj      ; debug-load-file tool
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

#### **2b: Message Queue Infrastructure** (After namespace refinement)
- [ ] **Implement send-message-async tool** 
  - Hand-off message to queue → get message-id
  - UUID v7 generation in utils namespace
  - Fail if no connection (check state atom)
  - **Isolated from actual nREPL communication**
  
- [ ] **Implement get-result-async tool**
  - Create result queue with promise waiting
  - Wait on promise for message-id results
  - **Test without nREPL** - use debug-eval to put items on queue

- [ ] **Implement send-message-sync wrapper**
  - Combines send-message-async + get-result-async
  - Single synchronous call for simple use cases

### **Phase 3: Real nREPL Communication Layer**

- [ ] **4a: Connection Management**
  - Connect/disconnect/reconnect operations
  - Connection state tracking and health monitoring
- [ ] **4b: Send Queue Processing** 
  - Pick up messages from send-queue
  - Send to nREPL server
  - Update result queue with status
  - **Monitor with debug-eval functions**
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
3. **Format code** - `./format.sh` 
4. **Lint code** - `./clojure-quality.sh` (includes format + lint)
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
  --tool debug-eval --args '{"code": "(+ 1 2 3)"}'
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
- ✅ **Comprehensive test coverage** (debug-eval testable)

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

## 🔍 **RESEARCH ITEMS**

- [ ] **Investigate ls-sessions scope** - Determine if `ls-sessions` lists ALL server sessions or only current connection's sessions via testing or nREPL documentation

- [ ] **Investigate HTTP MCP server option** - Research implementing HTTP-based MCP server as alternative to stdio for easier testing/debugging and potential web integration

- [ ] **Investigate auto-registration of MCP tools** - Research if tool functions could self-register when required by dispatch.clj (e.g., using macros or metadata). For now, manual registration in dispatch.clj is fine.

---

*This TODO list follows the living documentation principle - updated as implementation proceeds to reflect actual decisions and patterns that emerge.*