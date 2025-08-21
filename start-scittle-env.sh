#!/bin/bash

# Scittle Browser nREPL Environment Startup Script
# Usage: ./start-scittle-env.sh

set -e

SCITTLE_DIR="/Users/franksiebenlist/Development/scittle"
NREPL_PORT=7890
BROWSER_NREPL_PORT=1339
WEBSOCKET_PORT=1340
HTTP_PORT=1341

echo "🚀 Starting Scittle Browser nREPL Environment..."

# Check if Scittle directory exists
if [ ! -d "$SCITTLE_DIR" ]; then
    echo "❌ Scittle directory not found: $SCITTLE_DIR"
    echo "Please clone Scittle repo first:"
    echo "   git clone https://github.com/babashka/scittle.git $SCITTLE_DIR"
    exit 1
fi

# Kill any existing processes on our ports
echo "🧹 Cleaning up existing processes..."
lsof -ti:$NREPL_PORT | xargs kill -9 2>/dev/null || true
lsof -ti:$BROWSER_NREPL_PORT | xargs kill -9 2>/dev/null || true
lsof -ti:$WEBSOCKET_PORT | xargs kill -9 2>/dev/null || true
lsof -ti:$HTTP_PORT | xargs kill -9 2>/dev/null || true

# Start Babashka nREPL server in Scittle directory
echo "📡 Starting Babashka nREPL server on port $NREPL_PORT..."
cd "$SCITTLE_DIR"
bb --nrepl-server $NREPL_PORT &
BB_PID=$!

# Wait for nREPL server to start
echo "⏳ Waiting for nREPL server to start..."
sleep 3

# Store process info
echo $BB_PID > /tmp/scittle-bb-nrepl.pid
echo $NREPL_PORT > /tmp/scittle-nrepl-port

echo "✅ Scittle environment ready!"
echo ""
echo "📋 Connection Info:"
echo "   🔗 Babashka nREPL: localhost:$NREPL_PORT"
echo "   🌐 Browser nREPL: localhost:$BROWSER_NREPL_PORT (after setup)"
echo "   🔌 WebSocket: localhost:$WEBSOCKET_PORT (after setup)"
echo "   📁 HTTP Server: http://localhost:$HTTP_PORT (after setup)"
echo ""
echo "📝 Next Steps:"
echo "   1. Connect to nREPL: nrepl-connection {\"op\": \"connect\", \"connection\": \"$NREPL_PORT\"}"
echo "   2. Load setup functions: local-load-file {\"file-path\": \"scittle-setup.clj\"}"
echo "   3. Start Scittle: (start-scittle-environment!)"
echo "   4. Open browser: open http://localhost:$HTTP_PORT"
echo ""
echo "🛑 To stop: ./stop-scittle-env.sh"