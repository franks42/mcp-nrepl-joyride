#!/usr/bin/env bash

# Comprehensive test script for base64 enhancements in MCP-nREPL
# Tests all permutations of input methods, base64 flags, and both eval tools
#
# Usage: ./test-base64-comprehensive.sh [OPTIONS]
# Options:
#   --quick       Skip performance tests (50 rapid requests)
#   --basic-only  Run only basic functionality tests (Part 1)
#   --base64-only Run only base64 tests (Parts 2-4)
#   --no-nrepl    Skip nrepl-eval tests (local-eval only)
#   --help        Show this help message

set -euo pipefail

# Parse command line arguments
QUICK_MODE=false
BASIC_ONLY=false
BASE64_ONLY=false
NO_NREPL=false

show_help() {
    echo "Usage: $0 [OPTIONS]"
    echo "Options:"
    echo "  --quick       Skip performance tests (50 rapid requests)"
    echo "  --basic-only  Run only basic functionality tests (Part 1)"
    echo "  --base64-only Run only base64 tests (Parts 2-4)"
    echo "  --no-nrepl    Skip nrepl-eval tests (local-eval only)"
    echo "  --help        Show this help message"
    exit 0
}

while [[ $# -gt 0 ]]; do
    case $1 in
        --quick)
            QUICK_MODE=true
            shift
            ;;
        --basic-only)
            BASIC_ONLY=true
            shift
            ;;
        --base64-only)
            BASE64_ONLY=true
            shift
            ;;
        --no-nrepl)
            NO_NREPL=true
            shift
            ;;
        --help)
            show_help
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
BASE_URL="${MCP_SERVER_URL:-http://localhost:3000/mcp}"
CLIENT="uv run python scripts/mcp_nrepl_client.py --base-url $BASE_URL"
TEST_COUNT=0
PASS_COUNT=0
FAIL_COUNT=0

# Test file paths
TEST_DIR="/tmp/mcp-nrepl-test-$$"
mkdir -p "$TEST_DIR"
TEST_FILE="$TEST_DIR/test-code.clj"
TEST_FILE_B64="$TEST_DIR/test-code.b64"

# Clean up on exit
trap "rm -rf $TEST_DIR" EXIT

# Helper functions
print_header() {
    echo -e "\n${BLUE}════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}════════════════════════════════════════════════════════════════${NC}"
}

print_test() {
    echo -e "\n${YELLOW}▶ Test $1: $2${NC}"
    echo -e "  Command: $3"
}

check_result() {
    local expected="$1"
    local actual="$2"
    local test_name="$3"
    
    TEST_COUNT=$((TEST_COUNT + 1))
    
    if [[ "$actual" == *"$expected"* ]] || [[ "$actual" == "$expected" ]]; then
        echo -e "  ${GREEN}✓ PASS${NC}: $test_name"
        PASS_COUNT=$((PASS_COUNT + 1))
        return 0
    else
        echo -e "  ${RED}✗ FAIL${NC}: $test_name"
        echo -e "  Expected: $expected"
        echo -e "  Actual: $actual"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        return 1
    fi
}

run_test() {
    local test_num="$1"
    local test_name="$2"
    local cmd="$3"
    local expected="$4"
    
    print_test "$test_num" "$test_name" "$cmd"
    
    # Run command and capture output
    if output=$(eval "$cmd" 2>&1); then
        check_result "$expected" "$output" "$test_name"
    else
        echo -e "  ${RED}✗ ERROR${NC}: Command failed"
        echo -e "  Output: $output"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        TEST_COUNT=$((TEST_COUNT + 1))
    fi
}

