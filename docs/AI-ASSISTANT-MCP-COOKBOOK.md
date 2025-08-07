# AI Assistant MCP-nREPL Cookbook

## Overview

This cookbook is designed specifically for **AI assistants** (Claude, GPT, etc.) to effectively use the MCP-nREPL server for interactive Clojure/ClojureScript development and Python application introspection. Unlike human developers who use command-line clients, AI assistants interact directly with MCP functions through the protocol.

## Key Differences for AI Assistants

**❌ What AI Assistants DON'T Have:**
- No access to `python3 ./mcp_nrepl_client.py` command-line tool
- No direct terminal/shell access to the development environment
- No ability to run bash commands or access local filesystems directly

**✅ What AI Assistants DO Have:**
- Direct access to all 14 MCP function calls through the protocol
- Ability to call functions with JSON parameters
- Full access to nREPL evaluation and introspection capabilities
- Session management and error handling through MCP tools
- Comprehensive system diagnostics and health monitoring

## Available MCP Functions

### Core nREPL Functions
```javascript
// Connection Management
nrepl-connect({host: "localhost", port: 7888})
nrepl-status()
nrepl-test()

// Code Evaluation
nrepl-eval({code: "(+ 1 2 3)"})
nrepl-eval({code: "(defn greet [name] (str \"Hello, \" name))", session: "session-id"})

// File Operations
nrepl-load-file({file-path: "/path/to/file.clj"})
nrepl-load-file({file-path: "/path/to/utils.clj", session: "session-id", ns: "my.namespace"})
```

### Symbol Introspection Functions
```javascript
// Documentation and Source
nrepl-doc({symbol: "map"})
nrepl-source({symbol: "defn"})
nrepl-apropos({query: "str"})

// Code Completion
nrepl-complete({prefix: "ma"})
nrepl-complete({prefix: "clojure.string/", ns: "user"})
```

### Development Tools
```javascript
// Namespace Management
nrepl-require({namespace: "clojure.string", as: "str"})
nrepl-require({namespace: "my.project.core", reload: true})

// Session Management
nrepl-new-session()

// Error Handling
nrepl-stacktrace()
nrepl-interrupt()

// System Diagnostics
nrepl-health-check()
nrepl-health-check({include_performance: false, verbose: true})
```

## AI Assistant Development Workflows

### Phase 1: Environment Discovery

**Goal:** Understand the current nREPL environment and capabilities

```javascript
// 1. Run comprehensive health check first
nrepl-health-check()

// 2. Check connection status
nrepl-status()

// 3. Test basic functionality
nrepl-test()

// 4. Discover current namespace and environment
nrepl-eval({code: "*ns*"})
nrepl-eval({code: "(clojure-version)"})
nrepl-eval({code: "(keys (ns-publics *ns*))"})
```

### Phase 2: Symbol and Function Discovery

**Goal:** Find relevant functions and understand their usage

```javascript
// 1. Search for functions by pattern
nrepl-apropos({query: "map"})
nrepl-apropos({query: "reduce"})

// 2. Get documentation for discovered functions
nrepl-doc({symbol: "map"})
nrepl-doc({symbol: "reduce"})

// 3. View source code if needed
nrepl-source({symbol: "defn"})

// 4. Get completions for interactive development
nrepl-complete({prefix: "ma"})
nrepl-complete({prefix: "clojure.string/"})
```

### Phase 3: Interactive Development

**Goal:** Build and test code incrementally

```javascript
// 1. Load required namespaces
nrepl-require({namespace: "clojure.string", as: "str"})
nrepl-require({namespace: "clojure.set", as: "set"})

// 2. Define and test functions step by step
nrepl-eval({code: "(defn square [x] (* x x))"})
nrepl-eval({code: "(square 5)"}) // Test: should return 25

// 3. Build more complex functionality
nrepl-eval({code: "(defn sum-of-squares [coll] (reduce + (map square coll)))"})
nrepl-eval({code: "(sum-of-squares [1 2 3 4])"}) // Test: should return 30

// 4. Handle errors and debug
nrepl-eval({code: "(/ 1 0)"}) // This will cause an error
nrepl-stacktrace() // Get details about the error
```

### Phase 4: File-Based Development

**Goal:** Work with larger codebases and load external files

```javascript
// 1. Load utility files or libraries
nrepl-load-file({file-path: "/path/to/project/utils.clj"})

// 2. Load with specific namespace context
nrepl-load-file({
    file-path: "/path/to/project/core.clj", 
    ns: "my.project.core"
})

// 3. Reload files during development
nrepl-require({namespace: "my.project.core", reload: true})
```

