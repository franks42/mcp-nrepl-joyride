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

---

## Phase 3B: Type-Safe Token Conversion System (v3.1.0 - ✅ IMPLEMENTED)

### Overview

**Goal**: Eliminate catastrophic unit confusion errors by implementing tuple-based token amounts with division notation for exchange rates, plus flexible format preservation for maximum usability.

**Motivation**: Real-world feedback from Claude Desktop revealed the need for type-safe token conversions:
- Token amounts should never be bare numbers that can be confused
- Exchange rates need clear mathematical representation
- Conversions should be bidirectional and explicit
- System should prevent same-unit rate nonsense
- **NEW**: Users need control over output format (keywords vs strings, case)

**Status**: ✅ Core implementation complete (keyword support + format preservation)

**Related Documents**:
- `docs/calculator-implementation-plan.md` - Implementation tasks for Phase 3B

### Core Concepts

#### 1. Tuple-Based Token Amounts with Format Flexibility

**Principle**: Token amounts are ALWAYS represented as `[amount unit]` tuples, never as bare numbers. Units can be keywords or strings, with automatic normalization for internal comparison.

```clojure
;; ❌ BAD: Bare numbers - units unknown, catastrophic errors possible
17500000        ; Is this hash? nhash? btc? sats? WHO KNOWS?!

;; ✅ GOOD: Self-documenting tuples (multiple formats supported)
[17500000 :nhash]     ; Keyword (preferred - no JSON escaping!)
[17500000 "nhash"]    ; Lowercase string
[17500000 "NHASH"]    ; Uppercase string (all equivalent!)

[1000 :hash]          ; Keywords are idiomatic Clojure
[1.5 "btc"]           ; Strings for compatibility
[0.032 "USD"]         ; Uppercase for presentation
```

**Format Equivalence**: All formats represent the same unit (conversion = 1:1)
- `:usd`, `"usd"`, `"USD"`, `:USD` → all treated as the same unit
- Internal comparison uses normalized lowercase keywords
- Output format controlled by caller's input

**Benefits**:
- **Type safety** - Units cannot be confused
- **Self-documenting** - Code is readable without context
- **Prevents errors** - Can't accidentally mix units
- **Enables validation** - System can check unit compatibility
- **Format flexibility** - Keywords (no escaping) OR strings (compatibility)
- **Caller control** - Choose your output format (keyword vs string, case)

**Real-World Precedents**:
- **Stripe API** - Always pairs amounts with currency codes
- **F# Units of Measure** - Type-checked dimensional analysis
- **SQL Money types** - Amount + currency as atomic type
- **ISO 4217** - Uppercase currency codes for presentation

#### 2. Division Notation for Exchange Rates

**Principle**: Exchange rates are represented as explicit fractions using division notation. Units in rates can be keywords or strings.

**Format**: `[/ [numerator-amount unit] [denominator-amount unit]]`

```clojure
;; Exchange rate: "0.032 USD per 1 hash" (keyword format - preferred)
[/ [0.032 :usd] [1 :hash]]

;; Same rate with strings (also valid)
[/ [0.032 "usd"] [1 "hash"]]

;; Uppercase strings for presentation
[/ [0.032 "USD"] [1 "HASH"]]

;; Equivalent inverse: "31.25 hash per 1 USD"
[/ [31.25 :hash] [1 :usd]]

;; Unit conversion: "1,000,000,000 nhash per 1 hash"
[/ [1000000000 :nhash] [1 :hash]]

;; Mixed formats work too!
[/ [0.032 "USD"] [1 :hash]]  ; String + keyword → both valid
```

**Why Division Notation is Brilliant**:
- **Self-documenting** - Literally reads as "X per Y"
- **Mathematically explicit** - Shows the fraction structure
- **Symmetric** - Can express either direction
- **Type-safe** - Units flow through math naturally
- **Clear semantics** - No ambiguity about which direction
- **Format agnostic** - Works with keywords, strings, any case

#### 3. Auto-Promotion in Arithmetic

**Principle**: Arithmetic operations automatically promote plain numbers to tuples when mixed with tuple-based amounts. **Format is preserved from the tuple operand.**

**Auto-Promotion Hierarchy**:

