# Claude Code Implementation Guide: AI-MCP-nREPL-VS Code Integration

*Reconstructed from memory - Original implementation guidance document*

## Introduction

This document provides comprehensive guidance for integrating Claude Code with VS Code through the MCP-nREPL Joyride bridge. It covers the technical architecture, implementation patterns, and best practices for enabling AI-assisted development in VS Code environments.

## Architecture Overview

### Component Interaction Flow
```
┌─────────────┐    JSON-RPC 2.0    ┌──────────────────┐    nREPL     ┌─────────────┐    JS Interop    ┌─────────────┐
│ Claude Code │ ◄──────────────── │ MCP-nREPL Proxy │ ◄─────────── │   Joyride   │ ◄──────────────► │   VS Code   │
│    (AI)     │                   │   (Babashka)     │              │   nREPL     │                  │    APIs     │
└─────────────┘                   └──────────────────┘              └─────────────┘                  └─────────────┘
```

### Key Integration Points

1. **Claude Code → MCP Proxy**: JSON-RPC 2.0 tool calls
2. **MCP Proxy → Joyride**: nREPL protocol (bencode over TCP)
3. **Joyride → VS Code**: JavaScript interop via `js/vscode` namespace
4. **VS Code → User**: Direct UI manipulation and file operations

## MCP Protocol Implementation

### Tool Definition Schema

```typescript
interface MCPTool {
  name: string;
  description: string;
  inputSchema: JSONSchema;
}
```

### Core Tools Implemented

#### 1. `nrepl-connect`
```json
{
  "name": "nrepl-connect",
  "description": "Connect to Joyride nREPL server",
  "inputSchema": {
    "type": "object",
    "properties": {
      "host": {"type": "string", "description": "nREPL host (default: localhost)"},
      "port": {"type": "number", "description": "nREPL port (auto-discovered if not provided)"}
    }
  }
}
```

#### 2. `nrepl-eval`
```json
{
  "name": "nrepl-eval", 
  "description": "Evaluate Clojure code in nREPL session",
  "inputSchema": {
    "type": "object",
    "properties": {
      "code": {"type": "string", "description": "Clojure code to evaluate"},
      "session": {"type": "string", "description": "Session ID (optional)"},
      "ns": {"type": "string", "description": "Namespace context (optional)"}
    },
    "required": ["code"]
  }
}
```

## nREPL Protocol Integration

### Custom Client Implementation

Due to Babashka's limited Java interop, a custom nREPL client was implemented:

```clojure
(defn connect [host port]
  (let [socket (Socket. host port)
        out (PrintWriter. (.getOutputStream socket) true)
        in (BufferedReader. (InputStreamReader. (.getInputStream socket)))]
    {:socket socket :out out :in in :host host :port port}))

(defn send-message [conn message]
  (let [encoded (pr-str message)]
    (.println (:out conn) encoded)
    (.flush (:out conn))))
```

### Session Management Strategy

#### Stateful nREPL vs Stateless MCP Bridge
```clojure
(def server-state 
  (atom {:connections {}
         :sessions {}
         :default-session "main-session"
         :recent-commands []}))

(defn get-or-create-session [session-id]
  (if-let [session (get-in @server-state [:sessions session-id])]
    session
    (create-new-session! session-id)))
```

## Joyride Integration Patterns

### VS Code API Access Patterns

#### Command Execution
```clojure
;; Direct VS Code command execution
(joyride.core/execute-command "workbench.action.quickOpen")

;; Command with parameters
(joyride.core/execute-command "vscode.open" 
  {:resource (str "file://" workspace-root "/src/core.clj")})
```

#### Workspace Operations
```clojure
;; Get workspace root
(-> js/vscode.workspace.workspaceFolders
    (aget 0)
    (.-uri)
    (.-fsPath))

;; Get active editor
(-> js/vscode.window.activeTextEditor
    (.-document)
    (.-fileName))
```

#### File System Operations
```clojure
;; List files with glob pattern
(joyride/workspace-files "**/*.clj")

;; Open document
(-> (js/vscode.workspace.openTextDocument "README.md")
    (.then #(js/vscode.window.showTextDocument %)))
```

### Calva Middleware Integration

#### Symbol Information
```clojure
;; nREPL info operation
{:op "info" 
 :symbol "println" 
 :ns "user"}

;; Expected response
{:status ["done"]
 :info {:doc "Prints objects to *out*"
        :arglists [["& more"]]
        :file "clojure/core.clj"
        :line 3737}}
```

#### Code Completion
```clojure
;; nREPL complete operation  
{:op "complete"
 :prefix "pr"
 :ns "user"}

;; Expected response
{:status ["done"]
 :completions [{:candidate "print" :type "function"}
               {:candidate "println" :type "function"}
               {:candidate "prn" :type "function"}]}
```

## Error Handling Strategies

### Protocol Error Mapping

#### nREPL Errors → MCP Responses
```clojure
(defn handle-nrepl-error [nrepl-response]
  (case (:status nrepl-response)
    ["done" "error"] 
    {:error {:code -1 
             :message (or (:ex nrepl-response) "Evaluation error")
             :data {:session (:session nrepl-response)
                    :ns (:ns nrepl-response)}}}
    
    ["done" "interrupted"]
    {:error {:code -2
             :message "Evaluation interrupted"}}))
```