## Clojure/ClojureScript Patterns for AI Assistants

### Data Exploration and Analysis

```javascript
// Generate sample data
nrepl-eval({
    code: `(def sample-data 
             (for [i (range 10)] 
               {:id i :value (* i i) :category (if (even? i) "even" "odd")}))`
})

// Explore the data structure
nrepl-eval({code: "(take 3 sample-data)"})
nrepl-eval({code: "(count sample-data)"})

// Analyze and transform
nrepl-eval({code: "(group-by :category sample-data)"})
nrepl-eval({code: "(->> sample-data (map :value) (reduce +))"})
```

### Function Development and Testing

```javascript
// Start with simple functions
nrepl-eval({code: "(defn factorial [n] (if (<= n 1) 1 (* n (factorial (dec n)))))"})

// Test with various inputs
nrepl-eval({code: "(factorial 5)"}) // Should be 120
nrepl-eval({code: "(map factorial [1 2 3 4 5])"})

// Improve with error handling
nrepl-eval({
    code: `(defn safe-factorial [n]
             {:pre [(and (integer? n) (>= n 0))]}
             (if (<= n 1) 1 (* n (safe-factorial (dec n)))))`
})

nrepl-eval({code: "(safe-factorial -1)"}) // Should show precondition error
nrepl-stacktrace() // View the error details
```

### Macro Development

```javascript
// Define a simple macro
nrepl-eval({
    code: `(defmacro unless [test & body]
             \`(if (not ~test) (do ~@body)))`
})

// Test macro expansion
nrepl-eval({code: "(macroexpand '(unless false (println \"Hello\")))"})

// Test macro execution
nrepl-eval({code: "(unless false (println \"This should print\"))"})
nrepl-eval({code: "(unless true (println \"This should NOT print\"))"})
```

## Python Introspection Patterns for AI Assistants

### Connecting to Python Applications

When working with Python applications that have embedded Basilisp + nREPL:

```javascript
// 1. Connect to the Python application's nREPL server
nrepl-connect({host: "localhost", port: 7888})

// 2. Test Python interop capabilities
nrepl-eval({code: "(python/sys.version-info)"})
nrepl-eval({code: "(str (python/os.getcwd))"})
```

### Loading Python Introspection Utilities

```javascript
// 1. Load pre-built Python introspection functions
nrepl-load-file({file-path: "examples/python_introspection_utils.clj"})

// 2. Verify utilities are loaded
nrepl-apropos({query: "py-"})

// 3. Get documentation for key functions
nrepl-doc({symbol: "py-health-check"})
nrepl-doc({symbol: "py-system-info"})
```

### Python Application Health Monitoring

```javascript
// 1. Comprehensive health check
nrepl-eval({code: "(py-health-check)"})

// 2. System information
nrepl-eval({code: "(py-system-info)"})

// 3. Memory usage analysis
nrepl-eval({code: "(py-memory-info)"})

// 4. Configuration overview
nrepl-eval({code: "(py-config-summary)"})
```

### Python Object Introspection

```javascript
// 1. Explore Python application objects
nrepl-eval({code: "(py-explore-object python/app)"})

// 2. Check database connections
nrepl-eval({code: "(py-db-status)"})

// 3. Performance monitoring
nrepl-eval({code: "(py-performance-snapshot)"})

// 4. Module analysis
nrepl-eval({code: "(py-list-app-modules \"myapp\")"})
nrepl-eval({code: "(py-module-info \"myapp.models\")"})
```

## Comprehensive System Health Monitoring

The `nrepl-health-check` tool provides detailed diagnostics across 6 categories:

### Running Health Checks

```javascript
// Basic health check
nrepl-health-check()

// Detailed health check with performance benchmarks
nrepl-health-check({include_performance: true, verbose: true})

