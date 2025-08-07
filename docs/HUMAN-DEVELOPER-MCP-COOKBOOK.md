# Human Developer MCP-nREPL Cookbook

## Overview

This cookbook provides **practical examples** for human developers using the MCP-nREPL server through the Python MCP client for interactive Clojure/ClojureScript development and Python application introspection. This guide focuses on real terminal workflows that developers can use daily.

## Prerequisites

### 1. Setup and Installation

```bash
# Clone the repository
git clone https://github.com/franks42/mcp-nrepl-joyride.git
cd mcp-nrepl-joyride

# Start the MCP-nREPL server
./start-mcp-http-server.sh

# Verify connection (in another terminal)
python3 ./mcp_nrepl_client.py --eval "(+ 1 2 3)" --quiet
# Expected output: 6
```

### 2. Available Tools Overview

```bash
# List all available tools
python3 ./mcp_nrepl_client.py --tools

# Get help for specific functionality
python3 ./mcp_nrepl_client.py --help
```

## Quick Start Patterns

### Basic Connection and Testing

```bash
# Test connection and basic math
python3 ./mcp_nrepl_client.py --eval "(+ 1 2 3)"
python3 ./mcp_nrepl_client.py --eval "(* 6 7)"

# Check current environment
python3 ./mcp_nrepl_client.py --eval "*ns*"
python3 ./mcp_nrepl_client.py --eval "(clojure-version)"

# Run comprehensive health tests
python3 ./mcp_nrepl_client.py --tool nrepl-test
```

### Connection Management

```bash
# Check connection status
python3 ./mcp_nrepl_client.py --tool nrepl-status

# Connect to specific port (if needed)
python3 ./mcp_nrepl_client.py --tool nrepl-connect --args '{"host": "localhost", "port": 7888}'

# Create new session for isolated work
python3 ./mcp_nrepl_client.py --tool nrepl-new-session
```

## Interactive Clojure Development Workflows

### Phase 1: Environment Setup and Exploration

**Discover what's available:**
```bash
# Check current namespace contents
python3 ./mcp_nrepl_client.py --eval "(keys (ns-publics *ns*))"

# Explore available namespaces
python3 ./mcp_nrepl_client.py --eval "(map str (all-ns))"

# Find functions by pattern
python3 ./mcp_nrepl_client.py --tool nrepl-apropos --args '{"query": "map"}'
python3 ./mcp_nrepl_client.py --tool nrepl-apropos --args '{"query": "reduce"}'
```

**Load essential namespaces:**
```bash
# Load commonly used namespaces
python3 ./mcp_nrepl_client.py --tool nrepl-require --args '{"namespace": "clojure.string", "as": "str"}'
python3 ./mcp_nrepl_client.py --tool nrepl-require --args '{"namespace": "clojure.set", "as": "set"}'
python3 ./mcp_nrepl_client.py --tool nrepl-require --args '{"namespace": "clojure.pprint", "refer": ["pprint", "pp"]}'
```

### Phase 2: Interactive Function Development

**Start with simple functions:**
```bash
# Define and test basic functions
python3 ./mcp_nrepl_client.py --eval "(defn square [x] (* x x))"
python3 ./mcp_nrepl_client.py --eval "(square 5)" # Test: should return 25

# Build on success
python3 ./mcp_nrepl_client.py --eval "(defn sum-of-squares [coll] (reduce + (map square coll)))"
python3 ./mcp_nrepl_client.py --eval "(sum-of-squares [1 2 3 4])" # Test: should return 30
```

**Get help and documentation:**
```bash
# Get documentation for functions
python3 ./mcp_nrepl_client.py --tool nrepl-doc --args '{"symbol": "map"}'
python3 ./mcp_nrepl_client.py --tool nrepl-doc --args '{"symbol": "reduce"}'

# View source code
python3 ./mcp_nrepl_client.py --tool nrepl-source --args '{"symbol": "defn"}'

# Get auto-completions
python3 ./mcp_nrepl_client.py --tool nrepl-complete --args '{"prefix": "ma"}'
python3 ./mcp_nrepl_client.py --tool nrepl-complete --args '{"prefix": "str/"}'
```

**Error handling and debugging:**
```bash
# Intentionally create an error
python3 ./mcp_nrepl_client.py --eval "(/ 1 0)"

# Get detailed error information
python3 ./mcp_nrepl_client.py --tool nrepl-stacktrace

# If evaluation hangs, interrupt it (in another terminal)
python3 ./mcp_nrepl_client.py --tool nrepl-interrupt
```

### Phase 3: Working with Data

