(ns nrepl-mcp-server.calculator-test
  (:require [clojure.test :refer [deftest is testing]]
            [nrepl-mcp-server.calculator :as calc]))

(deftest basic-arithmetic-test
  (testing "Basic arithmetic operations"
    (is (= {:result 5 :type "class java.lang.Long" :expr "(+ 2 3)"}
           (calc/calculate "(+ 2 3)")))
    (is (= {:result 1 :type "class java.lang.Long" :expr "(- 3 2)"}
           (calc/calculate "(- 3 2)")))
    (is (= {:result 6 :type "class java.lang.Long" :expr "(* 2 3)"}
           (calc/calculate "(* 2 3)")))
    (is (= {:result 2 :type "class java.lang.Long" :expr "(/ 6 3)"}
           (calc/calculate "(/ 6 3)")))
    (is (= {:result 1 :type "class java.lang.Long" :expr "(mod 7 3)"}
           (calc/calculate "(mod 7 3)")))))

(deftest math-functions-test
  (testing "Powers and roots"
    (is (= {:result 5.0 :type "class java.lang.Double" :expr "(sqrt 25)"}
           (calc/calculate "(sqrt 25)")))
    (is (= {:result 8.0 :type "class java.lang.Double" :expr "(pow 2 3)"}
           (calc/calculate "(pow 2 3)")))
    (is (= {:result 2.0 :type "class java.lang.Double" :expr "(cbrt 8)"}
           (calc/calculate "(cbrt 8)"))))

  (testing "Logarithms"
    (let [result (calc/calculate "(ln (exp 2))")]
      (is (< (Math/abs (- (:result result) 2.0)) 0.0001)))
    (is (= {:result 2.0 :type "class java.lang.Double" :expr "(log10 100)"}
           (calc/calculate "(log10 100)")))))

(deftest trigonometry-test
  (testing "Trigonometry (radians)"
    (let [sin-result (calc/calculate "(sin (/ pi 2))")]
      (is (< (Math/abs (- (:result sin-result) 1.0)) 0.0001)))
    (let [cos-result (calc/calculate "(cos 0)")]
      (is (< (Math/abs (- (:result cos-result) 1.0)) 0.0001))))

  (testing "Trigonometry (degrees)"
    (let [sind-result (calc/calculate "(sind 90)")]
      (is (< (Math/abs (- (:result sind-result) 1.0)) 0.0001)))
    (let [cosd-result (calc/calculate "(cosd 0)")]
      (is (< (Math/abs (- (:result cosd-result) 1.0)) 0.0001)))))

(deftest rounding-test
  (testing "Rounding operations"
    (is (= {:result 5.0 :type "class java.lang.Double" :expr "(abs -5)"}
           (calc/calculate "(abs -5)")))
    (is (= {:result 3.0 :type "class java.lang.Double" :expr "(floor 3.7)"}
           (calc/calculate "(floor 3.7)")))
    (is (= {:result 4.0 :type "class java.lang.Double" :expr "(ceil 3.2)"}
           (calc/calculate "(ceil 3.2)")))
    (is (= {:result 4 :type "class java.lang.Long" :expr "(round 3.7)"}
           (calc/calculate "(round 3.7)")))))

(deftest constants-test
  (testing "Mathematical constants"
    (let [pi-result (calc/calculate "pi")]
      (is (< (Math/abs (- (:result pi-result) Math/PI)) 0.0001)))
    (let [e-result (calc/calculate "e")]
      (is (< (Math/abs (- (:result e-result) Math/E)) 0.0001)))
    (let [tau-result (calc/calculate "tau")]
      (is (< (Math/abs (- (:result tau-result) (* 2 Math/PI))) 0.0001)))
    (let [phi-result (calc/calculate "phi")]
      (is (< (Math/abs (- (:result phi-result) 1.618)) 0.01)))))

(deftest statistics-test
  (testing "Statistical functions"
    (is (= {:result 3 :type "class java.lang.Long" :expr "(mean [1 2 3 4 5])"}
           (calc/calculate "(mean [1 2 3 4 5])")))
    (is (= {:result 3 :type "class java.lang.Long" :expr "(median [1 2 3 4 5])"}
           (calc/calculate "(median [1 2 3 4 5])")))
    (let [stdev-result (calc/calculate "(stdev [1 2 3 4 5])")]
      (is (< (Math/abs (- (:result stdev-result) 1.414)) 0.01)))))

