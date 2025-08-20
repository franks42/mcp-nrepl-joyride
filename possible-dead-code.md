# Possible Dead Code Analysis

**Generated:** 2025-01-19  
**Method:** Tree-sitter AST analysis for symbol usage tracking  
**Scope:** Complete codebase analysis for unused functions and redundant code  

## 🔍 Analysis Summary

Comprehensive tree-sitter analysis identified **200-300 lines** of potentially dead code across multiple categories:
- Redundant tool registration patterns
- Unused debugging/summary functions  
- Potentially obsolete async tools
- Unused connection utility functions
- Orphaned test files

## 🔴 HIGH PRIORITY - Redundant Tool Registration

### File: `src/nrepl_mcp_server/state/register_tools.clj`

**Function: `register-tools!` (lines 68-84)**
```clojure
(defn register-tools!
  "Explicitly trigger tool registration by requiring tool namespaces..."
  []
  (let [tool-count (registry/registry-size)]
    ;; Log registration for debugging
    (binding [*out* *err*]
      (println (str "🔧 Registered " tool-count " MCP tools: "
                    (vec (registry/list-tool-names)))))
    tool-count))
```

**Status:** ❌ **UNUSED** - Redundant and misleading
- **Usage:** Only called from `dispatch.clj:12` 
- **Problem:** Function name suggests it registers tools, but it only logs count
- **Reality:** Actual registration happens via `(register-all-tools)` at line 94
- **Impact:** Misleading function that serves no real purpose

### File: `src/nrepl_mcp_server/mcp_server/dispatch.clj`

**Line 12: Redundant call**
```clojure
;; Initialize tool registry by triggering self-registration
;; This causes all tool namespaces to load and register themselves
(register/register-tools!)
```

**Status:** ❌ **REDUNDANT CALL**
- **Problem:** Tools are already registered by side-effect in `register_tools.clj:94`
- **Impact:** Unnecessary function call that does nothing useful

## 🟡 MEDIUM PRIORITY - Unused Summary Functions

### File: `src/nrepl_mcp_server/state/connection.clj`

**Function: `get-connection-summary`**
- **Status:** ❌ **UNUSED** - No references found in codebase
- **Purpose:** Debugging function for connection state
- **Impact:** Dead debugging code

### File: `src/nrepl_mcp_server/state/messages.clj`

**Function: `get-message-summary`**  
- **Status:** ❌ **UNUSED** - No references found in codebase
- **Purpose:** Debugging function for message state
- **Impact:** Dead debugging code

### File: `src/nrepl_mcp_server/state/results.clj`

**Function: `get-result-summary`**
- **Status:** ❌ **UNUSED** - No references found in codebase  
- **Purpose:** Debugging function for result state
- **Impact:** Dead debugging code

### File: `src/nrepl_mcp_server/state/register_tools.clj`

**Function: `get-registry-status` (lines 86-91)**
```clojure
(defn get-registry-status
  "Get current registry status for debugging
   Returns: {:tool-count N :tool-names [...]}"
  []
  {:tool-count (registry/registry-size)
   :tool-names (vec (registry/list-tool-names))})
```

**Status:** ❌ **UNUSED** - No references found in codebase
- **Purpose:** Registry debugging information
- **Impact:** Dead debugging code

## 🟠 MEDIUM PRIORITY - Potentially Obsolete Async Tools

**Context:** The new `nrepl-eval` tool provides a simpler synchronous interface with built-in timeout handling, potentially making these async tools redundant.

### File: `src/nrepl_mcp_server/mcp_server/tools/nrepl_send_message_async.clj`

**Status:** ❓ **POTENTIALLY OBSOLETE**
- **Usage:** No direct usage found in tree-sitter analysis
- **Superseded by:** `nrepl-eval` tool with simpler interface
- **Consideration:** May still be needed for advanced async use cases
- **Recommendation:** Validate if any external clients use this tool

### File: `src/nrepl_mcp_server/mcp_server/tools/nrepl_get_result_async.clj`

**Status:** ❓ **POTENTIALLY OBSOLETE**
- **Usage:** No direct usage found in tree-sitter analysis  
- **Superseded by:** `nrepl-eval` tool handles results internally
- **Consideration:** May still be needed for advanced async workflows
- **Recommendation:** Validate if any external clients use this tool