```clojure
;; Level 1: Plain numbers (backward compatible)
(+ 1000 2000)
; → 3000

;; Level 2: Tuple + number (auto-promote number to same unit, preserve format)
(+ [1000 :hash] 2000)
; → [3000 :hash]  ; Keyword preserved

(+ [1000 "hash"] 2000)
; → [3000 "hash"]  ; Lowercase string preserved

(+ [1000 "HASH"] 2000)
; → [3000 "HASH"]  ; Uppercase string preserved

;; Level 3: Tuple + compatible tuple (auto-convert if different but compatible units)
(+ [1000 :hash] [5000000000 :nhash])
; → [1005 :hash]  ; 5B nhash = 5 hash, first operand format preserved

;; Level 4: Tuple + incompatible (error - cannot convert without rate)
(+ [1000 :hash] [500 :btc])
; → Error: Cannot add incompatible units :hash and :btc without conversion rate
```

**Design Philosophy**:
- Backward compatible - plain numbers still work
- Ergonomic - don't force tuples everywhere
- Safe - auto-promotion only when unambiguous
- Explicit - incompatible operations require explicit conversion
- **Format preserving** - output matches input format (keyword/string/case)

#### 4. Token Conversion Function with Format Preservation

**Primary Function**: `token-convert`

**Three Signatures**:

```clojure
;; 1. Registry-based conversion (uses predefined rates)
(token-convert [amount from-unit] to-unit)
(token-convert [1000 :hash] :usd)
; → [32.0 :usd]  ; Output format matches caller's to-unit (keyword)

(token-convert [1000 :hash] "usd")
; → [32.0 "usd"]  ; Output format matches caller's to-unit (lowercase string)

(token-convert [1000 :hash] "USD")
; → [32.0 "USD"]  ; Output format matches caller's to-unit (uppercase string)

;; 2. Inferred target (rate determines destination, preserves rate's format)
(token-convert [amount from-unit] rate)
(token-convert [10 :usd] [/ [31.25 :hash] [1 :usd]])
; → [312.5 :hash]  ; Format matches rate's target unit (keyword)

(token-convert [10 :usd] [/ [31.25 "hash"] [1 :usd]])
; → [312.5 "hash"]  ; Format matches rate's target unit (lowercase string)

(token-convert [10 :usd] [/ [31.25 "HASH"] [1 :usd]])
; → [312.5 "HASH"]  ; Format matches rate's target unit (uppercase string)

;; 3. Explicit validation (target must match rate, preserves caller's format)
(token-convert [amount from-unit] to-unit rate)
(token-convert [10 :usd] :hash [/ [31.25 :hash] [1 :usd]])
; → [312.5 :hash]  ; Format matches caller's to-unit (keyword)

(token-convert [10 :usd] "HASH" [/ [31.25 :hash] [1 :usd]])
; → [312.5 "HASH"]  ; Format matches caller's to-unit (uppercase string)
```

**Format Preservation Rules**:
1. **3-arity form**: Output format matches caller's `to-unit` parameter exactly
2. **2-arity form**: Output format matches the target unit from the rate
3. **All cases**: Units are normalized internally (`:usd` = `"usd"` = `"USD"`) for comparison
4. **Caller control**: Choose your preferred output format in the result

**Conversion Logic**:

```clojure
;; Converting FROM denominator TO numerator: multiply by (num/denom)
[amount :hash] × [/ [0.032 :usd] [1 :hash]]
; → [amount × 0.032 :usd]  ; Format preserved from rate

;; Converting FROM numerator TO denominator: divide by (num/denom)
[amount :usd] ÷ [/ [0.032 :usd] [1 :hash]]
; → [amount ÷ 0.032 :hash]  ; Format preserved from rate

;; Format control examples:
[1000 :hash] × [/ [0.032 "USD"] [1 :hash]]  ; 2-arity
; → [32.0 "USD"]  ; Preserves uppercase string from rate

[1000 :hash] :usd [/ [0.032 "USD"] [1 :hash]]  ; 3-arity
; → [32.0 :usd]  ; Preserves keyword from caller's to-unit
```

### Rate Validation

**Valid Rate Requirements**:
1. Must use `/` operator
2. Amounts must be non-zero
3. Amounts must be positive
4. **Units must be different** (same-unit rates are REJECTED)

**Validation Function**:

```clojure
(defn valid-rate? [[op [num-amt num-unit] [denom-amt denom-unit]]]
  (cond
    (not= op /)
    {:valid false :error "Must use / operator for rates"}

    (or (zero? num-amt) (zero? denom-amt))
    {:valid false :error "Rate amounts cannot be zero"}

    (or (neg? num-amt) (neg? denom-amt))
    {:valid false :error "Rate amounts must be positive"}

    (= num-unit denom-unit)
    {:valid false :error "Same-unit rates are invalid - use plain numbers for multipliers"}

    :else
    {:valid true}))
```

