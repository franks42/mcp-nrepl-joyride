#!/usr/bin/env python3
"""
Test SSE bridge with stdio MCP server.

This script demonstrates the full SSE workflow:
1. Connect to SSE endpoint to get session ID
2. Send MCP messages via POST to /messages endpoint
3. Receive responses via SSE stream
"""

import asyncio
import json
import httpx
from typing import Optional
import sys


class SSEMCPClient:
    def __init__(self, base_url: str = "http://localhost:3000"):
        self.base_url = base_url
        self.session_id: Optional[str] = None
        self.messages_url: Optional[str] = None
        
    async def connect_sse(self):
        """Establish SSE connection and extract session info."""
        print("🔌 Connecting to SSE endpoint...")
        
        async with httpx.AsyncClient() as client:
            async with client.stream("GET", f"{self.base_url}/sse", 
                                   headers={"Accept": "text/event-stream"}) as response:
                print(f"📡 SSE Response: {response.status_code}")
                
                if response.status_code != 200:
                    raise Exception(f"SSE connection failed: {response.status_code}")
                
                # Read the first few SSE events to get session info
                async for line in response.aiter_lines():
                    print(f"📥 SSE: {line}")
                    
                    if line.startswith("data: /messages/"):
                        # Extract session endpoint
                        endpoint = line[6:]  # Remove "data: " prefix
                        self.messages_url = f"{self.base_url}{endpoint}"
                        # Extract session ID from URL
                        if "session_id=" in endpoint:
                            self.session_id = endpoint.split("session_id=")[1]
                        print(f"✅ Session established: {self.session_id}")
                        print(f"📬 Messages URL: {self.messages_url}")
                        return
                    
                    # Stop after getting the endpoint info
                    if self.messages_url:
                        break

    async def send_message(self, message: dict) -> dict:
        """Send MCP message via POST to messages endpoint."""
        if not self.messages_url:
            raise Exception("SSE connection not established")
            
        print(f"📤 Sending: {json.dumps(message, indent=2)}")
        
        async with httpx.AsyncClient() as client:
            response = await client.post(
                self.messages_url,
                json=message,
                headers={"Content-Type": "application/json"}
            )
            
            print(f"📨 Response Status: {response.status_code}")
            if response.status_code == 202:
                print("✅ Message accepted by bridge")
                return {"status": "accepted"}
            else:
                print(f"❌ Message failed: {response.text}")
                return {"status": "failed", "error": response.text}

    async def test_initialize(self):
        """Test MCP initialization."""
        message = {
            "jsonrpc": "2.0",
            "id": 1,
            "method": "initialize",
            "params": {
                "protocolVersion": "2024-11-05",
                "capabilities": {},
                "clientInfo": {"name": "sse-test-client", "version": "1.0"}
            }
        }
        return await self.send_message(message)

    async def test_list_tools(self):
        """Test tools/list."""
        message = {
            "jsonrpc": "2.0",
            "id": 2,
            "method": "tools/list",
            "params": {}
        }
        return await self.send_message(message)

    async def test_debug_eval(self):
        """Test debug-eval tool."""
        message = {
            "jsonrpc": "2.0",
            "id": 3,
            "method": "tools/call",
            "params": {
                "name": "debug-eval",
                "arguments": {"code": "(+ 1 2 3)"}
            }
        }
        return await self.send_message(message)


async def main():
    print("🧪 Testing SSE Bridge with stdio MCP Server")
    print("=" * 50)
    
    client = SSEMCPClient()
    
    try:
        # Step 1: Connect to SSE endpoint
        await client.connect_sse()
        
        # Step 2: Test MCP protocol
        print("\n🔄 Testing MCP Initialize...")
        await client.test_initialize()
        
        await asyncio.sleep(1)
        
        print("\n🔄 Testing Tools List...")
        await client.test_list_tools()
        
        await asyncio.sleep(1)
        
        print("\n🔄 Testing Debug Eval...")
        await client.test_debug_eval()
        
        print("\n✅ SSE Bridge Test Complete!")
        print("Note: Responses should appear in the SSE stream (not shown in this simple test)")
        
    except Exception as e:
        print(f"❌ Test failed: {e}")
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())