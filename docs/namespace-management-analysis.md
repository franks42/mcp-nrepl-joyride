# Namespace Management Analysis

**Date**: August 2025  
**Context**: MCP-nREPL server namespace synchronization investigation

## Problem Statement

The MCP-nREPL server provides multiple evaluation tools (`local-eval`, `local-load-file`, `nrepl-eval`) that needed to share state but were operating in different namespaces, requiring fully qualified names (FQN) for variable access.

## Investigation Results

### Initial Issue
- **`local-eval`**: Started in `nrepl-mcp-server.core` namespace
- **`nrepl-eval`**: Operated in `user` namespace  
- **Variable sharing**: Required FQN like `user/jaja` to access across contexts

### Root Cause Analysis
The issue was **timing of namespace initialization**. Several attempts were made:

1. **`(in-ns 'user)` in `core.clj`** - ❌ Too early, executed before tool loading
2. **`(in-ns 'user)` in `register_tools.clj`** - ❌ Wrong scope, only affected that file
3. **`(in-ns 'user)` in `server.clj` after watchers start** - ✅ **WORKED**

### Final Solution
Added namespace change in `src/nrepl_mcp_server/mcp_server/server.clj`:

```clojure
(defn stdio-server-loop
  "Main stdio server loop"
  []
  ;; Start watchers before entering the loop
  (watchers/start-all-watchers!)

  ;; Switch to user namespace for local-eval/local-load-file
  (in-ns 'user)

  ;; Set up shutdown hook to stop watchers
  ;; ... rest of server loop
```

**Timing**: After all initialization but before request handling begins.

## Namespace Synchronization Behavior

### Current State (Post-Fix)

| Tool | Namespace Behavior | Variable Access |
|------|-------------------|----------------|
| `local-eval` | ✅ Starts in `user`, changes persist | Direct access to same-namespace vars |
| `local-load-file` | ✅ Shares namespace with `local-eval` | Direct access to same-namespace vars |
| `nrepl-eval` | ❌ Independent namespace (`user` by default) | Can access all vars via FQN |

### Test Results

**Shared State**: ✅ **CONFIRMED**
- Variables defined in any tool are accessible from others
- Same babashka process, same SCI runtime
- Shared memory space

**Namespace Sync**: ⚠️ **PARTIAL**
- `local-eval` ↔ `local-load-file`: Full namespace synchronization
- `nrepl-eval`: Independent namespace state, but shared variable access via FQN

### Example Behavior

```clojure
;; 1. Change namespace in local-eval
(in-ns 'my.project.core)

;; 2. Define variable in local-eval  
(def test-var 42)

;; 3. local-load-file inherits the namespace
;; File executes in my.project.core, creates #'my.project.core/file-var

;; 4. nrepl-eval stays in user namespace
*ns* ;; => #object[sci.lang.Namespace "user"]

;; 5. But can access variables via FQN
my.project.core/test-var ;; => 42
```

## Architectural Implications

### Why This Happens
- **`local-eval` and `local-load-file`**: Use MCP server's SCI context directly
- **`nrepl-eval`**: Uses babashka nREPL server's evaluation context
- **Shared variables**: Same babashka process, same memory
- **Separate namespace state**: Different evaluation layers

### Trade-offs

**Benefits**:
- ✅ Shared state for debugging and introspection
- ✅ `local-eval`/`local-load-file` work seamlessly together
- ✅ nREPL maintains session independence

**Risks**:
- ⚠️ Implicit namespace dependencies in scripts
- ⚠️ Execution order affects script behavior
- ⚠️ Harder to debug namespace-related issues

## Best Practices Recommendation

### Script Writing Guidelines

**❌ Avoid implicit namespace dependencies:**
```clojure
;; Bad - assumes current namespace
(def my-var 42)
```

**✅ Use explicit namespace management:**
```clojure
;; Good - declare namespace at top
(ns my.project.core)
(def my-var 42)

;; Also good - explicit switch when needed
(in-ns 'my.project.core)
(def my-var 42)
```

### Development Workflow

1. **Use `local-eval` for quick debugging** in any namespace
2. **Write self-contained scripts** for `local-load-file` 
3. **Use `nrepl-eval` for standard REPL workflow**
4. **Access cross-namespace vars with FQN** when needed

## Technical Details

### SCI Context Architecture
- **Single SCI runtime**: All tools share the same babashka SCI context
- **Namespace isolation**: Different evaluation entry points maintain separate `*ns*` bindings
- **Variable sharing**: Same symbol table, accessible via fully qualified names

### Connection to nREPL
- **Babashka nREPL server**: Started via `(babashka.nrepl.server/start-server!)`
- **Same process**: nREPL server runs inside the MCP server's babashka instance
- **Independent session**: nREPL maintains its own namespace state per session

## Conclusion

The namespace synchronization provides powerful debugging capabilities while maintaining reasonable separation of concerns. The key insight is that **scripts should be written to be namespace-independent** rather than relying on ambient namespace state.

**Final recommendation**: Embrace explicit namespace management in all Clojure scripts to ensure predictable, portable behavior.