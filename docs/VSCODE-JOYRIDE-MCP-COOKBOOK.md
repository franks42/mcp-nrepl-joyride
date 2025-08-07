# VS Code MCP-nREPL-Joyride Cookbook

## Overview

This cookbook demonstrates how to **drive VS Code through the MCP-nREPL-Joyride-Calva integration**, enabling powerful editor automation and enhancement workflows. You'll learn to control VS Code using Clojure code, automate development tasks, and create custom editor experiences through our unified MCP interface.

## Architecture Overview

```
____________________________________
|               vscode              |
|-----------------------------------|
|      calva      |      joyride    |
|   nrepl-client  |   nrepl-server  |
------------------------------------|
         ||                /\                                  
         ||                ||       
_________\/________________||_______ 
|   nrepl-server      nrepl-client  |
|                          /\       |
|                          ||       |
|   mcp-nrepl-app          ||       |
|                      mcp-server   |
------------------------------------
                      /\         /\
                      ||         ||
                      ||         ||
                  mcp-client     AI
                      /\
                      ||
                      ||
                    human
```

**Architecture Components:**

**1. VS Code Process (Single Process):**
- **VS Code:** Main editor application
- **Calva:** nREPL client module for connecting to external Clojure REPLs
- **Joyride:** Embedded nREPL server (port 7889) for VS Code automation

**2. MCP-nREPL Application Process:**
- **MCP Server Module:** Handles MCP function calls via HTTP/WebSocket/stdio
- **nREPL Client Module:** Translates MCP requests to nREPL messages → Joyride
- **nREPL Server Module:** Debugging server (port 7888) for live introspection

**3. Client Connections:**
- **AI → MCP Server:** Direct MCP function calls
- **Human → Python Client → MCP Server:** Terminal-based MCP requests
- **Calva → MCP-nREPL Server:** Debug/inspect the bridge application
- **MCP-nREPL Client → Joyride:** Control VS Code via nREPL

## Prerequisites

### 1. VS Code Extensions Setup

```bash
# Install required extensions
code --install-extension betterthantomorrow.calva
code --install-extension betterthantomorrow.joyride

# Optional but recommended
code --install-extension continue.continue  # For AI assistance
```

### 2. Joyride Configuration

Create `.joyride/scripts/init.cljs` in your workspace:

```clojure
(ns init
  (:require ["vscode" :as vscode]
            [promesa.core :as p]))

;; Start nREPL server on startup
(defn start-nrepl []
  (println "Starting Joyride nREPL server...")
  ;; Joyride typically starts nREPL on port 7889
  (vscode/commands.executeCommand "joyride.startNRepl"))

;; Auto-start on activation
(start-nrepl)

;; Register custom commands
(vscode/commands.registerCommand 
  "mcp-nrepl.hello"
  (fn []
    (vscode/window.showInformationMessage "Hello from MCP-nREPL!")))
```

### 3. MCP Server Connection

Configure VS Code settings.json:

```json
{
  "mcpServers": {
    "mcp-nrepl-joyride": {
      "command": "bb",
      "args": ["src/mcp_nrepl_proxy/core.clj", "--mode", "http", "--port", "3001"],
      "env": {
        "NREPL_HOST": "localhost",
        "NREPL_PORT": "7889"
      }
    }
  }
}
```

### 4. Connect MCP to Joyride

```bash
# Start MCP-nREPL bridge connecting to Joyride
./start-mcp-http-server.sh --port 7889

# Verify connection
python3 ./mcp_nrepl_client.py --eval "(+ 1 2 3)" --quiet
# Result: 6

# Check VS Code availability
python3 ./mcp_nrepl_client.py --eval "(require '[\"vscode\" :as vscode])" --quiet
python3 ./mcp_nrepl_client.py --eval "vscode/version" --quiet
```

## Part 1: Basic VS Code Control

### Accessing VS Code API

