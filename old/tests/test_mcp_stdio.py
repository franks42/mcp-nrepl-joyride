#!/usr/bin/env python3
"""
Simple stdin/stdout MCP client for testing the MCP-nREPL proxy.
This client communicates with the MCP server using stdio transport.
"""

import json
import subprocess
import sys
from typing import Dict, Any, Optional

class StdioMCPClient:
    """MCP client using stdin/stdout transport."""
    
    def __init__(self, command: str, args: list = None):
        """Initialize the MCP client with a command to run."""
        self.command = command
        self.args = args or []
        self.process = None
        self.request_id = 0
        
    def start(self):
        """Start the MCP server process."""
        cmd = [self.command] + self.args
        print(f"🚀 Starting MCP server: {' '.join(cmd)}")
        self.process = subprocess.Popen(
            cmd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1
        )
        
    def send_request(self, method: str, params: Dict[str, Any] = None) -> Dict[str, Any]:
        """Send a JSON-RPC request and get response."""
        if not self.process:
            raise Exception("Server not started")
            
        self.request_id += 1
        request = {
            "jsonrpc": "2.0",
            "id": self.request_id,
            "method": method,
            "params": params or {}
        }
        
        # Send request
        request_str = json.dumps(request)
        print(f"📤 Sending: {request_str}")
        self.process.stdin.write(request_str + "\n")
        self.process.stdin.flush()
        
        # Get response
        response_str = self.process.stdout.readline()
        if not response_str:
            raise Exception("No response from server")
            
        print(f"📥 Received: {response_str}")
        response = json.loads(response_str)
        
        if "error" in response:
            print(f"❌ Error: {response['error']}")
            
        return response
    
    def initialize(self) -> bool:
        """Initialize the MCP session."""
        print("\n🔌 Initializing MCP session...")
        
        response = self.send_request("initialize", {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {
                "name": "test-client",
                "version": "1.0.0"
            }
        })
        
        if "result" in response:
            server_info = response["result"].get("serverInfo", {})
            print(f"✅ Connected to {server_info.get('name', 'unknown')} v{server_info.get('version', 'unknown')}")
            return True
        return False
    
    def list_tools(self):
        """List available tools."""
        print("\n📋 Listing tools...")
        response = self.send_request("tools/list")
        
        if "result" in response:
            tools = response["result"].get("tools", [])
            print(f"Found {len(tools)} tools:")
            for tool in tools:
                print(f"  - {tool['name']}: {tool['description']}")
                schema = tool.get('inputSchema', {})
                required = schema.get('required', [])
                if required:
                    print(f"    Required: {required}")
            return tools
        return []
    
    def call_tool(self, tool_name: str, arguments: Dict[str, Any]):
        """Call a specific tool."""
        print(f"\n🔧 Calling tool: {tool_name}")
        print(f"   Arguments: {arguments}")
        
        response = self.send_request("tools/call", {
            "name": tool_name,
            "arguments": arguments
        })
        
        if "result" in response:
            content = response["result"].get("content", [])
            if content:
                for item in content:
                    if item.get("type") == "text":
                        print(f"   Result: {item.get('text', '')}")
            return response["result"]
        return None
    
    def close(self):
        """Close the MCP server process."""
        if self.process:
            self.process.terminate()
            self.process.wait()
            print("\n👋 Server closed")

def main():
    """Test the MCP-nREPL proxy with Joyride."""
    # Create client for our MCP server
    client = StdioMCPClient("bb", ["-cp", "src", "src/mcp_nrepl_proxy/core.clj"])
    
    try:
        # Start server
        client.start()
        
        # Initialize
        if not client.initialize():
            print("Failed to initialize")
            return
        
        # List tools
        tools = client.list_tools()
        
        # Connect to Joyride nREPL (port 62577)
        print("\n1️⃣ Connecting to Joyride nREPL...")
        client.call_tool("nrepl-connect", {
            "host": "127.0.0.1",
            "port": 62577
        })
        
        # Test basic evaluation
        print("\n2️⃣ Testing basic evaluation...")
        client.call_tool("nrepl-eval", {
            "code": "(+ 1 2 3)"
        })
        
        # Test namespace check
        print("\n3️⃣ Checking namespace...")
        client.call_tool("nrepl-eval", {
            "code": "*ns*"
        })
        
        # Test VS Code API availability
        print("\n4️⃣ Testing VS Code API...")
        client.call_tool("nrepl-eval", {
            "code": "(resolve 'js/vscode)"
        })
        
        # Test Joyride loading
        print("\n5️⃣ Loading Joyride...")
        client.call_tool("nrepl-eval", {
            "code": "(require '[joyride.core :as joyride])"
        })
        
        # Get workspace info
        print("\n6️⃣ Getting workspace info...")
        client.call_tool("nrepl-eval", {
            "code": "(-> js/vscode .-workspace .-workspaceFolders (aget 0) .-uri .-fsPath)"
        })
        
        # Show status
        print("\n7️⃣ Checking status...")
        client.call_tool("nrepl-status", {})
        
        print("\n✅ All tests completed!")
        
    except KeyboardInterrupt:
        print("\n⚠️ Interrupted by user")
    except Exception as e:
        print(f"\n❌ Error: {e}")
    finally:
        client.close()

if __name__ == "__main__":
    main()