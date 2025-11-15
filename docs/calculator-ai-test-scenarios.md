# Calculator Tool - AI Test Scenarios

## Purpose

This document contains test scenarios written in natural language that AI assistants can read, understand, and execute to validate the `calculate` tool's functionality and usability.

## How to Use This Document

**For AI Assistants**:
1. Read each scenario
2. Use the `calculate` tool to solve the problem described
3. Compare the result to the expected value
4. Report PASS if result matches criteria, FAIL otherwise
5. Document any issues or observations

**For Human Reviewers**:
- Review AI's tool selection behavior (did it choose calculate?)
- Verify AI constructed correct expressions
- Check if AI interpreted scenarios correctly
- Note scenarios where AI struggled

## Test Execution

### Automated Testing
```bash
# AI can run automated executor (if available)
uv run python scripts/run_ai_test_scenarios.py
```

### Manual Testing
AI should process each scenario individually and report results.

---

## Basic Arithmetic Scenarios

### Scenario 1: Simple Addition
**Description**: Add two numbers together
**User Request**: "What is 2 plus 3?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(+ 2 3)`
3. Verify result: 5

**Expected Result**: 5
**Pass Criteria**: Result equals 5 exactly (integer or any numeric type)
**Notes**: Tests basic arithmetic and tool selection

---

### Scenario 2: Order of Operations
**Description**: Calculate expression with multiple operations
**User Request**: "Calculate 2 + 3 × 4"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(+ 2 (* 3 4))`
3. Verify result: 14

**Expected Result**: 14
**Pass Criteria**: Result equals 14 exactly
**Notes**: Tests that AI uses prefix notation correctly (no PEMDAS ambiguity)

---

### Scenario 3: Division with Ratios
**Description**: Divide two integers
**User Request**: "What is 10 divided by 3?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(/ 10 3)`
3. Understand result may be ratio (10/3) or decimal (~3.333...)

**Expected Result**: `10/3` (ratio) or `3.333...` (if forced to double)
**Pass Criteria**:
- Ratio: equals `10/3` exactly
- Decimal: between 3.333 and 3.334
**Notes**: Tests Clojure's rational number handling

---

### Scenario 4: Decimal Division
**Description**: Divide two decimal numbers
**User Request**: "What is 10.0 divided by 3.0?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(/ 10.0 3.0)`
3. Verify result is decimal

**Expected Result**: 3.3333333333333335 (or similar floating point)
**Pass Criteria**: Result is between 3.333 and 3.334
**Notes**: Tests floating point arithmetic

---

### Scenario 5: Complex Nested Expression
**Description**: Calculate a complex arithmetic expression
**User Request**: "Calculate ((2 + 3) × 4 - 6) ÷ 2"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(/ (- (* (+ 2 3) 4) 6) 2)`
3. Verify result: 7

**Expected Result**: 7
**Pass Criteria**: Result equals 7 exactly
**Notes**: Tests deeply nested expressions and operator precedence understanding

---

## Geometry and Trigonometry Scenarios

### Scenario 6: Pythagorean Theorem
**Description**: Calculate the hypotenuse of a right triangle
**User Request**: "What is the hypotenuse of a right triangle with sides 3 and 4?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Recall formula: c = √(a² + b²)
3. Construct expression: `(sqrt (+ (* 3 3) (* 4 4)))`
4. Verify result: 5.0

**Expected Result**: 5.0
**Pass Criteria**: Result is between 4.999 and 5.001
**Notes**: Tests sqrt, multiplication, and addition composition

---

### Scenario 7: Circle Area
**Description**: Calculate the area of a circle
**User Request**: "What is the area of a circle with radius 5?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Recall formula: A = πr²
3. Construct expression: `(* pi (* 5 5))` or `(* pi (pow 5 2))`
4. Verify result: ~78.54

**Expected Result**: 78.53981633974483
**Pass Criteria**: Result is between 78.5 and 78.6
**Notes**: Tests pi constant and area formula

---

### Scenario 8: Sine of 30 Degrees
**Description**: Calculate sine of 30 degrees
**User Request**: "What is the sine of 30 degrees?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Use degree-friendly function: `(sind 30)`
3. OR convert to radians: `(sin (/ (* pi 30) 180))`
4. Verify result: 0.5

**Expected Result**: 0.5
**Pass Criteria**: Result is between 0.499 and 0.501
**Notes**: Tests degree trigonometry or radian conversion

---

### Scenario 9: Triangle Height from Angle
**Description**: Calculate height of triangle using trigonometry
**User Request**: "A triangle has a base of 10 and an angle of 30 degrees at the base. What's the height?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Recall: height = base × tan(angle)
3. Construct: `(* 10 (tand 30))`
4. Verify result: ~5.77

**Expected Result**: 5.773502691896257
**Pass Criteria**: Result is between 5.77 and 5.78
**Notes**: Tests tangent function and practical geometry

---

## Statistics Scenarios

### Scenario 10: Mean (Average)
**Description**: Calculate the average of a list of numbers
**User Request**: "What is the average of 1, 2, 3, 4, and 5?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(mean [1 2 3 4 5])`
3. Verify result: 3

