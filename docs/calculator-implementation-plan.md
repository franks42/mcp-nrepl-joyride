# Calculator Tool Implementation Plan

## Overview

This document details the step-by-step implementation plan for adding a `calculate` tool to the mcp-nrepl-joyride MCP server. The tool provides mathematical computation using Clojure prefix notation with pre-loaded functions and timeout protection.

**Related Documents**:
- [Design Document](calculator-mcp-tool-design.md) - Comprehensive design and rationale
- [AI Test Scenarios](calculator-ai-test-scenarios.md) - Natural language test cases

## Implementation Phases

### Phase 1: Core Implementation (MVP)

**Goal**: Working `calculate` tool with timeout protection and basic logging

**Estimated Time**: 4-6 hours

#### Task 1.1: Create Calculator Namespace

**File**: `src/mcp_nrepl_proxy/calculator.clj`

**Subtasks**:
- [ ] Create namespace with SCI dependency
- [ ] Define `math-fns` map with all pre-loaded functions:
  - [ ] Arithmetic: `+ - * / mod quot rem inc dec`
  - [ ] Powers: `pow sqrt cbrt exp`
  - [ ] Logarithms: `ln log10 log2 logb`
  - [ ] Trigonometry (radians): `sin cos tan asin acos atan atan2 sinh cosh tanh`
  - [ ] Trigonometry (degrees): `sind cosd tand`
  - [ ] Rounding: `abs floor ceil round sign trunc`
  - [ ] Constants: `pi e tau phi`
  - [ ] Comparisons: `< > <= >= = not=`
  - [ ] Vector ops: `sum product vmin vmax count`
  - [ ] Statistics: `mean median variance stdev`
  - [ ] Linear algebra: `dot norm cross`
- [ ] Create SCI context with simplified config (no security paranoia)
- [ ] Implement timeout-protected `calculate` function
- [ ] Add helper functions for type coercion

**Code Structure**:
```clojure
(ns mcp-nrepl-proxy.calculator
  (:require [clojure.edn :as edn]
            [sci.core :as sci]))

(def math-fns {...})

(def sci-ctx
  (sci/init
    {:bindings math-fns
     :allow '[let if when cond -> ->> as-> fn defn]
     :realize-max 10000}))

(defn calculate
  "Evaluate mathematical expression with timeout protection"
  [expr-string]
  (let [timeout-ms 5000
        result-promise (promise)]
    ...))
```

**Acceptance Criteria**:
- [ ] All math functions defined and working
- [ ] SCI context evaluates expressions correctly
- [ ] Timeout protection works (test with `(loop [] (recur))`)
- [ ] Returns EDN map: `{:result ... :type ... :expr ...}`
- [ ] Error handling returns: `{:error ... :type "error" :expr ...}`
- [ ] Timeout returns: `{:error ... :type "timeout" :expr ...}`

#### Task 1.2: Add SCI Dependency

**File**: `bb.edn`

**Subtasks**:
- [ ] Check if `org.babashka/sci` is already in deps
- [ ] Add or update to latest version (0.8.41 or newer)
- [ ] Test that SCI loads correctly: `bb -e "(require '[sci.core :as sci]) :ok"`

**Acceptance Criteria**:
- [ ] SCI dependency resolves
- [ ] Can require `sci.core` in Babashka
- [ ] No version conflicts with existing deps

#### Task 1.3: Integrate Tool in MCP Server

**File**: `src/mcp_nrepl_proxy/core.clj`

**Subtasks**:
- [ ] Require calculator namespace: `[mcp-nrepl-proxy.calculator :as calc]`
- [ ] Add `calculate` to tools list with comprehensive description
- [ ] Create tool description file: `resources/calculate-tool-description.txt`
- [ ] Implement `handle-tool` method for "calculate"
- [ ] Ensure proper JSON-RPC response formatting

**Tool Schema**:
```clojure
{:name "calculate"
 :description (slurp "resources/calculate-tool-description.txt")
 :inputSchema {:type "object"
               :properties {:expr {:type "string"
                                   :description "Clojure mathematical expression"}}
               :required ["expr"]}}
```

