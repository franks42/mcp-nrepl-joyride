# Clojure Evaluation: Input/Output/Error Stream Processing

This document explains how the MCP-nREPL bridge handles the three distinct output streams from Clojure evaluation and makes them available through JSON response fields.

## Overview

When evaluating Clojure code, there are **three separate output streams** that need to be captured and processed independently:

1. **Standard Output (stdout)** - From `println`, `print`, etc.
2. **Standard Error (stderr)** - Error messages, stack traces, warnings  
3. **Evaluation Result** - The actual return value of the expression

Each stream is captured separately and returned in distinct JSON fields, allowing precise control over which output you want to access.

## Tool-Specific Field Mappings

### nrepl-eval Tool

The `nrepl-eval` tool uses nREPL protocol field names:

| Output Type | JSON Field | Description | Example |
|-------------|------------|-------------|---------|
| **stdout** | `out` | Standard output capture | `(println "hello")` → `"hello\n"` |
| **stderr** | `err` | Standard error capture | Stack traces, warnings |
| **eval-result** | `value` | Return value of expression | `(+ 1 2 3)` → `"6"` |

### local-eval Tool

The `local-eval` tool uses more descriptive field names:

| Output Type | JSON Field | Description | Example |
|-------------|------------|-------------|---------|
| **stdout** | `stdout` | Standard output capture | `(println "hello")` → `"hello\n"` |
| **stderr** | `stderr` | Standard error capture | Currently `""` (limited capture) |
| **eval-result** | `result` | Return value of expression | `(+ 1 2 3)` → `6` |

## Base64 Encoding Support

Both tools support base64 encoding of all output fields via the `--output-base64` flag:

### nrepl-eval with Base64
- `value-base64` - Base64 encoded evaluation result
- `out-base64` - Base64 encoded stdout
- `err-base64` - Base64 encoded stderr

### local-eval with Base64  
- `result-base64` - Base64 encoded evaluation result
- `stdout-base64` - Base64 encoded stdout
- `stderr-base64` - Base64 encoded stderr

## Extracting Specific Outputs with jq

### Getting Evaluation Results

```bash
# nrepl-eval result
python3 scripts/mcp_nrepl_client.py --tool nrepl-eval --code '(+ 1 2 3)' --quiet | jq -r '.value'
# Output: 6

# local-eval result  
python3 scripts/mcp_nrepl_client.py --tool local-eval --code '(+ 1 2 3)' --quiet | jq -r '.result'
# Output: 6
```

### Getting Standard Output

```bash
# nrepl-eval stdout
python3 scripts/mcp_nrepl_client.py --tool nrepl-eval --code '(println "Hello!")' --quiet | jq -r '.out'
# Output: Hello!

# local-eval stdout
python3 scripts/mcp_nrepl_client.py --tool local-eval --code '(println "Hello!")' --quiet | jq -r '.stdout'  
# Output: Hello!
```

### Getting Standard Error

```bash
# nrepl-eval stderr
python3 scripts/mcp_nrepl_client.py --tool nrepl-eval --code '(throw (Exception. "error"))' --quiet | jq -r '.err'

# local-eval stderr
python3 scripts/mcp_nrepl_client.py --tool local-eval --code '(some-error)' --quiet | jq -r '.stderr'
```

## Base64 Output Examples

### Manual Base64 Decoding

```bash
# Get base64-encoded result and decode
python3 scripts/mcp_nrepl_client.py --tool nrepl-eval --code '(+ 1 2 3)' --output-base64 --quiet | jq -r '.\"value-base64\"' | base64 -d
# Output: 6

# Get base64-encoded stdout and decode  
python3 scripts/mcp_nrepl_client.py --tool nrepl-eval --code '(println "test")' --output-base64 --quiet | jq -r '.\"out-base64\"' | base64 -d
# Output: test
```

### Auto-Decode with --decode-output

```bash
# Auto-decoded fields (when using --decode-output)
python3 scripts/mcp_nrepl_client.py --tool nrepl-eval --code '(+ 1 2 3)' --output-base64 --decode-output --quiet | jq -r '.\"value-decoded\"'
# Output: 6

python3 scripts/mcp_nrepl_client.py --tool local-eval --code '(println "test")' --output-base64 --decode-output --quiet | jq -r '.\"stdout-decoded\"'
# Output: test
```

## Practical Use Cases

### 1. Silent Evaluation (Just the Result)
```bash
# Get only the computed value, ignore any println output
result=$(python3 scripts/mcp_nrepl_client.py --eval "(do (println \"debug info\") (+ 10 20))" --quiet | jq -r '.value')
echo "Result: $result"  # Result: 30
```

### 2. Debug Output Only
```bash
# Get only the debug/logging output, ignore the result
debug=$(python3 scripts/mcp_nrepl_client.py --eval "(do (println \"Processing...\") :done)" --quiet | jq -r '.out')
echo "Debug: $debug"  # Debug: Processing...
```

### 3. Complex Output Processing
```bash
# Capture both result and stdout for different processing
response=$(python3 scripts/mcp_nrepl_client.py --eval "(do (println \"Count: \" (count [1 2 3])) [1 2 3])" --quiet)
stdout=$(echo "$response" | jq -r '.out')
result=$(echo "$response" | jq -r '.value')
echo "Debug: $stdout"    # Debug: Count:  3
echo "Data: $result"     # Data: [1 2 3]
```

## Why Three Separate Streams?

This separation provides several advantages:

1. **Clean APIs** - Get computation results without debug noise
2. **Logging Control** - Capture debug output separately from data
3. **Error Handling** - Process errors independently from successful output  
4. **Piping Flexibility** - Route each stream to different destinations
5. **JSON Safety** - Base64 encoding eliminates quote escaping issues

## Implementation Notes

### nrepl-eval Processing
- Uses nREPL protocol's native `out`, `err`, and `value` fields
- Full stderr capture through nREPL middleware
- EDN to JSON conversion for structured data (`value-parsed` field)

### local-eval Processing  
- Uses `with-out-str` for stdout capture
- Limited stderr capture (currently returns empty string)
- Direct evaluation in MCP server's SCI runtime environment

Both tools maintain the three-stream separation while providing consistent base64 encoding options for JSON safety and AI agent compatibility.

## Summary

The MCP-nREPL bridge provides clean separation of Clojure evaluation streams:
- **Input**: Code execution via nREPL or local SCI
- **Output**: Three independent streams (stdout, stderr, eval-result)  
- **Access**: JSON field extraction with jq for precise output routing
- **Encoding**: Optional base64 for JSON safety and quote elimination

This architecture enables sophisticated output processing while maintaining the simplicity of single-command evaluation.