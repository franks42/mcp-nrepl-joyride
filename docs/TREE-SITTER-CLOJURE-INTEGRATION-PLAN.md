# Tree-sitter Clojure Integration Plan

## Current Situation Analysis

### What Works Now
- **MCP tree-sitter server available**: Claude has access via `mcp__tree_sitter__*` tools
- **Clojure parser available**: Listed in `mcp__tree_sitter__list_languages()` 
- **Basic parsing works**: Can parse Clojure files into AST successfully
- **Custom queries possible**: Can write and run tree-sitter queries

### What's Missing
- **No Clojure query templates**: `mcp__tree_sitter__get_symbols()` fails for Clojure
- **No semantic understanding**: Can't find functions, namespaces, vars automatically
- **No built-in patterns**: Unlike Python/JS which have full template support

### The Discovery
Found that **nvim-treesitter has sophisticated Clojure templates** that provide semantic understanding beyond basic parsing.

## Research Findings

### 1. Clojure Tree-sitter Grammar (sogaiu/tree-sitter-clojure)
- **Minimalist approach**: Only provides syntactic nodes, not semantic ones
- **Philosophy**: Avoids semantic interpretation due to Clojure's homoiconic nature
- **Node types**: `list_lit`, `sym_lit`, `vec_lit`, `map_lit`, etc.
- **Limitation**: No built-in concept of "function" or "namespace"

### 2. nvim-treesitter Clojure Templates
**Location**: `https://github.com/nvim-treesitter/nvim-treesitter/tree/master/queries/clojure`

**Available files**:
- `highlights.scm` ✅ - Sophisticated semantic patterns
- `locals.scm` ❌ - Just placeholder
- `folds.scm` ✅ - Simple folding: `(source (list_lit) @fold)`
- `injections.scm` ✅ - Language injection support

**Key patterns from highlights.scm**:
```scheme
;; Function definitions
(list_lit
 .
 ((sym_lit name: (sym_name) @_keyword.function.name)
  (#any-of? @_keyword.function.name "defn" "defn-" "fn" "fn*"))
 .
 (sym_lit)? @function)

;; Namespace declarations  
(list_lit
 .
 (sym_lit) @_include
 (#eq? @_include "ns")
 .
 (sym_lit) @module)

;; Built-in vars
;; Method calls, interop, etc.
```

### 3. MCP Tree-sitter Server Architecture
- **Has template infrastructure**: Works for Python, JS, Go, Rust, etc.
- **Supports Clojure parsing**: Via tree-sitter-language-pack
- **Missing Clojure templates**: No one has added them yet
- **Template format**: Uses specific structure for `functions`, `classes`, `imports` etc.

## The Solution Path

### Immediate Action: Custom Queries
While templates don't exist, can write custom tree-sitter queries:

```python
# Example: Find function definitions
query = '''
(list_lit
  (sym_lit (sym_name) @fn-type)
  (sym_lit (sym_name) @fn-name)
  (#eq? @fn-type "defn")) @function.def
'''

result = mcp__tree_sitter__run_query(
    project="mcp-nrepl-joyride",
    query=query,
    language="clojure"
)
```

### Long-term Solution: Add Templates

**Step 1: Access Local MCP Tree-sitter Repository**
- User has local tree-sitter repo where MCP server runs
- Need to examine structure and template format
- Understand how existing templates are organized

**Step 2: Adapt nvim-treesitter Patterns**
- Convert nvim-treesitter `highlights.scm` patterns
- Transform to MCP server template format
- Create templates for: `functions`, `namespaces`, `vars`, `imports`

**Step 3: Integration**
- Add Clojure templates to local MCP server
- Test with existing MCP tools
- Contribute back to wrale/mcp-server-tree-sitter

## Template Conversion Strategy

### From nvim-treesitter to MCP Format

**nvim-treesitter pattern**:
```scheme
(list_lit
 .
 ((sym_lit name: (sym_name) @_keyword.function.name)
  (#any-of? @_keyword.function.name "defn" "defn-" "fn" "fn*"))
 .
 (sym_lit)? @function)
```

**Target MCP template format** (based on Python example):
```python
"functions": '''
    (list_lit
      (sym_lit (sym_name) @function.type)
      (sym_lit (sym_name) @function.name)
      (#any-of? @function.type "defn" "defn-" "fn" "fn*")) @function.def
'''
```

### Required Templates for Clojure

1. **functions** - defn, defn-, fn, fn*, defmacro
2. **namespaces** - ns declarations
3. **vars** - def, defonce
4. **imports** - :require, :use, :import in ns forms
5. **tests** - deftest (if using clojure.test)

## Expected Benefits

### For Claude Code
- **Semantic understanding**: Find functions, namespaces automatically
- **Better refactoring**: Use tree-sitter for Clojure code analysis
- **Consistent interface**: Same tools work for Clojure as Python/JS

### For MCP-nREPL Project
- **Enhanced analysis**: Combine static (tree-sitter) + dynamic (nREPL)
- **Better tooling**: Understand code structure without running it
- **IDE-like features**: Navigation, symbol search, etc.

## Implementation Checklist

- [ ] Access user's local MCP tree-sitter repository
- [ ] Analyze existing template structure and format
- [ ] Extract nvim-treesitter Clojure patterns
- [ ] Convert patterns to MCP template format
- [ ] Add Clojure templates to local server
- [ ] Test with `mcp__tree_sitter__get_symbols()`
- [ ] Verify all template types work
- [ ] Document usage and patterns
- [ ] Consider contributing back to upstream

## Timeline Priority

**High Priority**: Focus on enhancing MCP-nREPL server first
**Medium Priority**: Add Clojure tree-sitter templates after nREPL tools
**Low Priority**: Contribute back to upstream projects

## Success Criteria

Templates working when:
```python
# This should work for Clojure
symbols = mcp__tree_sitter__get_symbols(
    project="mcp-nrepl-joyride",
    file_path="src/core.clj",
    symbol_types=["functions", "namespaces", "vars"]
)
# Returns: List of all Clojure symbols with locations
```

## Notes for Future Implementation

- nvim-treesitter patterns are sophisticated and battle-tested
- MCP server has robust template infrastructure
- Integration should be straightforward with user's local repo access
- Will dramatically improve Claude's Clojure code understanding
- Complements nREPL's runtime capabilities with static analysis