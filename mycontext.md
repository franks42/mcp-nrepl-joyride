# Multi-Connection Architecture Context

## Session Date: 2025-08-19

## What We Were Working On
Implementing **multi-connection support** for the nREPL MCP server to allow connecting to multiple nREPL servers simultaneously. Following the plan in `multi-connection-refactoring-plan.md`.

## Current Status

### ✅ Completed Phases
- **Phase 1**: Connection parameter added to tools (nrepl-eval, nrepl-send-message-async, etc.)
- **Phase 2**: Nickname support implemented (e.g., "external-server", "local-bb")
- **Phase 3**: Enhanced nrepl-connection operations (list, disconnect with connection param)
- **Phase 4**: Per-connection queue infrastructure (separate message/result queues per connection)
- **Phase 5**: Per-connection watchers (send/receive watchers per connection)
- **Phase 6.3**: Updated nrepl-send-message-async and nrepl-send-message to pass connection-id

### 🎯 Key Achievement
**MULTI-CONNECTION NOW WORKS!** Successfully enabled connecting to multiple nREPL servers by changing `can-connect?` function to always return `true` (was the Phase 1 restriction).

## Test Setup
1. **External nREPL server**: Port 58252 (started via `./scripts/nrepl_test_server.py start`)
2. **Local Babashka server**: Port 59560 (started via `local-nrepl-server` MCP tool)
3. **HTTP Bridge**: Running on port 3000 (`./scripts/start-http-bridge.sh`)

## What's Working
- ✅ Can connect to multiple servers simultaneously
- ✅ Nicknames work ("external-server", "local-bb")
- ✅ Per-connection message queues created
- ✅ Messages routed to correct server via connection parameter
- ✅ Local Babashka server evaluation works perfectly
- ✅ Connection resolution works (nickname → connection-id)

## Known Issues to Fix
1. **External server partial response bug**: Messages sent to external server get stuck in `:partial-response` status (missing "done" status)
2. **Disconnect bug**: `{"op": "disconnect", "connection": "nickname"}` disconnects wrong connection
3. **List operation bug**: Shows incorrect connection count and empty array
4. **nrepl-eval and nrepl-load-file**: Still need connection-id parameter updates (Phase 6.1 and 6.2)

## Important Files Modified
- `src/nrepl_mcp_server/state/connection.clj` - Changed `can-connect?` to enable multi-connection
- `src/nrepl_mcp_server/mcp_server/tools/nrepl_send_message_async.clj` - Fixed to pass connection-id
- `src/nrepl_mcp_server/mcp_server/tools/nrepl_send_message.clj` - Fixed to pass connection-id

## Testing Commands
```bash
# Start HTTP bridge (required after code changes)
./scripts/stop-http-bridge.sh && ./scripts/start-http-bridge.sh

# Start external test server
./scripts/nrepl_test_server.py start  # Creates port 58252

# Connect to servers with nicknames
uv run python scripts/mcp_nrepl_client.py --base-url http://localhost:3000/mcp --tool nrepl-connection --args '{"op": "connect", "connection": "58252", "nickname": "external-server"}' --quiet

# Start local Babashka server
uv run python scripts/mcp_nrepl_client.py --base-url http://localhost:3000/mcp --tool local-nrepl-server --args '{"op": "start", "port": 0}' --quiet

# Send messages using nicknames
uv run python scripts/mcp_nrepl_client.py --base-url http://localhost:3000/mcp --tool nrepl-send-message-async --args '{"message": {"op": "eval", "code": "(+ 1 2)"}, "connection": "external-server"}' --quiet

# Check state
uv run python scripts/mcp_nrepl_client.py --base-url http://localhost:3000/mcp --tool local-eval --args '{"code": "@nrepl-mcp-server.state.connection/connection-state"}' --quiet
```

## Architecture Notes
- Each connection gets its own message queue and result queue
- Watchers are per-connection (not global anymore)
- Connection IDs format: `"IP:port-UUID"` (e.g., `"127.0.0.1:58252-0198c444-2949-7000-8000-00008d2d0222"`)
- Nicknames map to connection IDs in state

## Next Steps After Context Restore
1. Fix the disconnect operation to respect connection parameter
2. Fix the list operation to show connections correctly
3. Debug why external server responses are stuck in partial state
4. Complete Phase 6.1 and 6.2 (update nrepl-eval and nrepl-load-file)

## User Instructions
User said to focus ONLY on async tools (`nrepl-send-message-async`, `nrepl-get-result-async`) and `nrepl-connection` for now. The sync tools (nrepl-eval, nrepl-send-message) have issues that will be fixed later.

## Critical Reminder
**ALWAYS restart the HTTP bridge after code changes!** The bridge loads the code once at startup.