// Quick check without performance testing
nrepl-health-check({include_performance: false})
```

### Understanding Health Check Results

The health check provides color-coded diagnostics:

- **🟢 Green**: All systems operational
- **🟡 Yellow**: Partial functionality (degraded but usable)
- **🔴 Red**: Critical issues requiring attention

**Six Diagnostic Categories:**

1. **🔧 Environment Diagnostics**: System info, memory, versions
2. **🔌 Connection Health**: nREPL server status, available operations
3. **⚙️ Core Functionality**: Basic evaluation capabilities
4. **🔗 Tool Integration**: Advanced nREPL operations
5. **⚡ Performance Benchmarks**: Responsiveness and throughput
6. **🛠️ Configuration Status**: Server settings and parameters

### Interpreting Health Check Output

```javascript
// Example health check interpretation:
// ✅ Connection Health: nREPL server responding normally
// ⚠️ Tool Integration: Some operations not supported (normal for Joyride)
// ❌ Environment: Math operations limited (platform-specific)
```

### Using Health Check for Troubleshooting

```javascript
// If experiencing issues, run diagnostics first
nrepl-health-check({verbose: true})

// Check specific categories:
// - Connection issues → Look at Connection Health section
// - Performance problems → Review Performance Benchmarks
// - Missing operations → Check Tool Integration status
// - Environment errors → Examine Environment Diagnostics
```

### Health Check in Development Workflows

**Start of session:**
```javascript
// Always begin with health check to understand environment
nrepl-health-check()
```

**During development:**
```javascript
// If operations start failing unexpectedly
nrepl-health-check({include_performance: false}) // Quick check
```

**Performance monitoring:**
```javascript
// For performance-sensitive applications
nrepl-health-check({include_performance: true, verbose: true})
```

## Best Practices for AI Assistants

### 1. Progressive Exploration

Always start with basic checks before diving deep:

```javascript
// Good approach: Progressive discovery
nrepl-status() // Check connection first
nrepl-eval({code: "*ns*"}) // Understand current context
nrepl-apropos({query: "map"}) // Discover available functions
nrepl-doc({symbol: "map"}) // Understand function usage
nrepl-eval({code: "(map inc [1 2 3])"}) // Test with simple example
```

### 2. Error Recovery Patterns

When things go wrong, follow this pattern:

```javascript
// If evaluation fails or hangs
nrepl-interrupt() // Stop current evaluation

// Get error details
nrepl-stacktrace()

// Check connection status
nrepl-status()

// If needed, start fresh
nrepl-eval({code: "(in-ns 'user)"}) // Reset namespace
```

### 3. Session Management

Use sessions for complex workflows:

```javascript
// Create dedicated sessions for different tasks
nrepl-new-session() // Returns session ID

// Use session for related operations
nrepl-eval({code: "(def project-data [1 2 3])", session: "session-123"})
nrepl-eval({code: "(count project-data)", session: "session-123"})
```

### 4. Incremental Development

Build complexity gradually:

```javascript
// Start simple
nrepl-eval({code: "(defn add [a b] (+ a b))"})
nrepl-eval({code: "(add 2 3)"}) // Test: 5

// Add complexity
nrepl-eval({code: "(defn add-all [& nums] (reduce + nums))"})
nrepl-eval({code: "(add-all 1 2 3 4 5)"}) // Test: 15

// Add validation
nrepl-eval({
    code: `(defn safe-add [& nums]
             {:pre [(every? number? nums)]}
             (reduce + nums))`
})
```

## Common AI Assistant Use Cases

### 1. Code Analysis and Understanding

```javascript
// Analyze existing code structure
nrepl-eval({code: "(keys (ns-publics *ns*))"}) // See what's available
nrepl-apropos({query: "user"}) // Find user-defined functions
nrepl-source({symbol: "user-function"}) // Understand implementation
```

### 2. Interactive Problem Solving

```javascript
// Break down problems step by step
nrepl-eval({code: "(def problem-data [3 1 4 1 5 9 2 6])"})
nrepl-eval({code: "(sort problem-data)"}) // Step 1: sort
nrepl-eval({code: "(distinct problem-data)"}) // Step 2: unique values
nrepl-eval({code: "(->> problem-data distinct sort)"}) // Step 3: combine
```

### 3. Testing and Validation

```javascript
// Create test cases
nrepl-eval({code: "(defn is-palindrome? [s] (= s (str/reverse s)))"})

// Test with various inputs
nrepl-eval({code: "(is-palindrome? \"radar\")"}) // true
nrepl-eval({code: "(is-palindrome? \"hello\")"}) // false
nrepl-eval({code: "(map is-palindrome? [\"level\" \"world\" \"noon\"])"})
```

### 4. Data Processing Workflows

```javascript
// Load and transform data
nrepl-require({namespace: "clojure.string", as: "str"})
nrepl-require({namespace: "clojure.set", as: "set"})

