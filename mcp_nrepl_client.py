#!/usr/bin/env python3
"""
Enhanced MCP-nREPL Client - A user-friendly command-line client for MCP-nREPL servers.

This client provides intuitive commands for interacting with nREPL servers through MCP,
eliminating the need for curl and providing a much better developer experience.

Features:
- Direct code evaluation with --eval
- Quick status checks with --status  
- Comprehensive testing with --test-nrepl
- Beautiful formatted output
- Interactive mode with history and completion
- nREPL-specific commands and helpers

Usage Examples:
  # Quick code evaluation
  python3 mcp_nrepl_client.py --eval "(+ 1 2 3)"
  
  # Check server status
  python3 mcp_nrepl_client.py --status
  
  # Run comprehensive nREPL tests
  python3 mcp_nrepl_client.py --test-nrepl
  
  # Interactive mode
  python3 mcp_nrepl_client.py --interactive
  
  # Pretty formatted output
  python3 mcp_nrepl_client.py --tool nrepl-status --pretty
"""

import asyncio
import argparse
import json
import sys
import os
from typing import Dict, Any, Optional, List
import httpx
from urllib.parse import urljoin
from datetime import datetime
import re

try:
    import readline  # For command history
    HAS_READLINE = True
except ImportError:
    HAS_READLINE = False

try:
    from rich.console import Console
    from rich.table import Table
    from rich.syntax import Syntax
    from rich.panel import Panel
    from rich.text import Text
    HAS_RICH = True
except ImportError:
    HAS_RICH = False


class Colors:
    """ANSI color codes for terminal output"""
    RED = '\033[91m'
    GREEN = '\033[92m'  
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    MAGENTA = '\033[95m'
    CYAN = '\033[96m'
    WHITE = '\033[97m'
    BOLD = '\033[1m'
    END = '\033[0m'


