# Unified MCP Tester Manual

## Overview

The Unified MCP Tester is a comprehensive testing framework for Model Context Protocol (MCP) servers. It replaces all legacy hardcoded test scripts with a single, scalable solution that supports multiple transports and external test definitions.

## Introduction: The MCP-nREPL Ecosystem

### Why This Tool Exists

The MCP-nREPL Joyride project bridges multiple technologies to enable AI assistants (like Claude) to interact with Clojure development environments. This creates a complex ecosystem with multiple server components, transport layers, and communication protocols that all need to work together seamlessly.

### The Component Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   HTTP Client   │────▶│   HTTP Bridge    │────▶│  nREPL MCP      │
│  (Test Tools)   │     │   (mcp-proxy)    │     │    Server       │
└─────────────────┘     └──────────────────┘     └─────────────────┘
         │                      │                         │
         │                      ▼                         ▼
         │              StreamableHTTP → stdio      stdio protocol
         │                                                │
         └────────────────────────────────────────────────┘
                    Direct stdio connection
```

### Key Components

1. **nREPL MCP Server** (`src/nrepl_mcp_server/core.clj`)
   - The main Babashka-based MCP server
   - Provides tools for nREPL operations
   - Communicates via stdio protocol (JSON-RPC over stdin/stdout)
   - Manages state and connection lifecycle

2. **HTTP Bridge** (`mcp-proxy`)
   - Converts MCP-over-StreamableHTTP to MCP-over-stdio
   - Accepts HTTP requests and forwards them to stdio MCP server
   - Enables HTTP clients to communicate with stdio-only MCP servers
   - Runs with `--stateless` flag for true stateless HTTP mode

3. **nREPL Servers** (Various)
   - **Joyride Mock Server**: Simulates VS Code Joyride environment
   - **Test nREPL Server**: Full Clojure server for testing
   - **Babashka nREPL**: Lightweight scripting server
   - **Production nREPL**: Your actual development server

### The Testing Challenge

Testing this ecosystem is complex because:

1. **Multiple Transports**: stdio (direct) vs HTTP (bridged) communication
2. **Stateful vs Stateless**: Different behaviors in different modes
3. **Multiple Servers**: Each nREPL server has different capabilities
4. **Tool Evolution**: New tools are constantly being added
5. **Integration Points**: Many components must work together

### Legacy Testing Problem

Previously, we had:
- **15+ separate test scripts** (test-quick.sh, test-comprehensive.sh, etc.)
- **Hardcoded test cases** in each script
- **No reusability** between test suites
- **Manual updates** required for every new tool
- **Inconsistent testing** across transports

### The Unified Solution

The Unified MCP Tester solves these problems by providing:

1. **Single Testing Framework**: One tool for all testing needs
2. **Transport Agnostic**: Test stdio and HTTP with same test definitions
3. **External Test Definitions**: JSON files instead of hardcoded scripts
4. **Dynamic Discovery**: Automatically finds new tools
5. **Orchestrated Testing**: Run complex multi-phase test scenarios
6. **Interactive Exploration**: Manual testing and debugging

### When to Use This Tool

- **Development**: Test new MCP tools as you develop them
- **CI/CD**: Automated testing in continuous integration
- **Debugging**: Identify which component is failing
- **Validation**: Ensure all components work together
- **Exploration**: Discover capabilities of MCP servers
- **Regression Testing**: Ensure changes don't break existing functionality

### Testing Scenarios

1. **Basic Connectivity**: Is the server responding?
2. **Tool Functionality**: Do individual tools work correctly?
3. **State Persistence**: Does state persist across calls?
4. **Error Handling**: Are errors properly reported?
5. **Performance**: Are responses within acceptable time?
6. **Integration**: Do all components work together?

This unified testing framework ensures the entire MCP-nREPL ecosystem works reliably, from AI assistant to Clojure REPL.

## Table of Contents

1. [Introduction](#introduction-the-mcp-nrepl-ecosystem)
2. [Quick Start](#quick-start)
3. [Installation](#installation)
4. [Basic Usage](#basic-usage)
5. [Test Definition Files](#test-definition-files)
6. [Transport Modes](#transport-modes)
7. [Interactive Mode](#interactive-mode)
8. [Orchestrated Testing](#orchestrated-testing)
9. [Troubleshooting](#troubleshooting)
10. [Migration from Legacy Scripts](#migration-from-legacy-scripts)

## Quick Start

```bash
# Test via stdio transport (direct server communication)
uv run python scripts/unified_mcp_tester.py --transport stdio \
  --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" \
  --test-file tests/basic-tests.json

