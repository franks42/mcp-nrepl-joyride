#!/usr/bin/env python3
"""
Unified MCP Testing Framework

A comprehensive, scalable testing solution that replaces all hardcoded test scripts.
Supports both stdio and HTTP transport with dynamic test discovery and external
test case definitions.

Key Features:
- 🔄 Transport Agnostic: stdio, HTTP, or both
- 🎯 Dynamic Tool Discovery: No hardcoded test cases
- 📋 External Test Definitions: JSON/YAML test case files
- 🏗️ Orchestration: Phase-based testing like test-master.sh
- 🔍 Interactive Mode: Manual testing and exploration
- 📊 Rich Reporting: Detailed test results and summaries
- 🔧 Tool Management: Server lifecycle and cleanup

Architecture:
- GenericMCPClient: Pure MCP protocol client (stdio/HTTP)
- DynamicTestSuite: Discovers tools and loads external tests
- TestOrchestrator: Manages test phases and server lifecycle
- ConfigLoader: Loads test definitions from external files

Usage Examples:
  # Discover and test all tools via stdio
  python3 unified_mcp_tester.py --transport stdio \\
    --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj"
  
  # Test specific tools via HTTP bridge
  python3 unified_mcp_tester.py --transport http \\
    --base-url http://localhost:3000 \\
    --test-file tests/basic-tests.json
  
  # Interactive mode for exploration
  python3 unified_mcp_tester.py --transport stdio \\
    --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj" \\
    --interactive
  
  # Orchestrated testing (replaces test-master.sh)
  python3 unified_mcp_tester.py --orchestrate \\
    --config tests/orchestration.json

Design Philosophy:
- Server-agnostic: Works with any MCP server
- Test-agnostic: No hardcoded expectations
- Scale-friendly: External test definitions
- Transport-flexible: stdio, HTTP, or future transports
- Development-focused: Interactive exploration + automated testing
"""

import argparse
import asyncio
import json
import os
import subprocess
import sys
import time
from dataclasses import dataclass
from datetime import datetime
from enum import Enum
from typing import Any, Dict, List, Optional

try:
    import httpx

    HTTPX_AVAILABLE = True
except ImportError:
    HTTPX_AVAILABLE = False

try:
    from rich.console import Console
    from rich.panel import Panel
    from rich.prompt import Prompt
    from rich.syntax import Syntax
    from rich.table import Table

    RICH_AVAILABLE = True
except ImportError:
    RICH_AVAILABLE = False


class TransportType(Enum):
    """Supported MCP transport types."""

    STDIO = "stdio"
    HTTP = "http"


class TestResult(Enum):
    """Test execution results."""

    PASSED = "passed"
    FAILED = "failed"
    SKIPPED = "skipped"
    ERROR = "error"


@dataclass
class TestCase:
    """Individual test case definition."""

    name: str
    tool: str
    args: Dict[str, Any]
    expected: Optional[Dict[str, Any]] = None
    description: str = ""
    timeout: int = 30


@dataclass
class TestSuiteResult:
    """Results from a test suite execution."""

    suite_name: str
    total_tests: int
    passed: int
    failed: int
    errors: int
    skipped: int
    duration: float
    test_results: List[Dict[str, Any]]