**Handler**:
```clojure
(defmethod handle-tool "calculate"
  [{:keys [arguments]}]
  (let [expr (:expr arguments)
        result (calc/calculate expr)
        duration (measure-duration result)]
    (log-calculation expr result duration)
    {:content [{:type "text"
                :text (pr-str result)}]}))
```

**Acceptance Criteria**:
- [ ] Tool appears in `tools/list` MCP response
- [ ] Tool can be called via `tools/call` MCP request
- [ ] Returns properly formatted JSON-RPC response
- [ ] Integrates with existing error handling

#### Task 1.4: Create Logging Infrastructure

**File**: `src/mcp_nrepl_proxy/calculator_analytics.clj`

**Subtasks**:
- [ ] Create analytics namespace
- [ ] Implement `log-calculation` function
- [ ] Create log file: `calculator-usage.edn`
- [ ] Add timestamp, expr, result, duration, success, error fields
- [ ] Implement basic report generation function

**Log Entry Format**:
```clojure
{:timestamp 1705234567890
 :expr "(sqrt (+ (* 3 3) (* 4 4)))"
 :result 5.0
 :result-type "java.lang.Double"
 :duration-ms 12
 :success? true
 :error nil
 :functions-used #{sqrt + *}
 :expr-depth 3}
```

**Acceptance Criteria**:
- [ ] Every calculation logs to file
- [ ] Log entries are valid EDN
- [ ] Can read and parse log file
- [ ] Basic report generation works

#### Task 1.5: Write Unit Tests

**File**: `test/mcp_nrepl_proxy/calculator_test.clj`

**Subtasks**:
- [ ] Test basic arithmetic: `(+ 2 3)`, `(* 4 5)`, `(/ 10 3)`
- [ ] Test math functions: `(sqrt 25)`, `(sin (/ pi 2))`, `(pow 2 10)`
- [ ] Test statistics: `(mean [1 2 3 4 5])`, `(stdev [...])`
- [ ] Test vector ops: `(dot [1 2 3] [4 5 6])`, `(norm [3 4])`
- [ ] Test `let` bindings: `(let [x 3 y 4] (sqrt (+ (* x x) (* y y))))`
- [ ] Test threading: `(-> 100 sqrt (* 2) (+ 5))`
- [ ] Test error handling: `(foo 1 2)` → error
- [ ] Test timeout: `(loop [] (recur))` → timeout
- [ ] Test type preservation: ratios, doubles, longs
- [ ] Test edge cases: `(/ 1.0 0.0)` → Infinity

**Test Template**:
```clojure
(ns mcp-nrepl-proxy.calculator-test
  (:require [clojure.test :refer :all]
            [mcp-nrepl-proxy.calculator :as calc]))

(deftest basic-arithmetic
  (is (= 5 (:result (calc/calculate "(+ 2 3)"))))
  (is (= 14 (:result (calc/calculate "(+ 2 (* 3 4))")))))

(deftest timeout-protection
  (let [result (calc/calculate "(loop [] (recur))")]
    (is (= "timeout" (:type result)))
    (is (contains? result :error))))
```

**Run Tests**:
```bash
bb test-calculator  # Add task to bb.edn
```

**Acceptance Criteria**:
- [ ] All tests pass
- [ ] Test coverage >90% of calculator.clj
- [ ] Timeout test actually triggers timeout
- [ ] Error tests verify error messages

#### Task 1.6: Integration Testing via MCP Client

**Subtasks**:
- [ ] Test via `stdio_mcp_client.py` with calculate tool
- [ ] Verify tool appears in tools list
- [ ] Test successful calculation
- [ ] Test error handling
- [ ] Test timeout (if possible via client)
- [ ] Verify response format matches MCP spec

