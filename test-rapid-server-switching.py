#!/usr/bin/env python3
"""
Enhanced Rapid Server Switching Test - cycles between external and local nREPL servers
Tests the robustness of connection lifecycle, message queue management, and both
nrepl-eval and nrepl-load-file functionality during rapid connection switching
"""

import json
import subprocess
import sys
import time


def run_mcp_command(tool, args):
    """Run MCP command and return parsed JSON result"""
    cmd = [
        "uv",
        "run",
        "python",
        "scripts/mcp_nrepl_client.py",
        "--base-url",
        "http://localhost:3000/mcp",
        "--tool",
        tool,
        "--args",
        json.dumps(args),
        "--quiet",
    ]

    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
        if result.returncode != 0:
            print(f"❌ Command failed: {' '.join(cmd)}")
            print(f"   stderr: {result.stderr}")
            return None
        return json.loads(result.stdout)
    except subprocess.TimeoutExpired:
        print(f"⏰ Command timed out: {tool}")
        return None
    except json.JSONDecodeError as e:
        print(f"❌ JSON decode error for {tool}: {e}")
        print(f"   Raw output: {result.stdout}")
        return None
    except Exception as e:
        print(f"❌ Unexpected error for {tool}: {e}")
        return None


def test_eval(code, expected_type="success", server_name=""):
    """Test evaluation and return success/failure"""
    result = run_mcp_command("nrepl-eval", {"code": code})
    if not result:
        print(f"   ❌ {server_name} eval failed: {code}")
        return False

    if result.get("status") != expected_type:
        print(
            f"   ❌ {server_name} eval unexpected status: {result.get('status')} for {code}"
        )
        return False

    print(f"   ✅ {server_name} eval: {code} → {result.get('value', 'N/A')}")
    return True


def test_load_file(file_path, expected_type="success", server_name=""):
    """Test file loading and return success/failure"""
    result = run_mcp_command("nrepl-load-file", {"file-path": file_path})
    if not result:
        print(f"   ❌ {server_name} load-file failed: {file_path}")
        return False

    if result.get("status") != expected_type:
        print(
            f"   ❌ {server_name} load-file unexpected status: {result.get('status')} for {file_path}"
        )
        return False

    print(f"   ✅ {server_name} load-file: {file_path} → {result.get('value', 'N/A')}")
    return True


def test_cycle(cycle_num):
    """Run one complete cycle: external → eval + load-file → disconnect → local → eval + load-file → disconnect → external"""
    print(f"\n🔄 Cycle {cycle_num}")

    # Step 1: Connect to external server (58252)
    result = run_mcp_command(
        "nrepl-connection", {"op": "connect", "connection": "58252"}
    )
    if not result or result.get("status") != "success":
        print(f"   ❌ Failed to connect to external server")
        return False
    print(f"   🔗 Connected to external server: {result.get('connection-id', 'N/A')}")

    # Step 2: Eval on external server
    if not test_eval(
        f"(+ {cycle_num * 100} {cycle_num * 10} {cycle_num})", server_name="External"
    ):
        return False

    # Step 2b: Load-file on external server (alternating files based on cycle)
    test_file = (
        "test-complex-code.clj" if cycle_num % 2 == 1 else "test-stderr-capture.clj"
    )
    # For stderr-capture test, expect error status (stderr indicates error)
    expected_status = "error" if "stderr-capture" in test_file else "success"
    if not test_load_file(
        f"/Users/franksiebenlist/Development/mcp-nrepl-joyride/{test_file}",
        expected_type=expected_status,
        server_name="External",
    ):
        return False

    # Step 3: Disconnect from external
    result = run_mcp_command("nrepl-connection", {"op": "disconnect"})
    if not result or result.get("status") != "success":
        print(f"   ❌ Failed to disconnect from external server")
        return False
    print(f"   🔌 Disconnected from external server")

    # Step 4: Connect to local Babashka server (1667)
    result = run_mcp_command(
        "nrepl-connection", {"op": "connect", "connection": "1667"}
    )
    if not result or result.get("status") != "success":
        print(f"   ❌ Failed to connect to local server")
        return False
    print(f"   🔗 Connected to local server: {result.get('connection-id', 'N/A')}")

    # Step 5: Eval on local server
    if not test_eval(
        f"(* {cycle_num} {cycle_num + 1} {cycle_num + 2})", server_name="Local"
    ):
        return False

    # Step 5b: Load-file on local server (alternating files, opposite of external)
    test_file = (
        "test-stderr-capture.clj" if cycle_num % 2 == 1 else "test-complex-code.clj"
    )
    # For stderr-capture test, expect error status (stderr indicates error)
    expected_status = "error" if "stderr-capture" in test_file else "success"
    if not test_load_file(
        f"/Users/franksiebenlist/Development/mcp-nrepl-joyride/{test_file}",
        expected_type=expected_status,
        server_name="Local",
    ):
        return False

    # Step 6: Disconnect from local
    result = run_mcp_command("nrepl-connection", {"op": "disconnect"})
    if not result or result.get("status") != "success":
        print(f"   ❌ Failed to disconnect from local server")
        return False
    print(f"   🔌 Disconnected from local server")

    print(f"   ✅ Cycle {cycle_num} completed successfully")
    return True


def main():
    """Run 10 rapid server switching cycles with nrepl-eval and nrepl-load-file testing"""
    print("🚀 Starting Enhanced Rapid Server Switching Test")
    print("   Testing: External (58252) ↔ Local (1667) × 10 cycles")
    print("   Features: nrepl-eval + nrepl-load-file on both servers")

    # Check if local server is running
    result = run_mcp_command("local-nrepl-server", {"op": "status"})
    if not result or result.get("server-status") != "running":
        print("⚠️  Local nREPL server not running, starting it...")
        start_result = run_mcp_command("local-nrepl-server", {"op": "start"})
        if not start_result or start_result.get("status") != "success":
            print("❌ Failed to start local nREPL server")
            sys.exit(1)
        print(f"✅ Started local server on port {start_result.get('port')}")
        time.sleep(1)  # Give server time to start

    # Ensure we start disconnected
    print("\n🧹 Initial cleanup - disconnect if connected")
    run_mcp_command("nrepl-connection", {"op": "disconnect"})

    # Run 10 cycles
    successful_cycles = 0
    start_time = time.time()

    for i in range(1, 11):
        if test_cycle(i):
            successful_cycles += 1
        else:
            print(f"❌ Cycle {i} failed - stopping test")
            break

    end_time = time.time()
    duration = end_time - start_time

    # Final cleanup
    print("\n🧹 Final cleanup")
    run_mcp_command("nrepl-connection", {"op": "disconnect"})

    # Results
    print(f"\n📊 Test Results")
    print(f"   Successful cycles: {successful_cycles}/10")
    print(f"   Total duration: {duration:.2f} seconds")
    print(f"   Average per cycle: {duration/10:.2f} seconds")

    if successful_cycles == 10:
        print("🎉 All cycles completed successfully!")
        sys.exit(0)
    else:
        print(f"💥 {10 - successful_cycles} cycles failed")
        sys.exit(1)


if __name__ == "__main__":
    main()