**Why Reject Same-Unit Rates**:
- `[/ [2 "hash"] [1 "hash"]]` is semantically nonsensical
- "2 hash per 1 hash" is just a dimensionless multiplier (2×)
- If you need a multiplier, use plain numbers: `(* amount 2)`
- Having units on both sides that cancel out defeats type safety

**Token Migration Exception**:
```clojure
[/ [100 "new-token"] [1 "old-token"]]
; ✅ Valid - DIFFERENT units (migration/swap rate)

[/ [2 "hash"] [1 "hash"]]
; ❌ Invalid - SAME unit (use plain multiplier)
```

### Rate Utilities

**Rate Inversion**:

```clojure
(invert-rate [/ [0.032 "usd"] [1 "hash"]])
; → [/ [31.25 "hash"] [1 "usd"]]
```

**Rate Composition** (multi-hop conversions):

```clojure
(compose-rates
  [/ [0.032 "usd"] [1 "hash"]]
  [/ [0.000001 "btc"] [1 "usd"]])
; → [/ [0.000000032 "btc"] [1 "hash"]]
; Enables hash → usd → btc chains
```

**Rate Normalization** (canonical form):

```clojure
(normalize-rate [/ [3.2 "usd"] [100 "hash"]])
; → [/ [0.032 "usd"] [1 "hash"]]
; Prefer denominator = 1 for clarity
```

### Token Registry

**Purpose**: Centralized storage of token metadata and common exchange rates.

**Registry Structure**:

```clojure
(def token-registry
  {:tokens {"hash"    {:name "Provenance Hash" :decimals 9}
            "nhash"   {:name "Nano Hash" :decimals 0}
            "btc"     {:name "Bitcoin" :decimals 8}
            "eth"     {:name "Ethereum" :decimals 18}
            "usd"     {:name "US Dollar" :decimals 2}
            "usdc"    {:name "USD Coin" :decimals 6}
            "sol"     {:name "Solana" :decimals 9}
            "ylds"    {:name "Yields" :decimals 9}}

   :rates {"hash->nhash" [/ [1000000000 "nhash"] [1 "hash"]]
           "hash->usd"   [/ [0.032 "usd"] [1 "hash"]]
           "btc->usd"    [/ [95000 "usd"] [1 "btc"]]
           "eth->usd"    [/ [2500 "usd"] [1 "eth"]]}

   :compatibility {"hash" #{:hash :nhash}
                   "btc"  #{:btc :sats}
                   "eth"  #{:eth :wei :gwei}}})
```

**Registry Functions**:

```clojure
(get-rate from-unit to-unit)
; → Looks up or composes rate from registry

(register-token! unit metadata)
; → Adds new token to registry

(register-rate! from to rate)
; → Adds new exchange rate

(compatible-units? unit1 unit2)
; → Checks if units can be auto-converted
```

### Error Handling

**Type-Safe Error Messages**:

```clojure
;; Incompatible unit addition
(+ [100 "hash"] [50 "btc"])
; → Error: "Cannot add incompatible units 'hash' and 'btc' without conversion rate.
;           Use (token-convert [50 'btc'] 'hash' rate) first."

;; Invalid rate
(token-convert [100 "hash"] "usd" [/ [2 "hash"] [1 "hash"]])
; → Error: "Same-unit rates are invalid - use plain numbers for multipliers"

;; Unknown unit
(token-convert [100 "foo"] "bar")
; → Error: "Unknown units: 'foo', 'bar'.
;           Available: hash, nhash, btc, eth, usd, usdc, sol, ylds"

;; Missing rate
(token-convert [100 "hash"] "sol")
; → Error: "No conversion rate available for hash → sol.
;           Provide explicit rate or register in token-registry."
```

### Benefits

**Type Safety**:
- ✅ Units cannot be confused or mixed accidentally
- ✅ Compiler-enforced dimensional analysis
- ✅ Runtime validation of unit compatibility
- ✅ Format equivalence (`:usd` = `"usd"` = `"USD"`) prevents case/type errors

**Clarity**:
- ✅ Code is self-documenting with explicit units
- ✅ Exchange rates read naturally as "X per Y"
- ✅ No ambiguity about conversion direction
- ✅ Output format matches caller's input for predictability

**Correctness**:
- ✅ Prevents catastrophic unit confusion errors
- ✅ Invalid operations rejected at runtime
- ✅ Same-unit rate nonsense prevented
- ✅ Internal normalization ensures consistent comparisons

**Ergonomics**:
- ✅ Auto-promotion reduces boilerplate
- ✅ Registry provides common conversions
- ✅ Multiple signature convenience
- ✅ **Format flexibility** - use keywords (no escaping) OR strings (compatibility)
- ✅ **Caller control** - choose your preferred output format
- ✅ **JSON-friendly** - keywords avoid escaping issues in AI agent tool calls

