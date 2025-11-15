#!/usr/bin/env python3
"""
Automated Calculator Tool AI Test Scenario Runner

Executes all 30 AI test scenarios from docs/calculator-ai-test-scenarios.md
and generates a comprehensive test report.
"""

import json
import subprocess
import sys
import time
from datetime import datetime
from typing import Dict, List, Tuple, Any


class TestScenario:
    """Represents a single test scenario"""

    def __init__(
        self,
        number: int,
        name: str,
        expression: str,
        expected_result: Any,
        pass_criteria: str,
        notes: str = "",
    ):
        self.number = number
        self.name = name
        self.expression = expression
        self.expected_result = expected_result
        self.pass_criteria = pass_criteria
        self.notes = notes
        self.result = None
        self.passed = None
        self.error = None
        self.duration_ms = None


def call_calculate_tool(expression: str) -> Tuple[Dict, float]:
    """Call the calculate tool via MCP client"""
    start_time = time.time()

    try:
        result = subprocess.run(
            [
                "python3",
                "scripts/mcp_nrepl_client.py",
                "--tool",
                "calculate",
                "--args",
                json.dumps({"expr": expression}),
                "--quiet",
            ],
            capture_output=True,
            text=True,
            timeout=10,
        )

        duration_ms = (time.time() - start_time) * 1000

        if result.returncode != 0:
            return {"error": result.stderr, "type": "tool-error"}, duration_ms

        # Parse JSON response
        try:
            response = json.loads(result.stdout)
            return response, duration_ms
        except json.JSONDecodeError:
            return {
                "error": "Invalid JSON response",
                "type": "parse-error",
                "raw": result.stdout,
            }, duration_ms

    except subprocess.TimeoutExpired:
        duration_ms = (time.time() - start_time) * 1000
        return {"error": "Timeout", "type": "timeout"}, duration_ms
    except Exception as e:
        duration_ms = (time.time() - start_time) * 1000
        return {"error": str(e), "type": "exception"}, duration_ms


def verify_result(scenario: TestScenario, actual: Dict) -> bool:
    """Verify actual result matches pass criteria"""

    # Handle error responses
    if "error" in actual:
        # Some scenarios expect errors
        if scenario.number == 27:  # Undefined function error
            return actual.get("type") == "error"
        return False

    # Get actual result value
    result_value = actual.get("result")

    # Scenario-specific verification
    if scenario.number == 1:  # Simple addition: 2 + 3 = 5
        return result_value == 5

    elif scenario.number == 2:  # Order of operations: 2 + 3×4 = 14
        return result_value == 14

    elif scenario.number == 3:  # Division with ratios: 10/3
        # Accept ratio "10/3" as string or decimal ~3.333
        if isinstance(result_value, str) and result_value == "10/3":
            return True
        return 3.333 <= float(result_value) <= 3.334

    elif scenario.number == 4:  # Decimal division: 10.0/3.0
        return 3.333 <= result_value <= 3.334

    elif scenario.number == 5:  # Complex nested: 7
        return result_value == 7

    elif scenario.number == 6:  # Pythagorean: 5.0
        return 4.999 <= result_value <= 5.001

    elif scenario.number == 7:  # Circle area: ~78.54
        return 78.5 <= result_value <= 78.6

    elif scenario.number == 8:  # Sine 30 degrees: 0.5
        return 0.499 <= result_value <= 0.501

    elif scenario.number == 9:  # Triangle height: ~5.77
        return 5.77 <= result_value <= 5.78

    elif scenario.number == 10:  # Mean: 3
        return result_value == 3 or result_value == 3.0

    elif scenario.number == 11:  # Median odd: 5
        return result_value == 5

    elif scenario.number == 12:  # Median even: 2.5
        return result_value == 2.5 or str(result_value) == "5/2"

    elif scenario.number == 13:  # Stdev: ~2.0
        return 1.9 <= result_value <= 2.1

    elif scenario.number == 14:  # Sum: 150
        return result_value == 150

    elif scenario.number == 15:  # Compound interest: ~16288
        return 16288 <= result_value <= 16289

    elif scenario.number == 16:  # Percentage: 30
        return result_value == 30 or result_value == 30.0

    elif scenario.number == 17:  # Monthly payment: ~88.85
        return 88 <= result_value <= 90

    elif scenario.number == 18:  # Dot product: 32
        return result_value == 32

    elif scenario.number == 19:  # Vector norm: 5.0
        return 4.999 <= result_value <= 5.001

    elif scenario.number == 20:  # Cross product: [0, 0, 1]
        return result_value == [0, 0, 1]

    elif scenario.number == 21:  # Threading macro: 25.0
        return 24.99 <= result_value <= 25.01

    elif scenario.number == 22:  # Let bindings: 5.0
        return 4.999 <= result_value <= 5.001

    elif scenario.number == 23:  # Absolute value: 5
        return result_value == 5 or result_value == 5.0

    elif scenario.number == 24:  # Division by zero: Infinity
        return result_value == float("inf") or str(result_value) == "##Inf"

    elif scenario.number == 25:  # Large numbers: 2^100
        return result_value > 1.0e30

    elif scenario.number == 26:  # Floating point: 0.1 + 0.2
        return 0.299 <= result_value <= 0.301

    elif scenario.number == 27:  # Error handling
        # Already handled above
        return True

    elif scenario.number == 28:  # Temperature: 212
        return result_value == 212 or result_value == 212.0

    elif scenario.number == 29:  # Speed conversion: ~27.78
        return 27.7 <= result_value <= 27.8

    elif scenario.number == 30:  # Sphere volume: ~113.1
        return 113.0 <= result_value <= 113.2

    return False


