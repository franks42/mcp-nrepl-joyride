# Calculator MCP Tool Design Document

## Executive Summary

Add a dedicated `calculate` tool to the mcp-nrepl-joyride MCP server that provides a clean, well-documented interface for mathematical computations using Clojure syntax. This tool runs alongside the existing `nrepl-eval` tool but is optimized for pure mathematical operations with pre-loaded functions and typed results.

## Background and Motivation

### The Problem

AI assistants (LLMs) often struggle with numerical calculations not because they can't compute, but because:

1. **No tool available** - Chat-only contexts force "mental math" which leads to hallucination
2. **Wrong tool granularity** - Separate `add()`, `multiply()`, `sqrt()` tools require multiple calls and chain errors
3. **Ambiguous syntax** - Infix notation has precedence confusion; custom DSLs add translation overhead
4. **No clear signal** - When full eval is available, AI may not recognize when to use external computation vs. attempt in-context reasoning

### Why Clojure Syntax

After evaluating options (Python, RPN/HP-42 style, custom DSL, infix), Clojure prefix notation emerged as optimal:

- **No precedence ambiguity**: `(+ 2 (* 3 4))` is unambiguous - no PEMDAS confusion
- **Uniform structure**: Every operation is `(fn arg1 arg2 ...)` - no special cases
- **Rich numeric tower**: Ratios, BigDecimals, exact arithmetic when needed
- **Existing ecosystem**: Claude is already trained on Clojure syntax
- **Your infrastructure**: Runs in the same Babashka instance as mcp-nrepl-joyride

### Why a Dedicated Tool (vs. just using nrepl-eval)

1. **Intent clarity**: Tool description signals "use this for math"
2. **Pre-loaded symbols**: No `(require ...)` ceremony
3. **Typed output**: Always returns `{:result ... :type ... :expr ...}`
4. **Usage analytics**: Track what calculations AI actually performs
5. **Documentation as API**: Rich tool description serves as reference

## Use Cases

### Primary Use Cases (High Frequency Expected)

1. **Basic arithmetic with clear precedence**
   ```clojure
   (/ (+ 100 (* 0.15 100)) 12)  ; monthly payment calculation
   ```

2. **Trigonometry and geometry**
   ```clojure
   (sqrt (+ (pow 3 2) (pow 4 2)))  ; Pythagorean theorem
   (sin (/ pi 4))                   ; trig functions
   ```

3. **Statistical aggregations**
   ```clojure
   (mean [23 45 67 89 12])
   (stdev [1.2 3.4 5.6 7.8 9.0])
   ```

4. **Unit conversions with clear steps**
   ```clojure
   (-> 100 ; km/h
       (* 1000)    ; m/h
       (/ 3600))   ; m/s
   ```

### Secondary Use Cases (Medium Frequency)

5. **Financial calculations**
   ```clojure
   (let [principal 10000
         rate 0.05
         years 10]
     (* principal (pow (+ 1 rate) years)))
   ```

6. **Vector/linear algebra basics**
   ```clojure
   (dot [1 2 3] [4 5 6])
   (norm [3 4])
   ```

### Edge Cases to Monitor

7. **Complex nested expressions** - Do they get too deep?
8. **Data transformation** - Does AI try to use `map`/`filter`? (Should redirect to nrepl-eval)
9. **Side effects** - Any attempts to do I/O? (Should fail cleanly)

## Interface Specification

### Tool Schema (Current v2.5.0)

