;; Test file for load-file functionality
(println "Loading file: test-load-example.clj")

(defn greet [name]
  (println "Hello," name "from loaded file!")
  (str "Greeting: " name))

(def loaded-var 42)

(println "File loaded successfully. loaded-var =" loaded-var)

;; Return something
:file-loaded