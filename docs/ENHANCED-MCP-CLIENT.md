# Enhanced MCP-nREPL Client Documentation

**Never use curl again!** The enhanced Python MCP client provides a user-friendly, feature-rich interface for interacting with MCP-nREPL servers.

## Overview

The `mcp_nrepl_client.py` is a complete replacement for curl when working with MCP servers. It offers:

- **Direct command-line operations** - No JSON crafting needed
- **nREPL-specific features** - Built for Clojure/nREPL workflows  
- **Beautiful output formatting** - Colors, tables, and pretty-printing
- **Interactive mode** - Full-featured REPL with history
- **Automation-friendly** - Perfect for scripts and CI/CD

## Installation & Setup

### Basic Usage
```bash
# The client works out of the box with Python 3.7+
python3 mcp_nrepl_client.py --help

# Or run directly as executable (after chmod +x mcp_nrepl_client.py)
./mcp_nrepl_client.py --help
```

### Enhanced Features (Optional)
```bash
# Install rich for beautiful formatting (recommended)
pip install rich

# Install readline for command history (usually included)
pip install readline
```

## Quick Start Examples

### 1. Instant Code Evaluation
```bash
# Execute Clojure code directly
./mcp_nrepl_client.py --eval "(+ 1 2 3)"
./mcp_nrepl_client.py --eval "(defn hello [name] (str \"Hello \" name))"
./mcp_nrepl_client.py --eval "(hello \"World\")"

# Quiet mode for scripting
./mcp_nrepl_client.py --eval "(* 6 7)" --quiet

# Alternative: use python3 explicitly  
python3 mcp_nrepl_client.py --eval "(+ 1 2 3)"
```

### 2. Server Health & Status
```bash
# Check if server is running and connected
python3 mcp_nrepl_client.py --status

# Pretty formatted status with rich colors
python3 mcp_nrepl_client.py --status --pretty

# Minimal status for monitoring scripts  
python3 mcp_nrepl_client.py --status --quiet
```

### 3. Comprehensive Testing
```bash
# Full nREPL functionality test
python3 mcp_nrepl_client.py --test-nrepl

# Summary view for CI/CD
python3 mcp_nrepl_client.py --test-nrepl --summary --quiet

# Returns exit code 0 for success, 1 for failure
```

### 4. Tool Discovery & Usage
```bash
# List all available tools
python3 mcp_nrepl_client.py --tools

# Different output formats
python3 mcp_nrepl_client.py --tools --format table  # Rich table
python3 mcp_nrepl_client.py --tools --format json   # JSON output
python3 mcp_nrepl_client.py --tools --format text   # Plain text

# Call specific tools directly
python3 mcp_nrepl_client.py --tool nrepl-status
python3 mcp_nrepl_client.py --tool nrepl-eval --args '{"code": "(+ 1 2)"}'
python3 mcp_nrepl_client.py --tool nrepl-new-session
```

## Interactive Mode

### Starting Interactive Mode
```bash
# Full-featured interactive nREPL client
python3 mcp_nrepl_client.py --interactive
```

### Interactive Commands
```
nrepl> eval (+ 1 2 3)          # Evaluate Clojure code
nrepl> status                  # Show server status  
nrepl> tools                   # List available tools
nrepl> tool nrepl-test         # Run specific tool
nrepl> test                    # Run comprehensive tests
nrepl> history                 # Show evaluation history
nrepl> clear                   # Clear screen
nrepl> quit                    # Exit
```

### Interactive Features
- **Command History** - Use ↑/↓ arrows to navigate previous commands
- **Tab Completion** - Tab to complete commands and tool names
- **Multi-line Input** - Paste complex code blocks
- **Persistent History** - Commands saved between sessions

## Command Reference

### Connection Options
```bash
--url URL                    # MCP server URL (default: http://localhost:3000/mcp)
```

### Quick Actions
```bash
--eval CODE                  # Evaluate Clojure code and exit
--status                     # Show server status and exit
--test-nrepl                 # Run comprehensive nREPL tests  
--tools                      # List available tools
```

### Tool Operations
```bash
--tool NAME                  # Call specific tool
--args ARGS                  # Tool arguments as JSON (default: {})
```

### Output Control
```bash
--pretty                     # Pretty formatted output with colors
--quiet, -q                  # Minimal output for scripting
--summary                    # Show only summary for test results
--format {text,json,table}   # Output format for tools list
```

### Interactive Mode
```bash
--interactive, -i            # Start interactive mode
```

## Advanced Usage

### Scripting & Automation
```bash
#!/bin/bash
# Automated nREPL testing script

# Check if server is up
if python3 mcp_nrepl_client.py --status --quiet; then
    echo "Server is running"
    
    # Run comprehensive tests
    if python3 mcp_nrepl_client.py --test-nrepl --summary --quiet; then
        echo "All tests passed"
        exit 0
    else
        echo "Tests failed"
        exit 1
    fi
else
    echo "Server is down"
    exit 1
fi
```

### Complex Evaluations
```bash
# Multi-step operations
python3 mcp_nrepl_client.py --eval "(def data [1 2 3 4 5])"
python3 mcp_nrepl_client.py --eval "(map #(* % 2) data)"
python3 mcp_nrepl_client.py --eval "(reduce + data)"

# Function definitions
python3 mcp_nrepl_client.py --eval "(defn factorial [n] (if (<= n 1) 1 (* n (factorial (dec n)))))"
python3 mcp_nrepl_client.py --eval "(factorial 5)"
```

