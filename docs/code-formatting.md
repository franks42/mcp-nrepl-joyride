# Code Formatting Guide

## Overview

This project uses **cljfmt** for consistent Clojure code formatting. Unlike clj-kondo (which only lints), cljfmt actually reformats code to match style guidelines.

## Quick Start

```bash
# Format all Clojure files
./format.sh

# Check formatting without changing files
./format.sh check

# Format specific file manually
bb -Sdeps '{:deps {cljfmt/cljfmt {:mvn/version "0.9.2"}}}' \
   -M -e "(require '[cljfmt.main]) (cljfmt.main/-main \"fix\" \"src/mcp_nrepl_proxy/core.clj\")"
```

## Configuration

Settings are in `.cljfmt.edn`:

```clojure
{:remove-surrounding-whitespace? true   ; Clean up extra spaces
 :remove-trailing-whitespace? true      ; Remove end-of-line spaces
 :remove-consecutive-blank-lines? true  ; Max 1 blank line
 :insert-missing-whitespace? true       ; Add spaces where needed
 :align-associative? false              ; Don't vertically align maps
 :indents {...}                         ; Custom indentation rules
 :extra-indents {...}}                  ; Project-specific indents
```

## Development Workflow

### 1. Before Every Commit
```bash
# Run formatter
./format.sh

# Run linter
clj-kondo --lint src/

# Both clean? Commit!
git add . && git commit -m "..."
```

### 2. VS Code / Calva Integration

Calva uses cljfmt automatically. Configure in `.vscode/settings.json`:

```json
{
  "calva.fmt.configPath": ".cljfmt.edn",
  "calva.fmt.formatAsYouType": true,
  "editor.formatOnSave": true
}
```

### 3. Git Pre-commit Hook (Optional)

Create `.git/hooks/pre-commit`:

```bash
#!/bin/bash
echo "🎨 Formatting code..."
./format.sh

echo "🔍 Linting code..."
clj-kondo --lint src/

if [ $? -ne 0 ]; then
    echo "❌ Fix linting errors before committing"
    exit 1
fi

echo "✅ Code quality checks passed!"
```

## Tool Comparison

| Tool | Purpose | Auto-fix? | Config File |
|------|---------|-----------|-------------|
| **cljfmt** | Formatting | ✅ Yes | `.cljfmt.edn` |
| **clj-kondo** | Linting | ❌ No | `.clj-kondo/config.edn` |
| **zprint** | Formatting | ✅ Yes | `.zprintrc` |
| **cljstyle** | Both | ✅ Yes | `.cljstyle` |

## Why cljfmt?

1. **Community Standard** - Most popular formatter
2. **Editor Support** - Works with Calva, Cursive, CIDER
3. **Babashka Compatible** - Runs without JVM startup
4. **Configurable** - Flexible indentation rules
5. **Fast** - Minimal overhead in development

## Common Formatting Rules

### Indentation
```clojure
;; GOOD - cljfmt standard
(defn example
  [x y]
  (let [sum (+ x y)]
    (if (> sum 10)
      :big
      :small)))

;; BAD - inconsistent indentation
(defn example
[x y]
(let [sum (+ x y)]
(if (> sum 10)
:big
:small)))
```

### Whitespace
```clojure
;; GOOD - proper spacing
{:name "test" :value 42}
(+ 1 2 3)

;; BAD - missing spaces
{:name"test":value 42}
(+1 2 3)
```

### Line Breaks
```clojure
;; GOOD - logical grouping
(defn process
  [data]
  (let [cleaned (clean data)
        validated (validate cleaned)]
    
    (when validated
      (save validated))))

;; BAD - too many blank lines
(defn process
  [data]


  (let [cleaned (clean data)
  
  
        validated (validate cleaned)]


    (when validated
    
      (save validated))))
```

## Troubleshooting

### cljfmt not found
```bash
# Install Babashka (recommended)
brew install babashka

# Or use Clojure CLI
brew install clojure/tools/clojure
```

### Format conflicts with editor
- Ensure editor uses same `.cljfmt.edn` config
- Disable editor formatting if using script
- Check cljfmt version compatibility

### Large files slow to format
```bash
# Format only changed files
git diff --name-only | grep '\.clj$' | xargs -I {} \
  bb -Sdeps '{:deps {cljfmt/cljfmt {:mvn/version "0.9.2"}}}' \
     -M -e "(require '[cljfmt.main]) (cljfmt.main/-main \"fix\" \"{}\")"
```

## Integration with AI Development

When working with AI assistants (Claude, GitHub Copilot), consistent formatting:
1. **Improves code analysis** - Consistent AST structure
2. **Reduces noise** - Focus on logic, not style
3. **Better suggestions** - AI learns from consistent patterns
4. **Easier reviews** - Humans and AI see same style

## Best Practices

1. **Format Early, Format Often** - Don't let formatting debt accumulate
2. **Team Agreement** - All contributors use same config
3. **CI Integration** - Fail builds on formatting issues
4. **Document Exceptions** - Some macros need custom indents
5. **Version Control Config** - `.cljfmt.edn` in repository

## References

- [cljfmt Documentation](https://github.com/weavejester/cljfmt)
- [Clojure Style Guide](https://guide.clojure.style/)
- [Community Formatting Standards](https://clojureverse.org/t/formatting-standards)