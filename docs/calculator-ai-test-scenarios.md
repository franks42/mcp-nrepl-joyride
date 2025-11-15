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

## Number Formatting Scenarios

### Scenario 31: Format Number with Commas
**Description**: Format large numbers with thousand separators
**User Request**: "Format the number 1234567.89 with commas"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(with-commas 1234567.89)`
3. Verify result has commas

**Expected Result**: "1,234,567.89"
**Pass Criteria**: Result equals "1,234,567.89" (string)
**Notes**: Tests number formatting for readability

---

### Scenario 32: Round to Decimal Places
**Description**: Round a number to specific decimal places
**User Request**: "Round 3.14159 to 2 decimal places"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(round-to 3.14159 2)`
3. Verify result: 3.14

**Expected Result**: 3.14
**Pass Criteria**: Result equals 3.14 exactly
**Notes**: Tests precision control

---

### Scenario 33: Scientific Notation
**Description**: Convert large number to scientific notation
**User Request**: "Express 123456789 in scientific notation"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(scientific 123456789)`
3. Verify result in scientific format

**Expected Result**: "1.23e+08"
**Pass Criteria**: Result matches scientific notation pattern
**Notes**: Tests scientific notation formatting

---

### Scenario 34: Convert to Decimal
**Description**: Convert ratio to decimal
**User Request**: "Convert 22/7 to decimal"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(to-decimal (/ 22 7))`
3. Verify decimal result

**Expected Result**: 3.142857142857143
**Pass Criteria**: Result is between 3.14 and 3.15
**Notes**: Tests ratio to decimal conversion

---

## Enhanced Financial Scenarios

### Scenario 35: Percent Change
**Description**: Calculate percentage change between two values
**User Request**: "My investment went from $1000 to $1250. What's the percent change?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(percent-change 1000 1250)`
3. Verify result contains change, percent, direction, formatted

**Expected Result**: Map with :percent 25.0, :direction :increase
**Pass Criteria**: Percent equals 25.0, direction is :increase
**Notes**: Tests rich map output with multiple representations

---

### Scenario 36: Percent Of
**Description**: Calculate what percentage one number is of another
**User Request**: "What percentage is 45 of 180?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(percent-of 45 180)`
3. Verify result: 25%

**Expected Result**: Map with :percentage 25.0
**Pass Criteria**: Percentage equals 25.0
**Notes**: Tests percentage calculation

---

### Scenario 37: ROI Calculation
**Description**: Calculate return on investment
**User Request**: "I invested $5000 and got back $6500. What's my ROI?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(roi 5000 6500)`
3. Verify profit, roi-percent, multiplier

**Expected Result**: Map with :roi-percent 30.0, :profit 1500
**Pass Criteria**: ROI equals 30%, profit equals 1500
**Notes**: Tests investment return calculations

---

### Scenario 38: Simple Interest
**Description**: Calculate simple interest on a loan
**User Request**: "Calculate simple interest on $10,000 at 5% for 3 years"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(simple-interest 10000 0.05 3)`
3. Verify interest and total

**Expected Result**: Map with :interest 1500.0, :total 11500.0
**Pass Criteria**: Interest equals 1500, total equals 11500
**Notes**: Tests simple interest formula

---

### Scenario 39: Market Share
**Description**: Calculate company's market share
**User Request**: "Company revenue is $50M, total market is $500M. What's their market share?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(market-share 50000000 500000000)`
3. Verify percentage and decimal

**Expected Result**: Map with :percentage 10.0
**Pass Criteria**: Market share equals 10%
**Notes**: Tests market share calculation

---

### Scenario 40: Token Portfolio Value
**Description**: Calculate crypto token portfolio value
**User Request**: "I have 100 tokens worth $25 each. What's the total value?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(token-value 100 25)`
3. Verify total value

**Expected Result**: Map with :total-value 2500
**Pass Criteria**: Total value equals 2500
**Notes**: Tests token value calculation

---

## Date and Time Scenarios

