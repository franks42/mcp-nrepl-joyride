#!/usr/bin/env python3
"""
Comprehensive Multi-Connection Test Suite for nREPL MCP Server

TESTS COVERED:
- Single connection scenarios (backward compatibility)
- Multi-connection setup with external Clojure and local Babashka servers
- Connection nickname routing
- Async tool functionality (nrepl-send-message-async, nrepl-get-result-async)
- Connection lifecycle management (connect, disconnect, list)
- Error handling and edge cases
- Connection parameter validation
- Cross-server evaluation and isolation

PRECONDITIONS:
1. External nREPL test server management: ./scripts/nrepl_test_server.py
2. HTTP bridge for stateful testing: ./scripts/start-http-bridge.sh
3. MCP client for testing: scripts/mcp_nrepl_client.py
4. Local babashka nREPL server via local-nrepl-server MCP tool

TESTING APPROACH:
- Only async tools: nrepl-send-message-async, nrepl-get-result-async, nrepl-connection
- NO sync tools (nrepl-eval, nrepl-send-message) due to implementation issues
- Stateful HTTP testing to preserve connection state across test steps
- Nickname-based connection routing for user-friendly multi-connection management

DEPENDENCIES:
- External test server on dynamic port (started via scripts/nrepl_test_server.py)
- Local babashka server on auto-assigned port (via local-nrepl-server tool)
- HTTP bridge running on port 3000 for stateful operations
- Both servers must be available simultaneously for multi-connection testing
"""

import json
import subprocess
import sys
import time
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Tuple

import requests

# Constants
HTTP_BRIDGE_URL = "http://localhost:3000/mcp/"
HEADERS = {
    "Content-Type": "application/json",
    "Accept": "application/json, text/event-stream",
}
DEFAULT_TIMEOUT = 30


@dataclass
class TestResult:
    name: str
    success: bool
    message: str
    duration: float
    details: Optional[Dict[str, Any]] = None


