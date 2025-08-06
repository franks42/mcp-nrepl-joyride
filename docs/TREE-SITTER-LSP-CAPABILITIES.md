# Tree-sitter and LSP Capabilities for Claude Code

## Current Situation

### Tree-sitter MCP Tools Available NOW
Claude Code has access to powerful tree-sitter tools via MCP that provide semantic code understanding:

#### Supported Languages (20+)
- **Clojure** ✅ (parser available, but no query templates yet)
- **Python** ✅ (full support with templates)
- **JavaScript/TypeScript** ✅
- **Java, Go, Rust, Ruby** ✅
- And many more...

#### Available Tree-sitter Tools
1. **mcp__tree_sitter__get_ast** - Parse and navigate syntax trees
2. **mcp__tree_sitter__get_symbols** - Extract functions, classes, variables
3. **mcp__tree_sitter__find_usage** - Find where symbols are used
4. **mcp__tree_sitter__run_query** - Custom AST queries
5. **mcp__tree_sitter__analyze_complexity** - Code complexity metrics
6. **mcp__tree_sitter__get_dependencies** - Find imports/requires
7. **mcp__tree_sitter__find_similar_code** - Pattern matching

#### Python Example (Working Now)
```python
# Can find all functions in a file
mcp__tree_sitter__get_symbols(
    project="mcp-nrepl-joyride",
    file_path="mcp_nrepl_client.py",
    symbol_types=["functions", "classes"]
)
# Returns: List of all functions with locations

# Can find all usages of a symbol
mcp__tree_sitter__find_usage(
    project="mcp-nrepl-joyride",
    symbol="eval_code",
    language="python"
)
# Returns: All locations where eval_code is referenced
```

#### Clojure Support Status
- **Parser**: ✅ Available and working
- **Query Templates**: ❌ Not yet defined
- **Potential**: Can parse Clojure AST, just needs templates

### What Tree-sitter Provides vs LSP

| Feature | Tree-sitter (Have Now) | LSP (Don't Have) | 
|---------|------------------------|------------------|
| **Parse syntax** | ✅ Full AST | ✅ Full AST |
| **Find symbols** | ✅ With templates | ✅ Better |
| **Find usages** | ✅ File-level | ✅ Project-wide |
| **Go to definition** | ⚠️ Limited | ✅ Full |
| **Rename** | ❌ | ✅ |
| **Type information** | ❌ | ✅ |
| **Completion** | ❌ | ✅ |
| **Diagnostics** | ❌ | ✅ |
| **Quick fixes** | ❌ | ✅ |

### Tree-sitter Advantages
- **No server needed** - Direct parsing
- **Fast** - In-memory parsing
- **Language agnostic** - Same queries work across languages
- **Good enough** for many refactoring tasks

### LSP Advantages  
- **Semantic understanding** - Types, inheritance, etc.
- **Project-wide** - Cross-file references
- **IDE features** - Completion, diagnostics
- **Language-specific** - Understands language semantics

## Clojure Tree-sitter Templates Needed

To make Clojure tree-sitter useful, we need query templates:

```clojure
;; Functions
(defn name: (symbol) @function.name
      docstring?: (string) @function.doc
      params: (vector) @function.params
      body: (_) @function.body) @function.def

;; Namespaces  
(ns name: (symbol) @namespace.name
    requires: (list) @namespace.requires) @namespace

;; Vars
(def name: (symbol) @var.name
     value: (_) @var.value) @var.def
```

## LSP Integration Options for Claude Code

### Option 1: MCP Wrapper for LSP Servers
Create an MCP server that wraps language servers:
```
Claude Code → MCP → LSP Server → Code Analysis
```

Benefits:
- Access to any LSP server (clojure-lsp, pylsp, etc.)
- Full IDE-level capabilities
- Language agnostic

### Option 2: Direct LSP CLI Usage
Many LSP servers have CLI interfaces:
```bash
# clojure-lsp examples
clojure-lsp diagnostics
clojure-lsp references --file src/core.clj --line 10
clojure-lsp rename --from old-name --to new-name

# Could use via Bash tool
```

### Option 3: Enhance Tree-sitter Templates
Add comprehensive query templates for all languages:
- Cheaper than LSP (no server needed)
- Covers 80% of refactoring needs
- Already have the infrastructure

## Recommendations

### For MCP-nREPL Project
1. **Focus on nREPL proxy excellence** - Make it the best nREPL gateway
2. **Add priority nREPL tools**: load-file, interrupt, doc, source, complete
3. **Don't replicate LSP features** - Different problem domain

### For Claude Code Capabilities
1. **Short term**: Create Clojure tree-sitter templates
2. **Medium term**: Explore MCP-LSP wrapper for full LSP access
3. **Current workaround**: Use tree-sitter for Python/JS, basic grep for Clojure

### Tree-sitter Query Templates Priority
1. **Clojure** - Currently missing, high value for this project
2. **Enhanced Python** - Add more sophisticated queries
3. **TypeScript/JavaScript** - Common in many projects

## Conclusion

Claude Code already has significant code understanding capabilities through tree-sitter MCP tools. While not as powerful as full LSP, it's sufficient for many refactoring tasks. The main gap is Clojure query templates, which could be added relatively easily.

For the MCP-nREPL project, the focus should remain on being an excellent nREPL proxy rather than trying to replicate LSP features. The two protocols serve different purposes and both are valuable in their domains.