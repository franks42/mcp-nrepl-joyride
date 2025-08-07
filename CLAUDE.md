# Claude Code Integration Notes

This file contains important information for Claude Code when working with the MCP-nREPL Joyride project.

## 🎯 Project Status: ✅ COMPLETED

The MCP-nREPL Joyride bridge is fully implemented and tested. All core functionality is working correctly.

## 🏗️ Implementation Approach

### ✅ Completed Features
- **Pure Babashka implementation** - Fast startup, no TypeScript dependencies
- **Custom nREPL client** - Built from scratch for Babashka compatibility
- **Full MCP protocol support** - JSON-RPC 2.0 compliant server
- **Joyride/Calva integration** - Complete VS Code API support
- **Session management** - Isolated nREPL evaluation contexts
- **Comprehensive testing** - Integration tests with mock servers
- **Auto-discovery** - Finds nREPL via `.nrepl-port` files

### 🔧 Key Technical Decisions
1. **No TypeScript** - User explicitly rejected TypeScript, implemented pure Babashka solution
2. **Custom nREPL client** - Standard clients had JVM dependencies incompatible with Babashka
3. **Socket-based communication** - Raw TCP sockets for nREPL protocol
4. **Enhanced mock servers** - Realistic Joyride/Calva simulation for testing

## 🧪 Testing Strategy

### Test Commands
```bash
# Basic integration test (simple nREPL server)
bb -cp src run-integration-test.clj

# Enhanced Joyride integration test
bb -cp src test-joyride-integration.clj

# Start test servers manually
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

### Architecture Insights
- **Babashka is excellent for this use case** - Fast startup, good library support
- **Socket-based nREPL works well** - Simpler than full protocol implementations
- **Mock servers are essential** - Enable comprehensive testing without VS Code dependency
- **JSON-RPC 2.0 is straightforward** - Clean protocol for MCP implementation
- **Documentation preservation is critical** - All planning documents must be protected

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
- [x] nREPL auto-discovery works via `.nrepl-port`
- [x] VS Code API calls execute successfully
- [x] Session isolation functions correctly
- [x] Error handling is graceful
- [x] Documentation is up to date

The project is fully functional and ready for production use.

## 📸 Snapshot Command

When I say **"snapshot!"**, it means:
1. **commit** - Commit current changes with descriptive message
2. **push** - Push changes to remote repository  
3. **tag** - Create version tag with detailed changelog
4. **memory** - Store achievement in memory for future reference

This creates a complete milestone checkpoint of our progress.

**Recent Snapshots:**
- **v0.5.2** (2025-01-06) - Complete Joyride auto-discovery and fix nrepl-eval bug
- **v0.5.1** (2025-01-06) - Fix nrepl-status NPE by ensuring recent-commands is always a vector  
- **v0.5.0** (2025-01-06) - The Polyglot Stack milestone

## 🛠️ Enhanced MCP Client

**Never use curl again!** Use the enhanced Python MCP client for all server interactions.

**Quick Reference:**
- `python3 mcp_nrepl_client.py --help` - Full usage guide
- **Check memory** for complete usage patterns and examples
- **See docs/ENHANCED-MCP-CLIENT.md** for comprehensive documentation

## Testing MCP-nREPL Tools

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