### Scenario 41: Current Unix Timestamp
**Description**: Get current time as unix timestamp
**User Request**: "What's the current unix timestamp?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(unix-now)`
3. Verify result contains unix, iso, date, time

**Expected Result**: Map with current unix timestamp
**Pass Criteria**: Unix timestamp is reasonable (> 1700000000 for 2025)
**Notes**: Tests current time retrieval

---

### Scenario 42: Unix to Date Conversion
**Description**: Convert unix timestamp to readable date
**User Request**: "Convert unix timestamp 1609459200 to a date"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(unix-to-date 1609459200)`
3. Verify date components

**Expected Result**: Map with :date "2021-01-01"
**Pass Criteria**: Date equals 2021-01-01
**Notes**: Tests unix to date conversion (New Year 2021)

---

### Scenario 43: Date to Unix Conversion
**Description**: Convert date string to unix timestamp
**User Request**: "Convert 2025-01-01 to unix timestamp"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(date-to-unix "2025-01-01")`
3. Verify unix timestamp

**Expected Result**: 1735689600
**Pass Criteria**: Timestamp equals 1735689600
**Notes**: Tests date to unix conversion

---

### Scenario 44: Days Between Dates
**Description**: Calculate days between two dates
**User Request**: "How many days between 2025-01-01 and 2025-01-31?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(days-between "2025-01-01" "2025-01-31")`
3. Verify days count

**Expected Result**: Map with :days 30
**Pass Criteria**: Days equals 30
**Notes**: Tests date difference calculation

---

### Scenario 45: Add Days to Date
**Description**: Add days to a date
**User Request**: "What date is 30 days after 2025-01-01?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(add-days "2025-01-01" 30)`
3. Verify new date

**Expected Result**: Map with :new-date "2025-01-31"
**Pass Criteria**: New date equals 2025-01-31
**Notes**: Tests date arithmetic

---

### Scenario 46: Lock Period End Date
**Description**: Calculate when a staking lock period ends
**User Request**: "I locked tokens on Jan 1, 2025 (unix: 1735689600) for 365 days. When do they unlock?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(lock-period-end 1735689600 365)`
3. Verify unlock date and remaining days

**Expected Result**: Map with :unlock-date "2026-01-01"
**Pass Criteria**: Unlock date equals 2026-01-01
**Notes**: Tests DeFi lock period calculations

---

### Scenario 47: Check If Unlocked
**Description**: Check if tokens are currently unlocked
**User Request**: "Are tokens locked until Jan 1, 2026 (unix: 1767225600) unlocked yet?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(is-unlocked 1767225600)`
3. Verify unlocked status

**Expected Result**: Map with :unlocked false (assuming current date < 2026)
**Pass Criteria**: Unlocked status reflects current time vs unlock time
**Notes**: Tests time-based unlock checking

---

## Cryptocurrency Conversion Scenarios

### Scenario 48: Wei to Ether Conversion
**Description**: Convert wei (smallest Ethereum unit) to ether
**User Request**: "Convert 1000000000000000000 wei to ether"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(wei->ether 1000000000000000000)`
3. Verify ether and gwei values

**Expected Result**: Map with :ether 1.0
**Pass Criteria**: Ether equals 1.0
**Notes**: Tests Ethereum unit conversion (1 ETH = 10^18 wei)

---

### Scenario 49: Ether to Wei Conversion
**Description**: Convert ether to wei
**User Request**: "Convert 2.5 ether to wei"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(ether->wei 2.5)`
3. Verify wei value

**Expected Result**: Map with :wei 2500000000000000000
**Pass Criteria**: Wei equals 2.5 × 10^18
**Notes**: Tests reverse Ethereum conversion

---

### Scenario 50: Satoshis to Bitcoin
**Description**: Convert satoshis (smallest Bitcoin unit) to BTC
**User Request**: "Convert 50000000 satoshis to bitcoin"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(sats->btc 50000000)`
3. Verify BTC value

**Expected Result**: Map with :btc 0.5
**Pass Criteria**: BTC equals 0.5
**Notes**: Tests Bitcoin unit conversion (1 BTC = 10^8 sats)

---

### Scenario 51: Bitcoin to Satoshis
**Description**: Convert bitcoin to satoshis
**User Request**: "Convert 0.25 BTC to satoshis"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(btc->sats 0.25)`
3. Verify satoshi value