**Expected Result**: 3
**Pass Criteria**: Result equals 3 exactly (or 3.0)
**Notes**: Tests vector operations and statistics

---

### Scenario 11: Median - Odd Count
**Description**: Calculate median of odd number of values
**User Request**: "What is the median of 1, 3, 5, 7, 9?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(median [1 3 5 7 9])`
3. Verify result: 5

**Expected Result**: 5
**Pass Criteria**: Result equals 5 exactly
**Notes**: Tests median with odd count (middle value)

---

### Scenario 12: Median - Even Count
**Description**: Calculate median of even number of values
**User Request**: "What is the median of 1, 2, 3, 4?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(median [1 2 3 4])`
3. Verify result: 2.5 (average of 2 and 3)

**Expected Result**: 2.5
**Pass Criteria**: Result equals 2.5 exactly (or 5/2 ratio)
**Notes**: Tests median with even count (average of middle two)

---

### Scenario 13: Standard Deviation
**Description**: Calculate standard deviation of a dataset
**User Request**: "What is the standard deviation of 2, 4, 4, 4, 5, 5, 7, 9?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(stdev [2 4 4 4 5 5 7 9])`
3. Verify result: ~2

**Expected Result**: 2.0
**Pass Criteria**: Result is between 1.9 and 2.1
**Notes**: Tests statistical functions on real dataset

---

### Scenario 14: Sum and Count
**Description**: Calculate total and count of values
**User Request**: "What is the sum of 10, 20, 30, 40, 50, and how many numbers are there?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. For sum: `(sum [10 20 30 40 50])` → 150
3. For count: `(count [10 20 30 40 50])` → 5
4. OR combined: `(let [xs [10 20 30 40 50]] {:sum (sum xs) :count (count xs)})`

**Expected Result**: Sum = 150, Count = 5
**Pass Criteria**: Sum equals 150, count equals 5
**Notes**: Tests vector aggregation functions

---

## Financial Scenarios

### Scenario 15: Compound Interest
**Description**: Calculate future value of investment with compound interest
**User Request**: "I invested $10,000 at 5% annual interest for 10 years. What's it worth now?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Recall formula: FV = P × (1 + r)^n
3. Construct: `(let [p 10000 r 0.05 n 10] (* p (pow (+ 1 r) n)))`
4. Verify result: ~16,288.95

**Expected Result**: 16288.946267774418
**Pass Criteria**: Result is between 16,288 and 16,289
**Notes**: Tests let bindings, pow function, financial formulas

---

