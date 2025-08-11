# Claude Code Integration Notes

This file contains important information for Claude Code when working with the MCP-nREPL Joyride project.

## 🎯 Project Status: ✅ COMPLETED + ENHANCED (January 2025)

The MCP-nREPL Joyride bridge is fully implemented, tested, and enhanced with explicit connection architecture. All core functionality is working with **100% test reliability**.

## 🏗️ Implementation Approach

### ✅ Completed Features
- **Pure Babashka implementation** - Fast startup, no TypeScript dependencies
- **Custom nREPL client** - Built from scratch for Babashka compatibility
- **Full MCP protocol support** - JSON-RPC 2.0 compliant server
- **Joyride/Calva integration** - Complete VS Code API support
- **Session management** - Isolated nREPL evaluation contexts
- **Comprehensive testing** - Integration tests with mock servers
- **🆕 Explicit connection architecture** - Robust connection management, no auto-discovery
- **🆕 Dynamic port allocation** - Eliminates port conflicts and broken pipe errors
- **🆕 100% test reliability** - Complete test suite passes consistently

### 🔧 Key Technical Decisions
1. **No TypeScript** - User explicitly rejected TypeScript, implemented pure Babashka solution
2. **Custom nREPL client** - Standard clients had JVM dependencies incompatible with Babashka
3. **Socket-based communication** - Raw TCP sockets for nREPL protocol
4. **Enhanced mock servers** - Realistic Joyride/Calva simulation for testing
5. **🆕 Explicit connections only** - Eliminated auto-discovery for reliability (Calva-style jack-in pattern)
6. **🆕 Dynamic port allocation** - Prevents conflicts with bb-nrepl-server on port 3000

## 🧪 Testing Strategy

### Test Commands
```bash
# 🆕 RECOMMENDED: Comprehensive Python test suite
python3 test_nrepl_lifecycle.py                 # Full test suite (11 tests)
python3 test_nrepl_lifecycle.py --quick         # Skip long-running tests (7 tests)  
python3 test_nrepl_lifecycle.py --server-only   # Server lifecycle only (5 tests)

# Legacy Babashka integration tests (still functional)
bb -cp src run-integration-test.clj
bb -cp src test-joyride-integration.clj

# Manual server management
python3 nrepl_test_server.py [start|stop|restart|status]
bb test-nrepl-server
bb joyride-mock-server
```

### Test Coverage
- ✅ Basic nREPL operations (eval, clone, close, describe)
- ✅ VS Code API integration (commands, workspace operations)
- ✅ Calva middleware (info, completion, load-file)
- ✅ Session management and isolation
- ✅ MCP proxy functionality
- ✅ Error handling and connection management
- ✅ **🆕 Server lifecycle management** (start/stop/restart/status with PID tracking)
- ✅ **🆕 Explicit connection testing** (nrepl-connect tool with port parameter)
- ✅ **🆕 Full Clojure capabilities** (promises, futures, Java interop)
- ✅ **🆕 Port conflict resolution** (dynamic allocation prevents broken pipes)

### 🐍 MCP Test Client (`mcp_test_client.py`)
**Python-based MCP protocol client for testing and automation**

#### Key Capabilities
- **Session Management**: Persistent MCP session handling with automatic recovery
- **Interactive Mode**: Command-line interface for testing MCP servers
- **Tool Discovery**: Automatically lists available MCP tools and parameters
- **Direct Tool Calling**: Execute MCP tools with JSON arguments
- **REST API Comparison**: Compare MCP vs REST endpoint results
- **Session Persistence**: Saves session IDs across invocations for stable testing

#### Usage Commands
```bash
# Interactive mode
python3 mcp_test_client.py --mcp-url http://localhost:3000/mcp -i

# Predefined tests
python3 mcp_test_client.py --mcp-url http://localhost:3000/mcp -t

# Programmatic usage
from mcp_test_client import MCPTestClient
client = MCPTestClient('http://localhost:3000/mcp')
await client.call_tool('nrepl-eval', {'code': '(+ 1 2 3)'})
```

#### Interactive Commands
- `list` - List available tools
- `call <tool_name> <json_args>` - Call a tool
- `rest <endpoint> <json_args>` - Call REST API
- `compare <tool_name> <rest_endpoint> <json_args>` - Compare MCP vs REST
- `quit` - Exit