```bash
# Get VS Code version
python3 ./mcp_nrepl_client.py --eval "vscode/version" --quiet

# Get workspace information
python3 ./mcp_nrepl_client.py --eval "(.name (first vscode/workspace.workspaceFolders))" --quiet

# Get current theme
python3 ./mcp_nrepl_client.py --eval "(.get vscode/workspace.configuration \"workbench.colorTheme\")" --quiet
```

### Window and Editor Control

```bash
# Show information message
python3 ./mcp_nrepl_client.py --eval "(vscode/window.showInformationMessage \"Hello from MCP-nREPL!\")" --quiet

# Show warning message
python3 ./mcp_nrepl_client.py --eval "(vscode/window.showWarningMessage \"This is a warning\")" --quiet

# Show error message
python3 ./mcp_nrepl_client.py --eval "(vscode/window.showErrorMessage \"Example error message\")" --quiet

# Get active editor info
python3 ./mcp_nrepl_client.py --eval "(when-let [editor vscode/window.activeTextEditor] (.-fileName (.-document editor)))" --quiet
```

### Quick Pick and Input

```bash
# Show quick pick menu
python3 ./mcp_nrepl_client.py --eval "
(p/let [choice (vscode/window.showQuickPick 
                 #js [\"Option 1\" \"Option 2\" \"Option 3\"]
                 #js {:placeHolder \"Choose an option\"})]
  (vscode/window.showInformationMessage (str \"You chose: \" choice)))" --quiet

# Get user input
python3 ./mcp_nrepl_client.py --eval "
(p/let [input (vscode/window.showInputBox 
                #js {:prompt \"Enter your name\"
                     :placeHolder \"John Doe\"})]
  (vscode/window.showInformationMessage (str \"Hello, \" input \"!\")))" --quiet
```

## Part 2: Document and Editor Manipulation

### Working with Documents

```bash
# Get current document content
python3 ./mcp_nrepl_client.py --eval "
(when-let [editor vscode/window.activeTextEditor]
  (let [doc (.-document editor)]
    {:fileName (.-fileName doc)
     :lineCount (.-lineCount doc)
     :isDirty (.-isDirty doc)
     :languageId (.-languageId doc)}))" --quiet

# Get selected text
python3 ./mcp_nrepl_client.py --eval "
(when-let [editor vscode/window.activeTextEditor]
  (let [selection (.-selection editor)
        doc (.-document editor)]
    (.getText doc selection)))" --quiet

# Get current line
python3 ./mcp_nrepl_client.py --eval "
(when-let [editor vscode/window.activeTextEditor]
  (let [position (.-active (.-selection editor))
        doc (.-document editor)]
    (.getText (.lineAt doc (.-line position)))))" --quiet
```

### Modifying Documents

```bash
# Insert text at cursor
python3 ./mcp_nrepl_client.py --eval "
(when-let [editor vscode/window.activeTextEditor]
  (.edit editor 
    (fn [builder]
      (.insert builder (.-active (.-selection editor)) \"Hello from MCP!\"))))" --quiet

# Replace selected text
python3 ./mcp_nrepl_client.py --eval "
(when-let [editor vscode/window.activeTextEditor]
  (let [selection (.-selection editor)]
    (.edit editor
      (fn [builder]
        (.replace builder selection \"Replaced text\")))))" --quiet

# Add line at end of document
python3 ./mcp_nrepl_client.py --eval "
(when-let [editor vscode/window.activeTextEditor]
  (let [doc (.-document editor)
        last-line (.-lineCount doc)]
    (.edit editor
      (fn [builder]
        (.insert builder (vscode/Position. last-line 0) \"\\n;; Added by MCP-nREPL\")))))" --quiet
```

## Part 3: File and Workspace Operations

### File Operations

