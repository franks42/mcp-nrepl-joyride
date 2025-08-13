#!/usr/bin/env python3

import asyncio
import json
from mcp_test_client import MCPTestClient

async def test_autodiscovery():
    """Test the Joyride nREPL auto-discovery functionality"""
    
    client = MCPTestClient('http://localhost:3000/mcp')
    
    try:
        # Connect to MCP server
        print("🔌 Connecting to MCP server...")
        await client.connect()
        print("✅ Connected successfully!")
        
        # Test nrepl-status to see auto-discovery results
        print("\n🔍 Testing nrepl-status (auto-discovery)...")
        status_result = await client.call_tool('nrepl-status', {})
        
        if status_result and 'content' in status_result:
            status_text = status_result['content'][0]['text']
            status_json = json.loads(status_text)
            
            print("📊 nREPL Status:")
            print(f"   Connected: {status_json.get('connected', False)}")
            print(f"   Host: {status_json.get('host', 'N/A')}")
            print(f"   Port: {status_json.get('port', 'N/A')}")
            print(f"   Workspace: {status_json.get('workspace', 'N/A')}")
            print(f"   Sessions: {status_json.get('sessions', 0)}")
            print(f"   Recent commands: {status_json.get('recent-commands', 0)}")
            
            # Check if auto-discovery worked
            if status_json.get('connected') and status_json.get('port') == 62577:
                print("🎉 SUCCESS: Joyride nREPL auto-discovery is working!")
                print("   ✅ Found port 62577 in .joyride/.nrepl-port")
                print("   ✅ Successfully connected to Joyride nREPL")
            else:
                print("❌ Auto-discovery didn't work as expected")
        
        # Test a simple evaluation to verify connection works
        print("\n🧪 Testing nrepl-eval with simple expression...")
        eval_result = await client.call_tool('nrepl-eval', {'code': '(+ 1 2 3)'})
        
        if eval_result and 'content' in eval_result:
            result_text = eval_result['content'][0]['text']
            print(f"   Result: {result_text}")
            if result_text == "6":
                print("✅ nREPL evaluation working correctly!")
            else:
                print(f"⚠️ Unexpected result: {result_text}")
        
    except Exception as e:
        print(f"❌ Error: {e}")
    
    finally:
        await client.disconnect()
        print("🔌 Disconnected from MCP server")

if __name__ == "__main__":
    asyncio.run(test_autodiscovery())