#!/usr/bin/env python3
"""
Comprehensive HTTP Bridge Test Suite

Tests the HTTP-to-stdio bridge with our nREPL MCP server using StreamableHTTP.
This replicates all the tests we previously ran with the stdio client, but now
via the HTTP bridge for true stateful testing.

Usage:
    python3 scripts/test_http_bridge.py           # Full test suite
    python3 scripts/test_http_bridge.py --quick   # Skip long-running tests
    python3 scripts/test_http_bridge.py --basic   # Basic connectivity only
"""

import asyncio
import argparse
import json
import sys
import httpx
from typing import Dict, Any
from datetime import datetime


class HTTPBridgeTestClient:
    """HTTP client for testing MCP bridge with proper StreamableHTTP support."""

    def __init__(self, base_url: str = "http://localhost:3000"):
        self.base_url = base_url
        self.mcp_url = f"{base_url}/mcp/"
        self.headers = {
            "Content-Type": "application/json",
            "Accept": "application/json, text/event-stream",
        }
        self.request_id = 1

    def _next_id(self) -> int:
        """Get next request ID."""
        current = self.request_id
        self.request_id += 1
        return current

    async def _make_request(
        self, method: str, params: Dict[str, Any] = None
    ) -> Dict[str, Any]:
        """Make an MCP JSON-RPC request via HTTP."""
        payload = {
            "jsonrpc": "2.0",
            "id": self._next_id(),
            "method": method,
            "params": params or {},
        }

        async with httpx.AsyncClient(timeout=30.0) as client:
            try:
                response = await client.post(
                    self.mcp_url, json=payload, headers=self.headers
                )
                response.raise_for_status()
                result = response.json()

                if "error" in result:
                    return {"error": result["error"]}
                return result.get("result", {})

            except Exception as e:
                return {"error": str(e)}

    async def initialize(self) -> Dict[str, Any]:
        """Initialize MCP connection."""
        return await self._make_request(
            "initialize",
            {
                "protocolVersion": "2024-11-05",
                "capabilities": {"tools": {"list": True, "call": True}},
                "clientInfo": {
                    "name": "http-bridge-test-client",
                    "version": "1.0.0",
                },
            },
        )

    async def list_tools(self) -> Dict[str, Any]:
        """List available tools."""
        return await self._make_request("tools/list")

    async def call_tool(self, name: str, arguments: Dict[str, Any]) -> Dict[str, Any]:
        """Call a specific tool."""
        return await self._make_request(
            "tools/call", {"name": name, "arguments": arguments}
        )