```bash
# Create new file
python3 ./mcp_nrepl_client.py --eval "
(p/let [doc (vscode/workspace.openTextDocument 
              #js {:content \"(ns my-new-file)\\n\\n(defn hello []\\n  :world)\"})]
  (vscode/window.showTextDocument doc))" --quiet

# Open existing file
python3 ./mcp_nrepl_client.py --eval "
(p/let [uri (vscode/Uri.file \"/path/to/file.clj\")
        doc (vscode/workspace.openTextDocument uri)]
  (vscode/window.showTextDocument doc))" --quiet

# Save current file
python3 ./mcp_nrepl_client.py --eval "
(when-let [doc (.-document vscode/window.activeTextEditor)]
  (.save doc))" --quiet

# Save all files
python3 ./mcp_nrepl_client.py --eval "(vscode/workspace.saveAll)" --quiet
```

### Workspace Navigation

```bash
# List workspace folders
python3 ./mcp_nrepl_client.py --eval "
(map #(.-name %) vscode/workspace.workspaceFolders)" --quiet

# Find files in workspace
python3 ./mcp_nrepl_client.py --eval "
(p/let [files (vscode/workspace.findFiles \"**/*.clj\" \"**/node_modules/**\")]
  (take 10 (map #(.-path %) files)))" --quiet

# Get workspace configuration
python3 ./mcp_nrepl_client.py --eval "
(.get (vscode/workspace.getConfiguration \"editor\") \"fontSize\")" --quiet

# Update workspace settings
python3 ./mcp_nrepl_client.py --eval "
(.update (vscode/workspace.getConfiguration \"editor\") 
         \"fontSize\" 14 
         vscode/ConfigurationTarget.Workspace)" --quiet
```

## Part 4: Command Execution and Extension Interaction

### VS Code Commands

```bash
# Execute built-in commands
python3 ./mcp_nrepl_client.py --eval "(vscode/commands.executeCommand \"workbench.action.toggleSidebarVisibility\")" --quiet
python3 ./mcp_nrepl_client.py --eval "(vscode/commands.executeCommand \"workbench.action.terminal.toggleTerminal\")" --quiet
python3 ./mcp_nrepl_client.py --eval "(vscode/commands.executeCommand \"editor.action.formatDocument\")" --quiet

# Get all available commands
python3 ./mcp_nrepl_client.py --eval "(p/let [cmds (vscode/commands.getCommands)] (take 20 cmds))" --quiet

# Search for specific commands
python3 ./mcp_nrepl_client.py --eval "
(p/let [cmds (vscode/commands.getCommands)]
  (filter #(clojure.string/includes? % \"calva\") cmds))" --quiet
```

### Calva Integration

```bash
# Execute Calva commands
python3 ./mcp_nrepl_client.py --eval "(vscode/commands.executeCommand \"calva.jackIn\")" --quiet
python3 ./mcp_nrepl_client.py --eval "(vscode/commands.executeCommand \"calva.loadFile\")" --quiet
python3 ./mcp_nrepl_client.py --eval "(vscode/commands.executeCommand \"calva.evaluateCurrentForm\")" --quiet

# Connect to Calva REPL
python3 ./mcp_nrepl_client.py --eval "(vscode/commands.executeCommand \"calva.connect\" #js {:port 7889})" --quiet
```

### Joyride Scripts

```bash
# Run Joyride script
python3 ./mcp_nrepl_client.py --eval "(vscode/commands.executeCommand \"joyride.runWorkspaceScript\" \"my-script\")" --quiet

# List Joyride scripts
python3 ./mcp_nrepl_client.py --eval "(vscode/commands.executeCommand \"joyride.listScripts\")" --quiet

# Reload Joyride scripts
python3 ./mcp_nrepl_client.py --eval "(vscode/commands.executeCommand \"joyride.reloadScripts\")" --quiet
```

## Part 5: Advanced Automation Patterns

### Custom Command Registration

