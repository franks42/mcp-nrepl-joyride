# nREPL MCP Design Decisions

## Individual Functions vs Generic Message Handler

### Background

The nREPL protocol is message-based where each operation is a map with an `:op` key and parameters. Our MCP server wraps these operations, presenting a design choice:

1. **Individual MCP functions** for each nREPL operation (current approach)
2. **Single generic function** that accepts raw nREPL messages
3. **Hybrid approach** with both options

### Analysis

#### Current Approach: Individual MCP Functions (16 tools)

```javascript
// What AI assistants use today:
nrepl-eval({code: "(+ 1 2)"})
nrepl-doc({symbol: "map"})
nrepl-apropos({query: "str"})
```

**Pros:**
- **Discoverability** - AI assistants see all available operations in tool list
- **Type safety** - Each function has specific parameter validation
- **Documentation** - Each tool has dedicated description and examples
- **Error messages** - Clear, specific errors like "Missing required parameter 'code'"
- **IDE/AI support** - Autocomplete and tool selection work naturally
- **Low cognitive load** - Users don't need to know nREPL protocol details
- **Curated UX** - We control which operations are exposed and how

**Cons:**
- **Maintenance overhead** - 16+ functions to maintain and document
- **Code duplication** - Similar message wrapping logic repeated
- **Limited flexibility** - Can't use new/custom nREPL ops without code changes
- **Deployment friction** - Adding new ops requires server updates
- **Incomplete protocol** - We only expose subset of nREPL capabilities

#### Alternative: Single Generic Function

```javascript
// What it would look like:
nrepl-message({op: "eval", code: "(+ 1 2)"})
nrepl-message({op: "doc", symbol: "map"})
nrepl-message({op: "apropos", query: "str"})
```

**Pros:**
- **Complete protocol access** - Any nREPL operation works immediately
- **Future-proof** - New nREPL features work without server updates
- **Minimal code** - One function to maintain
- **Power user friendly** - Direct protocol access for advanced users
- **Smaller codebase** - No duplication of wrapping logic

**Cons:**
- **Poor discoverability** - AI/users must know protocol operations
- **No parameter validation** - Errors only discovered at nREPL level
- **Documentation challenge** - Single massive doc for all operations
- **Steep learning curve** - Users must understand nREPL protocol
- **Generic errors** - "Invalid message" instead of specific guidance
- **AI confusion** - AI assistants won't know available operations

#### Recommended: Hybrid Approach

```javascript
// Common operations as dedicated functions (80% use cases)
nrepl-eval({code: "(+ 1 2)"})
nrepl-doc({symbol: "map"})
nrepl-apropos({query: "str"})

// Power user escape hatch for completeness
nrepl-raw({op: "macroexpand", form: "(when true :ok)"})
nrepl-raw({op: "custom-vendor-op", special: "params"})
```

**Benefits:**
- **Best of both worlds** - Convenience + completeness
- **Progressive disclosure** - Simple for beginners, powerful for experts
- **100% protocol coverage** - Never blocked by missing functions
- **Gradual promotion** - Can promote commonly-used raw ops to dedicated functions
- **AI-friendly** - Maintains discoverability for common operations

## AI Assistant Perspective

Since AI assistants are the **primary users** of MCP functions, their needs should drive our design:

### Why Individual Functions Win for AI

1. **Tool Discovery**
   - AI can see all available operations in tool list
   - No need to search documentation for protocol details
   - Clear function names indicate purpose

2. **Parameter Clarity**
   - Each function has typed, documented parameters
   - AI knows exactly what to pass without protocol knowledge
   - Validation happens before sending to nREPL

3. **Error Recovery**
   - Specific error messages help AI correct mistakes
   - "Missing required parameter 'code'" vs "Invalid nREPL message"
   - AI can learn from clear error patterns

4. **Tool Selection**
   - AI can choose right tool from list
   - Natural language mapping: "get documentation" → `nrepl-doc`
   - No need to construct protocol messages

### Usage Pattern Analysis

Based on observed AI usage:

**High-frequency operations (need dedicated functions):**
- `nrepl-eval` - Core functionality, used constantly
- `nrepl-doc` - Discovery and learning
- `nrepl-apropos` - Finding relevant functions
- `nrepl-health-check` - Environment validation

**Medium-frequency operations (nice to have):**
- `nrepl-complete` - Code completion
- `nrepl-source` - Understanding implementations
- `nrepl-require` - Loading dependencies
- `nrepl-stacktrace` - Debugging

**Low-frequency operations (could use raw):**
- `nrepl-interrupt` - Rarely needed
- Session management - Advanced use cases
- Custom operations - Vendor-specific

## Implementation Strategy

### Phase 1: Add nrepl-raw Function
```clojure
(defn- tool-nrepl-raw
  "Send raw nREPL message - complete protocol access
   For advanced users and operations not covered by convenience functions"
  [{:keys [message session]}]
  (let [conn (:nrepl-conn @state)]
    (if conn
      (nrepl/send-message conn message)
      {:error "Not connected to nREPL server"})))
```

### Phase 2: Re-examine Convenience Functions

Analyze actual usage to determine optimal function set:

1. **Metrics to collect:**
   - Function call frequency
   - Error rates per function
   - AI retry patterns
   - Time to successful completion

2. **Optimization criteria:**
   - Keep high-frequency, high-success functions
   - Combine related low-frequency functions
   - Add missing high-value operations
   - Remove unused functions

3. **Potential additions:**
   - `nrepl-inspect` - Combined doc + source + type info
   - `nrepl-explore` - Namespace exploration helper
   - `nrepl-debug` - Combined stacktrace + locals + context

4. **Potential removals:**
   - Functions with < 1% usage
   - Functions better served by nrepl-raw
   - Redundant operations

## Design Principles

1. **AI-First Design** - Optimize for AI assistant usage patterns
2. **Progressive Disclosure** - Simple common cases, powerful escape hatches
3. **Protocol Completeness** - Never block users from any nREPL capability
4. **Maintainable Balance** - Enough functions for UX, not so many for burden
5. **Data-Driven Decisions** - Use actual usage patterns to guide design

## Conclusion

The hybrid approach (convenience functions + nrepl-raw) provides the best balance:

- **UX wins** with dedicated functions for common operations
- **Completeness wins** with raw message access
- **AI assistants win** with discoverable, documented tools
- **Maintenance wins** with bounded function set
- **Evolution wins** with data-driven optimization

The overhead of maintaining individual functions is justified by the massive UX improvement for AI assistants, who cannot easily browse protocol documentation or construct proper message formats without guidance.

---

*This document captures the design rationale for our nREPL MCP function architecture and guides future development decisions.*