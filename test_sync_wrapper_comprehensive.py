#!/usr/bin/env python3
"""
Comprehensive test suite for nrepl-send-message sync wrapper.
Tests the same scenarios as async tests but using the new sync wrapper.

This validates that the sync wrapper properly delegates to async tools
and provides the expected timeout recovery functionality.
"""

import argparse
import json
import subprocess
import sys
import time
from pathlib import Path


class SyncWrapperTestSuite:
    """Test suite for nrepl-send-message sync wrapper functionality."""

    def __init__(self, mcp_url: str, verbose: bool = False):
        self.mcp_url = mcp_url
        self.verbose = verbose
        self.connection_id = None
        self.test_results = []

    def call_mcp_tool(
        self, tool_name: str, args: dict = None, quiet: bool = True
    ) -> dict:
        """Call MCP tool using mcp_nrepl_client.py script."""
        if args is None:
            args = {}

        cmd = [
            "uv",
            "run",
            "python",
            "scripts/mcp_nrepl_client.py",
            "--base-url",
            self.mcp_url,
            "--tool",
            tool_name,
            "--args",
            json.dumps(args),
        ]

        if quiet:
            cmd.append("--quiet")

        try:
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)

            if result.returncode != 0:
                raise Exception(f"Command failed: {result.stderr}")

            return json.loads(result.stdout)

        except subprocess.TimeoutExpired:
            raise Exception("Command timed out")
        except json.JSONDecodeError as e:
            raise Exception(
                f"Failed to parse JSON response: {e}\nOutput: {result.stdout}"
            )
        except Exception as e:
            raise Exception(f"Command execution failed: {e}")

    def setup(self):
        """Initialize and connect to nREPL."""
        # Connect to nREPL server (assuming port from environment)
        port_file = Path("scripts/test-nrepl/.test-nrepl-server-port")
        if not port_file.exists():
            raise RuntimeError(
                "No nREPL server port file found. Start nREPL server first."
            )

        port = port_file.read_text().strip()

        # Connect using nrepl-connection tool
        connect_result = self.call_mcp_tool(
            "nrepl-connection", {"op": "connect", "connection": port}
        )

        if not self._is_success(connect_result):
            raise RuntimeError(f"Failed to connect to nREPL: {connect_result}")

        self.connection_id = self._extract_data(connect_result, "connection-id")
        print(f"✅ Connected to nREPL server (ID: {self.connection_id})")

    def cleanup(self):
        """Cleanup test resources."""
        if self.connection_id:
            try:
                self.call_mcp_tool("nrepl-connection", {"op": "disconnect"})
                print("✅ Disconnected from nREPL server")
            except Exception as e:
                print(f"⚠️ Cleanup warning: {e}")

    def _is_success(self, result):
        """Check if MCP result indicates success."""
        try:
            # mcp_nrepl_client.py returns JSON directly
            return result.get("status") == "success"
        except Exception:
            return False

    def _extract_data(self, result, key):
        """Extract data field from MCP result."""
        try:
            # mcp_nrepl_client.py returns JSON directly
            return result.get(key)
        except Exception:
            return None

    def _log_test(self, test_name: str, success: bool, details: str = ""):
        """Log test result."""
        status = "✅ PASS" if success else "❌ FAIL"
        print(f"{status}: {test_name}")
        if details and (self.verbose or not success):
            print(f"    {details}")

        self.test_results.append(
            {"name": test_name, "success": success, "details": details}
        )

    def test_basic_evaluation(self):
        """Test 1: Basic synchronous evaluation."""
        test_name = "Basic Evaluation (+ 1 2 3)"

        try:
            result = self.call_mcp_tool(
                "nrepl-send-message", {"message": {"op": "eval", "code": "(+ 1 2 3)"}}
            )

            success = self._is_success(result)
            if success:
                nrepl_result = self._extract_data(result, "result")
                value = nrepl_result.get("response", {}).get("value")
                success = value == "6"
                details = f"Expected: 6, Got: {value}"
            else:
                details = f"Failed: {result}"

            self._log_test(test_name, success, details)

        except Exception as e:
            self._log_test(test_name, False, f"Exception: {e}")

    def test_input_validation(self):
        """Test 2: Input validation."""
        test_name = "Input Validation (empty message)"

        try:
            result = self.call_mcp_tool("nrepl-send-message", {})  # No message provided

            # Should fail with error
            status = result.get("status")
            error_msg = self._extract_data(result, "error")
            success = status == "error" and "No message provided" in str(error_msg)
            details = (
                f"Error: {error_msg}" if success else f"Unexpected result: {result}"
            )

            self._log_test(test_name, success, details)

        except Exception as e:
            self._log_test(test_name, False, f"Exception: {e}")

    def test_nrepl_operations(self):
        """Test 3: Various nREPL operations."""
        operations = [
            {
                "name": "Clone Session",
                "message": {"op": "clone"},
                "expected_field": "new-session",
            },
            {
                "name": "Describe Server",
                "message": {"op": "describe"},
                "expected_field": "ops",
            },
            {
                "name": "List Sessions",
                "message": {"op": "ls-sessions"},
                "expected_field": "sessions",
            },
            {
                "name": "Completions",
                "message": {"op": "completions", "prefix": "ma"},
                "expected_field": "completions",
            },
        ]

        for op in operations:
            test_name = f"nREPL Operation: {op['name']}"

            try:
                result = self.call_mcp_tool(
                    "nrepl-send-message", {"message": op["message"]}
                )

                success = self._is_success(result)
                if success:
                    nrepl_result = self._extract_data(result, "result")
                    response = nrepl_result.get("response", {})
                    has_field = op["expected_field"] in response
                    success = has_field
                    details = (
                        f"Expected field '{op['expected_field']}' present: {has_field}"
                    )
                else:
                    details = f"Operation failed: {result}"

                self._log_test(test_name, success, details)

            except Exception as e:
                self._log_test(test_name, False, f"Exception: {e}")

    def test_error_propagation(self):
        """Test 4: Error propagation from nREPL."""
        test_name = "Error Propagation (division by zero)"

        try:
            result = self.call_mcp_tool(
                "nrepl-send-message", {"message": {"op": "eval", "code": "(/ 1 0)"}}
            )

            success = self._is_success(result)
            if success:
                nrepl_result = self._extract_data(result, "result")
                response = nrepl_result.get("response", {})
                has_error = "err" in response or "ex" in response
                success = has_error
                details = f"nREPL error properly propagated: {has_error}"
            else:
                details = f"Unexpected failure: {result}"

            self._log_test(test_name, success, details)

        except Exception as e:
            self._log_test(test_name, False, f"Exception: {e}")

    def test_timeout_and_recovery(self):
        """Test 5: Timeout behavior and recovery mechanism."""
        test_name = "Timeout and Recovery"

        try:
            # First: Create a slow operation that will timeout
            print("    Creating slow operation with short timeout...")
            result = self.call_mcp_tool(
                "nrepl-send-message",
                {
                    "message": {
                        "op": "eval",
                        "code": "(do (Thread/sleep 3000) :slow-result)",
                    },
                    "timeout-ms": 1000,  # 1 second timeout for 3 second operation
                },
            )

            # Should timeout
            status = self._extract_data(result, "status")
            message_id = self._extract_data(result, "message-id")

            timeout_success = status == "timeout" and message_id is not None
            if not timeout_success:
                self._log_test(test_name, False, f"Timeout phase failed: {result}")
                return

            print(f"    Timeout successful, message-id: {message_id}")

            # Second: Recovery - check for delayed result
            print("    Attempting recovery with message-id...")
            time.sleep(3)  # Wait for operation to complete

            recovery_result = self.call_mcp_tool(
                "nrepl-send-message", {"message-id": message_id, "timeout-ms": 5000}
            )

            recovery_success = self._is_success(recovery_result)
            if recovery_success:
                nrepl_result = self._extract_data(recovery_result, "result")
                value = nrepl_result.get("response", {}).get("value")
                recovery_success = value == ":slow-result"

            success = timeout_success and recovery_success
            details = f"Timeout: {timeout_success}, Recovery: {recovery_success}"

            self._log_test(test_name, success, details)

        except Exception as e:
            self._log_test(test_name, False, f"Exception: {e}")

    def test_connection_routing(self):
        """Test 6: Connection parameter (with single connection)."""
        test_name = "Connection Routing"

        try:
            result = self.call_mcp_tool(
                "nrepl-send-message",
                {
                    "message": {"op": "eval", "code": "(str *ns*)"},
                    "connection": self.connection_id,
                },
            )

            success = self._is_success(result)
            if success:
                nrepl_result = self._extract_data(result, "result")
                value = nrepl_result.get("response", {}).get("value")
                success = "user" in str(value)  # Should contain "user" namespace
                details = f"Namespace result: {value}"
            else:
                details = f"Connection routing failed: {result}"

            self._log_test(test_name, success, details)

        except Exception as e:
            self._log_test(test_name, False, f"Exception: {e}")

    def test_large_response(self):
        """Test 7: Large response handling."""
        test_name = "Large Response Handling"

        try:
            result = self.call_mcp_tool(
                "nrepl-send-message", {"message": {"op": "eval", "code": "(range 100)"}}
            )

            success = self._is_success(result)
            if success:
                nrepl_result = self._extract_data(result, "result")
                value = nrepl_result.get("response", {}).get("value")
                # Should contain a range representation
                success = "0 1 2" in str(value) and "99" in str(value)
                details = f"Large response properly handled: {len(str(value))} chars"
            else:
                details = f"Large response failed: {result}"

            self._log_test(test_name, success, details)

        except Exception as e:
            self._log_test(test_name, False, f"Exception: {e}")

    def test_session_isolation(self):
        """Test 8: Session variable sharing (server-specific behavior)."""
        test_name = "Session Variable Sharing"

        try:
            # Define variable in default session
            result1 = self.call_mcp_tool(
                "nrepl-send-message",
                {"message": {"op": "eval", "code": "(def test-var 42)"}},
            )

            if not self._is_success(result1):
                self._log_test(
                    test_name, False, f"Variable definition failed: {result1}"
                )
                return

            # Create new session
            clone_result = self.call_mcp_tool(
                "nrepl-send-message", {"message": {"op": "clone"}}
            )

            if not self._is_success(clone_result):
                self._log_test(
                    test_name, False, f"Session clone failed: {clone_result}"
                )
                return

            new_session = self._extract_data(clone_result, "result")["response"][
                "new-session"
            ]

            # Try to access variable in new session - should succeed (this nREPL server shares state)
            result2 = self.call_mcp_tool(
                "nrepl-send-message",
                {"message": {"op": "eval", "code": "test-var", "session": new_session}},
            )

            success = self._is_success(result2)
            if success:
                nrepl_result = self._extract_data(result2, "result")
                response = nrepl_result.get("response", {})
                # Should succeed and return the variable value
                value = response.get("value")
                success = value == "42"
                details = f"Variable accessible across sessions (this server behavior): {success}, value: {value}"
            else:
                details = f"Session test failed: {result2}"

            self._log_test(test_name, success, details)

        except Exception as e:
            self._log_test(test_name, False, f"Exception: {e}")

    def run_all_tests(self):
        """Run all test scenarios."""
        print(f"🧪 Starting sync wrapper comprehensive test suite...")
        print(f"📡 MCP URL: {self.mcp_url}")
        print()

        self.setup()

        try:
            # Run all tests
            self.test_basic_evaluation()
            self.test_input_validation()
            self.test_nrepl_operations()
            self.test_error_propagation()
            self.test_timeout_and_recovery()
            self.test_connection_routing()
            self.test_large_response()
            self.test_session_isolation()

        finally:
            self.cleanup()

        # Print summary
        total = len(self.test_results)
        passed = sum(1 for r in self.test_results if r["success"])
        failed = total - passed

        print()
        print("=" * 60)
        print(f"📊 TEST SUMMARY")
        print(f"   Total Tests: {total}")
        print(f"   ✅ Passed: {passed}")
        print(f"   ❌ Failed: {failed}")
        print(f"   📈 Success Rate: {(passed/total)*100:.1f}%")

        if failed > 0:
            print()
            print("❌ FAILED TESTS:")
            for result in self.test_results:
                if not result["success"]:
                    print(f"   - {result['name']}: {result['details']}")

        print("=" * 60)

        return failed == 0


def main():
    parser = argparse.ArgumentParser(
        description="Comprehensive sync wrapper test suite"
    )
    parser.add_argument(
        "--mcp-url",
        default="http://localhost:3000/mcp",
        help="MCP server URL (default: http://localhost:3000/mcp)",
    )
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")

    args = parser.parse_args()

    try:
        suite = SyncWrapperTestSuite(args.mcp_url, args.verbose)
        success = suite.run_all_tests()
        sys.exit(0 if success else 1)

    except Exception as e:
        print(f"❌ Test suite failed: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
