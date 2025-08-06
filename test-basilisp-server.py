#!/usr/bin/env python3
"""
Simple Python server with Basilisp nREPL
Demonstrates Python introspection via nREPL protocol
"""

import sys
import time
import threading
from pathlib import Path

# Python-specific data we can introspect
class PythonTestServer:
    def __init__(self):
        self.counter = 0
        self.data = {
            "name": "Python Test Server",
            "version": "0.1.0",
            "runtime": sys.version,
            "platform": sys.platform,
            "items": []
        }
    
    def increment(self):
        self.counter += 1
        return self.counter
    
    def add_item(self, item):
        self.data["items"].append(item)
        return len(self.data["items"])
    
    def get_status(self):
        return {
            "counter": self.counter,
            "items_count": len(self.data["items"]),
            "python_version": sys.version_info[:3],
            "uptime": getattr(self, 'uptime', 0)
        }

# Global instance for Basilisp to access
server = PythonTestServer()

def start_basilisp_nrepl(port=7890):
    """Start Basilisp nREPL server on specified port"""
    try:
        print(f"🐍 Starting Basilisp nREPL server on port {port}")
        print(f"📊 Python server data available as 'server' object")
        
        # Write port file for discovery
        Path(".nrepl-port-basilisp").write_text(str(port))
        
        # Start nREPL server using subprocess for better control
        import subprocess
        import os
        
        # Use the virtual environment's basilisp
        venv_python = "./venv-basilisp/bin/python"
        if not os.path.exists(venv_python):
            venv_python = "python3"  # fallback
            
        cmd = [venv_python, "-m", "basilisp", "nrepl-server", "--port", str(port)]
        print(f"🚀 Running: {' '.join(cmd)}")
        
        # Start basilisp nrepl server
        subprocess.run(cmd, check=True)
        
    except ImportError as e:
        print(f"❌ Basilisp not installed. Install with: pip install basilisp")
        print(f"   Error: {e}")
        sys.exit(1)
    except subprocess.CalledProcessError as e:
        print(f"❌ Failed to start Basilisp nREPL server")
        print(f"   Error: {e}")
        sys.exit(1)

def background_work():
    """Simulate Python server doing work"""
    while True:
        time.sleep(5)
        server.increment()
        if hasattr(server, 'uptime'):
            server.uptime += 5
        else:
            server.uptime = 5
        print(f"⚡ Server tick: counter={server.counter}, uptime={server.uptime}s")

if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 7890
    
    print("🚀 Python Test Server with Basilisp nREPL")
    print("=" * 50)
    
    # Start background work thread
    worker = threading.Thread(target=background_work, daemon=True)
    worker.start()
    
    # Start Basilisp nREPL server (blocks)
    start_basilisp_nrepl(port)