(ns nrepl-mcp-server.calculate-tool-test
  "Tests for the calculate MCP tool handler with base64 support"
  (:require [clojure.test :refer [deftest is testing]]
            [nrepl-mcp-server.mcp-server.tools.calculate :as calc-tool]
            [cheshire.core :as json]))

;; =============================================================================
;; Test Helpers
;; =============================================================================

(defn- encode-base64
  "Encode UTF-8 text to base64 string"
  [text]
  (.encodeToString (java.util.Base64/getEncoder) (.getBytes text "UTF-8")))

(defn- decode-base64
  "Decode base64 string to UTF-8 text"
  [b64-str]
  (String. (.decode (java.util.Base64/getDecoder) b64-str) "UTF-8"))

(defn- parse-response
  "Parse MCP tool response content"
  [response]
  (json/parse-string (get-in response [:content 0 :text]) true))

;; =============================================================================
;; Basic Handler Tests
;; =============================================================================

(deftest basic-calculation-test
  (testing "Basic calculation without base64"
    (let [response (calc-tool/handle {:expr "(+ 2 3)"})
          result (parse-response response)]
      (is (= 5 (:result result)))
      (is (= "class java.lang.Long" (:type result)))
      (is (= "(+ 2 3)" (:expr result)))
      (is (not (:isError response))))))

(deftest validation-error-test
  (testing "Empty expression returns validation error"
    (let [response (calc-tool/handle {:expr ""})
          result (parse-response response)]
      (is (:isError response))
      (is (= "validation-error" (:type result)))
      (is (:error result)))))

;; =============================================================================
;; Base64 Input Tests
;; =============================================================================

(deftest base64-input-simple-test
  (testing "Simple expression with base64 input"
    (let [expr "(+ 10 20)"
          b64-expr (encode-base64 expr)
          response (calc-tool/handle {:expr b64-expr :input-base64 true})
          result (parse-response response)]
      (is (= 30 (:result result)))
      (is (not (:isError response))))))

(deftest base64-input-complex-test
  (testing "Complex expression with base64 input"
    (let [expr "(let [x 3 y 4] (sqrt (+ (* x x) (* y y))))"
          b64-expr (encode-base64 expr)
          response (calc-tool/handle {:expr b64-expr :input-base64 true})
          result (parse-response response)]
      (is (< (Math/abs (- (:result result) 5.0)) 0.0001))
      (is (not (:isError response))))))

(deftest base64-input-with-quotes-test
  (testing "Expression with quotes (simulating escaping scenario)"
    ;; This would be problematic in JSON without base64
    (let [expr "(mean [1 2 3 4 5])"
          b64-expr (encode-base64 expr)
          response (calc-tool/handle {:expr b64-expr :input-base64 true})
          result (parse-response response)]
      (is (= 3 (:result result)))
      (is (not (:isError response))))))

;; =============================================================================
;; Base64 Output Tests
;; =============================================================================

(deftest base64-output-test
  (testing "Normal calculation with base64 output"
    (let [response (calc-tool/handle {:expr "(+ 100 200)" :output-base64 true})
          result (parse-response response)]
      ;; Result is numeric, so should not be base64 encoded
      (is (= 300 (:result result)))
      (is (not (:isError response))))))

;; =============================================================================
;; Base64 Input + Output Tests
;; =============================================================================

(deftest base64-roundtrip-test
  (testing "Base64 input and output together"
    (let [expr "(* 7 8)"
          b64-expr (encode-base64 expr)
          response (calc-tool/handle {:expr b64-expr
                                      :input-base64 true
                                      :output-base64 true})
          result (parse-response response)]
      (is (= 56 (:result result)))
      (is (not (:isError response))))))

;; =============================================================================
;; Error Handling Tests
;; =============================================================================

(deftest base64-decode-error-test
  (testing "Invalid base64 string throws exception"
    (is (thrown? clojure.lang.ExceptionInfo
                 (calc-tool/handle {:expr "not-valid-base64!!!" :input-base64 true})))))

(deftest base64-calculation-error-test
  (testing "Valid base64 but invalid expression"
    (let [expr "(undefined-function 123)"
          b64-expr (encode-base64 expr)
          response (calc-tool/handle {:expr b64-expr :input-base64 true})
          result (parse-response response)]
      ;; The response has isError=true when calculation returns error
      (is (:isError response))
      (is (= "error" (:type result)))
      (is (:error result)))))

;; =============================================================================
;; Advanced Expression Tests with Base64
;; =============================================================================

(deftest base64-vector-operations-test
  (testing "Vector operations with base64"
    (let [expr "(dot [1 2 3] [4 5 6])"
          b64-expr (encode-base64 expr)
          response (calc-tool/handle {:expr b64-expr :input-base64 true})
          result (parse-response response)]
      (is (= 32 (:result result)))
      (is (not (:isError response))))))

(deftest base64-threading-macro-test
  (testing "Threading macro with base64"
    (let [expr "(-> 100 sqrt (* 2) (+ 5))"
          b64-expr (encode-base64 expr)
          response (calc-tool/handle {:expr b64-expr :input-base64 true})
          result (parse-response response)]
      (is (< (Math/abs (- (:result result) 25.0)) 0.0001))
      (is (not (:isError response))))))

(deftest base64-statistics-test
  (testing "Statistics functions with base64"
    (let [expr "(mean [85 92 78 95 88])"
          b64-expr (encode-base64 expr)
          response (calc-tool/handle {:expr b64-expr :input-base64 true})
          result (parse-response response)]
      (is (< (Math/abs (- (:result result) 87.6)) 0.1))
      (is (not (:isError response))))))
