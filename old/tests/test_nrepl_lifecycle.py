#!/usr/bin/env python3
"""
Comprehensive nREPL Lifecycle and Functionality Test Suite

Tests the complete nREPL ecosystem including:
- Server lifecycle management (start/stop/restart)
- MCP proxy functionality
- Basic nREPL operations
- Full Clojure capabilities (promises, futures, Java interop)
- Error handling and edge cases

Usage:
    uv run python test_nrepl_lifecycle.py
    uv run python test_nrepl_lifecycle.py --quick    # Skip long-running tests
    uv run python test_nrepl_lifecycle.py --server-only  # Test server lifecycle only
"""

import os
import sys
import time
import json
import subprocess
from pathlib import Path
from typing import List
from dataclasses import dataclass

# Import port utilities for dynamic port allocation
sys.path.insert(0, str(Path(__file__).parent))
from port_utils import get_test_mcp_port


@dataclass
class TestResult:
    name: str
    success: bool
    duration: float
    details: str


class NReplTestSuite:
    def __init__(self, quick: bool = False, server_only: bool = False):
        self.project_root = Path(__file__).parent
        self.quick = quick
        self.server_only = server_only
        self.results: List[TestResult] = []

    def log(self, msg: str, level: str = "INFO"):
        """Log test messages with timestamps"""
        timestamp = time.strftime("%H:%M:%S")
        prefix = {"INFO": "ℹ️ ", "SUCCESS": "✅", "ERROR": "❌", "WARN": "⚠️ "}
        print(f"[{timestamp}] {prefix.get(level, '')} {msg}")

    def run_command(
        self, cmd: List[str], timeout: int = 10, capture: bool = True
    ) -> subprocess.CompletedProcess:
        """Run command with timeout and error handling"""
        try:
            if capture:
                return subprocess.run(
                    cmd,
                    capture_output=True,
                    text=True,
                    timeout=timeout,
                    cwd=self.project_root,
                )
            else:
                return subprocess.run(cmd, timeout=timeout, cwd=self.project_root)
        except subprocess.TimeoutExpired:
            raise Exception(f"Command timed out after {timeout}s: {' '.join(cmd)}")
        except FileNotFoundError:
            raise Exception(f"Command not found: {cmd[0]}")

    def test_server_lifecycle(self) -> List[TestResult]:
        """Test complete server lifecycle management"""
        tests = []

        # Test 1: Server start
        start_time = time.time()
        try:
            self.log("Testing server start...")
            result = self.run_command(
                ["uv", "run", "python", "nrepl_test_server.py", "start"]
            )
            if (
                result.returncode == 0
                and "Server started successfully" in result.stdout
            ):
                duration = time.time() - start_time
                tests.append(
                    TestResult(
                        "server_start",
                        True,
                        duration,
                        f"Server started in {duration:.1f}s",
                    )
                )
                self.log("Server start: SUCCESS", "SUCCESS")
            else:
                tests.append(
                    TestResult(
                        "server_start",
                        False,
                        time.time() - start_time,
                        f"Failed: {result.stderr}",
                    )
                )
                self.log("Server start: FAILED", "ERROR")
                return tests
        except Exception as e:
            tests.append(
                TestResult("server_start", False, time.time() - start_time, str(e))
            )
            self.log(f"Server start: ERROR - {e}", "ERROR")
            return tests

        # Test 2: Server status check
        start_time = time.time()
        try:
            self.log("Testing server status...")
            time.sleep(1)  # Brief pause for server to stabilize
            result = self.run_command(
                ["uv", "run", "python", "nrepl_test_server.py", "status"]
            )
            if result.returncode == 0 and "Status: ✅ Running" in result.stdout:
                duration = time.time() - start_time
                tests.append(
                    TestResult(
                        "server_status", True, duration, "Status check successful"
                    )
                )
                self.log("Server status: SUCCESS", "SUCCESS")
            else:
                tests.append(
                    TestResult(
                        "server_status",
                        False,
                        time.time() - start_time,
                        f"Status check failed: {result.stdout}",
                    )
                )
                self.log("Server status: FAILED", "ERROR")
        except Exception as e:
            tests.append(
                TestResult("server_status", False, time.time() - start_time, str(e))
            )
            self.log(f"Server status: ERROR - {e}", "ERROR")

        # Test 3: Port file creation
        start_time = time.time()
        try:
            self.log("Testing port file creation...")
            port_file = self.project_root / "test-nrepl" / ".test-nrepl-server-port"
            info_file = self.project_root / ".nrepl-test-server.json"

            if port_file.exists() and info_file.exists():
                # Verify port file contents
                port = int(port_file.read_text().strip())

                # Verify info file contents
                info = json.loads(info_file.read_text())
                required_keys = ["pid", "port", "started_at", "status"]

                if all(key in info for key in required_keys) and info["port"] == port:
                    duration = time.time() - start_time
                    tests.append(
                        TestResult(
                            "port_files",
                            True,
                            duration,
                            f"Port files created correctly (port: {port})",
                        )
                    )
                    self.log("Port files: SUCCESS", "SUCCESS")
                else:
                    tests.append(
                        TestResult(
                            "port_files",
                            False,
                            time.time() - start_time,
                            "Info file missing required keys",
                        )
                    )
                    self.log("Port files: FAILED - invalid info file", "ERROR")
            else:
                tests.append(
                    TestResult(
                        "port_files",
                        False,
                        time.time() - start_time,
                        "Port files not created",
                    )
                )
                self.log("Port files: FAILED - files not found", "ERROR")
        except Exception as e:
            tests.append(
                TestResult("port_files", False, time.time() - start_time, str(e))
            )
            self.log(f"Port files: ERROR - {e}", "ERROR")

        # Test 4: Server restart
        start_time = time.time()
        try:
            self.log("Testing server restart...")
            result = self.run_command(
                ["uv", "run", "python", "nrepl_test_server.py", "restart"]
            )
            if (
                result.returncode == 0
                and "Server started successfully" in result.stdout
            ):
                duration = time.time() - start_time
                tests.append(
                    TestResult(
                        "server_restart",
                        True,
                        duration,
                        f"Server restarted in {duration:.1f}s",
                    )
                )
                self.log("Server restart: SUCCESS", "SUCCESS")
            else:
                tests.append(
                    TestResult(
                        "server_restart",
                        False,
                        time.time() - start_time,
                        f"Restart failed: {result.stderr}",
                    )
                )
                self.log("Server restart: FAILED", "ERROR")
        except Exception as e:
            tests.append(
                TestResult("server_restart", False, time.time() - start_time, str(e))
            )
            self.log(f"Server restart: ERROR - {e}", "ERROR")

        # Test 5: Server stop
        start_time = time.time()
        try:
            self.log("Testing server stop...")
            result = self.run_command(
                ["uv", "run", "python", "nrepl_test_server.py", "stop"]
            )
            if (
                result.returncode == 0
                and "Server stopped successfully" in result.stdout
            ):
                duration = time.time() - start_time
                tests.append(
                    TestResult(
                        "server_stop",
                        True,
                        duration,
                        f"Server stopped in {duration:.1f}s",
                    )
                )
                self.log("Server stop: SUCCESS", "SUCCESS")

                # Verify cleanup
                time.sleep(0.5)
                info_file = self.project_root / ".nrepl-test-server.json"
                if not info_file.exists():
                    self.log("Cleanup verified: info file removed", "SUCCESS")
                else:
                    self.log("Cleanup incomplete: info file still exists", "WARN")
            else:
                tests.append(
                    TestResult(
                        "server_stop",
                        False,
                        time.time() - start_time,
                        f"Stop failed: {result.stderr}",
                    )
                )
                self.log("Server stop: FAILED", "ERROR")
        except Exception as e:
            tests.append(
                TestResult("server_stop", False, time.time() - start_time, str(e))
            )
            self.log(f"Server stop: ERROR - {e}", "ERROR")

        return tests

    def test_mcp_integration(self) -> List[TestResult]:
        """Test MCP proxy integration with nREPL server"""
        tests = []

        if self.server_only:
            self.log("Skipping MCP tests (server-only mode)")
            return tests

        # Start server for MCP tests
        self.log("Starting server for MCP integration tests...")
        start_result = self.run_command(
            ["uv", "run", "python", "nrepl_test_server.py", "start"]
        )
        if start_result.returncode != 0:
            self.log("Failed to start server for MCP tests", "ERROR")
            return [
                TestResult("mcp_server_start", False, 0, "Could not start test server")
            ]

        time.sleep(2)  # Allow server to stabilize

        # Get the test server port
        port_file = self.project_root / "test-nrepl" / ".test-nrepl-server-port"
        if not port_file.exists():
            self.log(
                "Test server port file not found, cannot start MCP server", "ERROR"
            )
            return [TestResult("mcp_no_port", False, 0, "No test server port file")]

        nrepl_port = int(port_file.read_text().strip())

        # Get free port for MCP server to avoid conflicts with bb-nrepl-server
        mcp_port = get_test_mcp_port()
        self.log(f"Using MCP HTTP port: {mcp_port} (dynamically allocated)")

        # Start MCP server (no auto-connection)
        mcp_process = None
        try:
            self.log("Starting MCP proxy server (no auto-connection)...")
            env = os.environ.copy()
            env["BABASHKA_CLASSPATH"] = "src"
            env["MCP_HTTP_PORT"] = str(mcp_port)

            mcp_process = subprocess.Popen(
                ["bb", "src/mcp_nrepl_proxy/core.clj"],
                cwd=self.project_root,
                env=env,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
            )
            time.sleep(3)  # Allow MCP server to start

            # Explicitly connect to nREPL server using MCP tool
            self.log(f"Connecting MCP proxy to nREPL server on port {nrepl_port}...")
            connect_result = self.run_command(
                [
                    "uv",
                    "run",
                    "python",
                    "./mcp_nrepl_client.py",
                    "--url",
                    f"http://localhost:{mcp_port}/mcp",
                    "--tool",
                    "nrepl-connect",
                    "--args",
                    f'{{"port": {nrepl_port}}}',
                    "--quiet",
                ],
                timeout=15,
            )

            if connect_result.returncode != 0:
                self.log(
                    f"❌ Failed to connect to nREPL: {connect_result.stderr}", "ERROR"
                )
                return [
                    TestResult(
                        "nrepl_connect",
                        False,
                        0,
                        f"Connect failed: {connect_result.stderr}",
                    )
                ]
            else:
                self.log("✅ Successfully connected to nREPL server")

            # Test 1: MCP health check
            start_time = time.time()
            try:
                self.log("Testing MCP health check...")
                result = self.run_command(
                    [
                        "uv",
                        "run",
                        "python",
                        "./mcp_nrepl_client.py",
                        "--url",
                        f"http://localhost:{mcp_port}/mcp",
                        "--status",
                        "--quiet",
                    ],
                    timeout=15,
                )

                if result.returncode == 0:
                    duration = time.time() - start_time
                    tests.append(
                        TestResult(
                            "mcp_health", True, duration, "MCP health check passed"
                        )
                    )
                    self.log("MCP health: SUCCESS", "SUCCESS")
                else:
                    tests.append(
                        TestResult(
                            "mcp_health",
                            False,
                            time.time() - start_time,
                            f"Health check failed: {result.stderr}",
                        )
                    )
                    self.log("MCP health: FAILED", "ERROR")
            except Exception as e:
                tests.append(
                    TestResult("mcp_health", False, time.time() - start_time, str(e))
                )
                self.log(f"MCP health: ERROR - {e}", "ERROR")

            # Test 2: Basic evaluation
            start_time = time.time()
            try:
                self.log("Testing basic nREPL evaluation...")
                result = self.run_command(
                    [
                        "uv",
                        "run",
                        "python",
                        "./mcp_nrepl_client.py",
                        "--url",
                        f"http://localhost:{mcp_port}/mcp",
                        "--eval",
                        "(+ 1 2 3)",
                    ],
                    timeout=15,
                )

                if result.returncode == 0 and "6" in result.stdout:
                    duration = time.time() - start_time
                    tests.append(
                        TestResult(
                            "basic_eval", True, duration, "Basic evaluation works"
                        )
                    )
                    self.log("Basic eval: SUCCESS", "SUCCESS")
                else:
                    tests.append(
                        TestResult(
                            "basic_eval",
                            False,
                            time.time() - start_time,
                            f"Evaluation failed: {result.stdout} " f"{result.stderr}",
                        )
                    )
                    self.log("Basic eval: FAILED", "ERROR")
            except Exception as e:
                tests.append(
                    TestResult("basic_eval", False, time.time() - start_time, str(e))
                )
                self.log(f"Basic eval: ERROR - {e}", "ERROR")

            # Test 3: Full Clojure capabilities (if not quick mode)
            if not self.quick:
                clojure_tests = [
                    (
                        "promises",
                        "(let [p (promise)] (deliver p :test) (deref p))",
                        ":test",
                    ),
                    ("promise_timeout", "(deref (promise) 100 :timeout)", ":timeout"),
                    ("futures", "@(future (+ 1 2 3))", "6"),
                    ("java_interop", "(> (System/currentTimeMillis) 0)", "true"),
                ]

                for test_name, code, expected in clojure_tests:
                    start_time = time.time()
                    try:
                        self.log(f"Testing {test_name}...")
                        result = self.run_command(
                            [
                                "uv",
                                "run",
                                "python",
                                "./mcp_nrepl_client.py",
                                "--url",
                                f"http://localhost:{mcp_port}/mcp",
                                "--eval",
                                code,
                            ],
                            timeout=20,
                        )

                        if result.returncode == 0 and expected in result.stdout:
                            duration = time.time() - start_time
                            tests.append(
                                TestResult(
                                    f"clojure_{test_name}",
                                    True,
                                    duration,
                                    f"{test_name} capability works",
                                )
                            )
                            self.log(f"{test_name}: SUCCESS", "SUCCESS")
                        else:
                            tests.append(
                                TestResult(
                                    f"clojure_{test_name}",
                                    False,
                                    time.time() - start_time,
                                    f"{test_name} failed: {result.stdout} "
                                    f"{result.stderr}",
                                )
                            )
                            self.log(f"{test_name}: FAILED", "ERROR")
                    except Exception as e:
                        tests.append(
                            TestResult(
                                f"clojure_{test_name}",
                                False,
                                time.time() - start_time,
                                str(e),
                            )
                        )
                        self.log(f"{test_name}: ERROR - {e}", "ERROR")

        finally:
            # Cleanup MCP process
            if mcp_process:
                try:
                    mcp_process.terminate()
                    mcp_process.wait(timeout=5)
                    self.log("MCP server stopped")
                except (subprocess.TimeoutExpired, OSError):
                    mcp_process.kill()
                    self.log("MCP server force killed")

            # Stop test server
            self.run_command(["uv", "run", "python", "nrepl_test_server.py", "stop"])
            self.log("Test server stopped")

        return tests

    def run_all_tests(self) -> None:
        """Run complete test suite"""
        self.log("🚀 Starting nREPL Lifecycle and Functionality Test Suite")
        self.log(f"Mode: {'Quick' if self.quick else 'Full'}")
        if self.server_only:
            self.log("Mode: Server lifecycle only")

        start_time = time.time()

        # Run server lifecycle tests
        self.log("\n=== Testing Server Lifecycle Management ===")
        lifecycle_results = self.test_server_lifecycle()
        self.results.extend(lifecycle_results)

        # Run MCP integration tests
        if not self.server_only:
            self.log("\n=== Testing MCP Integration ===")
            mcp_results = self.test_mcp_integration()
            self.results.extend(mcp_results)

        # Print summary
        total_time = time.time() - start_time
        self.print_summary(total_time)

    def print_summary(self, total_time: float) -> None:
        """Print test results summary"""
        passed = sum(1 for r in self.results if r.success)
        failed = len(self.results) - passed

        self.log(f"\n{'='*60}")
        self.log("📊 TEST RESULTS SUMMARY")
        self.log(f"{'='*60}")

        self.log(f"Total Tests: {len(self.results)}")
        self.log(f"Passed: {passed} ✅")
        self.log(f"Failed: {failed} ❌")
        self.log(f"Success Rate: {passed/len(self.results)*100:.1f}%")
        self.log(f"Total Time: {total_time:.1f}s")

        if failed > 0:
            self.log("\n❌ FAILED TESTS:")
            for result in self.results:
                if not result.success:
                    self.log(f"  • {result.name}: {result.details}")

        self.log("\n✅ PASSED TESTS:")
        for result in self.results:
            if result.success:
                self.log(f"  • {result.name}: {result.details}")

        if failed == 0:
            self.log("\n🎉 ALL TESTS PASSED! nREPL system is working " "correctly.")
        else:
            self.log(f"\n⚠️  {failed} test(s) failed. Check the details " "above.")


def main():
    import argparse

    parser = argparse.ArgumentParser(
        description="nREPL Lifecycle and Functionality Test Suite"
    )
    parser.add_argument(
        "--quick",
        action="store_true",
        help="Skip long-running tests (promises, futures)",
    )
    parser.add_argument(
        "--server-only",
        action="store_true",
        help="Test only server lifecycle, skip MCP integration",
    )

    args = parser.parse_args()

    suite = NReplTestSuite(quick=args.quick, server_only=args.server_only)
    suite.run_all_tests()

    # Exit with error code if any tests failed
    failed_count = sum(1 for r in suite.results if not r.success)
    sys.exit(0 if failed_count == 0 else 1)


if __name__ == "__main__":
    main()
