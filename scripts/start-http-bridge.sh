#!/bin/bash
# HTTP-to-stdio bridge startup script
# Bridges HTTP requests on port 3000 to stdio nREPL MCP server

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Configuration
HTTP_PORT="${MCP_HTTP_PORT:-3000}"
LOG_FILE="$PROJECT_ROOT/logs/http-bridge.log"
PID_FILE="$PROJECT_ROOT/.http-bridge.pid"

# Ensure logs directory exists
mkdir -p "$PROJECT_ROOT/logs"

# Function to cleanup on exit
cleanup() {
    if [[ -f "$PID_FILE" ]]; then
        local pid=$(cat "$PID_FILE")
        echo "🛑 Stopping bridge process (PID: $pid)..."
        kill $pid 2>/dev/null || true
        rm -f "$PID_FILE"
    fi
    exit 0
}

# Setup signal handlers
trap cleanup SIGTERM SIGINT

# Check if bridge is already running
if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "❌ Bridge already running (PID: $(cat "$PID_FILE"))"
    echo "   Stop it first: ./scripts/stop-http-bridge.sh"
    exit 1
fi

# Start the bridge
echo "🚀 Starting HTTP-to-stdio MCP bridge..."
echo "   Port: $HTTP_PORT"
echo "   MCP Server: bb -cp src src/nrepl_mcp_server/core.clj"
echo "   Log file: $LOG_FILE"
echo "   PID file: $PID_FILE"

cd "$PROJECT_ROOT"

# Start mcp-proxy with our nREPL MCP server using StreamableHTTP (stateless mode)
uv run mcp-proxy \
    --port "$HTTP_PORT" \
    --debug \
    --stateless \
    -- bb -cp src src/nrepl_mcp_server/core.clj \
    2>&1 | tee "$LOG_FILE" &

# Save PID
BRIDGE_PID=$!
echo $BRIDGE_PID > "$PID_FILE"

echo "✅ Bridge started successfully!"
echo "   PID: $BRIDGE_PID"
echo "   HTTP endpoint: http://localhost:$HTTP_PORT/mcp/"
echo "   Accept headers: application/json, text/event-stream"
echo ""
echo "🔍 Monitor logs: tail -f $LOG_FILE"
echo "🛑 Stop bridge: ./scripts/stop-http-bridge.sh"
echo ""

# Wait for bridge process
wait $BRIDGE_PID