# Test via HTTP bridge (stateful testing)
uv run python scripts/unified_mcp_tester.py --transport http \
  --base-url http://localhost:3000 \
  --test-file tests/basic-tests.json

# Run orchestrated testing (multiple phases)
uv run python scripts/unified_mcp_tester.py --orchestrate \
  --config tests/orchestration.json
```

## Installation

### Using UV (Strongly Recommended)

UV provides fast, reliable Python package management with automatic dependency isolation:

```bash
# Install UV if not already installed
curl -LsSf https://astral.sh/uv/install.sh | sh

# Dependencies are automatically managed when using uv run
uv run python scripts/unified_mcp_tester.py --help
```

### Manual Installation (Alternative)

If not using UV, install dependencies manually:

```bash
# Required
pip install httpx  # For HTTP transport

# Optional (for better UI)
pip install rich   # For colorful terminal output
pip install pyyaml # For YAML test definitions
```

## Basic Usage

### Command Structure

```bash
uv run python scripts/unified_mcp_tester.py [OPTIONS]
```

### Common Options

| Option | Description | Request Flow | Example |
|--------|-------------|--------------|---------|
| `--transport stdio` | Direct stdio communication | Tester → stdio → MCP Server | `--transport stdio` |
| `--transport http` | Via HTTP bridge | Tester → HTTP → Bridge → stdio → MCP Server | `--transport http` |
| `--server-cmd` | Starts MCP server subprocess (stdio only) | Spawns server as child process | `--server-cmd "bb -cp src core.clj"` |
| `--base-url` | HTTP bridge endpoint (http only) | Where to send HTTP requests | `--base-url http://localhost:3000` |
| `--test-file` | JSON file with test cases | Defines what to test | `--test-file tests/basic-tests.json` |
| `--interactive` | Manual testing mode | Interactive command prompt | `--interactive` |
| `--orchestrate` | Multi-phase testing | Runs test phases in sequence | `--orchestrate --config tests/orchestration.json` |
| `--quiet` | Minimal output | Less verbose logging | `--quiet` |
| `--timeout` | Max wait for responses | Prevents hanging | `--timeout 60` |

#### Transport Mode Details

**stdio Transport (`--transport stdio --server-cmd "..."`):**
```
[Tester Process]                    [MCP Server Process]
      │                                     │
      ├─── subprocess.Popen() ──────────────┤
      │                                     │
      ├─── stdin pipe  ────────────────────>│
      │    (JSON-RPC requests)              │
      │                                     │
      │<─── stdout pipe ────────────────────┤
      │    (JSON-RPC responses)             │
      │                                     │
      └─────────────────────────────────────┘
```
- **NO BRIDGE INVOLVED** - Direct communication via pipes
- Tester spawns MCP server as subprocess using `subprocess.Popen()`
- Creates bidirectional pipes: stdin (requests) and stdout (responses)
- Tester writes JSON-RPC requests to server's stdin pipe
- Tester reads JSON-RPC responses from server's stdout pipe
- Example Python code from the tester:
  ```python
  process = subprocess.Popen(
      server_cmd.split(),
      stdin=subprocess.PIPE,   # Pipe for sending requests
      stdout=subprocess.PIPE,  # Pipe for receiving responses
      stderr=subprocess.PIPE,  # Capture error messages
      text=True
  )
  # Send request
  process.stdin.write(json.dumps(request) + "\n")
  process.stdin.flush()
  # Read response
  response = process.stdout.readline()
  ```

**HTTP Transport (`--transport http --base-url "..."`):**
```
[Tester] ──HTTP──> [mcp-proxy Bridge] ──stdio──> [MCP Server]
                   (already running)              (already running)
```
- **BRIDGE REQUIRED** - Converts HTTP to stdio
- Bridge must be started first with: `./scripts/start-http-bridge.sh`
- Tester sends HTTP POST requests to bridge
- Bridge converts HTTP to stdio and forwards to MCP server
- Bridge converts stdio responses back to HTTP

