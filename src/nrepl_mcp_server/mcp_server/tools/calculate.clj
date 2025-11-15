(ns nrepl-mcp-server.mcp-server.tools.calculate
  "Mathematical expression evaluation tool with timeout protection"
  (:require [nrepl-mcp-server.calculator :as calc]
            [nrepl-mcp-server.calculator-analytics :as analytics]
            [cheshire.core :as json]))

(defn handle
  "Evaluate mathematical expressions using Clojure prefix notation.
   Returns result in EDN format with type information."
  [{:keys [expr]}]
  (cond
    ;; Validation: expr is required
    (or (nil? expr) (empty? expr))
    {:content [{:type "text"
                :text (json/generate-string
                       {:error "No expression provided"
                        :type "validation-error"}
                       {:pretty true})}]
     :isError true}

    ;; Evaluate expression
    :else
    (let [start-time (System/currentTimeMillis)
          result (calc/calculate expr)
          duration (- (System/currentTimeMillis) start-time)]
      ;; Log the calculation for analytics
      (analytics/log-calculation expr result duration)
      {:content [{:type "text"
                  :text (json/generate-string result {:pretty true})}]
       :isError (contains? result :error)})))

(def tool-name "calculate")

(def metadata
  {:description "Calculate mathematical expressions, statistics, geometry, finance, and physics formulas.

**USE THIS TOOL WHEN:**
- User asks for calculations, math problems, or numerical analysis
- Computing averages, percentages, statistics, or data analysis
- Solving geometry problems (areas, volumes, distances, angles)
- Financial calculations (interest, percentages, payments)
- Physics/engineering formulas (forces, velocities, trajectories)
- Vector operations (dot products, cross products, magnitudes)
- Converting units or temperatures

**Pre-loaded functions (50+ functions, no imports needed):**

**Arithmetic:** + - * / mod quot rem inc dec
**Powers:** pow sqrt cbrt exp
**Logarithms:** ln log10 log2 logb
**Trigonometry (radians):** sin cos tan asin acos atan atan2 sinh cosh tanh
**Trigonometry (degrees):** sind cosd tand
**Rounding:** abs floor ceil round sign trunc
**Constants:** pi e tau phi (golden ratio)
**Comparisons:** < > <= >= = not=

**Vector operations (on sequences):**
  sum product vmin vmax count
  mean median stdev variance
  dot norm cross (3D only)

**Examples:**
  (+ 2 3)                              => 5
  (sqrt (+ (pow 3 2) (pow 4 2)))       => 5.0
  (sin (/ pi 2))                       => 1.0
  (mean [1 2 3 4 5])                   => 3
  (-> 100 sqrt (* 2) (+ 5))            => 25.0
  (let [x 3 y 4] (sqrt (+ (* x x) (* y y)))) => 5.0
  (/ 10 3)                             => 10/3 (ratio)
  (/ 10.0 3.0)                         => 3.333...

**Returns:** JSON map with :result, :type, and :expr keys
**Timeout:** 5 seconds (prevents hanging on infinite loops)"
   :inputSchema {:type "object"
                 :properties {:expr {:type "string"
                                     :description "Clojure mathematical expression in prefix notation"}}
                 :required ["expr"]}})
