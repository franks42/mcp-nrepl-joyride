# Calculator Tool Implementation Plan

## Overview

This document details the implementation plan for the `calculate` tool in the mcp-nrepl-joyride MCP server. The tool provides mathematical computation using Clojure prefix notation with pre-loaded functions, timeout protection, and type-safe token conversion capabilities.

**Current Focus**: Phase 3E - Token Formatting Utilities

**Related Documents**:
- [Design Document](calculator-mcp-tool-design.md) - Comprehensive design and rationale
- [AI Test Scenarios](calculator-ai-test-scenarios.md) - Natural language test cases

---

## Completed Phases Summary

All completed implementation details are preserved in git history. See commit logs for detailed task breakdowns.

### Phase 1: Core Implementation (v1.0.0) ✅ COMPLETED
- Core calculator namespace with 50+ pre-loaded math functions
- SCI-based evaluation with timeout protection (5 second default)
- MCP tool integration with JSON-RPC interface
- Basic analytics logging

### Phase 2: Testing & Validation (v1.0.0) ✅ COMPLETED
- Comprehensive unit tests (79 assertions passing)
- AI-driven test scenarios (93% pass rate)
- Integration testing via MCP client

### Phase 2.5: Base64 Encoding & Error Logging (v2.5.0) ✅ COMPLETED
- Optional base64 encoding for complex expressions (input-base64/output-base64 flags)
- JSON parse error logging with automatic issue detection
- Production deployment and validation

### Phase 3A: DeFi & Financial Functions (v2.6.0) ✅ COMPLETED
- Financial calculations: ROI, compound interest, market share
- DeFi operations: impermanent loss, slippage, APY/APR conversions
- Cryptocurrency unit conversions: wei/ether, sats/btc, hash/nhash
- Date/time utilities: unix timestamps, lock periods

### Phase 3B: Type-Safe Token Conversion (v3.1.0) ✅ COMPLETED
- Tuple-based token amounts: `[amount unit]` with keyword/string support
- Division notation for rates: `[:/ [num unit] [denom unit]]`
- Format preservation: keyword/string/case flexibility
- Token conversion functions: `token-convert`, `invert-rate`, `compose-rates`, `normalize-rate`
- Comprehensive validation and error handling

### Phase 3C: Portfolio Aggregation (v3.2.0) ✅ COMPLETED
- `portfolio-value` function for multi-token portfolio valuation
- Auto-matching rates with bidirectional coverage (normalize + invert)
- Compatible-units registry for same-token denominations (hash/nhash, btc/sats)
- Format preservation across all operations

### Phase 3D: Ergonomic Improvements (v3.3.0) ✅ COMPLETED
- `rate` convenience constructor: `(rate 0.032 :usd :per :hash)` → `[:/ [0.032 :usd] [1 :hash]]`
- Natural syntax for creating exchange rates
- Full integration with token conversion system

---

## Current Phase: Phase 3E - Token Formatting Utilities (v3.4.0)

**Goal**: Format token amounts and exchange rates for human-readable presentation with flexible output options.

**Motivation**: Real-world feedback revealed calculation results need better formatting:
- Scientific notation is hard to read: `[1.750000000000403E7 "hash"]`
- Users want thousands separators: `"17,500,000 HASH"`
- Reports/UIs need structured components for charts/tables
- Exchange rates need clear "X per Y" format

**Estimated Time**: 3.5-4.5 hours
**Status**: 📋 **PLANNED**

### Task 3E.1: Core Formatting Functions (PRIMARY)

**File**: `src/nrepl_mcp_server/calculator.clj`

**Function Signatures**:
```clojure
;; Primary API - auto-detects token vs rate
(format value)
(format value options)

;; Underlying implementations (can be called directly)
(format-token token-tuple)
(format-token token-tuple options)

(format-rate rate-tuple)
(format-rate rate-tuple options)
```

**Subtasks**:
- [ ] Implement `format-with-separators` helper (thousands + decimal separators)
- [ ] Implement `auto-decimals` smart decimal place selection
- [ ] Implement `currency-symbols` registry
- [ ] Implement `format-token` with string output
- [ ] Implement `format-rate` with string output (e.g., "$0.032 per HASH")
- [ ] Implement `format` dispatcher with type detection
- [ ] Implement component map output (`:components true`) for both tokens and rates
- [ ] Handle edge cases (zero, negative, very large/small numbers)
- [ ] Export `format`, `format-token`, and `format-rate` in `token-conversion-fns` map

**Examples - Token Amounts**:
```clojure
;; Primary API - auto-detects type
(format [1.750000000000403E7 "hash"])
=> "17,500,000 HASH"

(format [32.156789 :usd])
=> "$32.16 USD"

;; Component map output
(format [123456789.12 :usd] {:components true})
=> {:type :token
    :amt "123,456,789.12"
    :unit "USD"
    :symbol "$"
    :formatted "$123,456,789.12 USD"
    :raw-amount 123456789.12}
```