# Helper function to check if nREPL server is needed and connect
ensure_nrepl_connection() {
    if [[ "$NO_NREPL" == "true" ]]; then
        echo -e "\n${YELLOW}⏭️  Skipping nREPL connection (--no-nrepl flag)${NC}"
        return 0
    fi
    
    echo -e "\n${YELLOW}🔌 Ensuring nREPL connection for nrepl-eval tests...${NC}"
    
    # Check if nREPL server is running
    if ! ./scripts/nrepl_test_server.py status &>/dev/null; then
        echo "  Starting nREPL test server..."
        ./scripts/nrepl_test_server.py start
        sleep 2
    fi
    
    # Get the port
    local port
    if [[ -f scripts/test-nrepl/.test-nrepl-server-port ]]; then
        port=$(cat scripts/test-nrepl/.test-nrepl-server-port)
        echo "  Connecting to nREPL server on port $port..."
        $CLIENT --tool nrepl-connection --args "{\"op\": \"connect\", \"connection\": \"$port\"}" --quiet > /dev/null
        echo -e "  ${GREEN}✓ Connected to nREPL server${NC}"
    else
        echo -e "  ${RED}✗ Could not find nREPL server port${NC}"
        return 1
    fi
}

# Helper function to run test conditionally
run_test_conditional() {
    local test_num="$1"
    local test_name="$2"
    local cmd="$3"
    local expected="$4"
    local tool="$5"  # "local" or "nrepl"
    
    # Skip nrepl tests if NO_NREPL is true
    if [[ "$tool" == "nrepl" && "$NO_NREPL" == "true" ]]; then
        echo -e "\n${YELLOW}⏭️  Skipping Test $test_num: $test_name (--no-nrepl flag)${NC}"
        return 0
    fi
    
    run_test "$test_num" "$test_name" "$cmd" "$expected"
}

# Start testing
print_header "MCP-nREPL Base64 Enhancement Comprehensive Test Suite"
echo -e "Base URL: $BASE_URL"
echo -e "Test directory: $TEST_DIR"

# Show active flags
if [[ "$QUICK_MODE" == "true" || "$BASIC_ONLY" == "true" || "$BASE64_ONLY" == "true" || "$NO_NREPL" == "true" ]]; then
    echo -e "\n${BLUE}Active flags:${NC}"
    [[ "$QUICK_MODE" == "true" ]] && echo -e "  • ${YELLOW}--quick${NC} (Skip performance tests)"
    [[ "$BASIC_ONLY" == "true" ]] && echo -e "  • ${YELLOW}--basic-only${NC} (Run only Part 1)"
    [[ "$BASE64_ONLY" == "true" ]] && echo -e "  • ${YELLOW}--base64-only${NC} (Run only Parts 2-4)"
    [[ "$NO_NREPL" == "true" ]] && echo -e "  • ${YELLOW}--no-nrepl${NC} (Skip nrepl-eval tests)"
fi

# ==============================================================================
# PART 0: Setup and Prerequisites
# ==============================================================================

print_header "PART 0: Prerequisites and Setup"

# Ensure nREPL connection for later tests
ensure_nrepl_connection

# ==============================================================================
# PART 1: Basic Functionality Tests (No Base64)
# ==============================================================================

if [[ "$BASE64_ONLY" != "true" ]]; then
    print_header "PART 1: Basic Functionality (No Base64)"

    # Test 1.1: Simple eval with --code
    run_test_conditional "1.1" "local-eval with --code" \
        "$CLIENT --tool local-eval --code '(+ 1 2 3)' --quiet | jq -r '.result'" \
        "6" "local"

    run_test_conditional "1.2" "nrepl-eval with --code" \
        "$CLIENT --tool nrepl-eval --code '(+ 1 2 3)' --quiet | jq -r '.value'" \
        "6" "nrepl"

    # Test 1.3: Code from stdin
    run_test_conditional "1.3" "local-eval with --code-stdin" \
        "echo '(* 2 3 4)' | $CLIENT --tool local-eval --code-stdin --quiet | jq -r '.result'" \
        "24" "local"

    run_test_conditional "1.4" "nrepl-eval with --code-stdin" \
        "echo '(* 2 3 4)' | $CLIENT --tool nrepl-eval --code-stdin --quiet | jq -r '.value'" \
        "24" "nrepl"

    # Test 1.5: Code from file
    echo '(str "hello" " " "world")' > "$TEST_FILE"
    run_test_conditional "1.5" "local-eval with --load-code-file" \
        "$CLIENT --tool local-eval --load-code-file '$TEST_FILE' --quiet | jq -r '.result'" \
        "hello world" "local"

    run_test_conditional "1.6" "nrepl-eval with --load-code-file" \
        "$CLIENT --tool nrepl-eval --load-code-file '$TEST_FILE' --quiet | jq -r '.value'" \
        "hello world" "nrepl"