### Implementation Plan

See `docs/calculator-implementation-plan.md` Phase 3B for detailed implementation tasks.

**Status**: ✅ **COMPLETED** (2025-11-15)

**Actual Effort**: ~6 hours (keyword support + format preservation)

**Files Modified**:
- ✅ `src/nrepl_mcp_server/calculator.clj` - Core implementation with `normalize-unit` and format preservation
- ✅ `test/nrepl_mcp_server/token_conversion_test.clj` - Comprehensive tests (20 tests, 90 assertions)
- ✅ `src/nrepl_mcp_server/mcp_server/tools/calculate.clj` - Updated tool description for keywords

**Test Results**:
- 20 tests passing
- 90 assertions passing
- 0 failures, 0 errors
- Format preservation validated across keyword/string/uppercase/lowercase variations

**Key Implementation Choices**:
1. **Dual keyword/string support** - Keywords preferred, strings for compatibility
2. **Internal normalization** - All units normalized to lowercase keywords for comparison
3. **Format preservation** - Output format matches caller's input exactly
4. **Case insensitivity** - `:USD` = `:usd` = `"USD"` = `"usd"` (all equivalent)

---

## Phase 3C: Portfolio Aggregation (v3.2.0)

### Overview

**Goal**: Aggregate multiple token holdings into a single target currency for portfolio valuation.

**Status**: ✅ **PHASE 3C.1 COMPLETED** (2025-11-15)

### Features

**Auto-Matching Rates**:
- Finds appropriate rate for each holding by comparing normalized units
- Supports both direct and inverted rates automatically
- Handles non-normalized rates (auto-normalizes to denominator = 1)

**Bidirectional Coverage**:
- Normalizes all incoming rates
- Generates inverted rates from normalized rates
- Combines both for auto-matching (doubles coverage)

**Compatible-Units Registry**:
- Handles same-token denominations (hash/nhash, btc/sats)
- Fallback for conversions not in provided rates
- Extensible registry design

**Format Preservation**:
- Output format matches caller's target unit format
- Keyword, string, and case variations all supported

### Core Function: portfolio-value

```clojure
(portfolio-value holdings to-unit rates)
;; holdings - Vector of token amounts: [[1000 :hash] [5E7 :nhash] [10 :usd]]
;; to-unit - Target unit for aggregation: :usd, "USD", etc.
;; rates - Vector of exchange rates (any format, any normalization)
```

**Algorithm**:
1. Validate all holdings are token amounts
2. Normalize all incoming rates to denominator = 1
3. Generate inverted rates for bidirectional matching
4. For each holding:
   - Skip if already in target currency
   - Find matching rate by unit comparison
   - Use `token-convert` for actual conversion (same algorithm!)
   - Fallback to compatible-units registry if needed
   - Throw error if no rate found
5. Sum all converted amounts, preserve target format

**Compatible-Units Registry**:
```clojure
(def compatible-units
  {#{:hash :nhash} [:/ [1 :hash] [1000000000 :nhash]]
   #{:btc :sats}   [:/ [1 :btc] [100000000 :sats]]})
```

### Examples

**Simple USD Portfolio Valuation**:
```clojure
(portfolio-value
  [[1000 :hash] [5E7 :nhash] [10 :usd]]
  :usd
  [[:/ [0.032 :usd] [1 :hash]]])
=> [42.6 :usd]  ; 1000*0.032 + 5E7*0.032/1E9 + 10
```

**Aggregate Hash Denominations**:
```clojure
(portfolio-value
  [[1000 :hash] [5E7 :nhash]]
  :hash
  [])
=> [1050.0 :hash]  ; Uses compatible-units registry
```

**Non-Normalized Rates**:
```clojure
(portfolio-value
  [[100 :hash]]
  :usd
  [[:/ [0.064 :usd] [2 :hash]]])
=> [3.2 :usd]  ; Normalizes to [:/ [0.032 :usd] [1 :hash]]
```

**Format Preservation**:
```clojure
(portfolio-value
  [[1000 :hash]]
  "USD"
  [[:/ [0.032 :usd] [1 :hash]]])
=> [32.0 "USD"]  ; Uppercase string preserved
```

**Bidirectional Rate Matching**:
```clojure
(portfolio-value
  [[10 :usd]]
  :hash
  [[:/ [0.032 :usd] [1 :hash]]])
=> [312.5 :hash]  ; Uses inverted rate automatically
```

### Design Rationale

