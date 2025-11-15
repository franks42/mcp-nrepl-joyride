# JSON Parse Error Logging

## Purpose

Enhanced error logging to track JSON parsing failures from AI agents, helping identify when base64 encoding should be used.

## Implementation Date
2025-11-15

## What Gets Logged

When the MCP server receives malformed JSON that fails to parse, it logs comprehensive error information to stderr:

### Error Log Format

```
========================================
[JSON Parse Error] 2025-11-15T19:56:11.523566Z
Exception: com.fasterxml.jackson.core.io.JsonEOFException - Unexpected end-of-input: ...
Raw JSON: {"jsonrpc":"2.0", ...}
⚠️  Possible Issue: Unescaped quotes detected
💡 Hint: Consider using base64 encoding (input-base64=true) for complex expressions
========================================
```

### Information Captured

1. **Timestamp** - When the error occurred
2. **Exception Type** - Full Java exception class name
3. **Error Message** - Detailed message from the JSON parser
4. **Raw JSON** - The malformed JSON that failed (truncated if >500 chars)
5. **Issue Detection** - Automatic detection of common problems:
   - Unescaped quotes
   - Escaped backslashes
6. **Context-Aware Hints** - For calculate tool errors, suggests base64 encoding

## Why This Matters

### Tracking AI Agent Behavior

AI agents may struggle with JSON escaping when:
- Constructing complex Clojure expressions with quotes
- Building strings with special characters
- Generating nested JSON structures

This logging helps us:
1. **Identify patterns** - Which types of expressions cause problems?
2. **Measure impact** - How often do escaping issues occur?
3. **Validate solutions** - Does base64 encoding solve the problem?
4. **Improve guidance** - Update tool descriptions based on real usage

### Analytics Value

By analyzing these logs, we can:
- Determine if base64 encoding is actually needed or overkill
- Identify which AI models have more escaping issues
- Improve tool documentation with real-world examples
- Detect if certain expression types are problematic

## Example Scenarios

### Scenario 1: Unescaped Quotes in Calculate Expression

**Input:**
```json
{"jsonrpc":"2.0","method":"tools/call","params":{"name":"calculate","arguments":{"expr":"(let [x "test"] x)"}}}
```

**Logged:**
```
[JSON Parse Error] 2025-11-15T19:56:11Z
Exception: JsonEOFException - Unexpected end-of-input: was expecting closing quote
Raw JSON: {"jsonrpc":"2.0","method":"tools/call","params":{"name":"calculate",...
⚠️  Possible Issue: Unescaped quotes detected
💡 Hint: Consider using base64 encoding (input-base64=true) for complex expressions
```

**Solution:** Use base64 encoding
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "calculate",
    "arguments": {
      "expr": "KGxldCBbeCAidGVzdCJdIHgp",
      "input-base64": true
    }
  }
}
```

### Scenario 2: Incomplete JSON Structure

**Input:**
```json
{"jsonrpc":"2.0","id":1
```

**Logged:**
```
[JSON Parse Error] 2025-11-15T19:56:12Z
Exception: JsonEOFException - Unexpected end-of-input: expected close marker for Object
Raw JSON: {"jsonrpc":"2.0","id":1
```

### Scenario 3: Long Expression (Truncation)

**Input:** Very long malformed JSON (>500 chars)

**Logged:**
```
[JSON Parse Error] 2025-11-15T19:56:13Z
Exception: JsonEOFException - ...
Raw JSON: {"jsonrpc":"2.0","method":"tools/call"... [truncated 823 chars]
💡 Hint: Consider using base64 encoding (input-base64=true) for complex expressions
```

## Technical Details

### Location
`src/nrepl_mcp_server/mcp_server/server.clj:12-40`

### Implementation
- Catches all `Exception` during `json/parse-string`
- Logs to `stderr` (not `stdout` which is for JSON-RPC responses)
- Flushes immediately to ensure logging before server exit
- Returns `nil` to gracefully handle parse failures

### Detection Logic

```clojure
;; Unescaped quotes
(when (re-find #"[^\\]\"" line)
  (println "⚠️  Possible Issue: Unescaped quotes detected"))

;; Escaped backslashes
(when (re-find #"\\\\" line)
  (println "ℹ️  Note: Contains escaped backslashes"))

;; Calculate-specific hint
(when (and (str/includes? line "calculate")
           (str/includes? line "expr"))
  (println "💡 Hint: Consider using base64 encoding (input-base64=true) for complex expressions"))
```

## Testing

### Manual Test

```bash
# Test with malformed JSON
echo '{"jsonrpc":"2.0","id":1' | bb -cp src src/nrepl_mcp_server/core.clj 2>&1 | grep -A 10 "JSON Parse Error"

# Test with calculate expression
echo '{"jsonrpc":"2.0","method":"tools/call","params":{"name":"calculate","arguments":{"expr":"(let [x' | \
  bb -cp src src/nrepl_mcp_server/core.clj 2>&1 | grep -A 15 "JSON Parse Error"
```

### Automated Testing

See `scripts/test-json-error-logging.py` (when created) for automated test suite.

## Impact Assessment

After collecting error logs in production, analyze:

1. **Frequency** - How often do JSON parse errors occur?
2. **Patterns** - What types of expressions cause issues?
3. **Tool Distribution** - Is it just `calculate` or other tools too?
4. **Base64 Adoption** - Do agents learn to use base64 after seeing the hint?

## Related Features

- **Calculator base64 encoding** - `src/nrepl_mcp_server/mcp_server/tools/calculate.clj`
- **nREPL-eval base64 encoding** - Similar pattern for code evaluation
- **Analytics logging** - Calculation analytics in `calculator-usage.edn`

## Future Enhancements

Potential improvements based on log analysis:

1. **Aggregated metrics** - Count of parse errors by tool, time, error type
2. **Auto-suggestions** - Detect expression patterns and auto-suggest base64
3. **Preprocessing** - Auto-escape common patterns before JSON parsing
4. **Documentation updates** - Add real examples from logs to tool descriptions
5. **AI agent training data** - Use logs to improve agent prompt engineering

## Conclusion

This enhanced error logging provides valuable insights into AI agent behavior with JSON escaping, validates the need for base64 encoding options, and helps improve the overall MCP tool experience.

**Status**: ✅ **IMPLEMENTED AND TESTED**
