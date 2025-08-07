# Clojure/ClojureScript Exploration Cookbook via MCP-nREPL

## Overview

This cookbook provides **practical examples** of using our MCP-nREPL bridge to explore and develop Clojure/ClojureScript applications interactively. All examples use familiar nREPL operations through our unified MCP interface, enabling powerful REPL-driven development workflows.

## Prerequisites

### 1. Start Your Development Environment

```bash
# Start the MCP-nREPL server
./start-mcp-http-server.sh

# Verify connection
python3 ./mcp_nrepl_client.py --eval "(+ 1 2 3)" --quiet
# Expected: 6
```

### 2. Available MCP-nREPL Tools

Our MCP server provides these essential development tools:

```bash
# Connection & Status
python3 ./mcp_nrepl_client.py --tool nrepl-connect --args '{"host": "localhost", "port": 7888}'
python3 ./mcp_nrepl_client.py --tool nrepl-status
python3 ./mcp_nrepl_client.py --tool nrepl-new-session

# Code Evaluation & Loading
python3 ./mcp_nrepl_client.py --eval "YOUR-CODE"
python3 ./mcp_nrepl_client.py --tool nrepl-load-file --args '{"file-path": "/path/to/file.clj"}'

# Symbol Introspection
python3 ./mcp_nrepl_client.py --tool nrepl-doc --args '{"symbol": "map"}'
python3 ./mcp_nrepl_client.py --tool nrepl-source --args '{"symbol": "defn"}'
python3 ./mcp_nrepl_client.py --tool nrepl-apropos --args '{"query": "str"}'

# Development Assistance
python3 ./mcp_nrepl_client.py --tool nrepl-complete --args '{"prefix": "ma"}'
python3 ./mcp_nrepl_client.py --tool nrepl-require --args '{"namespace": "clojure.string"}'

# Error Handling & Debugging
python3 ./mcp_nrepl_client.py --tool nrepl-stacktrace
python3 ./mcp_nrepl_client.py --tool nrepl-interrupt

# Health & Testing
python3 ./mcp_nrepl_client.py --tool nrepl-test
```

## Phase 1: Basic Exploration & Setup

### System Information and Connection

```bash
# Check REPL environment and capabilities
python3 ./mcp_nrepl_client.py --eval "(clojure-version)"
# Shows Clojure version

python3 ./mcp_nrepl_client.py --eval "*clojure-version*"
# Shows detailed version info

python3 ./mcp_nrepl_client.py --eval "(System/getProperty \"java.version\")"
# Shows Java version

# Check available namespaces
python3 ./mcp_nrepl_client.py --eval "(map str (all-ns))" --quiet
```

### Workspace Exploration

```bash
# Check current namespace
python3 ./mcp_nrepl_client.py --eval "*ns*" --quiet

# List vars in current namespace
python3 ./mcp_nrepl_client.py --eval "(keys (ns-publics *ns*))" --quiet

# Check classpath
python3 ./mcp_nrepl_client.py --eval "(System/getProperty \"java.class.path\")" --quiet
```

## Phase 2: Interactive Development Workflow

### Namespace Management

```bash
# Require common namespaces
python3 ./mcp_nrepl_client.py --tool nrepl-require --args '{"namespace": "clojure.string", "as": "str"}'
python3 ./mcp_nrepl_client.py --tool nrepl-require --args '{"namespace": "clojure.set", "as": "set"}'
python3 ./mcp_nrepl_client.py --tool nrepl-require --args '{"namespace": "clojure.walk", "as": "walk"}'

# Require with specific functions
python3 ./mcp_nrepl_client.py --tool nrepl-require --args '{"namespace": "clojure.pprint", "refer": ["pprint", "pp"]}'

# Reload namespace during development
python3 ./mcp_nrepl_client.py --tool nrepl-require --args '{"namespace": "my.project.core", "reload": true}'
```

### Symbol Discovery and Documentation

