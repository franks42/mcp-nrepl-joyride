#!/usr/bin/env python3
import asyncio
import json

import httpx


async def test_failing_call():
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json, text/event-stream",
    }

    # First connect
    connect_payload = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "tools/call",
        "params": {
            "name": "nrepl-server",
            "arguments": {"op": "connect", "connection": "56626"},
        },
    }

    # Then the failing call
    failing_payload = {
        "jsonrpc": "2.0",
        "id": 2,
        "method": "tools/call",
        "params": {
            "name": "local-eval",
            "arguments": {
                "code": "(let [s (:socket @nrepl-mcp-server.state.connection/connection-state)] (when s {:local-port (.getLocalPort s) :remote-port (.getPort s)}))"
            },
        },
    }

    async with httpx.AsyncClient() as client:
        # Connect first
        print("=== CONNECTING ===")
        response = await client.post(
            "http://localhost:3000/mcp/", json=connect_payload, headers=headers
        )
        print(json.dumps(response.json(), indent=2))

        # Try the failing call
        print("\n=== TRYING FAILING CALL ===")
        response = await client.post(
            "http://localhost:3000/mcp/", json=failing_payload, headers=headers
        )
        result = response.json()
        print(json.dumps(result, indent=2))

        # Extract the actual content
        if "result" in result and "content" in result["result"]:
            content = result["result"]["content"][0]["text"]
            parsed_content = json.loads(content)
            print("\n=== PARSED CONTENT ===")
            print(json.dumps(parsed_content, indent=2))


if __name__ == "__main__":
    asyncio.run(test_failing_call())
