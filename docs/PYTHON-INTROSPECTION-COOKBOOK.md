# Python Introspection Cookbook via Basilisp + nREPL

## Overview

This cookbook provides **practical examples** of using our MCP-nREPL + Basilisp architecture to introspect and debug live Python applications. All examples use familiar Clojure syntax to explore Python runtime state through our unified MCP interface.

## Prerequisites

### 1. Embed Basilisp + nREPL in Your Python Application

```python
# Add to your Python application
import basilisp.main

# Start embedded nREPL server (development/debug mode)
if __name__ == "__main__" and "--debug-nrepl" in sys.argv:
    basilisp.main.start_nrepl_server(port=7888)
    print("🔧 Basilisp nREPL server started on port 7888")

# Or conditionally based on environment
import os
if os.getenv('ENABLE_NREPL') == 'true':
    basilisp.main.start_nrepl_server(port=int(os.getenv('NREPL_PORT', 7888)))
```

### 2. Connect via MCP-nREPL Bridge

```bash
# Start your Python app with nREPL enabled
ENABLE_NREPL=true python your_app.py

# Connect via our MCP client and run health check
python3 ./mcp_nrepl_client.py --tool nrepl-health-check --quiet
python3 ./mcp_nrepl_client.py --eval "(+ 1 2 3)" --quiet
```

## MCP-nREPL Bridge Interface

Before diving into introspection patterns, it's essential to understand how the MCP-nREPL bridge works and how to send Clojure code to your embedded Python application.

### Available MCP Tools

Our MCP server provides these essential tools for Python introspection:

```bash
# Core Evaluation & File Loading
python3 ./mcp_nrepl_client.py --eval "(clojure-expression)" --quiet
python3 ./mcp_nrepl_client.py --tool nrepl-load-file --args '{"file-path": "/path/to/utils.clj"}' --quiet

# Symbol Introspection & Discovery
python3 ./mcp_nrepl_client.py --tool nrepl-doc --args '{"symbol": "py-system-info"}' --quiet
python3 ./mcp_nrepl_client.py --tool nrepl-source --args '{"symbol": "py-explore-object"}' --quiet
python3 ./mcp_nrepl_client.py --tool nrepl-apropos --args '{"query": "py-"}' --quiet

# Development Assistance
python3 ./mcp_nrepl_client.py --tool nrepl-complete --args '{"prefix": "python/sys."}' --quiet
python3 ./mcp_nrepl_client.py --tool nrepl-require --args '{"namespace": "your-introspection-utils"}' --quiet

# Session Management
python3 ./mcp_nrepl_client.py --tool nrepl-new-session
python3 ./mcp_nrepl_client.py --tool nrepl-status

# Error Handling & Debugging  
python3 ./mcp_nrepl_client.py --tool nrepl-stacktrace
python3 ./mcp_nrepl_client.py --tool nrepl-interrupt

# System Diagnostics
python3 ./mcp_nrepl_client.py --tool nrepl-health-check
python3 ./mcp_nrepl_client.py --tool nrepl-health-check --args '{"include_performance": true, "verbose": true}'

# Connection Management
python3 ./mcp_nrepl_client.py --tool nrepl-connect --args '{"port": 7888}'
python3 ./mcp_nrepl_client.py --tool nrepl-test
```

### How It Works

**1. Code Execution Flow:**
```
[MCP Client] → [MCP Server] → [nREPL Client] → [Basilisp nREPL] → [Python Runtime]
     ↓              ↓              ↓               ↓                    ↓
[Results] ← [JSON Response] ← [nREPL Response] ← [Clojure Evaluation] ← [Python Interop]
```

**2. Python Object Access:**
```clojure
;; Access Python objects using the python/ namespace prefix
(python/sys.modules)          ;; → Python's sys.modules dict
(python/os.environ)           ;; → Environment variables
(python/your_app.some_var)    ;; → Your application's variables
```

