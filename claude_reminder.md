🚨 **CRITICAL REMINDER FOR CLAUDE**

📋 **USE TODO.md FOR ALL TODO MANAGEMENT**
- Edit TODO.md directly for all todo changes
- Mark completed items with [x] in TODO.md
- Update progress/status in TODO.md
- Check TODO.md at start of new tasks

❌ **DO NOT USE: TodoWrite tool or Context memory for todos**

**IMPORTANT**: Always leverage tree-sitter tools for semantic code analysis before making changes!

🎨 **CRITICAL REQUIREMENTS: CLOJURE CODE QUALITY & FORMATTING**
1. For every Clojure code change, run ./scripts/clojure_quality.sh

🐍 **CRITICAL REQUIREMENTS: PYTHON CODE QUALITY & UV USAGE**
1. For every Python code change, run ./scripts/python_quality.sh
2. Use UV wherever possible for Python package management and tool execution!

**mcp-nrepl-server project: mcp server to manage/introspect/code live applications through NREPL**

Test: **mcp-client** => steamableHTTP => **persistent-bridge** => stdio => **mcp-nrepl-server** => socket => **nrepl-server**
bridge + mcp-nrepl-server are started in background (&): scripts/start-http-bridge.sh
nrepl-server start/stop/status: scripts/nrepl_test_server.py
mcp-client for testing: scripts/mcp_nrepl_client.py
do NOT pass env var to script but use cmdline parameter, like: uv run python scripts/mcp_nrepl_client.py --base-url http://localhost:3000/mcp
Note: you have to restart the bridge to test new code... a running bridge will run the code when it was started!!!

"Memory tags = ARRAY not string!"