**Examples - Exchange Rates**:
```clojure
;; Primary API - auto-detects rate
(format [:/ [0.032 :usd] [1 :hash]])
=> "$0.032 per HASH"

(format [:/ [31.25 :hash] [1 :usd]])
=> "31.25 HASH per USD"

;; Non-normalized rates auto-normalize
(format [:/ [0.064 :usd] [2 :hash]])
=> "$0.032 per HASH"

;; Component map output
(format [:/ [0.032 :usd] [1 :hash]] {:components true})
=> {:type :rate
    :numerator {:amt "0.032" :unit "USD" :symbol "$"}
    :denominator {:amt "1" :unit "HASH" :symbol nil}
    :formatted "$0.032 per HASH"
    :raw-rate [:/ [0.032 :usd] [1 :hash]]}
```

**Options Map**:
```clojure
{:decimals nil         ; nil = auto (smart), or 0-8 for explicit
 :symbol true          ; Show currency symbol ($, €, etc.) if available
 :uppercase true       ; Uppercase unit symbols (USD vs usd)
 :thousands-sep ","    ; Thousands separator character
 :decimal-sep "."      ; Decimal separator character
 :components false}    ; Return component map instead of string
```

**Auto-Decimals Logic**:
```clojure
(defn- auto-decimals
  "Smart decimal place selection based on amount size"
  [amount unit]
  (cond
    (< amount 0.01) 8     ; Tiny amounts - show precision
    (< amount 1) 6        ; Small amounts
    (< amount 1000) 2     ; Standard currency
    :else                 ; Large amounts
    (let [frac (- amount (long amount))]
      (if (< frac 0.01) 0 2))))
```

**Currency Symbol Support**:
```clojure
(def currency-symbols
  {:usd "$"
   :eur "€"
   :gbp "£"
   :jpy "¥"
   :btc "₿"
   :eth "Ξ"
   ;; Add more as needed
   })
```

**Type Detection and Dispatch**:
```clojure
(defn format
  "Format token amounts or exchange rates.
   Auto-detects type and delegates to format-token or format-rate."
  ([value] (format value {}))
  ([value options]
   (cond
     ;; 3-element vector starting with :/ -> rate
     (and (vector? value)
          (= 3 (count value))
          (#{:/ '/ "/"} (first value)))
     (format-rate value options)

     ;; 2-element vector -> token amount
     (and (vector? value) (= 2 (count value)))
     (format-token value options)

     :else
     (throw (ex-info "Value must be token amount or rate"
                     {:provided value})))))
```

**Acceptance Criteria**:
- [ ] `format` auto-detects token vs rate correctly
- [ ] `format-token` formats token amounts with thousands separators
- [ ] `format-rate` formats rates as "X per Y" with normalization
- [ ] Smart decimal place selection works correctly
- [ ] Currency symbols appear for known currencies
- [ ] Options map controls all formatting aspects
- [ ] Component map output includes all fields with `:type` discriminator
- [ ] No scientific notation in output
- [ ] Edge cases handled (zero, negative, very large/small)
- [ ] Unit tests for all three functions
- [ ] Exported in `token-conversion-fns` map

### Task 3E.2: Usage Examples (DOCUMENTATION ONLY)

**Purpose**: Show users how to compose formatting patterns, but don't prescribe implementations.

**File**: `docs/calculator-mcp-tool-design.md` - Examples section

**Subtasks**:
- [ ] Document portfolio summary pattern using `format`
- [ ] Document rate comparison pattern using `format`
- [ ] Document calculation steps pattern using `format`
- [ ] Document table building with component maps
- [ ] Add to MCP tool description as usage examples

**Note**: These are **examples**, not exported functions. Users can adapt patterns to their needs.

**Portfolio Summary Pattern** (Example):
```clojure
;; Users build their own portfolio summary
(let [holdings [[1000 :hash] [5E7 :nhash] [10 :usd]]
      to-unit :usd
      rates [[:/ [0.032 :usd] [1 :hash]]]
      total (portfolio-value holdings to-unit rates)
      lines (map #(str (format %1) "  " (format (token-convert %1 to-unit rates)))
                 holdings)]
  (str/join "\n"
            (concat ["Portfolio Summary" "================"]
                    lines
                    ["────────────────────────"
                     (str "Total: " (format total))])))
=>
"Portfolio Summary
================
1,000 HASH       $32.00
50,000,000 NHASH  $1.60
10 USD           $10.00
────────────────────────
Total:           $43.60"
```