**3. Result Handling:**
- **Simple values** (strings, numbers, booleans) → Direct JSON serialization
- **Complex objects** → String representation via `str()` 
- **Collections** → Clojure data structures when possible
- **Errors** → Exception messages with stack traces

### Connection Examples

```bash
# Test basic connection and math
python3 ./mcp_nrepl_client.py --eval "(+ 1 2 3)" --quiet
# Result: 6

# Access Python system info
python3 ./mcp_nrepl_client.py --eval "(str (python/sys.version-info))" --quiet  
# Result: "sys.version_info(major=3, minor=11, micro=5, ...)"

# Load utility functions first
python3 ./mcp_nrepl_client.py --tool nrepl-load-file --args '{"file-path": "examples/python_introspection_utils.clj"}' --quiet

# Then use loaded functions
python3 ./mcp_nrepl_client.py --eval "(py-system-info)" --quiet
# Result: Formatted system information
```

### Error Handling

```bash
# Syntax errors in Clojure code
python3 ./mcp_nrepl_client.py --eval "(python/nonexistent-module)" --quiet
# Result: Error message with details

# Python exceptions are caught and returned
python3 ./mcp_nrepl_client.py --eval "(python/1.__div__ 0)" --quiet  
# Result: "ZeroDivisionError: division by zero"
```

### Development Workflow

**Recommended approach for live introspection:**

1. **Start your Python app** with nREPL enabled
2. **Test connection** with simple evaluation
3. **Load utility functions** using nrepl-load-file  
4. **Explore interactively** using the loaded functions
5. **Create custom functions** as needed for your specific app

```bash
# 1. Test connection
python3 ./mcp_nrepl_client.py --eval "(+ 1 1)" --quiet

# 2. Load introspection utilities  
python3 ./mcp_nrepl_client.py --tool nrepl-load-file --args '{"file-path": "examples/python_introspection_utils.clj"}' --quiet

# 3. Run health check
python3 ./mcp_nrepl_client.py --eval "(py-health-check)" --quiet

# 4. Explore your app
python3 ./mcp_nrepl_client.py --eval "(py-explore-object python/app)" --quiet
```

## Phase 1: Pure Introspection (Safe Exploration)

### System Information

```clojure
;; Python version and platform info
(python/sys.version-info)
;; Returns: sys.version_info(major=3, minor=11, micro=5, releaselevel='final', serial=0)

(python/platform.system)
;; Returns: "Darwin" | "Linux" | "Windows"

(python/os.getcwd)  
;; Returns: "/path/to/current/directory"
```

### Memory and Resource Monitoring

```clojure
;; Memory usage (requires psutil)
(python/psutil.Process.memory-info)
;; Returns: pmem(rss=50331648, vms=4437135360, ...)

;; Garbage collection stats
(python/gc.get-stats)
;; Returns: [{'collections': 45, 'collected': 1234, 'uncollectable': 0}, ...]

;; Active thread count
(python/threading.active-count)
;; Returns: 3
```

### Module and Import Exploration

```clojure
;; List all imported modules
(->> (python/sys.modules.keys)
     (map str)
     (filter #(.startswith % "myapp"))
     sort)
;; Returns: ["myapp.models", "myapp.views", "myapp.utils"]

;; Explore module attributes
(python/dir python/myapp.models)
;; Returns: ["User", "Product", "Order", "__file__", "__name__", ...]

;; Check if module has attribute
(python/hasattr python/myapp.models "User")
;; Returns: True
```

### Object Introspection

```clojure
;; Deep object inspection
(python/vars some-python-object)
;; Returns: {'attr1': 'value1', 'attr2': 42, ...}

;; Object type information
(python/type some-object)
;; Returns: <class 'myapp.models.User'>

;; Method resolution order
(python/inspect.getmro (python/type some-object))
;; Returns: (<class 'myapp.models.User'>, <class 'object'>)

;; Get object members
(->> (python/inspect.getmembers some-object)
     (map first)
     (filter #(not (.startswith % "_"))))
;; Returns: ["name", "email", "save", "delete"]
```

