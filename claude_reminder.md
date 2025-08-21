🚨 **CRITICAL REMINDER FOR CLAUDE**

📋 **USE MEMORY FOR PROJECT STATE TRACKING**
- Query memory at session start: `mcp__memory__recall_memory "mcp-nrepl project status"`
- Store project progress in memory with tags: ["mcp-nrepl", "project-status", "current-work"]
- Update memory with completed work and discoveries
- Use TodoWrite tool only for internal session tracking (not persistent todos)

❌ **DO NOT USE: TODO.md files (archived as TODO-old-mess.md due to formatting chaos)**

**IMPORTANT**: Always leverage tree-sitter tools for semantic code analysis before making changes!

🎨 **CRITICAL REQUIREMENTS: CLOJURE CODE QUALITY & FORMATTING**
1. For every Clojure code change, run ./scripts/clojure_quality.sh

🐍 **CRITICAL REQUIREMENTS: PYTHON CODE QUALITY & UV USAGE**
1. For every Python code change, run ./scripts/python_quality.sh
2. Use UV wherever possible for Python package management and tool execution!

**mcp-nrepl-server project: mcp server to manage/introspect/code live applications through NREPL**

Do NOT start/stop the bridge + mcp-nrepl-server, because you have the nrepl-mcp-server running!!!
Do not use the mnrepl-mcp-client because you have the nrepl-mcp tools!!!

"Memory tags = ARRAY not string!"