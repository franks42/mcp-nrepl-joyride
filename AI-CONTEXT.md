# MCP-nREPL Server Context for AI Assistants

## 🎯 What This MCP Server Does

The **MCP-nREPL server** bridges AI assistants with **Clojure/ClojureScript development environments** through the nREPL protocol. It enables you to:

- **Execute Clojure code** in live development environments
- **Control VS Code** through Joyride's nREPL server
- **Explore codebases** using Clojure's powerful introspection tools
- **Build interactive applications** by evaluating code step-by-step
- **Debug and analyze** existing Clojure projects

## 🏗️ Architecture Overview

```
You (AI) → MCP Functions → nREPL Client → Joyride nREPL → VS Code APIs
                                     ↓
                                Clojure/ClojureScript Runtime
```

**Key Components:**
- **MCP Functions (15 total)** - Your interface to the nREPL world
- **nREPL Protocol** - Standard Clojure REPL over network protocol
- **Joyride Integration** - VS Code automation through Clojure
- **Session Management** - Isolated evaluation contexts

## 🚀 Essential Workflow for AI Assistants

### Phase 1: Always Start Here (MANDATORY)
```javascript
// 1. Run health check to understand environment
// TIP: If no nREPL server connected, start built-in server first:
// babashka-nrepl({op: "start"})
nrepl-health-check()

// 2. Check what's available in current namespace
nrepl-eval({code: "*ns*"})
nrepl-eval({code: "(keys (ns-publics *ns*))"})
```

### Phase 2: Explore Before Acting
```javascript
// 3. Discover relevant functions
nrepl-apropos({query: "keyword-related-to-task"})

// 4. Get documentation for unfamiliar functions
nrepl-doc({symbol: "function-name"})

// 5. Check if you need additional namespaces
nrepl-require({namespace: "clojure.string", as: "str"})
```

### Phase 3: Execute Your Task
```javascript
// 6. Test small pieces first
nrepl-eval({code: "(simple-test-expression)"})

// 7. Build complexity gradually
nrepl-eval({code: "(more-complex-function-call)"})

// 8. Handle errors with stack traces
nrepl-stacktrace() // If something goes wrong
```

## 🔧 The 15 MCP Functions You Have

### Essential Functions (Use These First)
1. **`nrepl-health-check`** - ALWAYS call this first to understand environment
2. **`nrepl-eval`** - Execute any Clojure code (your primary tool)
3. **`nrepl-status`** - Check connection and session information

### Discovery & Documentation
4. **`nrepl-doc`** - Get function documentation
5. **`nrepl-source`** - View function source code
6. **`nrepl-apropos`** - Find functions by name pattern
7. **`nrepl-complete`** - Get auto-completions for partial names

### Environment Management
8. **`nrepl-connect`** - Connect to specific nREPL servers
9. **`nrepl-new-session`** - Create isolated evaluation contexts
10. **`nrepl-require`** - Load additional namespaces/libraries

### File Operations
11. **`nrepl-load-file`** - Load and evaluate Clojure files

### Development Tools
12. **`nrepl-test`** - Run basic functionality tests
13. **`nrepl-interrupt`** - Stop long-running evaluations
14. **`nrepl-stacktrace`** - Get detailed error information

### Context Helper
15. **`get-mcp-nrepl-context`** - Get this context document

## 🎨 Common AI Assistant Use Cases

### VS Code Automation
```javascript
// Show information message in VS Code
nrepl-eval({code: "(vscode/window.showInformationMessage \"Hello from AI!\")"})

// Open files in VS Code
nrepl-eval({code: "(vscode/workspace.openTextDocument \"path/to/file.clj\")"})

// Execute VS Code commands
nrepl-eval({code: "(vscode/commands.executeCommand \"workbench.action.quickOpen\")"})
```

### Code Analysis
```javascript
// Analyze function definitions
nrepl-eval({code: "(defn analyze-fn [f] (-> f meta (select-keys [:name :arglists :doc])))"})

// Find all public functions in namespace
nrepl-eval({code: "(->> (ns-publics *ns*) keys (map str) sort)"})

// Search for specific patterns
nrepl-apropos({query: "map"}) // Find all functions with 'map' in name
```

### Interactive Problem Solving
```javascript
// Break problems into steps
nrepl-eval({code: "(def data [1 2 3 4 5])"})
nrepl-eval({code: "(map inc data)"}) // Step 1
nrepl-eval({code: "(filter even? (map inc data))"}) // Step 2
```

### Error Handling and Debugging
```javascript
// When errors occur, get details
nrepl-stacktrace()

// Test with simple cases first
nrepl-eval({code: "(+ 1 2)"}) // Verify basic functionality
nrepl-eval({code: "(str \"hello \" \"world\")"}) // Test string operations
```

## ⚠️ Important Guidelines for AI Assistants

### DO:
- **Always call `nrepl-health-check()` first** - This gives you environment context
- **Start with simple expressions** before building complex solutions
- **Use `nrepl-doc` and `nrepl-apropos`** to discover available functions
- **Test incrementally** - evaluate small pieces and build up
- **Handle errors gracefully** with `nrepl-stacktrace()`

### DON'T:
- Don't assume specific libraries are available - check with `nrepl-apropos`
- Don't write long complex expressions without testing parts first
- Don't ignore errors - always investigate with `nrepl-stacktrace()`
- Don't forget to load required namespaces with `nrepl-require`

## 🔍 Example: Complete AI Workflow

```javascript
// 1. Start with health check
nrepl-health-check()

// 2. Understand current environment
nrepl-eval({code: "*ns*"})
nrepl-eval({code: "(clojure-version)"})

// 3. Task: Create a function to process a list of numbers
// First, check what's available
nrepl-apropos({query: "map"})
nrepl-doc({symbol: "map"})

// 4. Test the approach
nrepl-eval({code: "(map inc [1 2 3])"}) // Simple test

// 5. Build the solution
nrepl-eval({code: "(defn process-numbers [nums] (map #(* % 2) nums))"})

// 6. Test the solution
nrepl-eval({code: "(process-numbers [1 2 3 4 5])"})

// 7. If errors occur, investigate
// nrepl-stacktrace() // Only if there were errors
```

## 🌟 Success Patterns

### For Clojure Development:
- Use `nrepl-eval` to define functions incrementally
- Leverage `nrepl-doc` and `nrepl-source` to understand existing code
- Use `nrepl-apropos` to discover relevant functions

### For VS Code Automation:
- Start with simple `vscode` namespace calls
- Build complex workflows step by step
- Test each VS Code command individually

### For Code Analysis:
- Use Clojure's reflection and metadata capabilities
- Leverage `nrepl-apropos` for symbol discovery
- Combine multiple small queries rather than complex ones

## 📚 What Makes This Powerful

This MCP server gives you **live access to Clojure runtime environments**, meaning you can:

- **Modify running applications** in real-time
- **Explore large codebases** interactively
- **Build solutions incrementally** with immediate feedback
- **Control development tools** (like VS Code) programmatically
- **Debug issues** with full stack trace access

## 🎯 Remember: Start with `nrepl-health-check()`!

Every interaction should begin with understanding your environment. This single function call will tell you:
- What nREPL server you're connected to
- What namespaces are available
- Whether Joyride/VS Code integration is working
- Current session and connection status

**This context document is your roadmap - refer back to it when planning complex tasks!**