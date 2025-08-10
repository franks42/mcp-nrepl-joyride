# AI Coding Best Practices for Python and Clojure

## Executive Summary

This document defines coding standards and best practices for AI assistants working with Python and Clojure codebases. These practices emphasize automation, verification, and consistency to ensure high-quality code generation and modification.

## 🎯 Core Principles

### 1. **Format → Lint → Fix → Format → Lint**
Always format before linting. Fix issues. Format again. Verify clean.

### 2. **Analyze Before Acting**
Use tree-sitter to understand code structure before making changes.

### 3. **Data Over Exceptions**
Return error objects rather than throwing exceptions when possible.

### 4. **Explicit Over Implicit**
Clear types, names, and documentation always.

### 5. **Tools Over Manual**
Use automation tools (cljfmt, black, tree-sitter) rather than manual formatting.

## 🌳 Tree-sitter Integration

### Initial Setup
```bash
# Register projects for analysis
mcp-treesitter register-project /path/to/project --language clojure
mcp-treesitter register-project /path/to/project --language python

# Verify registration
mcp-treesitter list-projects
```

### Before ANY Code Changes
```bash
# 1. Understand structure
mcp-treesitter analyze-project project --scan-depth 3

# 2. Find dependencies
mcp-treesitter analyze-namespace-dependencies project

# 3. Locate usages
mcp-treesitter find-usage project --symbol target-function
```

## 🎨 Clojure Best Practices

### Code Quality Workflow

**🎯 USE EXISTING SCRIPTS - CREATE MORE AS NEEDED!**

```bash
# 📜 REUSABLE SCRIPTS FOR CONSISTENCY!

# EXISTING: format.sh (already created!)
./format.sh              # Formats all Clojure code with cljfmt

# CREATE: lint-clojure.sh (for consistency)
#!/bin/bash
echo "🔍 Linting Clojure code..."
clj-kondo --lint src/
echo "✅ Clojure linting complete!"

# CREATE: clojure-quality.sh (Combined workflow)
#!/bin/bash
echo "🎨 Clojure code quality check..."
./format.sh              # 1. Format first
./lint-clojure.sh        # 2. Then lint

# If issues found, fix and repeat:
echo "If issues found:"
echo "  1. Fix the issues"
echo "  2. Run ./clojure-quality.sh again"
echo "  3. Repeat until clean"

# NEVER DO: One-off commands like this
# cljfmt fix src/ && clj-kondo --lint src/  # ❌ NOT reusable!

# ALWAYS DO: Reusable scripts
./clojure-quality.sh  # ✅ Consistent and repeatable!
```

### Naming Conventions

```clojure
;; Constants in CAPS
(def MAX-RETRIES 3)
(def DEFAULT-TIMEOUT 30000)

;; Predicates end with ?
(defn valid? [x] ...)
(defn empty? [coll] ...)

;; Side effects end with !
(defn save! [data] ...)
(defn delete! [id] ...)

;; Conversions use ->
(defn ->json [data] ...)
(defn map->User [m] ...)

;; Private functions use defn-
(defn- internal-helper [] ...)
```

### Function Structure

```clojure
(defn process-data
  "Processes input data with optional configuration.
  
  Args:
    data - Map containing :id and :content keys
    opts - Optional map with:
           :timeout - Processing timeout in ms (default: 30000)
           :retries - Number of retry attempts (default: 3)
  
  Returns:
    {:status :ok :result processed-data} on success
    {:error string :details map} on failure
  
  Example:
    (process-data {:id 1 :content \"test\"} {:timeout 5000})"
  [{:keys [id content] :as data} 
   & [{:keys [timeout retries] 
       :or {timeout 30000 retries 3}}]]
  (try
    {:status :ok :result (transform content)}
    (catch Exception e
      {:error (.getMessage e)
       :details {:id id :exception (type e)}})))
```

### State Management

```clojure
;; Use atoms for simple state
(defonce app-state (atom {}))

;; Clear update functions
(defn update-connection [conn]
  (swap! app-state assoc :connection conn))

;; Avoid refs, agents unless specifically needed
;; Atoms cover 95% of use cases
```

### Error Handling

```clojure
;; Return data, not exceptions
(defn safe-divide [a b]
  (if (zero? b)
    {:error "Division by zero" :numerator a}
    {:result (/ a b)}))

;; Use ex-info for rich exceptions when needed
(throw (ex-info "Connection failed" 
                {:host host :port port :attempts 3}))
```

