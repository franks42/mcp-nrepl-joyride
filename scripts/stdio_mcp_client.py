#!/usr/bin/env python3
"""
Generic stdio MCP Client.

A reusable test client for any MCP server that uses stdio transport.

This client can test any MCP server that follows the stdio transport
protocol, making it useful for testing various MCP implementations.

Features:
- Generic MCP protocol support (works with any stdio MCP server)
- Proper subprocess management with cleanup
- JSON-RPC 2.0 compliant communication
- Timeout handling for robust testing
- Rich output formatting (optional)
- Command-line interface for manual testing
- Programmatic API for test suites

Usage Examples:
  # Test our nREPL MCP server
  python3 stdio_mcp_client.py --server-cmd \\
    "bb -cp src src/mcp_nrepl_proxy/core.clj" \\
    --tool nrepl-eval --args '{"code": "(+ 1 2 3)"}'

  # Test any other MCP server
  python3 stdio_mcp_client.py --server-cmd "./my-mcp-server" --list-tools

  # Interactive mode
  python3 stdio_mcp_client.py --server-cmd "node mcp-server.js" \\
    --interactive

Design Philosophy:
- Server-agnostic: Works with any stdio MCP server
- Test-friendly: Easy integration into test suites
- Production-realistic: Uses exact same stdio interface as Claude Desktop
- Robust: Handles timeouts, errors, subprocess cleanup
"""

import argparse
import json
import os
import signal
import subprocess
import sys
import time
from typing import Any, Dict, List, Optional

try:
    from rich.console import Console
    from rich.table import Table
    HAS_RICH = True
except ImportError:
    HAS_RICH = False


