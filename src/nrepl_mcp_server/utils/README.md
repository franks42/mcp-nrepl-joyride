# Utils Namespace

This namespace contains utility modules that are used across the nREPL-MCP server.

## Modules

### `uuid-v7`
RFC 9562 compliant UUID v7 implementation for generating time-ordered unique identifiers.

**Usage:**
```clojure
(require '[nrepl-mcp-server.utils.uuid-v7 :as uuid])

;; Generate a UUID v7
(uuid/uuid-v7)
;; => "0193a567-89ab-7def-8123-456789abcdef"

;; Generate with tag suffix
(uuid/uuid-v7-with-tag :tag "msg")
;; => "0193a567-89ab-7def-8123-456789abcdef-msg"

;; Extract timestamp from UUID
(uuid/extract-timestamp-ms "0193a567-89ab-7def-8123-456789abcdef")
;; => 1736842284567
```

**Features:**
- Temporal ordering guarantees
- 67M IDs per millisecond capacity
- No random fallback - waits for next millisecond on overflow
- Used for nREPL message correlation

## Future Utils

Potential utilities to add to this namespace:
- String manipulation helpers
- Time/date utilities
- Validation functions
- Encoding/decoding helpers