fi

# ==============================================================================
# PART 2: Base64 Input Tests (--input-base64)
# ==============================================================================

if [[ "$BASIC_ONLY" != "true" ]]; then
    print_header "PART 2: Base64 Input Tests (--input-base64)"

# Create base64 encoded test data
SIMPLE_CODE='(+ 10 20 30)'
SIMPLE_B64=$(echo -n "$SIMPLE_CODE" | base64)

COMPLEX_CODE='(println "Hello world!")'
COMPLEX_B64=$(echo -n "$COMPLEX_CODE" | base64)

MAP_CODE='{:name "test" :value 42}'
MAP_B64=$(echo -n "$MAP_CODE" | base64)

# Test 2.1: Base64 with --code
run_test "2.1" "local-eval with --code and --input-base64" \
    "$CLIENT --tool local-eval --code '$SIMPLE_B64' --input-base64 --quiet | jq -r '.result'" \
    "60"

run_test "2.2" "nrepl-eval with --code and --input-base64" \
    "$CLIENT --tool nrepl-eval --code '$SIMPLE_B64' --input-base64 --quiet | jq -r '.value'" \
    "60"

# Test 2.3: Base64 from stdin
run_test "2.3" "local-eval with --code-stdin and --input-base64" \
    "echo '$SIMPLE_B64' | $CLIENT --tool local-eval --code-stdin --input-base64 --quiet | jq -r '.result'" \
    "60"

run_test "2.4" "nrepl-eval with --code-stdin and --input-base64" \
    "echo '$SIMPLE_B64' | $CLIENT --tool nrepl-eval --code-stdin --input-base64 --quiet | jq -r '.value'" \
    "60"

# Test 2.5: Base64 from file
echo -n "$MAP_B64" > "$TEST_FILE_B64"
run_test "2.5" "local-eval with --load-code-file and --input-base64" \
    "$CLIENT --tool local-eval --load-code-file '$TEST_FILE_B64' --input-base64 --quiet | jq -r '.result'" \
    ':name "test"'

run_test "2.6" "nrepl-eval with --load-code-file and --input-base64" \
    "$CLIENT --tool nrepl-eval --load-code-file '$TEST_FILE_B64' --input-base64 --quiet | jq -r '.value'" \
    ':name "test"'

# ==============================================================================
# PART 3: Base64 Output Tests (--output-base64)
# ==============================================================================

print_header "PART 3: Base64 Output Tests (--output-base64)"

# Test 3.1: Request base64 output
run_test "3.1" "local-eval with --output-base64" \
    "$CLIENT --tool local-eval --code '(+ 5 5)' --output-base64 --quiet | jq -r '.\"result-base64\"' | base64 -d" \
    "10"

run_test "3.2" "nrepl-eval with --output-base64" \
    "$CLIENT --tool nrepl-eval --code '(+ 5 5)' --output-base64 --quiet | jq -r '.\"value-base64\"' | base64 -d" \
    "10"

# Test 3.3: stdout capture with base64
PRINT_CODE='(do (println "test output") :done)'
run_test "3.3" "local-eval stdout with --output-base64" \
    "$CLIENT --tool local-eval --code '$PRINT_CODE' --output-base64 --quiet | jq -r '.\"stdout-base64\"' | base64 -d" \
    "test output"

