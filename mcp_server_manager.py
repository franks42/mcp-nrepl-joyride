#!/usr/bin/env python3
"""
MCP-nREPL Server Management Script

Handles starting, stopping, testing, and monitoring the MCP-nREPL server
without requiring user confirmations for each command.
"""

import subprocess
import time
import os
import signal
import sys
import argparse
import urllib.request
import urllib.error
from pathlib import Path


class MCPServerManager:
    def __init__(self, port=3004, debug=True):
        self.port = port
        self.debug = debug
        self.server_process = None
        self.base_dir = Path(__file__).parent
        self.server_script = self.base_dir / "src/mcp_nrepl_proxy/core.clj"
        self.client_script = self.base_dir / "mcp_nrepl_client.py"

    def get_env(self):
        """Get environment variables for server startup"""
        env = os.environ.copy()
        env.update(
            {
                "BABASHKA_CLASSPATH": "src",
                "MCP_HTTP_PORT": str(self.port),
                "MCP_DEBUG": str(self.debug).lower(),
            }
        )
        return env

    def find_server_pid(self):
        """Find running server process PID"""
        try:
            result = subprocess.run(
                ["ps", "aux"], capture_output=True, text=True, check=True
            )
            for line in result.stdout.split("\n"):
                if "bb" in line and "mcp_nrepl_proxy" in line:
                    parts = line.split()
                    if len(parts) > 1:
                        try:
                            return int(parts[1])
                        except ValueError:
                            continue
            return None
        except subprocess.CalledProcessError:
            return None

    def is_server_running(self):
        """Check if server is responding"""
        try:
            url = f"http://localhost:{self.port}/health"
            req = urllib.request.Request(url)
            with urllib.request.urlopen(req, timeout=2) as response:
                return response.status == 200
        except (urllib.error.URLError, OSError):
            return False

    def start_server(self, background=True):
        """Start the MCP server"""
        if self.is_server_running():
            print(f"✅ Server already running on port {self.port}")
            return True

        print(f"🚀 Starting MCP-nREPL server on port {self.port}...")

        cmd = ["bb", str(self.server_script)]
        env = self.get_env()

        if background:
            # Start in background
            log_file = self.base_dir / f"server_{self.port}.log"
            with open(log_file, "w") as f:
                self.server_process = subprocess.Popen(
                    cmd,
                    env=env,
                    stdout=f,
                    stderr=subprocess.STDOUT,
                    cwd=self.base_dir,
                )

            # Wait for server to start
            for i in range(10):
                time.sleep(1)
                if self.is_server_running():
                    pid = self.server_process.pid
                    print(f"✅ Server started on port {self.port} (PID: {pid})")
                    print(f"📋 Logs: {log_file}")
                    mcp_url = f"http://localhost:{self.port}/mcp"
                    health_url = f"http://localhost:{self.port}/health"
                    print(f"🔗 MCP endpoint: {mcp_url}")
                    print(f"💚 Health check: {health_url}")
                    return True

            print("❌ Server failed to start within 10 seconds")
            return False
        else:
            # Start in foreground
            try:
                subprocess.run(cmd, env=env, cwd=self.base_dir)
            except KeyboardInterrupt:
                print("\n🛑 Server stopped by user")
            return True

    def stop_server(self):
        """Stop the MCP server"""
        pid = self.find_server_pid()
        if pid:
            try:
                os.kill(pid, signal.SIGTERM)
                time.sleep(2)
                # Force kill if still running
                try:
                    os.kill(pid, signal.SIGKILL)
                    print(f"🛑 Force stopped server (PID: {pid})")
                except ProcessLookupError:
                    print(f"✅ Server stopped gracefully (PID: {pid})")
                return True
            except ProcessLookupError:
                print("❌ Server process not found")
                return False
        else:
            print("ℹ️  No server process found")
            return True

    def restart_server(self):
        """Restart the server"""
        print("🔄 Restarting server...")
        self.stop_server()
        time.sleep(2)
        return self.start_server()

    def server_status(self):
        """Get server status"""
        pid = self.find_server_pid()
        running = self.is_server_running()

        print("📊 Server Status:")
        print(f"   Port: {self.port}")
        print(f"   PID: {pid if pid else 'Not found'}")
        status = "✅ Responding" if running else "❌ Not responding"
        print(f"   HTTP Health: {status}")

        if running:
            try:
                url = f"http://localhost:{self.port}/health"
                req = urllib.request.Request(url)
                with urllib.request.urlopen(req, timeout=2) as response:
                    import json

                    health_data = json.loads(response.read().decode())
                    connected = health_data.get("nrepl-connected")
                    nrepl_status = "✅" if connected else "❌"
                    print(f"   nREPL Connected: {nrepl_status}")
            except (urllib.error.URLError, OSError):
                print("   Health Details: ❌ Unable to fetch")

        return running and pid is not None

    def run_health_check(self):
        """Run comprehensive health check"""
        if not self.is_server_running():
            msg = (
                "❌ Server not running. Start server first with: "
                "python3 mcp_server_manager.py start"
            )
            print(msg)
            return False

        print("🏥 Running comprehensive health check...")
        cmd = [
            "uv",
            "run",
            "python",
            str(self.client_script),
            "--url",
            f"http://localhost:{self.port}/mcp",
            "--tool",
            "nrepl-health-check",
            "--quiet",
        ]
        env = os.environ.copy()
        env["MCP_SERVER_URL"] = f"http://localhost:{self.port}/mcp"

        try:
            result = subprocess.run(
                cmd, capture_output=True, text=True, env=env, cwd=self.base_dir
            )
            if result.returncode == 0:
                print(result.stdout)
                return True
            else:
                print(f"❌ Health check failed:")
                print(f"Return code: {result.returncode}")
                print(f"STDOUT: {result.stdout}")
                print(f"STDERR: {result.stderr}")
                return False
        except Exception as e:
            print(f"❌ Health check error: {e}")
            return False

    def run_basic_tests(self):
        """Run basic functionality tests"""
        if not self.is_server_running():
            print("❌ Server not running. Start server first.")
            return False

        print("🧪 Running basic tests...")

        tests = [
            {
                "name": "Basic Math",
                "cmd": [
                    "uv",
                    "run",
                    "python",
                    str(self.client_script),
                    "--url",
                    f"http://localhost:{self.port}/mcp",
                    "--eval",
                    "(+ 2 3)",
                    "--quiet",
                ],
            },
            {
                "name": "String Operations",
                "cmd": [
                    "uv",
                    "run",
                    "python",
                    str(self.client_script),
                    "--url",
                    f"http://localhost:{self.port}/mcp",
                    "--eval",
                    '(str "hello" " " "world")',
                    "--quiet",
                ],
            },
            {
                "name": "Server Status",
                "cmd": [
                    "uv",
                    "run",
                    "python",
                    str(self.client_script),
                    "--url",
                    f"http://localhost:{self.port}/mcp",
                    "--status",
                    "--quiet",
                ],
            },
        ]

        env = os.environ.copy()
        env["MCP_SERVER_URL"] = f"http://localhost:{self.port}/mcp"

        passed = 0
        for test in tests:
            try:
                result = subprocess.run(
                    test["cmd"],
                    capture_output=True,
                    text=True,
                    env=env,
                    cwd=self.base_dir,
                    timeout=10,
                )
                if result.returncode == 0:
                    print(f"✅ {test['name']}: PASS")
                    passed += 1
                else:
                    print(f"❌ {test['name']}: FAIL - {result.stderr}")
            except subprocess.TimeoutExpired:
                print(f"⏰ {test['name']}: TIMEOUT")
            except Exception as e:
                print(f"❌ {test['name']}: ERROR - {e}")

        print(f"\n📊 Test Results: {passed}/{len(tests)} passed")
        return passed == len(tests)

    def show_tools(self):
        """Show available MCP tools"""
        if not self.is_server_running():
            print("❌ Server not running. Start server first.")
            return False

        print("🔧 Available MCP Tools:")
        cmd = [
            "uv",
            "run",
            "python",
            str(self.client_script),
            "--url",
            f"http://localhost:{self.port}/mcp",
            "--tools",
            "--format",
            "json",
            "--quiet",
        ]
        env = os.environ.copy()
        env["MCP_SERVER_URL"] = f"http://localhost:{self.port}/mcp"

        try:
            result = subprocess.run(
                cmd, capture_output=True, text=True, env=env, cwd=self.base_dir
            )
            if result.returncode == 0:
                print(result.stdout)
                return True
            else:
                print(f"❌ Failed to get tools:\n{result.stderr}")
                return False
        except Exception as e:
            print(f"❌ Error getting tools: {e}")
            return False