**Test Commands**:
```bash
# List tools (verify calculate appears)
uv run python scripts/stdio_mcp_client.py \
  --server-cmd "bb -cp src src/mcp_nrepl_proxy/core.clj" \
  --list-tools

# Basic calculation
uv run python scripts/stdio_mcp_client.py \
  --server-cmd "bb -cp src src/mcp_nrepl_proxy/core.clj" \
  --tool calculate --args '{"expr": "(sqrt 144)"}'

# Pythagorean theorem
uv run python scripts/stdio_mcp_client.py \
  --server-cmd "bb -cp src src/mcp_nrepl_proxy/core.clj" \
  --tool calculate --args '{"expr": "(sqrt (+ (* 3 3) (* 4 4)))"}'

# Statistics
uv run python scripts/stdio_mcp_client.py \
  --server-cmd "bb -cp src src/mcp_nrepl_proxy/core.clj" \
  --tool calculate --args '{"expr": "(mean [1 2 3 4 5])"}'

# Error case
uv run python scripts/stdio_mcp_client.py \
  --server-cmd "bb -cp src src/mcp_nrepl_proxy/core.clj" \
  --tool calculate --args '{"expr": "(foo 1 2)"}'
```

**Acceptance Criteria**:
- [ ] All test commands succeed
- [ ] Responses are valid JSON-RPC
- [ ] Results match expected values
- [ ] Errors are handled gracefully

### Phase 2: AI-Driven Testing (Validation)

**Goal**: Create natural language test scenarios that LLMs can read and execute

**Estimated Time**: 2-3 hours

#### Task 2.1: Create AI Test Scenarios Document

**File**: `docs/calculator-ai-test-scenarios.md`

**Structure**:
```markdown
# Calculator Tool - AI Test Scenarios

## How to Use This Document

This document contains test scenarios written in natural language.
An AI assistant should:
1. Read each scenario
2. Use the `calculate` tool to solve the problem
3. Verify the result matches the expected answer
4. Report pass/fail for each scenario

## Test Scenarios

### Scenario 1: Basic Arithmetic
**Description**: Calculate 2 + 3
**Expected Result**: 5
**Pass Criteria**: Result equals 5 exactly

### Scenario 2: Pythagorean Theorem
**Description**: Calculate the hypotenuse of a right triangle with sides 3 and 4
**Expected Result**: 5.0
**Pass Criteria**: Result is 5.0 ± 0.001

### Scenario 3: Compound Interest
**Description**: Calculate the future value of $10,000 invested at 5% annual interest for 10 years
**Formula**: FV = P × (1 + r)^n
**Expected Result**: ~16,288.95
**Pass Criteria**: Result is between 16,288 and 16,289
```

**Subtasks**:
- [ ] Create 20-30 test scenarios covering:
  - [ ] Basic arithmetic (5 scenarios)
  - [ ] Trigonometry (5 scenarios)
  - [ ] Statistics (5 scenarios)
  - [ ] Financial calculations (3 scenarios)
  - [ ] Vector operations (3 scenarios)
  - [ ] Complex nested expressions (3 scenarios)
  - [ ] Edge cases (3 scenarios)
- [ ] Include expected results with precision tolerances
- [ ] Add pass/fail criteria for each scenario
- [ ] Include both simple and complex calculations

**Acceptance Criteria**:
- [ ] Document is readable by humans and LLMs
- [ ] Each scenario has clear description, expected result, pass criteria
- [ ] Covers all major function categories
- [ ] Includes edge cases (infinity, precision, errors)

#### Task 2.2: Create AI Test Executor Script

**File**: `scripts/run_ai_test_scenarios.py`

**Purpose**: Script that an AI can use to execute test scenarios and report results

**Features**:
- [ ] Read calculator-ai-test-scenarios.md
- [ ] Parse scenarios (title, description, expected result)
- [ ] Execute each scenario using calculate tool
- [ ] Compare actual vs expected results
- [ ] Generate pass/fail report
- [ ] Output summary statistics

**Usage**:
```bash
# AI runs this to execute all scenarios
uv run python scripts/run_ai_test_scenarios.py

# Output:
# Running 25 test scenarios...
# ✓ Scenario 1: Basic Arithmetic - PASS
# ✓ Scenario 2: Pythagorean Theorem - PASS
# ✗ Scenario 3: Compound Interest - FAIL (expected 16288.95, got 16288.94)
# ...
# Results: 24/25 passed (96% success rate)
```

**Acceptance Criteria**:
- [ ] Can parse markdown test scenarios
- [ ] Executes calculate tool calls
- [ ] Compares results with tolerances
- [ ] Generates readable report
- [ ] Works with stdio_mcp_client.py

#### Task 2.3: Manual AI Testing Session