**Essential for testing MCP-nREPL bridge and VS Code integration without curl commands.**

## 🚨 CRITICAL ARCHITECTURE CHANGE (January 2025)

### 🎯 Explicit Connection Architecture Implementation

**Problem Solved**: Eliminated broken pipe errors and achieved 100% test reliability.

**Root Cause**: Port conflicts and brittle auto-discovery mechanisms were causing 18.2% test failure rate with "Broken pipe (type: class java.net.SocketException)" errors.

**Solution Implemented**:
1. **Removed auto-discovery** - No more brittle port detection mechanisms
2. **Explicit nrepl-connect** - Tool requires explicit port parameter (Calva-style jack-in pattern)
3. **Dynamic port allocation** - Uses `port_utils.py` to prevent conflicts with bb-nrepl-server
4. **Standardized file naming** - `.test-nrepl-server-port` instead of generic `.nrepl-port`

**Results**:
- ✅ **100% test reliability** (was 81.8% with broken pipes)
- ✅ **11/11 tests pass** consistently across all test modes
- ✅ **Zero port conflicts** with bb-nrepl-server
- ✅ **Robust connection management** with explicit control

**Key Files Modified**:
- `src/mcp_nrepl_proxy/core.clj` - Explicit connection, no auto-discovery
- `test_nrepl_lifecycle.py` - Comprehensive test suite with explicit connections
- `port_utils.py` - Dynamic port allocation to prevent conflicts
- `nrepl_test_server.py` - Enhanced server lifecycle management

**Architecture Pattern**:
```clojure
;; OLD (brittle): Auto-discovery with env vars
NREPL_PORT=3000 # Caused conflicts

;; NEW (robust): Explicit connection with dynamic ports
(mcp-tool "nrepl-connect" {:port 56388}) # Explicit, reliable
```

## 📚 Lessons Learned

### Technical Challenges Solved
1. **nREPL Library Incompatibility**
   - Problem: `java.security.cert.Certificate` class not available in Babashka
   - Solution: Created custom nREPL client using basic socket communication

2. **Stateful vs Stateless Impedance Mismatch**
   - Problem: nREPL is stateful, MCP is stateless
   - Solution: Session management with automatic session creation/tracking

3. **Classpath Issues in Subprocess Calls**
   - Problem: MCP server couldn't find modules when run via subprocess
   - Solution: Added `-cp src` to all process calls in tests

4. **Function Definition Order**
   - Problem: Forward references in mock server
   - Solution: Careful ordering of function definitions

5. **🚨 CRITICAL: Documentation Preservation**
   - Problem: Accidentally deleted original planning documents during cleanup
   - Solution: Reconstructed from memory and implemented strict protection policy
   - **LESSON**: NEVER DELETE planning/design/todo documents - they are valuable project history

6. **🆕 CRITICAL: Broken Pipe Root Cause Analysis**
   - Problem: 18.2% test failure rate with "Broken pipe" socket exceptions
   - Root Cause: Port 3000 conflicts with bb-nrepl-server causing "Address already in use"
   - Solution: Dynamic port allocation and explicit connection architecture
   - **LESSON**: Two conflicting port discovery methods = race conditions and failures

### Architecture Insights
- **Babashka is excellent for this use case** - Fast startup, good library support
- **Socket-based nREPL works well** - Simpler than full protocol implementations
- **Mock servers are essential** - Enable comprehensive testing without VS Code dependency
- **JSON-RPC 2.0 is straightforward** - Clean protocol for MCP implementation
- **Documentation preservation is critical** - All planning documents must be protected
- **🆕 Explicit connections > Auto-discovery** - Calva-style jack-in pattern is more reliable than port detection
- **🆕 Dynamic port allocation is essential** - Eliminates hardcoded conflicts and race conditions
- **🆕 Comprehensive testing reveals edge cases** - 100% reliability requires systematic test coverage

## 🔄 Development Workflow

### When Making Changes
1. **Always test both servers** - Simple and enhanced mock servers
2. **Run comprehensive tests** - Both integration test suites
3. **Check error handling** - Verify graceful failure modes
4. **Validate MCP compliance** - Ensure JSON-RPC 2.0 conformance