```json
{
  "name": "calculate",
  "description": "Evaluate mathematical expressions using Clojure prefix notation.\n\n**USE THIS TOOL WHEN:**\n- User asks for calculations, math problems, or numerical analysis\n- Computing averages, percentages, statistics, or data analysis\n- Solving geometry problems (areas, volumes, distances, angles)\n- Financial calculations (interest, percentages, payments)\n- Physics/engineering formulas (forces, velocities, trajectories)\n- Vector operations (dot products, cross products, magnitudes)\n- Converting units or temperatures\n\n**NOTE:** Supports optional base64 encoding (input-base64/output-base64 flags) to avoid JSON escaping issues with complex expressions.\n\n**Pre-loaded functions (50+ functions, no imports needed):**\n\n**Arithmetic:** + - * / mod quot rem inc dec\n**Powers:** pow sqrt cbrt exp\n**Logarithms:** ln log10 log2 logb\n**Trigonometry (radians):** sin cos tan asin acos atan atan2 sinh cosh tanh\n**Trigonometry (degrees):** sind cosd tand\n**Rounding:** abs floor ceil round sign trunc\n**Constants:** pi e tau phi (golden ratio)\n**Comparisons:** < > <= >= = not=\n\n**Vector operations (on sequences):**\n  sum product vmin vmax count\n  mean median stdev variance\n  dot norm cross (3D only)\n\n**Examples:**\n  (+ 2 3)                              => 5\n  (sqrt (+ (pow 3 2) (pow 4 2)))       => 5.0\n  (sin (/ pi 2))                       => 1.0\n  (mean [1 2 3 4 5])                   => 3\n  (-> 100 sqrt (* 2) (+ 5))            => 25.0\n  (let [x 3 y 4] (sqrt (+ (* x x) (* y y)))) => 5.0\n  (/ 10 3)                             => 10/3 (ratio)\n  (/ 10.0 3.0)                         => 3.333...\n\n**Returns:** JSON map with :result, :type, and :expr keys\n**Timeout:** 5 seconds (prevents hanging on infinite loops)\n\n**Optional Base64 Encoding:**\n- input-base64 (boolean): Interpret 'expr' parameter as base64-encoded string (default: false)\n- output-base64 (boolean): Return result fields as base64 encoded strings (default: false)\n- Use base64 to avoid JSON escaping issues with complex expressions",
  "inputSchema": {
    "type": "object",
    "properties": {
      "expr": {
        "type": "string",
        "description": "Clojure mathematical expression in prefix notation (or base64-encoded if input-base64=true)"
      },
      "input-base64": {
        "type": "boolean",
        "description": "Interpret 'expr' parameter as base64-encoded string (default: false)"
      },
      "output-base64": {
        "type": "boolean",
        "description": "Return result fields as base64 encoded strings (default: false)"
      }
    },
    "required": ["expr"]
  }
}
```

### Input Format

EDN/Clojure expression as a string:

```clojure
"(sqrt (+ (* 3 3) (* 4 4)))"
```

### Output Format

EDN map (returned as JSON for MCP):

```clojure
{:result 5.0
 :type "java.lang.Double"
 :expr "(sqrt (+ (* 3 3) (* 4 4)))"}
```

**Error case:**

```clojure
{:error "Unknown symbol: foo"
 :expr "(foo 1 2)"
 :type "error"}
```

## Implementation

### Core Calculator Namespace

**Actual Implementation**: `src/nrepl_mcp_server/calculator.clj`

