#!/bin/bash
# Test script for Python/Basilisp nREPL connection

echo "🔬 Testing Python/Basilisp nREPL Integration"
echo "==========================================="
echo ""

# Check if basilisp is installed
if ! python3 -c "import basilisp" 2>/dev/null; then
    echo "📦 Installing Basilisp..."
    pip3 install basilisp basilisp-nrepl-async
fi

echo "1️⃣  Starting Python server with Basilisp nREPL on port 7890..."
python3 test-basilisp-server.py 7890 &
PYTHON_PID=$!

echo "   PID: $PYTHON_PID"
sleep 3

echo ""
echo "2️⃣  Connecting MCP server to Python nREPL..."
echo ""

# Test connection via MCP
echo "Testing nrepl-connect to Python server:"
curl -X POST http://localhost:3000/mcp \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "nrepl-connect",
      "arguments": {
        "host": "localhost",
        "port": 7890
      }
    }
  }'

echo ""
echo ""
echo "3️⃣  Testing Python introspection via nREPL:"
echo ""

# Get Python server status
echo "Getting Python server status:"
curl -X POST http://localhost:3000/mcp \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "nrepl-eval",
      "arguments": {
        "code": "(python-bridge/server-status)"
      }
    }
  }'

echo ""
echo ""

# Get Python info
echo "Getting Python runtime info:"
curl -X POST http://localhost:3000/mcp \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "nrepl-eval",
      "arguments": {
        "code": "(python-bridge/python-info)"
      }
    }
  }'

echo ""
echo ""
echo "4️⃣  Cleanup"
echo "   Stopping Python server..."
kill $PYTHON_PID 2>/dev/null

echo ""
echo "✅ Test complete!"
echo ""
echo "You can now:"
echo "  1. Start the Python server: python3 test-basilisp-server.py"
echo "  2. Use MCP nrepl-connect to connect to localhost:7890"
echo "  3. Evaluate Clojure code that introspects Python objects!"