### File: `src/nrepl_mcp_server/mcp_server/tools/nrepl_send_message.clj`

**Status:** ❓ **POTENTIALLY OBSOLETE**
- **Usage:** No direct usage found in tree-sitter analysis
- **Complexity:** 148 lines of synchronous wrapper over async core
- **Superseded by:** `nrepl-eval` provides simpler evaluation interface
- **Consideration:** Supports full nREPL operations (not just eval)
- **Recommendation:** Determine if generic nREPL operations are still needed

## 🟢 LOW PRIORITY - Unused Connection Functions

### File: `src/nrepl_mcp_server/nrepl_client/connection.clj`

**Function: `wait-for-state-change`**
- **Status:** ❌ **UNUSED** - No references found
- **Purpose:** Connection state monitoring utility
- **Impact:** Dead utility code

**Function: `parse-host-port`**  
- **Status:** ❌ **UNUSED** - No references found
- **Purpose:** Host:port parsing utility
- **Impact:** Dead utility code

**Function: `read-connection-file`**
- **Status:** ❌ **UNUSED** - No references found
- **Purpose:** nREPL port file reading utility  
- **Impact:** Dead utility code

## 🔵 INVESTIGATION NEEDED - Test Files

### Root Directory Files

**File: `test-stderr.clj`**
```clojure
;; Test file that writes to stderr
(println "This goes to stdout")
(binding [*err* (java.io.PrintWriter. System/err)]
  (.println *err* "This should go to stderr")
  (.flush *err*))
:stderr-test-done
```

**Status:** ❓ **UNCLEAR PURPOSE**
- **Location:** Root directory (unusual for test files)
- **Content:** Simple stderr testing
- **Recommendation:** Clarify if this is a development artifact

**File: `test-stderr-capture.clj`**
```clojure
;; Test file for stderr capture validation
;; This file produces both stdout and stderr output for testing
(println "stdout: This message goes to standard output")
(binding [*out* *err*]
  (println "stderr: This message goes to standard error"))
;; ... more test code
```

**Status:** ❓ **UNCLEAR PURPOSE**
- **Location:** Root directory (unusual for test files)  
- **Content:** Stdout/stderr testing
- **Recommendation:** Clarify if this is a development artifact

## 📊 Impact Assessment

### Safe to Remove Immediately
- **`register-tools!` function and its call** - Redundant registration pattern
- **All `*-summary` functions** - Unused debugging functions  
- **`get-registry-status` function** - Unused debugging function
- **Unused connection utility functions** - Dead utility code

**Estimated cleanup:** ~100-150 lines

### Requires Validation Before Removal
- **Async nREPL tools** - May still be needed for advanced use cases
- **Root directory test files** - May be development artifacts

**Estimated additional cleanup:** ~100-150 lines

## 🎯 Recommended Action Plan

### Phase 1: Safe Removals (Immediate)
1. Remove `register-tools!` function from `register_tools.clj`
2. Remove redundant call from `dispatch.clj:12`
3. Remove all unused `*-summary` functions
4. Remove `get-registry-status` function
5. Remove unused connection utility functions

### Phase 2: Validation Required
1. Confirm async tools are no longer needed by external clients
2. Validate purpose of root directory test files
3. Remove validated dead code

### Phase 3: Cleanup Benefits
- **Reduced complexity** - Simpler codebase with clear responsibilities
- **Improved maintainability** - Less code to understand and maintain
- **Clearer architecture** - Remove misleading function names and patterns
- **Faster development** - Less cognitive overhead from dead code

## 🔧 Tree-sitter Analysis Method

**Tools used:**
- `mcp__tree_sitter__get_symbols()` - Extract function definitions
- `mcp__tree_sitter__find_usage()` - Track symbol usage across files
- `mcp__tree_sitter__analyze_project()` - Overall project structure

**Languages analyzed:** Clojure (full semantic analysis)

**Coverage:** Complete source tree under `src/` directory

**Confidence level:** High for unused functions, Medium for potentially obsolete tools (requires runtime validation)