```clojure
(ns nrepl-mcp-server.calculator
  (:require [clojure.edn :as edn]
            [sci.core :as sci]))

;; Math function definitions
(def math-fns
  {;; Core arithmetic
   '+ +, '- -, '* *, '/ /
   'mod mod, 'quot quot, 'rem rem
   'inc inc, 'dec dec
   
   ;; Powers and roots
   'pow #(Math/pow (double %1) (double %2))
   'sqrt #(Math/sqrt (double %))
   'cbrt #(Math/cbrt (double %))
   'exp #(Math/exp (double %))
   
   ;; Logarithms
   'ln #(Math/log (double %))
   'log10 #(Math/log10 (double %))
   'log2 #(/ (Math/log (double %)) (Math/log 2.0))
   'logb #(/ (Math/log (double %1)) (Math/log (double %2)))
   
   ;; Trigonometry (radians)
   'sin #(Math/sin (double %))
   'cos #(Math/cos (double %))
   'tan #(Math/tan (double %))
   'asin #(Math/asin (double %))
   'acos #(Math/acos (double %))
   'atan #(Math/atan (double %))
   'atan2 #(Math/atan2 (double %1) (double %2))
   'sinh #(Math/sinh (double %))
   'cosh #(Math/cosh (double %))
   'tanh #(Math/tanh (double %))
   
   ;; Trigonometry (degrees) - convenience
   'sind #(Math/sin (Math/toRadians (double %)))
   'cosd #(Math/cos (Math/toRadians (double %)))
   'tand #(Math/tan (Math/toRadians (double %)))
   
   ;; Rounding and magnitude
   'abs #(Math/abs (double %))
   'floor #(Math/floor (double %))
   'ceil #(Math/ceil (double %))
   'round #(Math/round (double %))
   'sign #(Math/signum (double %))
   'trunc #(long (Math/floor (double %)))
   
   ;; Constants
   'pi Math/PI
   'e Math/E
   'tau (* 2.0 Math/PI)
   'phi (/ (+ 1.0 (Math/sqrt 5.0)) 2.0)  ; golden ratio
   
   ;; Comparisons
   '< <, '> >, '<= <=, '>= >=, '= =, 'not= not=
   
   ;; Vector/sequence operations
   'sum #(reduce + %)
   'product #(reduce * %)
   'vmin #(apply min %)
   'vmax #(apply max %)
   'count count
   
   ;; Statistics
   'mean (fn [xs]
           (/ (reduce + xs) (count xs)))
   
   'median (fn [xs]
             (let [sorted (sort xs)
                   n (count sorted)
                   mid (quot n 2)]
               (if (odd? n)
                 (nth sorted mid)
                 (/ (+ (nth sorted mid)
                       (nth sorted (dec mid)))
                    2))))
   
   'variance (fn [xs]
               (let [m (/ (reduce + xs) (count xs))
                     n (count xs)]
                 (/ (reduce + (map #(* (- % m) (- % m)) xs))
                    n)))
   
   'stdev (fn [xs]
            (Math/sqrt ((fn [xs]
                          (let [m (/ (reduce + xs) (count xs))
                                n (count xs)]
                            (/ (reduce + (map #(* (- % m) (- % m)) xs))
                               n))) xs)))
   
   ;; Linear algebra basics
   'dot (fn [v1 v2]
          (reduce + (map * v1 v2)))
   
   'norm (fn [v]
           (Math/sqrt (reduce + (map #(* % %) v))))
   
   'cross (fn [[a1 a2 a3] [b1 b2 b3]]
            [(- (* a2 b3) (* a3 b2))
             (- (* a3 b1) (* a1 b3))
             (- (* a1 b2) (* a2 b1))])})

;; SCI context for safe evaluation
(def sci-ctx
  (sci/init
    {:bindings math-fns
     :allow '[let if when cond -> ->> as-> fn]
     :deny '[def defn ns require import loop recur]
     :realize-max 10000}))  ; prevent infinite sequences

(defn calculate
  "Evaluate a mathematical expression string.
   Returns map with :result, :type, and :expr keys."
  [expr-string]
  (try
    (let [result (sci/eval-string* sci-ctx expr-string)]
      {:result result
       :type (str (type result))
       :expr expr-string})
    (catch Exception e
      {:error (.getMessage e)
       :expr expr-string
       :type "error"})))
```

### MCP Tool Handler Integration

**Actual Implementation**: `src/nrepl_mcp_server/mcp_server/tools/calculate.clj`

```clojure
(require '[nrepl-mcp-server.calculator :as calc])

;; In tools list
(def tools
  [{:name "nrepl-connect"
    ...}
   {:name "nrepl-eval"
    ...}
   {:name "nrepl-status"
    ...}
   {:name "calculate"
    :description (slurp "resources/calculate-tool-description.txt")
    :inputSchema {:type "object"
                  :properties {:expr {:type "string"
                                      :description "Clojure mathematical expression"}}
                  :required ["expr"]}}])

;; In tool dispatch
(defmethod handle-tool "calculate"
  [{:keys [arguments]}]
  (let [expr (:expr arguments)
        result (calc/calculate expr)]
    ;; Log for analytics
    (log-calculation expr result)
    {:content [{:type "text"
                :text (pr-str result)}]}))
```

### Logging and Analytics

**Actual Implementation**: `src/nrepl_mcp_server/calculator_analytics.clj`

Create a logging namespace to track usage patterns:

```clojure
(ns nrepl-mcp-server.calculator-analytics
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]))

(def log-file "calculator-usage.edn")

(defn log-calculation
  "Append calculation to analytics log"
  [expr result]
  (let [entry {:timestamp (System/currentTimeMillis)
               :expr expr
               :result-type (:type result)
               :success? (not (contains? result :error))
               :error (get result :error nil)
               ;; Extract patterns
               :functions-used (extract-function-names expr)
               :expr-depth (measure-nesting-depth expr)
               :has-let? (clojure.string/includes? expr "let")
               :has-threading? (or (clojure.string/includes? expr "->")
                                   (clojure.string/includes? expr "->>"))}]
    (spit log-file (str (pr-str entry) "\n") :append true)))

(defn extract-function-names
  "Extract top-level function symbols from expression"
  [expr-str]
  (try
    (let [form (edn/read-string expr-str)]
      (set (filter symbol? (flatten (if (seq? form) form [form])))))
    (catch Exception _ #{})))

(defn measure-nesting-depth
  "Count max nesting level in expression"
  [expr-str]
  (let [chars (seq expr-str)]
    (loop [cs chars depth 0 max-depth 0]
      (if (empty? cs)
        max-depth
        (let [c (first cs)]
          (cond
            (= c \() (recur (rest cs) (inc depth) (max max-depth (inc depth)))
            (= c \)) (recur (rest cs) (dec depth) max-depth)
            :else (recur (rest cs) depth max-depth)))))))

(defn generate-usage-report
  "Analyze log file and generate summary statistics"
  []
  (when (.exists (io/file log-file))
    (let [entries (->> (slurp log-file)
                       clojure.string/split-lines
                       (remove empty?)
                       (map edn/read-string))
          total (count entries)
          successes (count (filter :success? entries))
          fn-freq (->> entries
                       (mapcat :functions-used)
                       frequencies
                       (sort-by val >))
          avg-depth (/ (reduce + (map :expr-depth entries)) total)]
      {:total-calculations total
       :success-rate (/ successes total)
       :avg-nesting-depth avg-depth
       :top-functions (take 20 fn-freq)
       :let-usage-rate (/ (count (filter :has-let? entries)) total)
       :threading-usage-rate (/ (count (filter :has-threading? entries)) total)
       :common-errors (->> entries
                           (filter :error)
                           (map :error)
                           frequencies
                           (sort-by val >)
                           (take 10))})))
```

### Configuration

Add to `bb.edn` or equivalent:

```clojure
{:paths ["src" "resources"]
 :deps {org.babashka/sci {:mvn/version "0.8.41"}
        ;; ... other deps
        }}
```

## Testing Strategy

### Unit Tests

**Actual Implementation**: `test/nrepl_mcp_server/calculator_test.clj`

```clojure
(ns nrepl-mcp-server.calculator-test
  (:require [clojure.test :refer :all]
            [nrepl-mcp-server.calculator :as calc]))

(deftest basic-arithmetic
  (is (= 5 (:result (calc/calculate "(+ 2 3)"))))
  (is (= 14 (:result (calc/calculate "(+ 2 (* 3 4))"))))
  (is (= 10/3 (:result (calc/calculate "(/ 10 3)")))))

(deftest math-functions
  (is (= 5.0 (:result (calc/calculate "(sqrt 25)"))))
  (is (< (Math/abs (- 1.0 (:result (calc/calculate "(sin (/ pi 2))")))) 1e-10))
  (is (= Math/PI (:result (calc/calculate "pi")))))

(deftest statistics
  (is (= 3 (:result (calc/calculate "(mean [1 2 3 4 5])"))))
  (is (= 3 (:result (calc/calculate "(median [1 2 3 4 5])")))))

(deftest let-bindings
  (is (= 5.0 (:result (calc/calculate "(let [x 3 y 4] (sqrt (+ (* x x) (* y y))))")))))

(deftest threading
  (is (= 25.0 (:result (calc/calculate "(-> 100 sqrt (* 2) (+ 5))")))))

(deftest error-handling
  (let [result (calc/calculate "(foo 1 2)")]
    (is (contains? result :error))
    (is (= "error" (:type result)))))

(deftest type-preservation
  (is (= "java.lang.Long" (:type (calc/calculate "(+ 1 2)"))))
  (is (= "java.lang.Double" (:type (calc/calculate "(+ 1.0 2)"))))
  (is (= "clojure.lang.Ratio" (:type (calc/calculate "(/ 1 3)")))))
```

### Integration Test with MCP Client

```bash
# Test via stdio MCP client
uv run python stdio_mcp_client.py \
  --server-cmd "bb -cp src src/mcp_nrepl_proxy/core.clj" \
  --tool calculate --args '{"expr": "(sqrt (+ (* 3 3) (* 4 4)))"}'

# Expected output:
# {:result 5.0, :type "java.lang.Double", :expr "(sqrt (+ (* 3 3) (* 4 4)))"}
```

### Real-World Usage Test