```bash
# Find symbols by pattern
python3 ./mcp_nrepl_client.py --tool nrepl-apropos --args '{"query": "map"}'
python3 ./mcp_nrepl_client.py --tool nrepl-apropos --args '{"query": "reduce"}'
python3 ./mcp_nrepl_client.py --tool nrepl-apropos --args '{"query": "str", "search-ns": "clojure.string"}'

# Get documentation
python3 ./mcp_nrepl_client.py --tool nrepl-doc --args '{"symbol": "reduce"}'
python3 ./mcp_nrepl_client.py --tool nrepl-doc --args '{"symbol": "map"}'
python3 ./mcp_nrepl_client.py --tool nrepl-doc --args '{"symbol": "filter"}'

# View source code
python3 ./mcp_nrepl_client.py --tool nrepl-source --args '{"symbol": "map"}'
python3 ./mcp_nrepl_client.py --tool nrepl-source --args '{"symbol": "defn"}'
```

### Code Completion and Discovery

```bash
# Get completions for prefixes
python3 ./mcp_nrepl_client.py --tool nrepl-complete --args '{"prefix": "ma"}'
python3 ./mcp_nrepl_client.py --tool nrepl-complete --args '{"prefix": "str"}'
python3 ./mcp_nrepl_client.py --tool nrepl-complete --args '{"prefix": "reduce"}'

# Namespace-specific completions
python3 ./mcp_nrepl_client.py --tool nrepl-complete --args '{"prefix": "string/", "ns": "user"}'
```

## Phase 3: Development Patterns

### Incremental Development

**1. Start with Simple Expressions:**
```bash
# Test basic functionality
python3 ./mcp_nrepl_client.py --eval "(defn greet [name] (str \"Hello, \" name \"!\"))"
python3 ./mcp_nrepl_client.py --eval "(greet \"World\")"
```

**2. Build Up Complex Functions:**
```bash
# Define helper functions
python3 ./mcp_nrepl_client.py --eval "(defn square [x] (* x x))"

# Test with various inputs
python3 ./mcp_nrepl_client.py --eval "(map square [1 2 3 4 5])"

# Refine and improve
python3 ./mcp_nrepl_client.py --eval "(defn square [x] {:pre [(number? x)]} (* x x))"
```

**3. Work with Collections:**
```bash
# Create test data
python3 ./mcp_nrepl_client.py --eval "(def users [{:name \"Alice\" :age 30} {:name \"Bob\" :age 25} {:name \"Carol\" :age 35}])"

# Explore data transformations
python3 ./mcp_nrepl_client.py --eval "(map :name users)"
python3 ./mcp_nrepl_client.py --eval "(filter #(> (:age %) 28) users)"
python3 ./mcp_nrepl_client.py --eval "(reduce + (map :age users))"
```

### File-Based Development

**1. Create Development Files:**
```bash
# Create a new namespace file
cat > /tmp/my_project.clj << 'EOF'
(ns my-project
  "Example project for MCP-nREPL exploration"
  (:require [clojure.string :as str]
            [clojure.set :as set]))

(defn process-text
  "Process text data with various transformations"
  [text]
  (->> text
       str/lower-case
       str/trim
       (re-seq #"\w+")))

(defn word-frequency
  "Calculate word frequencies in text"
  [text]
  (->> (process-text text)
       frequencies
       (sort-by val >)))

(defn common-words
  "Find common words between two texts"
  [text1 text2]
  (set/intersection
    (set (process-text text1))
    (set (process-text text2))))
EOF
```

**2. Load and Test:**
```bash
# Load the file
python3 ./mcp_nrepl_client.py --tool nrepl-load-file --args '{"file-path": "/tmp/my_project.clj"}'

# Switch to the namespace
python3 ./mcp_nrepl_client.py --eval "(in-ns 'my-project)"

# Test functions
python3 ./mcp_nrepl_client.py --eval "(process-text \"Hello World! How are you?\")"
python3 ./mcp_nrepl_client.py --eval "(word-frequency \"hello world hello universe world\")"
python3 ./mcp_nrepl_client.py --eval "(common-words \"hello world\" \"world universe\")"
```

## Phase 4: Advanced Development Techniques

### Data Exploration and Analysis

```bash
# Generate sample data
python3 ./mcp_nrepl_client.py --eval "(def sales-data (for [month (range 1 13) product [\"Widget\" \"Gadget\"]] {:month month :product product :sales (+ 100 (rand-int 200))}))"

# Explore the data structure
python3 ./mcp_nrepl_client.py --eval "(take 5 sales-data)"
python3 ./mcp_nrepl_client.py --eval "(count sales-data)"

# Group and analyze
python3 ./mcp_nrepl_client.py --eval "(group-by :product sales-data)"
python3 ./mcp_nrepl_client.py --eval "(->> sales-data (filter #(= (:product %) \"Widget\")) (map :sales) (reduce +))"
```

