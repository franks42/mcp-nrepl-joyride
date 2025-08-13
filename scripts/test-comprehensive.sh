#!/bin/bash
# Comprehensive test wrapper - all tests without confirmation prompts

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "🧪 Comprehensive MCP Server Tests"
cd "$PROJECT_ROOT"

echo "Test 1: Basic MCP protocol..."
uv run python scripts/stdio_mcp_client.py \
  --server-cmd "bb -cp src src/mcp_server/core.clj" \
  --test-basic --quiet

echo "Test 2: debug-eval arithmetic..."
uv run python scripts/stdio_mcp_client.py \
  --server-cmd "bb -cp src src/mcp_server/core.clj" \
  --tool debug-eval --args '{"code": "(+ 1 2 3)"}' --quiet

echo "Test 3: debug-eval variable definition..."
uv run python scripts/stdio_mcp_client.py \
  --server-cmd "bb -cp src src/mcp_server/core.clj" \
  --tool debug-eval --args '{"code": "(def test-var 42)"}' --quiet

echo "Test 4: debug-load-file..."
uv run python scripts/stdio_mcp_client.py \
  --server-cmd "bb -cp src src/mcp_server/core.clj" \
  --tool debug-load-file --args '{"file-path": "test-toolkit.clj"}' --quiet

echo "Test 5: List available tools..."
uv run python scripts/stdio_mcp_client.py \
  --server-cmd "bb -cp src src/mcp_server/core.clj" \
  --list-tools --quiet

echo "✅ All comprehensive tests passed"