Deploy the tool and monitor logs for:
1. Most frequently used functions
2. Average expression complexity
3. Error patterns
4. Expressions that should have used nrepl-eval instead
5. Missing functions that users attempt to call

## Future Enhancements

### Phase 2 (if usage warrants)

1. **Complex numbers**
   ```clojure
   'complex #(->Complex %1 %2)
   'real #(.-real %), 'imag #(.-imag %)
   ```

2. **Matrix operations** (if dot/norm see heavy use)
   ```clojure
   'matrix-mult, 'matrix-inv, 'matrix-det, 'eigenvalues
   ```

3. **Symbolic differentiation** (ambitious)
   ```clojure
   (deriv '(* x x) 'x) ; => (* 2 x)
   ```

4. **Units/dimensional analysis**
   ```clojure
   (-> (meters 100) (to-feet))  ; => 328.084
   ```

### Phase 3 (based on analytics)

- Add functions that are frequently requested but missing
- Remove functions that are never used
- Adjust tool description based on actual usage patterns
- Consider splitting into domain-specific tools (geometry, statistics, finance)

## Success Metrics

1. **Adoption**: Tool is called at least N times per week
2. **Accuracy**: >95% of calculations succeed without error
3. **Appropriate use**: <10% of calls are for operations better suited to nrepl-eval
4. **Function coverage**: Top 10 called functions match pre-loaded set (no "missing function" errors)
5. **Expression complexity**: Average nesting depth is reasonable (2-5 levels)

## Open Questions for Review

1. **Should `let` be allowed?** Currently yes, for intermediate values. Alternative: force single expressions only.

2. **How to handle infinite results?** `(/ 1 0)` - return `:infinity` symbol or error?

3. **Precision controls?** Should there be an optional parameter for decimal places or exact arithmetic?

4. **Namespace prefix?** Should functions be unprefixed (`sqrt`) or prefixed (`math/sqrt`)? Currently unprefixed for simplicity.

5. **Return format?** Current EDN map is verbose but informative. Alternative: just return the result value?

---

## Design Review Observations (2025-01-14)

### Security Considerations - REVISED

**Original Concern**: SCI sandbox security, deny dangerous operations, memory limits

**Actual Status**: Security is **not a concern** for this tool because:
- The `nrepl-eval` tool already allows arbitrary code execution in the Babashka MCP server instance
- The `calculate` tool does not introduce any new attack surface
- SCI sandbox serves to prevent **accidents**, not malicious attacks
- Focus should be on **correctness** and **performance**, not security

**Implication**: Simplified SCI configuration:
```clojure
(def sci-ctx
  (sci/init
    {:bindings math-fns
     :allow '[let if when cond -> ->> as-> fn defn]  ; Can even allow defn
     :realize-max 10000}))  ; Just prevent accidental infinite sequences
```

### Timeout Protection - CRITICAL

**Requirement**: Timeout protection is **non-negotiable** due to synchronous MCP interface.

**Problem**: A hanging calculation freezes the entire MCP session:
```
LLM: "Let me calculate (loop [] (recur))..."
User: *waits forever*
MCP: *completely frozen*
```

**Solution**: Promise-based timeout wrapper (5 second default):
```clojure
(defn calculate
  [expr-string]
  (let [timeout-ms 5000
        result-promise (promise)]
    (future
      (try
        (deliver result-promise
                 {:result (sci/eval-string* sci-ctx expr-string)
                  :type (str (type ...))
                  :expr expr-string})
        (catch Exception e
          (deliver result-promise {:error ... :type "error"}))))
    (deref result-promise timeout-ms
           {:error "Calculation timeout (>5s)"
            :type "timeout"
            :expr expr-string})))
```

### Tool Selection Psychology - KEY INSIGHT

**Hypothesis**: A dedicated `calculate` tool will encourage more reliable LLM usage compared to overloading `nrepl-eval`.

**Why Dedicated Tools Reduce Cognitive Friction**:

**Scenario A: Only `nrepl-eval` available**
```
User: "What's the monthly payment calculation?"
LLM thinking:
1. Need to calculate something
2. See "nrepl-eval" - general code evaluation
3. "Can this do math? Let me check description..."
4. "Will math functions be available? Need to require anything?"
5. Constructs expression with uncertainty
```