class MultiConnectionTester:
    """Comprehensive multi-connection test suite for nREPL MCP bridge."""

    def __init__(self):
        self.test_results: List[TestResult] = []
        self.external_server_port: Optional[int] = None
        self.local_server_port: Optional[int] = None
        self.start_time = time.time()

    def call_mcp_tool(
        self, tool_name: str, args: Dict[str, Any], timeout: int = DEFAULT_TIMEOUT
    ) -> Dict[str, Any]:
        """Call MCP tool via HTTP bridge with error handling."""
        payload = {
            "jsonrpc": "2.0",
            "id": int(time.time() * 1000),
            "method": "tools/call",
            "params": {"name": tool_name, "arguments": args},
        }

        try:
            response = requests.post(
                HTTP_BRIDGE_URL, json=payload, headers=HEADERS, timeout=timeout
            )
            response.raise_for_status()
            data = response.json()

            if "error" in data:
                raise Exception(f"MCP Error: {data['error']}")

            result = data.get("result", {})
            
            # Handle new MCP response format with content arrays
            if isinstance(result, dict) and "content" in result:
                content = result.get("content", [])
                if content and isinstance(content, list) and len(content) > 0:
                    text_content = content[0].get("text", "")
                    if text_content:
                        try:
                            # Try to parse JSON from the text content
                            return json.loads(text_content)
                        except json.JSONDecodeError:
                            # If not JSON, return the text as-is
                            return {"text": text_content}
                return result
            
            return result
        except requests.exceptions.RequestException as e:
            raise Exception(f"HTTP Request failed: {e}")
        except json.JSONDecodeError as e:
            raise Exception(f"JSON decode failed: {e}")

    def run_test(self, test_name: str, test_func) -> TestResult:
        """Run individual test with timing and error handling."""
        start_time = time.time()
        try:
            print(f"\n🧪 Running: {test_name}")
            result = test_func()
            duration = time.time() - start_time

            if isinstance(result, tuple):
                success, message, details = (
                    result[0],
                    result[1],
                    result[2] if len(result) > 2 else None,
                )
            else:
                success, message, details = result, "Test completed", None

            test_result = TestResult(test_name, success, message, duration, details)

            if success:
                print(f"✅ PASS: {test_name} ({duration:.2f}s)")
                if details:
                    print(f"   Details: {details}")
            else:
                print(f"❌ FAIL: {test_name} ({duration:.2f}s)")
                print(f"   Error: {message}")

            return test_result

        except Exception as e:
            duration = time.time() - start_time
            test_result = TestResult(test_name, False, str(e), duration)
            print(f"❌ ERROR: {test_name} ({duration:.2f}s)")
            print(f"   Exception: {e}")
            return test_result

    def test_prerequisites(self) -> Tuple[bool, str, Dict[str, Any]]:
        """Test that all prerequisites are available."""
        details = {}

        # Check HTTP bridge
        try:
            requests.get(f"{HTTP_BRIDGE_URL.rstrip('/')}/", headers=HEADERS, timeout=5)
            details["http_bridge"] = "✅ Available"
        except Exception:
            return False, "HTTP bridge not available on port 3000", details

        # Check external nREPL server status
        try:
            result = subprocess.run(
                ["python3", "scripts/nrepl_test_server.py", "status"],
                capture_output=True,
                text=True,
                timeout=10,
            )
            if result.returncode == 0:
                # Get port from JSON file for reliability
                port = self.get_external_nrepl_port()
                if port:
                    self.external_server_port = port
                    details["external_server"] = (
                        f"✅ Running on port {self.external_server_port}"
                    )
                else:
                    details["external_server"] = "❌ Port not found in server info"
            else:
                return (
                    False,
                    f"External nREPL server not running: {result.stderr}",
                    details,
                )
        except Exception as e:
            return False, f"Failed to check external server: {e}", details

        # Check if we can list MCP tools
        try:
            tools_response = self.call_mcp_tool("tools/list", {})
            tool_names = [
                tool.get("name", "") for tool in tools_response.get("tools", [])
            ]
            required_tools = [
                "nrepl-connection",
                "nrepl-send-message-async",
                "nrepl-get-result-async",
                "local-nrepl-server",
            ]
            missing_tools = [tool for tool in required_tools if tool not in tool_names]

            if missing_tools:
                return False, f"Missing required tools: {missing_tools}", details

            details["mcp_tools"] = f"✅ All required tools available: {required_tools}"
        except Exception as e:
            return False, f"Failed to list MCP tools: {e}", details

        return True, "All prerequisites available", details

    def test_start_local_nrepl_server(self) -> Tuple[bool, str, Dict[str, Any]]:
        """Start local babashka nREPL server for multi-connection testing."""
        try:
            # Start local nREPL server with auto-assigned port
            result = self.call_mcp_tool(
                "local-nrepl-server", {"op": "start", "port": 0}
            )

            if result.get("status") != "success":
                return False, f"Failed to start local server: {result}", {}

            self.local_server_port = result.get("port")
            connection_info = result.get("connection", "")

            details = {
                "port": self.local_server_port,
                "connection": connection_info,
                "server_status": result.get("server-status", ""),
                "message": result.get("message", ""),
            }

            return (
                True,
                f"Local server started on port {self.local_server_port}",
                details,
            )

        except Exception as e:
            return False, f"Exception starting local server: {e}", {}

    def test_single_connection_external(self) -> Tuple[bool, str, Dict[str, Any]]:
        """Test single connection to external nREPL server."""
        try:
            # Connect to external server
            connect_result = self.call_mcp_tool(
                "nrepl-connection",
                {
                    "op": "connect",
                    "connection": str(self.external_server_port),
                    "nickname": "external-server",
                },
            )

            if connect_result.get("status") != "success":
                return (
                    False,
                    f"Failed to connect to external server: {connect_result}",
                    {},
                )

            # Send async message
            message = {"op": "eval", "code": '(str "External: " (+ 10 20))'}
            send_result = self.call_mcp_tool(
                "nrepl-send-message-async",
                {"message": message, "connection": "external-server"},
            )

            if send_result.get("status") != "success":
                return False, f"Failed to send message: {send_result}", {}

            message_id = send_result.get("message-id")
            if not message_id:
                return False, f"No message-id returned: {send_result}", {}

            # Wait for result
            time.sleep(2)  # Allow processing time

            get_result = self.call_mcp_tool(
                "nrepl-get-result-async", {"message-id": message_id}
            )

            if get_result.get("status") != "success":
                return False, f"Failed to get result: {get_result}", {}

            response = get_result.get("response", {})
            value = response.get("value", "")

            expected = "External: 30"
            if value != expected:
                return (
                    False,
                    f"Unexpected result. Expected: {expected}, Got: {value}",
                    {},
                )

            details = {
                "connection": connect_result.get("connection", ""),
                "message_id": message_id,
                "evaluation_result": value,
                "response_ns": response.get("ns", ""),
            }

            return True, f"Single external connection working: {value}", details

        except Exception as e:
            return False, f"Exception in single external connection test: {e}", {}

    def test_single_connection_local(self) -> Tuple[bool, str, Dict[str, Any]]:
        """Test single connection to local babashka server."""
        try:
            # Disconnect from external first (single connection mode)
            self.call_mcp_tool("nrepl-connection", {"op": "disconnect"})
            time.sleep(1)

            # Connect to local server
            connect_result = self.call_mcp_tool(
                "nrepl-connection",
                {
                    "op": "connect",
                    "connection": str(self.local_server_port),
                    "nickname": "local-bb",
                },
            )

            if connect_result.get("status") != "success":
                return False, f"Failed to connect to local server: {connect_result}", {}

            # Send async message
            message = {"op": "eval", "code": '(str "Local: " (+ 30 40))'}
            send_result = self.call_mcp_tool(
                "nrepl-send-message-async",
                {"message": message, "connection": "local-bb"},
            )

            if send_result.get("status") != "success":
                return False, f"Failed to send message: {send_result}", {}

            message_id = send_result.get("message-id")
            if not message_id:
                return False, f"No message-id returned: {send_result}", {}

            # Wait for result
            time.sleep(2)

            get_result = self.call_mcp_tool(
                "nrepl-get-result-async", {"message-id": message_id}
            )

            if get_result.get("status") != "success":
                return False, f"Failed to get result: {get_result}", {}

            response = get_result.get("response", {})
            value = response.get("value", "")

            expected = "Local: 70"
            if value != expected:
                return (
                    False,
                    f"Unexpected result. Expected: {expected}, Got: {value}",
                    {},
                )

            details = {
                "connection": connect_result.get("connection", ""),
                "message_id": message_id,
                "evaluation_result": value,
                "response_ns": response.get("ns", ""),
            }

            return True, f"Single local connection working: {value}", details

        except Exception as e:
            return False, f"Exception in single local connection test: {e}", {}

    def test_multi_connection_setup(self) -> Tuple[bool, str, Dict[str, Any]]:
        """Test simultaneous connections to both servers."""
        try:
            # Disconnect all first
            self.call_mcp_tool("nrepl-connection", {"op": "disconnect"})
            time.sleep(1)

            # Connect to external server
            external_result = self.call_mcp_tool(
                "nrepl-connection",
                {
                    "op": "connect",
                    "connection": str(self.external_server_port),
                    "nickname": "external-server",
                },
            )

            if external_result.get("status") != "success":
                return (
                    False,
                    f"Failed to connect to external server: {external_result}",
                    {},
                )

            # Connect to local server (multi-connection should work)
            local_result = self.call_mcp_tool(
                "nrepl-connection",
                {
                    "op": "connect",
                    "connection": str(self.local_server_port),
                    "nickname": "local-bb",
                },
            )

            if local_result.get("status") != "success":
                return False, f"Failed to connect to local server: {local_result}", {}

            # List connections to verify both
            list_result = self.call_mcp_tool("nrepl-connection", {"op": "list"})

            if list_result.get("status") != "success":
                return False, f"Failed to list connections: {list_result}", {}

            connections = list_result.get("connections", [])
            if len(connections) != 2:
                return (
                    False,
                    f"Expected 2 connections, got {len(connections)}: {connections}",
                    {},
                )

            # Verify nicknames
            nicknames = [conn.get("nickname", "") for conn in connections]
            expected_nicknames = {"external-server", "local-bb"}
            actual_nicknames = set(nicknames)

            if actual_nicknames != expected_nicknames:
                return (
                    False,
                    f"Expected nicknames {expected_nicknames}, got {actual_nicknames}",
                    {},
                )

            details = {
                "external_connection": external_result.get("connection", ""),
                "local_connection": local_result.get("connection", ""),
                "total_connections": len(connections),
                "nicknames": nicknames,
            }

            return True, "Multi-connection setup successful", details

        except Exception as e:
            return False, f"Exception in multi-connection setup: {e}", {}

    def test_multi_connection_routing(self) -> Tuple[bool, str, Dict[str, Any]]:
        """Test nickname-based routing with multiple connections."""
        try:

            # Test external server via nickname
            external_message = {
                "op": "eval",
                "code": '(str "MULTI-External: " (+ 100 200))',
            }
            external_send = self.call_mcp_tool(
                "nrepl-send-message-async",
                {"message": external_message, "connection": "external-server"},
            )

            if external_send.get("status") != "success":
                return False, f"Failed to send to external server: {external_send}", {}

            external_message_id = external_send.get("message-id")

            # Test local server via nickname
            local_message = {"op": "eval", "code": '(str "MULTI-Local: " (+ 300 400))'}
            local_send = self.call_mcp_tool(
                "nrepl-send-message-async",
                {"message": local_message, "connection": "local-bb"},
            )

            if local_send.get("status") != "success":
                return False, f"Failed to send to local server: {local_send}", {}

            local_message_id = local_send.get("message-id")

            # Wait for both to process
            time.sleep(3)

            # Get external result
            external_get = self.call_mcp_tool(
                "nrepl-get-result-async", {"message-id": external_message_id}
            )

            if external_get.get("status") != "success":
                return False, f"Failed to get external result: {external_get}", {}

            external_value = external_get.get("response", {}).get("value", "")
            expected_external = "MULTI-External: 300"

            if external_value != expected_external:
                return (
                    False,
                    f"External result mismatch. Expected: {expected_external}, Got: {external_value}",
                    {},
                )

            # Get local result
            local_get = self.call_mcp_tool(
                "nrepl-get-result-async", {"message-id": local_message_id}
            )

            if local_get.get("status") != "success":
                return False, f"Failed to get local result: {local_get}", {}

            local_value = local_get.get("response", {}).get("value", "")
            expected_local = "MULTI-Local: 700"

            if local_value != expected_local:
                return (
                    False,
                    f"Local result mismatch. Expected: {expected_local}, Got: {local_value}",
                    {},
                )

            details = {
                "external_message_id": external_message_id,
                "external_result": external_value,
                "local_message_id": local_message_id,
                "local_result": local_value,
                "routing_success": True,
            }

            return (
                True,
                f"Multi-connection routing working: External={external_value}, Local={local_value}",
                details,
            )

        except Exception as e:
            return False, f"Exception in multi-connection routing test: {e}", {}

    def test_connection_isolation(self) -> Tuple[bool, str, Dict[str, Any]]:
        """Test that connections are properly isolated."""
        try:
            # Define variable in external server
            external_def_message = {
                "op": "eval",
                "code": '(def isolation-test-var "external-value")',
            }
            self.call_mcp_tool(
                "nrepl-send-message-async",
                {"message": external_def_message, "connection": "external-server"},
            )

            time.sleep(1)

            # Check variable exists in external
            external_check_message = {"op": "eval", "code": "isolation-test-var"}
            external_check_send = self.call_mcp_tool(
                "nrepl-send-message-async",
                {"message": external_check_message, "connection": "external-server"},
            )

            external_check_id = external_check_send.get("message-id")
            time.sleep(1)

            external_check_result = self.call_mcp_tool(
                "nrepl-get-result-async", {"message-id": external_check_id}
            )

            external_value = external_check_result.get("response", {}).get("value", "")
            if external_value != "external-value":
                return (
                    False,
                    f"Variable not set in external server: {external_value}",
                    {},
                )

            # Try to access variable in local server (should fail or be undefined)
            local_check_message = {
                "op": "eval",
                "code": "(try isolation-test-var (catch Exception e :undefined))",
            }
            local_check_send = self.call_mcp_tool(
                "nrepl-send-message-async",
                {"message": local_check_message, "connection": "local-bb"},
            )

            local_check_id = local_check_send.get("message-id")
            time.sleep(1)

            local_check_result = self.call_mcp_tool(
                "nrepl-get-result-async", {"message-id": local_check_id}
            )

            local_value = local_check_result.get("response", {}).get("value", "")

            # Should be undefined or error in local server
            if local_value == "external-value":
                return False, f"Variable leaked between connections: {local_value}", {}

            details = {
                "external_variable": external_value,
                "local_variable_check": local_value,
                "isolation_confirmed": local_value != external_value,
            }

            return (
                True,
                f"Connection isolation working: external={external_value}, local={local_value}",
                details,
            )

        except Exception as e:
            return False, f"Exception in connection isolation test: {e}", {}

    def test_connection_lifecycle(self) -> Tuple[bool, str, Dict[str, Any]]:
        """Test complete connection lifecycle including disconnect."""
        try:
            # List initial connections
            initial_list = self.call_mcp_tool("nrepl-connection", {"op": "list"})
            initial_count = len(initial_list.get("connections", []))

            # Disconnect specific connection
            disconnect_result = self.call_mcp_tool(
                "nrepl-connection",
                {"op": "disconnect", "connection": "external-server"},
            )

            if disconnect_result.get("status") != "success":
                return (
                    False,
                    f"Failed to disconnect external server: {disconnect_result}",
                    {},
                )

            # List connections after disconnect
            after_disconnect_list = self.call_mcp_tool(
                "nrepl-connection", {"op": "list"}
            )
            after_disconnect_count = len(after_disconnect_list.get("connections", []))

            if after_disconnect_count != initial_count - 1:
                return (
                    False,
                    f"Expected {initial_count - 1} connections, got {after_disconnect_count}",
                    {},
                )

            # Verify remaining connection is local-bb
            remaining_connections = after_disconnect_list.get("connections", [])
            if len(remaining_connections) != 1:
                return (
                    False,
                    f"Expected 1 remaining connection, got {len(remaining_connections)}",
                    {},
                )

            remaining_nickname = remaining_connections[0].get("nickname", "")
            if remaining_nickname != "local-bb":
                return (
                    False,
                    f"Expected 'local-bb' remaining, got '{remaining_nickname}'",
                    {},
                )

            # Test that external server is no longer accessible
            try:
                external_test_send = self.call_mcp_tool(
                    "nrepl-send-message-async",
                    {
                        "message": {"op": "eval", "code": "(+ 1 1)"},
                        "connection": "external-server",
                    },
                )
                # Should fail since connection was removed
                return (
                    False,
                    f"External server still accessible after disconnect: {external_test_send}",
                    {},
                )
            except Exception:
                # Expected - connection should not be found
                pass

            details = {
                "initial_connections": initial_count,
                "after_disconnect": after_disconnect_count,
                "remaining_nickname": remaining_nickname,
                "disconnect_message": disconnect_result.get("message", ""),
            }

            return (
                True,
                f"Connection lifecycle working: {initial_count} → {after_disconnect_count} connections",
                details,
            )

        except Exception as e:
            return False, f"Exception in connection lifecycle test: {e}", {}

    def test_error_handling(self) -> Tuple[bool, str, Dict[str, Any]]:
        """Test error handling scenarios."""
        try:
            error_tests = {}

            # Test invalid connection nickname
            try:
                self.call_mcp_tool(
                    "nrepl-send-message-async",
                    {
                        "message": {"op": "eval", "code": "(+ 1 1)"},
                        "connection": "nonexistent-server",
                    },
                )
                error_tests["invalid_nickname"] = "❌ Should have failed"
            except Exception as e:
                error_tests["invalid_nickname"] = (
                    f"✅ Correctly failed: {str(e)[:50]}..."
                )

            # Test malformed nREPL message
            try:
                malformed_send = self.call_mcp_tool(
                    "nrepl-send-message-async",
                    {"message": {"invalid": "message"}, "connection": "local-bb"},
                )
                malformed_id = malformed_send.get("message-id")
                time.sleep(1)

                malformed_result = self.call_mcp_tool(
                    "nrepl-get-result-async", {"message-id": malformed_id}
                )

                # Should get some kind of error response
                response = malformed_result.get("response", {})
                if "error" in response or "err" in response:
                    error_tests["malformed_message"] = (
                        "✅ Correctly handled malformed message"
                    )
                else:
                    error_tests["malformed_message"] = (
                        f"⚠️ Unexpected response: {response}"
                    )
            except Exception as e:
                error_tests["malformed_message"] = (
                    f"✅ Correctly failed: {str(e)[:50]}..."
                )

            # Test invalid message-id
            try:
                self.call_mcp_tool(
                    "nrepl-get-result-async", {"message-id": "nonexistent-message-id"}
                )
                error_tests["invalid_message_id"] = (
                    "❌ Should have failed or returned timeout"
                )
            except Exception as e:
                error_tests["invalid_message_id"] = (
                    f"✅ Correctly failed: {str(e)[:50]}..."
                )

            details = error_tests

            # Check if all error tests passed
            all_passed = all("✅" in result for result in error_tests.values())

            return (
                all_passed,
                f"Error handling tests: {len([r for r in error_tests.values() if '✅' in r])}/{len(error_tests)} passed",
                details,
            )

        except Exception as e:
            return False, f"Exception in error handling test: {e}", {}

    def test_cleanup_and_stop_servers(self) -> Tuple[bool, str, Dict[str, Any]]:
        """Clean up all connections and stop servers."""
        try:
            cleanup_results = {}

            # Disconnect all connections
            disconnect_all = self.call_mcp_tool(
                "nrepl-connection", {"op": "disconnect"}
            )
            cleanup_results["disconnect_all"] = disconnect_all.get("status", "unknown")

            # Stop local nREPL server
            stop_local = self.call_mcp_tool("local-nrepl-server", {"op": "stop"})
            cleanup_results["stop_local_server"] = stop_local.get("status", "unknown")

            # Stop external nREPL server
            try:
                stop_external = subprocess.run(
                    ["python3", "scripts/nrepl_test_server.py", "stop"],
                    capture_output=True,
                    text=True,
                    timeout=10,
                )
                cleanup_results["stop_external_server"] = (
                    "success" if stop_external.returncode == 0 else "failed"
                )
            except Exception as e:
                cleanup_results["stop_external_server"] = f"error: {e}"

            # Verify no connections remain
            final_list = self.call_mcp_tool("nrepl-connection", {"op": "list"})
            final_connections = len(final_list.get("connections", []))
            cleanup_results["final_connections"] = final_connections

            details = cleanup_results

            success = (
                cleanup_results.get("disconnect_all") == "success"
                and cleanup_results.get("stop_local_server") == "success"
                and final_connections == 0
            )

            return (
                success,
                f"Cleanup completed: {final_connections} connections remaining",
                details,
            )

        except Exception as e:
            return False, f"Exception in cleanup: {e}", {}

    def clean_environment(self) -> Tuple[bool, str, Dict[str, Any]]:
        """Ensure clean starting environment by stopping all servers and processes."""
        cleanup_results = {}

        print("🧹 Cleaning environment - stopping all servers and processes...")

        # Stop external nREPL test server
        try:
            stop_external = subprocess.run(
                ["python3", "scripts/nrepl_test_server.py", "stop"],
                capture_output=True,
                text=True,
                timeout=10,
            )
            cleanup_results["stop_external_server"] = (
                "success" if stop_external.returncode == 0 else "already_stopped"
            )
            print(
                f"   External nREPL server: {cleanup_results['stop_external_server']}"
            )
        except Exception as e:
            cleanup_results["stop_external_server"] = f"error: {e}"
            print(
                f"   External nREPL server: {cleanup_results['stop_external_server']}"
            )

        # Stop HTTP bridge
        try:
            stop_bridge = subprocess.run(
                ["./scripts/stop-http-bridge.sh"],
                capture_output=True,
                text=True,
                timeout=10,
            )
            cleanup_results["stop_http_bridge"] = (
                "success" if stop_bridge.returncode == 0 else "already_stopped"
            )
            print(f"   HTTP bridge: {cleanup_results['stop_http_bridge']}")
        except Exception as e:
            cleanup_results["stop_http_bridge"] = f"error: {e}"
            print(f"   HTTP bridge: {cleanup_results['stop_http_bridge']}")

        # Kill any remaining nREPL or bb processes
        try:
            subprocess.run(
                ["pkill", "-f", "nrepl"], capture_output=True, text=True, timeout=5
            )
            cleanup_results["kill_nrepl_processes"] = "cleaned"

            subprocess.run(
                ["pkill", "-f", "bb.*src"], capture_output=True, text=True, timeout=5
            )
            cleanup_results["kill_bb_processes"] = "cleaned"
            print("   Killed any remaining nREPL/bb processes")
        except Exception as e:
            cleanup_results["kill_processes"] = f"error: {e}"
            print(
                f"   Process cleanup: {cleanup_results.get('kill_processes', 'completed')}"
            )

        # Wait for cleanup to complete
        time.sleep(2)

        # Start HTTP bridge fresh
        try:
            start_bridge = subprocess.run(
                ["./scripts/start-http-bridge.sh"],
                capture_output=True,
                text=True,
                timeout=15,
            )
            cleanup_results["start_http_bridge"] = (
                "success" if start_bridge.returncode == 0 else "failed"
            )
            print(f"   Started HTTP bridge: {cleanup_results['start_http_bridge']}")

            # Wait for bridge to be ready
            time.sleep(3)
        except Exception as e:
            cleanup_results["start_http_bridge"] = f"error: {e}"
            print(f"   HTTP bridge start: {cleanup_results['start_http_bridge']}")
            return False, f"Failed to start HTTP bridge: {e}", cleanup_results

        # Start external nREPL server fresh
        try:
            start_external = subprocess.run(
                ["python3", "scripts/nrepl_test_server.py", "start"],
                capture_output=True,
                text=True,
                timeout=15,
            )
            cleanup_results["start_external_server"] = (
                "success" if start_external.returncode == 0 else "failed"
            )
            print(
                f"   Started external nREPL: {cleanup_results['start_external_server']}"
            )

            # Wait for server to be ready
            time.sleep(2)
        except Exception as e:
            cleanup_results["start_external_server"] = f"error: {e}"
            print(
                f"   External nREPL start: {cleanup_results['start_external_server']}"
            )
            return False, f"Failed to start external nREPL server: {e}", cleanup_results

        success = (
            cleanup_results.get("start_http_bridge") == "success"
            and cleanup_results.get("start_external_server") == "success"
        )

        return success, "Environment cleaned and servers started", cleanup_results

    def run_all_tests(self):
        """Run complete test suite starting from clean environment."""
        print("🚀 Starting Comprehensive Multi-Connection Test Suite")
        print("🧹 Starting from completely clean environment...")
        print("=" * 80)

        # Define test sequence
        tests = [
            ("Clean Environment", self.clean_environment),
            ("Prerequisites Check", self.test_prerequisites),
            ("Start Local nREPL Server", self.test_start_local_nrepl_server),
            ("Single Connection - External", self.test_single_connection_external),
            ("Single Connection - Local", self.test_single_connection_local),
            ("Multi-Connection Setup", self.test_multi_connection_setup),
            ("Multi-Connection Routing", self.test_multi_connection_routing),
            ("Connection Isolation", self.test_connection_isolation),
            ("Connection Lifecycle", self.test_connection_lifecycle),
            ("Error Handling", self.test_error_handling),
            ("Cleanup and Stop Servers", self.test_cleanup_and_stop_servers),
        ]

        # Run all tests
        for test_name, test_func in tests:
            result = self.run_test(test_name, test_func)
            self.test_results.append(result)

        # Print summary
        self.print_summary()

    def print_summary(self):
        """Print comprehensive test results summary."""
        total_time = time.time() - self.start_time
        passed = sum(1 for r in self.test_results if r.success)
        total = len(self.test_results)

        print("\n" + "=" * 80)
        print("📊 COMPREHENSIVE MULTI-CONNECTION TEST RESULTS")
        print("=" * 80)

        print(f"\n📈 SUMMARY:")
        print(f"   Total Tests: {total}")
        print(f"   Passed: {passed}")
        print(f"   Failed: {total - passed}")
        print(f"   Success Rate: {(passed/total)*100:.1f}%")
        print(f"   Total Duration: {total_time:.2f}s")

        print(f"\n🧪 DETAILED RESULTS:")
        for result in self.test_results:
            status = "✅ PASS" if result.success else "❌ FAIL"
            print(f"   {status} {result.name} ({result.duration:.2f}s)")
            if not result.success:
                print(f"      Error: {result.message}")
            elif result.details:
                # Show key details for passed tests
                if isinstance(result.details, dict):
                    key_details = []
                    for key, value in result.details.items():
                        if key in [
                            "evaluation_result",
                            "external_result",
                            "local_result",
                            "total_connections",
                            "nicknames",
                        ]:
                            key_details.append(f"{key}: {value}")
                    if key_details:
                        print(
                            f"      Details: {', '.join(key_details[:2])}"
                        )  # Limit to 2 details

        if self.external_server_port and self.local_server_port:
            print(f"\n🖥️  SERVER CONFIGURATION:")
            print(f"   External nREPL: port {self.external_server_port}")
            print(f"   Local Babashka: port {self.local_server_port}")
            print(f"   HTTP Bridge: {HTTP_BRIDGE_URL}")

        print(f"\n🎯 MULTI-CONNECTION STATUS:")
        if passed == total:
            print(
                "   🎉 ALL TESTS PASSED - Multi-connection architecture fully functional!"
            )
            print("   ✅ External + Local servers working simultaneously")
            print("   ✅ Nickname-based routing operational")
            print("   ✅ Connection isolation confirmed")
            print("   ✅ Async message queue handling all scenarios correctly")
        else:
            print(
                f"   ⚠️  {total - passed} test(s) failed - Multi-connection needs attention"
            )

        print("=" * 80)


if __name__ == "__main__":
    tester = MultiConnectionTester()
    tester.run_all_tests()

    # Exit with proper code
    passed = sum(1 for r in tester.test_results if r.success)
    total = len(tester.test_results)

    if passed == total:
        print("\n🎉 SUCCESS: All tests passed!")
        sys.exit(0)
    else:
        print(f"\n❌ FAILURE: {total - passed} test(s) failed")
        sys.exit(1)
