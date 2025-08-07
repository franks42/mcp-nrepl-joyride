(ns python-introspection-utils
  "Utility functions for Python application introspection via Basilisp interop.
   
   Load this file into your nREPL session to access convenient Python
   introspection functions that combine multiple operations."
  (:require [clojure.string :as str]
            [clojure.pprint :as pp]))

;; =============================================================================
;; Core Python Interop Helpers
;; =============================================================================

(defn py-safe-getattr
  "Safely get attribute from Python object with default value"
  [obj attr default]
  (if (python/hasattr obj attr)
    (python/getattr obj attr)
    default))

(defn py-safe-call
  "Safely call Python function, returning default on error"
  [f default & args]
  (try
    (apply f args)
    (catch Exception e
      (println "Python call failed:" (.getMessage e))
      default)))

(defn py-filter-safe-attrs
  "Filter out private and sensitive attributes from Python object"
  [attrs]
  (->> attrs
       (filter string?)
       (filter #(not (.startswith % "_")))
       (filter #(not (.contains (.toLowerCase %) "secret")))
       (filter #(not (.contains (.toLowerCase %) "password")))
       (filter #(not (.contains (.toLowerCase %) "token")))
       sort))

;; =============================================================================
;; System Information
;; =============================================================================

(defn py-system-info
  "Get comprehensive Python system information"
  []
  (let [info {:python-version (str (python/sys.version-info))
              :platform (py-safe-call python/platform.system "unknown")
              :cwd (py-safe-call python/os.getcwd "unknown")
              :pid (py-safe-call python/os.getpid "unknown")
              :thread-count (py-safe-call python/threading.active-count "unknown")}]
    (pp/pprint info)
    info))

(defn py-memory-info
  "Get memory usage information (requires psutil)"
  []
  (try
    (let [process (python/psutil.Process)
          memory-info (python/process.memory_info)
          info {:rss-mb (/ (python/memory_info.rss) 1024 1024)
                :vms-mb (/ (python/memory_info.vms) 1024 1024)
                :percent (python/process.memory_percent)
                :gc-stats (python/gc.get_stats)}]
      (pp/pprint info)
      info)
    (catch Exception e
      (println "Memory info failed (install psutil?):" (.getMessage e))
      {:error "psutil not available"})))

;; =============================================================================
;; Module and Import Analysis
;; =============================================================================

(defn py-list-app-modules
  "List all imported modules matching app prefix"
  [app-prefix]
  (->> (python/sys.modules.keys)
       (map str)
       (filter #(.startswith % app-prefix))
       sort))

(defn py-module-info
  "Get detailed information about a Python module"
  [module-name]
  (try
    (let [module (python/sys.modules.get module-name)]
      (if module
        (let [attrs (py-filter-safe-attrs (python/dir module))
              info {:name module-name
                    :file (py-safe-getattr module "__file__" "built-in")
                    :doc (py-safe-getattr module "__doc__" "No docstring")
                    :attributes (take 20 attrs)
                    :attribute-count (count attrs)}]
          (pp/pprint info)
          info)
        {:error (str "Module " module-name " not found")}))
    (catch Exception e
      {:error (.getMessage e)})))

(defn py-explore-object
  "Deep exploration of Python object"
  [obj]
  (let [obj-type (python/type obj)
        attrs (py-filter-safe-attrs (python/dir obj))
        methods (->> attrs
                     (filter #(python/callable (python/getattr obj %)))
                     (take 10))
        properties (->> attrs
                        (filter #(not (python/callable (python/getattr obj %))))
                        (take 10))
        info {:type (str obj-type)
              :methods methods
              :properties properties  
              :total-attributes (count attrs)
              :mro (map str (python/inspect.getmro obj-type))}]
    (pp/pprint info)
    info))

;; =============================================================================
;; Application Health Checks
;; =============================================================================

(defn py-health-check
  "Comprehensive Python application health check"
  []
  (println "🔍 Python Application Health Check")
  (println "=" 50)
  
  (let [health {:system (py-system-info)
                :memory (py-memory-info)
                :modules (count (python/sys.modules))
                :timestamp (str (python/datetime.datetime.now))}]
    
    ;; Check common frameworks
    (println "\n📦 Framework Detection:")
    (doseq [[framework module] [["Django" "django"]
                                ["Flask" "flask"] 
                                ["FastAPI" "fastapi"]
                                ["Celery" "celery"]]]
      (if (python/sys.modules.get module)
        (println (str "✅ " framework " detected"))
        (println (str "❌ " framework " not found"))))
    
    health))

(defn py-config-summary
  "Safe configuration summary (filters secrets)"
  []
  (println "⚙️ Configuration Summary")
  (println "=" 30)
  
  ;; Try common config locations
  (let [configs (atom {})]
    
    ;; Flask config
    (when-let [app (python/sys.modules.get "flask")]
      (when (python/hasattr python/app "config")
        (swap! configs assoc :flask
               (->> (python/dict python/app.config)
                    (filter (fn [[k v]] 
                      (not (.contains (.toUpperCase (str k)) "SECRET"))))
                    (into {})))))
    
    ;; Django settings  
    (when (python/sys.modules.get "django")
      (try
        (swap! configs assoc :django
               (->> (python/dir python/django.conf.settings)
                    (py-filter-safe-attrs)
                    (map (fn [attr] 
                      [attr (python/getattr python/django.conf.settings attr)]))
                    (take 10)
                    (into {})))
        (catch Exception e
          (swap! configs assoc :django {:error "Settings not accessible"}))))
    
    (pp/pprint @configs)
    @configs))

;; =============================================================================
;; Database and Connection Monitoring
;; =============================================================================

(defn py-db-status
  "Check database connection status"
  []
  (println "🗄️ Database Status")
  (println "=" 20)
  
  (let [status (atom {})]
    
    ;; Django database
    (when (python/sys.modules.get "django")
      (try
        (swap! status assoc :django-db
               {:databases (python/django.db.connections.databases)
                :default-connected (str (python/django.db.connection.is_usable))})
        (catch Exception e
          (swap! status assoc :django-db {:error (.getMessage e)}))))
    
    ;; SQLAlchemy
    (when-let [app (python/sys.modules.get "flask")]
      (when (and (python/hasattr python/app "db") 
                 (python/hasattr python/app.db "engine"))
        (swap! status assoc :sqlalchemy
               {:pool-size (py-safe-call python/app.db.engine.pool.size 0)
                :checked-out (py-safe-call python/app.db.engine.pool.checkedout 0)})))
    
    (pp/pprint @status)
    @status))

;; =============================================================================
;; Performance and Monitoring
;; =============================================================================

(defn py-performance-snapshot
  "Take performance snapshot of Python application"
  []
  (println "📊 Performance Snapshot")
  (println "=" 25)
  
  (let [start-time (System/currentTimeMillis)
        snapshot {:memory (py-memory-info)
                  :threads (python/threading.active-count)
                  :gc-collections (reduce + (map #(python/% "collections") (python/gc.get_stats)))
                  :open-files (try 
                                (python/len (python/psutil.Process.open_files))
                                (catch Exception e 0))}]
    
    ;; Add async info if available
    (when (python/sys.modules.get "asyncio")
      (try
        (let [tasks (python/asyncio.all_tasks)]
          (swap! (atom snapshot) assoc :async-tasks
                 {:total (python/len tasks)
                  :running (->> tasks 
                                (filter #(not (python/%.done)))
                                count)}))
        (catch Exception e
          ;; No async loop running
          nil)))
    
    (assoc snapshot :snapshot-time-ms (- (System/currentTimeMillis) start-time))))

;; =============================================================================
;; Development Utilities
;; =============================================================================

(defn py-reload-module
  "Safely reload a Python module"
  [module-name]
  (try
    (let [module (python/importlib.import-module module-name)
          reloaded (python/importlib.reload module)]
      (println (str "✅ Reloaded module: " module-name))
      {:status "success" :module module-name})
    (catch Exception e
      (println (str "❌ Failed to reload " module-name ": " (.getMessage e)))
      {:status "error" :module module-name :error (.getMessage e)})))

(defn py-clear-caches
  "Clear various Python application caches"
  []
  (let [results (atom [])]
    
    ;; Django cache
    (when (python/sys.modules.get "django")
      (try
        (python/django.core.cache.cache.clear)
        (swap! results conj "✅ Django cache cleared")
        (catch Exception e
          (swap! results conj (str "❌ Django cache: " (.getMessage e))))))
    
    ;; Flask cache (if using Flask-Caching)
    (when-let [app (python/sys.modules.get "flask")]
      (when (python/hasattr python/app "cache")
        (try
          (python/app.cache.clear)
          (swap! results conj "✅ Flask cache cleared")
          (catch Exception e
            (swap! results conj (str "❌ Flask cache: " (.getMessage e)))))))
    
    ;; Python import cache
    (try
      (python/importlib.invalidate_caches)
      (swap! results conj "✅ Import caches invalidated")
      (catch Exception e
        (swap! results conj (str "❌ Import cache: " (.getMessage e)))))
    
    (doseq [result @results]
      (println result))
    
    @results))

;; =============================================================================
;; Convenience Functions
;; =============================================================================

(defn py-quick-status
  "Quick one-liner status check"
  []
  (let [info {:modules (count (python/sys.modules))
              :threads (python/threading.active-count)
              :memory-mb (try 
                           (/ (python/psutil.Process.memory_info.rss) 1024 1024)
                           (catch Exception e "N/A"))}]
    (println (str "📊 Quick Status: " 
                  (:modules info) " modules, " 
                  (:threads info) " threads, " 
                  (:memory-mb info) " MB"))
    info))

(defn py-find-by-pattern
  "Find Python objects matching pattern"
  [pattern]
  (->> (python/sys.modules.items)
       (mapcat (fn [[module-name module]]
         (->> (python/dir module)
              (filter #(.contains (.toLowerCase %) (.toLowerCase pattern)))
              (map (fn [attr] [module-name attr])))))
       (take 20)))

;; =============================================================================
;; Usage Examples and Documentation
;; =============================================================================

(defn py-help
  "Show available Python introspection functions"
  []
  (println "\n🐍 Python Introspection Utils - Available Functions:")
  (println "=" 60)
  (println "
📊 System & Health:
  (py-system-info)         - System information
  (py-memory-info)         - Memory usage details  
  (py-health-check)        - Comprehensive health check
  (py-quick-status)        - One-line status summary
  
🔍 Module & Object Exploration:
  (py-list-app-modules \"myapp\") - List app modules
  (py-module-info \"module.name\") - Module details
  (py-explore-object obj)       - Deep object inspection
  (py-find-by-pattern \"user\")   - Find matching objects
  
⚙️ Configuration & Database:
  (py-config-summary)      - Safe config overview
  (py-db-status)           - Database connections
  
📈 Performance & Monitoring:
  (py-performance-snapshot) - Performance metrics
  
🛠️ Development Tools:
  (py-reload-module \"module\")  - Reload Python module
  (py-clear-caches)         - Clear application caches
  
💡 Usage Tips:
  - Start with (py-health-check) for overview
  - Use (py-quick-status) for frequent monitoring  
  - Explore objects with (py-explore-object obj)
  - Always test in development first!
")
  nil)

;; Show help on load
(py-help)