## Test Definition Files

### Location

Test definitions are stored in the `tests/` directory:

```
tests/
├── basic-tests.json       # Core functionality tests
├── orchestration.json     # Multi-phase test configuration
└── custom-tests.json      # Your custom test definitions
```

### Test File Format

Test files use JSON format with test suites as top-level keys:

```json
{
  "suite_name": [
    {
      "name": "test_name",
      "tool": "tool-name",
      "args": {"param": "value"},
      "expected": {"field": "value"},  // Optional validation
      "description": "What this tests",
      "timeout": 30  // Optional, defaults to 30 seconds
    }
  ]
}
```

### Example: basic-tests.json

```json
{
  "connectivity": [
    {
      "name": "debug_eval_basic",
      "tool": "local-eval",
      "args": {"code": "(+ 1 2 3)"},
      "expected": {"status": "success"},
      "description": "Basic arithmetic evaluation"
    }
  ],
  
  "persistence": [
    {
      "name": "define_variable",
      "tool": "local-eval",
      "args": {"code": "(def test-var 42)"},
      "description": "Define a test variable"
    },
    {
      "name": "access_variable",
      "tool": "local-eval",
      "args": {"code": "test-var"},
      "description": "Access previously defined variable"
    }
  ]
}
```

### Creating Custom Tests

1. Create a new JSON file in `tests/` directory
2. Define your test suites and cases
3. Run with `--test-file tests/your-tests.json`

#### Validation Rules

The `expected` field supports validation of nested JSON responses:

- Direct field matching: `{"status": "success"}`
- Nested response validation: Automatically parses `content[0].text` as JSON
- Partial matching: Only specified fields are validated

## Transport Modes

### stdio Transport

Direct communication with MCP server process:

```bash
uv run python scripts/unified_mcp_tester.py --transport stdio \
  --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj"
```

**Use Cases:**
- Testing server startup/shutdown
- Direct protocol testing
- Debugging server issues
- CI/CD integration

**Characteristics:**
- Fresh server instance per test run
- No state persistence between runs
- Direct error messages from server

### HTTP Transport

Communication via HTTP bridge (mcp-proxy):

```bash
# First, start the HTTP bridge
./scripts/start-http-bridge.sh

# Then run tests
uv run python scripts/unified_mcp_tester.py --transport http \
  --base-url http://localhost:3000
```

**Use Cases:**
- Stateful testing (persistence across calls)
- Testing through proxy/bridge
- Production-like testing
- Multi-client scenarios

**Characteristics:**
- Persistent connection
- State maintained across test suites
- Tests proxy functionality

## Interactive Mode

Explore and test MCP tools manually with a persistent pipe connection:

```bash
uv run python scripts/unified_mcp_tester.py --transport stdio \
  --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" \
  --interactive
```

### How Interactive Mode Works

**The pipe stays open throughout the session!**

```
[Tester Interactive Loop]          [MCP Server Process]
         │                                  │
         ├──── Spawns once ─────────────────┤
         │                                  │
         ├─ stdin pipe (stays open) ───────>│
         │                                  │
    ┌────┴────┐                             │
    │ mcp> list│──── Request 1 ────────────>│
    │         │<──── Response 1 ────────────┤
    │         │                             │
    │ mcp> call│──── Request 2 ────────────>│
    │         │<──── Response 2 ────────────┤
    │         │                             │
    │ mcp> ... │──── Request N ────────────>│
    │         │<──── Response N ────────────┤
    └─────────┘                             │
         │                                  │
         ├──── Terminates on quit ──────────┤
```

Key points:
- **Single subprocess** - Server spawned once, stays alive
- **Persistent pipes** - stdin/stdout remain connected
- **Stateful session** - Server maintains state between commands
- **Manual exploration** - Type commands interactively

### Interactive Commands

| Command | Description | Example |
|---------|-------------|---------|
| `list` | List available tools | `mcp> list` |
| `call` | Call a tool | `mcp> call local-eval {"code": "(+ 1 2)"}` |
| `quit` | Exit interactive mode | `mcp> quit` |

