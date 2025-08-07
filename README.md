# MCP-nREPL Joyride Bridge

A Babashka-based MCP (Model Context Protocol) server that bridges Claude Code with Joyride's nREPL server running in VS Code.

## 🎯 Overview

This project enables Claude Code to directly control VS Code through Joyride by providing an nREPL → MCP translation layer. Claude Code can evaluate Clojure expressions that manipulate VS Code's API through Joyride.

```
Claude Code ↔ [MCP-nREPL Proxy] ↔ [Joyride nREPL] ↔ [VS Code APIs]
```

## ⚡ Features

- **Pure Babashka implementation** - Fast startup (~200ms) and low memory usage (~50MB)
- **Custom nREPL client** - Babashka-compatible socket-based nREPL communication
- **Auto-discovery** - Automatically finds and connects to Joyride's nREPL server via `.nrepl-port` file
- **MCP compliant** - Full Model Context Protocol support with tools and resources
- **Session management** - Track and manage isolated nREPL evaluation sessions
- **Joyride/Calva integration** - Full support for VS Code API calls and Calva middleware
- **Enhanced testing** - Comprehensive test suite with mock Joyride server
- **Workspace operations** - File listing, document operations, and notification support

## 🚀 Quick Start

### Prerequisites

1. [Babashka](https://babashka.org/) installed
2. [UV](https://docs.astral.sh/uv/) installed (for Python tooling)
3. VS Code with [Joyride extension](https://marketplace.visualstudio.com/items?itemName=betterthantomorrow.joyride)
4. Joyride nREPL server running in your VS Code workspace

### Installation

```bash
# Clone the repository
git clone https://github.com/franks42/mcp-nrepl-joyride.git
cd mcp-nrepl-joyride

# Install Python dependencies
uv sync

# Start server using management script (recommended)
uv run python mcp_server_manager.py start

# OR test the server directly
bb mcp-server
```

### Server Management

The project includes `mcp_server_manager.py` for streamlined server operations:

```bash
# Start server in background
uv run python mcp_server_manager.py start

# Check server status
uv run python mcp_server_manager.py status

# Run comprehensive health check
uv run python mcp_server_manager.py health

# Run basic functionality tests
uv run python mcp_server_manager.py test

# Show available MCP tools
uv run python mcp_server_manager.py tools

# Full workflow (start + test + health check)
uv run python mcp_server_manager.py run

# Stop server
uv run python mcp_server_manager.py stop

# Restart server
uv run python mcp_server_manager.py restart
```

### Claude Code Configuration

Add to your Claude Code MCP configuration:

```json
{
  "mcpServers": {
    "joyride-nrepl": {
      "command": "bb",
      "args": ["-f", "/path/to/mcp-nrepl-joyride/bb.edn", "mcp-server"],
      "env": {
        "JOYRIDE_WORKSPACE": "${workspaceFolder}",
        "MCP_DEBUG": "false"
      }
    }
  }
}
```

## 🛠️ Available MCP Tools

### `nrepl-connect`
Connect to Joyride's nREPL server (usually auto-discovered).

### `nrepl-eval`
Evaluate Clojure code in the nREPL session with full Joyride/Calva support.

**Examples:**
```clojure
;; Simple evaluation
(+ 1 2 3)

;; VS Code interaction via Joyride
(joyride.core/execute-command "workbench.action.quickOpen")

;; Access VS Code APIs
(-> js/vscode.window.activeTextEditor .-document .-fileName)

;; Workspace operations
(joyride/workspace-root)
(joyride/workspace-files "**/*.clj")

;; VS Code notifications
(vscode.window.showInformationMessage "Hello from Claude!")
```

### `nrepl-status`
Get connection status and session information.

### `nrepl-new-session`
Create a new nREPL session for isolated evaluations.

## 🎨 Example Usage with Claude Code

Once configured, Claude Code can directly manipulate VS Code:

**"Open the file src/core.clj and highlight line 42"**
```clojure
(do
  (require '[joyride.core :as joyride])
  (joyride/execute-command "vscode.open" 
    (str (joyride/workspace-root) "/src/core.clj"))
  (joyride/execute-command "revealLine" {:lineNumber 42 :at "center"}))
```

**"Show me all Clojure files in the workspace"**
```clojure
(->> (joyride/workspace-files "**/*.clj")
     (map #(.-path %))
     (sort))
```

## 🔧 Development

### Using the Management Script (Recommended)

```bash
# Full development workflow
uv run python mcp_server_manager.py run

# Start server for development
uv run python mcp_server_manager.py start --port 3005 --foreground

# Monitor server status during development
uv run python mcp_server_manager.py status
```

### Direct Babashka Commands

```bash
# Start in debug mode
bb dev

# Run basic integration tests
bb -cp src run-integration-test.clj

# Run enhanced Joyride integration tests
bb -cp src test-joyride-integration.clj

# Start test nREPL server
bb test-nrepl-server

# Start enhanced Joyride mock server
bb joyride-mock-server

# Start development REPL
bb repl

# Build standalone jar
bb build
```

### Python Code Quality

All Python code changes must pass quality checks:

```bash
# Format Python code
uv run black mcp_server_manager.py

# Check code style
uv run flake8 mcp_server_manager.py

# Install development dependencies
uv add black flake8
```

## 🏗️ Architecture

- **Core MCP server** in `src/mcp_nrepl_proxy/core.clj` - JSON-RPC 2.0 compliant
- **Custom nREPL client** in `src/mcp_nrepl_proxy/nrepl_client.clj` - Socket-based for Babashka compatibility
- **Babashka native** - no JVM startup penalty, fast iteration
- **Auto-discovery** - Finds Joyride nREPL via `.nrepl-port` file
- **Test infrastructure** - Mock servers for comprehensive testing
- **Session isolation** - Supports multiple concurrent nREPL sessions
- **Error handling** - Graceful degradation and connection management

## 📚 References

- [Model Context Protocol](https://modelcontextprotocol.io/)
- [Babashka](https://babashka.org/)
- [Joyride](https://github.com/BetterThanTomorrow/joyride)
- [nREPL Protocol](https://nrepl.org/nrepl/design/protocol.html)

## 🤝 Contributing

This project is part of the broader effort to enable AI-assisted development through VS Code integration. See the `docs/` directory for detailed implementation notes and architectural decisions.

## 📄 License

MIT License - see LICENSE file for details.