nrepl-eval({
    code: `(def raw-data 
             ["Alice,30,Engineer" "Bob,25,Designer" "Carol,35,Manager"])`
})

nrepl-eval({
    code: `(defn parse-csv-line [line]
             (let [[name age role] (str/split line #",")]
               {:name name :age (Integer/parseInt age) :role role}))`
})

nrepl-eval({code: "(map parse-csv-line raw-data)"})
```

## Error Handling for AI Assistants

### Common Error Scenarios

**1. Symbol Not Found:**
```javascript
nrepl-eval({code: "(unknown-function 123)"})
// Response will show "Unable to resolve symbol: unknown-function"

// Solution: Search for similar functions
nrepl-apropos({query: "unknown"})
nrepl-complete({prefix: "unk"})
```

**2. Namespace Issues:**
```javascript
nrepl-eval({code: "(clojure.string/upper-case \"hello\")"})
// May fail if namespace not loaded

// Solution: Require namespace first
nrepl-require({namespace: "clojure.string", as: "str"})
nrepl-eval({code: "(str/upper-case \"hello\")"})
```

**3. Evaluation Timeout:**
```javascript
nrepl-eval({code: "(loop [] (recur))"}) // Infinite loop
// This may hang

// Solution: Interrupt and recover
nrepl-interrupt()
nrepl-stacktrace()
```

## Integration Patterns

### 1. Mixed Language Development

For applications using both Clojure and Python:

```javascript
// 1. Start with Clojure side
nrepl-eval({code: "(defn process-data [data] (map inc data))"})

// 2. Connect to Python side (if available via Basilisp)
nrepl-eval({code: "(python/sys.version)"})
nrepl-eval({code: "(py-system-info)"})

// 3. Bridge between languages
nrepl-eval({code: "(process-data (python/range 5))"})
```

### 2. Live System Monitoring

```javascript
// Set up monitoring functions
nrepl-eval({
    code: `(defn monitor-system []
             {:memory (py-memory-info)
              :connections (py-db-status)
              :performance (py-performance-snapshot)})`
})

// Periodic monitoring
nrepl-eval({code: "(monitor-system)"})
```

## VS Code Automation for AI Assistants

### Connecting to VS Code via Joyride

When connected to VS Code's Joyride nREPL server (typically port 7889), AI assistants can control and automate the editor:

```javascript
// First, ensure connection to Joyride
nrepl-connect({host: "localhost", port: 7889})

// Verify VS Code API availability
nrepl-eval({code: "(require '[\"vscode\" :as vscode])"})
nrepl-eval({code: "vscode/version"})
```

### Basic VS Code Control

**Window and Notifications:**
```javascript
// Show messages to user
nrepl-eval({code: "(vscode/window.showInformationMessage \"Task completed!\")"})
nrepl-eval({code: "(vscode/window.showWarningMessage \"Check this issue\")"})
nrepl-eval({code: "(vscode/window.showErrorMessage \"Error detected\")"})

// Get user input
nrepl-eval({code: "(p/let [input (vscode/window.showInputBox #js {:prompt \"Enter value\"})] input)"})
```

**Document Manipulation:**
```javascript
// Get current document info
nrepl-eval({code: "(when-let [editor vscode/window.activeTextEditor] (.-fileName (.-document editor)))"})

// Insert text at cursor
nrepl-eval({code: "(when-let [editor vscode/window.activeTextEditor] (.edit editor (fn [builder] (.insert builder (.-active (.-selection editor)) \"// AI generated code\"))))"})

// Replace selected text
nrepl-eval({code: "(when-let [editor vscode/window.activeTextEditor] (.edit editor (fn [builder] (.replace builder (.-selection editor) \"new text\"))))"})
```

### File and Workspace Operations

```javascript
// Open a file
nrepl-eval({code: "(p/let [doc (vscode/workspace.openTextDocument \"/path/to/file.clj\")] (vscode/window.showTextDocument doc))"})

// Save current file
nrepl-eval({code: "(when-let [doc (.-document vscode/window.activeTextEditor)] (.save doc))"})

// Find files in workspace
nrepl-eval({code: "(p/let [files (vscode/workspace.findFiles \"**/*.clj\")] (take 10 (map #(.-path %) files)))"})
```

### Command Execution

```javascript
// Execute VS Code commands
nrepl-eval({code: "(vscode/commands.executeCommand \"workbench.action.terminal.toggleTerminal\")"})
nrepl-eval({code: "(vscode/commands.executeCommand \"editor.action.formatDocument\")"})

