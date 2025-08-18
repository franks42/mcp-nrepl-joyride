# Babashka SCI Observations and Workarounds

This document captures important insights about working with SCI (Small Clojure Interpreter) within Babashka, discovered while building the MCP-nREPL bridge.

## What is SCI?

**SCI** (Small Clojure Interpreter) is Babashka's sandboxed Clojure interpreter. It's designed to be:
- **Secure** - Prevent code from accessing/modifying the host environment
- **Controlled** - Limit what code can do
- **Fast** - Subset of Clojure with quick startup

## SCI Namespace Restrictions

### 1. **No Dynamic Namespace Switching**
```clojure
;; ❌ These don't work in SCI:
(in-ns 'some.namespace)
(binding [*ns* other-namespace] ...)

;; ✅ This works:
*ns*  ; => #sci.lang.Namespace user (but read-only)
```

**Why**: SCI wants to prevent code from "escaping" its sandbox by switching to unrestricted namespaces.

### 2. **Limited `require` Capabilities**
```clojure
;; ❌ Can't require arbitrary namespaces:
(require 'java.io)
(require 'clojure.java.shell)

;; ✅ Can only require pre-configured allowed namespaces:
(require 'clojure.string)  ; If allowed in SCI config
```

**Why**: Prevents access to dangerous Java classes and system functionality.

### 3. **Private Var Access Restrictions**
```clojure
;; ❌ Can't directly access private vars:
(in-ns 'target.namespace)
private-var

;; ✅ But var reader macro still works:
@#'target.namespace/private-var
```

**Why**: Maintains encapsulation, but the var reader macro is harder to restrict.

### 4. **No Namespace Creation**
```clojure
;; ❌ Can't create new namespaces:
(create-ns 'my.new.namespace)

;; ✅ Stuck in the default namespace (usually 'user)
```

**Why**: Prevents namespace pollution and maintains control.

## Why These Restrictions Exist

### Security Concerns
```clojure
;; SCI prevents this kind of dangerous code:
(in-ns 'java.lang.System)
(exit 0)  ; Could crash the host process

;; Or this:
(require 'clojure.java.shell)
(sh "rm -rf /")  ; Could delete files
```

### Sandboxing
SCI is often used in contexts where **untrusted code** might run:
- **Web applications** - User-submitted code
- **Configuration files** - DSLs that shouldn't access system
- **Scripting** - Limited execution environments

### Performance
- **Smaller surface area** = faster startup
- **Fewer namespaces** = less memory usage
- **Controlled evaluation** = predictable performance

## How to Bypass SCI Restrictions

### 1. **Var Reader Macro Approach**
```clojure
;; SCI can't prevent this:
@@#'mcp-nrepl-proxy.state/message-queues

;; Because:
;; - #' (var reader) is a fundamental language feature
;; - @ (deref) is also fundamental
;; - SCI would break too much Clojure code if it blocked these
```

### 2. **Helper Functions Pattern**
```clojure
;; Create functions that encapsulate the access:
(def msg-queues (fn [] @@#'mcp-nrepl-proxy.state/message-queues))
(def server-state (fn [] @mcp-nrepl-proxy.core/state))

;; Now we can call simply:
(msg-queues)
(server-state)
```

### 3. **Pre-loaded Namespaces**
```clojure
;; The MCP server already loaded all namespaces when it started
;; So we can access them via fully-qualified names:
@mcp-nrepl-proxy.core/state
(mcp-nrepl-proxy.utils/log nil :info "message")
```

### 4. **Persistent Function Environment**
```clojure
;; Define helper functions once, use many times:
(def debug-summary 
  (fn [] 
    {:server-keys (keys @mcp-nrepl-proxy.core/state)
     :queue-keys (keys @@#'mcp-nrepl-proxy.state/message-queues)}))

;; Functions persist across local-eval calls
(debug-summary)  ; Works in subsequent calls
```

## The Irony of SCI Restrictions

**SCI's namespace restrictions are easily circumvented** if you:
1. Know the var reader macro syntax (`#'`)
2. Know the target namespace names  
3. Can define functions

This suggests the restrictions are more about:
- **Preventing accidental access** rather than determined circumvention
- **Making dangerous operations explicit** rather than impossible
- **Providing a safety net** for casual users rather than security

## What Runs on SCI vs JVM Clojure in Our Project