**Generate and explore sample data:**
```bash
# Create sample dataset
python3 ./mcp_nrepl_client.py --eval "(def users [{:name \"Alice\" :age 30 :role \"Engineer\"} {:name \"Bob\" :age 25 :role \"Designer\"} {:name \"Carol\" :age 35 :role \"Manager\"}])"

# Explore the data
python3 ./mcp_nrepl_client.py --eval "(count users)"
python3 ./mcp_nrepl_client.py --eval "(first users)"
python3 ./mcp_nrepl_client.py --eval "(map :name users)"

# Transform and analyze
python3 ./mcp_nrepl_client.py --eval "(filter #(> (:age %) 28) users)"
python3 ./mcp_nrepl_client.py --eval "(group-by :role users)"
python3 ./mcp_nrepl_client.py --eval "(reduce + (map :age users))" # Total age
```

**Complex data processing:**
```bash
# Define data processing pipeline
python3 ./mcp_nrepl_client.py --eval "(defn process-users [users] (->> users (filter #(> (:age %) 25)) (map #(select-keys % [:name :role])) (sort-by :name)))"

# Test the pipeline
python3 ./mcp_nrepl_client.py --eval "(process-users users)"

# Pretty print results
python3 ./mcp_nrepl_client.py --eval "(pprint (process-users users))"
```

### Phase 4: File-Based Development

**Create and load Clojure files:**
```bash
# Create a utility file (you can do this in your editor)
cat > /tmp/my-utils.clj << 'EOF'
(ns my-utils
  (:require [clojure.string :as str]))

(defn clean-string
  "Clean and normalize a string"
  [s]
  (-> s
      str/trim
      str/lower-case
      (str/replace #"[^\w\s]" "")))

(defn word-count
  "Count words in text"
  [text]
  (->> text
       clean-string
       (#(str/split % #"\s+"))
       (remove empty?)
       count))
EOF

# Load the file
python3 ./mcp_nrepl_client.py --tool nrepl-load-file --args '{"file-path": "/tmp/my-utils.clj"}'

# Test the loaded functions
python3 ./mcp_nrepl_client.py --eval "(my-utils/clean-string \"Hello, World! How are you?\")"
python3 ./mcp_nrepl_client.py --eval "(my-utils/word-count \"Hello, World! How are you?\")"
```

**Reload during development:**
```bash
# After making changes to the file, reload it
python3 ./mcp_nrepl_client.py --tool nrepl-require --args '{"namespace": "my-utils", "reload": true}'

# Or reload the file directly
python3 ./mcp_nrepl_client.py --tool nrepl-load-file --args '{"file-path": "/tmp/my-utils.clj"}'
```

## Advanced Development Patterns

### Macro Development and Testing

```bash
# Define a simple macro
python3 ./mcp_nrepl_client.py --eval "(defmacro unless [test & body] \`(if (not ~test) (do ~@body)))"

# Test macro expansion
python3 ./mcp_nrepl_client.py --eval "(macroexpand '(unless false (println \"This will print\")))"

# Test macro execution
python3 ./mcp_nrepl_client.py --eval "(unless false (println \"Hello from macro!\"))"
python3 ./mcp_nrepl_client.py --eval "(unless true (println \"This won't print\"))"
```

### Testing and Validation

```bash
# Load testing namespace
python3 ./mcp_nrepl_client.py --tool nrepl-require --args '{"namespace": "clojure.test", "refer": ["deftest", "is", "testing", "run-tests"]}'

# Define tests
python3 ./mcp_nrepl_client.py --eval "(deftest test-square (is (= 25 (square 5))) (is (= 0 (square 0))) (is (= 1 (square 1))))"

# Run tests
python3 ./mcp_nrepl_client.py --eval "(test-square)"
python3 ./mcp_nrepl_client.py --eval "(run-tests)"
```

### Performance Analysis

```bash
# Time operations
python3 ./mcp_nrepl_client.py --eval "(time (reduce + (range 100000)))"

# Compare implementations
python3 ./mcp_nrepl_client.py --eval "(defn sum-for [n] (loop [i 0 acc 0] (if (< i n) (recur (inc i) (+ acc i)) acc)))"
python3 ./mcp_nrepl_client.py --eval "(defn sum-reduce [n] (reduce + (range n)))"

python3 ./mcp_nrepl_client.py --eval "(time (sum-for 100000))"
python3 ./mcp_nrepl_client.py --eval "(time (sum-reduce 100000))"

# Memory usage (if available)
python3 ./mcp_nrepl_client.py --eval "(let [rt (Runtime/getRuntime)] {:total (.totalMemory rt) :free (.freeMemory rt)})"
```

