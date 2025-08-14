#!/bin/bash

# Enhanced MCP-nREPL Client Demo
# Demonstrates the improved Python client for MCP-nREPL interactions

echo "🎬 Enhanced MCP-nREPL Client Demo"
echo "================================"
echo ""

echo "💡 The new client eliminates the need for curl and provides:"
echo "   - Direct code evaluation with --eval"
echo "   - Quick status checks with --status"  
echo "   - Comprehensive testing with --test-nrepl"
echo "   - Beautiful formatted output with --pretty"
echo "   - Quiet mode for scripting with --quiet"
echo "   - Interactive mode with history and completion"
echo ""

echo "📝 Examples:"
echo ""

echo "1️⃣  Quick code evaluation:"
echo "   python3 mcp_nrepl_client.py --eval \"(+ 1 2 3)\" --quiet"
python3 mcp_nrepl_client.py --eval "(+ 1 2 3)" --quiet
echo ""

echo "2️⃣  Check server status (quiet mode):"
echo "   python3 mcp_nrepl_client.py --status --quiet"
python3 mcp_nrepl_client.py --status --quiet | head -5
echo "   ... (truncated)"
echo ""

echo "3️⃣  Run comprehensive tests with summary:"
echo "   python3 mcp_nrepl_client.py --test-nrepl --summary --quiet"
python3 mcp_nrepl_client.py --test-nrepl --summary --quiet
echo ""

echo "4️⃣  Direct tool calling:"
echo "   python3 mcp_nrepl_client.py --tool nrepl-eval --args '{\"code\": \"(* 21 2)\"}' --quiet"
python3 mcp_nrepl_client.py --tool nrepl-eval --args '{"code": "(* 21 2)"}' --quiet
echo ""

echo "5️⃣  List tools in different formats:"
echo "   python3 mcp_nrepl_client.py --tools --format json --quiet | jq '.[] | .name'"
python3 mcp_nrepl_client.py --tools --format json --quiet | python3 -c "import sys, json; tools = json.load(sys.stdin); [print(tool['name']) for tool in tools]"
echo ""

echo "🎮 Interactive Mode:"
echo "   python3 mcp_nrepl_client.py --interactive"
echo "   Available commands: eval, status, tools, tool, test, history, clear, quit"
echo ""

echo "🌟 Key Features:"
echo "   ✅ No more curl commands needed"
echo "   ✅ User-friendly command-line interface"
echo "   ✅ nREPL-specific shortcuts and helpers"
echo "   ✅ Beautiful formatted output (with rich library)"
echo "   ✅ Comprehensive error handling"
echo "   ✅ Session management and connection persistence"
echo "   ✅ Interactive mode with history and completion"
echo "   ✅ Perfect for both interactive use and automation"
echo ""

echo "📦 Optional Enhancement:"
echo "   pip install rich  # For beautiful table and syntax highlighting"
echo ""

echo "✨ The enhanced MCP client makes nREPL interaction a pleasure!"