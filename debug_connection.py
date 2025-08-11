#!/usr/bin/env python3
"""
Debug script to isolate broken pipe issues.
Tests connection behavior step by step.
"""

import time
import subprocess
import requests
from pathlib import Path
from port_utils import get_test_mcp_port


def log(msg):
    """Simple logging with timestamp"""
    timestamp = time.strftime("%H:%M:%S")
    print(f"[{timestamp}] {msg}")


def run_mcp_eval(code, mcp_port):
    """Run a single MCP eval with detailed logging"""
    log(f"🧪 Testing evaluation: {code}")

    # Prepare the MCP request
    mcp_request = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "tools/call",
        "params": {"name": "nrepl-eval", "arguments": {"code": code}},
    }

    try:
        log("📤 Sending MCP request...")
        start_time = time.time()

        url = f"http://localhost:{mcp_port}/mcp"
        response = requests.post(
            url,
            json=mcp_request,
            headers={"Content-Type": "application/json"},
            timeout=10,
        )

        duration = time.time() - start_time
        log(f"📥 Response received in {duration:.2f}s (status: {response.status_code})")

        if response.status_code == 200:
            result = response.json()
            if "error" in result:
                log(f"❌ MCP Error: {result['error']}")
                return False
            elif "result" in result and "content" in result["result"]:
                content = result["result"]["content"]
                if content and len(content) > 0:
                    text = content[0].get("text", "")
                    is_error = result["result"].get("isError", False)
                    if is_error:
                        log(f"❌ Evaluation Error: {text}")
                        return False
                    else:
                        log(f"✅ Success: {text}")
                        return True
                else:
                    log("❌ Empty response content")
                    return False
            else:
                log(f"❌ Unexpected response structure: {result}")
                return False
        else:
            log(f"❌ HTTP Error: {response.text}")
            return False

    except requests.exceptions.Timeout:
        log("❌ Request timeout")
        return False
    except Exception as e:
        log(f"❌ Request failed: {e}")
        return False


def test_sequence():
    """Test a sequence of evaluations to identify when the connection breaks"""
    log("🚀 Starting connection debug test...")

    # Start test server
    log("Starting test nREPL server...")
    subprocess.run(
        ["python3", "nrepl_test_server.py", "start"], cwd=".", capture_output=True
    )
    time.sleep(2)

    # Get port
    port_file = Path("test-nrepl/.nrepl-port")
    if not port_file.exists():
        log("❌ Port file not found")
        return

    nrepl_port = port_file.read_text().strip()
    log(f"📋 nREPL server running on port: {nrepl_port}")

    # Get dynamic MCP port
    mcp_port = get_test_mcp_port()
    log(f"📋 Using MCP HTTP port: {mcp_port}")

    # Start MCP server
    log("Starting MCP proxy server...")
    import os

    env = os.environ.copy()
    env["NREPL_PORT"] = nrepl_port
    env["BABASHKA_CLASSPATH"] = "src"
    env["MCP_HTTP_PORT"] = str(mcp_port)
    env["MCP_DEBUG"] = "true"  # Enable debug logging

    mcp_process = subprocess.Popen(
        ["bb", "src/mcp_nrepl_proxy/core.clj"],
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    time.sleep(3)

    try:
        # Test sequence
        tests = [
            "(+ 1 2)",  # Simple arithmetic
            "(+ 1 2 3)",  # The failing test case
            '(str "hello")',  # String operation
            "(+ 1 2 3 4)",  # Slightly more complex
            "(System/currentTimeMillis)",  # Java interop
        ]

        log("🧪 Running test sequence...")
        success_count = 0

        for i, test_code in enumerate(tests, 1):
            log(f"\n--- Test {i}/{len(tests)} ---")
            if run_mcp_eval(test_code, mcp_port):
                success_count += 1

            # Short pause between tests
            time.sleep(0.5)

        log(f"\n📊 Results: {success_count}/{len(tests)} tests passed")

        if success_count < len(tests):
            log("🔍 Checking MCP server logs...")
            # Try to get some stderr output
            try:
                mcp_process.poll()
                if mcp_process.stderr:
                    stderr_output = mcp_process.stderr.read()
                    if stderr_output:
                        log(f"MCP stderr:\n{stderr_output}")
            except Exception:
                pass

    finally:
        # Cleanup
        log("🧹 Cleaning up...")
        mcp_process.terminate()
        try:
            mcp_process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            mcp_process.kill()

        subprocess.run(
            ["python3", "nrepl_test_server.py", "stop"], cwd=".", capture_output=True
        )
        log("✅ Cleanup complete")


if __name__ == "__main__":
    test_sequence()