class StdioMCPClient:
    """
    Generic stdio MCP client for any MCP server using stdio transport.

    This client spawns the MCP server as a subprocess and communicates
    using JSON-RPC 2.0 over stdin/stdout, exactly like Claude Desktop
    and other MCP clients do.
    """

    def __init__(self, server_command: List[str], timeout: float = 30.0,
                 quiet: bool = False):
        """
        Initialize the stdio MCP client.

        Args:
            server_command: Command to spawn the MCP server
            timeout: Default timeout for operations in seconds
            quiet: Suppress debug output
        """
        self.server_command = server_command
        self.timeout = timeout
        self.quiet = quiet
        self.console = Console() if HAS_RICH and not quiet else None
        self.process: Optional[subprocess.Popen] = None
        self.request_id = 0
        self.initialized = False
        self.server_info: Dict[str, Any] = {}
        self.tools_cache: List[Dict[str, Any]] = []

    def _debug(self, message: str, style: str = "dim") -> None:
        """Print debug message if not in quiet mode."""
        if not self.quiet:
            if self.console:
                self.console.print(f"[DEBUG] {message}", style=style)
            else:
                print(f"[DEBUG] {message}", file=sys.stderr)

    def _error(self, message: str) -> None:
        """Print error message."""
        if self.console:
            self.console.print(f"❌ {message}", style="bold red")
        else:
            print(f"❌ {message}", file=sys.stderr)

    def _success(self, message: str) -> None:
        """Print success message."""
        if not self.quiet:
            if self.console:
                self.console.print(f"✅ {message}", style="bold green")
            else:
                print(f"✅ {message}")

    def start_server(self) -> bool:
        """
        Start the MCP server subprocess.

        Returns:
            True if server started successfully, False otherwise
        """
        try:
            cmd_str = ' '.join(self.server_command)
            self._debug(f"Starting MCP server: {cmd_str}")

            self.process = subprocess.Popen(
                self.server_command,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=0,  # Unbuffered for real-time communication
                # Ensure clean process tree
                start_new_session=True if os.name == "posix" else None,
            )

            # Give server a moment to start
            time.sleep(0.1)

            # Check if process is still running
            if self.process.poll() is not None:
                stderr_output = (
                    self.process.stderr.read()
                    if self.process.stderr
                    else "No error output"
                )
                self._error(
                    f"Server process exited immediately. "
                    f"Error: {stderr_output}"
                )
                return False

            self._debug(f"Server started with PID: {self.process.pid}")
            return True

        except Exception as e:
            self._error(f"Failed to start server: {e}")
            return False

    def stop_server(self) -> None:
        """Stop the MCP server subprocess with proper cleanup."""
        if self.process:
            try:
                self._debug(f"Stopping server (PID: {self.process.pid})")

                # Try graceful shutdown first
                if self.process.stdin:
                    self.process.stdin.close()

                # Wait briefly for graceful shutdown
                try:
                    self.process.wait(timeout=2.0)
                    self._debug("Server shut down gracefully")
                except subprocess.TimeoutExpired:
                    self._debug("Graceful shutdown timeout, terminating...")

                    # Force termination
                    if os.name == "posix":
                        os.killpg(os.getpgid(self.process.pid),
                                  signal.SIGTERM)
                    else:
                        self.process.terminate()

                    try:
                        self.process.wait(timeout=1.0)
                    except subprocess.TimeoutExpired:
                        self._debug("Termination timeout, killing...")
                        if os.name == "posix":
                            os.killpg(os.getpgid(self.process.pid),
                                      signal.SIGKILL)
                        else:
                            self.process.kill()
                        self.process.wait()

            except Exception as e:
                self._debug(f"Error during server shutdown: {e}")

            finally:
                self.process = None
                self.initialized = False

    def send_request(
        self,
        method: str,
        params: Optional[Dict[str, Any]] = None,
        timeout: Optional[float] = None,
    ) -> Dict[str, Any]:
        """
        Send a JSON-RPC request to the MCP server.

        Args:
            method: JSON-RPC method name
            params: Request parameters
            timeout: Request timeout (uses default if None)

        Returns:
            Response dictionary or error dictionary
        """
        if not self.process or not self.process.stdin:
            return {"error": {"code": -1, "message": "Server not started"}}

        self.request_id += 1
        request = {
            "jsonrpc": "2.0",
            "id": self.request_id,
            "method": method,
            "params": params or {},
        }

        try:
            request_json = json.dumps(request) + "\n"
            self._debug(f"→ {request_json.strip()}")

            # Send request
            self.process.stdin.write(request_json)
            self.process.stdin.flush()

            # Read response with timeout
            response_line = ""
            start_time = time.time()
            timeout = timeout or self.timeout

            while time.time() - start_time < timeout:
                if self.process.stdout and self.process.stdout.readable():
                    try:
                        # Non-blocking read attempt
                        response_line = self.process.stdout.readline()
                        if response_line:
                            break
                    except Exception:
                        pass
                time.sleep(0.01)  # Small delay to prevent busy waiting

            if not response_line:
                return {
                    "error": {
                        "code": -2,
                        "message": f"Timeout after {timeout}s"
                    }
                }

            self._debug(f"← {response_line.strip()}")

            response = json.loads(response_line)
            return response

        except json.JSONDecodeError as e:
            return {
                "error": {
                    "code": -3,
                    "message": f"Invalid JSON response: {e}"
                }
            }
        except Exception as e:
            return {
                "error": {
                    "code": -4,
                    "message": f"Communication error: {e}"
                }
            }

    def initialize(self) -> bool:
        """
        Initialize the MCP connection.

        Returns:
            True if initialization successful, False otherwise
        """
        if self.initialized:
            return True

        response = self.send_request(
            "initialize",
            {
                "protocolVersion": "2024-11-05",
                "capabilities": {"tools": {"list": True, "call": True}},
                "clientInfo": {"name": "stdio-mcp-client", "version": "1.0.0"},
            },
        )

        if "error" in response:
            error_msg = response["error"].get("message", "Unknown error")
            self._error(f"Initialization failed: {error_msg}")
            return False

        if "result" in response:
            self.server_info = response["result"]
            self.initialized = True
            self._success("MCP connection initialized")
            return True

        self._error("Invalid initialization response")
        return False

    def list_tools(self) -> List[Dict[str, Any]]:
        """
        List available tools from the MCP server.

        Returns:
            List of tool definitions or empty list on error
        """
        response = self.send_request("tools/list")

        if "error" in response:
            error_msg = response["error"].get("message", "Unknown error")
            self._error(f"Failed to list tools: {error_msg}")
            return []

        if "result" in response and "tools" in response["result"]:
            self.tools_cache = response["result"]["tools"]
            return self.tools_cache

        return []

    def call_tool(self, name: str,
                  arguments: Optional[Dict[str, Any]] = None
                  ) -> Dict[str, Any]:
        """
        Call a specific tool on the MCP server.

        Args:
            name: Tool name
            arguments: Tool arguments

        Returns:
            Tool response or error dictionary
        """
        response = self.send_request(
            "tools/call", {"name": name, "arguments": arguments or {}}
        )

        if "error" in response:
            return {"error": response["error"]}

        if "result" in response:
            return response["result"]

        return {"error": {"code": -5, "message": "Invalid tool response"}}

    def __enter__(self):
        """Context manager entry."""
        if self.start_server() and self.initialize():
            return self
        raise RuntimeError("Failed to start and initialize MCP server")

    def __exit__(self, exc_type, exc_val, exc_tb):
        """Context manager exit with cleanup."""
        self.stop_server()


