#!/usr/bin/env python3
"""
Comprehensive Async Primitives Test Suite.

Tests the two core async primitives (nrepl-send-message-async and
nrepl-get-result-async) exhaustively to validate they can handle ALL
nREPL operations before building convenience layers on top.

This script validates the async foundation is solid for:
- All nREPL operations (eval, doc, source, complete, etc.)
- Error handling and timeout scenarios
- Edge cases (large responses, concurrent operations)
- Both MCP server and real nREPL server environments

Architecture Validation:
The goal is to prove that ALL nREPL functionality can be achieved
using ONLY the two async primitives, enabling us to later build
sync convenience wrappers that use these primitives under the hood.

Usage:
  # Full test suite
  uv run python test_async_primitives_comprehensive.py

  # Quick validation
  uv run python test_async_primitives_comprehensive.py --quick

  # Specific test categories
  uv run python test_async_primitives_comprehensive.py --category basic
"""

import asyncio
import argparse
import json
import sys
import time
from typing import Dict, Any, List, Optional, Tuple

# Import our stdio MCP client
from stdio_mcp_client import StdioMCPClient


class AsyncPrimitivesTestSuite:
    """
    Comprehensive test suite for async primitives validation.

    Tests ONLY nrepl-send-message-async + nrepl-get-result-async
    to prove they can handle all nREPL use cases.
    """

    def __init__(self, server_command: List[str], quiet: bool = False):
        """Initialize test suite."""
        self.server_command = server_command
        self.quiet = quiet
        self.results: List[Dict[str, Any]] = []
        self.session_id: Optional[str] = None

    def _log(self, message: str, level: str = "INFO") -> None:
        """Log test progress."""
        if not self.quiet or level == "ERROR":
            timestamp = time.strftime("%H:%M:%S")
            print(f"[{timestamp}] {level}: {message}")

    def _record_result(
        self, test_name: str, success: bool, duration: float, details: str = ""
    ) -> None:
        """Record test result."""
        self.results.append(
            {
                "test": test_name,
                "success": success,
                "duration_ms": round(duration * 1000, 2),
                "details": details,
            }
        )

        status = "✅ PASS" if success else "❌ FAIL"
        duration_str = f"({duration*1000:.1f}ms)"
        self._log(f"{status} {test_name} {duration_str} {details}")

    async def _send_and_get(
        self,
        client: StdioMCPClient,
        message: Dict[str, Any],
        timeout_ms: int = 30000,
    ) -> Tuple[bool, Dict[str, Any]]:
        """
        Core async primitive workflow: send + poll + get.

        This is the fundamental pattern we're validating.
        Returns (success, result_data)
        """
        try:
            # Step 1: Send message async
            send_result = client.call_tool(
                "nrepl-send-message-async",
                {"message": message, "timeout-ms": timeout_ms},
            )

            if "error" in send_result:
                return False, {"error": f"Send failed: {send_result['error']}"}

            # Extract message-id
            content = send_result.get("content", [])
            if not content:
                return False, {"error": "No content in send result"}

            send_data = json.loads(content[0]["text"])
            message_id = send_data["message-id"]

            # Step 2: Poll for completion (with timeout)
            max_polls = timeout_ms // 500  # Poll every 500ms
            for attempt in range(max_polls):
                get_result = client.call_tool(
                    "nrepl-get-result-async", {"message-id": message_id}
                )

                if "error" in get_result:
                    return False, {
                        "error": f"Get failed: {get_result['error']}"
                    }

                get_content = get_result.get("content", [])
                if not get_content:
                    return False, {"error": "No content in get result"}

                get_data = json.loads(get_content[0]["text"])
                status = get_data["status"]

                if status == "completed":
                    return True, get_data["result"]
                elif status in ["failed", "expired"]:
                    return False, {
                        "error": get_data.get("error-info", "Unknown error")
                    }
                elif status == "pending":
                    await asyncio.sleep(0.5)  # Wait before next poll
                    continue
                else:
                    return False, {"error": f"Unknown status: {status}"}

            return False, {
                "error": "Polling timeout - message never completed"
            }

        except Exception as e:
            return False, {"error": f"Exception in send_and_get: {e}"}

    async def test_basic_evaluation(self, client: StdioMCPClient) -> bool:
        """Test basic Clojure evaluation."""
        tests = [
            ("Simple arithmetic", {"op": "eval", "code": "(+ 1 2 3)"}, "6"),
            (
                "String operations",
                {"op": "eval", "code": '(str "Hello" " " "World")'},
                '"Hello World"',
            ),
            (
                "Data structures",
                {"op": "eval", "code": "(count [1 2 3 4 5])"},
                "5",
            ),
            (
                "Function definition",
                {"op": "eval", "code": "(defn double [x] (* x 2))"},
                None,
            ),
            ("Function call", {"op": "eval", "code": "(double 21)"}, "42"),
            (
                "Boolean logic",
                {"op": "eval", "code": "(and true false)"},
                "false",
            ),
            (
                "Collections",
                {"op": "eval", "code": "(map inc [1 2 3])"},
                "(2 3 4)",
            ),
        ]

        all_passed = True

        for test_name, message, expected in tests:
            start_time = time.time()
            success, result = await self._send_and_get(client, message)
            duration = time.time() - start_time

            if success and expected:
                # Check if result contains expected value
                result_str = str(result.get("value", ""))
                test_success = expected in result_str
                details = f"Expected: {expected}, Got: {result_str}"
            elif success and not expected:
                # Just check for successful execution (like defn)
                test_success = "error" not in result
                details = "Executed successfully"
            else:
                test_success = False
                details = f"Failed: {result.get('error', 'Unknown error')}"

            self._record_result(
                f"Basic/{test_name}", test_success, duration, details
            )
            all_passed &= test_success

        return all_passed

    async def test_session_management(self, client: StdioMCPClient) -> bool:
        """Test session operations."""
        all_passed = True

        # Test 1: Clone session
        start_time = time.time()
        success, result = await self._send_and_get(client, {"op": "clone"})
        duration = time.time() - start_time

        if success and "session" in result:
            self.session_id = result["session"]
            test_success = True
            details = f"Created session: {self.session_id}"
        else:
            test_success = False
            details = f"Failed to clone session: {result}"

        self._record_result("Session/Clone", test_success, duration, details)
        all_passed &= test_success

        # Test 2: Eval in session
        if self.session_id:
            start_time = time.time()
            success, result = await self._send_and_get(
                client,
                {
                    "op": "eval",
                    "code": "(def session-var 42)",
                    "session": self.session_id,
                },
            )
            duration = time.time() - start_time

            test_success = success and "error" not in result
            details = f"Session eval: {result.get('value', result)}"
            self._record_result(
                "Session/Eval", test_success, duration, details
            )
            all_passed &= test_success

            # Test 3: Verify session isolation
            start_time = time.time()
            success, result = await self._send_and_get(
                client,
                {
                    "op": "eval",
                    "code": "session-var",
                    "session": self.session_id,
                },
            )
            duration = time.time() - start_time

            test_success = success and "42" in str(result.get("value", ""))
            details = f"Session isolation: {result.get('value', result)}"
            self._record_result(
                "Session/Isolation", test_success, duration, details
            )
            all_passed &= test_success

        return all_passed

    async def test_documentation_operations(
        self, client: StdioMCPClient
    ) -> bool:
        """Test doc, source, and info operations."""
        tests = [
            ("Doc for map", {"op": "info", "symbol": "map"}),
            ("Doc for reduce", {"op": "info", "symbol": "reduce"}),
            ("Doc for defn", {"op": "info", "symbol": "defn"}),
        ]

        all_passed = True

        for test_name, message in tests:
            start_time = time.time()
            success, result = await self._send_and_get(client, message)
            duration = time.time() - start_time

            # Check if we got documentation info
            has_doc = success and (
                "doc" in result
                or "arglists" in result
                or "info" in str(result)
            )

            details = f"Doc available: {has_doc}"
            self._record_result(f"Doc/{test_name}", has_doc, duration, details)
            all_passed &= has_doc

        return all_passed

    async def test_completion_operations(self, client: StdioMCPClient) -> bool:
        """Test completion operations."""
        tests = [
            ("Complete 'ma'", {"op": "completions", "prefix": "ma"}),
            ("Complete 'def'", {"op": "completions", "prefix": "def"}),
            ("Complete 'str'", {"op": "completions", "prefix": "str"}),
        ]

        all_passed = True

        for test_name, message in tests:
            start_time = time.time()
            success, result = await self._send_and_get(client, message)
            duration = time.time() - start_time

            # Check if we got completions
            has_completions = success and (
                "completions" in result
                or isinstance(result.get("completions"), list)
            )

            completion_count = 0
            if has_completions:
                completions = result.get("completions", [])
                completion_count = (
                    len(completions) if isinstance(completions, list) else 0
                )

            details = f"Completions: {completion_count}"
            self._record_result(
                f"Complete/{test_name}", has_completions, duration, details
            )
            all_passed &= has_completions

        return all_passed

    async def test_server_operations(self, client: StdioMCPClient) -> bool:
        """Test server-level operations."""
        tests = [
            ("Describe server", {"op": "describe"}),
        ]

        all_passed = True

        for test_name, message in tests:
            start_time = time.time()
            success, result = await self._send_and_get(client, message)
            duration = time.time() - start_time

            # Check if we got server info
            has_info = success and (
                "ops" in result or "versions" in result or "aux" in result
            )

            details = f"Server info available: {has_info}"
            self._record_result(
                f"Server/{test_name}", has_info, duration, details
            )
            all_passed &= has_info

        return all_passed

    async def test_error_conditions(self, client: StdioMCPClient) -> bool:
        """Test error handling."""
        tests = [
            ("Syntax error", {"op": "eval", "code": "(+ 1 2"}, "error"),
            (
                "Undefined symbol",
                {"op": "eval", "code": "undefined-symbol"},
                "error",
            ),
            ("Invalid operation", {"op": "invalid-op"}, "error"),
        ]

        all_passed = True

        for test_name, message, expected in tests:
            start_time = time.time()
            success, result = await self._send_and_get(client, message)
            duration = time.time() - start_time

            # For error tests, we expect either:
            # 1. send_and_get returns False (caught error)
            # 2. Result contains error information
            has_error = not success or "error" in str(result).lower()

            details = f"Error properly handled: {has_error}"
            self._record_result(
                f"Error/{test_name}", has_error, duration, details
            )
            all_passed &= has_error

        return all_passed

    async def test_timeout_scenarios(self, client: StdioMCPClient) -> bool:
        """Test timeout handling."""
        # Test with a very short timeout
        start_time = time.time()
        success, result = await self._send_and_get(
            client,
            {"op": "eval", "code": "(Thread/sleep 2000)"},  # 2 second sleep
            timeout_ms=1000,  # 1 second timeout
        )
        duration = time.time() - start_time

        # Should timeout
        timed_out = not success and "timeout" in str(result).lower()
        details = f"Properly timed out: {timed_out}"
        self._record_result(
            "Timeout/Short timeout", timed_out, duration, details
        )

        return timed_out

    async def test_large_responses(self, client: StdioMCPClient) -> bool:
        """Test handling of large responses."""
        start_time = time.time()
        success, result = await self._send_and_get(
            client, {"op": "eval", "code": "(take 1000 (range))"}  # Large list
        )
        duration = time.time() - start_time

        # Should handle large response
        handled_large = success and "value" in result
        details = f"Large response handled: {handled_large}"
        self._record_result(
            "Large/Large list", handled_large, duration, details
        )

        return handled_large

    async def run_test_category(
        self, client: StdioMCPClient, category: str
    ) -> bool:
        """Run specific test category."""
        self._log(f"Running {category} tests...")

        if category == "basic":
            return await self.test_basic_evaluation(client)
        elif category == "session":
            return await self.test_session_management(client)
        elif category == "doc":
            return await self.test_documentation_operations(client)
        elif category == "complete":
            return await self.test_completion_operations(client)
        elif category == "server":
            return await self.test_server_operations(client)
        elif category == "error":
            return await self.test_error_conditions(client)
        elif category == "timeout":
            return await self.test_timeout_scenarios(client)
        elif category == "large":
            return await self.test_large_responses(client)
        else:
            self._log(f"Unknown category: {category}", "ERROR")
            return False

    async def run_full_suite(
        self, client: StdioMCPClient, quick: bool = False
    ) -> bool:
        """Run the complete test suite."""
        self._log("🚀 Starting Comprehensive Async Primitives Test Suite")
        
        # First, establish nREPL connection
        self._log("📡 Connecting to nREPL server...")
        connect_result = client.call_tool("nrepl-connect", {"port": 61910})
        
        if "error" in connect_result:
            self._log(f"❌ Failed to connect to nREPL: {connect_result['error']}", "ERROR")
            return False
            
        self._log("✅ Connected to nREPL server")

        # Categories to test
        categories = ["basic", "session", "doc", "complete", "server", "error"]
        if not quick:
            categories.extend(["timeout", "large"])

        overall_success = True

        for category in categories:
            try:
                category_success = await self.run_test_category(
                    client, category
                )
                overall_success &= category_success

                if not category_success:
                    self._log(f"❌ {category} tests failed", "ERROR")
                else:
                    self._log(f"✅ {category} tests passed")

            except Exception as e:
                self._log(f"❌ {category} tests crashed: {e}", "ERROR")
                overall_success = False

        return overall_success

    def generate_report(self) -> Dict[str, Any]:
        """Generate comprehensive test report."""
        total_tests = len(self.results)
        passed_tests = sum(1 for r in self.results if r["success"])
        failed_tests = total_tests - passed_tests

        avg_duration = (
            sum(r["duration_ms"] for r in self.results) / total_tests
            if total_tests > 0
            else 0
        )

        report = {
            "summary": {
                "total_tests": total_tests,
                "passed": passed_tests,
                "failed": failed_tests,
                "success_rate": (
                    round(passed_tests / total_tests * 100, 2)
                    if total_tests > 0
                    else 0
                ),
                "average_duration_ms": round(avg_duration, 2),
            },
            "results": self.results,
        }

        return report