// Calva integration
nrepl-eval({code: "(vscode/commands.executeCommand \"calva.loadFile\")"})
nrepl-eval({code: "(vscode/commands.executeCommand \"calva.evaluateCurrentForm\")"})
```

### Terminal Control

```javascript
// Create and control terminals
nrepl-eval({code: "(def term (vscode/window.createTerminal \"AI Terminal\")) (.show term) (.sendText term \"echo 'Hello from AI'\")"})

// Send commands to active terminal
nrepl-eval({code: "(when-let [term vscode/window.activeTerminal] (.sendText term \"ls -la\"))"})
```

### Status Bar Integration

```javascript
// Create status bar item
nrepl-eval({code: "(def status (vscode/window.createStatusBarItem vscode/StatusBarAlignment.Left 100)) (set! (.-text status) \"🤖 AI Active\") (.show status)"})

// Update status
nrepl-eval({code: "(set! (.-text status) \"✅ Task Complete\")"})
```

### Advanced Automation Patterns

**Code Analysis:**
```javascript
// Analyze current file
nrepl-eval({code: `
(when-let [editor vscode/window.activeTextEditor]
  (let [doc (.-document editor)
        text (.getText doc)]
    {:line-count (.-lineCount doc)
     :language (.-languageId doc)
     :functions (count (re-seq #"defn\\s+" text))}))
`})
```

**Automated Refactoring:**
```javascript
// Find and replace pattern
nrepl-eval({code: `
(when-let [editor vscode/window.activeTextEditor]
  (.edit editor 
    (fn [builder]
      (let [doc (.-document editor)
            text (.getText doc)
            new-text (clojure.string/replace text #"oldPattern" "newPattern")
            full-range (vscode/Range. 0 0 (.-lineCount doc) 0)]
        (.replace builder full-range new-text)))))
`})
```

**Custom Command Registration:**
```javascript
// Register AI-specific commands
nrepl-eval({code: `
(vscode/commands.registerCommand 
  "ai.analyzeCode"
  (fn []
    (vscode/window.showInformationMessage "AI analysis starting...")))
`})
```

### Working with Joyride Scripts

```javascript
// Load Joyride utility scripts
nrepl-load-file({file-path: ".joyride/scripts/ai-utils.cljs"})

// Execute Joyride workspace scripts
nrepl-eval({code: "(vscode/commands.executeCommand \"joyride.runWorkspaceScript\" \"my-script\")"})
```

### VS Code Specific Error Handling

```javascript
// Safe VS Code operations
nrepl-eval({code: `
(try
  (require '["vscode" :as vscode])
  (vscode/window.showInformationMessage "VS Code connected")
  (catch js/Error e
    {:error (.-message e)
     :suggestion "Ensure connected to Joyride nREPL on port 7889"}))
`})
```

### Best Practices for AI VS Code Automation

1. **Always check for active editor** before manipulating documents
2. **Use promises (p/let)** for async VS Code operations
3. **Validate file paths** are within workspace boundaries
4. **Handle errors gracefully** with try-catch blocks
5. **Provide user feedback** via status bar or notifications
6. **Batch operations** when possible to improve performance

## Troubleshooting Guide for AI Assistants

### Connection Issues

```javascript
// Check if server is responding
nrepl-status()

// If connection fails, verify with basic test
nrepl-test()

// Reset if needed
nrepl-connect({host: "localhost", port: 7889}) // Try different port
```

### Function Not Working

```javascript
// Debug step by step
nrepl-apropos({query: "function-name"}) // Is function available?
nrepl-doc({symbol: "function-name"}) // Check documentation
nrepl-source({symbol: "function-name"}) // View implementation
```

### Evaluation Errors

```javascript
// Get detailed error information
nrepl-stacktrace()

// Try simpler version
nrepl-eval({code: "(+ 1 2)"}) // Test basic functionality

// Check namespace context
nrepl-eval({code: "*ns*"})
```

### VS Code Specific Issues

```javascript
// Verify VS Code API is available
nrepl-eval({code: "(exists? js/vscode)"})

// Check Joyride is active
nrepl-eval({code: "(some? (vscode/extensions.getExtension \"betterthantomorrow.joyride\"))"})

// Reload window if needed
nrepl-eval({code: "(vscode/commands.executeCommand \"workbench.action.reloadWindow\")"})
```

This cookbook enables AI assistants to effectively leverage the full power of MCP-nREPL for interactive development and introspection without relying on external command-line tools!