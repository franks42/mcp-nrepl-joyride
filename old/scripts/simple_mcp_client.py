#!/usr/bin/env python3
"""
Simple HTTP-based MCP client for AWS Lambda MCP servers.

This client uses direct HTTP JSON-RPC calls instead of SSE transport,
making it compatible with AWS Lambda MCP handlers.
"""

import asyncio
import argparse
import json
import sys
from typing import Dict, Any, List, Optional
import httpx
from urllib.parse import urljoin


class SimpleMCPClient:
    """Simple HTTP-based MCP client using JSON-RPC over HTTP."""
    
    def __init__(self, mcp_url: str, rest_base_url: Optional[str] = None):
        self.mcp_url = mcp_url
        # For Lambda URLs, need to include /Prod prefix
        if rest_base_url:
            self.rest_base_url = rest_base_url
        else:
            # Default: replace /mcp with empty string for local, keep /Prod for Lambda
            if 'localhost' in mcp_url:
                self.rest_base_url = mcp_url.replace('/mcp', '')
            else:
                # For Lambda, mcp_url ends with /Prod/mcp, we want base to be .../Prod/
                self.rest_base_url = mcp_url.replace('/mcp', '')
        self.session_id = 1  # JSON-RPC request ID counter
        self.initialized = False
        
    def next_id(self) -> int:
        """Get next JSON-RPC request ID."""
        self.session_id += 1
        return self.session_id
    
    async def _send_request(self, method: str, params: Dict[str, Any] = None) -> Dict[str, Any]:
        """Send a JSON-RPC request to the MCP server."""
        if params is None:
            params = {}
            
        request = {
            "jsonrpc": "2.0",
            "id": self.next_id(),
            "method": method,
            "params": params
        }
        
        try:
            async with httpx.AsyncClient() as client:
                response = await client.post(
                    self.mcp_url,
                    json=request,
                    headers={"Content-Type": "application/json"},
                    timeout=30.0
                )
                response.raise_for_status()
                result = response.json()
                
                if "error" in result:
                    raise Exception(f"MCP Error: {result['error']}")
                    
                return result.get("result", {})
                
        except Exception as e:
            print(f"❌ Request failed: {e}")
            return {"error": str(e)}
    
    async def initialize(self) -> bool:
        """Initialize the MCP session."""
        try:
            print(f"🔌 Initializing MCP session with {self.mcp_url}")
            
            result = await self._send_request("initialize", {
                "protocolVersion": "2024-11-05",
                "capabilities": {
                    "roots": {"listChanged": True},
                    "sampling": {}
                },
                "clientInfo": {
                    "name": "simple-mcp-client",
                    "version": "1.0.0"
                }
            })
            
            if "error" not in result:
                server_info = result.get("serverInfo", {})
                print(f"✅ Connected to {server_info.get('name', 'unknown')} v{server_info.get('version', 'unknown')}")
                self.initialized = True
                return True
            else:
                print(f"❌ Initialization failed: {result['error']}")
                return False
                
        except Exception as e:
            print(f"❌ Initialization failed: {e}")
            return False
    
    async def list_tools(self) -> List[Dict[str, Any]]:
        """List all available tools."""
        if not self.initialized:
            print("❌ Session not initialized")
            return []
        
        try:
            result = await self._send_request("tools/list")
            
            if "error" in result:
                print(f"❌ Failed to list tools: {result['error']}")
                return []
            
            tools = result.get("tools", [])
            print(f"\n📋 Available tools ({len(tools)}):")
            
            for i, tool in enumerate(tools, 1):
                print(f"  {i}. {tool['name']}")
                print(f"     Description: {tool['description']}")
                
                # Show required parameters
                schema = tool.get('inputSchema', {})
                required = schema.get('required', [])
                if required:
                    print(f"     Required: {required}")
                
                # Show optional parameters  
                properties = schema.get('properties', {})
                optional = [p for p in properties.keys() if p not in required]
                if optional:
                    print(f"     Optional: {optional}")
                print()
            
            return tools
            
        except Exception as e:
            print(f"❌ Failed to list tools: {e}")
            return []
    
    async def call_tool(self, tool_name: str, arguments: Dict[str, Any]) -> Dict[str, Any]:
        """Call a specific tool."""
        if not self.initialized:
            print("❌ Session not initialized")
            return {"error": "Session not initialized"}
        
        try:
            print(f"🔧 Calling tool: {tool_name}")
            print(f"📥 Arguments: {json.dumps(arguments, indent=2)}")
            
            result = await self._send_request("tools/call", {
                "name": tool_name,
                "arguments": arguments
            })
            
            if "error" in result:
                print(f"❌ Tool call failed: {result['error']}")
                return result
            
            # Extract content from MCP response
            content = result.get("content", [])
            if content and len(content) > 0:
                # Get the first content item (usually text)
                first_content = content[0]
                if first_content.get("type") == "text":
                    # Parse the text content as JSON if possible
                    text_content = first_content.get("text", "")
                    try:
                        parsed_result = json.loads(text_content.replace("'", '"'))
                        print(f"📤 Result: {json.dumps(parsed_result, indent=2)}")
                        return parsed_result
                    except json.JSONDecodeError:
                        # If not JSON, return as text
                        print(f"📤 Result: {text_content}")
                        return {"text": text_content}
            
            print(f"📤 Raw result: {json.dumps(result, indent=2)}")
            return result
            
        except Exception as e:
            error_result = {"error": str(e)}
            print(f"❌ Tool call failed: {e}")
            return error_result
    
    async def call_rest_api(self, endpoint: str, method: str = "GET", **kwargs) -> Dict[str, Any]:
        """Call the equivalent REST API endpoint."""
        # Fix urljoin issue: ensure base URL ends with / and endpoint doesn't start with /
        base_url = self.rest_base_url.rstrip('/') + '/'
        endpoint = endpoint.lstrip('/')
        url = urljoin(base_url, endpoint)
        
        try:
            print(f"🌐 REST base URL: {self.rest_base_url}")
            print(f"🌐 Endpoint: {endpoint}")
            print(f"🌐 Calling REST API: {method} {url}")
            if kwargs:
                print(f"📥 Parameters: {json.dumps(kwargs, indent=2)}")
            
            async with httpx.AsyncClient() as client:
                if method.upper() == "GET":
                    response = await client.get(url, params=kwargs, timeout=30.0)
                elif method.upper() == "POST":
                    response = await client.post(url, json=kwargs, timeout=30.0)
                else:
                    raise ValueError(f"Unsupported method: {method}")
                
                response.raise_for_status()
                result = response.json()
                print(f"📤 Result: {json.dumps(result, indent=2)}")
                return result
                
        except Exception as e:
            error_result = {"error": str(e)}
            print(f"❌ REST API call failed: {e}")
            return error_result
    
    async def compare_mcp_vs_rest(self, tool_name: str, rest_endpoint: str, arguments: Dict[str, Any]):
        """Compare MCP tool call with equivalent REST API call."""
        print(f"\n🔍 Comparing MCP vs REST for: {tool_name}")
        print("=" * 60)
        
        # Call MCP tool
        print("MCP CALL:")
        mcp_result = await self.call_tool(tool_name, arguments)
        
        print("\n" + "-" * 40 + "\n")
        
        # Call REST API
        print("REST CALL:")
        rest_result = await self.call_rest_api(rest_endpoint, **arguments)
        
        print("\n" + "-" * 40 + "\n")
        
        # Compare results
        print("COMPARISON:")
        if mcp_result == rest_result:
            print("✅ Results match perfectly!")
        else:
            print("❌ Results differ:")
            print(f"🔧 MCP:  {json.dumps(mcp_result, indent=2)}")
            print(f"🌐 REST: {json.dumps(rest_result, indent=2)}")
        
        return mcp_result, rest_result
    
    async def interactive_mode(self):
        """Interactive command-line interface."""
        print("\n🎮 Interactive MCP Test Client")
        print("Commands:")
        print("  list - List available tools")
        print("  call <tool_name> <json_args> - Call a tool") 
        print("  rest <endpoint> <json_args> - Call REST API")
        print("  compare <tool_name> <rest_endpoint> <json_args> - Compare MCP vs REST")
        print("  quit - Exit")
        print()
        
        while True:
            try:
                command = input("mcp> ").strip()
                if not command:
                    continue
                
                parts = command.split(' ', 1)
                cmd = parts[0].lower()
                
                if cmd == 'quit':
                    break
                elif cmd == 'list':
                    await self.list_tools()
                elif cmd == 'call' and len(parts) > 1:
                    args = parts[1].split(' ', 1)
                    tool_name = args[0]
                    arguments = json.loads(args[1]) if len(args) > 1 else {}
                    await self.call_tool(tool_name, arguments)
                elif cmd == 'rest' and len(parts) > 1:
                    args = parts[1].split(' ', 1)
                    endpoint = args[0]
                    arguments = json.loads(args[1]) if len(args) > 1 else {}
                    await self.call_rest_api(endpoint, **arguments)
                elif cmd == 'compare' and len(parts) > 1:
                    args = parts[1].split(' ', 2)
                    if len(args) >= 2:
                        tool_name = args[0]
                        rest_endpoint = args[1]
                        arguments = json.loads(args[2]) if len(args) > 2 else {}
                        await self.compare_mcp_vs_rest(tool_name, rest_endpoint, arguments)
                    else:
                        print("Usage: compare <tool_name> <rest_endpoint> <json_args>")
                else:
                    print(f"Unknown command: {cmd}")
                    
            except (EOFError, KeyboardInterrupt):
                break
            except json.JSONDecodeError as e:
                print(f"❌ JSON error: {e}")
            except Exception as e:
                print(f"❌ Error: {e}")