class GenericMCPClient:
    """Generic MCP client supporting multiple transports."""

    def __init__(self, transport: TransportType, **kwargs):
        self.transport = transport
        self.console = Console() if RICH_AVAILABLE else None
        self._initialized = False

        if transport == TransportType.STDIO:
            self._init_stdio_client(**kwargs)
        elif transport == TransportType.HTTP:
            self._init_http_client(**kwargs)
        else:
            raise ValueError(f"Unsupported transport: {transport}")

    def _init_stdio_client(self, server_cmd: str, timeout: int = 30, **kwargs):
        """Initialize stdio transport client."""
        self.server_cmd = server_cmd
        self.timeout = timeout
        self.process = None
        self.request_id = 1

    def _init_http_client(self, base_url: str, timeout: int = 30, **kwargs):
        """Initialize HTTP transport client."""
        if not HTTPX_AVAILABLE:
            raise ImportError("httpx required for HTTP transport")

        self.base_url = base_url.rstrip("/")
        self.mcp_url = f"{self.base_url}/mcp/"
        self.timeout = timeout
        self.request_id = 1
        self.headers = {
            "Content-Type": "application/json",
            "Accept": "application/json, text/event-stream",
        }

    async def connect(self) -> bool:
        """Establish connection to MCP server."""
        try:
            if self.transport == TransportType.STDIO:
                return await self._connect_stdio()
            elif self.transport == TransportType.HTTP:
                return await self._connect_http()
        except Exception as e:
            self._log(f"Connection failed: {e}")
            return False

    async def _connect_stdio(self) -> bool:
        """Connect to stdio MCP server."""
        try:
            # Start subprocess
            self.process = subprocess.Popen(
                self.server_cmd.split(),
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=0,
            )

            # Wait a moment for server startup
            await asyncio.sleep(1)

            # Check if process is still running
            if self.process.poll() is not None:
                stderr = self.process.stderr.read() if self.process.stderr else ""
                self._log(f"Server process died: {stderr}")
                return False

            # Try to initialize
            result = await self.initialize()
            return "error" not in result

        except Exception as e:
            self._log(f"stdio connection error: {e}")
            return False

    async def _connect_http(self) -> bool:
        """Connect to HTTP MCP server."""
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                # Test basic connectivity
                await client.get(f"{self.base_url}/")
                # Don't require 200 - server might return 404 for root

            # Try to initialize
            result = await self.initialize()
            return "error" not in result

        except Exception as e:
            self._log(f"HTTP connection error: {e}")
            return False

    async def initialize(self) -> Dict[str, Any]:
        """Initialize MCP session."""
        return await self._make_request(
            "initialize",
            {
                "protocolVersion": "2024-11-05",
                "capabilities": {"tools": {"list": True, "call": True}},
                "clientInfo": {
                    "name": "unified-mcp-tester",
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

    async def _make_request(
        self, method: str, params: Dict[str, Any] = None
    ) -> Dict[str, Any]:
        """Make MCP JSON-RPC request."""
        if self.transport == TransportType.STDIO:
            return await self._make_stdio_request(method, params)
        elif self.transport == TransportType.HTTP:
            return await self._make_http_request(method, params)

    async def _make_stdio_request(
        self, method: str, params: Dict[str, Any] = None
    ) -> Dict[str, Any]:
        """Make stdio JSON-RPC request."""
        if not self.process or self.process.poll() is not None:
            return {"error": "No active server process"}

        try:
            request = {
                "jsonrpc": "2.0",
                "id": self.request_id,
                "method": method,
                "params": params or {},
            }
            self.request_id += 1

            # Send request
            request_line = json.dumps(request) + "\\n"
            self.process.stdin.write(request_line)
            self.process.stdin.flush()

            # Read response
            response_line = self.process.stdout.readline()
            if not response_line:
                return {"error": "No response from server"}

            response = json.loads(response_line.strip())

            if "error" in response:
                return {"error": response["error"]}

            return response.get("result", {})

        except Exception as e:
            return {"error": str(e)}

    async def _make_http_request(
        self, method: str, params: Dict[str, Any] = None
    ) -> Dict[str, Any]:
        """Make HTTP JSON-RPC request."""
        try:
            payload = {
                "jsonrpc": "2.0",
                "id": self.request_id,
                "method": method,
                "params": params or {},
            }
            self.request_id += 1

            async with httpx.AsyncClient(timeout=self.timeout) as client:
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

    async def disconnect(self):
        """Disconnect from MCP server."""
        if self.transport == TransportType.STDIO and self.process:
            try:
                self.process.terminate()
                await asyncio.sleep(1)
                if self.process.poll() is None:
                    self.process.kill()
                self.process.wait()
            except:
                pass
            finally:
                self.process = None

    def _log(self, message: str, level: str = "info"):
        """Log message with optional rich formatting."""
        timestamp = datetime.now().strftime("%H:%M:%S")
        if self.console:
            if level == "error":
                self.console.print(f"[red][{timestamp}] ERROR: {message}[/red]")
            elif level == "warning":
                self.console.print(f"[yellow][{timestamp}] WARN: {message}[/yellow]")
            else:
                self.console.print(f"[dim][{timestamp}] {message}[/dim]")
        else:
            print(f"[{timestamp}] {level.upper()}: {message}")


class DynamicTestSuite:
    """Dynamic test suite with external test case loading."""

    def __init__(self, client: GenericMCPClient):
        self.client = client
        self.console = Console() if RICH_AVAILABLE else None

    async def discover_tools(self) -> List[str]:
        """Discover available tools from server."""
        try:
            result = await self.client.list_tools()
            if "error" in result:
                self._log(f"Tool discovery failed: {result['error']}", "error")
                return []

            tools = result.get("tools", [])
            tool_names = [tool["name"] for tool in tools]

            self._log(f"Discovered {len(tool_names)} tools: {tool_names}")
            return tool_names

        except Exception as e:
            self._log(f"Tool discovery error: {e}", "error")
            return []

    def load_test_cases(self, test_file: str) -> Dict[str, List[TestCase]]:
        """Load test cases from external file."""
        try:
            if not os.path.exists(test_file):
                self._log(f"Test file not found: {test_file}", "error")
                return {}

            with open(test_file, "r") as f:
                if test_file.endswith(".json"):
                    data = json.load(f)
                else:
                    # Try YAML if available
                    try:
                        import yaml

                        data = yaml.safe_load(f)
                    except ImportError:
                        self._log("YAML support not available", "error")
                        return {}

            # Convert to TestCase objects
            test_suites = {}
            for suite_name, tests in data.items():
                test_cases = []
                for test_data in tests:
                    test_case = TestCase(
                        name=test_data.get("name", f"test_{len(test_cases)}"),
                        tool=test_data["tool"],
                        args=test_data["args"],
                        expected=test_data.get("expected"),
                        description=test_data.get("description", ""),
                        timeout=test_data.get("timeout", 30),
                    )
                    test_cases.append(test_case)

                test_suites[suite_name] = test_cases

            total_tests = sum(len(tests) for tests in test_suites.values())
            self._log(f"Loaded {total_tests} tests from {len(test_suites)} suites")
            return test_suites

        except Exception as e:
            self._log(f"Failed to load test cases: {e}", "error")
            return {}

    def generate_basic_tests(self, tools: List[str]) -> Dict[str, List[TestCase]]:
        """Generate basic connectivity tests for discovered tools."""
        test_cases = []

        for tool in tools:
            # Basic connectivity test
            test_case = TestCase(
                name=f"{tool}_connectivity",
                tool=tool,
                args={},  # Empty args to test basic connectivity
                description=f"Basic connectivity test for {tool}",
            )
            test_cases.append(test_case)

        return {"basic_connectivity": test_cases}

    async def run_test_suite(
        self, suite_name: str, test_cases: List[TestCase]
    ) -> TestSuiteResult:
        """Run a suite of test cases."""
        self._log(f"Running test suite: {suite_name}")

        start_time = time.time()
        results = []
        passed = failed = errors = skipped = 0

        for i, test_case in enumerate(test_cases, 1):
            self._log(f"[{i}/{len(test_cases)}] {test_case.name}")

            try:
                # Execute test
                result = await self.client.call_tool(test_case.tool, test_case.args)

                # Evaluate result
                if "error" in result:
                    test_result = TestResult.FAILED
                    failed += 1
                    details = f"Tool error: {result['error']}"
                else:
                    # If expected result specified, validate it
                    if test_case.expected:
                        if self._validate_expected(result, test_case.expected):
                            test_result = TestResult.PASSED
                            passed += 1
                            details = "Expected result matched"
                        else:
                            test_result = TestResult.FAILED
                            failed += 1
                            details = f"Expected {test_case.expected}, got {result}"
                    else:
                        # No expectation, just check for no error
                        test_result = TestResult.PASSED
                        passed += 1
                        details = "Tool executed successfully"

                results.append(
                    {
                        "test": test_case.name,
                        "tool": test_case.tool,
                        "result": test_result.value,
                        "details": details,
                        "response": result,
                    }
                )

            except Exception as e:
                test_result = TestResult.ERROR
                errors += 1
                results.append(
                    {
                        "test": test_case.name,
                        "tool": test_case.tool,
                        "result": test_result.value,
                        "details": str(e),
                        "response": None,
                    }
                )

        duration = time.time() - start_time

        return TestSuiteResult(
            suite_name=suite_name,
            total_tests=len(test_cases),
            passed=passed,
            failed=failed,
            errors=errors,
            skipped=skipped,
            duration=duration,
            test_results=results,
        )

    def _validate_expected(
        self, actual: Dict[str, Any], expected: Dict[str, Any]
    ) -> bool:
        """Validate actual result against expected."""
        # Handle nested JSON responses from MCP content field
        response_data = actual

        # If this is an MCP response with content array, parse the JSON content
        if (
            "content" in actual
            and isinstance(actual["content"], list)
            and len(actual["content"]) > 0
        ):
            content_item = actual["content"][0]
            if "text" in content_item:
                try:
                    # Parse the JSON string in the content.text field
                    response_data = json.loads(content_item["text"])
                except (json.JSONDecodeError, TypeError):
                    # If parsing fails, use the original response
                    response_data = actual

        # Validate against the parsed response data
        for key, value in expected.items():
            if key not in response_data:
                return False
            if response_data[key] != value:
                return False
        return True

    def _log(self, message: str, level: str = "info"):
        """Log message with optional rich formatting."""
        timestamp = datetime.now().strftime("%H:%M:%S")
        if self.console:
            if level == "error":
                self.console.print(f"[red][{timestamp}] ERROR: {message}[/red]")
            elif level == "warning":
                self.console.print(f"[yellow][{timestamp}] WARN: {message}[/yellow]")
            else:
                self.console.print(f"[cyan][{timestamp}] {message}[/cyan]")
        else:
            print(f"[{timestamp}] {level.upper()}: {message}")


class TestOrchestrator:
    """Orchestrates comprehensive testing across multiple phases."""

    def __init__(self):
        self.console = Console() if RICH_AVAILABLE else None
        self.results: List[TestSuiteResult] = []

    async def run_orchestrated_tests(self, config_file: str) -> bool:
        """Run orchestrated tests from configuration file."""
        try:
            config = self._load_orchestration_config(config_file)
            if not config:
                return False

            self._print_header("Unified MCP Testing Framework")

            overall_success = True

            for phase in config.get("phases", []):
                phase_name = phase.get("name", "Unknown Phase")
                phase_success = await self._run_phase(phase)

                if not phase_success:
                    overall_success = False
                    if phase.get("stop_on_failure", False):
                        self._log(f"Stopping on phase failure: {phase_name}", "error")
                        break

            self._print_summary()
            return overall_success

        except Exception as e:
            self._log(f"Orchestration failed: {e}", "error")
            return False

    async def _run_phase(self, phase_config: Dict[str, Any]) -> bool:
        """Run a single test phase."""
        phase_name = phase_config.get("name", "Unknown Phase")
        transport_type = TransportType(phase_config.get("transport", "stdio"))

        self._print_phase_header(phase_name)

        try:
            # Create client for this phase
            client_config = phase_config.get("client", {})
            client = GenericMCPClient(transport_type, **client_config)

            # Connect
            if not await client.connect():
                self._log(f"Failed to connect for phase: {phase_name}", "error")
                return False

            # Create test suite
            test_suite = DynamicTestSuite(client)

            # Load or generate tests
            test_cases = {}
            if "test_file" in phase_config:
                test_cases = test_suite.load_test_cases(phase_config["test_file"])
            else:
                # Generate basic tests
                tools = await test_suite.discover_tools()
                test_cases = test_suite.generate_basic_tests(tools)

            # Run tests
            phase_success = True
            for suite_name, cases in test_cases.items():
                result = await test_suite.run_test_suite(suite_name, cases)
                self.results.append(result)

                if result.failed > 0 or result.errors > 0:
                    phase_success = False

            # Cleanup
            await client.disconnect()

            return phase_success

        except Exception as e:
            self._log(f"Phase {phase_name} failed: {e}", "error")
            return False

    def _load_orchestration_config(self, config_file: str) -> Optional[Dict[str, Any]]:
        """Load orchestration configuration."""
        try:
            with open(config_file, "r") as f:
                return json.load(f)
        except Exception as e:
            self._log(f"Failed to load config: {e}", "error")
            return None

    def _print_header(self, title: str):
        """Print formatted header."""
        if self.console:
            self.console.print(Panel(title, style="bold blue"))
        else:
            print(f"\\n{'='*60}")
            print(f" {title}")
            print(f"{'='*60}")

    def _print_phase_header(self, phase_name: str):
        """Print phase header."""
        if self.console:
            self.console.print(f"\\n[bold yellow]Phase: {phase_name}[/bold yellow]")
            self.console.print("-" * 50)
        else:
            print(f"\\n--- Phase: {phase_name} ---")

    def _print_summary(self):
        """Print test summary."""
        if not self.results:
            return

        # total_tests = sum(r.total_tests for r in self.results)
        # total_passed = sum(r.passed for r in self.results)
        total_failed = sum(r.failed for r in self.results)
        total_errors = sum(r.errors for r in self.results)

        if self.console:
            table = Table(title="Test Summary")
            table.add_column("Suite")
            table.add_column("Total")
            table.add_column("Passed", style="green")
            table.add_column("Failed", style="red")
            table.add_column("Errors", style="red")
            table.add_column("Duration")

            for result in self.results:
                table.add_row(
                    result.suite_name,
                    str(result.total_tests),
                    str(result.passed),
                    str(result.failed),
                    str(result.errors),
                    f"{result.duration:.2f}s",
                )

            self.console.print(table)

            if total_failed == 0 and total_errors == 0:
                self.console.print("\\n[bold green]🎉 All tests passed![/bold green]")
            else:
                self.console.print(
                    f"\\n[bold red]❌ {total_failed + total_errors} tests failed[/bold red]"
                )
        else:
            print("\\n--- Test Summary ---")
            for result in self.results:
                print(
                    f"{result.suite_name}: {result.passed}/{result.total_tests} passed"
                )

    def _log(self, message: str, level: str = "info"):
        """Log message."""
        timestamp = datetime.now().strftime("%H:%M:%S")
        if self.console:
            if level == "error":
                self.console.print(f"[red][{timestamp}] ERROR: {message}[/red]")
            else:
                self.console.print(f"[dim][{timestamp}] {message}[/dim]")
        else:
            print(f"[{timestamp}] {level.upper()}: {message}")


async def interactive_mode(client: GenericMCPClient):
    """Interactive mode for manual testing and exploration."""
    console = Console() if RICH_AVAILABLE else None

    if console:
        console.print(Panel("[bold green]Interactive MCP Testing Mode[/bold green]"))
        console.print("Available commands: list, call <tool> <args>, quit")
    else:
        print("\\n=== Interactive MCP Testing Mode ===")
        print("Available commands: list, call <tool> <args>, quit")

    while True:
        try:
            if RICH_AVAILABLE:
                command = Prompt.ask("\\n[bold blue]mcp>[/bold blue]")
            else:
                command = input("\\nmcp> ").strip()

            if not command:
                continue

            if command == "quit":
                break
            elif command == "list":
                result = await client.list_tools()
                if "error" in result:
                    print(f"Error: {result['error']}")
                else:
                    tools = result.get("tools", [])
                    if console:
                        table = Table(title="Available Tools")
                        table.add_column("Name")
                        table.add_column("Description")
                        for tool in tools:
                            table.add_row(
                                tool.get("name", ""), tool.get("description", "")
                            )
                        console.print(table)
                    else:
                        print("Available tools:")
                        for tool in tools:
                            print(
                                f"  - {tool.get('name', '')}: {tool.get('description', '')}"
                            )

            elif command.startswith("call "):
                parts = command.split(" ", 2)
                if len(parts) < 2:
                    print("Usage: call <tool> [args]")
                    continue

                tool_name = parts[1]
                args = {}
                if len(parts) > 2:
                    try:
                        args = json.loads(parts[2])
                    except json.JSONDecodeError:
                        print("Invalid JSON args")
                        continue

                result = await client.call_tool(tool_name, args)

                if console:
                    if "error" in result:
                        console.print(f"[red]Error: {result['error']}[/red]")
                    else:
                        syntax = Syntax(json.dumps(result, indent=2), "json")
                        console.print(syntax)
                else:
                    print(json.dumps(result, indent=2))

            else:
                print("Unknown command. Available: list, call <tool> <args>, quit")

        except KeyboardInterrupt:
            break
        except Exception as e:
            print(f"Error: {e}")

    if console:
        console.print("[dim]Goodbye![/dim]")
    else:
        print("Goodbye!")


async def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(
        description="Unified MCP Testing Framework",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Basic stdio testing
  %(prog)s --transport stdio --server-cmd "bb -cp src src/nrepl_mcp_server/core.clj"
  
  # HTTP bridge testing
  %(prog)s --transport http --base-url http://localhost:3000
  
  # With external test cases
  %(prog)s --transport stdio --server-cmd "my-server" --test-file tests/cases.json
  
  # Interactive mode
  %(prog)s --transport http --base-url http://localhost:3000 --interactive
  
  # Orchestrated testing (replaces test-master.sh)
  %(prog)s --orchestrate --config tests/orchestration.json
        """,
    )

    # Transport options
    parser.add_argument(
        "--transport", choices=["stdio", "http"], help="MCP transport type"
    )
    parser.add_argument("--server-cmd", help="Command to start stdio MCP server")
    parser.add_argument("--base-url", help="Base URL for HTTP MCP server")

    # Test options
    parser.add_argument("--test-file", help="JSON/YAML file with test case definitions")
    parser.add_argument(
        "--interactive", action="store_true", help="Run in interactive mode"
    )

    # Orchestration options
    parser.add_argument(
        "--orchestrate",
        action="store_true",
        help="Run orchestrated testing (replaces test-master.sh)",
    )
    parser.add_argument("--config", help="Configuration file for orchestrated testing")

    # Output options
    parser.add_argument("--quiet", action="store_true", help="Minimal output")
    parser.add_argument(
        "--timeout", type=int, default=30, help="Request timeout in seconds"
    )

    args = parser.parse_args()

    # Validate arguments
    if args.orchestrate:
        if not args.config:
            print("Error: --config required for orchestrated testing")
            return 1

        orchestrator = TestOrchestrator()
        success = await orchestrator.run_orchestrated_tests(args.config)
        return 0 if success else 1

    if not args.transport:
        print("Error: --transport required (stdio or http)")
        return 1

    if args.transport == "stdio" and not args.server_cmd:
        print("Error: --server-cmd required for stdio transport")
        return 1

    if args.transport == "http" and not args.base_url:
        print("Error: --base-url required for http transport")
        return 1

    # Create client
    transport = TransportType(args.transport)
    if transport == TransportType.STDIO:
        client = GenericMCPClient(
            transport, server_cmd=args.server_cmd, timeout=args.timeout
        )
    else:
        client = GenericMCPClient(
            transport, base_url=args.base_url, timeout=args.timeout
        )

    try:
        # Connect
        if not await client.connect():
            print("Failed to connect to MCP server")
            return 1

        if args.interactive:
            await interactive_mode(client)
            return 0

        # Create test suite
        test_suite = DynamicTestSuite(client)

        # Load or generate tests
        if args.test_file:
            test_cases = test_suite.load_test_cases(args.test_file)
        else:
            # Generate basic tests
            tools = await test_suite.discover_tools()
            test_cases = test_suite.generate_basic_tests(tools)

        # Run tests
        overall_success = True
        for suite_name, cases in test_cases.items():
            result = await test_suite.run_test_suite(suite_name, cases)

            if not args.quiet:
                print(f"\\nSuite: {result.suite_name}")
                print(f"Tests: {result.passed}/{result.total_tests} passed")
                if result.failed > 0:
                    print(f"Failed: {result.failed}")
                if result.errors > 0:
                    print(f"Errors: {result.errors}")
                print(f"Duration: {result.duration:.2f}s")

            if result.failed > 0 or result.errors > 0:
                overall_success = False

        return 0 if overall_success else 1

    finally:
        await client.disconnect()


if __name__ == "__main__":
    try:
        sys.exit(asyncio.run(main()))
    except KeyboardInterrupt:
        print("\\nInterrupted by user")
        sys.exit(0)
