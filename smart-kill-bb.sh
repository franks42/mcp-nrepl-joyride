#!/bin/bash

# Smart Babashka Process Killer
# Only kills specific BB processes, not all of them!

set -e

echo "🎯 Smart Babashka Process Killer"
echo "================================"

# Function to kill process by pattern and description
kill_by_pattern() {
    local pattern="$1"
    local description="$2"
    local pids=$(ps aux | grep -E "$pattern" | grep -v grep | awk '{print $2}')
    
    if [ -n "$pids" ]; then
        echo "🔪 Killing $description processes: $pids"
        for pid in $pids; do
            if kill -0 $pid 2>/dev/null; then
                echo "   Stopping PID $pid"
                kill $pid
                sleep 1
                # Force kill if still running
                if kill -0 $pid 2>/dev/null; then
                    echo "   Force killing PID $pid"
                    kill -9 $pid
                fi
            fi
        done
    else
        echo "ℹ️  No $description processes found"
    fi
}

# Show current BB processes before killing
echo "📋 Current Babashka processes:"
ps aux | grep bb | grep -v grep | grep -E "(nrepl_mcp_server|nrepl-server|scittle)" || echo "   No target BB processes found"
echo ""

# Parse command line arguments
case "${1:-help}" in
    "mcp")
        echo "🎯 Targeting: MCP nREPL server processes only"
        kill_by_pattern "bb.*nrepl_mcp_server" "MCP nREPL server"
        kill_by_pattern "mcp-proxy.*bb.*nrepl_mcp_server" "MCP proxy with BB server"
        ;;
    "scittle")
        echo "🎯 Targeting: Scittle environment processes only"
        kill_by_pattern "bb --nrepl-server 7890" "Scittle Babashka nREPL (port 7890)"
        kill_by_pattern "bb.*scittle" "Scittle-related BB processes"
        ;;
    "test")
        echo "🎯 Targeting: Test nREPL server processes only"
        kill_by_pattern "bb.*test.*nrepl" "Test nREPL server"
        kill_by_pattern "bb.*-nrepl-server" "Generic nREPL servers"
        ;;
    "all-mcp-related")
        echo "🎯 Targeting: All MCP-related BB processes (DANGEROUS!)"
        kill_by_pattern "bb.*nrepl" "All nREPL-related BB processes"
        ;;
    "help"|*)
        echo "Usage: $0 [target]"
        echo ""
        echo "Targets:"
        echo "  mcp              - Kill only MCP nREPL server processes"
        echo "  scittle          - Kill only Scittle environment processes"  
        echo "  test             - Kill only test nREPL server processes"
        echo "  all-mcp-related  - Kill all nREPL-related BB processes (DANGEROUS!)"
        echo "  help             - Show this help"
        echo ""
        echo "🔍 Current BB processes:"
        ps aux | grep bb | grep -v grep | awk '{print "   PID " $2 ": " $11 " " $12 " " $13 " " $14}' || echo "   No BB processes found"
        exit 0
        ;;
esac

echo ""
echo "✅ Process cleanup completed!"
echo ""
echo "📋 Remaining Babashka processes:"
ps aux | grep bb | grep -v grep | awk '{print "   PID " $2 ": " $11 " " $12 " " $13 " " $14}' || echo "   No BB processes remaining"