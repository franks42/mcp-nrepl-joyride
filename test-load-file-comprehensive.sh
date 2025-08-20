#!/bin/bash

# Comprehensive test suite for both load-file implementations
# Tests unified semantics and shared code behavior

set -e  # Exit on any error

echo "🧪 Testing Load-File Tools Comprehensive Suite"
echo "=============================================="

# =============================================================================
# Test Setup
# =============================================================================

echo "🔧 Setting up test environment..."

# Start services
echo "📡 Starting HTTP bridge..."
./scripts/stop-http-bridge.sh >/dev/null 2>&1 || true
./scripts/start-http-bridge.sh >/dev/null 2>&1

echo "🖥️ Starting nREPL test server..."
./scripts/nrepl_test_server.py restart >/dev/null 2>&1

# Give services time to start
sleep 2

# Create test files
TEST_DIR="/tmp/mcp-load-file-test"
mkdir -p "$TEST_DIR"

echo "📝 Creating test files..."

# Simple test file
cat > "$TEST_DIR/simple-test.clj" << 'EOF'
(def test-var 42)
(println "Simple test file loaded")
test-var
EOF

# Namespace test file
cat > "$TEST_DIR/namespace-test.clj" << 'EOF'
(ns test.example)

(defn greet [name]
  (str "Hello " name))

(def greeting (greet "World"))
(println "Namespace test file loaded:" greeting)
greeting
EOF

# Output test file
cat > "$TEST_DIR/output-test.clj" << 'EOF'
(println "This goes to stdout")
(binding [*out* *err*]
  (println "This goes to stderr"))
(+ 10 20 30)
EOF

# Error test file (syntax error)
cat > "$TEST_DIR/syntax-error.clj" << 'EOF'
(def valid-form 42)
invalid clojure syntax (
EOF

# Runtime error test file
cat > "$TEST_DIR/runtime-error.clj" << 'EOF'
(def working-var 42)
(/ 1 0)
EOF

# =============================================================================
# Test Functions
# =============================================================================

test_count=0
pass_count=0
fail_count=0

run_test() {
    local test_name="$1"
    local tool="$2"
    local args="$3"
    local expected_status="$4"  # "success" or "error"
    
    ((test_count++))
    echo
    echo "📋 Test $test_count: $test_name"
    echo "   Tool: $tool"
    echo "   Args: $args"
    
    local result
    result=$(python3 scripts/mcp_nrepl_client.py \
        --base-url http://localhost:3000/mcp \
        --tool "$tool" \
        --args "$args" \
        --quiet 2>/dev/null || echo "COMMAND_FAILED")
    
    if [[ "$result" == "COMMAND_FAILED" ]]; then
        echo "   ❌ FAIL: Command execution failed"
        ((fail_count++))
        return 1
    fi
    
    # Parse JSON to check status
    local status
    status=$(echo "$result" | python3 -c "
import json, sys
try:
    data = json.load(sys.stdin)
    print(data.get('status', 'unknown'))
except:
    print('parse_error')
" 2>/dev/null || echo "parse_error")
    
    if [[ "$status" == "$expected_status" ]]; then
        echo "   ✅ PASS: Status '$status' matches expected '$expected_status'"
        ((pass_count++))
        return 0
    else
        echo "   ❌ FAIL: Status '$status' does not match expected '$expected_status'"
        echo "   Full response: $result"
        ((fail_count++))
        return 1
    fi
}

# =============================================================================
# nREPL Load-File Tests
# =============================================================================

echo
echo "📁 Testing nrepl-load-file..."

# Connect to nREPL first
echo "🔗 Connecting to nREPL server..."
nrepl_port=$(cat scripts/test-nrepl/.test-nrepl-server-port)
python3 scripts/mcp_nrepl_client.py \
    --base-url http://localhost:3000/mcp \
    --tool nrepl-connection \
    --args "{\"op\": \"connect\", \"connection\": \"$nrepl_port\"}" \
    --quiet >/dev/null

# Basic functionality tests
run_test "nREPL simple file load" \
    "nrepl-load-file" \
    "{\"file-path\": \"$TEST_DIR/simple-test.clj\"}" \
    "success"

run_test "nREPL namespace file load" \
    "nrepl-load-file" \
    "{\"file-path\": \"$TEST_DIR/namespace-test.clj\"}" \
    "success"

run_test "nREPL output capture" \
    "nrepl-load-file" \
    "{\"file-path\": \"$TEST_DIR/output-test.clj\"}" \
    "success"

# Error handling tests
run_test "nREPL file not found" \
    "nrepl-load-file" \
    "{\"file-path\": \"$TEST_DIR/nonexistent.clj\"}" \
    "error"

run_test "nREPL syntax error" \
    "nrepl-load-file" \
    "{\"file-path\": \"$TEST_DIR/syntax-error.clj\"}" \
    "success"

run_test "nREPL runtime error" \
    "nrepl-load-file" \
    "{\"file-path\": \"$TEST_DIR/runtime-error.clj\"}" \
    "success"

# Parameter validation tests
run_test "nREPL missing file-path" \
    "nrepl-load-file" \
    "{}" \
    "error"

run_test "nREPL empty file-path" \
    "nrepl-load-file" \
    "{\"file-path\": \"\"}" \
    "error"

# =============================================================================
# Local Load-File Tests
# =============================================================================

echo
echo "📂 Testing local-load-file..."

# Basic functionality tests
run_test "Local simple file load" \
    "local-load-file" \
    "{\"file-path\": \"$TEST_DIR/simple-test.clj\"}" \
    "success"

run_test "Local namespace file load" \
    "local-load-file" \
    "{\"file-path\": \"$TEST_DIR/namespace-test.clj\"}" \
    "success"

run_test "Local output capture" \
    "local-load-file" \
    "{\"file-path\": \"$TEST_DIR/output-test.clj\"}" \
    "success"

# Error handling tests  
run_test "Local file not found" \
    "local-load-file" \
    "{\"file-path\": \"$TEST_DIR/nonexistent.clj\"}" \
    "error"

run_test "Local syntax error" \
    "local-load-file" \
    "{\"file-path\": \"$TEST_DIR/syntax-error.clj\"}" \
    "error"

run_test "Local runtime error" \
    "local-load-file" \
    "{\"file-path\": \"$TEST_DIR/runtime-error.clj\"}" \
    "error"

# Parameter validation tests
run_test "Local missing file-path" \
    "local-load-file" \
    "{}" \
    "error"

run_test "Local empty file-path" \
    "local-load-file" \
    "{\"file-path\": \"\"}" \
    "error"

# =============================================================================
# Cleanup and Results
# =============================================================================

echo
echo "🧹 Cleaning up..."
rm -rf "$TEST_DIR"
./scripts/stop-http-bridge.sh >/dev/null 2>&1 || true
./scripts/nrepl_test_server.py stop >/dev/null 2>&1 || true

echo
echo "=============================================="
echo "🎯 Test Results Summary"
echo "=============================================="
echo "Total tests: $test_count"
echo "Passed: $pass_count"
echo "Failed: $fail_count"

if [[ $fail_count -eq 0 ]]; then
    echo "🎉 ALL TESTS PASSED!"
    exit 0
else
    echo "❌ Some tests failed"
    exit 1
fi