### Threading & Data Flow

```clojure
;; Use threading macros for clarity
(->> data
     (filter valid?)
     (map process)
     (reduce aggregate))

;; some-> for nil-safe chains
(some-> user
        :profile
        :settings
        :theme)

;; cond-> for conditional threading
(cond-> data
  (seq errors) (assoc :errors errors)
  verbose?     (assoc :debug-info (get-debug)))
```

### Tree-sitter Analysis for Clojure

```bash
# Before refactoring
mcp-treesitter find-function-definitions project --pattern "defn.*"
mcp-treesitter find-usage project --symbol function-to-change

# Find patterns
mcp-treesitter find-clojure-idioms project --idiom-type threading-macros
mcp-treesitter run-query project --query "(defn- ) @private-fn"

# Verify structure after changes
mcp-treesitter get-ast project --path src/core.clj --max-depth 3
```

## 🐍 Python Best Practices

### Code Quality Workflow

**🎯 ALWAYS CREATE REUSABLE SCRIPTS - NO ONE-OFF CLI COMMANDS!**

```bash
# 🚀 ALWAYS USE UV FOR ALL PYTHON OPERATIONS!
# 📜 ALWAYS CREATE SCRIPTS FOR REPEATABILITY AND CONSISTENCY!

# CREATE: format-python.sh (NOT one-off commands)
#!/bin/bash
echo "🐍 Formatting Python code..."
uv run black .
uv run isort .
echo "✅ Python formatting complete!"

# CREATE: lint-python.sh (NOT one-off commands)  
#!/bin/bash
echo "🔍 Linting Python code..."
uv run flake8 .
uv run mypy .
echo "✅ Python linting complete!"

# CREATE: python-quality.sh (Combined workflow)
#!/bin/bash
echo "🎨 Python code quality check..."
./format-python.sh
./lint-python.sh
echo "🚨 MANUAL CHECK: Verify no blank lines between decorators and functions!"
echo "✅ Python quality check complete!"

# NEVER DO: One-off commands like this
# uv run black file.py && uv run flake8 file.py  # ❌ NOT reusable!

# ALWAYS DO: Reusable scripts
./python-quality.sh  # ✅ Consistent and repeatable!
```

**Why Scripts Over CLI Commands:**
- ✅ **Consistency** - Same commands every time
- ✅ **Documentation** - Scripts self-document the process
- ✅ **Team sharing** - Other developers use same workflow
- ✅ **CI/CD ready** - Scripts work in automation
- ✅ **Less errors** - No typos in repeated commands

### Type Annotations (REQUIRED)

```python
from typing import Dict, List, Optional, Union, Any
from dataclasses import dataclass

# ALWAYS include type hints
def process_data(
    data: Dict[str, Any],
    timeout: int = 30000,
    retries: int = 3
) -> Dict[str, Any]:
    """
    Process input data with configuration.
    
    Args:
        data: Input dictionary with 'id' and 'content' keys
        timeout: Processing timeout in milliseconds
        retries: Number of retry attempts
        
    Returns:
        Success: {"status": "ok", "result": processed_data}
        Failure: {"error": str, "details": dict}
        
    Raises:
        ValueError: If data missing required keys
    """
    ...

# Use dataclasses for structures
@dataclass
class Config:
    """Configuration for nREPL connection."""
    host: str
    port: int = 7888
    timeout: int = 30000
    
    def validate(self) -> bool:
        """Validate configuration values."""
        return self.port > 0 and self.timeout > 0
```

### Error Handling

```python
# Specific exception classes
class NREPLConnectionError(Exception):
    """Failed to connect to nREPL server."""
    pass

# Return error dictionaries for recoverable errors
def connect(host: str, port: int) -> Dict[str, Any]:
    try:
        conn = establish_connection(host, port)
        return {"status": "connected", "connection": conn}
    except socket.timeout:
        return {"error": "Connection timeout", "host": host, "port": port}
    except Exception as e:
        return {"error": str(e), "type": type(e).__name__}

# NEVER silent failures
# BAD:  except: pass
# GOOD: except Exception as e: logger.error(f"Failed: {e}")
```

### Async Best Practices

