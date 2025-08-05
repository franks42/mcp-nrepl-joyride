# MCP-nREPL Joyride Bridge

A Babashka-based MCP (Model Context Protocol) server that bridges Claude Code with Joyride's nREPL server running in VS Code.

## 🎯 Overview

This project enables Claude Code to directly control VS Code through Joyride by providing an nREPL → MCP translation layer. Claude Code can evaluate Clojure expressions that manipulate VS Code's API through Joyride.

```
Claude Code ↔ [MCP-nREPL Proxy] ↔ [Joyride nREPL] ↔ [VS Code APIs]
```

## ⚡ Features

- **Pure Babashka implementation** - Fast startup (~200ms) and low memory usage (~50MB)
- **Native nREPL integration** - Uses `babashka.nrepl.client` for seamless Clojure communication
- **Auto-discovery** - Automatically finds and connects to Joyride's nREPL server via `.nrepl-port` file
- **MCP compliant** - Full Model Context Protocol support with tools and resources
- **Session management** - Track and manage nREPL evaluation sessions
- **Command history** - Access recent evaluations through MCP resources

## 🚀 Quick Start

### Prerequisites

1. [Babashka](https://babashka.org/) installed
2. VS Code with [Joyride extension](https://marketplace.visualstudio.com/items?itemName=betterthantomorrow.joyride)
3. Joyride nREPL server running in your VS Code workspace

### Installation

```bash
# Clone the repository
git clone https://github.com/franks42/mcp-nrepl-joyride.git
cd mcp-nrepl-joyride

# Test the server
bb mcp-server
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
Evaluate Clojure code in the nREPL session.

**Example:**
```clojure
;; Simple evaluation
(+ 1 2 3)

;; VS Code interaction via Joyride
(joyride.core/execute-command "workbench.action.quickOpen")

;; Access VS Code APIs
(-> js/vscode.window.activeTextEditor .document .fileName)
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

```bash
# Start in debug mode
bb dev

# Run tests
bb test

# Start development REPL
bb repl

# Build standalone jar
bb build
```

## 🏗️ Architecture

- **Single-file implementation** in `src/mcp_nrepl_proxy/core.clj`
- **Babashka native** - no JVM startup penalty
- **Built-in nREPL client** - leverages `babashka.nrepl.client`
- **MCP resources** - Recent command history accessible via `nrepl://commands/*` URIs
- **Auto-discovery** - Finds Joyride nREPL via `.nrepl-port` file

## 📚 References

- [Model Context Protocol](https://modelcontextprotocol.io/)
- [Babashka](https://babashka.org/)
- [Joyride](https://github.com/BetterThanTomorrow/joyride)
- [nREPL Protocol](https://nrepl.org/nrepl/design/protocol.html)

## 🤝 Contributing

This project is part of the broader effort to enable AI-assisted development through VS Code integration. See the `docs/` directory for detailed implementation notes and architectural decisions.

## 📄 License

MIT License - see LICENSE file for details.