### Macro Development and Testing

```bash
# Define a simple macro
python3 ./mcp_nrepl_client.py --eval "(defmacro unless [test & body] \`(if (not ~test) (do ~@body)))"

# Test macro expansion
python3 ./mcp_nrepl_client.py --eval "(macroexpand '(unless false (println \"This will print\")))"

# Test macro execution
python3 ./mcp_nrepl_client.py --eval "(unless false (println \"This will print\"))"
python3 ./mcp_nrepl_client.py --eval "(unless true (println \"This will NOT print\"))"
```

### Error Handling and Debugging

```bash
# Create an error condition
python3 ./mcp_nrepl_client.py --eval "(/ 1 0)"

# Get stacktrace information
python3 ./mcp_nrepl_client.py --tool nrepl-stacktrace

# Test error boundaries
python3 ./mcp_nrepl_client.py --eval "(try (/ 1 0) (catch Exception e (str \"Caught: \" (.getMessage e))))"

# Debug with intermediate values
python3 ./mcp_nrepl_client.py --eval "(defn debug-pipeline [data] (->> data (map inc) (tap> \"after inc:\") (filter even?) (tap> \"after filter:\") (take 5)))"
```

## Phase 5: ClojureScript-Specific Patterns

### Browser Environment Exploration

```bash
# Check if in ClojureScript environment
python3 ./mcp_nrepl_client.py --eval "(exists? js/window)"

# Explore browser APIs (if available)
python3 ./mcp_nrepl_client.py --eval "(when (exists? js/document) js/document.title)"

# Work with JavaScript interop
python3 ./mcp_nrepl_client.py --eval "(js/Date.)"
python3 ./mcp_nrepl_client.py --eval "(.getTime (js/Date.))"
```

### Node.js Environment

```bash
# Check Node.js environment
python3 ./mcp_nrepl_client.py --eval "(exists? js/process)"

# Access Node.js APIs
python3 ./mcp_nrepl_client.py --eval "(when (exists? js/process) js/process.version)"
python3 ./mcp_nrepl_client.py --eval "js/process.env.HOME"

# File system operations
python3 ./mcp_nrepl_client.py --eval "(js/require \"fs\")"
```

## Phase 6: Testing and Quality Assurance

### Unit Testing Patterns

```bash
# Load testing libraries
python3 ./mcp_nrepl_client.py --tool nrepl-require --args '{"namespace": "clojure.test", "refer": ["deftest", "is", "testing", "run-tests"]}'

# Define tests inline
python3 ./mcp_nrepl_client.py --eval "(deftest test-greet (is (= \"Hello, Alice!\" (greet \"Alice\"))))"

# Run specific tests
python3 ./mcp_nrepl_client.py --eval "(test-greet)"

# Run all tests in namespace
python3 ./mcp_nrepl_client.py --eval "(run-tests)"
```

### Property-Based Testing

```bash
# If test.check is available
python3 ./mcp_nrepl_client.py --tool nrepl-require --args '{"namespace": "clojure.test.check", "as": "tc"}'
python3 ./mcp_nrepl_client.py --tool nrepl-require --args '{"namespace": "clojure.test.check.generators", "as": "gen"}'
python3 ./mcp_nrepl_client.py --tool nrepl-require --args '{"namespace": "clojure.test.check.properties", "as": "prop"}'

# Define generators and properties
python3 ./mcp_nrepl_client.py --eval "(def string-gen (gen/such-that not-empty gen/string-alphanumeric))"
```

## Phase 7: Performance Analysis and Optimization

### Benchmarking

```bash
# Simple timing
python3 ./mcp_nrepl_client.py --eval "(time (reduce + (range 100000)))"

# Compare implementations
python3 ./mcp_nrepl_client.py --eval "(defn sum-for [n] (loop [i 0 acc 0] (if (< i n) (recur (inc i) (+ acc i)) acc)))"
python3 ./mcp_nrepl_client.py --eval "(defn sum-reduce [n] (reduce + (range n)))"

python3 ./mcp_nrepl_client.py --eval "(time (sum-for 100000))"
python3 ./mcp_nrepl_client.py --eval "(time (sum-reduce 100000))"
```