```python
import asyncio
from typing import AsyncIterator

async def fetch_data(url: str) -> Dict[str, Any]:
    """
    Fetch data asynchronously.
    
    Always use async with for cleanup.
    Always specify return types.
    """
    async with aiohttp.ClientSession() as session:
        async with session.get(url) as response:
            return await response.json()

# Type hints for async generators
async def stream_results() -> AsyncIterator[Dict[str, Any]]:
    """Stream results with proper typing."""
    while True:
        result = await get_next_result()
        if not result:
            break
        yield result
```

### Documentation Standards

```python
def complex_operation(
    data: List[Dict[str, Any]],
    config: Optional[Config] = None,
    **kwargs: Any
) -> Dict[str, Union[List, str, int]]:
    """
    Perform complex operation on data.
    
    This function processes a list of dictionaries according to
    the provided configuration. It supports batch processing
    and incremental updates.
    
    Args:
        data: List of dictionaries to process. Each dict must
              contain 'id' and 'value' keys.
        config: Optional configuration object. If None, uses
                default configuration.
        **kwargs: Additional options:
                  - verbose (bool): Enable detailed logging
                  - batch_size (int): Process in batches
    
    Returns:
        Dictionary containing:
        - 'results': List of processed items
        - 'status': 'success' or 'partial'
        - 'processed_count': Number of items processed
        
    Raises:
        ValueError: If data is empty or malformed
        ConfigError: If configuration is invalid
        
    Example:
        >>> data = [{"id": 1, "value": "test"}]
        >>> result = complex_operation(data, verbose=True)
        >>> assert result["status"] == "success"
    """
    if not data:
        raise ValueError("Data cannot be empty")
    
    # Implementation...
```

### Tree-sitter Analysis for Python

```bash
# Find missing type hints
mcp-treesitter run-query project --language python --query "
  (function_definition
    !return_type) @missing-return"

# Find bare excepts (bad practice)
mcp-treesitter run-query project --query "
  (except_clause !type) @bare-except"

# Verify async patterns
mcp-treesitter run-query project --query "
  (function_definition async: (async)) @async-fn"

# Check imports
mcp-treesitter get-dependencies project --file-path src/main.py

# 🚨 CRITICAL: Find potential decorator spacing issues
# (Tree-sitter can't detect blank lines, so manual inspection required)
mcp-treesitter run-query project --query "
  (decorator) @decorator"
```

## 🔄 Universal Practices

### Version Control Workflow

```bash
# 1. Analyze impact
mcp-treesitter find-usage project --symbol changing-function

# 2. Make changes
# ... edit code ...

# 3. Format and lint
./format.sh && clj-kondo --lint src/           # Clojure
uv run black . && uv run flake8 && uv run mypy  # Python

# 4. Verify structure
mcp-treesitter analyze-complexity project --file-path changed-file

# 5. Commit with clear message
git add -p  # Review each change
git commit -m "type: Clear, specific description

- Detail what changed
- Explain why if not obvious
- Reference issue if applicable"
```

### Testing Practices

```clojure
;; Clojure - Use comment blocks for REPL testing
(comment
  ;; Test during development
  (process-data {:id 1 :content "test"})
  (process-data nil)  ; Edge case
  (process-data {:id 1} {:timeout 100})  ; Timeout test
  )
```

```python
# Python - Doctests for simple cases
def add(a: int, b: int) -> int:
    """
    Add two numbers.
    
    >>> add(2, 3)
    5
    >>> add(-1, 1)
    0
    """
    return a + b

# Pytest for complex cases
def test_connection_timeout():
    """Test that connection times out appropriately."""
    with pytest.raises(TimeoutError):
        connect("localhost", 9999, timeout=0.001)
```

### Performance Considerations

```clojure
;; Clojure - Use transducers for large data
(transduce 
  (comp (filter valid?) (map process))
  conj
  []
  large-dataset)

;; Prefer lazy sequences
(take 10 (filter expensive? (range)))

;; Avoid reflection
(set! *warn-on-reflection* true)
```

```python
# Python - Use generators for memory efficiency
def process_large_file(filepath: str) -> Iterator[Dict]:
    """Process file line by line without loading all."""
    with open(filepath) as f:
        for line in f:
            yield process_line(line)

# Use comprehensions appropriately
# Good for small/medium data
processed = [process(x) for x in data if valid(x)]

# Use generator for large data
processed = (process(x) for x in data if valid(x))
```

## 📋 Standard Development Checklist