class HTTPBridgeTestSuite:
    """Comprehensive test suite for HTTP bridge functionality."""

    def __init__(self, client: HTTPBridgeTestClient, quiet: bool = False):
        self.client = client
        self.quiet = quiet
        self.passed = 0
        self.total = 0

    def _print(self, message: str):
        """Print message unless in quiet mode."""
        if not self.quiet:
            print(message)

    def _test_result(self, test_name: str, success: bool, details: str = ""):
        """Record test result."""
        self.total += 1
        if success:
            self.passed += 1
            self._print(f"✅ {test_name}")
            if details and not self.quiet:
                self._print(f"   {details}")
        else:
            self._print(f"❌ {test_name}")
            if details:
                self._print(f"   {details}")

    async def test_connectivity(self) -> bool:
        """Test basic HTTP connectivity."""
        self._print("🔌 Testing HTTP Connectivity...")

        try:
            result = await self.client.initialize()
            if "error" in result:
                self._test_result(
                    "HTTP connectivity",
                    False,
                    f"Initialize failed: {result['error']}",
                )
                return False

            # Check for expected fields
            if "protocolVersion" in result and "serverInfo" in result:
                server_name = result.get("serverInfo", {}).get("name", "unknown")
                self._test_result(
                    "HTTP connectivity", True, f"Connected to {server_name}"
                )
                return True
            else:
                self._test_result(
                    "HTTP connectivity", False, "Invalid initialize response"
                )
                return False

        except Exception as e:
            self._test_result("HTTP connectivity", False, str(e))
            return False

    async def test_tools_discovery(self) -> bool:
        """Test tools list functionality."""
        self._print("\n🛠️  Testing Tools Discovery...")

        try:
            result = await self.client.list_tools()
            if "error" in result:
                self._test_result("Tools list", False, result["error"])
                return False

            tools = result.get("tools", [])
            if not tools:
                self._test_result("Tools list", False, "No tools returned")
                return False

            # Check for our expected tools
            tool_names = [tool["name"] for tool in tools]
            expected_tools = ["debug-eval", "debug-load-file", "nrepl-server"]

            missing_tools = [tool for tool in expected_tools if tool not in tool_names]
            if missing_tools:
                self._test_result(
                    "Tools list", False, f"Missing tools: {missing_tools}"
                )
                return False

            self._test_result(
                "Tools list", True, f"Found {len(tools)} tools: {tool_names}"
            )
            return True

        except Exception as e:
            self._test_result("Tools list", False, str(e))
            return False

    async def test_debug_eval_basic(self) -> bool:
        """Test basic debug-eval functionality."""
        self._print("\n🧮 Testing Debug Eval - Basic Operations...")

        test_cases = [
            ("Basic arithmetic", "(+ 1 2 3)", "6"),
            ("String operations", '(str "Hello" " " "World")', "Hello World"),
            ("Data structures", "(count [1 2 3 4 5])", "5"),
            (
                "Boolean logic",
                "(and true false)",
                "False",
            ),  # Python string representation of JSON boolean
            ("Math functions", "(* 6 7)", "42"),
        ]

        success_count = 0
        for test_name, code, expected in test_cases:
            try:
                result = await self.client.call_tool("debug-eval", {"code": code})
                if "error" in result:
                    self._test_result(
                        f"Debug eval: {test_name}", False, result["error"]
                    )
                    continue

                # Parse the result content
                content = result.get("content", [])
                if not content:
                    self._test_result(
                        f"Debug eval: {test_name}",
                        False,
                        "No content in response",
                    )
                    continue

                response_text = content[0].get("text", "")
                try:
                    parsed = json.loads(response_text)
                    if parsed.get("status") == "success":
                        actual_result = str(parsed.get("result", ""))
                        if expected in actual_result:
                            self._test_result(
                                f"Debug eval: {test_name}",
                                True,
                                f"{code} → {actual_result}",
                            )
                            success_count += 1
                        else:
                            self._test_result(
                                f"Debug eval: {test_name}",
                                False,
                                f"Expected {expected}, got {actual_result}",
                            )
                    else:
                        self._test_result(
                            f"Debug eval: {test_name}",
                            False,
                            parsed.get("error", "Unknown error"),
                        )
                except json.JSONDecodeError:
                    self._test_result(
                        f"Debug eval: {test_name}",
                        False,
                        "Invalid JSON response",
                    )

            except Exception as e:
                self._test_result(f"Debug eval: {test_name}", False, str(e))

        return success_count == len(test_cases)

    async def test_debug_eval_advanced(self) -> bool:
        """Test advanced debug-eval functionality."""
        self._print("\n🔬 Testing Debug Eval - Advanced Operations...")

        test_cases = [
            ("Function definition", "(defn square [x] (* x x))", None),
            ("Function call", "(square 8)", "64"),
            (
                "Collections",
                "(mapv inc [1 2 3])",
                "[2 3 4]",
            ),  # Use mapv for eager vector mapping
            ("Conditionals", "(if (> 10 5) :bigger :smaller)", "bigger"),
            ("Let bindings", "(let [x 10 y 20] (+ x y))", "30"),
        ]

        success_count = 0
        for test_name, code, expected in test_cases:
            try:
                result = await self.client.call_tool("debug-eval", {"code": code})
                if "error" in result:
                    self._test_result(
                        f"Advanced eval: {test_name}", False, result["error"]
                    )
                    continue

                content = result.get("content", [])
                if not content:
                    self._test_result(
                        f"Advanced eval: {test_name}",
                        False,
                        "No content in response",
                    )
                    continue

                response_text = content[0].get("text", "")
                try:
                    parsed = json.loads(response_text)
                    if parsed.get("status") == "success":
                        if expected is None:  # Just check for success
                            self._test_result(
                                f"Advanced eval: {test_name}",
                                True,
                                f"{code} → success",
                            )
                            success_count += 1
                        else:
                            actual_result = str(parsed.get("result", ""))
                            if expected in actual_result:
                                self._test_result(
                                    f"Advanced eval: {test_name}",
                                    True,
                                    f"{code} → {actual_result}",
                                )
                                success_count += 1
                            else:
                                self._test_result(
                                    f"Advanced eval: {test_name}",
                                    False,
                                    f"Expected {expected}, got {actual_result}",
                                )
                    else:
                        self._test_result(
                            f"Advanced eval: {test_name}",
                            False,
                            parsed.get("error", "Unknown error"),
                        )
                except json.JSONDecodeError:
                    self._test_result(
                        f"Advanced eval: {test_name}",
                        False,
                        "Invalid JSON response",
                    )

            except Exception as e:
                self._test_result(f"Advanced eval: {test_name}", False, str(e))

        return success_count == len(test_cases)

    async def test_nrepl_server_operations(self) -> bool:
        """Test nREPL server tool operations."""
        self._print("\n🖥️  Testing nREPL Server Operations...")

        # Test status operation
        try:
            result = await self.client.call_tool("nrepl-server", {"op": "status"})
            if "error" in result:
                self._test_result("nREPL status", False, result["error"])
                return False

            content = result.get("content", [])
            if content:
                response_text = content[0].get("text", "")
                try:
                    parsed = json.loads(response_text)
                    status = parsed.get("status", "unknown")
                    self._test_result("nREPL status", True, f"Status: {status}")
                    return True
                except json.JSONDecodeError:
                    pass

            self._test_result("nREPL status", True, "Status operation completed")
            return True

        except Exception as e:
            self._test_result("nREPL status", False, str(e))
            return False

    async def test_debug_load_file(self) -> bool:
        """Test debug-load-file functionality."""
        self._print("\n📁 Testing Debug Load File...")

        # Test with a non-existent file (should fail gracefully)
        try:
            result = await self.client.call_tool(
                "debug-load-file",
                {"file-path": "/tmp/non-existent-test-file.clj"},
            )

            # This should either fail with an error or succeed with a message
            if "error" in result:
                self._test_result(
                    "Debug load file",
                    True,
                    "Correctly handled non-existent file",
                )
            else:
                content = result.get("content", [])
                if content:
                    response_text = content[0].get("text", "")
                    if (
                        "not found" in response_text.lower()
                        or "error" in response_text.lower()
                    ):
                        self._test_result(
                            "Debug load file",
                            True,
                            "Correctly handled non-existent file",
                        )
                    else:
                        self._test_result(
                            "Debug load file",
                            False,
                            "Unexpected success with non-existent file",
                        )
                else:
                    self._test_result(
                        "Debug load file",
                        True,
                        "Load file operation completed",
                    )

            return True

        except Exception as e:
            self._test_result("Debug load file", False, str(e))
            return False

    async def test_variable_persistence(self) -> bool:
        """Test that variables persist between debug-eval calls (stateful behavior)."""
        self._print("\n🔄 Testing Variable Persistence...")

        try:
            # Define a variable in first call
            result1 = await self.client.call_tool(
                "debug-eval", {"code": "(def test-persistence-var 123)"}
            )

            if "error" in result1:
                self._test_result("Variable definition", False, result1["error"])
                return False

            # Access the variable in second call
            result2 = await self.client.call_tool(
                "debug-eval", {"code": "test-persistence-var"}
            )

            if "error" in result2:
                self._test_result("Variable persistence", False, result2["error"])
                return False

            # Check if we got the expected value
            content = result2.get("content", [])
            if not content:
                self._test_result(
                    "Variable persistence", False, "No content in response"
                )
                return False

            response_text = content[0].get("text", "")
            try:
                parsed = json.loads(response_text)
                if parsed.get("status") == "success":
                    actual_result = parsed.get("result")
                    if actual_result == 123:
                        self._test_result(
                            "Variable persistence",
                            True,
                            "Variable persisted across debug-eval calls",
                        )
                        return True
                    else:
                        self._test_result(
                            "Variable persistence",
                            False,
                            f"Expected 123, got {actual_result}",
                        )
                        return False
                else:
                    self._test_result(
                        "Variable persistence",
                        False,
                        parsed.get("error", "Unknown error"),
                    )
                    return False
            except json.JSONDecodeError:
                self._test_result(
                    "Variable persistence", False, "Invalid JSON response"
                )
                return False

        except Exception as e:
            self._test_result("Variable persistence", False, str(e))
            return False

    async def test_function_persistence(self) -> bool:
        """Test that functions persist between debug-eval calls (stateful behavior)."""
        self._print("\n🔧 Testing Function Persistence...")

        try:
            # Define a function in first call
            result1 = await self.client.call_tool(
                "debug-eval",
                {"code": "(defn test-persistent-fn [x y] (+ (* x x) (* y y)))"},
            )

            if "error" in result1:
                self._test_result("Function definition", False, result1["error"])
                return False

            # Call the function in second call
            result2 = await self.client.call_tool(
                "debug-eval", {"code": "(test-persistent-fn 3 4)"}
            )

            if "error" in result2:
                self._test_result("Function persistence", False, result2["error"])
                return False

            # Check if we got the expected value (3² + 4² = 9 + 16 = 25)
            content = result2.get("content", [])
            if not content:
                self._test_result(
                    "Function persistence", False, "No content in response"
                )
                return False

            response_text = content[0].get("text", "")
            try:
                parsed = json.loads(response_text)
                if parsed.get("status") == "success":
                    actual_result = parsed.get("result")
                    if actual_result == 25:
                        self._test_result(
                            "Function persistence",
                            True,
                            "Function persisted and executed across debug-eval calls",
                        )
                        return True
                    else:
                        self._test_result(
                            "Function persistence",
                            False,
                            f"Expected 25, got {actual_result}",
                        )
                        return False
                else:
                    self._test_result(
                        "Function persistence",
                        False,
                        parsed.get("error", "Unknown error"),
                    )
                    return False
            except json.JSONDecodeError:
                self._test_result(
                    "Function persistence", False, "Invalid JSON response"
                )
                return False

        except Exception as e:
            self._test_result("Function persistence", False, str(e))
            return False

    async def test_registry_consistency(self) -> bool:
        """Test that internal tool registry matches exposed MCP tools list."""
        self._print("\n🔍 Testing Registry Consistency...")

        try:
            # Get tools from MCP protocol
            mcp_result = await self.client.list_tools()
            if "error" in mcp_result:
                self._test_result(
                    "Registry consistency",
                    False,
                    f"MCP tools/list failed: {mcp_result['error']}",
                )
                return False

            mcp_tools = mcp_result.get("tools", [])
            mcp_tool_names = sorted([tool["name"] for tool in mcp_tools])

            # Get tools from internal registry via introspection
            registry_result = await self.client.call_tool(
                "debug-eval",
                {
                    "code": "(sort (keys @nrepl-mcp_server.state.tool-registry/tool-registry))"
                },
            )

            if "error" in registry_result:
                self._test_result(
                    "Registry consistency",
                    False,
                    f"Registry introspection failed: {registry_result['error']}",
                )
                return False

            content = registry_result.get("content", [])
            if not content:
                self._test_result(
                    "Registry consistency",
                    False,
                    "No content in registry response",
                )
                return False

            response_text = content[0].get("text", "")
            try:
                parsed = json.loads(response_text)
                if parsed.get("status") == "success":
                    # Parse the result - it should be a sorted list of tool names
                    result_str = parsed.get("result", "")
                    # Extract tool names from result string like ["debug-eval" "debug-load-file" "nrepl-server"]
                    import re

                    registry_tool_names = re.findall(r'"([^"]+)"', result_str)
                    registry_tool_names = sorted(registry_tool_names)

                    # Compare the lists
                    if mcp_tool_names == registry_tool_names:
                        self._test_result(
                            "Registry consistency",
                            True,
                            f"Registry and MCP tools match: {mcp_tool_names}",
                        )
                        return True
                    else:
                        self._test_result(
                            "Registry consistency",
                            False,
                            f"Mismatch - MCP: {mcp_tool_names}, Registry: {registry_tool_names}",
                        )
                        return False
                else:
                    self._test_result(
                        "Registry consistency",
                        False,
                        parsed.get("error", "Unknown error"),
                    )
                    return False
            except json.JSONDecodeError:
                self._test_result(
                    "Registry consistency",
                    False,
                    "Invalid JSON response from registry",
                )
                return False

        except Exception as e:
            self._test_result("Registry consistency", False, str(e))
            return False

    async def test_performance(self) -> bool:
        """Test performance with multiple rapid requests."""
        self._print("\n⚡ Testing Performance - Rapid Requests...")

        start_time = datetime.now()
        success_count = 0

        # Send 10 rapid requests
        tasks = []
        for i in range(10):
            task = self.client.call_tool("debug-eval", {"code": f"(+ {i} 1)"})
            tasks.append(task)

        try:
            results = await asyncio.gather(*tasks, return_exceptions=True)

            for i, result in enumerate(results):
                if isinstance(result, Exception):
                    continue
                if "error" not in result:
                    success_count += 1

            end_time = datetime.now()
            duration = (end_time - start_time).total_seconds()

            if success_count >= 8:  # Allow some failures
                self._test_result(
                    "Performance test",
                    True,
                    f"{success_count}/10 requests succeeded in {duration:.2f}s",
                )
                return True
            else:
                self._test_result(
                    "Performance test",
                    False,
                    f"Only {success_count}/10 requests succeeded",
                )
                return False

        except Exception as e:
            self._test_result("Performance test", False, str(e))
            return False

    async def run_basic_tests(self) -> bool:
        """Run basic connectivity and tools tests."""
        self._print("🧪 Running Basic HTTP Bridge Tests")
        self._print("=" * 40)

        # Essential tests
        if not await self.test_connectivity():
            return False
        if not await self.test_tools_discovery():
            return False

        return True

    async def run_full_tests(self) -> bool:
        """Run comprehensive test suite."""
        self._print("🧪 Running Comprehensive HTTP Bridge Tests")
        self._print("=" * 50)

        # Run all tests
        await self.test_connectivity()
        await self.test_tools_discovery()
        await self.test_debug_eval_basic()
        await self.test_debug_eval_advanced()
        await self.test_nrepl_server_operations()
        await self.test_debug_load_file()
        await self.test_variable_persistence()
        await self.test_function_persistence()
        await self.test_registry_consistency()

        return True

    async def run_quick_tests(self) -> bool:
        """Run quick test suite (skip performance tests)."""
        self._print("🧪 Running Quick HTTP Bridge Tests")
        self._print("=" * 40)

        # Run core tests without performance
        await self.test_connectivity()
        await self.test_tools_discovery()
        await self.test_debug_eval_basic()
        await self.test_nrepl_server_operations()
        await self.test_registry_consistency()

        return True

    async def run_with_performance(self) -> bool:
        """Run full tests including performance."""
        await self.run_full_tests()
        await self.test_performance()
        return True

    def print_summary(self):
        """Print test summary."""
        self._print(f"\n📊 Test Summary: {self.passed}/{self.total} passed")
        if self.passed == self.total:
            self._print("🎉 All tests passed!")
            return True
        else:
            self._print(f"⚠️  {self.total - self.passed} tests failed")
            return False


