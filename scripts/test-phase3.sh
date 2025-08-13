#!/bin/bash
# Phase 3 Test Suite - Real nREPL Communication
# Tests actual nREPL server communication, connection management, and message processing

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}🧪 Phase 3 Test Suite - Real nREPL Communication${NC}"
echo "Testing complete nREPL integration and message flow"
echo "Project root: $PROJECT_ROOT"
echo

cd "$PROJECT_ROOT"

# Test counter
TEST_COUNT=0
PASSED_COUNT=0

run_test() {
    local test_name="$1"
    local test_cmd="$2"
    local expected_pattern="$3"
    
    TEST_COUNT=$((TEST_COUNT + 1))
    echo -e "${BLUE}Test $TEST_COUNT: $test_name${NC}"
    
    if output=$(eval "$test_cmd" 2>&1); then
        if [[ -n "$expected_pattern" && ! "$output" =~ $expected_pattern ]]; then
            echo -e "${RED}❌ FAILED - Output doesn't match expected pattern${NC}"
            echo "Expected pattern: $expected_pattern"
            echo "Actual output: $output"
            return 1
        else
            echo -e "${GREEN}✅ PASSED${NC}"
            PASSED_COUNT=$((PASSED_COUNT + 1))
            return 0
        fi
    else
        echo -e "${RED}❌ FAILED - Command failed${NC}"
        echo "Error output: $output"
        return 1
    fi
}

echo -e "${YELLOW}=== Test nREPL Server Setup ===${NC}"

# Start test nREPL server
echo "Starting test nREPL server..."
if uv run python scripts/nrepl_test_server.py start > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Test nREPL server started${NC}"
    
    # Wait for server to be ready
    sleep 2
    
    # Get the port
    if [ -f ".nrepl-test-server.json" ]; then
        NREPL_PORT=$(python3 -c "import json; print(json.load(open('.nrepl-test-server.json'))['port'])")
        echo "nREPL server running on port: $NREPL_PORT"
    else
        echo -e "${RED}❌ Could not determine nREPL server port${NC}"
        exit 1
    fi
else
    echo -e "${RED}❌ Failed to start test nREPL server${NC}"
    exit 1
fi

echo
echo -e "${YELLOW}=== Real nREPL Connection Tests ===${NC}"

# Test 1: Connect to real nREPL server
run_test "Connect to real nREPL server" \
    "uv run python scripts/stdio_mcp_client.py --server-cmd \"bb -cp src src/mcp_server/core.clj\" --tool nrepl-connect --args '{\"port\": $NREPL_PORT}' --quiet" \
    "connection-id"

# Test 2: Send eval message to real nREPL
run_test "Send eval message to real nREPL" \
    "uv run python scripts/stdio_mcp_client.py --server-cmd \"bb -cp src src/mcp_server/core.clj\" --tool send-message --args '{\"message\": {\"op\": \"eval\", \"code\": \"(+ 1 2 3)\"}}' --quiet" \
    "message-id"

# Test 3: Basic nREPL evaluation workflow
run_test "Complete eval workflow (connect + send + get)" \
    "timeout 10 uv run python scripts/stdio_mcp_client.py --server-cmd \"bb -cp src src/mcp_server/core.clj\" --test-nrepl-workflow --nrepl-port $NREPL_PORT --quiet" \
    "result.*6"

echo
echo -e "${YELLOW}=== nREPL Operation Tests ===${NC}"

# Test 4: Clone session
run_test "Clone nREPL session" \
    "uv run python scripts/stdio_mcp_client.py --server-cmd \"bb -cp src src/mcp_server/core.clj\" --tool send-message --args '{\"message\": {\"op\": \"clone\"}}' --quiet" \
    "message-id"

# Test 5: Describe nREPL server
run_test "Describe nREPL server" \
    "uv run python scripts/stdio_mcp_client.py --server-cmd \"bb -cp src src/mcp_server/core.clj\" --tool send-message --args '{\"message\": {\"op\": \"describe\"}}' --quiet" \
    "message-id"

# Test 6: List sessions
run_test "List nREPL sessions" \
    "uv run python scripts/stdio_mcp_client.py --server-cmd \"bb -cp src src/mcp_server/core.clj\" --tool send-message --args '{\"message\": {\"op\": \"ls-sessions\"}}' --quiet" \
    "message-id"

echo
echo -e "${YELLOW}=== Error Handling Tests ===${NC}"

# Test 7: Invalid nREPL operation
run_test "Invalid nREPL operation" \
    "uv run python scripts/stdio_mcp_client.py --server-cmd \"bb -cp src src/mcp_server/core.clj\" --tool send-message --args '{\"message\": {\"op\": \"invalid-operation\"}}' --quiet" \
    "message-id"

# Test 8: Malformed nREPL message
run_test "Malformed nREPL message" \
    "uv run python scripts/stdio_mcp_client.py --server-cmd \"bb -cp src src/mcp_server/core.clj\" --tool send-message --args '{\"message\": \"invalid-message\"}' --quiet" \
    "error"

echo
echo -e "${YELLOW}=== Connection Management Tests ===${NC}"

# Test 9: Disconnect and reconnect
run_test "Disconnect operation" \
    "uv run python scripts/stdio_mcp_client.py --server-cmd \"bb -cp src src/mcp_server/core.clj\" --tool nrepl-disconnect --args '{}' --quiet" \
    "disconnected"

# Test 10: Connection status check
run_test "Connection status check" \
    "uv run python scripts/stdio_mcp_client.py --server-cmd \"bb -cp src src/mcp_server/core.clj\" --tool nrepl-status --args '{}' --quiet" \
    "status"

echo
echo -e "${YELLOW}=== Performance Tests ===${NC}"

# Test 11: Multiple rapid evaluations
run_test "Multiple rapid evaluations" \
    "timeout 15 uv run python scripts/stdio_mcp_client.py --server-cmd \"bb -cp src src/mcp_server/core.clj\" --test-performance --nrepl-port $NREPL_PORT --quiet" \
    "performance"

# Test 12: Large data structure handling
run_test "Large data structure evaluation" \
    "uv run python scripts/stdio_mcp_client.py --server-cmd \"bb -cp src src/mcp_server/core.clj\" --tool send-message --args '{\"message\": {\"op\": \"eval\", \"code\": \"(vec (range 1000))\"}}' --quiet" \
    "message-id"

echo
echo -e "${YELLOW}=== Cleanup ===${NC}"

# Stop test nREPL server
echo "Stopping test nREPL server..."
if uv run python scripts/nrepl_test_server.py stop > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Test nREPL server stopped${NC}"
else
    echo -e "${YELLOW}⚠️ Warning: Could not stop test nREPL server cleanly${NC}"
fi

echo
echo -e "${BLUE}=== Phase 3 Test Results ===${NC}"
echo "Tests run: $TEST_COUNT"
echo "Tests passed: $PASSED_COUNT"
echo "Tests failed: $((TEST_COUNT - PASSED_COUNT))"

if [ $PASSED_COUNT -eq $TEST_COUNT ]; then
    echo -e "${GREEN}🎉 All Phase 3 tests passed! Real nREPL communication is fully functional.${NC}"
    exit 0
else
    echo -e "${RED}❌ Some tests failed. Phase 3 implementation needs attention.${NC}"
    echo -e "${YELLOW}Note: Phase 3 functions may not be implemented yet - this is expected during development.${NC}"
    exit 1
fi