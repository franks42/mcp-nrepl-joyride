#!/bin/bash
# Comprehensive test suite for refactored nrepl-eval tool
# Tests clean delegation, EDN-to-JSON conversion, and all parameter support

set -e  # Exit on any error

# Configuration
BASE_URL="http://localhost:3000/mcp"
CLIENT="uv run python scripts/mcp_nrepl_client.py --base-url $BASE_URL"
NREPL_PORT=63406

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

echo -e "${BLUE}🧪 Comprehensive nrepl-eval Test Suite${NC}"
echo -e "${BLUE}======================================${NC}"
echo

# Function to run a test
run_test() {
    local test_name="$1"
    local test_cmd="$2"
    local expected_pattern="$3"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    echo -e "${YELLOW}Test $TOTAL_TESTS: $test_name${NC}"
    echo "Command: $test_cmd"
    
    if result=$(eval "$test_cmd" 2>&1); then
        if [[ "$result" =~ $expected_pattern ]]; then
            echo -e "${GREEN}✅ PASSED${NC}"
            PASSED_TESTS=$((PASSED_TESTS + 1))
        else
            echo -e "${RED}❌ FAILED - Pattern not found${NC}"
            echo "Expected pattern: $expected_pattern"
            echo "Actual result: $result"
            FAILED_TESTS=$((FAILED_TESTS + 1))
        fi
    else
        echo -e "${RED}❌ FAILED - Command error${NC}"
        echo "Error: $result"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
    echo "---"
}

# Function to check if service is running
check_service() {
    local service_name="$1"
    local check_cmd="$2"
    
    if eval "$check_cmd" >/dev/null 2>&1; then
        echo -e "${GREEN}✅ $service_name is running${NC}"
        return 0
    else
        echo -e "${RED}❌ $service_name is not running${NC}"
        return 1
    fi
}

# Pre-flight checks
echo -e "${BLUE}🔍 Pre-flight Checks${NC}"
echo "==================="

# Check if HTTP bridge is running
if ! check_service "HTTP Bridge" "curl -s http://localhost:3000/mcp/ -H 'Accept: application/json'"; then
    echo "Please start the HTTP bridge: ./scripts/start-http-bridge.sh"
    exit 1
fi

# Check if nREPL server is running
if ! check_service "nREPL Server" "uv run python scripts/nrepl_test_server.py status"; then
    echo "Please start nREPL server: uv run python scripts/nrepl_test_server.py start"
    exit 1
fi

echo

# Connect to nREPL server
echo -e "${BLUE}🔌 Connecting to nREPL Server${NC}"
echo "=============================="
connect_result=$($CLIENT --tool nrepl-connection --args "{\"op\": \"connect\", \"connection\": \"$NREPL_PORT\"}" --quiet 2>&1)

if [[ "$connect_result" =~ "success" ]]; then
    echo -e "${GREEN}✅ Connected to nREPL server${NC}"
    # Extract connection ID for later tests
    CONNECTION_ID=$(echo "$connect_result" | python3 -c "import json, sys; data=json.load(sys.stdin); print(data['connection-id'])")
    echo "Connection ID: $CONNECTION_ID"
else
    echo -e "${RED}❌ Failed to connect to nREPL server${NC}"
    echo "Result: $connect_result"
    exit 1
fi

echo

# Start tests
echo -e "${BLUE}🧪 Running Comprehensive Tests${NC}"
echo "==============================="

# Test 1: Simple arithmetic with EDN conversion
run_test "Simple arithmetic with EDN conversion" \
    "$CLIENT --tool nrepl-eval --args '{\"code\": \"(+ 1 2 3)\"}' --quiet" \
    '"value": "6".*"value-parsed": 6'

# Test 2: Vector with EDN conversion
run_test "Vector with EDN conversion" \
    "$CLIENT --tool nrepl-eval --args '{\"code\": \"[1 2 3 4]\"}' --quiet" \
    '"value-parsed": \[1, 2, 3, 4\]'

# Test 3: Map with keywords to JSON conversion
run_test "Map with keyword-to-string conversion" \
    "$CLIENT --tool nrepl-eval --args '{\"code\": \"{:name \\\"test\\\" :count 42}\"}' --quiet" \
    '"value-parsed": \{.*"name": "test".*"count": 42.*\}'

