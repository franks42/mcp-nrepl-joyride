#!/bin/bash
# Phase 2 Test Suite - Core MCP Functions (nrepl-connect, send-message, get-result)
# Tests the three core MCP functions in isolation without real nREPL communication

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}🧪 Phase 2 Test Suite - Core MCP Functions${NC}"
echo "Testing nrepl-connect, send-message, get-result in isolation"
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

echo -e "${YELLOW}=== Phase 2 Functions Available ===${NC}"

# Test 1: Verify Phase 2 tools are listed
run_test "Phase 2 tools available" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/mcp_server/core.clj" --list-tools --quiet' \
    "nrepl-connect"

echo
echo -e "${YELLOW}=== nrepl-connect Function Tests ===${NC}"

# Test 2: nrepl-connect with valid port
run_test "nrepl-connect with valid port" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/mcp_server/core.clj" --tool nrepl-connect --args '"'"'{"port": 12345}'"'"' --quiet' \
    "connection-id"

# Test 3: nrepl-connect without port parameter
run_test "nrepl-connect missing port parameter" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/mcp_server/core.clj" --tool nrepl-connect --args '"'"'{}'"'"' --quiet' \
    "port parameter is required"

# Test 4: nrepl-connect with invalid port
run_test "nrepl-connect with invalid port" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/mcp_server/core.clj" --tool nrepl-connect --args '"'"'{"port": "invalid"}'"'"' --quiet' \
    "Invalid port"

echo
echo -e "${YELLOW}=== send-message Function Tests ===${NC}"

# Test 5: send-message with valid message
run_test "send-message with valid message" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/mcp_server/core.clj" --tool send-message --args '"'"'{"message": {"op": "eval", "code": "(+ 1 2 3)"}}'"'"' --quiet' \
    "message-id"

# Test 6: send-message without connection
run_test "send-message without connection" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/mcp_server/core.clj" --tool send-message --args '"'"'{"message": {"op": "eval", "code": "(+ 1 2)"}}'"'"' --quiet' \
    "No nREPL connection"

# Test 7: send-message with empty message
run_test "send-message with empty message" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/mcp_server/core.clj" --tool send-message --args '"'"'{}'"'"' --quiet' \
    "message parameter is required"

echo
echo -e "${YELLOW}=== get-result Function Tests ===${NC}"

# Test 8: get-result with valid message-id (timeout expected)
run_test "get-result with timeout" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/mcp_server/core.clj" --tool get-result --args '"'"'{"message-id": "test-msg-id-123", "timeout-ms": 1000}'"'"' --quiet' \
    "timeout"

# Test 9: get-result without message-id
run_test "get-result missing message-id" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/mcp_server/core.clj" --tool get-result --args '"'"'{}'"'"' --quiet' \
    "message-id parameter is required"

# Test 10: get-result with invalid timeout
run_test "get-result with invalid timeout" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/mcp_server/core.clj" --tool get-result --args '"'"'{"message-id": "test-123", "timeout-ms": "invalid"}'"'"' --quiet' \
    "Invalid timeout"

echo
echo -e "${YELLOW}=== Integration Workflow Tests ===${NC}"

# Test 11: Use debug-eval to simulate queue operations
run_test "Simulate queue operations with debug-eval" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/mcp_server/core.clj" --tool debug-eval --args '"'"'{"code": "(do (def test-queue (atom {})) (swap! test-queue assoc :test-key :test-value) (:test-key @test-queue))"}'"'"' --quiet' \
    '"result" : "test-value"'

# Test 12: Test UUID generation
run_test "UUID generation test" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/mcp_server/core.clj" --tool debug-eval --args '"'"'{"code": "(str (java.util.UUID/randomUUID))"}'"'"' --quiet' \
    '"result-type" : "java.lang.String"'

echo
echo -e "${BLUE}=== Phase 2 Test Results ===${NC}"
echo "Tests run: $TEST_COUNT"
echo "Tests passed: $PASSED_COUNT"
echo "Tests failed: $((TEST_COUNT - PASSED_COUNT))"

if [ $PASSED_COUNT -eq $TEST_COUNT ]; then
    echo -e "${GREEN}🎉 All Phase 2 tests passed! Core MCP functions are working correctly.${NC}"
    exit 0
else
    echo -e "${RED}❌ Some tests failed. Phase 2 implementation needs attention.${NC}"
    echo -e "${YELLOW}Note: Phase 2 functions may not be implemented yet - this is expected during development.${NC}"
    exit 1
fi