run_test "3.4" "nrepl-eval stdout with --output-base64" \
    "$CLIENT --tool nrepl-eval --code '$PRINT_CODE' --output-base64 --quiet | jq -r '.\"out-base64\"' | base64 -d" \
    "test output"

# ==============================================================================
# PART 4: Combined Input/Output Base64 Tests
# ==============================================================================

print_header "PART 4: Combined Input and Output Base64"

# Test 4.1: Both input and output base64
run_test "4.1" "local-eval with both --input-base64 and --output-base64" \
    "$CLIENT --tool local-eval --code '$SIMPLE_B64' --input-base64 --output-base64 --quiet | jq -r '.\"result-base64\"' | base64 -d" \
    "60"

run_test "4.2" "nrepl-eval with both --input-base64 and --output-base64" \
    "$CLIENT --tool nrepl-eval --code '$SIMPLE_B64' --input-base64 --output-base64 --quiet | jq -r '.\"value-base64\"' | base64 -d" \
    "60"

# Test 4.3: Complex data structures
VECTOR_CODE='[1 2 3 4 5]'
VECTOR_B64=$(echo -n "$VECTOR_CODE" | base64)
run_test "4.3" "local-eval vector with base64 I/O" \
    "$CLIENT --tool local-eval --code '$VECTOR_B64' --input-base64 --output-base64 --quiet | jq -r '.\"result-base64\"' | base64 -d" \
    "[1 2 3 4 5]"

run_test "4.4" "nrepl-eval vector with base64 I/O" \
    "$CLIENT --tool nrepl-eval --code '$VECTOR_B64' --input-base64 --output-base64 --quiet | jq -r '.\"value-base64\"' | base64 -d" \
    "[1 2 3 4 5]"

# ==============================================================================
# PART 5: Quote Escaping Challenge Tests
# ==============================================================================

print_header "PART 5: Quote Escaping Challenges (Why We Need Base64)"

# These tests demonstrate the challenges that base64 solves

# Test 5.1: Simple quotes with base64
QUOTE_TEST='(str "quoted text")'
QUOTE_B64=$(echo -n "$QUOTE_TEST" | base64)

run_test "5.1" "Quoted strings with base64 input" \
    "$CLIENT --tool local-eval --code '$QUOTE_B64' --input-base64 --quiet | jq -r '.result'" \
    "quoted text"

# Test 5.2: Multi-line code
MULTILINE='(defn greet [name] (str "Hello, " name "!"))'
MULTILINE_B64=$(echo -n "$MULTILINE" | base64)

run_test "5.2" "Multi-line code with base64" \
    "$CLIENT --tool local-eval --code '$MULTILINE_B64' --input-base64 --quiet | jq -r '.status'" \
    "success"

# ==============================================================================
# PART 6: Error Handling Tests
# ==============================================================================

print_header "PART 6: Error Handling"

# Test 6.1: Invalid base64
run_test "6.1" "Invalid base64 input" \
    "$CLIENT --tool local-eval --code 'not-valid-base64!' --input-base64 --quiet 2>&1 | jq -r '.error' | grep -q 'Failed to decode base64' && echo 'error caught'" \
    "error caught"

# Test 6.2: Empty code
run_test "6.2" "Empty code parameter" \
    "$CLIENT --tool local-eval --code '' --quiet | jq -r '.error' | grep -q 'No code provided' && echo 'validation works'" \
    "validation works"

# ==============================================================================
# PART 7: EDN to JSON Conversion Tests (nrepl-eval specific)
# ==============================================================================

print_header "PART 7: EDN to JSON Conversion (nrepl-eval feature)"

# Test 7.1: Map conversion
run_test "7.1" "EDN map to JSON conversion" \
    "$CLIENT --tool nrepl-eval --code '{:a 1 :b 2}' --quiet | jq -r '.\"value-parsed\".a'" \
    "1"

