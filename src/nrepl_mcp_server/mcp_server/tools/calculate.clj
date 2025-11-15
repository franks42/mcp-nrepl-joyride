(ns nrepl-mcp-server.mcp-server.tools.calculate
  "Mathematical expression evaluation tool with timeout protection and base64 support"
  (:require [nrepl-mcp-server.calculator :as calc]
            [nrepl-mcp-server.calculator-analytics :as analytics]
            [cheshire.core :as json]))

;; =============================================================================
;; Base64 Utilities
;; =============================================================================

(defn- decode-base64
  "Decode base64 string to UTF-8 text"
  [b64-str]
  (String. (.decode (java.util.Base64/getDecoder) b64-str) "UTF-8"))

(defn- encode-base64
  "Encode UTF-8 text to base64 string"
  [text]
  (.encodeToString (java.util.Base64/getEncoder) (.getBytes text "UTF-8")))

;; =============================================================================
;; Main Handler
;; =============================================================================

(defn handle
  "Evaluate mathematical expressions using Clojure prefix notation.
   Returns result in EDN format with type information.

   Supports input-base64 flag to avoid JSON escaping issues with complex expressions."
  [{:keys [expr input-base64 output-base64]}]
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
          ;; Decode base64 if requested
          actual-expr (if input-base64
                        (try
                          (decode-base64 expr)
                          (catch Exception e
                            (binding [*out* *err*]
                              (println "[calculate] Base64 decode error:" (.getMessage e)))
                            ;; Log decoding error
                            (analytics/log-calculation
                             expr
                             {:error (str "Base64 decode failed: " (.getMessage e))
                              :type "decode-error"}
                             0)
                            ;; Return error response
                            (throw (ex-info "Failed to decode base64 expression"
                                            {:error (.getMessage e)
                                             :expr expr}))))
                        expr)
          result (calc/calculate actual-expr)
          duration (- (System/currentTimeMillis) start-time)]
      ;; Log the calculation for analytics (with actual expression)
      (analytics/log-calculation actual-expr result duration)
      ;; Format response with optional base64 encoding
      (let [response-map (if output-base64
                           (-> result
                               (update :result #(if (string? %) (encode-base64 %) %))
                               (cond->
                                (:error result) (update :error encode-base64)))
                           result)]
        {:content [{:type "text"
                    :text (json/generate-string response-map {:pretty true})}]
         :isError (contains? result :error)}))))

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

**NOTE:** Supports optional base64 encoding (input-base64/output-base64 flags) to avoid JSON escaping issues with complex expressions.

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
**Timeout:** 5 seconds (prevents hanging on infinite loops)

**Optional Base64 Encoding:**
- input-base64 (boolean): Interpret 'expr' as base64-encoded string (default: false)
- output-base64 (boolean): Return result fields as base64 encoded strings (default: false)
- Use base64 to avoid JSON escaping issues with complex expressions"
   :inputSchema {:type "object"
                 :properties {:expr {:type "string"
                                     :description "Clojure mathematical expression in prefix notation (or base64-encoded if input-base64=true)"}
                              :input-base64 {:type "boolean"
                                             :description "Interpret 'expr' parameter as base64-encoded string (default: false)"}
                              :output-base64 {:type "boolean"
                                              :description "Return result fields as base64 encoded strings (default: false)"}}
                 :required ["expr"]}})
