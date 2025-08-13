#!/bin/bash
# Test wrapper for minimal MCP server - avoids permission prompts

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🧪 Testing Minimal MCP Server${NC}"
echo "Project root: $PROJECT_ROOT"
echo

# Test 1: Basic MCP protocol
echo -e "${BLUE}Test 1: Basic MCP protocol tests${NC}"
cd "$PROJECT_ROOT"
uv run python stdio_mcp_client.py \
  --server-cmd "bb -cp src src/mcp_server/core.clj" \
  --test-basic --quiet

echo

# Test 2: debug-eval tool
echo -e "${BLUE}Test 2: debug-eval functionality${NC}"
uv run python stdio_mcp_client.py \
  --server-cmd "bb -cp src src/mcp_server/core.clj" \
  --tool debug-eval --args '{"code": "(+ 1 2 3)"}' --quiet

echo

# Test 3: debug-load-file tool
echo -e "${BLUE}Test 3: debug-load-file functionality${NC}"
uv run python stdio_mcp_client.py \
  --server-cmd "bb -cp src src/mcp_server/core.clj" \
  --tool debug-load-file --args '{"file-path": "test-toolkit.clj"}' --quiet

echo
echo -e "${GREEN}✅ All minimal server tests completed${NC}"