**Expected Result**: Map with :satoshis 25000000
**Pass Criteria**: Satoshis equal 25000000
**Notes**: Tests reverse Bitcoin conversion

---

### Scenario 52: Generic Token to Smallest Unit
**Description**: Convert token amount to smallest unit (with custom decimals)
**User Request**: "Convert 100 USDC (6 decimals) to smallest unit"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(to-smallest-unit 100 6)`
3. Verify smallest unit value

**Expected Result**: Map with :smallest-unit 100000000
**Pass Criteria**: Smallest unit equals 100 × 10^6
**Notes**: Tests generic token conversion with custom decimals

---

## DeFi Operations Scenarios

### Scenario 53: Impermanent Loss
**Description**: Calculate impermanent loss in AMM liquidity pool
**User Request**: "I provided liquidity when token was $100, now it's $150. What's my impermanent loss?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(impermanent-loss 100 150)`
3. Verify IL percentage and vs-hodl comparison

**Expected Result**: Map with :impermanent-loss-percent ~2.02%
**Pass Criteria**: IL is between 2.0% and 2.1%
**Notes**: Tests AMM impermanent loss formula (critical DeFi metric)

---

### Scenario 54: Liquidity Pool Share
**Description**: Calculate ownership percentage in liquidity pool
**User Request**: "I have 1000 tokenA and 2000 tokenB. Pool has 100000 tokenA and 200000 tokenB. What's my share?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(liquidity-pool-share 1000 2000 100000 200000)`
3. Verify pool share percentage

**Expected Result**: Map with :pool-share-percent 1.0
**Pass Criteria**: Pool share equals 1%
**Notes**: Tests LP share calculation

---

### Scenario 55: Slippage Impact
**Description**: Calculate slippage on Uniswap-style swap
**User Request**: "Swapping 1000 tokens in a pool with 100000 reserves each. What's the slippage?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(slippage-impact 1000 100000 100000)`
3. Verify price impact percentage

**Expected Result**: Map with :slippage ~1.97%
**Pass Criteria**: Slippage is between 1.9% and 2.0%
**Notes**: Tests AMM slippage with 0.3% fee

---

### Scenario 56: APY to APR Conversion
**Description**: Convert annual percentage yield to annual percentage rate
**User Request**: "Convert 12.5% APY with daily compounding to APR"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(apy-to-apr 12.5 365)`
3. Verify APR value

**Expected Result**: Map with :apr ~11.78
**Pass Criteria**: APR is between 11.7 and 11.9
**Notes**: Tests yield-to-rate conversion

---

### Scenario 57: APR to APY Conversion
**Description**: Convert annual percentage rate to annual percentage yield
**User Request**: "Convert 10% APR with daily compounding to APY"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(apr-to-apy 10 365)`
3. Verify APY value

**Expected Result**: Map with :apy ~10.52
**Pass Criteria**: APY is between 10.5 and 10.6
**Notes**: Tests rate-to-yield conversion with compounding

---

### Scenario 58: Staking Rewards
**Description**: Calculate staking rewards over time
**User Request**: "I staked 10000 tokens at 5% APY for 365 days. What are my rewards?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(staking-rewards 10000 0.05 365)`
3. Verify rewards and total

**Expected Result**: Map with :rewards 5.0, :total 10005.0
**Pass Criteria**: Rewards equal 5, total equals 10005 (note: this is simple interest, not compound)
**Notes**: Tests staking reward calculation

---

### Scenario 59: Liquidation Price
**Description**: Calculate liquidation price for leveraged position
**User Request**: "I have $10000 collateral, borrowed $7000, liquidation threshold 75%. What's the liquidation price?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(liquidation-price 10000 7000 0.75)`
3. Verify liquidation price and health factor