(deftest vector-operations-test
  (testing "Vector operations"
    (is (= {:result 15 :type "class java.lang.Long" :expr "(sum [1 2 3 4 5])"}
           (calc/calculate "(sum [1 2 3 4 5])")))
    (is (= {:result 120 :type "class java.lang.Long" :expr "(product [1 2 3 4 5])"}
           (calc/calculate "(product [1 2 3 4 5])")))
    (is (= {:result 5 :type "class java.lang.Long" :expr "(vmax [1 2 3 4 5])"}
           (calc/calculate "(vmax [1 2 3 4 5])")))
    (is (= {:result 1 :type "class java.lang.Long" :expr "(vmin [1 2 3 4 5])"}
           (calc/calculate "(vmin [1 2 3 4 5])")))))

(deftest linear-algebra-test
  (testing "Linear algebra operations"
    (is (= {:result 32 :type "class java.lang.Long" :expr "(dot [1 2 3] [4 5 6])"}
           (calc/calculate "(dot [1 2 3] [4 5 6])")))
    (let [norm-result (calc/calculate "(norm [3 4])")]
      (is (< (Math/abs (- (:result norm-result) 5.0)) 0.0001)))
    (is (= {:result [-3 6 -3] :type "class clojure.lang.PersistentVector" :expr "(cross [1 2 3] [4 5 6])"}
           (calc/calculate "(cross [1 2 3] [4 5 6])")))))

(deftest threading-macros-test
  (testing "Threading macros"
    (is (= {:result 25.0 :type "class java.lang.Double" :expr "(-> 100 sqrt (* 2) (+ 5))"}
           (calc/calculate "(-> 100 sqrt (* 2) (+ 5))")))
    (is (= {:result 20 :type "class java.lang.Long" :expr "(->> [1 2 3 4 5] (map inc) (reduce +))"}
           (calc/calculate "(->> [1 2 3 4 5] (map inc) (reduce +))")))))

(deftest let-bindings-test
  (testing "Let bindings for complex calculations"
    (is (= {:result 5.0 :type "class java.lang.Double" :expr "(let [x 3 y 4] (sqrt (+ (* x x) (* y y))))"}
           (calc/calculate "(let [x 3 y 4] (sqrt (+ (* x x) (* y y))))")))
    (is (= {:result 11 :type "class java.lang.Long" :expr "(let [a 2 b 3] (+ a b (* a b)))"}
           (calc/calculate "(let [a 2 b 3] (+ a b (* a b)))")))))

(deftest error-handling-test
  (testing "Error handling for undefined functions"
    (let [result (calc/calculate "(undefined-fn 1)")]
      (is (contains? result :error))
      (is (= "error" (:type result)))))

  (testing "Division by zero"
    (let [result (calc/calculate "(/ 1 0)")]
      (is (contains? result :error))
      (is (= "error" (:type result)))))

  (testing "Invalid syntax"
    (let [result (calc/calculate "(+ 1")]
      (is (contains? result :error))
      (is (= "error" (:type result))))))

(deftest timeout-test
  (testing "Timeout protection (this test should complete in ~5 seconds)"
    (let [start (System/currentTimeMillis)
          result (calc/calculate "(loop [] (recur))")
          duration (- (System/currentTimeMillis) start)]
      (is (contains? result :error))
      (is (= "timeout" (:type result)))
      (is (> duration 4000))  ; Should be at least 4 seconds
      (is (< duration 7000))))) ; Should complete within 7 seconds

(deftest type-preservation-test
  (testing "Type preservation in results"
    (is (= "class java.lang.Long" (:type (calc/calculate "(+ 2 3)"))))
    (is (= "class java.lang.Double" (:type (calc/calculate "(sqrt 25)"))))
    (is (= "class clojure.lang.Ratio" (:type (calc/calculate "(/ 10 3)"))))))

(deftest complex-expressions-test
  (testing "Complex mathematical expressions"
    ;; Pythagorean theorem
    (is (= {:result 5.0 :type "class java.lang.Double" :expr "(sqrt (+ (pow 3 2) (pow 4 2)))"}
           (calc/calculate "(sqrt (+ (pow 3 2) (pow 4 2)))")))

    ;; Quadratic formula discriminant
    (let [result (calc/calculate "(let [a 1 b -3 c 2] (sqrt (- (* b b) (* 4 a c))))")]
      (is (< (Math/abs (- (:result result) 1.0)) 0.0001)))

    ;; Circle area
    (let [result (calc/calculate "(let [r 5] (* pi r r))")]
      (is (< (Math/abs (- (:result result) 78.54)) 0.01)))))