**Rate Comparison Pattern** (Example):
```clojure
;; Users build their own rate comparison
(let [rates [[:/ [0.032 :usd] [1 :hash]]
             [:/ [0.0315 :usd] [1 :hash]]
             [:/ [0.0325 :usd] [1 :hash]]]
      sources ["Coinbase" "Kraken" "Binance"]
      lines (map #(str %2 ":  " (format %1)) rates sources)]
  (str/join "\n"
            (concat ["Exchange Rates (USD per HASH)"
                     "=============================="]
                    lines)))
=>
"Exchange Rates (USD per HASH)
==============================
Coinbase:  $0.032 per HASH
Kraken:    $0.0315 per HASH
Binance:   $0.0325 per HASH"
```

**Acceptance Criteria**:
- [ ] Portfolio summary pattern documented with example
- [ ] Rate comparison pattern documented with example
- [ ] Calculation steps pattern documented with example
- [ ] Component map usage examples shown
- [ ] Examples added to MCP tool description
- [ ] All examples use the generic `format` function

### Task 3E.3: Documentation Updates

**Files to Update**:
- [ ] `src/nrepl_mcp_server/mcp_server/tools/calculate.clj` - Add `format`, `format-token`, and `format-rate` to function list
- [ ] `src/nrepl_mcp_server/mcp_server/tools/calculate.clj` - Add usage examples (portfolio, rates, steps patterns)
- [ ] `docs/calculator-ai-test-scenarios.md` - Add formatting test scenarios
- [ ] Update MCP tool description with type auto-detection explanation

**New Scenarios to Add**:
```markdown
### Scenario 71: Auto-Detection - Token Amount
**Expected AI Behavior**:
1. Call: (format [17500000000004030 :nhash])
2. Verify result: "17,500,000,000,004,030 NHASH"

### Scenario 72: Auto-Detection - Exchange Rate
**Expected AI Behavior**:
1. Call: (format [:/ [0.032 :usd] [1 :hash]])
2. Verify result: "$0.032 per HASH"

### Scenario 73: Component Map - Token Amount
**Expected AI Behavior**:
1. Call: (format [123456.78 :usd] {:components true})
2. Verify keys: :type, :amt, :unit, :symbol, :formatted, :raw-amount

### Scenario 74: Component Map - Exchange Rate
**Expected AI Behavior**:
1. Call: (format [:/ [0.032 :usd] [1 :hash]] {:components true})
2. Verify keys: :type, :numerator, :denominator, :formatted, :raw-rate

### Scenario 75: Custom Pattern - Portfolio Summary
**Expected AI Behavior**:
1. Build portfolio summary using (format ...) in map operations
2. Verify readable table output
```

**Acceptance Criteria**:
- [ ] Tool description includes `format`, `format-token`, and `format-rate`
- [ ] Type auto-detection explained in MCP description
- [ ] At least 5 new AI test scenarios added
- [ ] MCP description shows component map usage for both tokens and rates
- [ ] Usage patterns documented (not prescriptive helpers)
- [ ] All documentation consistent

---

## Implementation Timeline

| Task | Estimated Time | Dependencies |
|------|----------------|--------------|
| 3E.1: Core formatting functions | 2.5-3h | Phase 3B (token amounts) |
| 3E.2: Usage examples documentation | 0.5h | 3E.1 |
| 3E.3: Documentation and test scenarios | 0.5-1h | 3E.1, 3E.2 |
| **Total** | **3.5-4.5h** | |

**Estimated Calendar Time**: 0.5-1 day focused work

---

## Success Criteria

### Technical Requirements
- [ ] `format` auto-detects token vs rate correctly
- [ ] `format-token` eliminates scientific notation
- [ ] `format-rate` produces "X per Y" format with normalization
- [ ] Thousands separators work correctly
- [ ] Auto-decimals logic is smart and useful
- [ ] Component maps have all required fields with `:type` discriminator
- [ ] Component maps support both tokens and rates
- [ ] All unit tests passing
- [ ] Code formatted and linted

### User Experience Requirements
- [ ] Output is human-readable for both tokens and rates
- [ ] Currency symbols appear appropriately
- [ ] Component maps work in tables/charts
- [ ] Generic `format` function is intuitive (no manual type checking)
- [ ] Usage patterns are flexible (not prescriptive)
- [ ] AI test scenarios pass (>90%)

### Code Quality Requirements
- [ ] Code formatted with cljfmt
- [ ] clj-kondo shows no warnings
- [ ] Comprehensive test coverage (>90%)
- [ ] Documentation complete and accurate

---

## Status

**Current**: ✅ **PHASE 3D.1 COMPLETED** (2025-11-15)
**Implementation**: rate convenience constructor with natural syntax
**Next**: Phase 3E token formatting utilities (format, format-token, format-rate)

---

## Historical Reference

For detailed implementation history of completed phases, see git commit logs:
- Phase 1-2: v1.0.0 commits
- Phase 2.5: v2.5.0 commits
- Phase 3A: v2.6.0 commits
- Phase 3B: v3.1.0 commits
- Phase 3C: v3.2.0 commits
- Phase 3D: v3.3.0 commits