```bash
# Register a custom command
python3 ./mcp_nrepl_client.py --eval "
(def my-command-disposable
  (vscode/commands.registerCommand 
    \"mcp-nrepl.customGreeting\"
    (fn []
      (p/let [name (vscode/window.showInputBox 
                     #js {:prompt \"What's your name?\"})]
        (vscode/window.showInformationMessage 
          (str \"Hello, \" name \"! From MCP-nREPL\"))))))" --quiet

# Execute the custom command
python3 ./mcp_nrepl_client.py --eval "(vscode/commands.executeCommand \"mcp-nrepl.customGreeting\")" --quiet
```

### Status Bar Integration

```bash
# Create status bar item
python3 ./mcp_nrepl_client.py --eval "
(def status-item (vscode/window.createStatusBarItem 
                   vscode/StatusBarAlignment.Left 
                   100))
(set! (.-text status-item) \"🔗 MCP-nREPL\")
(set! (.-tooltip status-item) \"MCP-nREPL Bridge Active\")
(set! (.-command status-item) \"mcp-nrepl.showInfo\")
(.show status-item)" --quiet

# Update status bar
python3 ./mcp_nrepl_client.py --eval "
(set! (.-text status-item) \"🟢 Connected\")" --quiet
```

### Terminal Control

```bash
# Create new terminal
python3 ./mcp_nrepl_client.py --eval "
(def term (vscode/window.createTerminal \"MCP Terminal\"))
(.show term)
(.sendText term \"echo 'Hello from MCP-nREPL!'\")" --quiet

# Send commands to terminal
python3 ./mcp_nrepl_client.py --eval "
(.sendText term \"ls -la\")" --quiet

# Get active terminal
python3 ./mcp_nrepl_client.py --eval "
(when-let [active-term vscode/window.activeTerminal]
  (.sendText active-term \"pwd\"))" --quiet
```

### Decorations and Highlights

```bash
# Create text decorations
python3 ./mcp_nrepl_client.py --eval "
(def highlight-type 
  (vscode/window.createTextEditorDecorationType 
    #js {:backgroundColor \"rgba(255,255,0,0.3)\"
         :border \"1px solid gold\"}))

(when-let [editor vscode/window.activeTextEditor]
  (let [doc (.-document editor)
        range (vscode/Range. 0 0 0 10)]
    (.setDecorations editor highlight-type #js [range])))" --quiet
```

## Part 6: Project-Specific Workflows

### Clojure Project Automation

```bash
# Load project file utility
cat > /tmp/project-utils.clj << 'EOF'
(ns project-utils
  (:require ["vscode" :as vscode]
            [promesa.core :as p]
            [clojure.string :as str]))

(defn find-project-files
  "Find all Clojure files in project"
  []
  (p/let [files (vscode/workspace.findFiles "**/*.{clj,cljs,cljc}" "**/node_modules/**")]
    (map #(.-fsPath %) files)))

(defn load-namespace-from-file
  "Load namespace from current file"
  []
  (when-let [editor vscode/window.activeTextEditor]
    (let [doc (.-document editor)
          text (.getText doc)
          ns-match (re-find #"^\(ns\s+([\w.-]+)" text)]
      (when ns-match
        (vscode/commands.executeCommand "calva.loadFile")
        (second ns-match)))))

(defn run-tests-in-file
  "Run tests in current file"
  []
  (vscode/commands.executeCommand "calva.runTestsInCurrentNS"))

(defn format-current-file
  "Format current Clojure file"
  []
  (vscode/commands.executeCommand "calva.fmt.formatCurrentForm"))
EOF

python3 ./mcp_nrepl_client.py --tool nrepl-load-file --args '{"file-path": "/tmp/project-utils.clj"}' --quiet

# Use the utilities
python3 ./mcp_nrepl_client.py --eval "(project-utils/find-project-files)" --quiet
python3 ./mcp_nrepl_client.py --eval "(project-utils/load-namespace-from-file)" --quiet
```

### Live Development Enhancement