class StdioMCPTestSuite:
    """Test suite for stdio MCP servers with common test patterns."""

    def __init__(self, server_command: List[str], quiet: bool = False):
        """Initialize test suite."""
        self.server_command = server_command
        self.quiet = quiet

    def run_basic_tests(self) -> bool:
        """Run basic MCP protocol tests."""
        print("🧪 Running basic MCP protocol tests...")

        try:
            with StdioMCPClient(self.server_command,
                                quiet=self.quiet) as client:
                # Test 1: Server info
                print("  1. Testing server initialization...")
                assert client.initialized, "Server should be initialized"

                # Test 2: Tool listing
                print("  2. Testing tool listing...")
                tools = client.list_tools()
                assert len(tools) > 0, "Server should have at least one tool"

                # Test 3: Tool calling (if server has tools)
                print("  3. Testing basic tool calls...")
                for tool in tools[:3]:  # Test first 3 tools
                    tool_name = tool["name"]
                    print(f"     Testing tool: {tool_name}")

                    # Try calling with empty args first
                    result = client.call_tool(tool_name, {})
                    assert (
                        "error" not in result or result["error"]["code"] != -1
                    ), f"Tool {tool_name} should be callable"

                print("✅ Basic tests passed!")
                return True

        except Exception as e:
            print(f"❌ Basic tests failed: {e}")
            return False

    def run_nrepl_tests(self) -> bool:
        """Run nREPL-specific tests (if this is an nREPL MCP server)."""
        print("🧪 Running nREPL-specific tests...")

        try:
            with StdioMCPClient(self.server_command,
                                quiet=self.quiet) as client:
                # Test nREPL evaluation
                print("  1. Testing nREPL evaluation...")
                result = client.call_tool("nrepl-eval", {"code": "(+ 1 2 3)"})

                if "error" in result:
                    print(f"     nREPL not available: {result['error']}")
                    return False

                # Check if we got a reasonable response
                assert "content" in result, "Should have content in response"

                # Test status
                print("  2. Testing nREPL status...")
                status = client.call_tool("nrepl-status", {})
                assert "error" not in status, "Status should work"

                print("✅ nREPL tests passed!")
                return True

        except Exception as e:
            print(f"❌ nREPL tests failed: {e}")
            return False


def main() -> None:
    """Command-line interface for the stdio MCP client."""
    parser = argparse.ArgumentParser(
        description="Generic stdio MCP client - test any MCP server "
                    "using stdio transport",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Test our nREPL MCP server
  %(prog)s --server-cmd "bb -cp src src/mcp_nrepl_proxy/core.clj" \\
    --list-tools

  # Call a specific tool
  %(prog)s --server-cmd "bb -cp src src/mcp_nrepl_proxy/core.clj" \\
    --tool nrepl-eval --args '{"code": "(+ 1 2 3)"}'

  # Run test suite
  %(prog)s --server-cmd "bb -cp src src/mcp_nrepl_proxy/core.clj" \\
    --test-basic

  # Interactive mode
  %(prog)s --server-cmd "./my-server" --interactive
        """,
    )

    # Server configuration
    parser.add_argument(
        "--server-cmd",
        required=True,
        help="Command to start the MCP server (shell command)",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=30.0,
        help="Request timeout in seconds (default: 30)",
    )
    parser.add_argument(
        "--quiet", "-q", action="store_true", help="Suppress debug output"
    )

    # Actions
    parser.add_argument(
        "--list-tools",
        action="store_true",
        help="List available tools and exit"
    )
    parser.add_argument("--tool", metavar="NAME", help="Call specific tool")
    parser.add_argument(
        "--args", default="{}", help="Tool arguments as JSON (default: {})"
    )
    parser.add_argument(
        "--interactive", "-i", action="store_true", help="Interactive mode"
    )

    # Testing
    parser.add_argument(
        "--test-basic",
        action="store_true",
        help="Run basic MCP protocol tests"
    )
    parser.add_argument(
        "--test-nrepl", action="store_true", help="Run nREPL-specific tests"
    )

    # Output
    parser.add_argument(
        "--pretty", action="store_true", help="Pretty-print JSON output"
    )

    args = parser.parse_args()

    # Parse server command
    import shlex

    server_command = shlex.split(args.server_cmd)

    try:
        # Handle test suites
        if args.test_basic or args.test_nrepl:
            suite = StdioMCPTestSuite(server_command, args.quiet)
            success = True

            if args.test_basic:
                success &= suite.run_basic_tests()

            if args.test_nrepl:
                success &= suite.run_nrepl_tests()

            sys.exit(0 if success else 1)

        # Handle single operations
        with StdioMCPClient(server_command, args.timeout,
                            args.quiet) as client:
            if args.list_tools:
                tools = client.list_tools()
                if args.pretty and HAS_RICH:
                    console = Console()
                    table = Table(title="Available Tools")
                    table.add_column("Name", style="cyan")
                    table.add_column("Description", style="white")

                    for tool in tools:
                        table.add_row(
                            tool["name"],
                            tool.get("description", "No description")
                        )
                    console.print(table)
                else:
                    indent = 2 if args.pretty else None
                    print(json.dumps(tools, indent=indent))

            elif args.tool:
                try:
                    tool_args = json.loads(args.args)
                except json.JSONDecodeError:
                    print("❌ Invalid JSON in --args")
                    sys.exit(1)

                result = client.call_tool(args.tool, tool_args)
                indent = 2 if args.pretty else None
                print(json.dumps(result, indent=indent))

                if "error" in result:
                    sys.exit(1)

            elif args.interactive:
                # Interactive mode - keep server running
                client.run_interactive_mode()

            else:
                # Default: show server info and tools
                server_info_str = json.dumps(client.server_info, indent=2)
                print(f"Server info: {server_info_str}")
                tools = client.list_tools()
                tool_names = [t['name'] for t in tools]
                print(f"Available tools ({len(tools)}): {tool_names}")

    except KeyboardInterrupt:
        print("\n👋 Interrupted by user")
        sys.exit(0)
    except Exception as e:
        print(f"❌ Error: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
