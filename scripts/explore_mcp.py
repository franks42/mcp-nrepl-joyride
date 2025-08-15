#!/usr/bin/env python3
"""
Simple MCP Explorer - See raw MCP tool responses

Shows exactly what MCP callers receive - the raw JSON structure without
any test framework overhead or validation. Perfect for exploration and
debugging.

Usage:
    python explore_mcp.py --tool debug-eval --args '{"code": "(+ 1 2 3)"}'
    python explore_mcp.py --tool nrepl-server --args '{"op": "status"}'
    python explore_mcp.py --list-tools
"""

import argparse
import asyncio
import json
import sys
from typing import Any, Dict

try:
    import httpx

    HTTPX_AVAILABLE = True
except ImportError:
    HTTPX_AVAILABLE = False

try:
    from rich.console import Console
    from rich.panel import Panel
    from rich.syntax import Syntax

    RICH_AVAILABLE = True
except ImportError:
    RICH_AVAILABLE = False


class MCPExplorer:
    """Simple MCP explorer for seeing raw responses."""

    def __init__(self, base_url: str = "http://localhost:3000"):
        if not HTTPX_AVAILABLE:
            raise ImportError("httpx required: uv add httpx")

        self.base_url = base_url.rstrip("/")
        self.mcp_url = f"{self.base_url}/mcp/"
        self.headers = {
            "Content-Type": "application/json",
            "Accept": "application/json, text/event-stream",
        }
        self.request_id = 1
        self.console = Console() if RICH_AVAILABLE else None

    async def call_tool(
        self, tool_name: str, arguments: Dict[str, Any]
    ) -> Dict[str, Any]:
        """Call an MCP tool and return raw response."""
        payload = {
            "jsonrpc": "2.0",
            "id": self.request_id,
            "method": "tools/call",
            "params": {"name": tool_name, "arguments": arguments},
        }
        self.request_id += 1

        async with httpx.AsyncClient(timeout=30) as client:
            response = await client.post(
                self.mcp_url, json=payload, headers=self.headers
            )
            response.raise_for_status()
            return response.json()

    async def list_tools(self) -> Dict[str, Any]:
        """List available MCP tools."""
        payload = {"jsonrpc": "2.0", "id": self.request_id, "method": "tools/list"}
        self.request_id += 1

        async with httpx.AsyncClient(timeout=30) as client:
            response = await client.post(
                self.mcp_url, json=payload, headers=self.headers
            )
            response.raise_for_status()
            return response.json()

    def print_response(self, response: Dict[str, Any], title: str = "MCP Response"):
        """Pretty print the response."""
        if self.console:
            # Use rich for pretty printing
            self.console.print(Panel(title, style="bold blue"))
            syntax = Syntax(json.dumps(response, indent=2), "json", theme="monokai")
            self.console.print(syntax)
        else:
            # Fallback to plain JSON
            print(f"\\n=== {title} ===")
            print(json.dumps(response, indent=2))

    def extract_content(self, response: Dict[str, Any]) -> str:
        """Extract the actual content from MCP response if available."""
        try:
            if "result" in response and "content" in response["result"]:
                content = response["result"]["content"]
                if isinstance(content, list) and len(content) > 0:
                    if "text" in content[0]:
                        text = content[0]["text"]
                        # Try to parse as JSON if it looks like JSON
                        if text.strip().startswith(("{", "[")):
                            try:
                                return json.loads(text)
                            except json.JSONDecodeError:
                                return text
                        return text
            return None
        except (KeyError, IndexError, TypeError):
            return None

    def extract_clojure_value(self, response: Dict[str, Any]) -> Any:
        """Extract the Clojure return value from debug-eval responses."""
        content = self.extract_content(response)
        if isinstance(content, dict):
            # For debug-eval, look for the actual Clojure result
            if "result" in content:
                return content["result"]
            elif "value" in content:
                return content["value"]
            # If it's a structured response, return the whole thing
            return content
        return content