def create_scenarios() -> List[TestScenario]:
    """Create all 30 test scenarios"""
    return [
        # Basic Arithmetic (1-5)
        TestScenario(1, "Simple Addition", "(+ 2 3)", 5, "equals 5"),
        TestScenario(
            2, "Order of Operations", "(+ 2 (* 3 4))", 14, "equals 14"
        ),
        TestScenario(3, "Division with Ratios", "(/ 10 3)", "10/3", "ratio or ~3.333"),
        TestScenario(4, "Decimal Division", "(/ 10.0 3.0)", 3.333, "between 3.333-3.334"),
        TestScenario(
            5, "Complex Nested", "(/ (- (* (+ 2 3) 4) 6) 2)", 7, "equals 7"
        ),
        # Geometry and Trig (6-9)
        TestScenario(
            6, "Pythagorean Theorem", "(sqrt (+ (* 3 3) (* 4 4)))", 5.0, "~5.0"
        ),
        TestScenario(7, "Circle Area", "(* pi (* 5 5))", 78.54, "~78.54"),
        TestScenario(8, "Sine 30 Degrees", "(sind 30)", 0.5, "~0.5"),
        TestScenario(9, "Triangle Height", "(* 10 (tand 30))", 5.77, "~5.77"),
        # Statistics (10-14)
        TestScenario(10, "Mean", "(mean [1 2 3 4 5])", 3, "equals 3"),
        TestScenario(11, "Median Odd", "(median [1 3 5 7 9])", 5, "equals 5"),
        TestScenario(12, "Median Even", "(median [1 2 3 4])", 2.5, "equals 2.5"),
        TestScenario(
            13, "Standard Deviation", "(stdev [2 4 4 4 5 5 7 9])", 2.0, "~2.0"
        ),
        TestScenario(14, "Sum", "(sum [10 20 30 40 50])", 150, "equals 150"),
        # Financial (15-17)
        TestScenario(
            15,
            "Compound Interest",
            "(let [p 10000 r 0.05 n 10] (* p (pow (+ 1 r) n)))",
            16288.95,
            "~16288",
        ),
        TestScenario(16, "Percentage", "(* 200 0.15)", 30, "equals 30"),
        TestScenario(
            17, "Monthly Payment", "(/ (+ 1000 (* 1000 0.12)) 12)", 88.85, "~88.85"
        ),
        # Vector/Linear Algebra (18-20)
        TestScenario(18, "Dot Product", "(dot [1 2 3] [4 5 6])", 32, "equals 32"),
        TestScenario(19, "Vector Norm", "(norm [3 4])", 5.0, "~5.0"),
        TestScenario(
            20, "Cross Product", "(cross [1 0 0] [0 1 0])", [0, 0, 1], "equals [0,0,1]"
        ),
        # Advanced Expressions (21-23)
        TestScenario(21, "Threading Macro", "(-> 100 sqrt (* 2) (+ 5))", 25.0, "~25.0"),
        TestScenario(
            22,
            "Let Bindings",
            "(let [a 3 b 4] (sqrt (+ (* a a) (* b b))))",
            5.0,
            "~5.0",
        ),
        TestScenario(23, "Absolute Value", "(abs -5)", 5, "equals 5"),
        # Edge Cases (24-27)
        TestScenario(24, "Division by Zero", "(/ 1.0 0.0)", "Infinity", "Infinity"),
        TestScenario(25, "Large Numbers", "(pow 2 100)", 1e30, ">1e30"),
        TestScenario(26, "Floating Point", "(+ 0.1 0.2)", 0.3, "~0.3"),
        TestScenario(
            27, "Error Handling", "(undefined-fn 1)", "error", "error response"
        ),
        # Unit Conversions (28-30)
        TestScenario(28, "Temperature", "(+ (* 100 (/ 9 5)) 32)", 212, "equals 212"),
        TestScenario(29, "Speed Conversion", "(/ (* 100 1000) 3600)", 27.78, "~27.78"),
        TestScenario(30, "Sphere Volume", "(* (/ 4 3) pi (pow 3 3))", 113.1, "~113.1"),
    ]