```bash
# Auto-reload on save
python3 ./mcp_nrepl_client.py --eval "
(def reload-on-save
  (vscode/workspace.onDidSaveTextDocument
    (fn [doc]
      (when (str/ends-with? (.-fileName doc) \".clj\")
        (vscode/window.showInformationMessage 
          (str \"Reloading \" (.-fileName doc)))
        (vscode/commands.executeCommand \"calva.loadFile\")))))" --quiet

# Watch for file changes
python3 ./mcp_nrepl_client.py --eval "
(def file-watcher
  (vscode/workspace.createFileSystemWatcher \"**/*.clj\"))
(.onDidChange file-watcher
  (fn [uri]
    (println \"File changed:\" (.-fsPath uri))))" --quiet
```

### Code Analysis Integration

```bash
# Analyze current file
python3 ./mcp_nrepl_client.py --eval "
(defn analyze-current-file []
  (when-let [editor vscode/window.activeTextEditor]
    (let [doc (.-document editor)
          text (.getText doc)
          lines (str/split-lines text)]
      {:total-lines (count lines)
       :blank-lines (count (filter str/blank? lines))
       :comment-lines (count (filter #(str/starts-with? (str/trim %) \";\") lines))
       :function-count (count (re-seq #\"defn\\s+\" text))
       :namespace (second (re-find #\"^\\(ns\\s+([\\w.-]+)\" text))})))" --quiet

python3 ./mcp_nrepl_client.py --eval "(analyze-current-file)" --quiet
```

## Part 7: Debugging and Development Tools

### REPL Integration

```bash
# Send to REPL
python3 ./mcp_nrepl_client.py --eval "
(defn send-to-repl [code]
  (vscode/commands.executeCommand \"calva.evaluateSelection\" 
    #js {:text code :ns \"user\"}))" --quiet

# Evaluate and show result
python3 ./mcp_nrepl_client.py --eval "
(send-to-repl \"(+ 1 2 3)\")" --quiet

# Interactive evaluation
python3 ./mcp_nrepl_client.py --eval "
(p/let [code (vscode/window.showInputBox 
               #js {:prompt \"Enter Clojure code to evaluate\"})]
  (send-to-repl code))" --quiet
```

### Debugging Helpers

```bash
# Add debug markers
python3 ./mcp_nrepl_client.py --eval "
(defn add-debug-marker [line-num]
  (when-let [editor vscode/window.activeTextEditor]
    (.edit editor
      (fn [builder]
        (let [pos (vscode/Position. line-num 0)]
          (.insert builder pos \"(tap> \\\"Debug point\\\")\\n\"))))))" --quiet

# Toggle debug output
python3 ./mcp_nrepl_client.py --eval "
(def debug-channel (vscode/window.createOutputChannel \"MCP Debug\"))
(.show debug-channel)
(.appendLine debug-channel \"Debug session started\")" --quiet
```

## Part 8: Custom VS Code Extension via MCP

### Building a Mini Extension

