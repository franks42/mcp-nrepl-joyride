# Dynamic Tool Loading Design

A design for making the MCP server dynamically extensible with hot-loadable tools.

## Goals

1. **Minimal foundation** - Start with basic MCP server + bootstrap tools
2. **Dynamic loading** - Add new tools at runtime without restart
3. **Hot-reload** - Update tools during development
4. **Lifecycle management** - Clean start/stop for stateful tools
5. **Pod support** - Load Babashka pods for extended functionality

## Current Architecture Analysis

### What Already Supports Dynamic Loading

The existing architecture is well-suited for dynamic loading:

1. **`tool-registry.clj`** - atom-based store, `register-tool!` works anytime
2. **`dispatch/call-tool`** - looks up from registry at call time (not cached)
3. **`local-eval` + `local-load-file`** - already exist for runtime code loading

### Current Limitation

`register-tools.clj` has hardcoded requires (lines 14-25) - but this is only for initial startup. Nothing prevents adding more tools later.

### Tool Structure Pattern

Each tool exports three things:
- `tool-name` - string identifier
- `handle` - the handler function
- `metadata` - description + inputSchema

## Research Findings

### Babashka Pods

**Loading:**
```clojure
(require '[babashka.pods :as pods])
(pods/load-pod 'huahaiy/datalevin "0.9.22")
(require '[pod.huahaiy.datalevin :as d])
```

**Lifecycle:**
- Pods stay running for script duration
- Automatic shutdown via `{"op" "shutdown"}` message
- No explicit `unload-pod` - process killed on exit

**Key insight:** Pods are external processes, not reloadable. Tool files that use pods can be reloaded, but the pod itself persists.

### Component Library & SCI Compatibility

Stuart Sierra's Component library uses protocols and records. SCI implements these using multimethods and maps (no real Java classes), which may cause issues.

**Alternatives considered:**
- **Integrant** - works with babashka (with spartan.spec)
- **Mount** - doesn't rely on protocols (simpler)

**Decision:** Use a simple function-based pattern instead of external libraries. This is:
- Lighter weight
- No external dependency
- Fully SCI compatible

## Design: Simple Lifecycle Pattern

Instead of Component's protocol, use simple conventions with regular functions.

### Tool with Lifecycle

```clojure
(ns my-tools.datalevin-knowledge
  (:require [babashka.pods :as pods]
            [nrepl-mcp-server.state.tool-registry :as registry]))

;; State atom (standard pattern)
(defonce ^:private state (atom nil))

;; Lifecycle functions (just regular fns)
(defn start!
  "Initialize tool resources. Returns state."
  [{:keys [db-path] :as config}]
  (pods/load-pod 'huahaiy/datalevin "0.9.22")
  (require '[pod.huahaiy.datalevin :as d])
  (let [conn ((resolve 'd/get-conn) db-path)]
    (reset! state {:conn conn :config config})
    @state))

(defn stop!
  "Clean up resources. Returns nil."
  []
  (when-let [conn (:conn @state)]
    ((resolve 'd/close) conn))
  (reset! state nil))

(defn started? []
  (some? @state))

;; Handler uses state
(defn handle [{:keys [op] :as args}]
  (when-not (started?)
    (throw (ex-info "Tool not started" {:tool "datalevin-knowledge"})))
  ...)

;; Standard exports
(def tool-name "datalevin-knowledge")
(def metadata {:description "..." :inputSchema {...}})

;; Self-registration with lifecycle info
(registry/register-tool! tool-name handle metadata
  {:start-fn start!
   :stop-fn stop!
   :requires-start? true})
```

### Comparison with Component

| Component Library | Our Simple Pattern |
|-------------------|-------------------|
| `defrecord` with `Lifecycle` protocol | Regular `defn start!` / `defn stop!` |
| `component/using` for deps | Just require or resolve at runtime |
| `system-map` orchestration | Direct function calls |
| Protocol dispatch | Map lookup in registry |

**Why this works:**
- Tools are independent (no complex dependency graphs)
- Start order is simple: pods → tools
- SCI-compatible (just atoms, maps, functions)
- Easy to understand and debug

