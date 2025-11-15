(ns nrepl-mcp-server.token-conversion-test
  (:require [clojure.test :refer :all]
            [nrepl-mcp-server.calculator :as calc]))

;;=============================================================================
;; Token Amount Validation Tests
;;=============================================================================

(deftest token-amount-validation
  (testing "Valid token amounts - strings"
    (is (true? (calc/token-amount? [1000 "hash"])))
    (is (true? (calc/token-amount? [0.032 "usd"])))
    (is (true? (calc/token-amount? [1.5 "btc"]))))

  (testing "Valid token amounts - keywords"
    (is (true? (calc/token-amount? [1000 :hash])))
    (is (true? (calc/token-amount? [0.032 :usd])))
    (is (true? (calc/token-amount? [1.5 :btc]))))

  (testing "Invalid token amounts"
    (is (false? (calc/token-amount? "not-a-vector")))
    (is (false? (calc/token-amount? [1000])))  ; Missing unit
    (is (false? (calc/token-amount? [1000 "hash" "extra"])))  ; Too many elements
    (is (false? (calc/token-amount? ["hash" 1000])))  ; Reversed
    (is (false? (calc/token-amount? nil)))))

(deftest token-amount-accessors
  (testing "get-amount extracts amount"
    (is (= 1000 (calc/get-amount [1000 "hash"])))
    (is (= 0.032 (calc/get-amount [0.032 :usd]))))

  (testing "get-unit extracts and normalizes unit to keyword"
    (is (= :hash (calc/get-unit [1000 "hash"])))   ; String → keyword
    (is (= :usd (calc/get-unit [0.032 :usd]))))    ; Keyword → keyword

  (testing "token-amount constructor normalizes units"
    (is (= [1000 :hash] (calc/token-amount 1000 "hash")))   ; String input
    (is (= [0.032 :usd] (calc/token-amount 0.032 :usd)))))  ; Keyword input

(deftest normalize-unit-test
  (testing "normalize-unit converts strings to keywords"
    (is (= :hash (calc/normalize-unit "hash")))
    (is (= :usd (calc/normalize-unit "usd")))
    (is (= :btc (calc/normalize-unit "btc"))))

  (testing "normalize-unit preserves keywords"
    (is (= :hash (calc/normalize-unit :hash)))
    (is (= :usd (calc/normalize-unit :usd)))
    (is (= :btc (calc/normalize-unit :btc))))

  (testing "normalize-unit rejects invalid types"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Unit must be string or keyword"
         (calc/normalize-unit 123)))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Unit must be string or keyword"
         (calc/normalize-unit nil)))))

;;=============================================================================
;; Rate Validation Tests
;;=============================================================================