### JSON Tool Arguments
```bash
# Complex tool arguments
python3 mcp_nrepl_client.py --tool nrepl-eval --args '{
  "code": "(println \"Hello World\")",
  "ns": "user",
  "session": "my-session"
}'

# Session management
python3 mcp_nrepl_client.py --tool nrepl-new-session
python3 mcp_nrepl_client.py --tool nrepl-eval --args '{
  "code": "(def x 42)",
  "session": "session-id-here"
}'
```

## Output Formats

### Standard Output
```
ℹ️  Connecting to MCP server: http://localhost:3000/mcp
✅ Connected successfully!
ℹ️  Evaluating: (+ 1 2 3)
6
```

### Pretty Output (with --pretty)
Rich-formatted output with:
- Syntax highlighting for JSON
- Colored panels and borders  
- Table formatting for tools
- Error highlighting

### Quiet Output (with --quiet)
```
6
```

### JSON Output (with --format json)
```json
[
  {
    "name": "nrepl-eval",
    "description": "Evaluate Clojure code in nREPL session",
    "inputSchema": {
      "type": "object",
      "properties": {
        "code": {"type": "string"},
        "session": {"type": "string"},
        "ns": {"type": "string"}
      }
    }
  }
]
```

## Error Handling

### Connection Errors
```bash
❌ Connection failed: Connection refused
# Server not running - start the MCP server first
```

### Evaluation Errors
```bash
❌ Evaluation failed: Unable to resolve symbol: undefined-var
# Check your Clojure syntax and variable names
```

### Tool Errors
```bash
❌ Tool 'invalid-tool' not found. Available tools: nrepl-eval, nrepl-status, nrepl-test, nrepl-new-session, nrepl-connect
# Use --tools to see available tools
```

## Comparison: curl vs Enhanced Client

### Old Way (curl)
```bash
# Complex curl command
curl -X POST http://localhost:3000/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "nrepl-eval",
      "arguments": {"code": "(+ 1 2 3)"}
    }
  }' | jq '.'
```

### New Way (Enhanced Client)
```bash
# Simple, intuitive command
python3 mcp_nrepl_client.py --eval "(+ 1 2 3)"
```

## Integration Examples

### CI/CD Pipeline
```yaml
# GitHub Actions example
- name: Test nREPL Connection
  run: |
    python3 mcp_nrepl_client.py --test-nrepl --summary --quiet
    if [ $? -eq 0 ]; then
      echo "✅ nREPL tests passed"
    else
      echo "❌ nREPL tests failed"
      exit 1
    fi
```

### Monitoring Script
```bash
#!/bin/bash
# Health monitoring for nREPL server

while true; do
    if python3 mcp_nrepl_client.py --status --quiet > /dev/null 2>&1; then
        echo "$(date): nREPL server healthy"
    else
        echo "$(date): nREPL server DOWN" >&2
        # Send alert...
    fi
    sleep 60
done
```

### Development Workflow
```bash
# Quick development cycle
alias nrepl="python3 mcp_nrepl_client.py"
alias nr-eval="nrepl --eval"
alias nr-test="nrepl --test-nrepl --summary"
alias nr-repl="nrepl --interactive"

# Usage
nr-eval "(+ 1 2 3)"
nr-test
nr-repl
```

## Best Practices

### 1. Use Appropriate Output Modes
- **Interactive development**: Use default or `--pretty` mode
- **Scripting/automation**: Use `--quiet` mode
- **CI/CD pipelines**: Use `--quiet --summary` for tests

### 2. Leverage Tool-Specific Commands
```bash
# Instead of generic tool calls
python3 mcp_nrepl_client.py --tool nrepl-eval --args '{"code": "..."}'

# Use specific shortcuts
python3 mcp_nrepl_client.py --eval "..."
```

### 3. Error Checking in Scripts
```bash
# Always check exit codes
if python3 mcp_nrepl_client.py --eval "(+ 1 2)" --quiet; then
    echo "Success"
else
    echo "Failed" >&2
    exit 1
fi
```

### 4. Interactive Development
- Start with `--interactive` for exploration
- Use `history` command to review past evaluations
- Copy successful commands to scripts

## Troubleshooting

### Common Issues

**Client won't connect**
- Verify MCP server is running: `python3 mcp_nrepl_client.py --status`
- Check URL: `--url http://localhost:3000/mcp`
- Verify network connectivity

**Missing rich formatting**
- Install rich: `pip install rich`
- Use `--format text` as fallback

**Interactive mode issues**
- Install readline: `pip install readline`
- Check terminal compatibility

**JSON parsing errors**  
- Validate JSON with `--args`: Use online JSON validators
- Escape quotes properly in shell

## Performance Notes

- **Connection reuse**: Client maintains sessions across multiple tool calls
- **Caching**: Tool definitions cached after first connection
- **Timeouts**: 30-second default timeout for all operations
- **Parallel safe**: Multiple client instances can run simultaneously

## Security Considerations

- **Local connections**: Default assumes localhost MCP server
- **No credentials stored**: Session management only, no sensitive data
- **JSON injection**: Arguments properly escaped and validated
- **Network exposure**: Use HTTPS URLs for remote connections

---

## Summary

The enhanced MCP-nREPL client transforms what was previously a painful curl-based workflow into an intuitive, powerful command-line experience. Whether you're doing interactive development, writing automation scripts, or building CI/CD pipelines, this client provides all the tools you need for efficient nREPL interaction.

**Key Benefits:**
- ✅ **No more curl** - Simple, memorable commands
- ✅ **nREPL optimized** - Built specifically for Clojure workflows  
- ✅ **Developer friendly** - Colors, formatting, and great error messages
- ✅ **Automation ready** - Perfect exit codes and quiet modes
- ✅ **Feature complete** - Everything you need in one tool

Start with `python3 mcp_nrepl_client.py --help` and enjoy the improved developer experience!