**Expected Result**: Map with :liquidation-price ~9333.33, :health-factor ~1.07, :safe true
**Pass Criteria**: Liquidation price ~9333, health factor > 1, safe = true
**Notes**: Tests liquidation risk calculation (critical for DeFi lending)

---

### Scenario 60: Leverage Ratio
**Description**: Calculate leverage ratio of a position
**User Request**: "I have $10000 collateral and borrowed $7000. What's my leverage?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(leverage-ratio 10000 7000)`
3. Verify leverage multiplier and LTV

**Expected Result**: Map with :leverage ~3.33, :ltv 0.7
**Pass Criteria**: Leverage ~3.33x, LTV = 0.7 (70%)
**Notes**: Tests leverage calculation

---

### Scenario 61: Gas Cost Calculation
**Description**: Calculate Ethereum transaction gas cost
**User Request**: "Transaction uses 21000 gas at 50 gwei. ETH is $3000. What's the cost?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(gas-cost 21000 50 3000)`
3. Verify cost in ETH and USD

**Expected Result**: Map with :gas-cost-eth 0.00105, :gas-cost-usd 3.15
**Pass Criteria**: ETH cost = 0.00105, USD cost = 3.15
**Notes**: Tests Ethereum gas cost calculation

---

### Scenario 62: Market Capitalization
**Description**: Calculate crypto market cap
**User Request**: "Token price is $50, circulating supply is 21 million. What's the market cap?"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(market-cap 50 21000000)`
3. Verify market cap

**Expected Result**: Map with :market-cap 1050000000, :billions 1.05
**Pass Criteria**: Market cap equals $1.05 billion
**Notes**: Tests market cap calculation

---

## Enhanced Error Message Scenarios

### Scenario 63: Division by Zero with Hint
**Description**: Test enhanced error message for division by zero
**User Request**: "Divide 10 by 0"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(/ 10 0)`
3. Recognize enhanced error response

**Expected Result**: Error map with :hint and :suggestion
**Pass Criteria**: Error includes "Division by zero detected" hint and conditional logic suggestion
**Notes**: Tests enhanced error messaging system

---

### Scenario 64: Undefined Symbol with Suggestions
**Description**: Test enhanced error for undefined function
**User Request**: "Use the foobar function on 5"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(foobar 5)`
3. Recognize enhanced error with function suggestions

**Expected Result**: Error map with function name and available functions list
**Pass Criteria**: Error includes "Function 'foobar' not found" and lists available functions
**Notes**: Tests helpful error guidance for undefined symbols

---

### Scenario 65: Wrong Arity with Examples
**Description**: Test enhanced error for incorrect argument count
**User Request**: "Calculate pow of 2 (missing exponent)"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(pow 2)`
3. Recognize wrong arity error with example

**Expected Result**: Error map with signature explanation
**Pass Criteria**: Error includes "Wrong number of args" hint and example showing correct usage
**Notes**: Tests argument count error guidance

---

### Scenario 66: DeFi-Specific Error Hint
**Description**: Test DeFi-specific error detection
**User Request**: "Calculate slippage with zero reserves"
**Expected AI Behavior**:
1. Select `calculate` tool
2. Construct expression: `(slippage-impact 1000 0 100000)`
3. Recognize DeFi context in error

**Expected Result**: Error map with DeFi-specific hint
**Pass Criteria**: Error mentions "pool reserves" or "liquidity values"
**Notes**: Tests contextual error detection for DeFi operations

---

## Test Execution Report Template

After executing all scenarios, AI should provide a summary:

```
CALCULATOR TOOL - AI TEST EXECUTION REPORT
==========================================
Date: [YYYY-MM-DD]
AI Model: [Model name/version]
Total Scenarios: 66

Results:
- PASSED: XX/66 (XX%)
- FAILED: XX/66 (XX%)

Tool Selection:
- Used 'calculate' tool: XX/66 (XX%)
- Used other tool: XX/66 (XX%)

Performance:
- Average response time: XXms
- Timeouts: XX/66

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

**Last Updated**: 2025-11-15 (Phase 3A: Added 36 scenarios for new functions - formatting, financial, date/time, crypto, DeFi, enhanced errors)