## Phase 2: Safe Data Retrieval

### Database and Connection Inspection

```clojure
;; Database connection pool status (Django example)
(python/django.db.connections.databases)
;; Returns: {'default': {'ENGINE': 'django.db.backends.postgresql', ...}}

;; SQLAlchemy connection pool
(when (python/hasattr python/app "db")
  (python/len python/app.db.pool._available_connections))
;; Returns: 5

;; Redis connection status  
(when (python/hasattr python/app "redis")
  (python/app.redis.ping))
;; Returns: True
```

### Configuration and Settings

```clojure
;; Flask configuration
(when (python/hasattr python/app "config")
  (->> (python/dict python/app.config)
       (filter (fn [[k v]] (not (.startswith (str k) "SECRET"))))
       (into {})))
;; Returns: {"DEBUG": True, "TESTING": False, ...}

;; Django settings (safe subset)
(->> (python/dir python/django.conf.settings)
     (filter #(not (.startswith % "_")))
     (filter #(not (.contains (.upper %) "SECRET")))
     (map (fn [attr] [attr (python/getattr python/django.conf.settings attr)]))
     (into {}))
```

### Application State Queries

```clojure
;; Active user sessions (Django)
(python/len (python/Session.objects.filter :expire_date__gt (python/timezone.now)))
;; Returns: 42

;; Cache statistics (if using django-cache-machine)
(when (python/hasattr python/cache "stats")
  (python/cache.stats))
;; Returns: {"hits": 1234, "misses": 567, "hit_ratio": 0.685}

;; Queue length (Celery/RQ)
(when (python/hasattr python/app "task_queue")
  (python/len python/app.task_queue))
;; Returns: 7
```

### Function Signatures and Documentation

```clojure
;; Function signature inspection
(python/inspect.signature python/myapp.utils.process_data)
;; Returns: <Signature (data: dict, validate: bool = True) -> dict>

;; Function docstring
(python/inspect.getdoc python/myapp.utils.process_data)
;; Returns: "Process user data with optional validation..."

;; Function source code location
(python/inspect.getsourcefile python/myapp.utils.process_data)
;; Returns: "/app/myapp/utils.py"

;; Get function line numbers
(python/inspect.getsourcelines python/myapp.utils.process_data)
;; Returns: [["def process_data(data, validate=True):", "    ...", ...], 42]
```

## Phase 3: Controlled State Changes

### Safe Configuration Updates

```clojure
;; Change log level (reversible)
(let [old-level (python/logging.getLogger.level)]
  (python/logging.getLogger.setLevel python/logging.DEBUG)
  (str "Log level changed from " old-level " to DEBUG"))

;; Toggle feature flags (if using feature flag system)
(python/app.feature_flags.toggle "new_ui_beta" true)
;; Returns: "Feature 'new_ui_beta' enabled"

;; Update cache timeout
(python/cache.set "debug_mode_timeout" 300 300)
;; Returns: True
```

### Development Utilities

```clojure
;; Clear application caches
(python/cache.clear)
(python/app.cache.clear)
;; Returns: True

;; Reset test data (development only)
(when (python/app.config.get "TESTING")
  (python/app.reset_test_data))

;; Reload specific module (careful!)
(python/importlib.reload python/myapp.utils)
;; Returns: <module 'myapp.utils' from '/app/myapp/utils.py'>
```

### Performance Profiling

```clojure
;; Profile a function call
(python/cProfile.runctx 
  "myapp.utils.expensive_function(test_data)"
  (python/globals)
  (python/locals))

;; Memory profiling with tracemalloc
(do
  (python/tracemalloc.start)
  ;; Run some operations
  (let [current (python/tracemalloc.get_traced_memory)]
    (python/tracemalloc.stop)
    current))
;; Returns: (current_memory, peak_memory)
```

## Framework-Specific Patterns

### Django Applications