### Before Starting
- [ ] Register project with tree-sitter
- [ ] Analyze project structure
- [ ] Understand dependencies
- [ ] Find existing patterns

### During Development
- [ ] Follow naming conventions
- [ ] Add type hints (Python) / docstrings (both)
- [ ] Use standard patterns (threading, destructuring)
- [ ] Handle errors with data returns

### Before Committing
- [ ] Run formatters (cljfmt/black)
- [ ] Run linters (clj-kondo/flake8)
- [ ] Fix all warnings, not just errors
- [ ] Run type checker (mypy for Python)
- [ ] 🚨 **Python CRITICAL**: Manually verify no blank lines between decorators and functions
- [ ] Verify with tree-sitter analysis
- [ ] Test edge cases

### Common Anti-Patterns to Avoid

#### Python - Critical Decorator Issues

```python
# CRITICAL: Blank lines between decorators and functions cause obscure errors!
@dataclass
class Config:
    pass

@app.route('/api')

def handler():  # <-- This blank line breaks the decorator!
    pass

# Many linters miss this! Causes hard-to-debug runtime issues
# Function won't be registered with Flask, dataclass won't work, etc.

# CORRECT: No blank line between decorator and function
@dataclass
class Config:
    pass

@app.route('/api')
def handler():  # <-- Decorator properly applied
    pass

# CORRECT: Multiple decorators
@lru_cache(maxsize=128)
@dataclass
class CachedConfig:
    pass
```

**🚨 Critical Rule**: NEVER put blank lines between decorators and the decorated function/class. This breaks the decorator application and causes obscure runtime errors that are difficult to debug.

#### Clojure
```clojure
;; BAD: Deeply nested code
(if condition
  (if another
    (if third
      (do-something))))

;; GOOD: Use cond or extract functions
(cond
  (not condition) nil
  (not another) nil
  (not third) nil
  :else (do-something))

;; BAD: Mutable state without atoms
(def state {})  ; Will cause bugs

;; GOOD: Use atoms
(defonce state (atom {}))
```

#### Python
```python
# BAD: Missing type hints
def process(data, config=None):
    ...

# GOOD: Full type annotations
def process(data: Dict[str, Any], config: Optional[Config] = None) -> Dict[str, Any]:
    ...

# BAD: Mutable default arguments
def process(data, items=[]):  # Shared between calls!
    items.append(data)

# GOOD: Use None default
def process(data, items=None):
    if items is None:
        items = []
    items.append(data)
```

## 🚀 Quick Reference Commands

```bash
# 📜 REUSABLE SCRIPTS (NOT one-off CLI commands!)

# Clojure workflow - USE SCRIPTS!
./clojure-quality.sh    # ✅ Complete workflow (format + lint)
./format.sh             # ✅ Just formatting
./lint-clojure.sh       # ✅ Just linting

# Python workflow - USE SCRIPTS!
./python-quality.sh     # ✅ Complete workflow (format + lint + manual check reminder)
./format-python.sh      # ✅ Just formatting (black + isort)
./lint-python.sh        # ✅ Just linting (flake8 + mypy)

# ❌ NEVER DO: One-off commands like these
# uv run black . && uv run flake8    # NOT reusable!
# cljfmt fix . && clj-kondo --lint   # NOT reusable!

# Tree-sitter analysis
mcp-treesitter analyze-project project
mcp-treesitter find-usage project --symbol function-name
mcp-treesitter analyze-complexity project --file-path file

# Git workflow
git add -p && git commit -m "type: description"
```

## 📚 Tool Versions

- **cljfmt**: 0.9.2 (from Clojars)
- **clj-kondo**: Latest stable
- **black**: Latest via UV
- **flake8**: Latest via UV
- **mypy**: Latest via UV
- **tree-sitter**: Via MCP server

## 🎓 Remember

1. **📜 Scripts over CLI** - Create reusable scripts, not one-off commands!
2. **Format before lint** - Always!
3. **Tree-sitter before refactor** - Understand first!
4. **🚀 UV for ALL Python operations** - Never use pip/python directly!
5. **Types everywhere** - No exceptions!
6. **Data over exceptions** - Return errors!
7. **Test the edges** - Empty, nil, timeout!
8. **🚨 Check decorator spacing** - Manual verification required!

---

*This document is the authoritative guide for AI assistants working with Python and Clojure code. Follow these practices consistently for high-quality, maintainable code.*