### Debugging Tips
- Use `MCP_DEBUG=true` environment variable for verbose logging
- Check `.nrepl-port` file creation for server startup issues
- Monitor stderr for detailed nREPL communication logs
- Test direct nREPL connection before MCP proxy testing

## 🚀 Future Enhancements (Optional)

### Potential Improvements
- **Connection pooling** - Reuse nREPL connections across MCP calls
- **Caching** - Cache describe/info results for better performance
- **Configuration** - Allow custom nREPL discovery patterns
- **Metrics** - Add performance monitoring and usage statistics

### Integration Ideas
- **VS Code extension** - Direct integration with Claude Code
- **Workspace templates** - Pre-configured project setups
- **Documentation generation** - Auto-generate docs from nREPL introspection

## 📖 Key Files

### Core Implementation
- `src/mcp_nrepl_proxy/core.clj` - Main MCP server with JSON-RPC handling
- `src/mcp_nrepl_proxy/nrepl_client.clj` - Custom Babashka-compatible nREPL client

### Test Infrastructure
- `test-nrepl-server.clj` - Simple test nREPL server
- `joyride-mock-server.clj` - Enhanced Joyride/Calva mock server
- `run-integration-test.clj` - Basic integration test suite
- `test-joyride-integration.clj` - Comprehensive Joyride test suite
- `mcp_test_client.py` - Python MCP protocol client for testing and automation

### Original Planning Documents (RECOVERED)
- `docs/babashka_mcp_nrepl_implementation_plan.md` - Core implementation strategy
- `docs/claude_implementation_guide_ai_mcp_nrepl_vscode.md` - Technical integration guide
- `docs/claude_vscode_use_cases.md` - Comprehensive use case analysis
- `docs/comprehensive_clojure_mcp_analysis.md` - Technical ecosystem analysis

### Implementation Documentation
- `docs/lessons-learned.md` - Technical insights and best practices
- `docs/test-results.md` - Integration test documentation
- `docs/vscode-test-cases.md` - VS Code testing specifications

### Configuration
- `bb.edn` - Babashka project configuration with tasks
- `README.md` - User documentation and setup instructions

## 💡 Tips for Claude Code

### Common Operations
```clojure
;; Connect and evaluate
(+ 1 2 3)

;; VS Code commands
(joyride.core/execute-command "workbench.action.quickOpen")

;; Workspace queries
(joyride/workspace-root)
(joyride/workspace-files "**/*.clj")

;; VS Code API access
(-> js/vscode.window.activeTextEditor .-document .-fileName)
```

### Error Patterns to Watch For
- Connection timeouts (check if Joyride nREPL is running)
- Classpath issues (ensure `-cp src` for subprocess calls)
- Session leaks (close connections properly)
- Port conflicts (clean up `.nrepl-port` files)

## ✅ Validation Checklist

Before considering the project complete, ensure:
- [x] Both integration test suites pass completely
- [x] MCP server starts without errors
- [x] ~~nREPL auto-discovery works via `.nrepl-port`~~ **REPLACED with explicit connection**
- [x] **🆕 Explicit nrepl-connect works with required port parameter**
- [x] **🆕 Full test suite achieves 100% reliability (11/11 tests)**
- [x] **🆕 Dynamic port allocation prevents bb-nrepl-server conflicts**
- [x] VS Code API calls execute successfully
- [x] Session isolation functions correctly
- [x] Error handling is graceful
- [x] Documentation is up to date
- [x] **🆕 Python code quality (linting, formatting) meets standards**

The project is fully functional, enhanced with explicit connection architecture, and ready for production use with 100% test reliability.

## 📸 Snapshot Command

When I say **"snapshot!"**, it means:
1. **commit** - Commit current changes with descriptive message
2. **push** - Push changes to remote repository  
3. **tag** - Create version tag with detailed changelog
4. **memory** - Store achievement in memory for future reference

This creates a complete milestone checkpoint of our progress.

**Recent Snapshots:**
- **v0.6.0** (2025-01-11) - 🎯 **EXPLICIT CONNECTION ARCHITECTURE** - 100% test reliability, eliminated broken pipes
- **v0.5.2** (2025-01-08) - Complete Joyride auto-discovery and fix nrepl-eval bug
- **v0.5.1** (2025-01-08) - Fix nrepl-status NPE by ensuring recent-commands is always a vector  
- **v0.5.0** (2025-01-08) - The Polyglot Stack milestone

