#!/bin/bash

# Phase 2a Simple Test Suite - Tests each feature individually
# Accounts for stdio client creating new server instances

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Colors for output
BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}🧪 Phase 2a Simple Test Suite - Reactive Connection Management${NC}"
echo "Testing individual features (each stdio call = new server)"
echo ""

# Track test results
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

# Test function
run_test() {
    local test_name="$1"
    local tool="$2"
    local args="$3"
    local expected_pattern="$4"
    
    TESTS_RUN=$((TESTS_RUN + 1))
    echo -e "${BLUE}Test $TESTS_RUN: $test_name${NC}"
    
    result=$(uv run python "$SCRIPT_DIR/stdio_mcp_client.py" \
        --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" \
        --tool "$tool" \
        --args "$args" \
        --quiet 2>&1)
    
    if echo "$result" | grep -q "$expected_pattern"; then
        echo -e "${GREEN}✅ PASSED${NC}"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        return 0
    else
        echo -e "${RED}❌ FAILED${NC}"
        echo "Expected pattern: $expected_pattern"
        echo "Got: $result"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi
}

echo -e "${YELLOW}=== Basic Connection Interface Tests ===${NC}"

run_test "Server starts with disconnected status" \
    "nrepl-server" \
    '{"op": "status"}' \
    'disconnected'

run_test "Invalid operation returns error" \
    "nrepl-server" \
    '{"op": "invalid"}' \
    'Unknown operation'

run_test "Missing connection parameter error" \
    "nrepl-server" \
    '{"op": "connect"}' \
    'No connection info provided'

run_test "Invalid port format error" \
    "nrepl-server" \
    '{"op": "connect", "connection": "abc"}' \
    'Invalid connection format'

echo ""
echo -e "${YELLOW}=== Live Connection Tests ===${NC}"

# Ensure test server is running
if ! "$SCRIPT_DIR/nrepl-test" status | grep -q "Status: ✅ Running"; then
    echo "Starting test nREPL server..."
    "$SCRIPT_DIR/nrepl-test" start
    sleep 2
fi

NREPL_PORT=$(cat "$SCRIPT_DIR/test-nrepl/.test-nrepl-server-port")
echo "Using test nREPL server on port: $NREPL_PORT"

run_test "Connect to real nREPL server" \
    "nrepl-server" \
    "{\"op\": \"connect\", \"connection\": \"$NREPL_PORT\"}" \
    'Connected to nREPL server'

run_test "Connect via file path" \
    "nrepl-server" \
    '{"op": "connect", "connection": "scripts/test-nrepl/.test-nrepl-server-port"}' \
    'Connected to nREPL server'

run_test "Connect with custom timeout" \
    "nrepl-server" \
    "{\"op\": \"connect\", \"connection\": \"$NREPL_PORT\", \"timeout\": 1000}" \
    'Connected to nREPL server'

run_test "Connection failure to invalid port" \
    "nrepl-server" \
    '{"op": "connect", "connection": "99999", "timeout": 1000}' \
    'Connection failed'

echo ""
echo -e "${YELLOW}=== Architecture Verification Tests ===${NC}"

run_test "State namespace accessible via debug-eval" \
    "debug-eval" \
    '{"code": "(keys @nrepl-mcp-server.state/connection-state)"}' \
    'status.*hostname.*port'

run_test "Connection namespace functions exist" \
    "debug-eval" \
    '{"code": "(fn? nrepl-mcp-server.nrepl-client.connection/resolve-connection-params)"}' \
    'true'

run_test "State watchers are installed" \
    "debug-eval" \
    '{"code": "(contains? (set (keys (.getWatches nrepl-mcp-server.state/connection-state))) :connection-handler)"}' \
    'true'

run_test "Tools registry includes nrepl-connect" \
    "debug-eval" \
    '{"code": "(contains? nrepl-mcp-server.mcp.dispatch/tool-registry \"nrepl-connect\")"}' \
    'true'

echo ""
echo -e "${BLUE}=== Phase 2a Simple Test Results ===${NC}"
echo "Tests run: $TESTS_RUN"
echo "Tests passed: $TESTS_PASSED" 
echo "Tests failed: $TESTS_FAILED"

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}🎉 All Phase 2a tests passed!${NC}"
    echo -e "${GREEN}Reactive connection management architecture verified!${NC}"
    exit 0
else
    echo -e "${RED}❌ Some tests failed.${NC}"
    exit 1
fi