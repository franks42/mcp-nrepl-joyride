# MCP-nREPL Testing Guide

**Quick Start for New Claude Sessions: Read this first to understand how to test the MCP server!**

## 🎯 Overview

This project has a **two-layer architecture** for MCP communication:

```
┌─────────────────────────────────────────────────────────────┐
│  Test Client (HTTP or stdio)                                │
└────────────────────┬────────────────────────────────────────┘
                     │
     ┌───────────────┴────────────────┐
     │                                │
     ▼ HTTP Mode                      ▼ stdio Mode
┌─────────────────┐             ┌────────────────┐
│  HTTP Bridge    │             │  Direct Pipes  │
│  (Port 3000)    │             │  (stdin/out)   │
└────────┬────────┘             └────────┬───────┘
         │                               │
         └───────────┬───────────────────┘
                     ▼
         ┌───────────────────────┐
         │   MCP Server (stdio)  │
         │   bb -cp src ...      │
         └───────────┬───────────┘
                     │
                     ▼
         ┌───────────────────────┐
         │  nREPL Server         │
         │  (Port 58340)         │
         └───────────────────────┘
```

**Key Insight:** The MCP server maintains state (connections, sessions) that persists across tool calls. You MUST use a persistent connection method for realistic testing.

## 🔧 Test Environment Setup

### Step 1: Start Test nREPL Server

The MCP server connects to an nREPL server to execute Clojure code.

```bash
# Start full Clojure nREPL test server (required for nREPL tools)
python3 scripts/nrepl_test_server.py start

# Check status
python3 scripts/nrepl_test_server.py status

# Server info is saved in:
# - scripts/test-nrepl/.test-nrepl-server-port  (port number)
# - scripts/test-nrepl/.nrepl-test-server.json  (full details)
```

**Port:** Typically 58340 (auto-assigned, check the files above)

### Step 2: Choose Your Test Mode

You have **TWO options** for testing:

#### Option A: HTTP Mode (RECOMMENDED for tool testing)
**When to use:** Testing specific MCP tools with persistent state

```bash
# 1. Start HTTP bridge (spawns MCP server internally)
bash scripts/start-http-bridge.sh

# 2. HTTP bridge now running on http://localhost:3000
# 3. Use mcp_nrepl_client.py to test (see below)

# Stop when done
bash scripts/stop-http-bridge.sh
```

#### Option B: stdio Mode (For protocol-level testing)
**When to use:** Testing MCP protocol directly, debugging stdio communication

```bash
# No bridge needed - tools spawn MCP server directly via pipes
# See unified_mcp_tester.py examples below
```

## 🧪 Testing Tools Reference

### Tool 1: `mcp_nrepl_client.py` (HTTP Mode - BEST for manual testing)

**Purpose:** Interactive HTTP-based client for testing individual MCP tools

**Requirements:**
- HTTP bridge must be running
- Uses persistent HTTP connection (state persists!)

**Basic Usage:**
```bash
# List available tools
uv run python scripts/mcp_nrepl_client.py --list-tools

# Call a tool
uv run python scripts/mcp_nrepl_client.py \
  --tool TOOL_NAME \
  --args '{"param": "value"}'

# Quick eval shortcut
uv run python scripts/mcp_nrepl_client.py \
  --eval "(+ 1 2 3)"
```

**Common Examples:**
```bash
# Connect to nREPL
uv run python scripts/mcp_nrepl_client.py \
  --tool nrepl-connection \
  --args '{"op":"connect","connection":"58340"}'

# Evaluate code
uv run python scripts/mcp_nrepl_client.py \
  --tool nrepl-eval \
  --args '{"code":"(+ 1 2 3)"}'

# Evaluate local file
uv run python scripts/mcp_nrepl_client.py \
  --tool nrepl-eval-local-file \
  --args '{"file-path":"/absolute/path/to/file.clj"}'

# Check connection status
uv run python scripts/mcp_nrepl_client.py \
  --tool nrepl-connection \
  --args '{"op":"status"}'
```