### Scenario 16: Percentage Calculation
**Description**: Calculate percentage of a number
**User Request**: "What is 15% of 200?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct: `(* 200 0.15)` or `(/ (* 200 15) 100)`
3. Verify result: 30

**Expected Result**: 30
**Pass Criteria**: Result equals 30 exactly (or 30.0)
**Notes**: Tests basic percentage calculations

---

### Scenario 17: Monthly Payment Calculation
**Description**: Calculate monthly payment on a loan
**User Request**: "What's the monthly payment on a $1,000 loan at 12% annual rate for 12 months?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Simple approximation: `(/ (+ 1000 (* 1000 0.12)) 12)`
3. OR exact formula (more complex)
4. Verify result: ~88.85

**Expected Result**: ~88.85 (simple interest) or ~88.85 (amortized)
**Pass Criteria**: Result is between 88 and 90
**Notes**: Tests practical financial calculations

---

## Vector and Linear Algebra Scenarios

### Scenario 18: Dot Product
**Description**: Calculate dot product of two vectors
**User Request**: "What is the dot product of vectors [1, 2, 3] and [4, 5, 6]?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Recall: dot product = sum of element-wise products
3. Construct: `(dot [1 2 3] [4 5 6])`
4. Verify result: 32 (1×4 + 2×5 + 3×6)

**Expected Result**: 32
**Pass Criteria**: Result equals 32 exactly
**Notes**: Tests vector operations

---

### Scenario 19: Vector Magnitude (Norm)
**Description**: Calculate the length/magnitude of a vector
**User Request**: "What is the magnitude of vector [3, 4]?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Recall: magnitude = √(x² + y²)
3. Construct: `(norm [3 4])`
4. Verify result: 5.0

**Expected Result**: 5.0
**Pass Criteria**: Result is between 4.999 and 5.001
**Notes**: Tests norm function (essentially Pythagorean theorem)

---

### Scenario 20: Cross Product (3D)
**Description**: Calculate cross product of two 3D vectors
**User Request**: "What is the cross product of [1, 0, 0] and [0, 1, 0]?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Recall: cross product results in perpendicular vector
3. Construct: `(cross [1 0 0] [0 1 0])`
4. Verify result: [0, 0, 1]

**Expected Result**: [0, 0, 1]
**Pass Criteria**: Result is vector [0, 0, 1] (or equivalent)
**Notes**: Tests 3D vector operations

---

## Advanced Expression Scenarios

### Scenario 21: Threading Macro
**Description**: Use threading macro for readable calculations
**User Request**: "Start with 100, take the square root, multiply by 2, then add 5. What's the result?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Use threading: `(-> 100 sqrt (* 2) (+ 5))`
3. Verify result: 25.0

**Expected Result**: 25.0
**Pass Criteria**: Result is between 24.99 and 25.01
**Notes**: Tests threading macro readability

---

### Scenario 22: Let Bindings for Clarity
**Description**: Use let bindings for complex calculations
**User Request**: "Calculate the hypotenuse where a=3 and b=4"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Use let for clarity: `(let [a 3 b 4] (sqrt (+ (* a a) (* b b))))`
3. Verify result: 5.0

**Expected Result**: 5.0
**Pass Criteria**: Result is between 4.999 and 5.001
**Notes**: Tests let bindings for intermediate values

---

### Scenario 23: Conditional Logic
**Description**: Use conditional logic in calculation
**User Request**: "Calculate the absolute value of -5"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Use abs function: `(abs -5)`
3. OR conditional: `(if (< -5 0) (* -5 -1) -5)`
4. Verify result: 5

**Expected Result**: 5
**Pass Criteria**: Result equals 5 exactly (or 5.0)
**Notes**: Tests conditional logic and abs function

---

## Edge Case Scenarios

### Scenario 24: Division by Zero (Infinity)
**Description**: Handle division by zero
**User Request**: "What is 1.0 divided by 0.0?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct: `(/ 1.0 0.0)`
3. Recognize result is Infinity
4. Report special value