**Why Option B (Normalize + Registry)?**
1. **Simplicity** - 90% of use cases don't need graph search
2. **Performance** - O(n) instead of O(n²) graph search
3. **Predictability** - Clear error when rate is missing
4. **Extensibility** - Registry can grow with user needs

**Why Use token-convert?**
- Ensures exact same algorithm and validation
- Leverages all existing format preservation logic
- Maintains consistency across all conversion operations
- Reduces code duplication and test surface

**Why Normalize + Invert?**
- Doubles rate coverage without user duplication
- User provides `[:/ [0.032 :usd] [1 :hash]]` once
- System automatically handles both USD→hash and hash→USD
- Eliminates need to provide bidirectional rates manually

### Design Debate: Vector vs Map for Rate Representation

During implementation, we considered two approaches for representing exchange rates:

#### Option A: Vector with Division Operator (CHOSEN ✅)

```clojure
[:/ [0.032 :usd] [1 :hash]]
```

**Pros**:
- ✅ **Mathematically correct** - Rates ARE fractions/ratios
- ✅ **Visual metaphor** - Looks like a fraction, reads like math
- ✅ **Intuitive operations** - Invert = flip fraction, compose = multiply fractions
- ✅ **Compact syntax** - Minimal ceremony for interactive use
- ✅ **No quoting issues** - `:/ ` keyword requires no quoting
- ✅ **Position conveys meaning** - Numerator/denominator is obvious
- ✅ **Already implemented** - All utilities work (normalize, invert, compose)

**Cons**:
- ❌ Position-dependent - Must remember numerator comes first
- ❌ Less extensible - Hard to add metadata without wrapping
- ❌ New users might not recognize `:/ ` as division initially

**Key Insight - Mathematical Elegance**:
```clojure
;; Composition is obviously fraction multiplication!
(compose-rates [:/ [0.032 :usd] [1 :hash]]
               [:/ [0.00001 :btc] [1 :usd]])
=> [:/ [0.00000032 :btc] [1 :hash]]

;; Inversion is obviously flipping the fraction!
(invert-rate [:/ [0.032 :usd] [1 :hash]])
=> [:/ [31.25 :hash] [1 :usd]]
```

#### Option B: Map with Named Fields

```clojure
{:rate [0.032 :usd] :per :hash}
;; or more verbose
{:type :rate
 :num {:amt 0.032 :unit :usd}
 :denom {:amt 1 :unit :hash}}
```

**Pros**:
- ✅ Self-documenting - Field names explain everything
- ✅ No quoting issues - Just data
- ✅ Extensible - Easy to add `:source`, `:timestamp`, `:confidence`
- ✅ Order-independent - Keys can be in any order
- ✅ Validation-friendly - Can use spec/schema

**Cons**:
- ❌ **Obscures mathematical meaning** - How does `{:rate X :per Y}` compose?
- ❌ Verbose - 2-3x more syntax
- ❌ Lost visual metaphor - Doesn't "look like" a rate
- ❌ Operations feel arbitrary - Less intuitive why invert/compose work

#### Decision: Vector Approach Wins

**Rationale**:
1. **Domain correctness trumps API convenience** - Rates ARE fractions mathematically
2. **Operations become intuitive** - When structure matches semantics, code is self-documenting
3. **Quoting problem solved** - `:/ ` keyword is clean and requires no quoting
4. **Already working** - Don't fix what isn't broken

**Future Extensibility - Hybrid Approach**:

When metadata is needed (Phase 3D), wrap the vector in a map:

```clojure
{:rate [:/ [0.032 :usd] [1 :hash]]  ; Keep vector structure!
 :source :coingecko
 :timestamp 1737000000
 :confidence :high
 :bid-ask-spread 0.0001}
```

Best of both worlds - mathematical correctness + metadata when needed.

### Convenience Constructor (Phase 3D.1)

To improve ergonomics while keeping mathematical correctness, add a `rate` helper:

```clojure
(defn rate
  "Convenient rate constructor.

   Examples:
     (rate 0.032 :usd :per :hash)      => [:/ [0.032 :usd] [1 :hash]]
     (rate 31.25 :hash :per :usd)      => [:/ [31.25 :hash] [1 :usd]]
     (rate 0.064 :usd :per [2 :hash])  => [:/ [0.064 :usd] [2 :hash]]"
  ([num-amt num-unit per-kw per-unit]
   {:pre [(= per-kw :per)
          (or (keyword? per-unit) (string? per-unit))]}
   [:/ [num-amt num-unit] [1 per-unit]])
  ([num-amt num-unit per-kw [denom-amt denom-unit]]
   {:pre [(= per-kw :per)]}
   [:/ [num-amt num-unit] [denom-amt denom-unit]]))

;; Usage examples
(rate 0.032 :usd :per :hash)
=> [:/ [0.032 :usd] [1 :hash]]

(rate 0.064 :usd :per [2 :hash])  ; Non-normalized
=> [:/ [0.064 :usd] [2 :hash]]

;; Use in token-convert
(token-convert [1000 :hash] :usd (rate 0.032 :usd :per :hash))
=> [32.0 :usd]
```

