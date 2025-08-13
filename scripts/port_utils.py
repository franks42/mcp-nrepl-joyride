#!/usr/bin/env python3
"""
Port utilities for MCP testing infrastructure.
Provides dynamic port allocation to avoid conflicts.
"""

import socket
import subprocess
from contextlib import closing


def find_free_port(start_port=3001, max_attempts=100):
    """Find a free port starting from start_port"""
    for port in range(start_port, start_port + max_attempts):
        try:
            with closing(socket.socket(socket.AF_INET, socket.SOCK_STREAM)) as sock:
                sock.bind(("localhost", port))
                return port
        except OSError:
            continue
    raise Exception(
        f"Could not find free port in range {start_port}-{start_port + max_attempts}"
    )


def is_port_in_use(port, host="localhost"):
    """Check if a port is in use"""
    try:
        with closing(socket.socket(socket.AF_INET, socket.SOCK_STREAM)) as sock:
            sock.settimeout(1)
            result = sock.connect_ex((host, port))
            return result == 0
    except:
        return False


def get_bb_nrepl_server_port():
    """Get the port used by bb-nrepl-server if running"""
    try:
        # Check if bb process is running on port 3000 (default bb-nrepl-server port)
        result = subprocess.run(
            ["lsof", "-ti:3000"], capture_output=True, text=True, timeout=5
        )
        if result.returncode == 0 and result.stdout.strip():
            return 3000
    except:
        pass
    return None


def get_test_mcp_port():
    """Get a port for test MCP server, avoiding bb-nrepl-server conflicts"""
    bb_port = get_bb_nrepl_server_port()
    if bb_port:
        print(f"⚠️  bb-nrepl-server detected on port {bb_port}, using alternate port")
        return find_free_port(3001)
    else:
        # Check if 3000 is free, use it if available
        if not is_port_in_use(3000):
            return 3000
        else:
            return find_free_port(3001)


if __name__ == "__main__":
    import sys

    if len(sys.argv) > 1:
        if sys.argv[1] == "test":
            port = get_test_mcp_port()
            print(f"Test MCP port: {port}")
        elif sys.argv[1] == "free":
            port = find_free_port()
            print(f"Free port: {port}")
    else:
        print("Usage: port_utils.py [test|free]")