### ✅ **SCI (Babashka's interpreter):**
- **The MCP server itself** - `bb src/mcp_nrepl_proxy/core.clj`
- **All our namespace files** - config.clj, utils.clj, tools/*.clj, etc.
- **local-eval code** - Code executed via the local-eval tool
- **debug-toolkit.clj** - When loaded via local-load-file

### ✅ **Full JVM Clojure:**
- **External nREPL servers** - The ones we connect TO (Joyride, CIDER, etc.)
- **Our test nREPL server** - `./nrepl-test start` (full Clojure REPL)
- **Any traditional Clojure project** we might connect to

## Architecture Split

```
┌─────────────────────────────────────────┐
│           MCP Server (SCI)              │
│  ┌─────────────────────────────────┐    │
│  │  local-eval executes in SCI     │    │
│  │  - Limited namespace switching  │    │
│  │  - Can bypass with var reader   │    │
│  └─────────────────────────────────┘    │
│  ┌─────────────────────────────────┐    │
│  │  All our .clj files run in SCI │    │
│  │  - config.clj, tools/*.clj     │    │
│  │  - Subject to SCI restrictions │    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
                   │
                   │ nREPL protocol
                   ▼
┌─────────────────────────────────────────┐
│      External nREPL Server (JVM)       │
│  ┌─────────────────────────────────┐    │
│  │  Full Clojure capabilities      │    │
│  │  - Complete namespace control   │    │
│  │  - All Java interop            │    │
│  │  - No SCI restrictions         │    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
```

## Practical Guidelines

### When Writing MCP Server Code:
```clojure
;; ❌ Don't try this in our server files:
(in-ns 'other.namespace)

;; ✅ Do this instead:
(other.namespace/some-function)
```

### When Sending Code to nREPL:
```clojure
;; ✅ This is fine - runs on target JVM:
(nrepl/eval conn "(in-ns 'user)")
(nrepl/eval conn "(require 'java.io)")
```

### When Using local-eval:
```clojure
;; ❌ This runs in SCI, so restricted:
(local-eval "(in-ns 'some.namespace)")

;; ✅ This works - using var reader:
(local-eval "@@#'some.namespace/some-var")

;; ✅ Even better - use helper functions:
(local-eval "(def helper (fn [] @@#'some.namespace/some-var))")
(local-eval "(helper)")
```

## Discovered Patterns for SCI

### 1. **Debug Toolkit Pattern**
Create a comprehensive toolkit file that can be loaded once:

```clojure
;; debug-toolkit.clj
(def server-state (fn [] @mcp-nrepl-proxy.core/state))
(def msg-queues (fn [] @@#'mcp-nrepl-proxy.state/message-queues))
(def debug-summary (fn [] {:keys (keys (server-state)) :queues (keys (msg-queues))}))

;; Shortcuts
(def ds debug-summary)
(def ss server-state)
```

### 2. **Var Reader Circumvention**
```clojure
;; For private atoms:
@@#'namespace/private-atom

;; For private functions:
(#'namespace/private-function args)

;; For public vars:
@namespace/public-var
```

### 3. **Persistent Function Environment**
```clojure
;; Define once, use many times across local-eval calls
(def analysis-fn 
  (fn [] 
    ;; Complex analysis using multiple namespace accesses
    {:state-analysis ...
     :queue-analysis ...}))
```

## Key Insights

1. **SCI restrictions are more guidelines than walls** - Determined users can bypass most limitations

2. **The var reader macro is SCI's Achilles heel** - It provides backdoor access to everything

3. **Helper functions are the key** - Wrap complex access patterns in simple function calls

4. **Persistence is powerful** - Functions defined in SCI context remain available across calls

5. **Know your execution context** - Our MCP server runs on SCI, but target nREPL servers run full Clojure

6. **Embrace the split** - Use SCI's limitations as design constraints, not obstacles

## Why This Matters for MCP-nREPL

In our project, SCI restrictions are actually **more annoying than helpful** because:
- We **control the code** being executed (our debugging toolkit)
- We **want** to access internal state (that's the point!)
- We're **not running untrusted code** (we're debugging our own server)

But understanding how to work around them efficiently has led to some elegant solutions like the debug toolkit pattern, which provides a clean API for server introspection despite the underlying restrictions.

## Future Considerations

The ease of bypassing SCI restrictions suggests that:
1. **Security by obscurity** - Many users won't know these workarounds
2. **Developer experience vs security** - SCI prioritizes usability over absolute security
3. **Trust boundaries** - SCI assumes you trust the code you're running, just not what it can access
4. **Architectural insight** - The debug tools' success without state parameter injection hints at cleaner design patterns for the upcoming refactoring

The fact that our debug tools work better WITHOUT the complex state parameter passing used throughout the rest of the codebase suggests that much of that complexity is unnecessary ceremony that could be eliminated.