def main():
    parser = argparse.ArgumentParser(description="MCP-nREPL Server Manager")
    parser.add_argument(
        "command",
        choices=[
            "start",
            "stop",
            "restart",
            "status",
            "health",
            "test",
            "tools",
            "run",
        ],
        help="Command to execute",
    )
    parser.add_argument(
        "--port", type=int, default=3004, help="Server port (default: 3004)"
    )
    parser.add_argument("--no-debug", action="store_true", help="Disable debug logging")
    parser.add_argument(
        "--foreground", action="store_true", help="Run server in foreground"
    )

    args = parser.parse_args()

    manager = MCPServerManager(port=args.port, debug=not args.no_debug)

    if args.command == "start":
        success = manager.start_server(background=not args.foreground)
        sys.exit(0 if success else 1)
    elif args.command == "stop":
        success = manager.stop_server()
        sys.exit(0 if success else 1)
    elif args.command == "restart":
        success = manager.restart_server()
        sys.exit(0 if success else 1)
    elif args.command == "status":
        success = manager.server_status()
        sys.exit(0 if success else 1)
    elif args.command == "health":
        success = manager.run_health_check()
        sys.exit(0 if success else 1)
    elif args.command == "test":
        success = manager.run_basic_tests()
        sys.exit(0 if success else 1)
    elif args.command == "tools":
        success = manager.show_tools()
        sys.exit(0 if success else 1)
    elif args.command == "run":
        # Full workflow: start, test, health check
        print("🚀 Full test workflow...")
        if not manager.start_server():
            print("❌ Failed to start server")
            sys.exit(1)

        time.sleep(2)  # Let server stabilize

        if not manager.run_basic_tests():
            print("❌ Basic tests failed")
            sys.exit(1)

        if not manager.run_health_check():
            print("❌ Health check failed")
            sys.exit(1)

        print("✅ All tests passed! Server is ready.")
        sys.exit(0)


if __name__ == "__main__":
    main()