## Python Application Introspection

### Connecting to Python Applications

**Prerequisites for Python introspection:**
```bash
# Your Python application must have Basilisp + nREPL embedded
# See examples/flask_with_basilisp.py for implementation example

# Start your Python app with nREPL enabled
ENABLE_NREPL=true python examples/flask_with_basilisp.py

# Connect to the Python application's nREPL (usually different port)
python3 ./mcp_nrepl_client.py --tool nrepl-connect --args '{"host": "localhost", "port": 7888}'
```

### Loading Python Introspection Utilities

```bash
# Load the comprehensive Python introspection utilities
python3 ./mcp_nrepl_client.py --tool nrepl-load-file --args '{"file-path": "examples/python_introspection_utils.clj"}'

# Verify utilities are loaded
python3 ./mcp_nrepl_client.py --tool nrepl-apropos --args '{"query": "py-"}'

# Get help for available functions
python3 ./mcp_nrepl_client.py --eval "(py-help)"
```

### Python System Introspection

```bash
# Basic system information
python3 ./mcp_nrepl_client.py --eval "(py-system-info)"
python3 ./mcp_nrepl_client.py --eval "(py-quick-status)"

# Memory and performance
python3 ./mcp_nrepl_client.py --eval "(py-memory-info)"
python3 ./mcp_nrepl_client.py --eval "(py-performance-snapshot)"

# Comprehensive health check
python3 ./mcp_nrepl_client.py --eval "(py-health-check)"
```

### Python Application Exploration

```bash
# Explore Python objects
python3 ./mcp_nrepl_client.py --eval "(py-explore-object python/app)"
python3 ./mcp_nrepl_client.py --eval "(py-explore-object python/sys)"

# Check configuration (safely filters secrets)
python3 ./mcp_nrepl_client.py --eval "(py-config-summary)"

# Database status
python3 ./mcp_nrepl_client.py --eval "(py-db-status)"

# Module analysis
python3 ./mcp_nrepl_client.py --eval "(py-list-app-modules \"myapp\")"
python3 ./mcp_nrepl_client.py --eval "(py-module-info \"flask\")"
```

### Python Development Utilities

```bash
# Reload Python modules during development
python3 ./mcp_nrepl_client.py --eval "(py-reload-module \"myapp.models\")"

# Clear application caches
python3 ./mcp_nrepl_client.py --eval "(py-clear-caches)"

# Find objects by pattern
python3 ./mcp_nrepl_client.py --eval "(py-find-by-pattern \"user\")"
```

## Session Management for Complex Workflows

### Working with Multiple Sessions

```bash
# Create a new session for a specific project
SESSION_ID=$(python3 ./mcp_nrepl_client.py --tool nrepl-new-session --quiet | jq -r '.content[0].text')
echo "Created session: $SESSION_ID"

# Work within the session
python3 ./mcp_nrepl_client.py --eval "(def project-data {:name \"MyProject\" :version \"1.0.0\"})" --session "$SESSION_ID"
python3 ./mcp_nrepl_client.py --eval "project-data" --session "$SESSION_ID"

# Load project-specific utilities in the session
python3 ./mcp_nrepl_client.py --tool nrepl-load-file --args '{"file-path": "/tmp/my-utils.clj"}' --session "$SESSION_ID"
```

### Session-Based Python Introspection

```bash
# Create dedicated session for Python debugging
DEBUG_SESSION=$(python3 ./mcp_nrepl_client.py --tool nrepl-new-session --quiet | jq -r '.content[0].text')

# Load Python utilities in the debug session
python3 ./mcp_nrepl_client.py --tool nrepl-load-file --args '{"file-path": "examples/python_introspection_utils.clj"}' --session "$DEBUG_SESSION"

# Perform debugging in isolated session
python3 ./mcp_nrepl_client.py --eval "(py-health-check)" --session "$DEBUG_SESSION"
python3 ./mcp_nrepl_client.py --eval "(def debug-info (py-system-info))" --session "$DEBUG_SESSION"
```

## VS Code Integration

### Setting up MCP-nREPL in VS Code

```bash
# Install the VS Code extension (if available)
# Configure your settings.json to include the MCP server

# Example VS Code settings.json configuration:
cat > .vscode/settings.json << 'EOF'
{
    "mcp.servers": {
        "mcp-nrepl-joyride": {
            "command": "node",
            "args": ["path/to/mcp-nrepl-server.js"],
            "env": {
                "NREPL_HOST": "localhost",
                "NREPL_PORT": "7889"
            }
        }
    }
}
EOF
```