def run_tests() -> Dict:
    """Run all test scenarios and generate report"""
    print("=" * 80)
    print("CALCULATOR TOOL - AI TEST EXECUTION")
    print("=" * 80)
    print(f"Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"AI Model: Claude Sonnet 4.5 (via automated test runner)")
    print("=" * 80)
    print()

    scenarios = create_scenarios()
    total = len(scenarios)
    passed = 0
    failed = 0
    timeouts = 0
    total_duration = 0

    for i, scenario in enumerate(scenarios, 1):
        print(f"[{i}/{total}] Scenario {scenario.number}: {scenario.name}...", end=" ")
        sys.stdout.flush()

        # Execute test
        result, duration = call_calculate_tool(scenario.expression)
        scenario.result = result
        scenario.duration_ms = duration
        total_duration += duration

        # Verify result
        if result.get("type") == "timeout":
            scenario.passed = False
            scenario.error = "Timeout"
            timeouts += 1
            failed += 1
            print("⏱️  TIMEOUT")
        else:
            scenario.passed = verify_result(scenario, result)
            if scenario.passed:
                passed += 1
                print(f"✅ PASS ({duration:.1f}ms)")
            else:
                failed += 1
                print(f"❌ FAIL ({duration:.1f}ms)")
                if "error" in result:
                    scenario.error = result.get("error", "Unknown error")

    # Generate summary report
    print()
    print("=" * 80)
    print("TEST EXECUTION REPORT")
    print("=" * 80)
    print(f"Total Scenarios: {total}")
    print(f"PASSED: {passed}/{total} ({passed*100//total}%)")
    print(f"FAILED: {failed}/{total} ({failed*100//total}%)")
    print(f"Timeouts: {timeouts}/{total}")
    print(f"Average Response Time: {total_duration/total:.1f}ms")
    print()

    # Failed scenarios
    if failed > 0:
        print("FAILED SCENARIOS:")
        print("-" * 80)
        for scenario in scenarios:
            if not scenario.passed:
                print(f"  #{scenario.number}: {scenario.name}")
                print(f"    Expression: {scenario.expression}")
                print(f"    Expected: {scenario.pass_criteria}")
                if scenario.error:
                    print(f"    Error: {scenario.error}")
                else:
                    print(f"    Actual: {scenario.result.get('result', 'N/A')}")
                print()

    # Success message
    if passed == total:
        print("🎉 ALL TESTS PASSED! 🎉")
    elif passed >= total * 0.9:
        print("✅ Excellent! Most tests passed.")
    elif passed >= total * 0.7:
        print("⚠️  Good progress, but some failures need attention.")
    else:
        print("❌ Multiple failures detected. Review implementation.")

    print("=" * 80)

    return {
        "total": total,
        "passed": passed,
        "failed": failed,
        "timeouts": timeouts,
        "avg_duration_ms": total_duration / total,
        "scenarios": [
            {
                "number": s.number,
                "name": s.name,
                "passed": s.passed,
                "duration_ms": s.duration_ms,
                "error": s.error,
            }
            for s in scenarios
        ],
    }


if __name__ == "__main__":
    try:
        report = run_tests()
        sys.exit(0 if report["passed"] == report["total"] else 1)
    except KeyboardInterrupt:
        print("\n\nTest execution interrupted by user.")
        sys.exit(1)
    except Exception as e:
        print(f"\n\nFATAL ERROR: {e}")
        import traceback

        traceback.print_exc()
        sys.exit(1)