**Process**:
- [ ] Start MCP server
- [ ] Provide AI with calculator-ai-test-scenarios.md
- [ ] Ask AI to execute each scenario
- [ ] AI uses calculate tool and reports results
- [ ] Document AI's tool selection behavior
- [ ] Note any scenarios where AI struggles

**Documentation**:
Create `docs/calculator-ai-testing-results.md` with:
- [ ] Date and AI model used
- [ ] Scenario-by-scenario results
- [ ] Tool selection accuracy
- [ ] Observations on AI behavior
- [ ] Recommendations for improvement

**Acceptance Criteria**:
- [ ] AI successfully executes >90% of scenarios
- [ ] AI consistently chooses calculate tool for math
- [ ] Results are accurate
- [ ] Any failures are documented with explanations

### Phase 3: Analytics and Monitoring

**Goal**: Deploy logging and create analytics tools

**Estimated Time**: 2-3 hours

#### Task 3.1: Create Usage Report Generator

**File**: `scripts/analyze_calculator_usage.clj`

**Features**:
- [ ] Read calculator-usage.edn
- [ ] Generate summary statistics:
  - [ ] Total calculations
  - [ ] Success rate
  - [ ] Average duration
  - [ ] Function frequency distribution
  - [ ] Error frequency distribution
  - [ ] Average expression depth
  - [ ] let/threading usage rates
- [ ] Output formatted report

**Usage**:
```bash
bb analyze-calculator-usage

# Output:
# Calculator Usage Report (2025-01-14 to 2025-02-14)
# =================================================
# Total calculations: 347
# Success rate: 96.5% (335/347)
# Average duration: 15ms
# Timeout rate: 0.3% (1/347)
#
# Top 10 Functions:
# 1. + (245 uses)
# 2. * (198 uses)
# 3. sqrt (87 uses)
# ...
#
# Common Errors:
# 1. "Unknown symbol: foo" (8 occurrences)
# 2. "Division by zero" (3 occurrences)
```

**Acceptance Criteria**:
- [ ] Parses all log entries correctly
- [ ] Computes accurate statistics
- [ ] Output is human-readable
- [ ] Can handle large log files (1000+ entries)

#### Task 3.2: Create Dashboard/Monitoring

**File**: `docs/calculator-dashboard.md`

**Purpose**: Live-updating dashboard for calculator usage

**Features**:
- [ ] Recent calculations (last 10)
- [ ] Real-time success rate
- [ ] Function popularity chart (ASCII art)
- [ ] Error trend over time
- [ ] Performance metrics (p50, p95, p99 duration)

**Update Mechanism**:
```bash
# Regenerate dashboard from logs
bb update-calculator-dashboard

# Output written to docs/calculator-dashboard.md
# Can be viewed in editor or rendered
```

**Acceptance Criteria**:
- [ ] Dashboard updates from log file
- [ ] Shows meaningful metrics
- [ ] Readable in markdown format
- [ ] Updates quickly (<1 second)

### Phase 4: Documentation and Deployment

**Goal**: Complete documentation and prepare for release

**Estimated Time**: 2 hours

#### Task 4.1: Update Main Documentation

**Files to Update**:
- [ ] `README.md` - Add calculate tool to features list
- [ ] `CLAUDE.md` - Add usage examples and testing notes
- [ ] `docs/TESTING-GUIDE.md` - Include calculator testing procedures

**Content**:
```markdown
## Calculator Tool

The mcp-nrepl-joyride server includes a dedicated `calculate` tool for mathematical computations.

**Features**:
- Pre-loaded math functions (trig, stats, linear algebra)
- Clojure prefix notation (no precedence ambiguity)
- Timeout protection (5 second default)
- Comprehensive logging and analytics

**Usage**:
```json
{
  "tool": "calculate",
  "arguments": {
    "expr": "(sqrt (+ (* 3 3) (* 4 4)))"
  }
}
```

**Result**:
```json
{
  "result": 5.0,
  "type": "java.lang.Double",
  "expr": "(sqrt (+ (* 3 3) (* 4 4)))"
}
```
```

**Acceptance Criteria**:
- [ ] README includes calculator tool
- [ ] CLAUDE.md has usage examples
- [ ] Testing guide includes calculator tests
- [ ] All docs are consistent

