# Test nREPL Server

A simple full Clojure nREPL test server for testing the MCP-nREPL bridge.

## Usage

```bash
# Start server (auto-assigns port, tracks PID)
./nrepl-test start

# Check status and connection info
./nrepl-test status  

# Stop server
./nrepl-test stop

# Restart server
./nrepl-test restart
```

## Features

- Full JVM Clojure (not SCI) - promises, futures, Java interop
- Auto-discovery via `.test-nrepl-server-port` file
- Process management with PID tracking
- Graceful shutdown

## Known Limitations

### `info` Operation Not Supported
The test nREPL server (nrepl 1.3.1) does not include the `info` operation by default. This means:
- `doc` and `source` operations will fail with "unknown-op" status
- Documentation lookup features are not available
- This is not a bug in our implementation - it's a limitation of the basic nREPL server

To get `info` operation support, you would need to add cider-nrepl middleware, which is beyond the scope of this simple test server.

### Session Isolation Issues
The nREPL 1.3.1 server has a well-documented bug where sessions are acknowledged but fail with "unknown-session" errors. This affects:
- Session-specific evaluations
- Isolated namespace management
- Concurrent operation handling

This is tracked as issue #100 in the nREPL GitHub repository.

## Supported Operations

The server supports these nREPL operations:
- `eval` - Evaluate Clojure code
- `clone` - Create new sessions (though sessions have bugs)
- `close` - Close sessions
- `describe` - Get server capabilities
- `completions` - Code completion
- `load-file` - Load Clojure files
- `interrupt` - Interrupt evaluation
- `ls-sessions` - List active sessions
- Plus various middleware operations