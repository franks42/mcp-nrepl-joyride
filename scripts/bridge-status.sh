#!/bin/bash
# Check HTTP-to-stdio bridge status

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
PID_FILE="$PROJECT_ROOT/.http-bridge.pid"
LOG_FILE="$PROJECT_ROOT/logs/http-bridge.log"
HTTP_PORT="${MCP_HTTP_PORT:-3000}"

echo "🔍 HTTP-to-stdio Bridge Status"
echo "================================"

# Check PID file
if [[ -f "$PID_FILE" ]]; then
    PID=$(cat "$PID_FILE")
    echo "PID file: $PID_FILE"
    echo "PID: $PID"
    
    if kill -0 "$PID" 2>/dev/null; then
        echo "Status: ✅ RUNNING"
        
        # Check HTTP endpoint
        echo ""
        echo "🌐 HTTP Endpoint Test:"
        if curl -s "http://localhost:$HTTP_PORT/health" >/dev/null 2>&1; then
            echo "HTTP endpoint: ✅ RESPONDING (port $HTTP_PORT)"
            
            # Try MCP protocol test
            echo ""
            echo "🛠️  MCP Protocol Test:"
            if curl -s -X POST "http://localhost:$HTTP_PORT/mcp/" \
                -H "Content-Type: application/json" \
                -H "Accept: application/json, text/event-stream" \
                -d '{"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}}' \
                | grep -q "result\|error"; then
                echo "MCP protocol: ✅ WORKING"
            else
                echo "MCP protocol: ❌ NOT RESPONDING"
            fi
        else
            echo "HTTP endpoint: ❌ NOT RESPONDING (port $HTTP_PORT)"
        fi
        
        # Show recent logs
        if [[ -f "$LOG_FILE" ]]; then
            echo ""
            echo "📄 Recent logs (last 5 lines):"
            tail -5 "$LOG_FILE" 2>/dev/null || echo "No logs available"
        fi
        
    else
        echo "Status: ❌ NOT RUNNING (stale PID file)"
        rm -f "$PID_FILE"
    fi
else
    echo "PID file: None"
    echo "Status: ❌ NOT RUNNING"
fi

echo ""
echo "Configuration:"
echo "  Port: $HTTP_PORT"
echo "  Log file: $LOG_FILE"
echo "  Endpoints:"
echo "    • MCP: http://localhost:$HTTP_PORT/mcp/ (POST)"
echo "    • Headers: Accept: application/json, text/event-stream"
echo ""
echo "Commands:"
echo "  • Start: ./scripts/start-http-bridge.sh"
echo "  • Stop: ./scripts/stop-http-bridge.sh"
echo "  • Logs: tail -f $LOG_FILE"