#### Task 4.2: Create User Guide

**File**: `docs/calculator-user-guide.md`

**Sections**:
- [ ] Introduction and motivation
- [ ] Quick start examples
- [ ] Complete function reference
- [ ] Advanced usage (let, threading, vectors)
- [ ] Error handling
- [ ] Performance tips
- [ ] Troubleshooting

**Acceptance Criteria**:
- [ ] Comprehensive coverage of all features
- [ ] 20+ examples
- [ ] Copy-pasteable code snippets
- [ ] Beginner-friendly explanations

#### Task 4.3: Create Release Notes

**File**: `CHANGELOG.md` (or append to existing)

**Content**:
```markdown
## [v0.7.0] - 2025-01-XX - Calculator Tool Release

### Added
- **New Tool: `calculate`** - Dedicated mathematical computation tool
  - 50+ pre-loaded math functions
  - Clojure prefix notation
  - Timeout protection (5s default)
  - Statistics: mean, median, stdev, variance
  - Linear algebra: dot product, norm, cross product
  - Complete logging and analytics

### Changed
- Updated tool descriptions for better LLM guidance
- Enhanced error messages with function suggestions

### Documentation
- Added calculator-user-guide.md
- Added calculator-ai-test-scenarios.md
- Updated testing guide with calculator tests
```

**Acceptance Criteria**:
- [ ] Clear description of new features
- [ ] Migration notes if needed
- [ ] Links to documentation
- [ ] Breaking changes noted (none expected)

## Testing Strategy

### Traditional RPC Testing

**Unit Tests** (`test/mcp_nrepl_proxy/calculator_test.clj`):
- Test all math functions individually
- Test expression composition (nested, let, threading)
- Test error handling
- Test timeout protection
- Test type preservation

**Integration Tests** (via `stdio_mcp_client.py`):
- Test tool registration (appears in tools list)
- Test tool calling (correct request/response format)
- Test end-to-end calculation flow
- Test error responses
- Test JSON-RPC compliance

**Run All Tests**:
```bash
# Unit tests
bb test-calculator

# Integration tests
bb test-calculator-integration

# Or combined
bb test-all
```

### AI-Driven Testing

**Natural Language Test Scenarios** (`docs/calculator-ai-test-scenarios.md`):

Each scenario follows this format:
```markdown
### Scenario N: [Name]
**Description**: [Natural language description of calculation needed]
**User Request**: "[Exact user prompt]"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `[Clojure expr]`
3. Verify result: [expected value]

**Expected Result**: [Numerical result]
**Pass Criteria**: [Acceptance criteria with tolerances]
**Notes**: [Any special considerations]
```

**Example**:
```markdown
### Scenario 5: Financial - Compound Interest
**Description**: Calculate future value of investment with compound interest
**User Request**: "I invested $10,000 at 5% annual interest for 10 years. What's it worth now?"
**Expected AI Behavior**:
1. Select `calculate` tool (not nrepl-eval)
2. Construct: `(let [p 10000 r 0.05 n 10] (* p (pow (+ 1 r) n)))`
3. Verify result is approximately 16,288.95

**Expected Result**: 16288.95
**Pass Criteria**: Result is between 16,288.00 and 16,289.00
**Notes**: Tests let bindings and pow function
```

**How AI Executes Tests**:
1. Read scenario description
2. Understand the calculation needed
3. Call calculate tool with appropriate expression
4. Compare result to expected value
5. Report pass/fail based on criteria

**Benefits of AI-Driven Testing**:
- ✅ Tests actual LLM behavior (tool selection, expression construction)
- ✅ Validates tool description effectiveness
- ✅ Human-readable test cases (documentation + tests in one)
- ✅ Easy to add new scenarios (no coding required)
- ✅ Reveals UX issues that unit tests miss

### Manual Testing Checklist

Before release:
- [ ] Start MCP server successfully
- [ ] Calculate tool appears in tools list
- [ ] Basic arithmetic works (2+3=5)
- [ ] Complex expression works (Pythagorean theorem)
- [ ] Statistics work (mean, stdev)
- [ ] Vector operations work (dot product)
- [ ] Error handling works (undefined function)
- [ ] Timeout protection works (infinite loop)
- [ ] Logging creates entries
- [ ] Analytics report generates
- [ ] AI test scenarios execute successfully
- [ ] AI consistently chooses calculate for math