### Loading Files in Interactive Mode

**Current Limitation**: The interactive mode doesn't have a built-in file loading command.

**Workarounds**:

1. **Use the local-load-file tool** (if available):
   ```
   mcp> call local-load-file {"file-path": "debug-toolkit.clj"}
   ```

2. **Copy-paste from file**:
   ```bash
   # In another terminal, prepare the JSON:
   cat > /tmp/test-args.json << 'EOF'
   {"code": "(defn greet [name] (str \"Hello, \" name))"}
   EOF
   
   # Then copy-paste into interactive session:
   mcp> call local-eval {"code": "(defn greet [name] (str \"Hello, \" name))"}
   ```

3. **Use batch mode with test file**:
   ```bash
   # Better for complex test sequences
   uv run python scripts/unified_mcp_tester.py --transport stdio \
     --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" \
     --test-file tests/my-interactive-tests.json
   ```

4. **Hybrid approach** - Start interactive, then batch:
   ```bash
   # First explore interactively to understand tools
   uv run python scripts/unified_mcp_tester.py --interactive ...
   
   # Then create test file based on exploration
   # Finally run as batch for repeatability
   ```

**Future Enhancement**: A `load` command could be added to read JSON arguments from a file:
```
mcp> load /path/to/args.json
# Would execute: call tool-name {contents of args.json}
```

### Interactive Session Example

```
mcp> list
Available tools:
  - local-eval: Evaluate code in server runtime
  - nrepl-server: nREPL server operations
  - local-load-file: Load debug toolkit

mcp> call local-eval {"code": "(def x 42)"}
{
  "content": [
    {
      "text": "{\"status\": \"success\", \"result\": \"#'user/x\"}"
    }
  ]
}

mcp> call local-eval {"code": "x"}
{
  "content": [
    {
      "text": "{\"status\": \"success\", \"result\": 42}"
    }
  ]
}
# Note: The value persists! The server maintains state.

mcp> quit
Goodbye!
```

### Batch vs Interactive Modes

| Mode | Use Case | Server Lifecycle | State |
|------|----------|------------------|-------|
| **Batch** (default) | Automated testing | Spawned → Tests run → Terminated | Fresh for each run |
| **Interactive** (`--interactive`) | Manual exploration | Spawned → User commands → Quit | Persists during session |

Both modes use the same pipes, but:
- **Batch**: Runs predefined tests, then closes
- **Interactive**: Keeps pipes open for user commands

## Orchestrated Testing

Run multiple test phases with different configurations:

### Orchestration Configuration

File: `tests/orchestration.json`

```json
{
  "description": "Multi-phase testing configuration",
  "phases": [
    {
      "name": "Phase 1: stdio Basic Tests",
      "transport": "stdio",
      "client": {
        "server_cmd": "bb -cp src src/nrepl_mcp_server/core.clj",
        "timeout": 30
      },
      "test_file": "tests/basic-tests.json",
      "stop_on_failure": false
    },
    {
      "name": "Phase 2: HTTP Stateful Tests",
      "transport": "http",
      "client": {
        "base_url": "http://localhost:3000",
        "timeout": 30
      },
      "test_file": "tests/basic-tests.json",
      "stop_on_failure": true
    }
  ],
  "global_settings": {
    "parallel_execution": false,
    "cleanup_between_phases": true,
    "verbose_logging": true
  }
}
```

### Running Orchestrated Tests

```bash
uv run python scripts/unified_mcp_tester.py --orchestrate \
  --config tests/orchestration.json
```

## Troubleshooting

### Common Issues

#### 1. Tests Failing with "status not found"

**Problem:** Validation expects nested JSON but receives direct response.

**Solution:** Check if tool returns JSON in `content[0].text` field. The framework automatically handles this.

#### 2. HTTP Transport Connection Refused

**Problem:** HTTP bridge not running.

**Solution:** Start the bridge first:
```bash
./scripts/start-http-bridge.sh
```

#### 3. stdio Server Dies Immediately

**Problem:** Server command incorrect or dependencies missing.

**Solution:** Test server command manually:
```bash
bb -cp src src/nrepl_mcp_server/core.clj
```

