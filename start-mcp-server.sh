#!/bin/bash

# Start MCP-nREPL Proxy Server
# This connects to Joyride nREPL on port 62577

echo "🚀 Starting MCP-nREPL Proxy Server..."
echo "📍 Connecting to Joyride nREPL on port 62577"
echo "📝 Use your Python MCP client to connect to this server"
echo "----------------------------------------"

# Set the nREPL port for auto-discovery
export NREPL_PORT=62577

# Start the MCP server
bb -cp src src/mcp_nrepl_proxy/core.clj