class MCPnREPLClient:
    """Enhanced MCP client specifically designed for nREPL server interaction."""
    
    def __init__(self, mcp_url: str = "http://localhost:3000/mcp", quiet: bool = False):
        self.mcp_url = mcp_url
        self.quiet = quiet
        self.console = Console() if HAS_RICH else None
        self.session_id: Optional[str] = None
        self.initialized: bool = False
        self.tools_cache: List[Dict] = []
        self.history: List[str] = []
        
        # Load command history if available
        if HAS_READLINE:
            try:
                readline.read_history_file(".mcp_history")
            except FileNotFoundError:
                pass
    
    def _save_history(self):
        """Save command history to file."""
        if HAS_READLINE:
            try:
                readline.write_history_file(".mcp_history")
            except:
                pass
    
    def _print(self, message: str, color: str = "", style: str = ""):
        """Print with optional color/style, respecting quiet mode."""
        if self.quiet:
            return
        
        if HAS_RICH and self.console:
            if style:
                self.console.print(message, style=style)
            else:
                self.console.print(message)
        else:
            print(f"{color}{message}{Colors.END}")
    
    def _error(self, message: str):
        """Print error message."""
        self._print(f"❌ {message}", Colors.RED, "bold red")
    
    def _success(self, message: str):
        """Print success message."""
        self._print(f"✅ {message}", Colors.GREEN, "bold green")
    
    def _info(self, message: str):
        """Print info message."""
        self._print(f"ℹ️  {message}", Colors.BLUE, "blue")
    
    def _warn(self, message: str):
        """Print warning message."""
        self._print(f"⚠️  {message}", Colors.YELLOW, "yellow")
    
    async def _make_request(self, method: str, params: Dict = None) -> Dict[str, Any]:
        """Make an MCP JSON-RPC request."""
        try:
            async with httpx.AsyncClient(timeout=30.0) as client:
                payload = {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": method,
                    "params": params or {}
                }
                
                response = await client.post(self.mcp_url, json=payload)
                response.raise_for_status()
                result = response.json()
                
                if "error" in result:
                    return {"error": result["error"]}
                return result.get("result", {})
                
        except Exception as e:
            return {"error": str(e)}
    
    async def connect(self) -> bool:
        """Connect to MCP server."""
        if not self.quiet:
            self._info(f"Connecting to MCP server: {self.mcp_url}")
        
        # Initialize
        result = await self._make_request("initialize", {
            "protocolVersion": "2024-11-05",
            "capabilities": {"tools": {"list": True, "call": True}},
            "clientInfo": {"name": "mcp-nrepl-client", "version": "1.0.0"}
        })
        
        if "error" in result:
            self._error(f"Connection failed: {result['error']}")
            return False
        
        self.initialized = True
        
        # Cache available tools
        await self._cache_tools()
        
        if not self.quiet:
            self._success("Connected successfully!")
        return True
    
    async def _cache_tools(self):
        """Cache available tools for faster access."""
        result = await self._make_request("tools/list")
        if "tools" in result:
            self.tools_cache = result["tools"]
    
    def _get_tool_names(self) -> List[str]:
        """Get list of available tool names."""
        return [tool["name"] for tool in self.tools_cache]
    
    def _find_tool(self, name: str) -> Optional[Dict]:
        """Find tool by name."""
        for tool in self.tools_cache:
            if tool["name"] == name:
                return tool
        return None
    
    async def list_tools(self, format: str = "table") -> bool:
        """List available tools in various formats."""
        if not self.tools_cache:
            await self._cache_tools()
        
        if not self.tools_cache:
            self._error("No tools available")
            return False
        
        if format == "json":
            print(json.dumps(self.tools_cache, indent=2))
        elif format == "table" and HAS_RICH and self.console:
            table = Table(title="Available MCP Tools")
            table.add_column("Tool Name", style="cyan")
            table.add_column("Description", style="white")
            table.add_column("Parameters", style="yellow")
            
            for tool in self.tools_cache:
                params = []
                if "inputSchema" in tool and tool["inputSchema"]:
                    props = tool["inputSchema"].get("properties", {})
                    required = tool["inputSchema"].get("required", [])
                    for param, info in props.items():
                        param_str = f"{param}" + ("*" if param in required else "")
                        params.append(param_str)
                
                table.add_row(
                    tool["name"],
                    tool.get("description", "No description"),
                    ", ".join(params) if params else "None"
                )
            
            self.console.print(table)
        else:
            # Simple text format
            self._print(f"\n📋 Available Tools ({len(self.tools_cache)}):", Colors.BOLD)
            for i, tool in enumerate(self.tools_cache, 1):
                self._print(f"  {i}. {tool['name']}", Colors.CYAN)
                self._print(f"     {tool.get('description', 'No description')}")
                if "inputSchema" in tool and tool["inputSchema"]:
                    props = tool["inputSchema"].get("properties", {})
                    if props:
                        params = list(props.keys())
                        self._print(f"     Parameters: {', '.join(params)}")
                print()
        
        return True
    
    async def call_tool(self, tool_name: str, arguments: Dict[str, Any], pretty: bool = False) -> Dict[str, Any]:
        """Call a specific tool."""
        if not self._find_tool(tool_name):
            self._error(f"Tool '{tool_name}' not found. Available tools: {', '.join(self._get_tool_names())}")
            return {"error": "Tool not found"}
        
        if not self.quiet:
            self._info(f"Calling tool: {tool_name}")
            if arguments:
                self._print(f"Arguments: {json.dumps(arguments, indent=2)}")
        
        result = await self._make_request("tools/call", {
            "name": tool_name,
            "arguments": arguments
        })
        
        if "error" in result:
            self._error(f"Tool call failed: {result['error']}")
            return result
        
        # Format output
        if pretty and HAS_RICH and self.console:
            self._format_pretty_result(result)
        elif not self.quiet:
            print(json.dumps(result, indent=2))
        
        return result
    
    def _format_pretty_result(self, result: Dict[str, Any]):
        """Format result with rich formatting."""
        if not HAS_RICH or not self.console:
            print(json.dumps(result, indent=2))
            return
        
        if "content" in result:
            for item in result["content"]:
                if item.get("type") == "text":
                    text = item["text"]
                    
                    # Try to parse as JSON for pretty formatting
                    try:
                        parsed = json.loads(text)
                        syntax = Syntax(json.dumps(parsed, indent=2), "json", theme="monokai")
                        self.console.print(Panel(syntax, title="Result"))
                    except:
                        # Not JSON, display as plain text
                        self.console.print(Panel(text, title="Result"))
        else:
            syntax = Syntax(json.dumps(result, indent=2), "json", theme="monokai")
            self.console.print(Panel(syntax, title="Result"))
    
    async def eval_code(self, code: str, ns: str = None, pretty: bool = False) -> Dict[str, Any]:
        """Evaluate Clojure code via nREPL."""
        args = {"code": code}
        if ns:
            args["ns"] = ns
        
        if not self.quiet:
            self._info(f"Evaluating: {code}")
        
        result = await self.call_tool("nrepl-eval", args, pretty)
        
        # Add to history
        self.history.append(code)
        
        return result
    
    async def get_status(self, pretty: bool = False) -> Dict[str, Any]:
        """Get nREPL server status."""
        return await self.call_tool("nrepl-status", {}, pretty)
    
    async def run_nrepl_tests(self, summary: bool = False) -> bool:
        """Run comprehensive nREPL tests."""
        if not summary:
            self._info("Running comprehensive nREPL tests...")
        
        tests = [
            ("Connection Status", "nrepl-status", {}),
            ("Basic Arithmetic", "nrepl-eval", {"code": "(+ 1 2 3)"}),
            ("String Operations", "nrepl-eval", {"code": "(str \"Hello\" \" \" \"World\")"}),
            ("Data Structures", "nrepl-eval", {"code": "(count [1 2 3 4 5])"}),
            ("Function Definition", "nrepl-eval", {"code": "(defn test-fn [x] (* x 2))"}),
            ("Function Call", "nrepl-eval", {"code": "(test-fn 21)"}),
            ("Comprehensive Health Test", "nrepl-test", {})
        ]
        
        passed = 0
        total = len(tests)
        
        for test_name, tool_name, args in tests:
            if not summary:
                self._info(f"Running test: {test_name}")
            result = await self.call_tool(tool_name, args)
            
            if "error" not in result:
                # Check if it's an error result from the tool
                if "content" in result:
                    content = result["content"][0].get("text", "") if result["content"] else ""
                    if not content.startswith("❌"):
                        if not summary:
                            self._success(f"✓ {test_name}")
                        passed += 1
                    else:
                        if not summary:
                            self._error(f"✗ {test_name}: {content}")
                else:
                    if not summary:
                        self._success(f"✓ {test_name}")
                    passed += 1
            else:
                if not summary:
                    self._error(f"✗ {test_name}: {result['error']}")
        
        self._print(f"\n📊 Test Results: {passed}/{total} passed", Colors.BOLD)
        if passed == total:
            self._success("All tests passed! 🎉")
        else:
            self._warn(f"{total - passed} tests failed")
        
        return passed == total
    
    async def interactive_mode(self):
        """Enhanced interactive mode with nREPL-specific features."""
        self._print("\n🎮 Interactive MCP-nREPL Client", Colors.BOLD)
        self._print("Commands:", Colors.CYAN)
        self._print("  eval <clojure-code>     - Evaluate Clojure code")
        self._print("  status                  - Show nREPL server status")
        self._print("  tools                   - List available tools")
        self._print("  tool <name> [args]      - Call a specific tool")
        self._print("  test                    - Run comprehensive tests")
        self._print("  history                 - Show evaluation history")
        self._print("  clear                   - Clear screen")
        self._print("  quit                    - Exit")
        print()
        
        while True:
            try:
                if HAS_READLINE:
                    # Set up tab completion
                    readline.set_completer(self._completer)
                    readline.parse_and_bind('tab: complete')
                
                command = input("nrepl> ").strip()
                if not command:
                    continue
                
                parts = command.split(' ', 1)
                cmd = parts[0].lower()
                args = parts[1] if len(parts) > 1 else ""
                
                if cmd == 'quit' or cmd == 'exit':
                    break
                elif cmd == 'eval':
                    if args:
                        await self.eval_code(args, pretty=True)
                    else:
                        self._error("Usage: eval <clojure-code>")
                elif cmd == 'status':
                    await self.get_status(pretty=True)
                elif cmd == 'tools':
                    await self.list_tools("table" if HAS_RICH else "text")
                elif cmd == 'tool':
                    if args:
                        tool_args = args.split(' ', 1)
                        tool_name = tool_args[0]
                        tool_params = {}
                        if len(tool_args) > 1:
                            try:
                                tool_params = json.loads(tool_args[1])
                            except:
                                self._error("Invalid JSON arguments")
                                continue
                        await self.call_tool(tool_name, tool_params, pretty=True)
                    else:
                        self._error("Usage: tool <name> [json-args]")
                elif cmd == 'test':
                    await self.run_nrepl_tests()
                elif cmd == 'history':
                    if self.history:
                        self._print("📜 Evaluation History:", Colors.CYAN)
                        for i, code in enumerate(self.history[-10:], 1):  # Last 10
                            print(f"  {i}. {code}")
                    else:
                        self._info("No evaluation history")
                elif cmd == 'clear':
                    os.system('clear' if os.name == 'posix' else 'cls')
                else:
                    self._error(f"Unknown command: {cmd}")
                    
            except (EOFError, KeyboardInterrupt):
                break
            except Exception as e:
                self._error(f"Error: {e}")
        
        self._save_history()
        self._info("Goodbye!")
    
    def _completer(self, text: str, state: int) -> Optional[str]:
        """Tab completion for commands and tool names."""
        commands = ['eval', 'status', 'tools', 'tool', 'test', 'history', 'clear', 'quit']
        tool_names = self._get_tool_names()
        all_completions = commands + tool_names
        
        matches = [cmd for cmd in all_completions if cmd.startswith(text)]
        if state < len(matches):
            return matches[state]
        return None