### VS Code Workflow Patterns

```bash
# From VS Code terminal, you can use all the same commands
# The advantage is having your editor and REPL in the same workspace

# Quick evaluation from terminal
python3 ./mcp_nrepl_client.py --eval "(+ 1 2 3)"

# Load the current file you're editing
python3 ./mcp_nrepl_client.py --tool nrepl-load-file --args '{"file-path": "'$(pwd)'/src/my_project/core.clj"}'

# Get documentation for symbol under cursor (copy symbol first)
python3 ./mcp_nrepl_client.py --tool nrepl-doc --args '{"symbol": "map"}'
```

## Troubleshooting and Best Practices

### Common Issues and Solutions

**Connection Problems:**
```bash
# Check if server is running
python3 ./mcp_nrepl_client.py --tool nrepl-status

# If connection fails, check server logs
tail -f server.log

# Restart server if needed
./start-mcp-http-server.sh
```

**Symbol Not Found:**
```bash
# Check current namespace
python3 ./mcp_nrepl_client.py --eval "*ns*"

# Search for similar symbols
python3 ./mcp_nrepl_client.py --tool nrepl-apropos --args '{"query": "partial-symbol"}'

# Load missing namespace
python3 ./mcp_nrepl_client.py --tool nrepl-require --args '{"namespace": "missing.namespace"}'
```

**Evaluation Hangs:**
```bash
# Interrupt in another terminal
python3 ./mcp_nrepl_client.py --tool nrepl-interrupt

# Check what went wrong
python3 ./mcp_nrepl_client.py --tool nrepl-stacktrace

# Reset to clean state
python3 ./mcp_nrepl_client.py --eval "(in-ns 'user)"
```

### Development Best Practices

**1. Start Small and Build Up:**
```bash
# Test basic operations first
python3 ./mcp_nrepl_client.py --eval "(+ 1 2)"

# Then build complexity
python3 ./mcp_nrepl_client.py --eval "(defn add [a b] (+ a b))"
python3 ./mcp_nrepl_client.py --eval "(add 3 4)"
```

**2. Use Documentation Extensively:**
```bash
# Before using unfamiliar functions
python3 ./mcp_nrepl_client.py --tool nrepl-doc --args '{"symbol": "reduce"}'
python3 ./mcp_nrepl_client.py --tool nrepl-source --args '{"symbol": "reduce"}'
```

**3. Leverage Auto-completion:**
```bash
# Get suggestions for what you're typing
python3 ./mcp_nrepl_client.py --tool nrepl-complete --args '{"prefix": "clojure.str"}'
python3 ./mcp_nrepl_client.py --tool nrepl-complete --args '{"prefix": "def"}'
```

**4. Save Your Work:**
```bash
# Save successful experiments to files
echo "(defn my-useful-function [x] (inc x))" >> my-project-utils.clj

# Load saved work
python3 ./mcp_nrepl_client.py --tool nrepl-load-file --args '{"file-path": "my-project-utils.clj"}'
```

## Advanced Integration Patterns

### Shell Scripting with MCP-nREPL

```bash
#!/bin/bash
# Example script for automated Clojure testing

# Function to evaluate Clojure and get result
eval_clj() {
    python3 ./mcp_nrepl_client.py --eval "$1" --quiet
}

# Load test utilities
python3 ./mcp_nrepl_client.py --tool nrepl-load-file --args '{"file-path": "test/test_utils.clj"}'

# Run tests and capture results
RESULT=$(eval_clj "(run-all-tests)")
echo "Test results: $RESULT"

# Check if all tests passed
if [[ $RESULT == *"0 failures"* ]]; then
    echo "✅ All tests passed!"
else
    echo "❌ Tests failed!"
    exit 1
fi
```

### Integration with Other Tools

```bash
# Generate documentation from docstrings
python3 ./mcp_nrepl_client.py --tool nrepl-apropos --args '{"query": "my-project"}' | \
    jq -r '.content[0].text' | \
    while read -r func; do
        echo "## $func"
        python3 ./mcp_nrepl_client.py --tool nrepl-doc --args "{\"symbol\": \"$func\"}" --quiet
        echo
    done > project-docs.md

# Performance benchmarking
echo "Function,Time(ms)" > benchmark.csv
for func in "map" "filter" "reduce"; do
    time_result=$(python3 ./mcp_nrepl_client.py --eval "(time ($func identity (range 10000)))" --quiet)
    echo "$func,$time_result" >> benchmark.csv
done
```

This cookbook provides human developers with practical, copy-pasteable commands for leveraging the full power of MCP-nREPL in real development workflows!