```clojure
;; Model introspection
(->> (python/django.apps.apps.get_models)
     (map python/str)
     sort)
;; Returns: ["<class 'myapp.models.User'>", "<class 'myapp.models.Product'>", ...]

;; Active database queries (with django-debug-toolbar)
(when (python/hasattr python/connection "queries")
  (python/len python/connection.queries))
;; Returns: 15

;; URL pattern inspection
(->> python/django.conf.urls.urlpatterns
     (map (fn [pattern] (python/str pattern)))
     (take 10))

;; Middleware inspection
python/django.conf.settings.MIDDLEWARE
;; Returns: ["django.middleware.security.SecurityMiddleware", ...]
```

### Flask Applications

```clojure
;; Route inspection
(->> python/app.url_map.iter_rules
     (map (fn [rule] [(python/str rule.rule) (python/str rule.endpoint)]))
     (into {}))
;; Returns: {"/": "index", "/api/users": "api.users", ...}

;; Blueprint inspection
(->> python/app.blueprints.keys
     (map str)
     sort)
;; Returns: ["admin", "api", "main"]

;; Request context inspection (during request)
(when (python/has_request_context)
  {:method python/request.method
   :path python/request.path
   :args (python/dict python/request.args)})
```

### FastAPI Applications

```clojure
;; Route metadata
(->> python/app.routes
     (map (fn [route] 
       {:path (python/getattr route "path" nil)
        :methods (python/getattr route "methods" nil)
        :name (python/getattr route "name" nil)}))
     (filter :path))

;; OpenAPI schema inspection  
(python/app.openapi)
;; Returns: {"openapi": "3.0.2", "info": {...}, "paths": {...}}

;; Dependency inspection
(->> python/app.dependency_overrides.keys
     (map str))
```

## Error Investigation Patterns

### Exception Analysis

```clojure
;; Last exception info
(when python/sys.last_traceback
  (python/traceback.format_tb python/sys.last_traceback))

;; Current exception in except block
(python/sys.exc_info)
;; Returns: (<class 'ValueError'>, ValueError('invalid value'), <traceback object>)

;; Frame inspection during debugging
(let [frame (python/sys._getframe)]
  {:filename (python/frame.f_code.co_filename)
   :function (python/frame.f_code.co_name)
   :lineno python/frame.f_lineno
   :locals (python/dict python/frame.f_locals)})
```

### Log Analysis

```clojure
;; Recent log entries (if using structured logging)
(when (python/hasattr python/app "log_buffer")
  (->> python/app.log_buffer
       (take 10)
       (map (fn [entry] [(python/entry.timestamp) (python/entry.message)]))))

;; Logger levels
(->> (python/logging.Logger.manager.loggerDict.items)
     (map (fn [[name logger]] 
       [name (when (python/hasattr logger "level") 
               python/logger.level)]))
     (into {}))
```

## Advanced Patterns

### Async/Await Introspection

```clojure
;; Running async tasks (asyncio)
(python/len (python/asyncio.all_tasks))
;; Returns: 3

;; Event loop status
(python/asyncio.get_running_loop.is_running)
;; Returns: True

;; Task inspection
(->> (python/asyncio.all_tasks)
     (map (fn [task] 
       {:name (python/task.get_name)
        :done (python/task.done)
        :cancelled (python/task.cancelled)})))
```

### Celery/Background Jobs

```clojure
;; Active workers (Celery)
(python/celery_app.control.inspect.active)
;; Returns: {"worker1@hostname": [...], ...}

;; Queue inspection
(python/celery_app.control.inspect.reserved)
;; Returns: Queue contents

;; Task history
(python/celery_app.control.inspect.history)
```

### WebSocket Connections

```clojure
;; Active WebSocket connections (if using channels/websockets)
(when (python/hasattr python/app "websocket_connections")
  (python/len python/app.websocket_connections))

;; Connection details
(->> python/app.websocket_connections
     (map (fn [conn] 
       {:remote_addr python/conn.remote_addr
        :connected_at python/conn.connected_at
        :state python/conn.state})))
```

