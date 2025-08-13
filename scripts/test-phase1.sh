#!/bin/bash
# Phase 1 Test Suite - Minimal MCP Server with Debug Tools
# Comprehensive test coverage for debug-eval and debug-load-file functionality

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}🧪 Phase 1 Test Suite - Minimal MCP Server${NC}"
echo "Testing debug-eval and debug-load-file functionality"
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

echo -e "${YELLOW}=== Basic MCP Protocol Tests ===${NC}"

# Test 1: Server initialization
run_test "Server initialization" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" --test-basic --quiet' \
    "Basic tests passed"

# Test 2: Tool listing
run_test "Tool listing" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" --list-tools --quiet' \
    "debug-eval"

echo
echo -e "${YELLOW}=== debug-eval Function Tests ===${NC}"

# Test 3: Simple arithmetic
run_test "Simple arithmetic (+ 1 2 3)" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" --tool debug-eval --args '"'"'{"code": "(+ 1 2 3)"}'"'"' --quiet' \
    'result.*:.*6'

# Test 4: Variable definition
run_test "Variable definition" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" --tool debug-eval --args '"'"'{"code": "(def test-var 42)"}'"'"' --quiet' \
    "test-var"

# Test 5: Function definition and call
run_test "Function definition and call" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" --tool debug-eval --args '"'"'{"code": "(do (defn square [x] (* x x)) (square 5))"}'"'"' --quiet' \
    'result.*:.*25'

# Test 6: Data structures
run_test "Data structures (vector)" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" --tool debug-eval --args '"'"'{"code": "[1 2 3 4 5]"}'"'"' --quiet' \
    'result.*\[1 2 3 4 5\]'

# Test 7: Map operations
run_test "Map operations" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" --tool debug-eval --args '"'"'{"code": "(:name {:name \"test\" :value 42})"}'"'"' --quiet' \
    'result.*test'

# Test 8: Error handling
run_test "Error handling (division by zero)" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" --tool debug-eval --args '"'"'{"code": "(/ 1 0)"}'"'"' --quiet' \
    'status.*error'

# Test 9: Empty code parameter
run_test "Empty code parameter validation" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" --tool debug-eval --args '"'"'{"code": ""}'"'"' --quiet' \
    "Code parameter is required"

echo
echo -e "${YELLOW}=== debug-load-file Function Tests ===${NC}"

# Test 10: Load test-toolkit.clj
run_test "Load test-toolkit.clj" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" --tool debug-load-file --args '"'"'{"file-path": "test-toolkit.clj"}'"'"' --quiet' \
    'successful.*4'

# Test 11: File not found error
run_test "File not found error" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" --tool debug-load-file --args '"'"'{"file-path": "non-existent-file.clj"}'"'"' --quiet' \
    'File not found'

# Test 12: Empty file-path parameter
run_test "Empty file-path parameter validation" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" --tool debug-load-file --args '"'"'{"file-path": ""}'"'"' --quiet' \
    "file-path parameter is required"

echo
echo -e "${YELLOW}=== MCP Introspection Tests ===${NC}"

# Test 13: Comprehensive MCP introspection via file loading
run_test "MCP introspection via debug-load-file" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" --tool debug-load-file --args "{\"file-path\": \"mcp-introspection-tests.clj\"}" --quiet' \
    'successful.*14'

# Test 14: Check namespace functionality
run_test "Current namespace check" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" --tool debug-eval --args "{\"code\": \"(str *ns*)\"}" --quiet' \
    'nrepl-mcp_server.core'

# Test 15: Create and use simple tool registry
run_test "Simple tool registry creation" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" --tool debug-eval --args "{\"code\": \"(keys {\\\"debug-eval\\\" 1 \\\"debug-load-file\\\" 2})\"}" --quiet' \
    'debug-eval.*debug-load-file'

# Test 16: Server state atom simulation
run_test "Server state atom simulation" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" --tool debug-eval --args "{\"code\": \"(let [state (atom {:connected true})] @state)\"}" --quiet' \
    'connected.*true'

# Test 17: Tool invocation simulation
run_test "Tool invocation simulation" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" --tool debug-eval --args "{\"code\": \"(defn mock-tool [name] {:tool name :status \\\"success\\\"}) (mock-tool \\\"debug-eval\\\")\"}" --quiet' \
    'tool.*debug-eval'

echo
echo -e "${YELLOW}=== Advanced Integration Tests ===${NC}"

# Create a temporary test file for advanced testing
cat > /tmp/phase1-test.clj << 'EOF'
(def pi 3.14159)
(defn circle-area [radius] (* pi radius radius))
(defn factorial [n]
  (if (<= n 1)
    1
    (* n (factorial (dec n)))))
(circle-area 5)
EOF

# Test 18: Advanced file loading
run_test "Advanced file loading with calculations" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" --tool debug-load-file --args '"'"'{"file-path": "/tmp/phase1-test.clj"}'"'"' --quiet' \
    'successful.*4'

# Test 19: State persistence across tool calls
run_test "State persistence test (define var)" \
    'uv run python scripts/stdio_mcp_client.py --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" --tool debug-eval --args '"'"'{"code": "(def persistent-var :phase1-test)"}'"'"' --quiet' \
    "persistent-var"

# Note: State persistence across separate server instances isn't expected to work
# since each stdio_mcp_client.py call starts a new server process

# Cleanup
rm -f /tmp/phase1-test.clj

echo
echo -e "${BLUE}=== Phase 1 Test Results ===${NC}"
echo "Tests run: $TEST_COUNT"
echo "Tests passed: $PASSED_COUNT"
echo "Tests failed: $((TEST_COUNT - PASSED_COUNT))"

if [ $PASSED_COUNT -eq $TEST_COUNT ]; then
    echo -e "${GREEN}🎉 All Phase 1 tests passed! Minimal MCP server is fully functional.${NC}"
    echo -e "${GREEN}Including MCP introspection capabilities via debug-eval!${NC}"
    exit 0
else
    echo -e "${RED}❌ Some tests failed. Phase 1 implementation needs attention.${NC}"
    exit 1
fi