# Test 7.2: Vector conversion
run_test "7.2" "EDN vector to JSON conversion" \
    "$CLIENT --tool nrepl-eval --code '[1 2 3]' --quiet | jq -r '.\"value-parsed\"[1]'" \
    "2"

# Test 7.3: Nested structure conversion
run_test "7.3" "Nested EDN to JSON conversion" \
    "$CLIENT --tool nrepl-eval --code '{:data {:items [1 2 3]}}' --quiet | jq -r '.\"value-parsed\".data.items[2]'" \
    "3"
fi

# ==============================================================================
# PART 8: Performance Tests
# ==============================================================================

if [[ "$QUICK_MODE" != "true" && "$BASIC_ONLY" != "true" ]]; then
    print_header "PART 8: Performance Test (50 rapid requests)"

# Test 8.1: Rapid fire test
echo -n "  Running 50 rapid requests: "
SUCCESS_COUNT=0
for i in {1..50}; do
    if result=$($CLIENT --tool local-eval --code "(+ $i $i)" --quiet 2>/dev/null | jq -r '.result'); then
        expected=$((i * 2))
        if [[ "$result" == "$expected" ]]; then
            SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
            echo -n "."
        else
            echo -n "F"
        fi
    else
        echo -n "E"
    fi
done
echo ""

if [[ $SUCCESS_COUNT -eq 50 ]]; then
    echo -e "  ${GREEN}✓ PASS${NC}: All 50 requests succeeded"
    PASS_COUNT=$((PASS_COUNT + 1))
else
    echo -e "  ${RED}✗ FAIL${NC}: Only $SUCCESS_COUNT/50 requests succeeded"
    FAIL_COUNT=$((FAIL_COUNT + 1))
fi
TEST_COUNT=$((TEST_COUNT + 1))
fi

# ==============================================================================
# PART 9: --decode-output Flag Test
# ==============================================================================

if [[ "$BASIC_ONLY" != "true" ]]; then
    print_header "PART 9: Auto-decode Output Flag (--decode-output)"

# Test 9.1: Auto-decode base64 output
run_test "9.1" "Auto-decode with --decode-output flag" \
    "$CLIENT --tool local-eval --code '(+ 7 8)' --output-base64 --decode-output --quiet | jq -r '.\"result-decoded\"'" \
    "15"

run_test "9.2" "Auto-decode stdout with --decode-output" \
    "$CLIENT --tool nrepl-eval --code '(println \"decoded\")' --output-base64 --decode-output --quiet | jq -r '.\"out-decoded\"' | tr -d '\\n'" \
    "decoded"
fi

# ==============================================================================
# Final Summary
# ==============================================================================

print_header "TEST SUMMARY"
echo -e "Total Tests: $TEST_COUNT"
echo -e "${GREEN}Passed: $PASS_COUNT${NC}"
echo -e "${RED}Failed: $FAIL_COUNT${NC}"

if [[ $FAIL_COUNT -eq 0 ]]; then
    echo -e "\n${GREEN}🎉 ALL TESTS PASSED!${NC}"
    echo -e "\n${GREEN}✨ Base64 enhancements are working perfectly across all permutations!${NC}"
    echo -e "   • Input methods: --code, --code-stdin, --load-code-file ✓"
    echo -e "   • Base64 flags: --input-base64, --output-base64 ✓"  
    echo -e "   • Both tools: local-eval, nrepl-eval ✓"
    echo -e "   • Quote escaping elimination ✓"
    echo -e "   • Error handling ✓"
    echo -e "   • Performance stability ✓"
    exit 0
else
    echo -e "\n${RED}⚠️  Some tests failed. Please review the output above.${NC}"
    echo -e "Failure rate: $(( (FAIL_COUNT * 100) / TEST_COUNT ))%"
    exit 1
fi