**Output Formats:**
- `--output json` (default) - Clean tool response
- `--output raw` - Full MCP JSON-RPC response
- `--output clj` - Just the Clojure value (for eval tools)
- `--quiet` - Minimal output

### Tool 2: `unified_mcp_tester.py` (Batch Testing)

**Purpose:** Run multiple test cases from JSON definitions

**Requirements:**
- Test definition files in `tests/*.json`
- Can use HTTP or stdio mode

**Basic Usage:**

**HTTP Mode (with bridge):**
```bash
# 1. Start bridge first
bash scripts/start-http-bridge.sh

# 2. Run tests
uv run python scripts/unified_mcp_tester.py \
  --transport http \
  --base-url http://localhost:3000/mcp \
  --test-file tests/YOUR-TEST-FILE.json
```

**stdio Mode (direct):**
```bash
uv run python scripts/unified_mcp_tester.py \
  --transport stdio \
  --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" \
  --test-file tests/YOUR-TEST-FILE.json
```

**Interactive Mode:**
```bash
uv run python scripts/unified_mcp_tester.py \
  --transport http \
  --base-url http://localhost:3000/mcp \
  --interactive

# Commands in interactive mode:
# list - List available tools
# call TOOL_NAME {"args": "json"} - Call a tool
# quit - Exit
```

### Tool 3: Direct Babashka Scripts (Quick Tests)

**Purpose:** Simple one-off tests without Python dependencies

**Requirements:** Just Babashka

**Pattern:**
```clojure
#!/usr/bin/env bb
;; Each invocation spawns NEW MCP server - NO state persistence!

(require '[cheshire.core :as json]
         '[clojure.java.shell :as shell])

(defn call-mcp-tool [tool-name args]
  (let [request {:jsonrpc "2.0"
                 :id (rand-int 10000)
                 :method "tools/call"
                 :params {:name tool-name :arguments args}}
        result (shell/sh "bb" "-cp" "src" "src/nrepl_mcp_server/core.clj"
                        :in (json/generate-string request))]
    (json/parse-string (:out result) true)))
```

**⚠️ Warning:** Each `call-mcp-tool` spawns a **fresh MCP server**. No state persists between calls. Only use for isolated tool tests, not integration tests.

## 📝 Test Definition Files

Located in `tests/*.json`

**Format:**
```json
{
  "test-suite-name": [
    {
      "name": "test_case_name",
      "tool": "tool-name",
      "args": {"param": "value"},
      "expected": {"status": "success"},
      "description": "What this test does"
    }
  ]
}
```

**Example:** `tests/nrepl-eval-local-file-tests.json`

## 🎯 Common Testing Workflows

### Workflow 1: Test a New MCP Tool

```bash
# 1. Start test nREPL server
python3 scripts/nrepl_test_server.py start

# 2. Start HTTP bridge
bash scripts/start-http-bridge.sh

# 3. Connect to nREPL (if tool requires it)
uv run python scripts/mcp_nrepl_client.py \
  --tool nrepl-connection \
  --args '{"op":"connect","connection":"58340"}'

# 4. Test your tool
uv run python scripts/mcp_nrepl_client.py \
  --tool YOUR-NEW-TOOL \
  --args '{"your":"args"}'

# 5. Test error cases
uv run python scripts/mcp_nrepl_client.py \
  --tool YOUR-NEW-TOOL \
  --args '{"invalid":"params"}'

# 6. Clean up
bash scripts/stop-http-bridge.sh
python3 scripts/nrepl_test_server.py stop
```

### Workflow 2: Run Comprehensive Test Suite