## Design: Enhanced Tool Registry

```clojure
;; In tool-registry.clj - no protocols needed

(def tool-registry
  "Format: {tool-name {:handler fn
                       :metadata map
                       :start-fn fn (optional)
                       :stop-fn fn (optional)
                       :started? bool}}"
  (atom {}))

(defn register-tool!
  "Register tool with optional lifecycle"
  [tool-name handler metadata & [lifecycle]]
  (swap! tool-registry assoc tool-name
         (merge {:handler handler
                 :metadata metadata
                 :started? false}
                lifecycle)))

(defn unregister-tool!
  "Unregister, stopping if running"
  [tool-name]
  (when-let [tool (get @tool-registry tool-name)]
    (when (and (:started? tool) (:stop-fn tool))
      ((:stop-fn tool)))
    (swap! tool-registry dissoc tool-name)))

(defn start-tool!
  "Start a tool's lifecycle"
  [tool-name config]
  (when-let [tool (get @tool-registry tool-name)]
    (when-let [start (:start-fn tool)]
      (start config)
      (swap! tool-registry assoc-in [tool-name :started?] true))))

(defn stop-tool!
  "Stop a tool's lifecycle"
  [tool-name]
  (when-let [tool (get @tool-registry tool-name)]
    (when (and (:started? tool) (:stop-fn tool))
      ((:stop-fn tool))
      (swap! tool-registry assoc-in [tool-name :started?] false))))

(defn reload-tool!
  "Hot-reload: stop, unload, reload file, start"
  [tool-name tool-path config]
  (stop-tool! tool-name)
  (swap! tool-registry dissoc tool-name)
  ;; Reload file (will re-register)
  (load-file tool-path)
  ;; Start with config
  (start-tool! tool-name config))
```

## Design: Tool Manifest Format

Tools can be described by a manifest for complex loading scenarios.

### Manifest Structure (tool-manifest.edn)

```clojure
{;; Identity
 :tool-name "datalevin-knowledge"
 :version "0.1.0"
 :description "Expert knowledge store using Datalevin"

 ;; Dependencies
 :pods [{:name huahaiy/datalevin
         :version "0.9.22"}]

 :requires [cheshire.core]

 ;; Files to load (in order)
 :files ["src/utils.clj"
         "src/schema.clj"]

 ;; Main tool file (has handle, metadata, self-registers)
 :main "src/datalevin_tools.clj"

 ;; Lifecycle functions (resolved from main ns)
 :lifecycle {:start-fn start!
             :stop-fn stop!}

 ;; Default configuration
 :default-config {:db-path "/tmp/knowledge-db"}}
```

### Inline Manifest (for simple tools)

For single-file tools, embed manifest in ns metadata:

```clojure
(ns my-tools.simple-tool
  {:tool/manifest
   {:version "0.1.0"
    :pods [{:name huahaiy/datalevin :version "0.9.22"}]
    :lifecycle true}}  ;; true = look for start!/stop! in this ns
  (:require ...))
```

### Manifest Fields

| Field | Required | Description |
|-------|----------|-------------|
| `:tool-name` | Yes | Unique tool identifier |
| `:version` | No | Semantic version |
| `:description` | No | Human-readable description |
| `:pods` | No | List of pods to load first |
| `:requires` | No | Namespaces to require |
| `:files` | No | Files to load before main |
| `:main` | Yes | Main tool file (self-registers) |
| `:lifecycle` | No | Start/stop function names |
| `:default-config` | No | Default configuration map |

## Design: Tool Loader

A loader that handles manifests and lifecycle:

```clojure
(ns nrepl-mcp-server.tool-loader
  "Dynamic tool loading with lifecycle management"
  (:require [babashka.pods :as pods]
            [nrepl-mcp-server.state.tool-registry :as registry]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defonce ^:private loaded-pods (atom #{}))
(defonce ^:private tool-configs (atom {}))

(defn load-pod-if-needed!
  "Load a pod if not already loaded"
  [{:keys [name version]}]
  (when-not (contains? @loaded-pods name)
    (pods/load-pod name version)
    (swap! loaded-pods conj name)
    (println "Loaded pod:" name version)))

(defn read-manifest
  "Read manifest from .edn file or extract from ns metadata"
  [path]
  (cond
    ;; Explicit manifest file
    (.endsWith path ".edn")
    (edn/read-string (slurp path))

    ;; Extract from clj file ns metadata
    (.endsWith path ".clj")
    (let [forms (read-string (str "[" (slurp path) "]"))
          ns-form (first (filter #(and (list? %) (= 'ns (first %))) forms))
          ns-meta (when (map? (nth ns-form 2 nil)) (nth ns-form 2))]
      (:tool/manifest ns-meta))

    :else nil))

(defn load-tool!
  "Load a tool from manifest or self-describing file"
  [tool-path & {:keys [config]}]
  (let [manifest (read-manifest tool-path)
        effective-config (merge (:default-config manifest) config)]

    ;; Load pods first
    (doseq [pod (:pods manifest)]
      (load-pod-if-needed! pod))

    ;; Load dependency files
    (doseq [f (:files manifest)]
      (load-file f))

    ;; Load main tool file (self-registers)
    (let [main-file (or (:main manifest) tool-path)]
      (load-file main-file))

    ;; Track config for reload
    (when-let [tool-name (:tool-name manifest)]
      (swap! tool-configs assoc tool-name
             {:path tool-path
              :config effective-config}))

    ;; Start lifecycle if defined
    (when-let [tool-name (:tool-name manifest)]
      (registry/start-tool! tool-name effective-config))

    {:status :loaded
     :tool-name (:tool-name manifest)
     :path tool-path}))

(defn unload-tool!
  "Unload a tool, stopping lifecycle"
  [tool-name]
  (registry/stop-tool! tool-name)
  (registry/unregister-tool! tool-name)
  (swap! tool-configs dissoc tool-name)
  {:status :unloaded :tool-name tool-name})

(defn reload-tool!
  "Hot-reload a tool"
  [tool-name]
  (if-let [{:keys [path config]} (get @tool-configs tool-name)]
    (do
      (unload-tool! tool-name)
      (load-tool! path :config config))
    {:status :error
     :error (str "No config found for tool: " tool-name)}))

(defn list-loaded-tools
  "List all dynamically loaded tools with their status"
  []
  (for [[tool-name {:keys [path config]}] @tool-configs]
    {:tool-name tool-name
     :path path
     :config config
     :started? (get-in @registry/tool-registry [tool-name :started?])}))
```

## Design: MCP Tools for Dynamic Loading

Expose the loader as MCP tools:

### load-tool

```clojure
(def tool-name "load-tool")

(def metadata
  {:description "Load a dynamic tool from manifest or file path"
   :inputSchema
   {:type "object"
    :properties
    {:path {:type "string"
            :description "Path to tool manifest (.edn) or main file (.clj)"}
     :config {:type "object"
              :description "Configuration to pass to tool start"}}
    :required ["path"]}})

(defn handle [{:keys [path config]}]
  (let [result (loader/load-tool! path :config config)]
    {:content [{:type "text"
                :text (json/generate-string result {:pretty true})}]}))
```

### unload-tool

```clojure
(def tool-name "unload-tool")

(def metadata
  {:description "Unload a dynamic tool, stopping its lifecycle"
   :inputSchema
   {:type "object"
    :properties
    {:tool-name {:type "string"
                 :description "Name of tool to unload"}}
    :required ["tool-name"]}})
```

### reload-tool

```clojure
(def tool-name "reload-tool")

(def metadata
  {:description "Hot-reload a tool (stop, unload, load, start)"
   :inputSchema
   {:type "object"
    :properties
    {:tool-name {:type "string"
                 :description "Name of tool to reload"}}
    :required ["tool-name"]}})
```

### list-loaded-tools

```clojure
(def tool-name "list-loaded-tools")

(def metadata
  {:description "List all dynamically loaded tools with status"
   :inputSchema
   {:type "object"
    :properties {}}})
```

## Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│                 MCP Server Foundation                │
├─────────────────────────────────────────────────────┤
│  tool-registry.clj     - register/unregister/start  │
│  tool-loader.clj       - load/unload with lifecycle │
│  server.clj            - stdio JSON-RPC loop        │
│  dispatch.clj          - route to handlers          │
├─────────────────────────────────────────────────────┤
│              Bootstrap Tools (always loaded)         │
├─────────────────────────────────────────────────────┤
│  local-eval            - inline code execution      │
│  local-load-file       - file loading               │
│  load-tool             - manifest-aware loader      │
│  unload-tool           - clean shutdown             │
│  reload-tool           - hot-reload                 │
│  list-loaded-tools     - status overview            │
├─────────────────────────────────────────────────────┤
│              Dynamic Tools (loaded on demand)        │
├─────────────────────────────────────────────────────┤
│  datalevin-knowledge   - uses datalevin pod         │
│  calculate             - math expressions           │
│  nrepl-*               - nREPL connectivity         │
│  ... any new tools                                  │
└─────────────────────────────────────────────────────┘
```

## Usage Examples

### Loading a Simple Tool

```clojure
;; Via MCP
{"method": "tools/call",
 "params": {"name": "load-tool",
            "arguments": {"path": "/tools/my-tool.clj"}}}

;; Via local-eval
(load-file "/tools/my-tool.clj")
```

### Loading a Tool with Manifest

```clojure
;; Via MCP
{"method": "tools/call",
 "params": {"name": "load-tool",
            "arguments": {"path": "/tools/datalevin-manifest.edn",
                          "config": {"db-path": "/data/knowledge.db"}}}}
```

### Hot-Reloading During Development

```clojure
;; Edit tool file, then:
{"method": "tools/call",
 "params": {"name": "reload-tool",
            "arguments": {"tool-name": "datalevin-knowledge"}}}
```

### Checking Status

```clojure
{"method": "tools/call",
 "params": {"name": "list-loaded-tools",
            "arguments": {}}}

;; Returns:
[{"tool-name": "datalevin-knowledge",
  "path": "/tools/datalevin-manifest.edn",
  "config": {"db-path": "/data/knowledge.db"},
  "started?": true}]