**Benefits**:
- Friendly front-door for new users
- Reads naturally: "rate 0.032 USD per hash"
- Validates `:per` keyword position
- Returns canonical vector structure
- Works seamlessly with all existing functions

**Status**: 📋 **PLANNED** for Phase 3D.1

### Files Modified

- ✅ `src/nrepl_mcp_server/calculator.clj`:
  - Added `portfolio-value` function
  - Added `compatible-units` registry
  - Added `find-compatible-rate` helper
  - Exported `portfolio-value` in `token-conversion-fns`
- ✅ `src/nrepl_mcp_server/mcp_server/tools/calculate.clj`:
  - Updated tool description with portfolio-value examples
- ✅ Code formatting and linting:
  - cljfmt: ✅ Clean
  - clj-kondo: ✅ 0 warnings, 0 errors

### Future Enhancements

**Phase 3C.2: Graph Search for Multi-Hop Conversions**

**Goal**: Enable multi-hop conversions through intermediary currencies (e.g., nhash → hash → usd)

**Use Case**:
```clojure
;; User only provides: nhash→hash and hash→usd rates
;; System automatically finds path: nhash → hash → usd
(portfolio-value
  [[5E7 :nhash]]
  :usd
  [[:/ [1 :hash] [1E9 :nhash]]      ; nhash → hash
   [:/ [0.032 :usd] [1 :hash]]])    ; hash → usd
=> [1.6 :usd]  ; Automatic multi-hop conversion
```

**Why Deferred**:
- Phase 3C.1 (normalize + registry) handles 90% of real-world use cases
- Graph search adds significant complexity
- Most users provide direct rates or use compatible-units registry
- Can add later if analytics show strong demand

**Decision Criteria for Implementation**:
- >10% of portfolio-value calls fail due to missing multi-hop path
- User feedback specifically requests this feature
- Clear use case patterns emerge that can't be handled by registry

**Other Future Enhancements**:
- Reader macros: `#hash 1000` → `[1000 "hash"]`
- Smart error recovery: suggest similar units
- Historical rate time-series
- Automatic rate updates from oracles/APIs

**Status**: 📋 **DOCUMENTED** - Awaiting usage analytics

---

## Phase 3E: Token Formatting Utilities (v3.4.0)

### Overview

**Goal**: Format token tuples and calculation results for human-readable presentation with flexible output options.

**Status**: 📋 **PLANNED** - Awaiting implementation

**Motivation**: Real-world feedback from Claude Desktop revealed that calculation results need better formatting:

> "The Input/Output I was referring to: When I converted nhash to hash:
> `(token-convert [17500000000004030 :nhash] :hash [:/ [1 :hash] [1000000000 :nhash]])`
> The Output was: `[1.750000000000403E7, "hash"]` ; Scientific notation
>
> What I was suggesting: A formatting function that takes this token tuple output and formats it nicely:
> `(format-token [1.750000000000403E7 "hash"])` ; → "17,500,000 HASH""

**Key Use Cases**:
1. **Reports/UIs**: Display calculation results to end users
2. **Logging**: Readable audit trails and transaction logs
3. **Debugging**: Understand intermediate calculation steps
4. **Charting/Tables**: Structured components for visualization

### Core Function: format-token

**Function Signature**:
```clojure
(format-token token-tuple)
(format-token token-tuple options)
```

**Design Philosophy**:
- **Human-readable by default** - No scientific notation, appropriate decimals
- **Flexible output** - String OR component map for different use cases
- **Smart defaults** - Auto-decimals based on amount size and unit type
- **Configurable** - Options map controls all formatting aspects

#### String Output (Default)

**Basic Usage**:
```clojure
;; Scientific notation → thousands separators
(format-token [1.750000000000403E7 "hash"])
=> "17,500,000 HASH"

;; Auto-decimals for currency
(format-token [32.156789 :usd])
=> "$32.16 USD"

;; Tiny amounts - show more precision
(format-token [0.00000123 :btc])
=> "0.00000123 BTC"

;; Large whole numbers - no unnecessary decimals
(format-token [1000000 :hash])
=> "1,000,000 HASH"
```

