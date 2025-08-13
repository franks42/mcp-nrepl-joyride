# MCP-nREPL Server Documentation

A Babashka-based Model Context Protocol (MCP) server that bridges AI assistants with Joyride nREPL for Clojure code evaluation and REPL interaction.

## Table of Contents

- [Overview](#overview)
- [Server Operations](#server-operations)
- [Interface Functions](#interface-functions)  
- [Connection Setup](#connection-setup)
- [Session Management](#session-management)
- [Protocol Details](#protocol-details)
- [Implementation Guide](#implementation-guide)
- [User Guide](#user-guide)

## Overview

The MCP-nREPL Server provides a bridge between AI assistants (via MCP) and Clojure nREPL servers (specifically Joyride). It enables:

- **Code Evaluation**: Execute Clojure code in nREPL sessions
- **Session Management**: Create/manage isolated evaluation contexts  
- **Health Monitoring**: Background heartbeat and comprehensive testing
- **Protocol Translation**: MCP JSON-RPC ↔ nREPL bencode conversion
- **Multiple Transports**: stdio and HTTP transport support

### Architecture

```
AI Assistant <--MCP/JSON-RPC--> MCP Server <--bencode--> nREPL Server
     ^                            ^                        ^
   Claude                 mcp-nrepl-proxy              Joyride
```

## Server Operations

### Starting the Server

#### HTTP Transport (Recommended)
```bash
# Start HTTP server on port 3000
./start-mcp-http-server.sh

# Or with custom port
bb src/mcp_nrepl_proxy/core.clj 8080

# Or with environment variables
MCP_HTTP_PORT=8080 bb src/mcp_nrepl_proxy/core.clj
```

#### Stdio Transport
```bash
# Start stdio server
./start-mcp-server.sh

# Or directly
bb src/mcp_nrepl_proxy/core.clj
```

### Configuration Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `MCP_DEBUG` | `false` | Enable debug logging |
| `MCP_HTTP_PORT` | - | Use HTTP transport on specified port |
| `JOYRIDE_WORKSPACE` | `$(pwd)` | Workspace directory for .nrepl-port discovery |
| `NREPL_PORT` | - | Override nREPL port (skips auto-discovery) |

### Stopping the Server

```bash
# Stop any running MCP server
./scripts.sh stop-mcp-server

# Or manually
pkill -f mcp_nrepl_proxy
```

### Health Monitoring

```bash
# HTTP health check
curl http://localhost:3000/health

# Via scripts
./scripts.sh health-check
```

## Interface Functions

### MCP Tools Available

#### `nrepl-connect`
Connect to nREPL server.

**Parameters:**
- `host` (string, optional): nREPL host (default: "localhost")
- `port` (number, optional): nREPL port (auto-discovered from .nrepl-port if not provided)

**Example:**
```json
{
  "name": "nrepl-connect",
  "arguments": {
    "host": "localhost",
    "port": 7888
  }
}
```

#### `nrepl-eval`
Evaluate Clojure code in nREPL session.

**Parameters:**
- `code` (string, required): Clojure code to evaluate
- `session` (string, optional): Session ID for evaluation context
- `ns` (string, optional): Namespace context

**Example:**
```json
{
  "name": "nrepl-eval", 
  "arguments": {
    "code": "(+ 1 2 3)",
    "session": "session-uuid-here",
    "ns": "user"
  }
}
```

#### `nrepl-status`
Get connection and session status with health metrics.

**Returns:**
```json
{
  "connected": true,
  "host": "localhost", 
  "port": 7888,
  "workspace": "/path/to/workspace",
  "sessions": 2,
  "recent-commands": 5,
  "health": {
    "heartbeat-connected": true,
    "last-heartbeat": 1704067200000,
    "heartbeat-failures": 0,
    "last-test-passed": true,
    "last-test-timestamp": 1704067190000
  }
}
```

#### `nrepl-new-session`
Create new nREPL session for isolated evaluation.

**Returns:**
```json
{
  "new-session": "uuid-session-id"
}
```

#### `nrepl-test`
Run comprehensive health and functionality tests.

**Returns:**
```
✅ Health Test Results: 5/5 tests passed (took 145ms)

✅ Server Description: Server alive with 6 operations
✅ Basic Arithmetic: (+ 2 3) → 5  
✅ String Operations: (str ...) → "hello world"
✅ Data Structures: (count [1 2 3 4 5]) → 5
✅ Output Handling: println captured correctly
```

### HTTP Endpoints

| Endpoint | Method | Description |
|----------|---------|-------------|
| `/mcp` | POST | MCP JSON-RPC requests |
| `/health` | GET | Health check |
| `/mcp` | OPTIONS | CORS preflight |

## Connection Setup

### Automatic Discovery

The server automatically discovers nREPL connection details:

1. **Port Discovery**: Reads `.nrepl-port` file in workspace directory
2. **Auto-Connect**: Attempts connection on server startup  
3. **Host**: Defaults to `localhost`

### Manual Connection

Use `nrepl-connect` tool with explicit parameters:

```bash
# Via HTTP API
curl -X POST http://localhost:3000/mcp \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0",
    "id": 1, 
    "method": "tools/call",
    "params": {
      "name": "nrepl-connect",
      "arguments": {
        "host": "remote-host",
        "port": 7888
      }
    }
  }'
```

### Connection States

- **Disconnected**: No active nREPL connection
- **Connected**: Active connection with successful heartbeat
- **Failed**: Connection lost or heartbeat failures
- **Auto-retry**: Background reconnection attempts

## Session Management

### Session Lifecycle

1. **Creation**
   ```clojure
   ;; Creates new isolated session
   {:op "clone"} → {:new-session "uuid"}
   ```

2. **Usage**
   ```clojure
   ;; Evaluation in session context
   {:op "eval" :code "(def x 42)" :session "uuid"}
   ```

3. **Persistence**
   - Variables persist between evaluations
   - Namespace changes maintained
   - Independent from other sessions

4. **Cleanup**
   ```clojure
   ;; Close session
   {:op "close" :session "uuid"}
   ```

### Session Storage

Sessions stored in server state:
```clojure
{:sessions 
  {"session-uuid-1" {:created #inst "2024-01-01T00:00:00"
                     :last-used #inst "2024-01-01T00:05:00"}
   "session-uuid-2" {:created #inst "2024-01-01T00:02:00"
                     :last-used #inst "2024-01-01T00:03:00"}}}
```

### Best Practices

- **Create sessions** for isolated evaluation contexts
- **Reuse sessions** for related operations  
- **Session cleanup** happens automatically on nREPL restart
- **Track session IDs** returned from evaluations

## Protocol Details

### MCP (Model Context Protocol)

**Transport**: JSON-RPC 2.0 over HTTP or stdio

**Request Format**:
```json
{
  "jsonrpc": "2.0",
  "id": "unique-id", 
  "method": "tools/call",
  "params": {
    "name": "tool-name",
    "arguments": {...}
  }
}
```

**Response Format**:
```json
{
  "jsonrpc": "2.0",
  "id": "unique-id",
  "result": {
    "content": [{"type": "text", "text": "response"}],
    "session": "session-uuid",
    "namespace": "current-ns"
  }
}
```

### nREPL Bencode Protocol

**Wire Format**: Bencode (BitTorrent encoding)

**Message Structure**:
```clojure
;; Request
{"op" "eval" 
 "code" "(+ 1 2)" 
 "id" "msg-uuid"
 "session" "session-uuid"}

;; Response (may be multiple messages)
{"id" "msg-uuid" "value" "3" "ns" "user"}
{"id" "msg-uuid" "status" ["done"]}
```

### Bencode Implementation Details

**Encoding Rules**:
- Strings: `<length>:<data>` (e.g., `4:spam`)
- Integers: `i<number>e` (e.g., `i42e`)  
- Lists: `l<elements>e`
- Dictionaries: `d<key-value-pairs>e`

**Key Operations**:
- `describe`: Get server capabilities
- `eval`: Evaluate code
- `clone`: Create new session
- `close`: Close session

**Response Handling**:
- Multiple messages per request
- Message collection until `["done"]` status
- Response merging (concatenate output, take last value)

### Protocol Translation

```
MCP Tool Call → nREPL Message → Bencode → Network
    ↓              ↓              ↓         ↓
JSON-RPC     Clojure Map    Byte Stream  TCP/Socket
    ↑              ↑              ↑         ↑  
MCP Response ← Merged Result ← Parsed ← Network Response
```

## Implementation Guide

### For MCP Server Implementers

#### Core Components

1. **Transport Layer** (`stdio-server-loop`, `http-handler`)
   - Handle MCP JSON-RPC protocol  
   - Route requests to tool handlers
   - Format responses per MCP spec

2. **nREPL Client** (`nrepl-client` namespace)
   - Bencode encode/decode
   - Socket connection management
   - Message collection and merging

3. **Tool Implementations** (`tool-nrepl-*` functions)
   - Parameter validation
   - nREPL message construction  
   - Response formatting

4. **State Management** (atom-based)
   - Connection pooling
   - Session tracking
   - Health monitoring

#### Extension Points

```clojure
;; Add new MCP tools
(defn- tool-my-custom-tool [args]
  ;; Implementation here
  {:content [{:type "text" :text "result"}]})

;; Update tool definitions
(def tool-definitions
  (conj existing-tools
    {:name "my-custom-tool"
     :description "Custom tool description"
     :inputSchema {...}}))

;; Add to call dispatcher  
(defn- call-tool [tool-name args]
  (case tool-name
    ;; existing cases...
    "my-custom-tool" (tool-my-custom-tool args)))
```

#### Testing Strategy

1. **Unit Tests**: Individual tool functions
2. **Integration Tests**: Full MCP protocol flow
3. **Health Tests**: nREPL connectivity and operations
4. **Load Tests**: Concurrent request handling

### For Client Implementers

#### MCP Client Requirements

1. **JSON-RPC 2.0**: Proper request/response handling
2. **HTTP Transport**: POST to `/mcp` endpoint
3. **Tool Discovery**: Call `tools/list` for available tools
4. **Error Handling**: Handle MCP error responses

#### Example Client (Python)

```python
import requests
import json

class MCPnREPLClient:
    def __init__(self, url="http://localhost:3000/mcp"):
        self.url = url
        self.id_counter = 1
    
    def call_tool(self, name, arguments=None):
        request = {
            "jsonrpc": "2.0", 
            "id": self.id_counter,
            "method": "tools/call",
            "params": {
                "name": name,
                "arguments": arguments or {}
            }
        }
        self.id_counter += 1
        
        response = requests.post(self.url, json=request)
        return response.json()
    
    def eval_clojure(self, code, session=None):
        args = {"code": code}
        if session:
            args["session"] = session
        return self.call_tool("nrepl-eval", args)
```

## User Guide

### Quick Start

1. **Start Joyride** with nREPL enabled
2. **Start MCP Server**: `./start-mcp-http-server.sh` 
3. **Verify Connection**: `./scripts.sh health-check`
4. **Test Evaluation**: `./scripts.sh test-eval`

### Common Workflows

#### Basic Evaluation
```bash
# Simple expression
curl -X POST http://localhost:3000/mcp \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0", "id": 1,
    "method": "tools/call", 
    "params": {
      "name": "nrepl-eval",
      "arguments": {"code": "(+ 1 2 3)"}
    }
  }'
```

#### Session-based Development
```bash
# 1. Create session
curl ... -d '{"method": "tools/call", "params": {"name": "nrepl-new-session"}}'

# 2. Define variables in session  
curl ... -d '{
  "params": {
    "name": "nrepl-eval",
    "arguments": {
      "code": "(def my-data [1 2 3 4 5])",
      "session": "session-uuid-here"
    }
  }
}'

# 3. Use variables in same session
curl ... -d '{
  "params": {
    "name": "nrepl-eval", 
    "arguments": {
      "code": "(map inc my-data)",
      "session": "session-uuid-here"
    }
  }
}'
```

#### Health Monitoring
```bash
# Check connection status
./scripts.sh health-check

# Run comprehensive tests
curl ... -d '{"method": "tools/call", "params": {"name": "nrepl-test"}}'

# Get detailed status
curl ... -d '{"method": "tools/call", "params": {"name": "nrepl-status"}}'
```

### Troubleshooting

#### Connection Issues
- Check `.nrepl-port` file exists in workspace
- Verify Joyride nREPL server is running
- Use `nrepl-connect` with explicit host/port

#### Evaluation Errors
- Check Clojure syntax in code parameter
- Verify namespace context if using `:ns` parameter
- Review server logs for detailed error messages

#### Session Problems  
- Sessions are server-scoped, not persistent across restarts
- Use `nrepl-status` to see active session count
- Create new sessions if evaluation context is lost

#### Performance Issues
- Background heartbeat runs every 45 seconds
- Use sessions to avoid repeated connection overhead
- Monitor health test results for nREPL responsiveness

### Development Scripts

Available via `./scripts.sh <command>`:

| Command | Description |
|---------|-------------|
| `start-mcp-server` | Start HTTP MCP server |
| `stop-mcp-server` | Stop MCP server |  
| `health-check` | Check server health |
| `test-eval` | Test nREPL evaluation |
| `list-tools` | List available MCP tools |
| `lint` | Lint Clojure code |
| `bb-repl` | Start Babashka REPL |

### Integration Examples

#### VS Code Extension
Use MCP client library to integrate with VS Code for Clojure development.

#### Claude Code Integration  
Configure as MCP server in Claude Code settings for AI-assisted Clojure development.

#### Jupyter Kernel
Implement Jupyter kernel using MCP-nREPL bridge for notebook-style Clojure development.