#### Connection Error Handling
```clojure
(defn safe-eval [conn code]
  (try
    (nrepl/eval-code conn code)
    (catch java.net.ConnectException e
      {:error "nREPL server not available. Start Joyride nREPL in VS Code."})
    (catch java.net.SocketTimeoutException e
      {:error "nREPL evaluation timeout. Check for infinite loops."})
    (catch Exception e
      {:error (str "Unexpected error: " (.getMessage e))})))
```

## Implementation Best Practices

### 1. Resource Management
```clojure
;; Always use try/finally for cleanup
(defn with-connection [host port f]
  (let [conn (connect host port)]
    (try
      (f conn)
      (finally
        (close-connection conn)))))
```

### 2. Auto-Discovery Implementation
```clojure
(defn discover-nrepl-port 
  "Discover nREPL port from .nrepl-port file"
  [workspace-dir]
  (let [port-file (str workspace-dir "/.nrepl-port")]
    (when (fs/exists? port-file)
      (-> (slurp port-file)
          (str/trim)
          (Integer/parseInt)))))
```

### 3. Logging and Debugging
```clojure
(defn log [level & args]
  (when (= "true" (System/getenv "MCP_DEBUG"))
    (binding [*out* *err*]
      (println (str "[" level "] " (apply str args))))))
```

## Testing and Validation

### Mock Server Implementation
```clojure
(defn create-joyride-mock-server []
  {:workspace {:root "/path/to/workspace"
               :files ["src/core.clj" "test/core_test.clj"]}
   :vscode {:active-editor {:file "src/core.clj" :line 42}}
   :sessions {}})
```

### Integration Test Patterns
```clojure
(defn integration-test []
  (let [server-proc (start-mock-server!)
        port (wait-for-port-file 5000)]
    (try
      (test-basic-evaluation port)
      (test-vscode-integration port)
      (test-session-management port)
      (finally
        (stop-server! server-proc)))))
```

## Performance Optimization

### Connection Pooling Strategy
```clojure
(def connection-pool
  (atom {:connections {}
         :last-used {}}))

(defn get-pooled-connection [host port]
  (let [key [host port]]
    (or (get-in @connection-pool [:connections key])
        (create-and-cache-connection! key))))
```

### Caching Strategies
```clojure
(def response-cache
  (atom {:describe-cache {}
         :info-cache {}
         :ttl 300000})) ; 5 minutes

(defn cached-describe [conn]
  (let [cache-key [:describe (:host conn) (:port conn)]]
    (or (get-cached-response cache-key)
        (cache-response! cache-key (nrepl/describe-server conn)))))
```

## Deployment Configuration

### Claude Code MCP Configuration
```json
{
  "mcpServers": {
    "joyride-nrepl": {
      "command": "bb",
      "args": ["-f", "/path/to/mcp-nrepl-joyride/bb.edn", "mcp-server"],
      "env": {
        "JOYRIDE_WORKSPACE": "${workspaceFolder}",
        "MCP_DEBUG": "false"
      }
    }
  }
}
```

### Environment Variables
- `MCP_DEBUG`: Enable verbose logging
- `JOYRIDE_WORKSPACE`: Override workspace detection
- `NREPL_TIMEOUT`: Connection timeout in milliseconds

## Security Considerations

### Code Execution Safety
- All code execution happens within Joyride's sandboxed environment
- VS Code API access limited to Joyride's permissions
- No direct file system access outside workspace

### Connection Security
- Local-only connections (localhost)
- No authentication required (local development)
- Session isolation prevents cross-contamination

## Troubleshooting Guide

### Common Issues

#### 1. Connection Failures
```bash
# Check if Joyride nREPL is running
ls -la .nrepl-port

# Test direct connection
bb -e "(require '[clojure.java.io :as io]) (println (slurp \".nrepl-port\"))"
```

#### 2. Evaluation Errors
```clojure
;; Check namespace context
{:op "eval" :code "*ns*"}

;; Verify available functions
{:op "eval" :code "(keys (ns-publics *ns*))"}
```

#### 3. VS Code Integration Issues
```clojure
;; Test VS Code API availability
{:op "eval" :code "(type js/vscode)"}

;; Check workspace context
{:op "eval" :code "js/vscode.workspace.workspaceFolders"}
```

## Future Enhancements

### Planned Features
1. **Multi-workspace support**: Handle multiple VS Code workspaces
2. **Custom command registration**: Allow user-defined commands
3. **Plugin architecture**: Extensible command system
4. **Enhanced error reporting**: Detailed stack traces and context

### Performance Improvements
1. **Persistent connections**: Keep connections alive between calls
2. **Batch operations**: Multiple evaluations in single request
3. **Streaming responses**: Large result streaming
4. **Background processing**: Async operation support

This implementation guide provided the technical foundation for successfully integrating Claude Code with VS Code through the MCP-nREPL Joyride bridge, enabling seamless AI-assisted development workflows.