**Controlled Formatting**:
```clojure
;; Override decimal places
(format-token [1234567.89 :usd] {:decimals 0})
=> "$1,234,568 USD"

(format-token [1234567.89 :usd] {:decimals 4})
=> "$1,234,567.8900 USD"

;; Disable currency symbol
(format-token [1000000 :usd] {:symbol false})
=> "1,000,000 USD"

;; Lowercase unit symbol
(format-token [1000000 :hash] {:uppercase false})
=> "1,000,000 hash"

;; Custom separators (European format)
(format-token [1234.56 :eur] {:thousands-sep "." :decimal-sep ","})
=> "€1.234,56 EUR"
```

#### Component Map Output (for Charts/Tables)

**Use Case**: When you need to render components in different UI elements:

```clojure
;; Get structured components
(format-token [123456789.12 :usd] {:components true})
=> {:amt "123,456,789.12"
    :token-symbol "USD"
    :token-char "$"
    :formatted "$123,456,789.12 USD"
    :raw-amount 123456789.12}

;; Use in table/chart rendering
(let [{:keys [amt token-symbol token-char]}
      (format-token [42.6 :usd] {:components true})]
  {:label token-symbol
   :value amt
   :prefix token-char})
=> {:label "USD" :value "42.60" :prefix "$"}
```

**Benefits**:
- **Flexibility** - Components can be rearranged for UI needs
- **Consistency** - All components use same formatting rules
- **Reusability** - Components work in charts, tables, reports
- **Customization** - Each component can be styled independently

### Options Map

```clojure
{:decimals nil         ; nil = auto (smart), or 0-8 for explicit
 :symbol true          ; Show currency symbol ($, €, etc.) if available
 :uppercase true       ; Uppercase token symbol (USD vs usd)
 :thousands-sep ","    ; Thousands separator character
 :decimal-sep "."      ; Decimal separator character
 :components false}    ; Return component map instead of string
```

### Auto-Decimals Logic (Smart Default)

**Design Goal**: Automatically choose appropriate decimal places based on amount size and context:

```clojure
(defn- auto-decimals
  "Smart decimal place selection based on amount size"
  [amount unit]
  (cond
    ;; Tiny amounts (< 0.01) - show 8 decimals
    ;; Example: 0.00000123 BTC
    (< amount 0.01) 8

    ;; Small amounts (< 1) - show 6 decimals
    ;; Example: 0.156789 ETH
    (< amount 1) 6

    ;; Medium amounts (< 1000) - show 2 decimals (standard currency)
    ;; Example: 32.16 USD
    (< amount 1000) 2

    ;; Large amounts (>= 1000) - show 0-2 decimals based on fractional part
    ;; Example: 1,000,000 HASH (no decimals)
    ;;          1,234.56 USD (2 decimals)
    :else
    (let [frac (- amount (long amount))]
      (if (< frac 0.01) 0 2))))
```

**Rationale**:
- **Tiny amounts need precision** - Cryptocurrency dust and fractional units
- **Standard currency uses 2 decimals** - USD, EUR, etc.
- **Large whole numbers don't need decimals** - 1,000,000.00 is cluttered
- **Fractional large amounts use 2** - 1,234.56 is clear

### Currency Symbol Support

**Symbol Registry**:
```clojure
(def currency-symbols
  {:usd "$"
   :eur "€"
   :gbp "£"
   :jpy "¥"
   :cny "¥"
   :btc "₿"
   :eth "Ξ"
   ;; Add more as needed
   })
```

**Behavior**:
- If symbol exists and `:symbol true` → prefix amount with symbol
- If symbol doesn't exist → no prefix, just unit name
- Can be disabled with `:symbol false`

### Helper Functions

#### 1. format-portfolio-summary

**Purpose**: Generate human-readable portfolio summary tables.

**Example**:
```clojure
(format-portfolio-summary
  [[1000 :hash] [5E7 :nhash] [10 :usd]]
  :usd
  [[:/ [0.032 :usd] [1 :hash]]])
=>
"Portfolio Summary
================
1,000 HASH       $32.00
50,000,000 NHASH  $1.60
10 USD           $10.00
────────────────────────
Total:           $43.60"
```

**Use Cases**:
- Portfolio valuation reports
- Transaction summaries
- Balance snapshots
- Audit trails

#### 2. format-rate-comparison

**Purpose**: Compare multiple exchange rates for the same currency pair.

