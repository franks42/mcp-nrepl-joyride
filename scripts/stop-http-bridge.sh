#!/bin/bash
# Stop HTTP-to-stdio bridge

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
PID_FILE="$PROJECT_ROOT/.http-bridge.pid"

if [[ ! -f "$PID_FILE" ]]; then
    echo "❌ Bridge not running (no PID file found)"
    exit 1
fi

PID=$(cat "$PID_FILE")

if ! kill -0 "$PID" 2>/dev/null; then
    echo "❌ Bridge process not found (stale PID file)"
    rm -f "$PID_FILE"
    exit 1
fi

echo "🛑 Stopping HTTP-to-stdio bridge (PID: $PID)..."

# Try graceful shutdown first
kill -TERM "$PID"

# Wait for graceful shutdown
for i in {1..10}; do
    if ! kill -0 "$PID" 2>/dev/null; then
        echo "✅ Bridge stopped gracefully"
        rm -f "$PID_FILE"
        exit 0
    fi
    sleep 0.5
done

# Force kill if still running
echo "⚡ Force killing bridge..."
kill -KILL "$PID" 2>/dev/null || true
rm -f "$PID_FILE"
echo "✅ Bridge force stopped"