**Expected Result**: ##Inf (positive infinity)
**Pass Criteria**: Result is infinite (special float value)
**Notes**: Tests edge case handling

---

### Scenario 25: Very Large Numbers
**Description**: Calculate with large exponents
**User Request**: "What is 2 to the power of 100?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct: `(pow 2 100)`
3. Recognize very large result

**Expected Result**: 1.2676506002282294e30 (scientific notation)
**Pass Criteria**: Result is > 1.0e30
**Notes**: Tests large number handling

---

### Scenario 26: Floating Point Precision
**Description**: Understand floating point precision limits
**User Request**: "What is 0.1 + 0.2?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct: `(+ 0.1 0.2)`
3. Recognize result may not be exactly 0.3 (floating point precision)

**Expected Result**: 0.30000000000000004 (typical floating point result)
**Pass Criteria**: Result is between 0.299 and 0.301
**Notes**: Tests floating point precision awareness

---

### Scenario 27: Error - Undefined Function
**Description**: Handle undefined function error gracefully
**User Request**: "Calculate foo of 1 and 2"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Attempt: `(foo 1 2)`
3. Recognize error response
4. Report error type and message

**Expected Result**: Error response with message about undefined symbol
**Pass Criteria**: Response contains `:error` key and `:type "error"`
**Notes**: Tests error handling and reporting

---

## Unit Conversion Scenarios

### Scenario 28: Temperature Conversion
**Description**: Convert Celsius to Fahrenheit
**User Request**: "Convert 100°C to Fahrenheit"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Recall formula: F = (C × 9/5) + 32
3. Construct: `(+ (* 100 (/ 9 5)) 32)`
4. Verify result: 212

**Expected Result**: 212
**Pass Criteria**: Result equals 212 exactly (or 212.0)
**Notes**: Tests formula application and unit conversion

---

### Scenario 29: Speed Conversion
**Description**: Convert km/h to m/s
**User Request**: "Convert 100 km/h to meters per second"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Recall: 1 km = 1000 m, 1 h = 3600 s
3. Construct: `(/ (* 100 1000) 3600)` or `(-> 100 (* 1000) (/ 3600))`
4. Verify result: ~27.78

**Expected Result**: 27.77777777777778
**Pass Criteria**: Result is between 27.7 and 27.8
**Notes**: Tests multi-step conversions with threading

---

### Scenario 30: Volume Conversion
**Description**: Calculate volume of sphere
**User Request**: "What is the volume of a sphere with radius 3?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Recall formula: V = (4/3)πr³
3. Construct: `(* (/ 4 3) pi (pow 3 3))`
4. Verify result: ~113.1

**Expected Result**: 113.09733552923255
**Pass Criteria**: Result is between 113.0 and 113.2
**Notes**: Tests 3D geometry formulas

---

## Test Execution Report Template

After executing all scenarios, AI should provide a summary:

```
CALCULATOR TOOL - AI TEST EXECUTION REPORT
==========================================
Date: [YYYY-MM-DD]
AI Model: [Model name/version]
Total Scenarios: 30

Results:
- PASSED: XX/30 (XX%)
- FAILED: XX/30 (XX%)

Tool Selection:
- Used 'calculate' tool: XX/30 (XX%)
- Used other tool: XX/30 (XX%)

Performance:
- Average response time: XXms
- Timeouts: XX/30

Common Issues:
1. [Issue description]
2. [Issue description]

Observations:
- [General observations about tool usage]
- [Suggestions for improvement]

Failed Scenarios:
[List of failed scenarios with reasons]
```

---

## Notes for Continuous Improvement

As the calculator tool evolves:
- Add new scenarios for new functions
- Remove scenarios for deprecated features
- Adjust pass criteria based on implementation changes
- Document any scenario-specific edge cases discovered
- Use failure patterns to improve tool description

**Last Updated**: 2025-01-14
