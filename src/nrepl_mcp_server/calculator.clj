(ns nrepl-mcp-server.calculator
  (:require [sci.core :as sci]))

;; Math function definitions - 50+ pre-loaded functions
(def math-fns
  {;; Core arithmetic
   '+ +, '- -, '* *, '/ /
   'mod mod, 'quot quot, 'rem rem
   'inc inc, 'dec dec

   ;; Powers and roots
   'pow #(Math/pow (double %1) (double %2))
   'sqrt #(Math/sqrt (double %))
   'cbrt #(Math/cbrt (double %))
   'exp #(Math/exp (double %))

   ;; Logarithms
   'ln #(Math/log (double %))
   'log10 #(Math/log10 (double %))
   'log2 #(/ (Math/log (double %)) (Math/log 2.0))
   'logb #(/ (Math/log (double %1)) (Math/log (double %2)))

   ;; Trigonometry (radians)
   'sin #(Math/sin (double %))
   'cos #(Math/cos (double %))
   'tan #(Math/tan (double %))
   'asin #(Math/asin (double %))
   'acos #(Math/acos (double %))
   'atan #(Math/atan (double %))
   'atan2 #(Math/atan2 (double %1) (double %2))
   'sinh #(Math/sinh (double %))
   'cosh #(Math/cosh (double %))
   'tanh #(Math/tanh (double %))

   ;; Trigonometry (degrees) - convenience functions
   'sind #(Math/sin (Math/toRadians (double %)))
   'cosd #(Math/cos (Math/toRadians (double %)))
   'tand #(Math/tan (Math/toRadians (double %)))

   ;; Rounding and magnitude
   'abs #(Math/abs (double %))
   'floor #(Math/floor (double %))
   'ceil #(Math/ceil (double %))
   'round #(Math/round (double %))
   'sign #(Math/signum (double %))
   'trunc #(long (Math/floor (double %)))

   ;; Constants
   'pi Math/PI
   'e Math/E
   'tau (* 2.0 Math/PI)
   'phi (/ (+ 1.0 (Math/sqrt 5.0)) 2.0)  ; golden ratio

   ;; Comparisons
   '< <, '> >, '<= <=, '>= >=, '= =, 'not= not=

   ;; Vector/sequence operations
   'sum #(reduce + %)
   'product #(reduce * %)
   'vmin #(apply min %)
   'vmax #(apply max %)
   'count count

   ;; Statistics
   'mean (fn [xs]
           (/ (reduce + xs) (count xs)))

   'median (fn [xs]
             (let [sorted (sort xs)
                   n (count sorted)
                   mid (quot n 2)]
               (if (odd? n)
                 (nth sorted mid)
                 (/ (+ (nth sorted mid)
                       (nth sorted (dec mid)))
                    2))))

   'variance (fn [xs]
               (let [m (/ (reduce + xs) (count xs))
                     n (count xs)]
                 (/ (reduce + (map #(* (- % m) (- % m)) xs))
                    n)))

   'stdev (fn [xs]
            (Math/sqrt ((fn [xs]
                          (let [m (/ (reduce + xs) (count xs))
                                n (count xs)]
                            (/ (reduce + (map #(* (- % m) (- % m)) xs))
                               n))) xs)))

   ;; Linear algebra basics
   'dot (fn [v1 v2]
          (reduce + (map * v1 v2)))

   'norm (fn [v]
           (Math/sqrt (reduce + (map #(* % %) v))))

   'cross (fn [[a1 a2 a3] [b1 b2 b3]]
            [(- (* a2 b3) (* a3 b2))
             (- (* a3 b1) (* a1 b3))
             (- (* a1 b2) (* a2 b1))])})

;; SCI context for safe evaluation
;; Note: No :allow list - we want to allow our math-fns bindings
;; No :deny list - security is non-issue (nrepl-eval already allows arbitrary code)
(def sci-ctx
  (sci/init
   {:bindings math-fns
    :realize-max 10000}))  ; prevent infinite sequences

(defn calculate
  "Evaluate mathematical expression with timeout protection.
   Returns {:result ... :type ... :expr ...} or {:error ... :type ... :expr ...}

   Timeout protection is CRITICAL for synchronous MCP interface - prevents hanging."
  [expr-string]
  (let [timeout-ms 5000
        result-promise (promise)]
    (future
      (try
        (deliver result-promise
                 (let [result (sci/eval-string* sci-ctx expr-string)]
                   {:result result
                    :type (str (type result))
                    :expr expr-string}))
        (catch Exception e
          (deliver result-promise
                   {:error (.getMessage e)
                    :expr expr-string
                    :type "error"}))))
    (deref result-promise timeout-ms
           {:error "Calculation timeout (>5s)"
            :type "timeout"
            :expr expr-string})))
