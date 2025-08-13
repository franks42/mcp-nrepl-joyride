# Namespace Refactoring Plan for MCP-nREPL Bridge

## Current State
- **core.clj**: 1,627 lines (45 functions)
- **nrepl_client.clj**: 706 lines (33 functions)
- **Total**: 2,333 lines across 2 files

## Proposed Namespace Structure

### 1. `mcp-nrepl-proxy.protocol` (~200 lines)
**Purpose**: MCP protocol handling and JSON-RPC communication
- `handle-initialize`
- `handle-request`
- `handle-list-tools`
- `handle-call-tool`
- `handle-list-resources`
- `handle-read-resource`
- Protocol constants and specifications

### 2. `mcp-nrepl-proxy.server` (~150 lines)
**Purpose**: Server infrastructure (STDIO and HTTP)
- `stdio-server-loop`
- `http-handler`
- `start-http-server`
- `-main`
- Server configuration and startup

### 3. `mcp-nrepl-proxy.nrepl.connection` (~400 lines)
**Purpose**: nREPL connection management
**Move from nrepl_client.clj**:
- `connect`
- `close-connection`
- `get-connection-state`
- `list-connections`
- `active-connections`
- `cleanup-closed-connections`
- Connection state atom and management
**From core.clj**:
- `connect-to-nrepl`
- `ensure-nrepl-connection`
- `get-joyride-connection`
- `discover-nrepl-port`

### 4. `mcp-nrepl-proxy.nrepl.messaging` (~500 lines)
**Purpose**: nREPL message handling and async operations
**Move from nrepl_client.clj**:
- `send-message`
- `send-message-async`
- `collect-responses`
- `collect-responses-async`
- `merge-responses`
- `queue-message-async`
- `fetch-result`
- Message queue lifecycle management

### 5. `mcp-nrepl-proxy.nrepl.operations` (~300 lines)
**Purpose**: High-level nREPL operations
**Move from nrepl_client.clj**:
- `eval-code`
- `create-session`
- `close-session`
- `describe-server`
- `load-file`
- `doc`
- `source`
- `complete`
- `apropos`
- `require-ns`
- `interrupt`
- `stacktrace`

### 6. `mcp-nrepl-proxy.tools.eval` (~250 lines)
**Purpose**: Evaluation-related MCP tools
- `tool-nrepl-eval`
- `tool-nrepl-test`
- `tool-nrepl-load-file`
- `eval-in-joyride`

### 7. `mcp-nrepl-proxy.tools.introspection` (~300 lines)
**Purpose**: Code introspection MCP tools
- `tool-nrepl-doc`
- `tool-nrepl-source`
- `tool-nrepl-complete`
- `tool-nrepl-apropos`
- `tool-nrepl-status`

### 8. `mcp-nrepl-proxy.tools.session` (~200 lines)
**Purpose**: Session and connection management tools
- `tool-nrepl-connect`
- `tool-nrepl-new-session`
- `tool-nrepl-interrupt`
- `tool-nrepl-stacktrace`

### 9. `mcp-nrepl-proxy.tools.async` (~150 lines)
**Purpose**: Async messaging tools
- `tool-nrepl-send-message-async`
- `tool-nrepl-get-result-async`

### 10. `mcp-nrepl-proxy.tools.health` (~400 lines)
**Purpose**: Health check and monitoring
- `tool-nrepl-health-check`
- `run-health-test`
- `run-comprehensive-health-check`
- `format-health-check-report`
- `heartbeat-test`
- `start-heartbeat-monitor`

### 11. `mcp-nrepl-proxy.tools.special` (~150 lines)
**Purpose**: Special integration tools
- `tool-babashka-nrepl`
- `tool-nrepl-require`
- `tool-get-mcp-nrepl-context`

### 12. `mcp-nrepl-proxy.utils` (~100 lines)
**Purpose**: Utility functions
- `log`
- `base64-decode`
- `decode-nrepl-response`
- `cache-command`
- `get-server-state`
- `get-mcp-stats`

### 13. `mcp-nrepl-proxy.config` (~50 lines)
**Purpose**: Configuration and constants
- Tool definitions
- Resource definitions
- Protocol versions
- Default settings

## Benefits of This Structure

1. **Reduced Complexity**: Each file ~100-500 lines (manageable)
2. **Clear Separation**: Protocol, transport, and business logic separated
3. **Easier Testing**: Can test each namespace independently
4. **Better Debugging**: Narrower scope makes issues easier to locate
5. **Parallel Development**: Multiple people (or AI sessions) can work on different parts
6. **Reusability**: Clean interfaces between components
7. **Documentation**: Each namespace has clear, focused purpose

## Migration Strategy

### Phase 1: Create Structure (No Breaking Changes)
1. Create new namespace files with declarations
2. Move functions one namespace at a time
3. Update requires in core.clj to use new namespaces
4. Test after each namespace migration

### Phase 2: Clean Dependencies
1. Remove circular dependencies
2. Create clean interfaces between namespaces
3. Add namespace documentation

### Phase 3: Optimize
1. Remove duplicate code
2. Consolidate similar functions
3. Add specs/schemas for inter-namespace contracts

## File Size Estimates After Refactoring
- Largest file: ~500 lines (messaging)
- Smallest file: ~50 lines (config)
- Average file: ~250 lines
- Total files: 13 (from 2)

This represents a 6.5x increase in modularity while maintaining the same functionality!