#!/bin/bash
# Development scripts for MCP-nREPL Joyride project
# Usage: uv run scripts.sh <command>

case "$1" in
    start-mcp-server)
        ./start-mcp-http-server.sh
        ;;
    start-mcp-stdio)
        ./start-mcp-server.sh
        ;;
    stop-mcp-server)
        pkill -f mcp_nrepl_proxy || echo "No MCP server running"
        ;;
    test-mcp-interactive)
        python simple_mcp_client.py --mcp-url http://localhost:3000/mcp --interactive
        ;;
    bb-test-joyride)
        bb test-real-joyride.clj
        ;;
    bb-repl)
        bb -cp src -i
        ;;
    health-check)
        curl http://localhost:3000/health
        ;;
    test-eval)
        curl -X POST http://localhost:3000/mcp \
          -H 'Content-Type: application/json' \
          -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"nrepl-eval","arguments":{"code":"(+ 1 2 3)"}}}'
        ;;
    list-tools)
        curl -X POST http://localhost:3000/mcp \
          -H 'Content-Type: application/json' \
          -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
        ;;
    lint)
        echo "🔍 Linting Clojure code with clj-kondo..."
        clj-kondo --lint src/
        ;;
    lint-fix)
        echo "🔧 Linting and attempting to fix issues..."
        clj-kondo --lint src/ --fix
        ;;
    help|*)
        echo "Available commands:"
        echo "  start-mcp-server     - Start HTTP MCP server"
        echo "  start-mcp-stdio      - Start stdio MCP server"
        echo "  stop-mcp-server      - Stop MCP server"
        echo "  test-mcp-interactive - Interactive MCP client"
        echo "  bb-test-joyride      - Test direct Joyride connection"
        echo "  bb-repl              - Start Babashka REPL"
        echo "  health-check         - Check server health"
        echo "  test-eval            - Test nREPL evaluation"
        echo "  list-tools           - List available MCP tools"
        echo "  lint                 - Lint Clojure code with clj-kondo"
        echo "  lint-fix             - Lint and attempt to fix issues"
        echo ""
        echo "Usage: ./scripts.sh <command>"
        ;;
esac