**Example**:
```clojure
(format-rate-comparison
  [[:/ [0.032 :usd] [1 :hash]]
   [:/ [0.0315 :usd] [1 :hash]]
   [:/ [0.0325 :usd] [1 :hash]]]
  ["Coinbase" "Kraken" "Binance"])
=>
"Exchange Rates (USD per HASH)
==============================
Coinbase:  $0.032000
Kraken:    $0.031500
Binance:   $0.032500
────────────────────────────
Best:      $0.032500 (Binance)"
```

**Use Cases**:
- Rate shopping (find best exchange)
- Historical rate analysis
- Arbitrage opportunity detection
- Rate consistency verification

#### 3. format-calculation-steps

**Purpose**: Display intermediate calculation steps for debugging and learning.

**Example**:
```clojure
(format-calculation-steps
  [{:desc "Initial holdings" :value [1000 :hash]}
   {:desc "Convert to USD" :value [32.0 :usd]}
   {:desc "Add cash" :value [42.0 :usd]}])
=>
"Calculation Steps
================
1. Initial holdings:  1,000 HASH
2. Convert to USD:    $32.00 USD
3. Add cash:          $42.00 USD"
```

**Use Cases**:
- Understanding portfolio calculations
- Debugging conversion issues
- Learning/educational demonstrations
- Audit trail for transactions

### Design Decisions

#### Why Two Output Modes?

**String Mode**:
- ✅ Simple, direct answer to "format this"
- ✅ Works in logs, reports, console output
- ✅ Human-readable immediately

**Component Map Mode**:
- ✅ Flexibility for UI rendering
- ✅ Reusable components (amt, symbol, unit)
- ✅ Better for charts/tables
- ✅ Allows custom styling per component

**Decision**: Support both with `:components` flag for maximum flexibility.

#### Why Auto-Decimals?

**Alternative**: Always require explicit `:decimals` parameter

**Problems with explicit**:
- Users don't know best decimal places for each amount
- Cluttered API - too many options
- Inconsistent formatting across use cases

**Benefits of auto**:
- ✅ Smart defaults work for 95% of cases
- ✅ Can override when needed (`:decimals` option)
- ✅ Consistent formatting rules
- ✅ Reduces cognitive load

#### Why Currency Symbol Support?

**Real-World Standard**: ISO 4217 currencies have standard symbols ($, €, £, etc.)

**Benefits**:
- ✅ More readable: "$1,234.56" vs "1,234.56 USD"
- ✅ Space-efficient: Shorter formatted strings
- ✅ International recognition: € is universally understood
- ✅ Professional appearance: Matches financial reports

**Trade-off**: Symbol registry needs maintenance for new currencies.

**Mitigation**:
- Registry is extensible
- Missing symbols fall back to unit name
- Can be disabled with `:symbol false`

### Implementation Notes

**Dependencies**:
- Requires Phase 3B (token amounts) - uses `normalize-unit` and token tuple structure
- Uses existing `round-to` function from calculator.clj
- Uses existing `with-commas` function (or implements `format-with-separators`)

**Integration Points**:
1. Export in `token-conversion-fns` map
2. Update MCP tool description
3. Add to calculator tests
4. Document in AI test scenarios

**Edge Cases to Handle**:
- Zero amounts: "0 USD" vs "$0.00 USD"
- Negative amounts: "-$1,234.56 USD" (symbol positioning)
- Very large amounts: 1E15 → "1,000,000,000,000,000"
- Very small amounts: 1E-10 → "0.0000000001"
- Infinity: ##Inf → "Infinity USD"
- NaN: ##NaN → "NaN USD"

### Benefits

**For Users**:
- ✅ No more scientific notation confusion
- ✅ Readable calculation results
- ✅ Professional-looking reports
- ✅ Flexible output for different use cases

**For Developers**:
- ✅ Consistent formatting rules
- ✅ Component reusability
- ✅ Reduces custom formatting code
- ✅ Helper functions for common patterns

**For AI Agents**:
- ✅ Clear function purpose
- ✅ Examples show usage patterns
- ✅ Options map is self-documenting
- ✅ Component mode enables rich UIs

### Related Documents

- `docs/calculator-implementation-plan.md` - Phase 3E implementation tasks
- `docs/calculator-ai-test-scenarios.md` - Will add formatting test scenarios

### Future Enhancements

**Phase 3F (if needed)**:
- Localization support (i18n)
- Custom number formatting patterns
- Cryptocurrency-specific formatting (wei, gwei, etc.)
- Historical rate formatting with timestamps
- Multi-currency summary tables

**Decision Criteria for Implementation**:
- User feedback requests these features
- Analytics show format-token is heavily used
- Clear use cases emerge from production usage