## 🛠️ Enhanced MCP Client

**Never use curl again!** Use the enhanced Python MCP client for all server interactions.

**Quick Reference:**
- `python3 mcp_nrepl_client.py --help` - Full usage guide
- **Check memory** for complete usage patterns and examples
- **See docs/ENHANCED-MCP-CLIENT.md** for comprehensive documentation

## Testing MCP-nREPL Tools

### Full Clojure nREPL Test Server (NEW!)

**Managed Test Server with Complete Async Capabilities:**
```bash
# Start server (auto-assigns port, tracks PID)
./nrepl-test start

# Check status and connection info
./nrepl-test status  

# Stop server (clean shutdown and cleanup)
./nrepl-test stop

# Restart server
./nrepl-test restart
```

**Features:**
- ✅ **Full JVM Clojure** (not SCI) - promises, futures, Java interop
- ✅ **Auto-discovery** - creates `.nrepl-port` and `.nrepl-test-server.json`
- ✅ **Process management** - PID tracking, graceful shutdown
- ✅ **Ready for async testing** - perfect for Phase 1 implementation

### MCP Client Testing

Use the enhanced Python MCP client for testing (use `python3` to avoid confirmation prompts):

```bash
# Test nREPL evaluation
python3 ./mcp_nrepl_client.py --eval "(+ 1 2 3)" --quiet

# Get nREPL status
python3 ./mcp_nrepl_client.py --status --quiet

# Run health tests
python3 ./mcp_nrepl_client.py --test-nrepl --quiet

# List available tools
python3 ./mcp_nrepl_client.py --tools --format json --quiet

# Call specific tools
python3 ./mcp_nrepl_client.py --tool nrepl-load-file --args '{"file-path": "path/to/file.clj"}' --quiet

# Interactive mode
python3 ./mcp_nrepl_client.py --interactive
```

**Note**: Use `python3 ./mcp_nrepl_client.py` instead of `./mcp_nrepl_client.py` to avoid confirmation prompts in Claude Code.

**Most Common Commands:**
```bash
# Direct evaluation
python3 mcp_nrepl_client.py --eval "(+ 1 2 3)"

# Health check  
python3 mcp_nrepl_client.py --status --quiet

# Full testing
python3 mcp_nrepl_client.py --test-nrepl --summary

# Interactive mode
python3 mcp_nrepl_client.py --interactive
```

💡 **Pro Tip**: Always check memory for "mcp-client" or "tool-usage" tags for detailed usage patterns and automation examples.

## 🌳 Tree-sitter Integration for Enhanced Coding

**IMPORTANT**: Always leverage tree-sitter tools for semantic code analysis before making changes!

### Available Tree-sitter Tools
- `mcp__tree_sitter__get_symbols()` - Find functions, classes, imports
- `mcp__tree_sitter__find_usage()` - Track symbol usage across files
- `mcp__tree_sitter__get_ast()` - Parse syntax trees for deep analysis
- `mcp__tree_sitter__run_query()` - Custom AST queries
- `mcp__tree_sitter__analyze_complexity()` - Code complexity metrics
- `mcp__tree_sitter__find_similar_code()` - Find code patterns

### Supported Languages (Full Template Support)
✅ **Python, JavaScript, TypeScript, Go, Rust, C, C++, Swift, Java, Kotlin, Julia**
⚠️ **Clojure** - Parser available, templates pending (see integration plan)

### Enhanced Workflow: Think Before You Code

**Instead of just reading files:**
```python
# ❌ Old approach
# "Let me read the file to understand the structure..."

# ✅ New approach - Semantic analysis first
symbols = mcp__tree_sitter__get_symbols(
    project="mcp-nrepl-joyride",
    file_path="mcp_nrepl_client.py", 
    symbol_types=["functions", "classes"]
)

# Find usage patterns
usage = mcp__tree_sitter__find_usage(
    project="mcp-nrepl-joyride",
    symbol="eval_code",
    language="python"
)

# Analyze complexity before refactoring
complexity = mcp__tree_sitter__analyze_complexity(
    project="mcp-nrepl-joyride",
    file_path="mcp_nrepl_client.py"
)
```

