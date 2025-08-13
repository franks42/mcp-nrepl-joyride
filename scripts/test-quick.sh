#!/bin/bash
# Quick test wrapper - no environment variables, no confirmation prompts

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "🧪 Quick MCP Server Test"
cd "$PROJECT_ROOT"

# Test basic protocol + debug-eval in one call
uv run python scripts/stdio_mcp_client.py \
  --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" \
  --tool debug-eval --args '{"code": "(+ 1 2 3)"}' --quiet

echo "✅ Quick test passed"