(deftest rate-validation-structure
  (testing "Valid rate structure"
    (let [rate ['/ [0.032 "usd"] [1 "hash"]]
          result (calc/valid-rate? rate)]
      (is (true? (:valid result)))))

  (testing "Invalid operator"
    (let [rate ['* [0.032 "usd"] [1 "hash"]]
          result (calc/valid-rate? rate)]
      (is (false? (:valid result)))
      (is (= "Must use / operator for rates" (:error result)))))

  (testing "Not a vector"
    (let [result (calc/valid-rate? "not-a-rate")]
      (is (false? (:valid result)))))

  (testing "Wrong vector length"
    (let [result (calc/valid-rate? ['/ [0.032 "usd"]])]
      (is (false? (:valid result))))))

(deftest rate-validation-amounts
  (testing "Valid positive amounts"
    (let [rate ['/ [0.032 "usd"] [1 "hash"]]
          result (calc/valid-rate? rate)]
      (is (true? (:valid result)))))

  (testing "Zero numerator rejected"
    (let [rate ['/ [0 "usd"] [1 "hash"]]
          result (calc/valid-rate? rate)]
      (is (false? (:valid result)))
      (is (= "Rate amounts cannot be zero" (:error result)))))

  (testing "Zero denominator rejected"
    (let [rate ['/ [0.032 "usd"] [0 "hash"]]
          result (calc/valid-rate? rate)]
      (is (false? (:valid result)))
      (is (= "Rate amounts cannot be zero" (:error result)))))

  (testing "Negative numerator rejected"
    (let [rate ['/ [-0.032 "usd"] [1 "hash"]]
          result (calc/valid-rate? rate)]
      (is (false? (:valid result)))
      (is (= "Rate amounts must be positive" (:error result)))))

  (testing "Negative denominator rejected"
    (let [rate ['/ [0.032 "usd"] [-1 "hash"]]
          result (calc/valid-rate? rate)]
      (is (false? (:valid result)))
      (is (= "Rate amounts must be positive" (:error result))))))

(deftest rate-validation-same-unit-rejection
  (testing "Same-unit rates are REJECTED (critical requirement)"
    (let [rate ['/ [2 "hash"] [1 "hash"]]
          result (calc/valid-rate? rate)]
      (is (false? (:valid result)))
      (is (= "Same-unit rates are invalid - use plain numbers for multipliers"
             (:error result)))))

  (testing "Different-unit rates are accepted"
    (let [rate ['/ [0.032 "usd"] [1 "hash"]]
          result (calc/valid-rate? rate)]
      (is (true? (:valid result))))))

(deftest rate-validation-keyword-support
  (testing "Rates with keyword units are valid"
    (let [rate ['/ [0.032 :usd] [1 :hash]]
          result (calc/valid-rate? rate)]
      (is (true? (:valid result)))))

  (testing "Mixed string/keyword units are valid"
    (let [rate ['/ [0.032 "usd"] [1 :hash]]
          result (calc/valid-rate? rate)]
      (is (true? (:valid result)))))

  (testing "Same-unit rejection works with keywords"
    (let [rate ['/ [2 :hash] [1 :hash]]
          result (calc/valid-rate? rate)]
      (is (false? (:valid result)))))

  (testing "Mixed same-unit rejection (string vs keyword)"
    (let [rate ['/ [2 "hash"] [1 :hash]]
          result (calc/valid-rate? rate)]
      (is (false? (:valid result))))))

;;=============================================================================
;; Token Conversion Tests
;;=============================================================================

(deftest token-conversion-from-denominator
  (testing "Convert FROM denominator TO numerator (multiply by rate)"
    ;; [1000 "hash"] × [/ [0.032 "usd"] [1 "hash"]] → [32.0 :usd]
    (let [result (calc/token-convert [1000 "hash"] "usd" ['/ [0.032 "usd"] [1 "hash"]])]
      (is (vector? result))
      (is (= 2 (count result)))
      (is (= 32.0 (first result)))
      (is (= :usd (second result))))))  ; Returns keyword!

(deftest token-conversion-keyword-support
  (testing "Conversion with keyword units (no escaping needed!)"
    ;; [1000 :hash] × [/ [0.032 :usd] [1 :hash]] → [32.0 :usd]
    (let [result (calc/token-convert [1000 :hash] :usd ['/ [0.032 :usd] [1 :hash]])]
      (is (= 32.0 (first result)))
      (is (= :usd (second result)))))

  (testing "Mixed string/keyword inputs normalize to keywords"
    ;; String input, keyword rate → keyword output
    (let [result (calc/token-convert [1000 "hash"] :usd ['/ [0.032 :usd] [1 :hash]])]
      (is (= :usd (second result))))

    ;; Keyword input, string rate → keyword output
    (let [result (calc/token-convert [1000 :hash] "usd" ['/ [0.032 "usd"] [1 "hash"]])]
      (is (= :usd (second result))))

    ;; All strings → keyword output
    (let [result (calc/token-convert [1000 "hash"] "usd" ['/ [0.032 "usd"] [1 "hash"]])]
      (is (= :usd (second result))))))

(deftest token-conversion-from-numerator
  (testing "Convert FROM numerator TO denominator (divide by rate)"
    ;; [10 "usd"] ÷ [/ [0.032 "usd"] [1 "hash"]] → [312.5 :hash]
    (let [result (calc/token-convert [10 "usd"] "hash" ['/ [0.032 "usd"] [1 "hash"]])]
      (is (vector? result))
      (is (= 2 (count result)))
      (is (= 312.5 (first result)))
      (is (= :hash (second result))))))

(deftest token-conversion-inferred-target
  (testing "Infer target from rate (2-arity signature)"
    ;; [1000 "hash"] with rate [/ [0.032 "usd"] [1 "hash"]] → infer :usd
    (let [result (calc/token-convert [1000 "hash"] ['/ [0.032 "usd"] [1 "hash"]])]
      (is (vector? result))
      (is (= 32.0 (first result)))
      (is (= :usd (second result))))

    ;; [10 "usd"] with rate [/ [0.032 "usd"] [1 "hash"]] → infer :hash
    (let [result (calc/token-convert [10 "usd"] ['/ [0.032 "usd"] [1 "hash"]])]
      (is (vector? result))
      (is (= 312.5 (first result)))
      (is (= :hash (second result))))))

(deftest token-conversion-error-handling
  (testing "Invalid rate throws error"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Invalid rate"
         (calc/token-convert [1000 "hash"] "usd" ['/ [2 "hash"] [1 "hash"]]))))

  (testing "Target doesn't match rate units throws error"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Target unit doesn't match rate units"
         (calc/token-convert [1000 "hash"] "btc" ['/ [0.032 "usd"] [1 "hash"]]))))

  (testing "Units don't match rate throws error"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Units don't match rate"
         (calc/token-convert [1000 "btc"] "usd" ['/ [0.032 "usd"] [1 "hash"]])))))

;;=============================================================================
;; Rate Utility Tests
;;=============================================================================

(deftest rate-inversion-test
  (testing "invert-rate swaps numerator and denominator"
    (let [rate ['/ [0.032 "usd"] [1 "hash"]]
          inverted (calc/invert-rate rate)]
      (is (= ['/ [1 "hash"] [0.032 "usd"]] inverted))))

  (testing "Double inversion returns original"
    (let [rate ['/ [0.032 "usd"] [1 "hash"]]
          double-inverted (calc/invert-rate (calc/invert-rate rate))]
      (is (= rate double-inverted)))))

(deftest rate-composition-test
  (testing "compose-rates chains conversions"
    ;; hash→usd: 0.032 usd/hash
    ;; usd→btc: 0.00001 btc/usd
    ;; hash→btc: 0.032 × 0.00001 = 0.00000032 btc/hash
    (let [hash-usd ['/ [0.032 "usd"] [1 "hash"]]
          usd-btc ['/ [0.00001 "btc"] [1 "usd"]]
          hash-btc (calc/compose-rates hash-usd usd-btc)]
      (is (= ['/ [0.00000032 "btc"] [1 "hash"]] hash-btc))))

  (testing "compose-rates validates unit compatibility"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Cannot compose rates - units don't chain"
         (calc/compose-rates
          ['/ [0.032 "usd"] [1 "hash"]]
          ['/ [0.00001 "btc"] [1 "eth"]])))))

(deftest rate-normalization-test
  (testing "normalize-rate prefers denominator = 1"
    (let [rate ['/ [3.2 "usd"] [100 "hash"]]
          normalized (calc/normalize-rate rate)]
      (is (= ['/ [0.032 "usd"] [1 "hash"]] normalized))))

  (testing "already-normalized rate unchanged"
    (let [rate ['/ [0.032 "usd"] [1 "hash"]]
          normalized (calc/normalize-rate rate)]
      (is (= rate normalized)))))

;;=============================================================================
;; Integration Tests
;;=============================================================================

(deftest integration-multi-hop-conversion
  (testing "Multi-hop conversion: hash → usd → btc"
    (let [hash-usd ['/ [0.032 "usd"] [1 "hash"]]
          usd-btc ['/ [0.00001 "btc"] [1 "usd"]]
          hash-btc (calc/compose-rates hash-usd usd-btc)

          ;; Convert 1000 hash → btc
          result (calc/token-convert [1000 "hash"] hash-btc)]
      (is (= 0.00032 (first result)))
      (is (= :btc (second result))))))

(deftest integration-bidirectional-conversion
  (testing "Conversion is reversible"
    (let [rate ['/ [0.032 "usd"] [1 "hash"]]

          ;; hash → usd
          usd-result (calc/token-convert [1000 "hash"] rate)

          ;; usd → hash (using inverted rate)
          hash-result (calc/token-convert usd-result (calc/invert-rate rate))]
      (is (= 1000.0 (first hash-result)))
      (is (= :hash (second hash-result))))))

;;=============================================================================
;; Real-World Scenarios
;;=============================================================================

(deftest real-world-hash-usd-conversion
  (testing "Convert 17500000 nhash to hash to USD"
    ;; 17,500,000 nhash = 0.0175 hash
    ;; 0.0175 hash × 0.032 usd/hash = 0.00056 usd
    (let [nhash-hash ['/ [1 "hash"] [1000000000 "nhash"]]
          hash-usd ['/ [0.032 "usd"] [1 "hash"]]

          ;; Step 1: nhash → hash
          hash-amount (calc/token-convert [17500000 "nhash"] nhash-hash)

          ;; Step 2: hash → usd
          usd-amount (calc/token-convert hash-amount hash-usd)]
      ;; Use approximate comparison due to floating point
      (is (< (Math/abs (- 0.00056 (first usd-amount))) 0.00001))
      (is (= :usd (second usd-amount))))))

(deftest real-world-btc-sats-conversion
  (testing "Convert 1.5 BTC to satoshis"
    (let [btc-sats ['/ [100000000 "sats"] [1 "btc"]]
          result (calc/token-convert [1.5 "btc"] btc-sats)]
      (is (= 150000000.0 (first result)))
      (is (= :sats (second result))))))

(deftest real-world-eth-wei-conversion
  (testing "Convert 2.5 ETH to wei"
    (let [eth-wei ['/ [1000000000000000000N "wei"] [1 "eth"]]
          result (calc/token-convert [2.5 "eth"] eth-wei)]
      ;; Result is a double, check it's close to expected
      (is (< (Math/abs (- 2.5E18 (first result))) 1E10))
      (is (= :wei (second result))))))
