# MCP-nREPL Project TODO List

Last updated: 2025-08-10

## Critical Priority - Async Architecture Implementation

### Phase 1: Transport Layer Foundation
- [ ] **Implement send-message-async with timeout handling**
  - [ ] Create `send-message-async` function with timeout parameter (no default)
  - [ ] Implement async response collection with promise-based handling
  - [ ] Add basic connection state management
  - [ ] Write unit tests for timeout scenarios
  - [ ] Test: timeout cleanup, concurrent messaging, error reporting
  - **Commit**: `feat: implement send-message-async with timeout handling`
  - **Tag**: `v0.x.0-async-transport`

### Phase 2: Internal Layer Implementation
- [ ] **Create nrepl-raw-async-int as pure nREPL interface**
  - [ ] Implement `nrepl-raw-async-int` using `send-message-async`
  - [ ] Add message validation and error handling
  - [ ] Standardize response format
  - [ ] Write integration tests with real nREPL server
  - [ ] Test: all nREPL operations, error messages, performance parity
  - **Commit**: `feat: implement nrepl-raw-async-int foundation` 
  - **Tag**: `v0.x.0-async-internal`

### Phase 3: MCP Layer Implementation  
- [ ] **Create nrepl-raw-async MCP function with full protocol compliance**
  - [ ] Implement `nrepl-raw-async` MCP function with validation
  - [ ] Add optional timeout_ms parameter (default 30000ms) for AI control
  - [ ] Add MCP-compliant error formatting
  - [ ] Comprehensive parameter validation
  - [ ] Write MCP integration tests
  - [ ] Test: MCP protocol compliance, timeout parameter flow, error formatting
  - **Commit**: `feat: implement nrepl-raw-async MCP function`
  - **Tag**: `v0.x.0-async-mcp`

### Phase 4: Migration and Testing
- [ ] **Demonstrate backward compatibility and performance**
  - [ ] Create side-by-side comparison tests
  - [ ] Run performance benchmarks
  - [ ] Document migration strategy and rollback procedures
  - [ ] Test: existing functions work, equivalent functionality, performance <10% overhead
  - **Commit**: `feat: complete async architecture with migration path`
  - **Tag**: `v0.x.0-async-complete`

## High Priority

## Medium Priority

- [ ] **Add nrepl-session-clone tool** for creating parallel evaluation contexts
- [ ] **Improve error handling** with better context and suggestions
- [ ] **Add file watching capability** to auto-reload changed Clojure files
- [ ] **Add bulk operation support** for loading multiple files at once

## Lower Priority

- [ ] **Add logging and monitoring dashboard**
- [ ] **Create deployment script for production**
- [ ] **Add configuration management system**
- [ ] **Investigate git MCP server applicability** for enhanced local git workflow integration

## Future Considerations (Low Priority)

- [ ] **Consider daemon-based persistent MCP connections**
- [ ] **Consider unified MCP/nREPL client with direct nREPL support**

---

## Recently Completed ✅

- [x] **Test Python linters to determine which detect blank lines between decorators and functions**
- [x] **Create reusable quality scripts for Python and Clojure instead of one-off CLI commands**
- [x] **Add cljfmt code formatting to project - configure and document workflow**
- [x] **Create comprehensive sync-async queuing architecture design document**
- [x] **Add nrepl-apropos tool for symbol discovery and exploration**
- [x] **Add nrepl-stacktrace tool for better error debugging**
- [x] **Create AI Assistant MCP Function Cookbook for Claude and other AI agents**
- [x] **Create comprehensive VS Code Joyride MCP cookbook for human developers and AI assistants**
- [x] **Implement additional nREPL tools: interrupt, doc, source, complete**
- [x] **Implement nrepl-load-file tool for loading Clojure source files**
- [x] **Add nrepl-require tool for dynamically loading namespaces/libraries**

---

*This TODO list is managed through the TodoWrite tool and should be updated whenever tasks are started, completed, or priorities change.*