async def main():
    parser = argparse.ArgumentParser(
        description="Simple MCP Explorer - See raw tool responses",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # List available tools
  %(prog)s --list-tools
  
  # Default: show clean tool response (JSON)
  %(prog)s --tool debug-eval --args '{"code": "(+ 1 2 3)"}'
  
  # Show raw MCP JSON-RPC response  
  %(prog)s --tool debug-eval --args '{"code": "(+ 1 2 3)"}' --output raw
  
  # Show just the Clojure return value (for debug-eval)
  %(prog)s --tool debug-eval --args '{"code": "(+ 1 2 3)"}' --output clj
  
  # 🚀 SHORTCUT: Evaluate Clojure code directly (returns just the value)
  %(prog)s --eval "(+ 1 2 3)"
  %(prog)s --eval "@nrepl-mcp-server.state.connection/connection-state"
  %(prog)s --eval "(keys (ns-publics *ns*))"
  
  # Check nREPL connection status
  %(prog)s --tool nrepl-server --args '{"op": "status"}'
  
  # Quiet mode: just the data, no headers
  %(prog)s --tool debug-eval --args '{"code": "42"}' --quiet
        """,
    )

    parser.add_argument(
        "--base-url",
        default="http://localhost:3000",
        help="Base URL for MCP server (default: %(default)s)",
    )
    parser.add_argument("--tool", help="Tool name to call")
    parser.add_argument("--args", help="Tool arguments as JSON string", default="{}")
    parser.add_argument(
        "--eval", help="Evaluate Clojure code directly (shortcut for debug-eval)"
    )
    parser.add_argument(
        "--list-tools", action="store_true", help="List available tools"
    )
    parser.add_argument(
        "--output",
        choices=["raw", "json", "clj"],
        default="json",
        help="Output format: raw (full MCP), json (tool response), clj (Clojure value for debug-eval)",
    )
    parser.add_argument(
        "--quiet", action="store_true", help="Only show essential output"
    )

    args = parser.parse_args()

    if not args.list_tools and not args.tool and not args.eval:
        parser.error("Must specify --list-tools, --tool, or --eval")

    try:
        explorer = MCPExplorer(args.base_url)

        if args.list_tools:
            response = await explorer.list_tools()
            if not args.quiet:
                explorer.print_response(response, "Available MCP Tools")
            else:
                # Just show tool names
                if "result" in response and "tools" in response["result"]:
                    tools = response["result"]["tools"]
                    for tool in tools:
                        print(tool.get("name", "unknown"))

        elif args.eval:
            # Special case: direct Clojure evaluation
            response = await explorer.call_tool("debug-eval", {"code": args.eval})

            # Always return just the Clojure value for --eval
            clj_value = explorer.extract_clojure_value(response)
            if clj_value is not None:
                if isinstance(clj_value, (dict, list)):
                    print(json.dumps(clj_value, indent=2))
                else:
                    print(clj_value)
            else:
                # Fallback: show any error or response
                content = explorer.extract_content(response)
                if content and isinstance(content, dict) and "error" in content:
                    print(f"Error: {content['error']}", file=sys.stderr)
                    return 1
                else:
                    print(json.dumps(content or response, indent=2))

        elif args.tool:
            try:
                tool_args = json.loads(args.args)
            except json.JSONDecodeError as e:
                print(f"Error parsing arguments JSON: {e}", file=sys.stderr)
                return 1

            response = await explorer.call_tool(args.tool, tool_args)

            # Handle different output formats
            if args.output == "raw":
                # Show full MCP JSON-RPC response
                if not args.quiet:
                    explorer.print_response(response, f"Raw MCP Response: {args.tool}")
                else:
                    print(json.dumps(response, indent=2))

            elif args.output == "json":
                # Show extracted tool response (parsed JSON from content)
                content = explorer.extract_content(response)
                if content is not None:
                    if not args.quiet:
                        print(f"\\n=== Tool Response: {args.tool} ===")
                    if isinstance(content, (dict, list)):
                        print(json.dumps(content, indent=2))
                    else:
                        print(content)
                else:
                    # Fallback to raw if no content extractable
                    if not args.quiet:
                        print(f"\\n=== No extractable content, showing raw ===")
                    print(json.dumps(response, indent=2))

            elif args.output == "clj":
                # Show Clojure value (for debug-eval)
                clj_value = explorer.extract_clojure_value(response)
                if clj_value is not None:
                    if not args.quiet:
                        print(f"\\n=== Clojure Value: {args.tool} ===")
                    if isinstance(clj_value, (dict, list)):
                        print(json.dumps(clj_value, indent=2))
                    else:
                        print(clj_value)
                else:
                    # Fallback to json mode
                    content = explorer.extract_content(response)
                    if content is not None:
                        if isinstance(content, (dict, list)):
                            print(json.dumps(content, indent=2))
                        else:
                            print(content)
                    else:
                        print(json.dumps(response, indent=2))

        return 0

    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    try:
        sys.exit(asyncio.run(main()))
    except KeyboardInterrupt:
        print("\\nInterrupted", file=sys.stderr)
        sys.exit(1)