```bash
# Create a complete mini-extension via MCP
cat > /tmp/mcp-extension.clj << 'EOF'
(ns mcp-extension
  (:require ["vscode" :as vscode]
            [promesa.core :as p]))

;; Extension state
(def state (atom {:commands []
                  :status-items []
                  :terminals []}))

;; Main activation function
(defn activate []
  (println "MCP Extension Activating...")
  
  ;; Register main command palette entry
  (let [cmd (vscode/commands.registerCommand
              "mcp.showMenu"
              show-command-menu)]
    (swap! state update :commands conj cmd))
  
  ;; Create status bar
  (let [status (create-status-bar)]
    (swap! state update :status-items conj status))
  
  ;; Set up file watchers
  (setup-file-watchers)
  
  (vscode/window.showInformationMessage "MCP Extension Activated!"))

(defn show-command-menu []
  (p/let [choice (vscode/window.showQuickPick
                   #js ["📁 Open Project File"
                        "🔍 Search Symbol"
                        "🧪 Run Tests"
                        "📊 Show Statistics"
                        "🔄 Reload Extension"]
                   #js {:placeHolder "Choose MCP action"})]
    (case choice
      "📁 Open Project File" (open-project-file)
      "🔍 Search Symbol" (search-symbol)
      "🧪 Run Tests" (run-all-tests)
      "📊 Show Statistics" (show-statistics)
      "🔄 Reload Extension" (reload-extension)
      nil)))

(defn create-status-bar []
  (let [item (vscode/window.createStatusBarItem 
               vscode/StatusBarAlignment.Right 99)]
    (set! (.-text item) "🚀 MCP")
    (set! (.-tooltip item) "MCP-nREPL Extension")
    (set! (.-command item) "mcp.showMenu")
    (.show item)
    item))

(defn setup-file-watchers []
  (let [watcher (vscode/workspace.createFileSystemWatcher 
                  "**/*.{clj,cljs,cljc}")]
    (.onDidCreate watcher 
      (fn [uri] 
        (vscode/window.showInformationMessage 
          (str "New file: " (.-fsPath uri)))))
    watcher))

(defn open-project-file []
  (p/let [files (vscode/workspace.findFiles "**/*.clj" nil 100)
          paths (map #(.-fsPath %) files)
          choice (vscode/window.showQuickPick 
                   (clj->js paths)
                   #js {:placeHolder "Select file to open"})]
    (when choice
      (p/let [doc (vscode/workspace.openTextDocument choice)]
        (vscode/window.showTextDocument doc)))))

(defn search-symbol []
  (p/let [symbol (vscode/window.showInputBox 
                   #js {:prompt "Enter symbol to search"
                        :placeHolder "e.g., defn, map, user/my-func"})]
    (when symbol
      (vscode/commands.executeCommand "workbench.action.findInFiles" 
        #js {:query symbol :isRegex false}))))

(defn run-all-tests []
  (vscode/commands.executeCommand "calva.runAllTests"))

(defn show-statistics []
  (p/let [files (vscode/workspace.findFiles "**/*.{clj,cljs}" nil)]
    (let [stats {:total-files (count files)
                 :workspace (.-name (first vscode/workspace.workspaceFolders))}]
      (vscode/window.showInformationMessage 
        (str "Project: " (:workspace stats) 
             " | Files: " (:total-files stats))))))

(defn reload-extension []
  (vscode/window.showInformationMessage "Reloading MCP Extension...")
  (deactivate)
  (activate))

(defn deactivate []
  (println "MCP Extension Deactivating...")
  ;; Dispose of all registered items
  (doseq [cmd (:commands @state)] (.dispose cmd))
  (doseq [item (:status-items @state)] (.dispose item))
  (reset! state {:commands [] :status-items [] :terminals []}))

;; Auto-activate
(activate)
EOF

# Load the extension
python3 ./mcp_nrepl_client.py --tool nrepl-load-file --args '{"file-path": "/tmp/mcp-extension.clj"}' --quiet

# Trigger the command menu
python3 ./mcp_nrepl_client.py --eval "(vscode/commands.executeCommand \"mcp.showMenu\")" --quiet
```

## Part 9: Integration with MCP Tools

### Combining MCP Tools with VS Code

```bash
# Use tree-sitter analysis in VS Code
python3 ./mcp_nrepl_client.py --eval "
(defn analyze-with-tree-sitter [file-path]
  ;; This would call out to tree-sitter MCP tool
  ;; For now, we'll simulate the analysis
  (p/let [doc (vscode/workspace.openTextDocument file-path)
          _ (vscode/window.showTextDocument doc)]
    (vscode/window.showInformationMessage 
      (str \"Analyzing \" file-path \" with tree-sitter...\"))))" --quiet

# Memory inspection integration
python3 ./mcp_nrepl_client.py --eval "
(defn show-memory-dashboard []
  (let [panel (vscode/window.createWebviewPanel 
                \"mcpMemory\" 
                \"MCP Memory Dashboard\"
                vscode/ViewColumn.Two
                #js {})]
    (set! (.-html (.-webview panel))
      \"<html><body>
         <h1>Memory Dashboard</h1>
         <p>Connect to MCP memory tool for live data...</p>
       </body></html>\")))" --quiet
```