async def run_predefined_tests(client: SimpleMCPClient):
    """Run a set of predefined tests."""
    print("\n🧪 Running predefined tests...")
    
    # Use a known working wallet for testing
    test_wallet = "pb1gghjut3ccd8ay0zduzj64hwre2fxs9ldmqhffj"  # Known working wallet
    
    tests = [
        {
            "name": "Current HASH Statistics",
            "tool": "fetchCurrentHashStatistics",
            "rest": "/api/current_hash_statistics",
            "args": {}
        },
        {
            "name": "System Context",
            "tool": "getSystemContext",
            "rest": "/api/system_context",
            "args": {}
        },
        {
            "name": "Figure Markets Data",
            "tool": "fetchCurrentFmData",
            "rest": "/api/current_fm_data",
            "args": {}
        }
    ]
    
    for test in tests:
        print(f"\n📋 {test['name']}")
        await client.compare_mcp_vs_rest(test['tool'], test['rest'], test['args'])
        print()


async def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(description="Simple HTTP-based MCP Test Client")
    parser.add_argument("--mcp-url", default="http://localhost:3000/mcp",
                       help="MCP server URL")
    parser.add_argument("--rest-url", help="REST API base URL (defaults to MCP URL without /mcp)")
    parser.add_argument("--interactive", "-i", action="store_true",
                       help="Run in interactive mode")
    parser.add_argument("--test", "-t", action="store_true",
                       help="Run predefined tests")
    
    args = parser.parse_args()
    
    client = SimpleMCPClient(args.mcp_url, args.rest_url)
    
    # Initialize session
    if not await client.initialize():
        sys.exit(1)
    
    try:
        # List tools
        await client.list_tools()
        
        # Run tests if requested
        if args.test:
            await run_predefined_tests(client)
        
        # Interactive mode if requested
        if args.interactive:
            await client.interactive_mode()
            
    except KeyboardInterrupt:
        print("\n👋 Goodbye!")


if __name__ == "__main__":
    asyncio.run(main())