# Clojure Tree-sitter Query Templates

## Understanding Clojure's Tree-sitter AST

The Clojure tree-sitter grammar (sogaiu/tree-sitter-clojure) takes a **minimalist approach** due to Clojure's homoiconic nature. It doesn't try to identify semantic elements like "functions" or "namespaces" because these can be redefined by macros.

### Core Node Types

From analyzing the AST, Clojure's tree-sitter grammar uses these primary node types:

- **`list_lit`** - Lists `(...)` 
- **`vec_lit`** - Vectors `[...]`
- **`map_lit`** - Maps `{...}`
- **`sym_lit`** - Symbols with child `sym_name`
- **`kwd_lit`** - Keywords `:keyword`
- **`str_lit`** - Strings `"..."`
- **`num_lit`** - Numbers
- **`bool_lit`** - Booleans `true/false`
- **`nil_lit`** - Nil value

### AST Example

For `(defn hello [name] (str "Hello" name))`:

```
list_lit
  ├── "("
  ├── sym_lit (defn)
  │   └── sym_name: "defn"
  ├── sym_lit (hello)
  │   └── sym_name: "hello"
  ├── vec_lit
  │   ├── "["
  │   ├── sym_lit (name)
  │   └── "]"
  └── list_lit
      ├── sym_lit (str)
      ├── str_lit ("Hello")
      └── sym_lit (name)
```

## Useful Query Templates for Clojure

Since tree-sitter-clojure doesn't have built-in templates, here are practical patterns:

### 1. Find Function Definitions (defn)

```scm
;; Match (defn function-name ...)
(list_lit
  (sym_lit (sym_name) @fn-type)
  (sym_lit (sym_name) @fn-name)
  (#eq? @fn-type "defn")) @function.def
```

### 2. Find Namespace Declarations

```scm
;; Match (ns namespace.name ...)
(list_lit
  (sym_lit (sym_name) @ns-type)
  (sym_lit (sym_name) @ns-name)
  (#eq? @ns-type "ns")) @namespace.def
```

### 3. Find Variable Definitions (def)

```scm
;; Match (def var-name value)
(list_lit
  (sym_lit (sym_name) @def-type)
  (sym_lit (sym_name) @var-name)
  (#eq? @def-type "def")) @variable.def
```

### 4. Find Macro Definitions

```scm
;; Match (defmacro macro-name ...)
(list_lit
  (sym_lit (sym_name) @macro-type)
  (sym_lit (sym_name) @macro-name)
  (#eq? @macro-type "defmacro")) @macro.def
```

### 5. Find Require Statements

```scm
;; Match (:require [namespace :as alias])
(list_lit
  (kwd_lit) @require-kw
  (vec_lit
    (sym_lit (sym_name) @required-ns))
  (#eq? @require-kw ":require")) @require.statement
```

### 6. Find Function Calls

```scm
;; Match any list starting with a symbol (potential function call)
(list_lit
  (sym_lit (sym_name) @fn-call)) @call.expression
```

### 7. Find Test Functions

```scm
;; Match (deftest test-name ...)
(list_lit
  (sym_lit (sym_name) @test-type)
  (sym_lit (sym_name) @test-name)
  (#eq? @test-type "deftest")) @test.def
```

### 8. Find Let Bindings

```scm
;; Match (let [bindings...] body)
(list_lit
  (sym_lit (sym_name) @let-type)
  (vec_lit) @bindings
  (#eq? @let-type "let")) @let.expression
```

### 9. Find Map Literals

```scm
;; Match {:key value ...}
(map_lit
  (kwd_lit) @map.key
  (_) @map.value) @map.entry
```

### 10. Find Anonymous Functions

```scm
;; Match (fn [...] body) or #(...)
(list_lit
  (sym_lit (sym_name) @fn-type)
  (vec_lit) @params
  (#eq? @fn-type "fn")) @anon-fn
```

## Limitations and Considerations

### Why Clojure Tree-sitter is Different

1. **No Semantic Understanding**: The grammar only understands syntax, not semantics
2. **Macro Problem**: Users can define macros that look like functions
3. **Dynamic Nature**: Clojure can redefine anything at runtime
4. **False Positives**: A list starting with `defn` might not be a function definition

### When to Use Tree-sitter vs clojure-lsp

**Use Tree-sitter for:**
- Quick syntactic analysis
- Finding patterns in code
- Syntax highlighting
- Basic refactoring (rename symbols)

**Use clojure-lsp for:**
- Semantic understanding
- Type information
- Cross-file references
- Accurate go-to-definition
- Understanding macro expansions

## Implementing These Queries

To use these queries with the MCP tree-sitter tools:

```python
# Example: Find all function definitions
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

## Future Enhancement Ideas

1. **Create MCP tool**: `register_clojure_templates` to add these as built-in templates
2. **Build query library**: Common Clojure patterns for tree-sitter
3. **Combine with nREPL**: Use tree-sitter for static analysis, nREPL for runtime

## Conclusion

While Clojure's tree-sitter grammar is intentionally minimal, it's still useful for:
- Pattern matching in code
- Basic structural navigation
- Syntax highlighting
- Finding common forms

For deeper semantic understanding, combine with clojure-lsp or nREPL runtime analysis.