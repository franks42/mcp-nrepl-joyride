#!/bin/bash

# Scittle Browser nREPL Environment Stop Script
# Usage: ./stop-scittle-env.sh

echo "🛑 Stopping Scittle Browser nREPL Environment..."

# Kill processes by PID if available
if [ -f /tmp/scittle-bb-nrepl.pid ]; then
    BB_PID=$(cat /tmp/scittle-bb-nrepl.pid)
    if kill -0 $BB_PID 2>/dev/null; then
        echo "🔪 Stopping Babashka nREPL server (PID: $BB_PID)..."
        kill $BB_PID
    fi
    rm -f /tmp/scittle-bb-nrepl.pid
fi

# Kill any remaining processes on our ports
echo "🧹 Cleaning up all Scittle-related processes..."
lsof -ti:7890 | xargs kill -9 2>/dev/null || true   # Babashka nREPL
lsof -ti:1339 | xargs kill -9 2>/dev/null || true   # Browser nREPL  
lsof -ti:1340 | xargs kill -9 2>/dev/null || true   # WebSocket
lsof -ti:1341 | xargs kill -9 2>/dev/null || true   # HTTP Server

# Clean up temp files
rm -f /tmp/scittle-nrepl-port

echo "✅ Scittle environment stopped!"
echo "🔄 To restart: ./start-scittle-env.sh"