## Integration with MCP Tools

### Enhanced nREPL Development Workflow

With our new nREPL tools, Python introspection becomes much more powerful and interactive:

**1. Discovery and Documentation Workflow:**
```bash
# Discover available Python introspection functions
python3 ./mcp_nrepl_client.py --tool nrepl-apropos --args '{"query": "py-"}' --quiet

# Get documentation for specific functions
python3 ./mcp_nrepl_client.py --tool nrepl-doc --args '{"symbol": "py-health-check"}' --quiet
python3 ./mcp_nrepl_client.py --tool nrepl-doc --args '{"symbol": "py-explore-object"}' --quiet

# View source code to understand implementations
python3 ./mcp_nrepl_client.py --tool nrepl-source --args '{"symbol": "py-system-info"}' --quiet
```

**2. Interactive Development with Sessions:**
```bash
# Create dedicated session for Python introspection
python3 ./mcp_nrepl_client.py --tool nrepl-new-session

# Load utilities in specific session
python3 ./mcp_nrepl_client.py --tool nrepl-load-file --args '{"file-path": "examples/python_introspection_utils.clj", "session": "session-id"}' --quiet

# Work within that session context
python3 ./mcp_nrepl_client.py --eval "(py-health-check)" --session "session-id" --quiet
```

**3. Namespace Management for Introspection:**
```bash
# Require Python interop namespaces as needed
python3 ./mcp_nrepl_client.py --tool nrepl-require --args '{"namespace": "clojure.pprint", "as": "pp"}'

# Load additional utility libraries
python3 ./mcp_nrepl_client.py --tool nrepl-require --args '{"namespace": "python-introspection-utils"}'
```

**4. Code Completion for Python Objects:**
```bash
# Get completions for Python namespace
python3 ./mcp_nrepl_client.py --tool nrepl-complete --args '{"prefix": "python/sys."}' --quiet

# Find Python-specific functions
python3 ./mcp_nrepl_client.py --tool nrepl-complete --args '{"prefix": "py-"}' --quiet
```

**5. Error Handling and Debugging:**
```bash
# If introspection code fails, get detailed stack trace
python3 ./mcp_nrepl_client.py --tool nrepl-stacktrace --quiet

# Interrupt long-running introspection operations
python3 ./mcp_nrepl_client.py --tool nrepl-interrupt --quiet
```

### Enhanced Python Introspection Functions

With the new tools, you can create more sophisticated introspection utilities:

```clojure
;; Load Python introspection utilities
python3 ./mcp_nrepl_client.py --tool nrepl-load-file --args '{"file-path": "examples/python_introspection_utils.clj"}' --quiet

;; Enhanced usage examples:
(py-health-check)           ; Comprehensive app health
(py-system-info)            ; System and runtime information
(py-memory-info)            ; Memory usage details
(py-config-summary)         ; Safe configuration overview
(py-db-status)              ; Database connection health
(py-performance-snapshot)   ; Performance metrics
(py-explore-object python/app)  ; Deep object introspection

;; New workflow patterns:
;; 1. Discover -> Document -> Experiment -> Build
python3 ./mcp_nrepl_client.py --tool nrepl-apropos --args '{"query": "memory"}'
python3 ./mcp_nrepl_client.py --tool nrepl-doc --args '{"symbol": "py-memory-info"}'
python3 ./mcp_nrepl_client.py --eval "(py-memory-info)" --quiet

;; 2. Session-based exploration
python3 ./mcp_nrepl_client.py --tool nrepl-new-session  # Create isolated context
python3 ./mcp_nrepl_client.py --eval "(def debug-session true)" --session "session-id"
```

### Development Best Practices with New Tools