async def main():
    """Main test runner."""
    parser = argparse.ArgumentParser(
        description="Comprehensive Async Primitives Test Suite"
    )

    parser.add_argument(
        "--server-cmd",
        default="bb -cp src src/mcp_nrepl_proxy/core.clj",
        help="MCP server command",
    )
    parser.add_argument(
        "--quick",
        action="store_true",
        help="Run quick test suite (skip timeout/large tests)",
    )
    parser.add_argument(
        "--category",
        choices=[
            "basic",
            "session",
            "doc",
            "complete",
            "server",
            "error",
            "timeout",
            "large",
        ],
        help="Run specific test category only",
    )
    parser.add_argument(
        "--quiet", action="store_true", help="Quiet output (errors only)"
    )
    parser.add_argument("--report", help="Save detailed report to JSON file")

    args = parser.parse_args()

    # Parse server command
    import shlex

    server_command = shlex.split(args.server_cmd)

    # Initialize test suite
    suite = AsyncPrimitivesTestSuite(server_command, args.quiet)

    try:
        with StdioMCPClient(server_command, quiet=args.quiet) as client:
            if args.category:
                # Run specific category
                success = await suite.run_test_category(client, args.category)
            else:
                # Run full suite
                success = await suite.run_full_suite(client, args.quick)

            # Generate and display report
            report = suite.generate_report()

            if not args.quiet:
                print("\n" + "=" * 60)
                print("📊 TEST SUITE REPORT")
                print("=" * 60)
                print(f"Total Tests: {report['summary']['total_tests']}")
                print(f"Passed: {report['summary']['passed']}")
                print(f"Failed: {report['summary']['failed']}")
                print(f"Success Rate: {report['summary']['success_rate']}%")
                avg_dur = report['summary']['average_duration_ms']
                print(f"Average Duration: {avg_dur}ms")

                if report["summary"]["failed"] > 0:
                    print("\n❌ FAILED TESTS:")
                    for result in report["results"]:
                        if not result["success"]:
                            print(f"  - {result['test']}: {result['details']}")

            # Save report if requested
            if args.report:
                with open(args.report, "w") as f:
                    json.dump(report, f, indent=2)
                print(f"\n📄 Report saved to: {args.report}")

            # Exit with appropriate code
            sys.exit(0 if success else 1)

    except Exception as e:
        print(f"❌ Test suite crashed: {e}")
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())
