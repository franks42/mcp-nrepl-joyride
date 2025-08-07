(ns python-utils-simple
  "Simple Python introspection utilities for Babashka nREPL.
   
   These functions work with basic Babashka without external dependencies.")

;; =============================================================================
;; Basic Python Interop Functions
;; =============================================================================

(defn py-system-info
  "Get basic Python system information"
  []
  (let [info {:python-version (str (python/sys.version-info))
              :platform (try (python/platform.system) (catch Exception e "unknown"))
              :cwd (try (python/os.getcwd) (catch Exception e "unknown"))
              :thread-count (try (python/threading.active-count) (catch Exception e "unknown"))}]
    (println "🐍 Python System Information:")
    (doseq [[k v] info]
      (println (str "  " (name k) ": " v)))
    info))

(defn py-quick-status
  "Quick Python runtime status"
  []
  (let [module-count (count (python/sys.modules))
        thread-count (try (python/threading.active-count) (catch Exception e "N/A"))]
    (println (str "📊 Quick Status: " module-count " modules, " thread-count " threads"))
    {:modules module-count :threads thread-count}))

(defn py-list-modules
  "List Python modules matching prefix"
  [prefix]
  (->> (python/sys.modules.keys)
       (map str)
       (filter #(.startswith % prefix))
       sort))

(defn py-explore-simple
  "Simple object exploration"
  [obj]
  (let [obj-type (python/type obj)
        attrs (python/dir obj)]
    (println (str "Object type: " obj-type))
    (println (str "Attributes count: " (count attrs)))
    (println "Sample attributes:")
    (->> attrs
         (filter #(not (.startswith % "_")))
         (take 10)
         (map #(println (str "  " %)))
         doall)
    {:type (str obj-type) :attribute-count (count attrs)}))

(defn py-test-interop
  "Test basic Python interop functionality"
  []
  (println "🧪 Testing Python Interop:")
  
  ;; Test basic operations
  (println "✅ Basic math:" (python/+ 2 3))
  (println "✅ List operations:" (python/len [1 2 3 4 5]))
  (println "✅ String operations:" (python/str.upper "hello"))
  (println "✅ Type checking:" (python/type "hello"))
  
  ;; Test module access
  (println "✅ Sys module:" (python/sys.version-info.major))
  (println "✅ OS module:" (try (python/os.name) (catch Exception e "not available")))
  
  "All tests completed!")

(defn py-help
  "Show available simple Python functions"
  []
  (println "\n🐍 Simple Python Utils:")
  (println "=" 30)
  (println "  (py-system-info)     - System information")
  (println "  (py-quick-status)    - Quick status check") 
  (println "  (py-list-modules \"prefix\") - List modules")
  (println "  (py-explore-simple obj)   - Explore object")
  (println "  (py-test-interop)    - Test Python interop")
  (println "  (py-help)            - Show this help")
  nil)

;; Show help on load
(println "\n🎉 Simple Python introspection utils loaded!")
(py-help)