**1. Safe Experimentation:**
```bash
# Create a dedicated debugging session
python3 ./mcp_nrepl_client.py --tool nrepl-new-session

# Load only the utilities you need
python3 ./mcp_nrepl_client.py --tool nrepl-require --args '{"namespace": "python-utils-simple"}' --session "debug-session"

# If something goes wrong, interrupt and check errors
python3 ./mcp_nrepl_client.py --tool nrepl-interrupt --session "debug-session"
python3 ./mcp_nrepl_client.py --tool nrepl-stacktrace --session "debug-session"
```

**2. Iterative Development:**
```bash
# Start with discovery
python3 ./mcp_nrepl_client.py --tool nrepl-apropos --args '{"query": "config"}'

# Get documentation for functions of interest  
python3 ./mcp_nrepl_client.py --tool nrepl-doc --args '{"symbol": "py-config-summary"}'

# Experiment with modifications
python3 ./mcp_nrepl_client.py --eval "(defn my-config-check [] (merge (py-config-summary) {:custom true}))"

# Test your changes
python3 ./mcp_nrepl_client.py --eval "(my-config-check)"
```

**3. Documentation and Knowledge Sharing:**
```bash
# Document your introspection functions
python3 ./mcp_nrepl_client.py --tool nrepl-source --args '{"symbol": "py-health-check"}' > health-check-impl.clj

# Share useful patterns with your team
python3 ./mcp_nrepl_client.py --eval "(defn team-debug-setup [] (do (require 'python-introspection-utils) (println \"Debug environment ready!\")))"
```

### Custom MCP Tools for Python Introspection

Building on our nREPL foundation, we could extend our MCP server with Python-specific tools:

```json
{
  "name": "python-health-check", 
  "description": "Comprehensive Python application health check via Basilisp",
  "inputSchema": {
    "type": "object",
    "properties": {
      "include_memory": {"type": "boolean", "default": true},
      "include_db": {"type": "boolean", "default": true},
      "include_config": {"type": "boolean", "default": false},
      "session": {"type": "string", "description": "nREPL session ID"}
    }
  }
}
```

These tools would internally use our nREPL infrastructure while providing a more specialized Python-focused interface.

## Best Practices

### Safety Guidelines

1. **Start with read-only operations** - Explore before modifying
2. **Use hasattr checks** - Verify attributes exist before accessing
3. **Handle exceptions gracefully** - Wrap risky operations in try-catch
4. **Avoid secrets** - Filter out sensitive configuration values
5. **Test in development** - Validate patterns before production use

### Performance Considerations

1. **Limit large data retrievals** - Use take/limit for big collections  
2. **Cache inspection results** - Store frequently accessed data
3. **Use lazy sequences** - Don't realize entire collections unnecessarily
4. **Monitor resource usage** - Track memory impact of introspection

### Development Workflow

1. **Incremental exploration** - Build understanding step by step
2. **Document discoveries** - Save useful patterns for reuse
3. **Create utility functions** - Abstract common operations
4. **Share with team** - Build organizational knowledge base

## Common Troubleshooting

### Connection Issues

```clojure
;; Test nREPL connection
(python3 ./mcp_nrepl_client.py --status --quiet)

;; Verify Basilisp is available
(python/sys.modules.get "basilisp.core" "Not loaded")

;; Check Python interop
(python/type [1 2 3])  ; Should return <class 'list'>
```

### Import Problems

```clojure
;; Check if module is importable
(try 
  (python/importlib.import-module "myapp.models")
  (catch Exception e (str "Import failed: " e)))

;; List available modules
(->> (python/sys.modules.keys) 
     (filter #(.startswith % "myapp"))
     sort)
```

### Access Errors

```clojure
;; Safe attribute access
(defn safe-getattr [obj attr default]
  (if (python/hasattr obj attr)
    (python/getattr obj attr)
    default))

;; Usage
(safe-getattr python/app "config" "No config found")
```

## System Health Monitoring

### Comprehensive Health Checks

Use the health check tool to diagnose system issues:

```bash
# Full system diagnostic
python3 ./mcp_nrepl_client.py --tool nrepl-health-check

# Quick health check (skip performance tests)
python3 ./mcp_nrepl_client.py --tool nrepl-health-check --args '{"include_performance": false}'

# Detailed verbose output for debugging
python3 ./mcp_nrepl_client.py --tool nrepl-health-check --args '{"verbose": true, "include_performance": true}'
```

### Understanding Health Check Results for Python Applications

The health check provides diagnostics across 6 categories:

1. **🔧 Environment Diagnostics**
   - Python version, system info, memory usage
   - JVM details (if applicable), available libraries

2. **🔌 Connection Health** 
   - nREPL server connectivity and response times
   - Available operations and protocol version

3. **⚙️ Core Functionality**
   - Basic evaluation, Python interop, data structures
   - String operations and mathematical functions

4. **🔗 Tool Integration**
   - Advanced nREPL operations (doc, source, completion)
   - Session management and introspection capabilities

5. **⚡ Performance Benchmarks**
   - Evaluation speed across multiple iterations
   - Complex computation performance

6. **🛠️ Configuration Status**
   - Server settings, debug mode, operational parameters

### Interpreting Health Results

```bash
# Example health check interpretation for Python apps:
# ✅ Connection Health: nREPL server responding normally
# ✅ Environment: Full Basilisp environment with Python interop
# ✅ Core Functionality: All basic operations working
# ✅ Tool Integration: All introspection tools available
# ✅ Performance: Meeting performance benchmarks  
# ✅ Configuration: Optimal server configuration
```

### Using Health Check for Python Troubleshooting

**Performance Issues:**
```bash
# Focus on performance diagnostics
python3 ./mcp_nrepl_client.py --tool nrepl-health-check --args '{"include_performance": true}'
# Look for: Performance Benchmarks section results
```

**Connection Problems:**
```bash
# Quick connection test
python3 ./mcp_nrepl_client.py --tool nrepl-health-check --args '{"include_performance": false}'
# Look for: Connection Health section status
```

**Python Interop Issues:**
```bash
# Detailed environment analysis
python3 ./mcp_nrepl_client.py --tool nrepl-health-check --args '{"verbose": true}'
# Look for: Environment Diagnostics and Core Functionality sections
```

## Future Enhancements

### Recently Implemented nREPL Tools ✅

- ✅ `nrepl-doc` - Get documentation for symbols and functions
- ✅ `nrepl-source` - View source code for introspection functions  
- ✅ `nrepl-apropos` - Discover functions by pattern matching
- ✅ `nrepl-complete` - Auto-completion for Python objects and functions
- ✅ `nrepl-require` - Load namespaces and utilities dynamically
- ✅ `nrepl-stacktrace` - Debug errors with detailed stack traces
- ✅ `nrepl-interrupt` - Stop long-running introspection operations
- ✅ `nrepl-health-check` - Comprehensive system diagnostics with 6 categories

### Potential Future nREPL Tools

- `python-inspect-object` - Enhanced deep object introspection with type analysis
- `python-call-function` - Safe function execution with sandboxing
- `python-health-report` - Comprehensive system status with alerting
- `python-performance-profile` - Real-time performance analysis and profiling
- `python-memory-analyzer` - Advanced memory usage breakdown and leak detection

### Integration Ideas

- **VS Code extension** - Python introspection from editor
- **Dashboard creation** - Real-time monitoring views  
- **Alert systems** - Threshold-based notifications
- **Log analysis** - Pattern detection and insights

## Conclusion

This cookbook demonstrates the power of combining **Clojure's functional paradigm** with **Python's runtime introspection** through our unified MCP-nREPL architecture. The result is a uniquely powerful debugging and monitoring experience that leverages the best of both worlds.

The patterns shown here represent just the beginning - the true power comes from combining these building blocks to create sophisticated introspection and debugging workflows tailored to your specific Python applications.

---

*Remember: Always test introspection patterns in development environments before applying them to production systems. The goal is insight and understanding, not disruption of running applications.*