### Memory Analysis

```bash
# Check memory usage (if available)
python3 ./mcp_nrepl_client.py --eval "(System/gc)"
python3 ./mcp_nrepl_client.py --eval "(let [rt (Runtime/getRuntime)] {:total (.totalMemory rt) :free (.freeMemory rt) :used (- (.totalMemory rt) (.freeMemory rt))})"
```

## Development Best Practices

### 1. Session Management

```bash
# Create dedicated sessions for different contexts
python3 ./mcp_nrepl_client.py --tool nrepl-new-session

# Use sessions to isolate experiments
python3 ./mcp_nrepl_client.py --eval "(def experimental-data [1 2 3])" --session "session-id"
```

### 2. Code Organization

- **Start small** - Test individual functions first
- **Build incrementally** - Add complexity gradually  
- **Use REPL for exploration** - Try ideas interactively
- **Extract to files** - Move stable code to source files
- **Test continuously** - Verify each step

### 3. Error Recovery

```bash
# If evaluation hangs, interrupt it
python3 ./mcp_nrepl_client.py --tool nrepl-interrupt

# Check what went wrong
python3 ./mcp_nrepl_client.py --tool nrepl-stacktrace

# Reset if needed
python3 ./mcp_nrepl_client.py --eval "(in-ns 'user)"
```

### 4. Exploration Workflow

```bash
# 1. Discover - Find relevant functions
python3 ./mcp_nrepl_client.py --tool nrepl-apropos --args '{"query": "YOUR-CONCEPT"}'

# 2. Learn - Get documentation
python3 ./mcp_nrepl_client.py --tool nrepl-doc --args '{"symbol": "FUNCTION-NAME"}'

# 3. Experiment - Try it out
python3 ./mcp_nrepl_client.py --eval "(FUNCTION-NAME test-args)"

# 4. Build - Create your own functions
python3 ./mcp_nrepl_client.py --eval "(defn my-function [args] ...)"

# 5. Test - Verify it works
python3 ./mcp_nrepl_client.py --eval "(my-function test-cases)"
```

## Troubleshooting Common Issues

### Connection Problems

```bash
# Check server status
python3 ./mcp_nrepl_client.py --tool nrepl-status

# Reconnect if needed
python3 ./mcp_nrepl_client.py --tool nrepl-connect --args '{"port": 7888}'
```

### Symbol Not Found

```bash
# Check if namespace is loaded
python3 ./mcp_nrepl_client.py --eval "(find-ns 'namespace.name)"

# Require missing namespace
python3 ./mcp_nrepl_client.py --tool nrepl-require --args '{"namespace": "missing.namespace"}'
```

### Evaluation Errors

```bash
# Get detailed error info
python3 ./mcp_nrepl_client.py --tool nrepl-stacktrace

# Reset namespace if corrupted
python3 ./mcp_nrepl_client.py --eval "(in-ns 'user)"
python3 ./mcp_nrepl_client.py --eval "(clojure.core/refer 'clojure.core)"
```

## Advanced Integration Examples

### With External Tools

```bash
# Pretty printing results
python3 ./mcp_nrepl_client.py --eval "(require '[clojure.pprint :as pp])"
python3 ./mcp_nrepl_client.py --eval "(pp/pprint (range 20))"

# Data validation
python3 ./mcp_nrepl_client.py --tool nrepl-require --args '{"namespace": "clojure.spec.alpha", "as": "s"}'
```

### Multi-Step Development

```bash
# 1. Define specs
python3 ./mcp_nrepl_client.py --eval "(s/def ::name string?)"
python3 ./mcp_nrepl_client.py --eval "(s/def ::age pos-int?)"
python3 ./mcp_nrepl_client.py --eval "(s/def ::person (s/keys :req [::name ::age]))"

# 2. Test specs
python3 ./mcp_nrepl_client.py --eval "(s/valid? ::person {::name \"Alice\" ::age 30})"

# 3. Generate test data
python3 ./mcp_nrepl_client.py --eval "(s/exercise ::person 3)"
```

This cookbook provides a comprehensive foundation for exploring and developing Clojure/ClojureScript applications using our MCP-nREPL bridge, enabling truly interactive and productive development workflows!