```bash
# 1. Start environment
python3 scripts/nrepl_test_server.py start
bash scripts/start-http-bridge.sh

# 2. Create test file in tests/
cat > tests/my-feature-tests.json << 'EOF'
{
  "my-feature": [
    {"name": "test1", "tool": "...", "args": {...}},
    {"name": "test2", "tool": "...", "args": {...}}
  ]
}
EOF

# 3. Run tests
uv run python scripts/unified_mcp_tester.py \
  --transport http \
  --base-url http://localhost:3000/mcp \
  --test-file tests/my-feature-tests.json

# 4. Clean up
bash scripts/stop-http-bridge.sh
python3 scripts/nrepl_test_server.py stop
```

### Workflow 3: Debug MCP Server Issues

```bash
# 1. Start bridge with visible logs
bash scripts/start-http-bridge.sh

# 2. In another terminal, tail logs
tail -f logs/http-bridge.log

# 3. Run failing test
uv run python scripts/mcp_nrepl_client.py \
  --tool PROBLEMATIC-TOOL \
  --args '...' \
  --output raw  # See full MCP response

# 4. Check for server errors in logs
```

## ⚠️ Common Pitfalls

### Pitfall 1: "No nREPL connections available"
**Cause:** You called a nREPL tool without connecting first
**Fix:** Always connect first:
```bash
uv run python scripts/mcp_nrepl_client.py \
  --tool nrepl-connection \
  --args '{"op":"connect","connection":"58340"}'
```

### Pitfall 2: Tests pass individually but fail in sequence
**Cause:** Using Babashka scripts that spawn new server per call
**Fix:** Use HTTP mode with `mcp_nrepl_client.py` for persistent state

### Pitfall 3: "Connection refused" on port 3000
**Cause:** HTTP bridge not running
**Fix:**
```bash
bash scripts/start-http-bridge.sh
# Check: lsof -i :3000
```

### Pitfall 4: unified_mcp_tester.py hangs
**Cause:** Known issue with stdio mode in some environments
**Workaround:** Use HTTP mode instead:
```bash
bash scripts/start-http-bridge.sh
uv run python scripts/unified_mcp_tester.py \
  --transport http \
  --base-url http://localhost:3000/mcp \
  --test-file tests/your-tests.json
```

## 🔍 Debugging Tips

### Check Server Status
```bash
# nREPL server
python3 scripts/nrepl_test_server.py status

# HTTP bridge
lsof -i :3000
cat .http-bridge.pid

# MCP server (when using stdio directly)
ps aux | grep "bb -cp src src/nrepl_mcp_server"
```

### Read Logs
```bash
# HTTP bridge logs
tail -f logs/http-bridge.log

# nREPL test server logs
cat scripts/test-nrepl/.nrepl-test-server.json
```

### Test Basic Connectivity
```bash
# Ping HTTP bridge
curl -X POST http://localhost:3000/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'

# Test nREPL server directly (not via MCP)
nc localhost 58340
```

## 📚 Further Documentation

- **Unified Tester Manual:** `docs/unified_mcp_tester_manual.md`
- **Enhanced MCP Client:** `docs/ENHANCED-MCP-CLIENT.md` (if exists)
- **Main Project Docs:** `CLAUDE.md`

## 🎓 Key Takeaways for Future Claude Sessions

1. **Always use HTTP mode for tool testing** - State persists across calls
2. **Always start services in order:**
   1. nREPL test server
   2. HTTP bridge (which starts MCP server)
   3. Connect to nREPL
   4. Run tests
3. **Use `mcp_nrepl_client.py` for manual testing** - It's the fastest and most reliable
4. **Use `unified_mcp_tester.py` for batch testing** - Best with HTTP mode
5. **Avoid direct Babashka scripts for integration tests** - No state persistence
6. **Check logs when debugging** - `logs/http-bridge.log` is your friend

## ✅ Quick Verification Checklist

Before running tests, verify:
- [ ] nREPL test server is running (check `scripts/test-nrepl/.test-nrepl-server-port`)
- [ ] HTTP bridge is running (check `lsof -i :3000`)
- [ ] You know the nREPL port number
- [ ] You've connected to nREPL (if testing nREPL tools)
- [ ] You're using `uv run python` (not just `python3`)

Now you're ready to test! 🚀
