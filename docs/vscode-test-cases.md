# VS Code Testing Use Cases

Ordered subset of simple use cases for testing the MCP-nREPL Joyride bridge with real VS Code environment.

## 🎯 Prerequisites

1. **VS Code Setup**:
   - Install [Joyride extension](https://marketplace.visualstudio.com/items?itemName=betterthantomorrow.joyride)
   - Start Joyride nREPL server in your workspace
   - Verify `.nrepl-port` file exists in workspace root

2. **MCP Server Setup**:
   - Configure Claude Code with MCP server
   - Ensure `bb mcp-server` runs without errors
   - Test MCP connection with Claude Code

## 📋 Test Cases (Ordered by Complexity)

### Phase 1: Basic Functionality ✅

#### 1. **Connection Test**
**Priority**: CRITICAL  
**Command**: Test nREPL connection status  
**Expected**: Connection successful, port discovered automatically
```clojure
;; Use MCP tool: nrepl-status
;; Should show: connected, port number, session info
```

#### 2. **Basic Evaluation** 
**Priority**: CRITICAL  
**Command**: `(+ 1 2 3)`  
**Expected**: Returns `6`
```clojure
;; Simple arithmetic to verify evaluation pipeline
(+ 1 2 3)
;; Expected result: 6
```

#### 3. **String Operations**
**Priority**: HIGH  
**Command**: `(str "Hello " "VS Code")`  
**Expected**: Returns `"Hello VS Code"`
```clojure
;; Test string concatenation
(str "Hello " "VS Code")
;; Expected result: "Hello VS Code"
```

### Phase 2: VS Code Integration 🎨

#### 4. **Quick Open Command**
**Priority**: HIGH  
**Command**: `(joyride.core/execute-command "workbench.action.quickOpen")`  
**Expected**: Quick Open file picker appears in VS Code
```clojure
;; Should open VS Code's Quick Open dialog
(joyride.core/execute-command "workbench.action.quickOpen")
```

#### 5. **Command Palette**
**Priority**: HIGH  
**Command**: `(joyride.core/execute-command "workbench.action.showCommands")`  
**Expected**: Command palette appears in VS Code
```clojure
;; Should open VS Code's Command Palette
(joyride.core/execute-command "workbench.action.showCommands")
```

#### 6. **VS Code Notification**
**Priority**: MEDIUM  
**Command**: `(vscode.window.showInformationMessage "Hello from Claude!")`  
**Expected**: Information notification appears in VS Code
```clojure
;; Should show notification in VS Code
(vscode.window.showInformationMessage "Hello from Claude!")
```

### Phase 3: Workspace Operations 📁

#### 7. **Get Workspace Root**
**Priority**: HIGH  
**Command**: `(joyride/workspace-root)`  
**Expected**: Returns absolute path to workspace root
```clojure
;; Should return workspace root path
(joyride/workspace-root)
;; Expected: "/path/to/your/workspace"
```

#### 8. **Get Active Editor Info**
**Priority**: HIGH  
**Command**: `(-> js/vscode.window.activeTextEditor .-document .-fileName)`  
**Expected**: Returns currently active file path
```clojure
;; Should return current file name/path
(-> js/vscode.window.activeTextEditor .-document .-fileName)
;; Expected: file path string
```

#### 9. **List Workspace Files**
**Priority**: MEDIUM  
**Command**: `(joyride/workspace-files "**/*.clj")`  
**Expected**: Returns array of Clojure files in workspace
```clojure
;; Should list all .clj files in workspace
(joyride/workspace-files "**/*.clj")
;; Expected: ["src/core.clj", "test/core_test.clj", ...]
```

### Phase 4: File Operations 📝

#### 10. **Open Specific File**
**Priority**: MEDIUM  
**Command**: `(joyride/execute-command "vscode.open" "README.md")`  
**Expected**: Opens README.md file in VS Code
```clojure
;; Should open README.md in editor
(joyride/execute-command "vscode.open" "README.md")
```

#### 11. **Navigate to Line**
**Priority**: LOW  
**Command**: `(joyride/execute-command "revealLine" {:lineNumber 10})`  
**Expected**: Cursor moves to line 10 in active editor
```clojure
;; Should jump to line 10 in current file
(joyride/execute-command "revealLine" {:lineNumber 10 :at "center"})
```

### Phase 5: Advanced Operations 🔧

#### 12. **Session Creation**
**Priority**: MEDIUM  
**Command**: Create new nREPL session  
**Expected**: New isolated session created
```clojure
;; Use MCP tool: nrepl-new-session
;; Should return new session ID
```

#### 13. **Session Isolation Test**
**Priority**: LOW  
**Command**: Test variable isolation between sessions  
**Expected**: Variables defined in one session don't affect others
```clojure
;; In session 1:
(def x 42)

;; In session 2:
x  ;; Should be unbound/error
```

## 🧪 Testing Protocol

### Setup Phase
1. **Start VS Code** with Joyride extension
2. **Open workspace** containing this project
3. **Start Joyride nREPL** (automatic or manual)
4. **Verify `.nrepl-port`** file exists
5. **Start Claude Code** with MCP configuration
6. **Test MCP connection** with simple tool call

### Execution Phase
1. **Run tests in order** (1-13)
2. **Document results** for each test case
3. **Note any failures** or unexpected behavior
4. **Test error handling** with invalid commands
5. **Verify cleanup** after each test

### Validation Criteria

#### ✅ Success Indicators
- Commands execute without throwing exceptions
- VS Code UI responds as expected (dialogs, notifications)
- File operations work correctly
- Workspace queries return valid data
- Sessions maintain isolation
- Connection remains stable throughout testing

#### ❌ Failure Indicators
- Connection timeouts or failures
- VS Code commands don't execute
- Incorrect return values
- VS Code UI doesn't respond
- Session state bleeding between contexts
- Memory leaks or resource issues

## 🔧 Troubleshooting Guide

### Common Issues

#### Connection Problems
- **Check**: `.nrepl-port` file exists and contains valid port
- **Check**: Joyride nREPL server is running
- **Fix**: Restart Joyride or VS Code workspace

#### Command Execution Failures
- **Check**: Command names are correct (case-sensitive)
- **Check**: Joyride extension is active
- **Fix**: Reload VS Code window

#### Workspace Operation Issues
- **Check**: Workspace is properly opened in VS Code
- **Check**: File paths are relative to workspace root
- **Fix**: Verify workspace folder structure

### Debug Commands
```bash
# Check MCP server status
bb mcp-server

# Test direct nREPL connection
bb -cp src test-nrepl-client.clj

# Enable debug logging
MCP_DEBUG=true bb mcp-server
```

## 📊 Expected Results Template

Use this template to document test results:

```markdown
### Test Case: [Name]
- **Command**: `[clojure-code]`
- **Status**: ✅ PASS / ❌ FAIL / ⚠️ PARTIAL
- **Result**: [actual-result]
- **VS Code Response**: [ui-behavior]
- **Notes**: [additional-observations]
- **Duration**: [execution-time]
```

## 🎯 Success Metrics

### Minimum Viable Testing
- **Phase 1**: All connection and basic evaluation tests pass
- **Phase 2**: At least 2/3 VS Code integration tests pass
- **Phase 3**: At least 2/3 workspace operation tests pass

### Full Success
- **All test cases**: Pass without errors
- **VS Code integration**: All UI responses work correctly
- **Performance**: All operations complete within 5 seconds
- **Stability**: No connection drops during testing session

This test suite validates the complete integration between Claude Code, the MCP-nREPL bridge, and VS Code through Joyride.