**Scenario B: `calculate` tool available**
```
User: "What's the monthly payment calculation?"
LLM thinking:
1. Need to calculate something
2. See "calculate" - INSTANT MATCH! ✨
3. High confidence: This is THE tool for this
4. Constructs expression immediately
```

**Cognitive Benefits**:
- ✅ **Tool name signals intent** - "calculate" vs "eval"
- ✅ **Description is focused** - Math examples, not general code
- ✅ **Pre-loaded guarantee** - No "will sqrt be available?" doubt
- ✅ **Lower stakes** - Feels safe/sandboxed vs general execution

**Real-World Analogy**:
- **Swiss Army Knife** (nrepl-eval) - "Can probably cut with this... which blade?"
- **Chef's Knife** (calculate) - "This is obviously for cutting!"

Both work, but specialized tools reduce decision paralysis.

### Architectural Simplifications

**Original Concern**: SCI vs nREPL duplication, consider wrapper approach

**Revised Assessment**: Standalone SCI implementation is **correct** because:
- ✅ **Faster** - No network round-trip to nREPL server
- ✅ **Simpler** - No connection management required
- ✅ **Standalone** - Works even if nREPL connection fails
- ✅ **Isolated** - Math evaluation doesn't pollute nREPL session state
- ✅ **Already precedented** - `local-eval` tool already uses SCI directly

**Synergy Opportunity**: Share SCI context configuration with `local-eval`:
```clojure
(ns nrepl-mcp-server.sci-shared
  "Shared SCI contexts for different use cases")

(def debug-ctx ...)     ; For local-eval (unrestricted)
(def math-ctx ...)      ; For calculate (math-focused)
```

**Note**: Currently each tool has its own SCI context for isolation.

### Testing Strategy - DUAL APPROACH

**Traditional RPC Testing**: Unit and integration tests via `stdio_mcp_client.py`
```bash
uv run python stdio_mcp_client.py \
  --server-cmd "bb -cp src src/mcp_nrepl_proxy/core.clj" \
  --tool calculate --args '{"expr": "(sqrt 144)"}'
# Expected: {:result 12.0, :type "java.lang.Double"}
```

**AI-Driven Test Scenarios**: Natural language test cases in markdown
```markdown
# Test Scenario: Pythagorean Theorem

**User Request**: "Calculate the hypotenuse of a right triangle with sides 3 and 4"

**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(sqrt (+ (* 3 3) (* 4 4)))`
3. Verify result: 5.0

**Pass Criteria**: Result is 5.0 ± 0.001
```

This dual approach tests both **technical correctness** and **LLM usability**.

### Open Questions - ANSWERED

**Q1: Should `let` be allowed?**
**A**: Yes, with caveats:
- ✅ Allow for intermediate values (readability)
- ✅ Keep unlimited (security is moot)
- 📝 Document in examples

**Q2: How to handle infinite results?**
**A**: Return the value with metadata:
```clojure
{:result ##Inf
 :type "java.lang.Double"
 :expr "(/ 1.0 0.0)"
 :warning "Result is infinite"}
