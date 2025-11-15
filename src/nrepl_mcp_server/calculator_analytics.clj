(ns nrepl-mcp-server.calculator-analytics
  "Analytics and logging for calculator tool usage patterns"
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def log-file "calculator-usage.edn")

(defn extract-function-names
  "Extract top-level function symbols from expression"
  [expr-str]
  (try
    (let [form (edn/read-string expr-str)]
      (set (filter symbol? (flatten (if (seq? form) form [form])))))
    (catch Exception _ #{})))

(defn measure-nesting-depth
  "Count max nesting level in expression"
  [expr-str]
  (let [chars (seq expr-str)]
    (loop [cs chars depth 0 max-depth 0]
      (if (empty? cs)
        max-depth
        (let [c (first cs)]
          (cond
            (= c \() (recur (rest cs) (inc depth) (max max-depth (inc depth)))
            (= c \)) (recur (rest cs) (dec depth) max-depth)
            :else (recur (rest cs) depth max-depth)))))))

(defn log-calculation
  "Append calculation to analytics log.
   Gracefully handles write failures (e.g., read-only file systems in sandboxed environments)."
  [expr result duration-ms]
  (try
    (let [entry {:timestamp (System/currentTimeMillis)
                 :expr expr
                 :result-type (:type result)
                 :success? (not (contains? result :error))
                 :error (get result :error nil)
                 :duration-ms duration-ms
                 ;; Extract patterns
                 :functions-used (extract-function-names expr)
                 :expr-depth (measure-nesting-depth expr)
                 :has-let? (str/includes? expr "let")
                 :has-threading? (or (str/includes? expr "->")
                                     (str/includes? expr "->>"))}]
      (spit log-file (str (pr-str entry) "\n") :append true))
    (catch Exception e
      ;; Silently ignore logging failures - calculator should work even if logging fails
      ;; This handles read-only file systems (Claude Desktop, sandboxed environments)
      (binding [*out* *err*]
        (println "[calculator-analytics] Warning: Failed to write analytics log:" (.getMessage e)))
      nil)))

(defn generate-usage-report
  "Analyze log file and generate summary statistics"
  []
  (if (.exists (io/file log-file))
    (let [entries (->> (slurp log-file)
                       str/split-lines
                       (remove empty?)
                       (map edn/read-string))
          total (count entries)]
      (if (zero? total)
        {:total-calculations 0
         :message "No calculations logged yet"}
        (let [successes (count (filter :success? entries))
              fn-freq (->> entries
                           (mapcat :functions-used)
                           frequencies
                           (sort-by val >))
              avg-depth (/ (reduce + (map :expr-depth entries)) total)
              avg-duration (/ (reduce + (map :duration-ms entries)) total)]
          {:total-calculations total
           :success-rate (double (/ successes total))
           :avg-nesting-depth (double avg-depth)
           :avg-duration-ms (double avg-duration)
           :top-functions (take 20 fn-freq)
           :let-usage-rate (double (/ (count (filter :has-let? entries)) total))
           :threading-usage-rate (double (/ (count (filter :has-threading? entries)) total))
           :common-errors (->> entries
                               (filter :error)
                               (map :error)
                               frequencies
                               (sort-by val >)
                               (take 10))})))
    {:error "Log file does not exist"
     :log-file log-file}))

(defn clear-logs
  "Clear the analytics log file"
  []
  (when (.exists (io/file log-file))
    (io/delete-file log-file))
  {:status "cleared"})