# Test 4: Special objects (no parsing)
run_test "Special objects (vars) - no value-parsed field" \
    "$CLIENT --tool nrepl-eval --args '{\"code\": \"(def my-test-var 100)\"}' --quiet" \
    '"value": "#'\''user/my-test-var"'

# Test 5: Stdout capture
run_test "Stdout capture with println" \
    "$CLIENT --tool nrepl-eval --args '{\"code\": \"(do (println \\\"Hello\\\") (+ 10 20 30))\"}' --quiet" \
    '"out": "Hello\\n".*"value-parsed": 60'

# Test 6: Error handling (stderr capture)
run_test "Error handling with stderr capture" \
    "$CLIENT --tool nrepl-eval --args '{\"code\": \"(/ 1 0)\"}' --quiet" \
    '"err": ".*ArithmeticException.*"'

# Test 7: Connection parameter support
run_test "Connection parameter support" \
    "$CLIENT --tool nrepl-eval --args '{\"code\": \"(+ 5 5)\", \"connection\": \"$CONNECTION_ID\"}' --quiet" \
    '"value": "10".*"value-parsed": 10'

# Test 8: Custom timeout parameter
run_test "Custom timeout parameter" \
    "$CLIENT --tool nrepl-eval --args '{\"code\": \"(* 7 8)\", \"timeout\": 5000}' --quiet" \
    '"value": "56".*"value-parsed": 56'

# Test 9: Nested collections
run_test "Nested collections (vector of maps)" \
    "$CLIENT --tool nrepl-eval --args '{\"code\": \"[{:id 1 :name \\\"Alice\\\"} {:id 2 :name \\\"Bob\\\"}]\"}' --quiet" \
    '"value-parsed": \[.*"id": 1.*"name": "Alice".*"id": 2.*"name": "Bob".*\]'

# Test 10: Boolean values
run_test "Boolean values conversion" \
    "$CLIENT --tool nrepl-eval --args '{\"code\": \"true\"}' --quiet" \
    '"value": "true".*"value-parsed": true'

# Test 11: Nil value handling
run_test "Nil value handling" \
    "$CLIENT --tool nrepl-eval --args '{\"code\": \"nil\"}' --quiet" \
    '"value": "nil"'

# Test 12: String evaluation
run_test "String evaluation" \
    "$CLIENT --tool nrepl-eval --args '{\"code\": \"\\\"Hello World\\\"\"}' --quiet" \
    '"value": "\\"Hello World\\"".*"value-parsed": "Hello World"'

# Test 13: Validation - no code provided
run_test "Validation - no code provided" \
    "$CLIENT --tool nrepl-eval --args '{}' --quiet" \
    '"status": "error".*"error": "No code provided"'

# Test 14: Complex data structure
run_test "Complex nested data structure" \
    "$CLIENT --tool nrepl-eval --args '{\"code\": \"{:users [{:name \\\"Alice\\\" :age 30} {:name \\\"Bob\\\" :age 25}] :total 2}\"}' --quiet" \
    '"value-parsed": \{.*"users": \[.*"name": "Alice".*"age": 30.*\].*"total": 2.*\}'

# Test 15: Keywords as values
run_test "Keywords as values" \
    "$CLIENT --tool nrepl-eval --args '{\"code\": \":status\"}' --quiet" \
    '"value": ":status".*"value-parsed": "status"'

# Summary
echo
echo -e "${BLUE}📊 Test Summary${NC}"
echo "==============="
echo -e "Total tests: ${BLUE}$TOTAL_TESTS${NC}"
echo -e "Passed: ${GREEN}$PASSED_TESTS${NC}"
echo -e "Failed: ${RED}$FAILED_TESTS${NC}"

if [ $FAILED_TESTS -eq 0 ]; then
    echo
    echo -e "${GREEN}🎉 ALL TESTS PASSED!${NC}"
    echo -e "${GREEN}nrepl-eval refactoring is working perfectly!${NC}"
    exit 0
else
    echo
    echo -e "${RED}❌ Some tests failed. Please review the issues above.${NC}"
    exit 1
fi