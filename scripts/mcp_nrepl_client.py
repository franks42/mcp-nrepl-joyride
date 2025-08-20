#!/usr/bin/env python3
"""
MCP nREPL Client - Interactive client for MCP nREPL servers

Shows exactly what MCP callers receive - the raw JSON structure without
any test framework overhead or validation. Perfect for exploration and
debugging nREPL-enabled MCP servers.

Usage:
    python mcp_nrepl_client.py --tool local-eval --args '{"code": "(+ 1 2 3)"}'
    python mcp_nrepl_client.py --tool nrepl-connection --args '{"op": "status"}'
    python mcp_nrepl_client.py --list-tools
"""

import argparse
import asyncio
import base64
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


# =============================================================================
# Base64 Utilities
# =============================================================================


def encode_code_to_base64(code: str) -> str:
    """Encode Clojure code to base64"""
    return base64.b64encode(code.encode("utf-8")).decode("ascii")


def decode_base64_to_code(b64_str: str) -> str:
    """Decode base64 to Clojure code"""
    return base64.b64decode(b64_str).decode("utf-8")


def decode_base64_response(response_data: dict) -> dict:
    """Auto-decode base64 fields in response if present"""
    if isinstance(response_data, dict):
        decoded_data = response_data.copy()
        # Decode common base64 fields
        for field in [
            "value-base64",
            "result-base64",
            "out-base64",
            "err-base64",
            "stdout-base64",
            "stderr-base64",
            "error-base64",
        ]:
            if field in decoded_data:
                try:
                    decoded_value = decode_base64_to_code(decoded_data[field])
                    decoded_field_name = field.replace("-base64", "-decoded")
                    decoded_data[decoded_field_name] = decoded_value
                except Exception:
                    pass  # Keep original if decode fails
        return decoded_data
    else:
        # Return original data if not a dict
        return response_data


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
        """Extract the Clojure return value from local-eval responses."""
        content = self.extract_content(response)
        if isinstance(content, dict):
            # For local-eval, look for the actual Clojure result
            if "result" in content:
                return content["result"]
            elif "value" in content:
                return content["value"]
            # If it's a structured response, return the whole thing
            return content
        return content


