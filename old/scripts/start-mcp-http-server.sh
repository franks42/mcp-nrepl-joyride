#!/bin/bash

# Start MCP-nREPL Proxy HTTP Server
# This connects to Joyride nREPL on port 62577 and serves HTTP MCP requests

echo "🚀 Starting MCP-nREPL Proxy HTTP Server..."
echo "📍 Connecting to Joyride nREPL on port 62577"
echo "🌐 HTTP server will listen on port 3000"
echo "🔗 MCP endpoint: http://localhost:3000/mcp"
echo "💚 Health check: http://localhost:3000/health"
echo "----------------------------------------"

# Set environment variables
export NREPL_PORT=62577
export MCP_HTTP_PORT=3000
export MCP_DEBUG=true

# Start the HTTP MCP server (pass port as argument)
bb -cp src src/mcp_nrepl_proxy/core.clj 3000