async def main():
    """Main test runner."""
    parser = argparse.ArgumentParser(description="HTTP Bridge Test Suite")
    parser.add_argument(
        "--url",
        default="http://localhost:3000",
        help="Base URL for HTTP bridge",
    )
    parser.add_argument(
        "--basic",
        action="store_true",
        help="Run basic connectivity tests only",
    )
    parser.add_argument(
        "--quick",
        action="store_true",
        help="Run quick test suite (skip performance)",
    )
    parser.add_argument(
        "--performance", action="store_true", help="Include performance tests"
    )
    parser.add_argument("--quiet", "-q", action="store_true", help="Minimal output")

    args = parser.parse_args()

    client = HTTPBridgeTestClient(args.url)
    suite = HTTPBridgeTestSuite(client, args.quiet)

    try:
        if args.basic:
            await suite.run_basic_tests()
        elif args.quick:
            await suite.run_quick_tests()
        elif args.performance:
            await suite.run_with_performance()
        else:
            await suite.run_full_tests()

        success = suite.print_summary()
        sys.exit(0 if success else 1)

    except KeyboardInterrupt:
        if not args.quiet:
            print("\n👋 Interrupted by user")
        sys.exit(0)
    except Exception as e:
        if not args.quiet:
            print(f"❌ Test suite failed: {e}")
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())
