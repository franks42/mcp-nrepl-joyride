# Issue: Unified MCP Tester Validation Logic

## Summary
The new `unified_mcp_tester.py` has a validation bug with nested JSON responses.

## Problem
**Validation logic fails for tools that return JSON strings in MCP content field.**

Expected structure:
```
MCP Response → content[0].text → JSON_STRING → {"status": "success", ...}
```

Current validation looks for `"status"` directly in MCP response, not in parsed JSON_STRING.

## Impact
- Tests incorrectly fail despite server working perfectly
- local-eval and nrepl-server tools affected
- Framework issue, not server issue

## Evidence
Manual curl tests show server works:
```bash
# This works fine:
curl -X POST http://localhost:3000/mcp/ -d '{"method": "tools/call", "params": {"name": "local-eval", "arguments": {"code": "(+ 1 2 3)"}}}'
# Returns: {"content": [{"text": "{\"status\": \"success\", \"result\": 6}"}]}
```

## Fix Needed
Update `_validate_expected()` method in `DynamicTestSuite` class to:
1. Parse `content[0].text` as JSON 
2. Validate against parsed object, not raw MCP response

## Status
- ✅ Unified framework created and working
- ✅ Server functionality confirmed
- ✅ Validation logic fixed
- ✅ Old scripts moved to /old/scripts/
- ✅ Framework ready for production use

## Resolution
Fixed `_validate_expected()` method to properly handle nested JSON responses:
1. Parse `content[0].text` as JSON when present
2. Validate against parsed object, not raw MCP response
3. Graceful fallback to original response if JSON parsing fails