#### 4. Timeout Errors

**Problem:** Server takes too long to respond.

**Solution:** Increase timeout:
```bash
--timeout 60  # 60 seconds
```

### Debug Mode

Enable verbose logging by using rich console (automatically enabled if installed with UV):

```bash
# UV automatically handles rich if available
uv run python scripts/unified_mcp_tester.py ...
```

### Checking Server Logs

For stdio transport, stderr is captured and shown on errors.

For HTTP transport, check bridge logs:
```bash
tail -f logs/http-bridge.log
```

## Migration from Legacy Scripts

### Legacy Script Mapping

| Old Script | New Command |
|------------|-------------|
| `test-quick.sh` | `--test-file tests/basic-tests.json` |
| `test-comprehensive.sh` | `--orchestrate --config tests/orchestration.json` |
| `test_http_bridge.py` | `--transport http --test-file tests/basic-tests.json` |
| `stdio_mcp_client.py` | `--transport stdio --interactive` |
| `test-master.sh` | `--orchestrate --config tests/orchestration.json` |

### Legacy Scripts Location

All legacy scripts have been moved to `/old/scripts/` for reference but are no longer maintained.

## Adding New Tests

### Step 1: Define Test Cases

Edit or create a JSON file in `tests/`:

```json
{
  "my_new_suite": [
    {
      "name": "my_test",
      "tool": "my-tool",
      "args": {"param": "value"},
      "expected": {"result": "expected"},
      "description": "Test description"
    }
  ]
}
```

### Step 2: Run Tests

```bash
uv run python scripts/unified_mcp_tester.py --transport stdio \
  --server-cmd "your-server-command" \
  --test-file tests/my-tests.json
```

### Step 3: Add to Orchestration (Optional)

Add a new phase to `tests/orchestration.json`:

```json
{
  "name": "My Custom Tests",
  "transport": "stdio",
  "client": {...},
  "test_file": "tests/my-tests.json"
}
```

## Advanced Features

### Dynamic Tool Discovery

Run without test file to discover and test all tools:

```bash
uv run python scripts/unified_mcp_tester.py --transport stdio \
  --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj"
```

### Custom Validation

The framework supports complex validation patterns:

```json
{
  "expected": {
    "status": "success",
    "result": {"nested": {"field": "value"}}
  }
}
```

### Parallel Testing

While not yet implemented, the architecture supports parallel test execution. See `global_settings.parallel_execution` in orchestration config.

## Best Practices

1. **Start Simple**: Begin with basic connectivity tests
2. **Test Incrementally**: Add tests gradually, validating each
3. **Use Descriptive Names**: Make test names self-documenting
4. **Group Related Tests**: Organize into logical suites
5. **Validate Critical Fields**: Only validate what matters
6. **Document Expected Failures**: Note known issues in descriptions
7. **Version Test Files**: Track test evolution with git

## Support and Contribution

### Reporting Issues

Found a bug? Create an issue with:
1. Test file that reproduces the issue
2. Command used to run tests
3. Expected vs actual results
4. Server/bridge logs if relevant

### Contributing Tests

1. Create test file in `tests/`
2. Ensure tests pass locally
3. Document test purpose
4. Submit pull request

### Getting Help

- Check this manual first
- Review example test files in `tests/`
- Look at framework source: `scripts/unified_mcp_tester.py`
- Check server documentation

## Appendix: Architecture

### Framework Components

1. **GenericMCPClient**: Transport abstraction layer
2. **DynamicTestSuite**: Test execution engine
3. **TestOrchestrator**: Multi-phase coordinator
4. **ConfigLoader**: External test definition parser

### Validation Pipeline

1. Tool returns MCP response
2. Framework checks for `content[0].text`
3. If present, parses as JSON
4. Validates against `expected` fields
5. Reports pass/fail with details

### Extension Points

- Custom transports: Extend `GenericMCPClient`
- Custom validators: Override `_validate_expected()`
- Custom reporters: Implement result formatters
- Custom test generators: Create programmatic test cases

---

*Last Updated: January 2025*
*Version: 1.0.0*
*Framework Version: unified_mcp_tester.py v1.0*