## Success Criteria

### Technical Requirements
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] >90% AI test scenarios pass
- [ ] Tool appears in MCP tools list
- [ ] Responses are valid JSON-RPC
- [ ] Timeout protection works reliably
- [ ] Logging captures all calculations
- [ ] No performance degradation to MCP server

### User Experience Requirements
- [ ] AI consistently chooses calculate for math (>80% rate)
- [ ] Results are accurate (>95% success rate)
- [ ] Error messages are helpful
- [ ] Response time <100ms average
- [ ] Timeout rate <1%
- [ ] Documentation is clear and comprehensive

### Analytics Requirements
- [ ] Every calculation logged
- [ ] Usage report generates successfully
- [ ] Can identify top functions
- [ ] Can identify error patterns
- [ ] Can measure tool selection accuracy

## Timeline Estimate

| Phase | Tasks | Time | Dependencies |
|-------|-------|------|--------------|
| Phase 1.1-1.2 | Calculator namespace + deps | 2h | None |
| Phase 1.3 | MCP integration | 1h | Phase 1.1 |
| Phase 1.4 | Logging infrastructure | 1h | Phase 1.1 |
| Phase 1.5 | Unit tests | 2h | Phase 1.1-1.4 |
| Phase 1.6 | Integration tests | 1h | Phase 1.3 |
| Phase 2.1 | AI test scenarios | 2h | Phase 1.6 |
| Phase 2.2-2.3 | AI testing execution | 2h | Phase 2.1 |
| Phase 3.1-3.2 | Analytics tools | 2h | Phase 1.4 |
| Phase 4.1-4.3 | Documentation | 2h | All above |
| **Total** | | **15h** | |

**Estimated Calendar Time**: 2-3 days of focused work

## Risk Mitigation

### Potential Issues

**Risk**: AI doesn't prefer calculate over nrepl-eval
- **Mitigation**: Monitor tool selection in logs
- **Fallback**: Enhance nrepl-eval description, deprecate calculate

**Risk**: Timeout doesn't work reliably
- **Mitigation**: Extensive timeout testing in Phase 1.5
- **Fallback**: Increase timeout, add max-eval-depth limits

**Risk**: SCI evaluation differs from nREPL
- **Mitigation**: Comparative testing between SCI and nREPL results
- **Fallback**: Document differences, add SCI-specific behaviors to user guide

**Risk**: Performance degradation
- **Mitigation**: Benchmark MCP server with/without calculate tool
- **Fallback**: Lazy-load calculator namespace, optimize SCI context creation

**Risk**: Missing critical math functions
- **Mitigation**: Monitor error logs for "unknown symbol" patterns
- **Fallback**: Iteratively add requested functions based on usage data

## Post-Deployment Monitoring

### Week 1: Intensive Monitoring
- [ ] Check logs daily
- [ ] Monitor error rates
- [ ] Track tool selection accuracy
- [ ] Respond to user feedback quickly

### Weeks 2-4: Usage Analysis
- [ ] Generate weekly usage reports
- [ ] Identify most-used functions
- [ ] Identify missing functions (error patterns)
- [ ] Measure AI tool selection rate
- [ ] Compare calculate vs nrepl-eval usage for math

### Month 2: Decision Point
Based on analytics, decide:
- **High usage + good selection** → Keep and enhance (add Phase 2 features)
- **Low usage** → Deprecate, improve nrepl-eval instead
- **High misuse** → Revise tool description, add more examples

## Next Steps

To begin implementation:

1. **Review this plan** - Ensure all tasks are clear
2. **Create feature branch** - `git checkout -b feature/calculator-tool`
3. **Start with Phase 1.1** - Create calculator namespace
4. **Work sequentially** - Complete each task before moving to next
5. **Test continuously** - Run tests after each task
6. **Update TODO.md** - Track progress in project todo list
7. **Commit frequently** - Small, atomic commits with clear messages

**Ready to start?** Begin with Task 1.1: Create Calculator Namespace.