### When to Use Tree-sitter Analysis

**Always use BEFORE:**
- Refactoring functions or classes
- Understanding unfamiliar codebases
- Making architectural changes
- Finding code patterns to follow
- Analyzing dependencies and imports

**Proactive Analysis Steps:**
1. **Map the codebase** - Get symbols and structure
2. **Find patterns** - Look for similar implementations  
3. **Understand dependencies** - Check imports and usage
4. **Assess complexity** - Understand what you're changing
5. **Then implement** - With full context and understanding

### Example: Enhanced Implementation Process

```python
# Step 1: Understand the existing codebase
symbols = mcp__tree_sitter__get_symbols(...)
similar = mcp__tree_sitter__find_similar_code(...)

# Step 2: Analyze patterns  
ast = mcp__tree_sitter__get_ast(...)

# Step 3: Implement with full semantic understanding
# Now write better, more consistent code
```

**Benefits:**
- **Faster comprehension** of complex codebases
- **Consistent patterns** following existing code style
- **Better refactoring** with semantic understanding
- **Fewer bugs** from better contextual awareness
- **More maintainable code** following project conventions

**Remember**: Tree-sitter gives you IDE-level understanding without an IDE!

## 🏥 Comprehensive Health Check System

**Latest Enhancement**: Full diagnostic system for troubleshooting and monitoring.

### Health Check Features (✅ IMPLEMENTED)
- **🔧 Environment Diagnostics**: System info, memory, versions, runtime details
- **🔌 Connection Health**: nREPL server status, available operations, response times  
- **⚙️ Core Functionality**: Basic evaluation, data structures, operations testing
- **🔗 Tool Integration**: Advanced nREPL operations (doc, source, completion)
- **⚡ Performance Benchmarks**: Multi-iteration evaluation speed testing
- **🛠️ Configuration Status**: Server settings, debug mode, operational parameters

### Health Check Usage
```bash
# Full diagnostic report
python3 ./mcp_nrepl_client.py --tool nrepl-health-check

# Quick check (skip performance tests)  
python3 ./mcp_nrepl_client.py --tool nrepl-health-check --args '{"include_performance": false}'

# Detailed verbose output
python3 ./mcp_nrepl_client.py --tool nrepl-health-check --args '{"verbose": true}'
```

### Health Check Results Interpretation

**Color-Coded Status:**
- 🟢 **Green**: All systems operational
- 🟡 **Yellow**: Partial functionality (degraded but usable)  
- 🔴 **Red**: Critical issues requiring attention

**Environment-Specific Results:**
- **Joyride**: Expects some limitations (Math/round, str/join, limited nREPL ops)
- **Python/Basilisp**: Should show full functionality across all categories
- **Standard Clojure**: Complete nREPL operation support expected

### Integration in Development Workflows

**Always start sessions with:**
```bash
python3 ./mcp_nrepl_client.py --tool nrepl-health-check --quiet
```

**For troubleshooting:**
1. **Connection issues** → Check Connection Health section
2. **Performance problems** → Review Performance Benchmarks  
3. **Missing operations** → Examine Tool Integration status
4. **Environment errors** → Analyze Environment Diagnostics

### Cookbook Integration

Health check documentation has been integrated into all three main cookbooks:

1. **AI Assistant MCP Cookbook** - Added as first step in environment discovery
2. **VS Code Joyride MCP Cookbook** - Troubleshooting section with Joyride-specific expectations
3. **Python Introspection Cookbook** - System monitoring with Python-specific diagnostics

**Key Achievement**: Provides unified diagnostic approach across all deployment scenarios (Joyride, Python, standard Clojure) with environment-aware result interpretation.

## 🚨 WORKFLOW RULE: TODO PROGRESSION

**CRITICAL REQUIREMENT**: Do not go to next todo without explicit user confirmation!

### Todo Management Protocol
1. **Complete current task** - Mark as completed when finished
2. **Report completion** - Summarize what was accomplished  
3. **Wait for explicit confirmation** - User must explicitly approve moving to next task
4. **No automatic progression** - Never assume user wants to continue to next todo

### Example Confirmation Patterns
- ✅ **"move to next task"**
- ✅ **"continue with next todo"**  
- ✅ **"proceed to testing"**
- ❌ **Implied continuation without explicit request**

