#!/usr/bin/env python3
"""
Full Clojure nREPL Test Server Manager

Provides start/stop/restart functionality for the full Clojure nREPL test
server with complete async capabilities (promises, futures, Java interop).

Usage:
    python3 nrepl_test_server.py start
    python3 nrepl_test_server.py stop
    python3 nrepl_test_server.py restart
    python3 nrepl_test_server.py status
"""

import os
import sys
import json
import time
import signal
import subprocess
from pathlib import Path
from typing import Optional, Dict, Any


class NReplTestServer:
    def __init__(self):
        self.project_root = Path(__file__).parent
        self.test_nrepl_dir = self.project_root / "test-nrepl"
        self.server_info_file = self.project_root / ".nrepl-test-server.json"
        self.port_file = self.test_nrepl_dir / ".test-nrepl-server-port"

    def get_server_info(self) -> Optional[Dict[str, Any]]:
        """Get server information from JSON file."""
        if not self.server_info_file.exists():
            return None

        try:
            with open(self.server_info_file, "r") as f:
                return json.load(f)
        except (json.JSONDecodeError, OSError):
            return None

    def save_server_info(self, pid: int, port: int) -> None:
        """Save server information to JSON file."""
        info = {
            "pid": pid,
            "port": port,
            "started_at": time.time(),
            "status": "running",
        }

        with open(self.server_info_file, "w") as f:
            json.dump(info, f, indent=2)

        print(f"📝 Server info saved: PID={pid}, Port={port}")

    def cleanup_files(self) -> None:
        """Clean up server info and port files."""
        for file_path in [self.server_info_file, self.port_file]:
            if file_path.exists():
                file_path.unlink()
                print(f"🗑️  Cleaned up: {file_path.name}")

    def is_process_running(self, pid: int) -> bool:
        """Check if process is still running."""
        try:
            os.kill(pid, 0)  # Signal 0 just checks if process exists
            return True
        except OSError:
            return False

    def wait_for_port_file(self, timeout: int = 10) -> Optional[int]:
        """Wait for .nrepl-port file to be created and return port."""
        start_time = time.time()

        while time.time() - start_time < timeout:
            if self.port_file.exists():
                try:
                    port = int(self.port_file.read_text().strip())
                    return port
                except (ValueError, OSError):
                    pass
            time.sleep(0.1)

        return None

    def start_server(self) -> bool:
        """Start the nREPL test server."""
        # Check if already running
        info = self.get_server_info()
        if info and self.is_process_running(info["pid"]):
            print(f"⚠️  Server already running: PID={info['pid']}, "
                  f"Port={info['port']}")
            return True

        # Clean up any stale files
        self.cleanup_files()

        # Start server process
        print("🚀 Starting Full Clojure nREPL Test Server...")
        print("   (Supports promises, futures, full Java interop - NOT SCI)")

        try:
            process = subprocess.Popen(
                ["clj", "-M:test-server"],
                cwd=self.test_nrepl_dir,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
            )

            # Wait for port file to be created
            port = self.wait_for_port_file(timeout=15)
            if not port:
                print("❌ Failed to start server - no port file created")
                process.terminate()
                return False

            # Verify process is still running
            if process.poll() is not None:
                print("❌ Server process terminated unexpectedly")
                stdout, stderr = process.communicate()
                if stderr:
                    print(f"Error: {stderr}")
                return False

            # Save server information
            self.save_server_info(process.pid, port)

            print("✅ Server started successfully!")
            print(f"   PID: {process.pid}")
            print(f"   Port: {port}")
            print(f"   Connect: clj -M -m nrepl.cmdline --connect "
                  f"--port {port}")
            print("   Or use: python3 ./mcp_nrepl_client.py --status")
            print()
            print("💡 This server supports FULL Clojure capabilities:")
            print("   - Promises with timeouts: "
                  "(deref (promise) 100 :timeout)")
            print("   - Futures with background execution")
            print("   - Complete Java interoperability")
            print("   - All concurrent programming primitives")

            return True

        except FileNotFoundError:
            print("❌ Error: 'clj' command not found. "
                  "Please install Clojure CLI tools.")
            return False
        except Exception as e:
            print(f"❌ Error starting server: {e}")
            return False

    def stop_server(self) -> bool:
        """Stop the nREPL test server."""
        info = self.get_server_info()
        if not info:
            print("ℹ️  No server info found - server may not be running")
            return True

        pid = info["pid"]
        if not self.is_process_running(pid):
            print(f"ℹ️  Process {pid} not running - cleaning up files")
            self.cleanup_files()
            return True

        print(f"🛑 Stopping nREPL server (PID: {pid})...")

        try:
            # Try graceful shutdown first
            os.kill(pid, signal.SIGTERM)

            # Wait for process to terminate
            for _ in range(50):  # Wait up to 5 seconds
                if not self.is_process_running(pid):
                    break
                time.sleep(0.1)

            # Force kill if still running
            if self.is_process_running(pid):
                print("⚡ Force killing server process...")
                os.kill(pid, signal.SIGKILL)
                time.sleep(0.5)

            if self.is_process_running(pid):
                print(f"❌ Failed to stop process {pid}")
                return False

            self.cleanup_files()
            print("✅ Server stopped successfully")
            return True

        except OSError as e:
            print(f"❌ Error stopping server: {e}")
            return False

    def restart_server(self) -> bool:
        """Restart the nREPL test server."""
        print("🔄 Restarting nREPL test server...")
        self.stop_server()
        time.sleep(1)  # Brief pause between stop and start
        return self.start_server()

    def show_status(self) -> None:
        """Show server status information."""
        print("📊 nREPL Test Server Status")
        print("=" * 40)

        info = self.get_server_info()
        if not info:
            print("Status: ❌ Not running (no server info)")
            return

        pid = info["pid"]
        port = info["port"]
        started_at = info.get("started_at", 0)

        if self.is_process_running(pid):
            uptime = time.time() - started_at
            uptime_str = (f"{uptime:.1f}s" if uptime < 60
                          else f"{uptime/60:.1f}m")

            print("Status: ✅ Running")
            print(f"PID: {pid}")
            print(f"Port: {port}")
            print(f"Uptime: {uptime_str}")
            print(f"Port file: {self.port_file}")
            print(f"Info file: {self.server_info_file}")
            print()
            print("🔗 Connection commands:")
            print(f"   clj -M -m nrepl.cmdline --connect --port {port}")
            print("   python3 ./mcp_nrepl_client.py --status")
            print()
            print("💡 Full Clojure capabilities available:")
            print("   - Promises, futures, Java interop")
            print("   - All concurrent programming primitives")
        else:
            print(f"Status: ❌ Not running (PID {pid} not found)")
            print("Cleaning up stale files...")
            self.cleanup_files()

    def run_command(self, command: str) -> bool:
        """Run the specified command."""
        if command == "start":
            return self.start_server()
        elif command == "stop":
            return self.stop_server()
        elif command == "restart":
            return self.restart_server()
        elif command == "status":
            self.show_status()
            return True
        else:
            print(f"❌ Unknown command: {command}")
            print("Usage: python3 nrepl_test_server.py "
                  "[start|stop|restart|status]")
            return False


def main():
    if len(sys.argv) != 2:
        print("Usage: python3 nrepl_test_server.py "
              "[start|stop|restart|status]")
        print()
        print("Commands:")
        print("  start   - Start the Full Clojure nREPL test server")
        print("  stop    - Stop the running server")
        print("  restart - Stop and start the server")
        print("  status  - Show server status and connection info")
        print()
        print("Features:")
        print("  ✅ Full JVM Clojure environment (not SCI)")
        print("  ✅ Promises, futures, complete Java interop")
        print("  ✅ Auto-assigns ephemeral port")
        print("  ✅ Creates .nrepl-port file for discovery")
        print("  ✅ Process management with PID tracking")
        sys.exit(1)

    command = sys.argv[1].lower()
    server = NReplTestServer()

    success = server.run_command(command)
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