async def main():
    """Main entry point with enhanced argument parsing."""
    parser = argparse.ArgumentParser(
        description="Enhanced MCP-nREPL Client - Never use curl again!",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Quick code evaluation
  %(prog)s --eval "(+ 1 2 3)"
  %(prog)s --eval "(defn hello [name] (str \\"Hello \\" name))" 
  %(prog)s --eval "(hello \\"World\\")"
  
  # Server health & status
  %(prog)s --status                    # Check connection
  %(prog)s --status --pretty           # Formatted status
  %(prog)s --status --quiet            # Minimal output
  
  # Comprehensive testing
  %(prog)s --test-nrepl                # Full test suite
  %(prog)s --test-nrepl --summary      # Summary only
  %(prog)s --test-nrepl --quiet        # For automation
  
  # Tool discovery & usage
  %(prog)s --tools                     # List tools
  %(prog)s --tools --format table     # Rich table format
  %(prog)s --tools --format json      # JSON output
  
  # Direct tool calling
  %(prog)s --tool nrepl-status
  %(prog)s --tool nrepl-eval --args '{"code": "(* 6 7)"}'
  %(prog)s --tool nrepl-new-session
  
  # Interactive mode (full REPL)
  %(prog)s --interactive               # Start interactive mode
  
  # Automation & scripting
  %(prog)s --eval "(+ 1 2)" --quiet   # Returns: 3
  %(prog)s --test-nrepl --summary --quiet  # Exit code 0/1
  
Common Interactive Commands:
  eval (+ 1 2 3)        # Evaluate Clojure code
  status                # Show server status
  tools                 # List available tools
  test                  # Run comprehensive tests
  history               # Show evaluation history
  quit                  # Exit interactive mode

Output Modes:
  --pretty              # Rich formatting with colors and tables
  --quiet               # Minimal output perfect for scripts
  --format json         # Machine-readable JSON output
  --format table        # Beautiful table formatting (requires 'rich')
  
Installation Tips:
  pip install rich     # For beautiful formatting (recommended)
  pip install readline # For command history (usually included)

Never use curl again! This client provides everything you need for
efficient MCP-nREPL interaction with an intuitive command-line interface.
        """
    )
    
    # Connection options
    parser.add_argument("--url", default="http://localhost:3000/mcp", 
                       help="MCP server URL (default: http://localhost:3000/mcp)")
    
    # Quick actions
    parser.add_argument("--eval", metavar="CODE", help="Evaluate Clojure code and exit")
    parser.add_argument("--status", action="store_true", help="Show server status and exit")
    parser.add_argument("--test-nrepl", action="store_true", help="Run comprehensive nREPL tests")
    parser.add_argument("--summary", action="store_true", help="Show only summary for test results")
    parser.add_argument("--tools", action="store_true", help="List available tools")
    
    # Tool calling
    parser.add_argument("--tool", metavar="NAME", help="Call specific tool")
    parser.add_argument("--args", default="{}", help="Tool arguments as JSON (default: {})")
    
    # Output options
    parser.add_argument("--pretty", action="store_true", help="Pretty formatted output")
    parser.add_argument("--quiet", "-q", action="store_true", help="Minimal output")
    parser.add_argument("--format", choices=["text", "json", "table"], default="text",
                       help="Output format for tools list")
    
    # Interactive mode
    parser.add_argument("--interactive", "-i", action="store_true", help="Interactive mode")
    
    args = parser.parse_args()
    
    # Check for rich library recommendation
    if not HAS_RICH and not args.quiet:
        print("💡 Install 'rich' for beautiful formatted output: pip install rich")
    
    client = MCPnREPLClient(args.url, args.quiet)
    
    # Connect to server
    if not await client.connect():
        sys.exit(1)
    
    try:
        # Handle quick actions
        if args.eval:
            result = await client.eval_code(args.eval, pretty=args.pretty)
            if "error" in result:
                sys.exit(1)
        elif args.status:
            result = await client.get_status(pretty=args.pretty)
            if "error" in result:
                sys.exit(1)
        elif args.test_nrepl:
            success = await client.run_nrepl_tests(summary=args.summary)
            if not success:
                sys.exit(1)
        elif args.tools:
            await client.list_tools(args.format)
        elif args.tool:
            try:
                tool_args = json.loads(args.args)
            except json.JSONDecodeError:
                print("❌ Invalid JSON in --args")
                sys.exit(1)
            result = await client.call_tool(args.tool, tool_args, args.pretty)
            if "error" in result:
                sys.exit(1)
        elif args.interactive:
            await client.interactive_mode()
        else:
            # Default: list tools and enter interactive mode
            await client.list_tools(args.format)
            print("\nUse --help for more options, or --interactive for interactive mode")
    
    except KeyboardInterrupt:
        if not args.quiet:
            print("\n👋 Interrupted by user")
        sys.exit(0)


if __name__ == "__main__":
    asyncio.run(main())