```

## Minimal MCP Server

### Ultimate Minimal Bootstrap

The absolute minimum foundation requires only **five components**:

1. **Core infrastructure:**
   - `tool-registry.clj` - atom + register/unregister
   - `server.clj` - stdio JSON-RPC loop
   - `dispatch.clj` - route to handlers

2. **Bootstrap tools:**
   - `local-eval` - inline code execution
   - `local-load-file` - file loading

**That's it.** Everything else is dynamically loadable.

### Self-Hosting Tool Management

Key insight: `local-eval` and `local-load-file` are sufficient for all tool management:

```clojure
;; Load a pod
(local-eval "(require '[babashka.pods :as pods])
             (pods/load-pod 'huahaiy/datalevin \"0.9.22\")")

;; Load a tool (self-registers)
(local-load-file "/tools/my-tool.clj")

;; Unregister a tool
(local-eval "(registry/unregister-tool! \"my-tool\")")

;; Start/stop lifecycle
(local-eval "(registry/start-tool! \"my-tool\" {:db-path \"/tmp/db\"})")
(local-eval "(registry/stop-tool! \"my-tool\")")
```

### Recursive Bootstrap Pattern

The convenience tools (`load-tool`, `unload-tool`, `reload-tool`, `list-loaded-tools`) can themselves be dynamically loaded:

1. **Start with absolute minimum:** `local-eval` + `local-load-file`
2. **Dynamically load tool-management tools:** `load-tool`, `unload-tool`, etc.
3. **Use those to load everything else:** calculate, nrepl-*, datalevin-knowledge, etc.

This is beautifully self-hosting - the tool-management tools eat their own dog food.

### When to Add Convenience Tools

The dedicated MCP tools (`load-tool`, etc.) are optional convenience wrappers that provide:
- **Discoverability** - show up in `tools/list`
- **Cleaner interface** - don't need to know internal function names
- **Manifest parsing** - handle .edn manifests automatically
- **Validation** - check dependencies, config requirements

For MVP, just `local-eval` + `local-load-file` + enhanced registry functions is sufficient.

### Ephemeral Tool-Tools Pattern

The tool-management tools can be loaded temporarily and then removed:

1. Load `load-tool`, `unload-tool`, etc.
2. Use them to load your actual application tools
3. Unload the tool-management tools
4. Clean MCP interface with only the tools you need

The management tools become scaffolding you remove after construction.

### Lock-Down Mode (Security)

For production security, you can unload the bootstrap tools themselves:

1. Start with `local-eval` + `local-load-file`
2. Load and configure all required tools
3. **Unload `local-eval` and `local-load-file`**
4. System is now immutable - no further code injection possible

This creates a one-way door: once locked down, the MCP server cannot be modified at runtime. Useful for production deployments where you want to prevent tampering.

```clojure
;; Final lock-down step
(local-eval "(registry/unregister-tool! \"local-eval\")")
;; Now even local-eval is gone - system is sealed
```

## Benefits

1. **Minimal startup** - Only load what you need
2. **Fast iteration** - Hot-reload during development
3. **Clean lifecycle** - Proper resource management
4. **Pod sharing** - Pods loaded once, used by multiple tools
5. **Self-describing** - Manifests document dependencies
6. **No external deps** - Works in pure SCI/Babashka

## Future Considerations

1. **Tool dependencies** - Tool A requires Tool B
2. **Version constraints** - Semantic versioning for tools
3. **Remote loading** - Load tools from URLs
4. **Tool marketplace** - Registry of available tools
5. **Sandboxing** - Restrict what tools can do

## Appendix A: Manifest Format Specification

### Complete Schema

```clojure
{;; ===== IDENTITY =====
 :tool-name    string?        ;; REQUIRED - unique identifier
 :version      string?        ;; semver, e.g., "1.2.3"
 :description  string?        ;; human-readable
 :author       string?        ;; maintainer info
 :license      string?        ;; e.g., "MIT", "Apache-2.0"

 ;; ===== DEPENDENCIES =====
 :pods [{:name symbol?        ;; e.g., 'huahaiy/datalevin
         :version string?}]   ;; e.g., "0.9.22"

 :requires [symbol?]          ;; namespaces to require before loading

 ;; ===== FILES =====
 :files [string?]             ;; dependency files to load first (in order)
 :main string?                ;; REQUIRED - main tool file

 ;; ===== LIFECYCLE =====
 :lifecycle
 {:start-fn symbol?           ;; function to call on start
  :stop-fn symbol?            ;; function to call on stop
  :config-schema map?}        ;; optional schema for config validation

 ;; ===== CONFIGURATION =====
 :default-config map?         ;; default values for config
 :required-config [keyword?]  ;; config keys that must be provided

 ;; ===== METADATA =====
 :tags [string?]              ;; for discovery, e.g., ["database", "storage"]
 :homepage string?            ;; URL for documentation
 :repository string?}         ;; URL for source code
```

### Example Manifests

#### 1. Simple Stateless Tool

```clojure
;; tools/string-utils-manifest.edn
{:tool-name "string-utils"
 :version "0.1.0"
 :description "String manipulation utilities"
 :main "tools/string_utils.clj"
 :tags ["utility" "string"]}
```

#### 2. Tool with Pod Dependency

```clojure
;; tools/datalevin-manifest.edn
{:tool-name "datalevin-knowledge"
 :version "0.2.0"
 :description "Expert knowledge store using Datalevin"
 :author "team@example.com"

 :pods [{:name huahaiy/datalevin
         :version "0.9.22"}]

 :main "tools/datalevin_knowledge.clj"

 :lifecycle
 {:start-fn start!
  :stop-fn stop!}

 :default-config
 {:db-path "/tmp/knowledge-db"
  :cache-size 1000}

 :required-config [:db-path]

 :tags ["database" "knowledge" "datalevin"]}
```

#### 3. Multi-File Tool

```clojure
;; tools/analytics-manifest.edn
{:tool-name "analytics-suite"
 :version "1.0.0"
 :description "Comprehensive analytics tools"

 :requires [cheshire.core
            clojure.string]

 :files ["tools/analytics/utils.clj"
         "tools/analytics/charts.clj"
         "tools/analytics/reports.clj"]

 :main "tools/analytics/main.clj"

 :lifecycle
 {:start-fn init!
  :stop-fn cleanup!}

 :default-config
 {:output-dir "/tmp/analytics"
  :format "json"}}
```

#### 4. Tool with External Service

```clojure
;; tools/http-client-manifest.edn
{:tool-name "http-client"
 :version "0.3.0"
 :description "HTTP client with connection pooling"

 :pods [{:name org.babashka/http-client
         :version "0.4.19"}]

 :main "tools/http_client.clj"

 :lifecycle
 {:start-fn create-pool!
  :stop-fn shutdown-pool!}

 :default-config
 {:pool-size 10
  :timeout-ms 30000
  :retry-count 3}

 :required-config [:base-url]}
```

### Inline Manifest Examples

For single-file tools, embed in namespace metadata:

#### Simple Inline

```clojure
(ns my-tools.formatter
  {:tool/manifest
   {:tool-name "formatter"
    :version "0.1.0"
    :tags ["utility"]}}
  (:require [clojure.pprint :as pp]))

(defn handle [{:keys [data format]}]
  ...)

(def tool-name "formatter")
(def metadata {:description "Format data" :inputSchema {...}})
```

#### Inline with Lifecycle

```clojure
(ns my-tools.cache
  {:tool/manifest
   {:tool-name "cache"
    :version "0.1.0"
    :lifecycle {:start-fn start! :stop-fn stop!}
    :default-config {:max-size 1000}}}
  (:require [nrepl-mcp-server.state.tool-registry :as registry]))

(defonce ^:private cache (atom nil))

(defn start! [{:keys [max-size]}]
  (reset! cache {:data {} :max-size max-size}))

(defn stop! []
  (reset! cache nil))

(defn handle [{:keys [op key value]}]
  ...)

(def tool-name "cache")
(def metadata {...})

(registry/register-tool! tool-name handle metadata
  {:start-fn start! :stop-fn stop!})
```

### Loading Precedence

When `load-tool!` is called:

1. If path ends in `.edn` → read as manifest file
2. If path ends in `.clj` → check for inline `:tool/manifest` in ns
3. If no manifest found → load as simple self-registering tool

### Validation Rules

The loader validates:

1. **Required fields**: `:tool-name`, `:main` (or inline tool file)
2. **Pod availability**: Check pod exists in registry before loading
3. **File existence**: All `:files` and `:main` must exist
4. **Config requirements**: All `:required-config` keys provided
5. **No duplicates**: Tool name not already registered (unless reloading)

### Error Handling

```clojure
;; Returned on validation failure
{:status :error
 :error-type :validation
 :message "Missing required config: db-path"
 :manifest {:tool-name "datalevin-knowledge" ...}}

;; Returned on pod loading failure
{:status :error
 :error-type :pod-load
 :message "Pod not found: huahaiy/datalevin 0.9.22"
 :pod {:name huahaiy/datalevin :version "0.9.22"}}

;; Returned on file loading failure
{:status :error
 :error-type :file-load
 :message "Syntax error in tools/broken.clj"
 :file "tools/broken.clj"
 :cause "Unexpected EOF"}

;; Returned on lifecycle start failure
{:status :error
 :error-type :lifecycle
 :message "start! failed: Connection refused"
 :tool-name "datalevin-knowledge"
 :phase :start}
```

### Best Practices

1. **Always provide `:version`** - enables tracking and debugging
2. **Use `:default-config`** - makes tools work out-of-box
3. **Document `:required-config`** - clear contract for users
4. **Add `:tags`** - enables discovery and filtering
5. **Keep `:files` minimal** - faster loading
6. **Make lifecycle idempotent** - safe to call start!/stop! multiple times

---

*Created: 2024-11-18*
*Status: Design Phase*