**Remember**: This prevents unwanted task execution and ensures user maintains full control over development priorities.

## 📋 TODO MANAGEMENT RULE

**CRITICAL**: Always use TODO.md to manage/update/change/add todos

### Todo Management Requirements
1. **NEVER use TodoWrite tool** - This is only for internal progress tracking
2. **ALWAYS update TODO.md document** - This is the single source of truth
3. **Edit TODO.md directly** when adding, changing, or updating todos
4. **Keep TODO.md synchronized** with actual project status
5. **Mark completed items** in TODO.md when tasks are finished

### Why TODO.md Only?
- ✅ **Persistent** - Survives session changes
- ✅ **Visible** - User can see and review all todos
- ✅ **Version controlled** - Changes tracked in git
- ✅ **Single source of truth** - No confusion between tools
- ✅ **Shareable** - Other developers can see project status

**Remember**: TODO.md is the authoritative todo list. TodoWrite tool is for internal session management only.

## 🎨 CLOJURE CODE QUALITY & FORMATTING

**CRITICAL REQUIREMENTS**:
1. Run `./format.sh` (cljfmt) BEFORE clj-kondo - formatting first!
2. After EVERY code change: format → lint → fix issues → format → lint again
3. cljfmt auto-fixes formatting, clj-kondo only reports issues

### Clojure Quality Protocol
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

### Why Format Before Lint?
- Consistent formatting makes linting more accurate
- Some linting issues are caused by formatting problems
- Clean format = cleaner lint output = easier fixes

## 🐍 PYTHON CODE QUALITY & UV USAGE

**CRITICAL REQUIREMENTS**: 
1. For every Python code change, run black and flake8!
2. Use UV wherever possible for Python package management and tool execution!

### UV-First Python Protocol
1. **Always use UV** - `uv run black`, `uv run flake8`, `uv pip install`, etc.
2. **Black formatting** - `uv run black filename.py` for consistent formatting
3. **Flake8 linting** - `uv run flake8 filename.py` for code quality checks  
4. **Package installation** - `uv add package-name` or `uv pip install package-name`
5. **Script execution** - `uv run python script.py` for isolated execution
6. **Fix all issues** - Don't commit Python code with linting errors

### Example Workflow
```bash
# After editing Python files (UV-first approach)
uv run black mcp_server_manager.py
uv run flake8 mcp_server_manager.py

# Install packages with UV
uv add black flake8

# Run Python scripts with UV
uv run python mcp_server_manager.py --help

# Fix any reported issues before proceeding
```

**Remember**: UV provides faster, more reliable Python tooling. Clean, well-formatted code is essential for maintainability and team collaboration.

## 🏗️ Architecture Documentation

### Sync-Async Queuing Architecture (January 2025)

**Document**: `/docs/sync-async-queuing-architecture.md`

**Key Innovation**: Anonymous function heartbeat `((fn [] :pong))` - zero setup, comprehensive testing, minimal overhead (~80 byte responses).

**Architecture Highlights**:
- Atom-based message router with O(1) correlation using PersistentMap
- PersistentQueue send queue with backpressure protection
- Elegant sync facade over async core engine
- Connection state management with unique connection IDs
- TCP backpressure integration for flow control
- Anonymous function heartbeat strategy (tested and validated)
- 5-phase implementation strategy with tree-sitter validation and clj-kondo quality gates

**Status**: Design complete, validation testing performed, ready for implementation.

## 📅 IMPORTANT: Current Date Context

**Today is 2025** - Always search for current 2025 information, not 2024!

### Search Query Guidelines
- ✅ Use "2025" for current information
- ✅ Omit years entirely for general technical searches
- ❌ Don't use "2024" unless specifically referencing historical information
- 🎯 My knowledge cutoff is January 2025, so search for the latest available information

### Why This Matters
- **Web search results** - Get the most current best practices and solutions
- **Technology updates** - Don't miss recent developments and improvements
- **Accurate context** - Ensure recommendations reflect current state of technology
- **Proper documentation** - Date references should reflect the actual current year

**Example Corrections:**
- ❌ "Babashka queuing mechanisms 2024" 
- ✅ "Babashka queuing mechanisms 2025" or "Babashka queuing mechanisms"

This ensures all research and recommendations are based on the most current information available.