```

**Q3: Precision controls?**
**A**: Not in Phase 1. Use analytics to determine need.

**Q4: Namespace prefix?**
**A**: Unprefixed is correct. Convenience is the goal.

**Q5: Return format?**
**A**: Current EDN map is good. Metadata aids debugging and analytics.

### Implementation Priority - REVISED

**Phase 1: MVP** (Implement First)
1. ✅ Core calculator namespace with math functions
2. ✅ SCI context (simplified - no security paranoia)
3. ✅ **Timeout wrapper** (CRITICAL for sync interface)
4. ✅ MCP tool integration
5. ✅ Basic logging (timestamp, expr, result, duration)
6. ✅ Unit tests for functions
7. ✅ Integration test via stdio client
8. ✅ AI-driven test scenarios document

**Phase 2: Validation** (2-4 weeks after deployment)
1. 📊 Analyze calculator-usage.edn logs
2. 📊 Compare calculate vs nrepl-eval usage for math
3. 📊 Identify missing functions (error patterns)
4. 📊 Measure tool selection accuracy

**Phase 3: Refinement** (Data-driven)
1. Add/remove functions based on usage
2. Optimize tool description if selection is poor
3. Consider precision controls if needed
4. Evaluate continuation vs deprecation

### Success Metrics - REFINED

1. **Adoption**: Tool called >10 times/week
2. **Correctness**: >95% success rate (no errors/timeouts)
3. **Appropriate Selection**: LLMs choose calculate for math >80% of time
4. **Function Coverage**: Top 10 functions match pre-loaded set
5. **Performance**: <100ms average evaluation time, <1% timeout rate

---

---

## Phase 2.5: Base64 Encoding & Error Logging (v2.5.0 - 2025-11-15)

### Enhancement: Optional Base64 Encoding

**Motivation**: AI agents sometimes struggle with JSON escaping when constructing complex Clojure expressions containing quotes, backslashes, or nested strings.

**Implementation**:
- Added `input-base64` flag to accept base64-encoded expressions
- Added `output-base64` flag to return base64-encoded results (for strings)
- Base64 decode errors are logged to analytics with type "decode-error"
- Consistent with `nrepl-eval` tool's base64 pattern

**Example Usage**:
```json
{
  "tool": "calculate",
  "arguments": {
    "expr": "KGxldCBbeCAzIHkgNF0gKHNxcnQgKCsgKCogeCB4KSAoKiB5IHkpKSkp",
    "input-base64": true
  }
}
```

Decodes to: `(let [x 3 y 4] (sqrt (+ (* x x) (* y y))))`

**Benefits**:
- ✅ Eliminates JSON escaping issues entirely
- ✅ Supports arbitrarily complex expressions
- ✅ Optional - only used when needed
- ✅ Errors are tracked in analytics

### Enhancement: JSON Parse Error Logging

**Motivation**: Track when AI agents encounter JSON escaping issues to validate the need for base64 encoding and improve tool guidance.

**Implementation**:
- Enhanced `src/nrepl_mcp_server/mcp_server/server.clj` error logging
- Logs all JSON parse failures to stderr with:
  - Timestamp for analytics
  - Exception type and message
  - Raw JSON (truncated if >500 chars)
  - Automatic issue detection (unescaped quotes, backslashes)
  - Context-aware hints for calculate tool errors

**Error Log Format**:
```
========================================
[JSON Parse Error] 2025-11-15T19:56:11Z
Exception: JsonEOFException - Unexpected end-of-input...
Raw JSON: {"jsonrpc":"2.0","method":"tools/call","params":{"name":"calculate",...
⚠️  Possible Issue: Unescaped quotes detected
💡 Hint: Consider using base64 encoding (input-base64=true) for complex expressions
========================================
```

**Analytics Value**:
- Track frequency of JSON parse failures
- Identify problematic expression patterns
- Measure base64 encoding adoption by AI agents
- Validate whether escaping issues are common or rare
- Generate real-world examples for documentation

**Documentation**: See `docs/json-parse-error-logging.md` for complete details.

### Production Status

**Current Version**: v2.5.0
**Status**: ✅ **PRODUCTION READY**

**Deployment Evidence**:
- 79 passing test assertions (52 core + 27 tool handler)
- 93% pass rate on 30 AI agent test scenarios
- Live testing verified on production MCP server
- Analytics logging active and tracking all calculations
- Base64 encoding tested and working
- JSON error logging implemented and verified

**Files**:
- Core: `src/nrepl_mcp_server/calculator.clj`
- Analytics: `src/nrepl_mcp_server/calculator_analytics.clj`
- Tool Handler: `src/nrepl_mcp_server/mcp_server/tools/calculate.clj`
- Server Integration: `src/nrepl_mcp_server/mcp_server/server.clj`
- Tests: `test/nrepl_mcp_server/calculator_test.clj`
- Tool Tests: `test/nrepl_mcp_server/calculate_tool_test.clj`

**Analytics Log**: `calculator-usage.edn` (tracks all calculations with metadata)

---

## Getting Started (for Claude Code)

**Note**: This tool is already implemented and deployed. This section is for historical reference.

1. ✅ Core calculator implementation (`src/nrepl_mcp_server/calculator.clj`)
2. ✅ SCI dependency in `bb.edn`
3. ✅ Tool integration in dispatch system
4. ✅ Analytics logging infrastructure
5. ✅ Comprehensive unit tests
6. ✅ Base64 encoding support
7. ✅ JSON error logging
8. ✅ Production deployment

The key insight: **The tool description IS the interface**. Make it clear, comprehensive, and example-rich so the AI (your cousin) naturally reaches for it when doing math.