async def main():
    parser = argparse.ArgumentParser(
        description="MCP nREPL Client - Interactive client for MCP nREPL servers",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # List available tools
  %(prog)s --list-tools
  
  # Default: show clean tool response (JSON)
  %(prog)s --tool local-eval --args '{"code": "(+ 1 2 3)"}'
  
  # Show raw MCP JSON-RPC response  
  %(prog)s --tool local-eval --args '{"code": "(+ 1 2 3)"}' --output raw
  
  # Show just the Clojure return value (for local-eval)
  %(prog)s --tool local-eval --args '{"code": "(+ 1 2 3)"}' --output clj
  
  # 🚀 SHORTCUT: Evaluate Clojure code directly (returns just the value)
  %(prog)s --eval "(+ 1 2 3)"
  %(prog)s --eval "@nrepl-mcp-server.state.connection/connection-state"
  %(prog)s --eval "(keys (ns-publics *ns*))"
  
  # 💡 NEW: Easy code passing for any tool (no JSON escaping needed!)
  %(prog)s --tool nrepl-eval --code "(+ 1 2 3)"
  %(prog)s --tool local-eval --code "(println \"Hello World\")"
  %(prog)s --tool nrepl-eval --code "(defn greet [name] (str \"Hello \" name))"
  
  # 🚀 ZERO ESCAPING: Use stdin for complex code  
  echo "(defn greet [name] (str \"Hello, \" name \"!\"))" | %(prog)s --tool nrepl-eval --code-stdin
  cat my-code.clj | %(prog)s --tool nrepl-eval --code-stdin --base-url http://localhost:3000/mcp
  
  # 💫 BEST: Load from file (no confirmation prompts!)
  %(prog)s --tool nrepl-eval --load-code-file my-code.clj --base-url http://localhost:3000/mcp
  %(prog)s --tool local-eval --load-code-file test.clj --quiet
  
  # Check nREPL connection status
  %(prog)s --tool nrepl-connection --args '{"op": "status"}'
  
  # 🔗 NEW: Use specific connection for nREPL tools
  %(prog)s --tool nrepl-eval --code "(+ 1 2 3)" --connection "localhost:7890"
  %(prog)s --tool nrepl-eval --code "(+ 1 2 3)" --connection "conn-123"
  %(prog)s --tool nrepl-send-message --args '{"message": {"op": "eval", "code": "(+ 1 2)"}}' --connection "my-repl"
  
  # Quiet mode: just the data, no headers
  %(prog)s --tool local-eval --args '{"code": "42"}' --quiet
  
  # 🚀 NEW: Base64 input flag (works with any code input method!)
  %(prog)s --tool nrepl-eval --code "KHByaW50bG4gIkhlbGxvIHdvcmxkISIp" --input-base64
  echo "KHByaW50bG4gIkhlbGxvIHdvcmxkISIp" | %(prog)s --tool nrepl-eval --code-stdin --input-base64
  
  # 🔄 Round-trip base64 (input and output)
  %(prog)s --tool local-eval --code "KCsgMSAyKQ==" --input-base64 --output-base64 --decode-output
  
  # 🤖 AI-friendly: Encode offline, submit cleanly
  # echo '(println "Hello 'quoted' world!")' | base64 | xargs -I {} ./script --tool nrepl-eval --code {} --input-base64"
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
        "--connection",
        help="Connection identifier for multi-connection nREPL tools (nickname, connection-id, or host:port)",
    )
    parser.add_argument(
        "--code",
        help="Clojure code to pass as 'code' parameter (auto-escaped for any tool)",
    )
    parser.add_argument(
        "--code-stdin",
        action="store_true",
        help="Read code from stdin (no escaping needed at all!)",
    )
    parser.add_argument(
        "--load-code-file",
        help="Load code from file path (no escaping, handles any file size)",
    )
    parser.add_argument(
        "--input-base64",
        action="store_true",
        help="Interpret input code as base64-encoded (works with --code, --code-stdin, --load-code-file)",
    )
    parser.add_argument(
        "--output-base64",
        action="store_true",
        help="Request base64-encoded output from server",
    )
    parser.add_argument(
        "--decode-output",
        action="store_true",
        help="Auto-decode base64 output to readable text",
    )
    parser.add_argument(
        "--eval", help="Evaluate Clojure code directly (shortcut for local-eval)"
    )
    parser.add_argument(
        "--list-tools", action="store_true", help="List available tools"
    )
    parser.add_argument(
        "--output",
        choices=["raw", "json", "clj"],
        default="json",
        help="Output format: raw (full MCP), json (tool response), clj (Clojure value for local-eval)",
    )
    parser.add_argument(
        "--quiet", action="store_true", help="Only show essential output"
    )

    args = parser.parse_args()

    if not args.list_tools and not args.tool and not args.eval:
        parser.error("Must specify --list-tools, --tool, or --eval")

    # Code parameters require --tool
    if (args.code or args.code_stdin or args.load_code_file) and not args.tool:
        parser.error(
            "--code/--code-stdin/--load-code-file requires --tool to be specified"
        )

    # Code parameters are mutually exclusive (now only 3)
    code_params = sum(
        [
            bool(args.code),
            bool(args.code_stdin),
            bool(args.load_code_file),
        ]
    )
    if code_params > 1:
        parser.error(
            "Code parameters are mutually exclusive: --code, --code-stdin, --load-code-file"
        )

    # --input-base64 requires a code input method
    if args.input_base64 and code_params == 0:
        parser.error(
            "--input-base64 requires one of: --code, --code-stdin, --load-code-file"
        )

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
            response = await explorer.call_tool("local-eval", {"code": args.eval})

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

            # If --connection is specified, add it to the arguments
            if args.connection:
                tool_args["connection"] = args.connection

            # Get code content from various sources
            code_content = None
            if args.code:
                code_content = args.code
            elif args.code_stdin:
                # Read code from stdin
                code_from_stdin = sys.stdin.read().strip()
                if not code_from_stdin:
                    print("Error: No code provided on stdin", file=sys.stderr)
                    return 1
                code_content = code_from_stdin
            elif args.load_code_file:
                # Read code from file
                try:
                    with open(args.load_code_file, "r", encoding="utf-8") as f:
                        code_from_file = f.read().strip()
                    if not code_from_file:
                        print(
                            f"Error: File {args.load_code_file} is empty",
                            file=sys.stderr,
                        )
                        return 1
                    code_content = code_from_file
                except FileNotFoundError:
                    print(
                        f"Error: File {args.load_code_file} not found", file=sys.stderr
                    )
                    return 1
                except Exception as e:
                    print(
                        f"Error reading file {args.load_code_file}: {e}",
                        file=sys.stderr,
                    )
                    return 1

            # Apply input format flag to determine MCP tool parameter
            if code_content:
                if args.input_base64:
                    # Route to input-base64 parameter (new interface)
                    tool_args["code"] = code_content
                    tool_args["input-base64"] = True
                else:
                    # Standard code parameter
                    tool_args["code"] = code_content

            # Add output-base64 parameter if requested
            if args.output_base64:
                tool_args["output-base64"] = True

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
                    # Auto-decode base64 output if requested
                    if args.decode_output:
                        content = decode_base64_response(content)

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
                # Show Clojure value (for local-eval)
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