## Part 10: Troubleshooting and Best Practices

### Common Issues

```bash
# Check Joyride nREPL status
python3 ./mcp_nrepl_client.py --eval "
(try
  (require '[\"vscode\" :as vscode])
  {:status \"connected\"
   :vscode-version vscode/version}
  (catch js/Error e
    {:status \"error\"
     :message (.-message e)}))" --quiet

# Verify extension APIs
python3 ./mcp_nrepl_client.py --eval "
{:has-vscode (exists? js/vscode)
 :has-calva (some? (vscode/extensions.getExtension \"betterthantomorrow.calva\"))
 :has-joyride (some? (vscode/extensions.getExtension \"betterthantomorrow.joyride\"))}" --quiet

# Reset if needed
python3 ./mcp_nrepl_client.py --eval "
(when (exists? js/vscode)
  ;; Dispose of any registered commands
  (vscode/commands.executeCommand \"workbench.action.reloadWindow\"))" --quiet
```

### Performance Optimization

```bash
# Batch operations
python3 ./mcp_nrepl_client.py --eval "
(defn batch-file-operation [file-patterns operation]
  (p/let [files (vscode/workspace.findFiles file-patterns)]
    (p/all (map operation files))))" --quiet

# Debounced file watching
python3 ./mcp_nrepl_client.py --eval "
(def debounce-timers (atom {}))
(defn debounced [key fn ms]
  (when-let [timer (get @debounce-timers key)]
    (js/clearTimeout timer))
  (let [timer (js/setTimeout fn ms)]
    (swap! debounce-timers assoc key timer)))" --quiet
```

### Security Considerations

```bash
# Sandbox evaluation
python3 ./mcp_nrepl_client.py --eval "
(defn safe-eval [code]
  (try
    (eval (read-string code))
    (catch js/Error e
      {:error (.-message e)})))" --quiet

# Validate file paths
python3 ./mcp_nrepl_client.py --eval "
(defn validate-workspace-path [path]
  (let [workspace-root (.-uri (first vscode/workspace.workspaceFolders))]
    (clojure.string/starts-with? path (.-fsPath workspace-root))))" --quiet
```

## Quick Reference Card

### Essential Commands

| Task | Command |
|------|---------|
| Show message | `(vscode/window.showInformationMessage "text")` |
| Get active file | `(.-fileName (.-document vscode/window.activeTextEditor))` |
| Open file | `(vscode/workspace.openTextDocument uri)` |
| Execute command | `(vscode/commands.executeCommand "command.id")` |
| Create terminal | `(vscode/window.createTerminal "name")` |
| Get selection | `(.getText doc (.-selection editor))` |
| Save file | `(.save (.-document editor))` |
| Format document | `(vscode/commands.executeCommand "editor.action.formatDocument")` |

### Useful Patterns

```clojure
;; Promise handling
(p/let [result (async-operation)]
  (process result))

;; Editor guard
(when-let [editor vscode/window.activeTextEditor]
  ;; safe to use editor
  )

;; Document modification
(.edit editor (fn [builder]
  (.insert builder position text)))

;; Event subscription
(def disposable 
  (.onDidSaveTextDocument vscode/workspace
    (fn [doc] (println "Saved:" (.-fileName doc)))))
```

## Conclusion

This cookbook demonstrates the power of combining MCP-nREPL with VS Code's Joyride extension, enabling you to:

- **Automate** repetitive development tasks
- **Enhance** VS Code with custom functionality
- **Integrate** multiple tools through a unified interface
- **Control** every aspect of your development environment
- **Build** custom extensions without leaving your REPL

The combination of MCP's tool protocol, nREPL's evaluation capabilities, and VS Code's extensive API creates a uniquely powerful development environment that can be customized and automated to match any workflow.