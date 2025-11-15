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

   ;; Mathematical Constants
   'pi Math/PI
   'e Math/E
   'tau (* 2.0 Math/PI)
   'phi (/ (+ 1.0 (Math/sqrt 5.0)) 2.0)  ; golden ratio

   ;; Crypto/Blockchain Decimals
   'eth-decimals 18
   'btc-decimals 8
   'hash-decimals 9
   'usdc-decimals 6
   'usdt-decimals 6

   ;; Crypto Unit Conversions
   'wei-per-eth 1000000000000000000N
   'gwei-per-eth 1000000000N
   'sat-per-btc 100000000N

   ;; Time/Date Constants
   'year-seconds 31536000
   'day-seconds 86400
   'hour-seconds 3600
   'minute-seconds 60
   'week-seconds 604800
   'year-days 365
   'leap-year-days 366

   ;; Blockchain Block Times (seconds)
   'eth-block-time-seconds 12
   'btc-block-time-seconds 600
   'blocks-per-day-eth 7200
   'blocks-per-day-btc 144

   ;; Finance/Time Periods
   'months-per-year 12
   'weeks-per-year 52
   'quarters-per-year 4
   'days-per-week 7

   ;; DeFi Common Values
   'typical-slippage 0.005  ; 0.5%
   'high-slippage 0.01      ; 1%

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
             (- (* a1 b2) (* a2 b1))])

   ;; Number Formatting Utilities
   'with-commas (fn [num]
                  (let [s (str num)
                        dot-idx (or (first (keep-indexed #(when (= %2 \.) %1) s)) (count s))
                        whole (subs s 0 dot-idx)
                        decimal (when (< dot-idx (count s)) (subs s dot-idx))
                        rev-whole (vec (reverse whole))
                        grouped (partition-all 3 rev-whole)
                        rev-grouped (map reverse grouped)
                        formatted (apply str (reverse (interpose "," (map #(apply str %) rev-grouped))))]
                    (str formatted (or decimal ""))))

   'round-to (fn [num decimals]
               (let [factor (Math/pow 10 decimals)]
                 (/ (Math/round (* (double num) factor)) factor)))

   'scientific (fn [num]
                 (format "%.2e" (double num)))

   'to-decimal (fn [num]
                 (double num))

   ;; Financial & Percentage Functions (return rich maps)
   'percent-change (fn [old new]
                     (let [change (- new old)
                           percent (* (/ change old) 100.0)
                           direction (cond
                                       (pos? change) :increase
                                       (neg? change) :decrease
                                       :else :unchanged)
                           formatted (str (if (pos? change) "+" "") percent "%")]
                       {:change change
                        :percent percent
                        :direction direction
                        :formatted formatted
                        :old-value old
                        :new-value new}))

   'percent-of (fn [part total]
                 (let [percentage (* (/ part total) 100.0)
                       decimal (/ part total)]
                   {:percentage percentage
                    :decimal decimal
                    :formatted (str percentage "%")
                    :part part
                    :total total}))

   'percentage (fn [total percent]
                 (let [value (* total (/ percent 100.0))]
                   {:value value
                    :of-total total
                    :percent percent
                    :formatted (str value " (" percent "% of " total ")")}))

   'roi (fn [initial final]
          (let [profit (- final initial)
                percent (* (/ profit initial) 100.0)
                multiplier (/ final initial)]
            {:profit profit
             :roi-percent percent
             :multiplier multiplier
             :formatted (str (if (pos? profit) "+" "") percent "%")
             :initial initial
             :final final}))

   'compound-interest (fn [principal rate periods]
                        (let [final (* principal (Math/pow (+ 1 rate) periods))
                              total-interest (- final principal)]
                          {:initial principal
                           :final final
                           :total-interest total-interest
                           :rate rate
                           :periods periods
                           :formatted (str "$" final " (+" total-interest " interest)")}))

   'simple-interest (fn [principal rate time]
                      (let [interest (* principal rate time)
                            final (+ principal interest)]
                        {:initial principal
                         :final final
                         :interest interest
                         :rate rate
                         :time time
                         :formatted (str "$" final " (+" interest " interest)")}))

   'market-share (fn [my-amount total]
                   (let [percentage (* (/ my-amount total) 100.0)
                         decimal (/ my-amount total)
                         ratio (str "1:" (long (/ total my-amount)))]
                     {:percentage percentage
                      :decimal decimal
                      :ratio ratio
                      :formatted (str percentage "%")
                      :my-amount my-amount
                      :total total}))

   'token-value (fn [price holdings]
                  (let [total-value (* price holdings)]
                    {:total-value total-value
                     :price price
                     :holdings holdings
                     :formatted (str "$" total-value)
                     :per-token (str "$" price)}))

   ;; Date/Time/Duration Functions (using java.time, all UTC)
   'unix-now (fn []
               (let [now (java.time.Instant/now)
                     unix (.getEpochSecond now)
                     dt (java.time.ZonedDateTime/ofInstant now java.time.ZoneOffset/UTC)]
                 {:unix unix
                  :iso (str (.toLocalDate dt))
                  :date (str (.toLocalDate dt))
                  :time (str (.toLocalTime dt))
                  :formatted (str dt)}))

   'unix-to-date (fn [timestamp]
                   (let [instant (java.time.Instant/ofEpochSecond (long timestamp))
                         dt (java.time.ZonedDateTime/ofInstant instant java.time.ZoneOffset/UTC)
                         ld (.toLocalDate dt)]
                     {:unix timestamp
                      :date (str ld)
                      :iso (str ld)
                      :year (.getYear ld)
                      :month (.getMonthValue ld)
                      :day (.getDayOfMonth ld)}))

   'date-to-unix (fn [date-str]
                   (let [ld (java.time.LocalDate/parse date-str)
                         zdt (java.time.ZonedDateTime/of ld (java.time.LocalTime/of 0 0) java.time.ZoneOffset/UTC)
                         instant (.toInstant zdt)
                         unix (.getEpochSecond instant)]
                     {:date date-str
                      :unix unix
                      :iso (str ld)}))

   'days-between (fn [start end]
                   (let [start-ld (if (number? start)
                                    (.toLocalDate (java.time.ZonedDateTime/ofInstant
                                                   (java.time.Instant/ofEpochSecond (long start))
                                                   java.time.ZoneOffset/UTC))
                                    (java.time.LocalDate/parse (str start)))
                         end-ld (if (number? end)
                                  (.toLocalDate (java.time.ZonedDateTime/ofInstant
                                                 (java.time.Instant/ofEpochSecond (long end))
                                                 java.time.ZoneOffset/UTC))
                                  (java.time.LocalDate/parse (str end)))
                         days (.between java.time.temporal.ChronoUnit/DAYS start-ld end-ld)]
                     {:start (str start-ld)
                      :end (str end-ld)
                      :days days
                      :weeks (/ days 7)
                      :hours (* days 24)}))

   'add-days (fn [date days-to-add]
               (let [ld (if (number? date)
                          (.toLocalDate (java.time.ZonedDateTime/ofInstant
                                         (java.time.Instant/ofEpochSecond (long date))
                                         java.time.ZoneOffset/UTC))
                          (java.time.LocalDate/parse (str date)))
                     new-ld (.plusDays ld days-to-add)
                     zdt (java.time.ZonedDateTime/of new-ld (java.time.LocalTime/of 0 0) java.time.ZoneOffset/UTC)
                     new-unix (.getEpochSecond (.toInstant zdt))]
                 {:original (str ld)
                  :added-days days-to-add
                  :result (str new-ld)
                  :unix new-unix}))

   'add-seconds (fn [timestamp seconds-to-add]
                  (let [instant (java.time.Instant/ofEpochSecond (long timestamp))
                        new-instant (.plusSeconds instant seconds-to-add)
                        new-unix (.getEpochSecond new-instant)
                        dt (java.time.ZonedDateTime/ofInstant new-instant java.time.ZoneOffset/UTC)]
                    {:original timestamp
                     :added-seconds seconds-to-add
                     :result new-unix
                     :date (str (.toLocalDate dt))}))

   'days-until (fn [date]
                 (let [target-ld (if (number? date)
                                   (.toLocalDate (java.time.ZonedDateTime/ofInstant
                                                  (java.time.Instant/ofEpochSecond (long date))
                                                  java.time.ZoneOffset/UTC))
                                   (java.time.LocalDate/parse (str date)))
                       now-ld (java.time.LocalDate/now java.time.ZoneOffset/UTC)
                       days (.between java.time.temporal.ChronoUnit/DAYS now-ld target-ld)]
                   {:target-date (str target-ld)
                    :days-until days
                    :weeks-until (/ days 7)
                    :in-past (neg? days)}))

   'timestamp-in-days (fn [days]
                        (let [now (java.time.Instant/now)
                              target (.plusSeconds now (* days 86400))
                              unix (.getEpochSecond target)
                              dt (java.time.ZonedDateTime/ofInstant target java.time.ZoneOffset/UTC)]
                          {:days-from-now days
                           :target-date (str (.toLocalDate dt))
                           :unix unix}))

   'lock-period-end (fn [start-unix duration-days]
                      (let [start-instant (java.time.Instant/ofEpochSecond (long start-unix))
                            end-instant (.plusSeconds start-instant (* duration-days 86400))
                            end-unix (.getEpochSecond end-instant)
                            start-dt (java.time.ZonedDateTime/ofInstant start-instant java.time.ZoneOffset/UTC)
                            end-dt (java.time.ZonedDateTime/ofInstant end-instant java.time.ZoneOffset/UTC)
                            now (java.time.Instant/now)
                            days-remaining (long (/ (- end-unix (.getEpochSecond now)) 86400))]
                        {:locked-at (str (.toLocalDate start-dt))
                         :duration-days duration-days
                         :unlock-date (str (.toLocalDate end-dt))
                         :unlock-unix end-unix
                         :days-remaining days-remaining
                         :unlocked (neg? days-remaining)}))

   'is-unlocked (fn [lock-end-timestamp]
                  (let [end-instant (java.time.Instant/ofEpochSecond (long lock-end-timestamp))
                        now (java.time.Instant/now)
                        unlocked (.isAfter now end-instant)
                        dt (java.time.ZonedDateTime/ofInstant end-instant java.time.ZoneOffset/UTC)
                        days-remaining (long (/ (- lock-end-timestamp (.getEpochSecond now)) 86400))]
                    {:unlock-date (str (.toLocalDate dt))
                     :unlocked unlocked
                     :days-remaining (if unlocked 0 days-remaining)}))

   ;; Blockchain/Crypto Unit Conversions (High Precision)
   'to-smallest-unit (fn [amount decimals]
                       (let [factor (Math/pow 10 decimals)
                             smallest-unit (long (* amount factor))]
                         {:amount amount
                          :smallest-unit smallest-unit
                          :decimals decimals
                          :formatted (str smallest-unit)}))

   'from-smallest-unit (fn [units decimals]
                         (let [factor (Math/pow 10 decimals)
                               amount (/ units factor)]
                           {:smallest-unit units
                            :amount amount
                            :decimals decimals
                            :formatted (str amount)}))

   'wei->ether (fn [wei]
                 (let [ether (/ wei 1000000000000000000.0)
                       gwei (/ wei 1000000000.0)]
                   {:wei wei
                    :ether ether
                    :gwei gwei
                    :formatted (str ether " ETH")}))

   'ether->wei (fn [eth]
                 (let [wei (long (* eth 1000000000000000000))
                       gwei (long (* eth 1000000000))]
                   {:ether eth
                    :wei wei
                    :gwei gwei
                    :formatted (str wei " wei")}))

   'sats->btc (fn [sats]
                (let [btc (/ sats 100000000.0)]
                  {:satoshis sats
                   :btc btc
                   :formatted (str btc " BTC")}))

   'btc->sats (fn [btc]
                (let [sats (long (* btc 100000000))]
                  {:btc btc
                   :satoshis sats
                   :formatted (str sats " sats")}))

   ;; Market & Portfolio Calculations
   'market-cap (fn [price circulation]
                 (let [mcap (* price circulation)
                       billions (/ mcap 1000000000.0)]
                   {:price price
                    :circulation circulation
                    :market-cap mcap
                    :billions billions
                    :formatted (str "$" mcap)}))

   ;; DeFi Calculations
   'impermanent-loss (fn [initial-price current-price]
                       (let [price-ratio (/ current-price initial-price)
                             il-multiplier (/ (* 2 (Math/sqrt price-ratio)) (+ 1 price-ratio))
                             il-percent (* (- 1 il-multiplier) 100.0)
                             hodl-value (* 0.5 (+ initial-price current-price))
                             pool-value (* initial-price il-multiplier)
                             vs-hodl-percent (* (/ (- pool-value hodl-value) hodl-value) 100.0)]
                         {:initial-price initial-price
                          :current-price current-price
                          :price-change-percent (* (- price-ratio 1) 100.0)
                          :impermanent-loss-percent il-percent
                          :vs-hodl-percent vs-hodl-percent
                          :formatted (str il-percent "% IL")}))

   'liquidity-pool-share (fn [token-a-amount token-b-amount pool-token-a pool-token-b]
                           (let [pool-share-percent (* (/ token-a-amount pool-token-a) 100.0)]
                             {:your-token-a token-a-amount
                              :your-token-b token-b-amount
                              :pool-token-a pool-token-a
                              :pool-token-b pool-token-b
                              :pool-share-percent pool-share-percent
                              :formatted (str pool-share-percent "% of pool")}))

   'apy-to-apr (fn [apy compounds-per-year]
                 (let [apr (* (- (Math/pow (+ 1 (/ apy 100.0)) (/ 1.0 compounds-per-year)) 1) compounds-per-year 100.0)
                       daily-rate (/ apr 365.0)]
                   {:apy apy
                    :apr apr
                    :compounds compounds-per-year
                    :daily-rate daily-rate}))

   'apr-to-apy (fn [apr compounds-per-year]
                 (let [apy (* (- (Math/pow (+ 1 (/ apr compounds-per-year 100.0)) compounds-per-year) 1) 100.0)
                       daily-rate (/ apr 365.0)]
                   {:apr apr
                    :apy apy
                    :compounds compounds-per-year
                    :daily-rate daily-rate}))

   'staking-rewards (fn [amount apy duration-days]
                      (let [daily-rate (/ apy 365.0 100.0)
                            rewards (* amount daily-rate duration-days)
                            total (+ amount rewards)
                            daily-rewards (/ rewards duration-days)]
                        {:principal amount
                         :apy apy
                         :days duration-days
                         :rewards rewards
                         :total total
                         :daily-rewards daily-rewards
                         :formatted (str "$" rewards " rewards")}))

   'slippage-impact (fn [amount-in reserve-in reserve-out]
                      (let [amount-in-with-fee (* amount-in 0.997)  ; 0.3% fee
                            amount-out (/ (* amount-in-with-fee reserve-out)
                                          (+ reserve-in amount-in-with-fee))
                            price-before (/ reserve-out reserve-in)
                            price-after (/ (- reserve-out amount-out) (+ reserve-in amount-in))
                            price-impact-percent (* (/ (- price-after price-before) price-before) 100.0)]
                        {:amount-in amount-in
                         :reserve-in reserve-in
                         :reserve-out reserve-out
                         :amount-out amount-out
                         :price-impact-percent (Math/abs price-impact-percent)
                         :slippage (Math/abs price-impact-percent)
                         :effective-price (/ amount-in amount-out)
                         :formatted (str (Math/abs price-impact-percent) "% slippage")}))

   ;; Leverage & Liquidation
   'liquidation-price (fn [collateral-value borrowed-value liquidation-threshold]
                        (let [liq-price (* borrowed-value (/ 1.0 liquidation-threshold))
                              health-factor (/ (* collateral-value liquidation-threshold) borrowed-value)
                              safe (>= health-factor 1.0)]
                          {:collateral collateral-value
                           :borrowed borrowed-value
                           :threshold liquidation-threshold
                           :liquidation-price liq-price
                           :health-factor health-factor
                           :safe safe
                           :formatted (str "$" liq-price " liquidation")}))

   'leverage-ratio (fn [collateral borrowed]
                     (let [equity (- collateral borrowed)
                           leverage (/ collateral equity)
                           ltv (/ borrowed collateral)]
                       {:collateral collateral
                        :borrowed borrowed
                        :leverage leverage
                        :equity equity
                        :ltv ltv
                        :formatted (str leverage "x leverage")}))

   ;; Gas & Fee Calculations
   'gas-cost (fn [gas-used gwei-price eth-price]
               (let [gas-cost-gwei (* gas-used gwei-price)
                     gas-cost-eth (/ gas-cost-gwei 1000000000.0)
                     gas-cost-usd (* gas-cost-eth eth-price)]
                 {:gas-used gas-used
                  :gwei-price gwei-price
                  :gas-cost-eth gas-cost-eth
                  :gas-cost-usd gas-cost-usd
                  :eth-price eth-price
                  :formatted (str "$" gas-cost-usd)}))})

;;=============================================================================
;; Phase 3B: Type-Safe Token Conversion System
;;=============================================================================

;; Unit Normalization

(defn normalize-unit
  "Convert string or keyword to keyword for consistent unit handling.
   Accepts both for flexibility:
   - Keywords: easier typing, no escaping (e.g., :usd, :hash, :btc)
   - Strings: compatibility with external data (e.g., \"usd\", \"hash\", \"btc\")

   Examples:
     (normalize-unit :usd)   => :usd
     (normalize-unit \"usd\") => :usd"
  [unit]
  (cond
    (keyword? unit) unit
    (string? unit) (keyword unit)
    :else (throw (ex-info "Unit must be string or keyword"
                          {:unit unit
                           :type (type unit)}))))

;; Token Amount Support Functions

(defn token-amount?
  "Check if value is a valid token amount tuple: [amount unit]
   Unit can be keyword or string.

   Examples:
     (token-amount? [1000 :hash])    => true
     (token-amount? [0.032 \"usd\"])  => true
     (token-amount? [1.5 :btc])      => true"
  [x]
  (and (vector? x)
       (= 2 (count x))
       (number? (first x))
       (or (keyword? (second x)) (string? (second x)))))

(defn get-amount
  "Extract amount from token tuple"
  [[amt _]]
  amt)

(defn get-unit
  "Extract unit from token tuple as normalized keyword"
  [[_ unit]]
  (normalize-unit unit))

(defn token-amount
  "Construct a token amount tuple with normalized unit.
   Unit can be provided as keyword or string.

   Examples:
     (token-amount 1000 :hash)    => [1000 :hash]
     (token-amount 0.032 \"usd\")  => [0.032 :usd]"
  [amt unit]
  [amt (normalize-unit unit)])

;; Rate Validation

(defn valid-rate?
  "Validate exchange rate structure and values.
   Returns {:valid true} or {:valid false :error msg}

   CRITICAL: Rejects same-unit rates (e.g., [/ [2 'hash'] [1 'hash']])
   as they are semantically nonsensical - use plain multipliers instead."
  [rate]
  (if-not (and (vector? rate)
               (= 3 (count rate)))
    {:valid false :error "Rate must be a 3-element vector"}
    (let [[op num denom] rate]
      (cond
        (not= op '/)
        {:valid false :error "Must use / operator for rates"}

        (not (and (token-amount? num) (token-amount? denom)))
        {:valid false :error "Rate numerator and denominator must be token amounts"}

        :else
        (let [num-amt (get-amount num)
              denom-amt (get-amount denom)]
          (cond
            (or (zero? num-amt) (zero? denom-amt))
            {:valid false :error "Rate amounts cannot be zero"}

            (or (neg? num-amt) (neg? denom-amt))
            {:valid false :error "Rate amounts must be positive"}

            (= (get-unit num) (get-unit denom))
            {:valid false :error "Same-unit rates are invalid - use plain numbers for multipliers"}

            :else
            {:valid true}))))))

;; Token Conversion Function

(defn token-convert
  "Convert token amounts using exchange rates.

  Signatures:
    (token-convert [amt from] to-unit rate)  ; Full validation
    (token-convert [amt from] rate)           ; Infer target from rate

  Examples:
    (token-convert [1000 'hash'] 'usd' [/ [0.032 'usd'] [1 'hash']])
    => [32.0 'usd']

    (token-convert [10 'usd'] [/ [0.032 'usd'] [1 'hash']])
    => [312.5 'hash']"

  ;; Two-arity: infer target from rate
  ([amount-tuple rate]
   (let [[_ num denom] rate
         from-unit (get-unit amount-tuple)
         to-unit (if (= from-unit (get-unit denom))
                   (get-unit num)
                   (get-unit denom))]
     (token-convert amount-tuple to-unit rate)))

  ;; Three-arity: explicit validation
  ([amount-tuple to-unit rate]
   (when-not (token-amount? amount-tuple)
     (throw (ex-info "First argument must be a token amount tuple [amount unit]"
                     {:provided amount-tuple})))

   ;; Validate rate
   (let [validation (valid-rate? rate)]
     (when-not (:valid validation)
       (throw (ex-info "Invalid rate" validation))))

   ;; Extract and normalize all components
   (let [[amount from-unit] amount-tuple
         from-unit-norm (normalize-unit from-unit)
         [_ [num-amt num-unit] [denom-amt denom-unit]] rate
         num-unit-norm (normalize-unit num-unit)
         denom-unit-norm (normalize-unit denom-unit)
         to-unit-norm (normalize-unit to-unit)]

     ;; Validate target matches rate (using normalized units)
     (when-not (or (= to-unit-norm num-unit-norm) (= to-unit-norm denom-unit-norm))
       (throw (ex-info "Target unit doesn't match rate units"
                       {:target to-unit-norm
                        :rate-units [num-unit-norm denom-unit-norm]})))

     ;; Perform conversion (using normalized units for comparison and output)
     (cond
       ;; FROM denominator TO numerator: multiply by (num/denom)
       (and (= from-unit-norm denom-unit-norm) (= to-unit-norm num-unit-norm))
       [(*' amount (/ num-amt denom-amt)) num-unit-norm]

       ;; FROM numerator TO denominator: divide by (num/denom)
       (and (= from-unit-norm num-unit-norm) (= to-unit-norm denom-unit-norm))
       [(*' amount (/ denom-amt num-amt)) denom-unit-norm]

       :else
       (throw (ex-info "Units don't match rate"
                       {:from from-unit-norm
                        :to to-unit-norm
                        :rate rate}))))))

;; Rate Utility Functions

(defn invert-rate
  "Invert an exchange rate (swap numerator and denominator)

   Example:
     (invert-rate [/ [0.032 'usd'] [1 'hash']])
     => [/ [31.25 'hash'] [1 'usd']]"
  [[_ num denom]]
  ['/ denom num])

(defn compose-rates
  "Compose two rates for multi-hop conversion.

   Example: hash→usd + usd→btc = hash→btc
     (compose-rates
       [/ [0.032 'usd'] [1 'hash']]
       [/ [0.00001 'btc'] [1 'usd']])
     => [/ [0.00000032 'btc'] [1 'hash']]"
  [[_ [num1 unit1] [denom1 unit1-denom]]
   [_ [num2 unit2] [denom2 unit2-denom]]]
  (when-not (= unit1 unit2-denom)
    (throw (ex-info "Cannot compose rates - units don't chain"
                    {:rate1-numerator unit1
                     :rate2-denominator unit2-denom})))
  ['/ [(*' num1 num2) unit2] [(*' denom1 denom2) unit1-denom]])

(defn normalize-rate
  "Normalize rate to have denominator = 1

   Example:
     (normalize-rate [/ [3.2 'usd'] [100 'hash']])
     => [/ [0.032 'usd'] [1 'hash']]"
  [[_ [num-amt num-unit] [denom-amt denom-unit]]]
  (if (= 1 denom-amt)
    ['/ [num-amt num-unit] [denom-amt denom-unit]]
    ['/ [(/ num-amt denom-amt) num-unit] [1 denom-unit]]))

;; Add token conversion functions to math-fns for use in expressions
(def token-conversion-fns
  {'token-convert token-convert
   'valid-rate? valid-rate?
   'invert-rate invert-rate
   'compose-rates compose-rates
   'normalize-rate normalize-rate
   'normalize-unit normalize-unit
   'token-amount token-amount
   'token-amount? token-amount?
   'get-amount get-amount
   'get-unit get-unit})

;; Merge all functions for SCI context
(def all-math-fns
  (merge math-fns token-conversion-fns))

;; SCI context for safe evaluation
;; Note: No :allow list - we want to allow our math-fns bindings
;; No :deny list - security is non-issue (nrepl-eval already allows arbitrary code)
(def sci-ctx
  (sci/init
   {:bindings all-math-fns
    :realize-max 10000}))  ; prevent infinite sequences

(defn- enhance-error-message
  "Enhance error messages with contextual hints based on error patterns"
  [error-msg expr]
  (let [msg (.toLowerCase error-msg)]
    (cond
      ;; Division by zero
      (or (re-find #"divide.*zero" msg)
          (re-find #"infinity" msg))
      {:error error-msg
       :hint "Division by zero detected. Check denominator values."
       :suggestion "Use conditional logic: (if (zero? x) 0 (/ y x))"}

      ;; Undefined symbol
      (re-find #"unable to resolve symbol" msg)
      (let [symbol (second (re-find #"symbol: (\S+)" msg))]
        {:error error-msg
         :hint (str "Function '" symbol "' not found.")
         :suggestion "Check available functions or use 'let' to define variables."
         :available-help "Common functions: +, -, *, /, sqrt, pow, sin, cos, mean, etc."})

      ;; Wrong number of arguments
      (re-find #"wrong number of args" msg)
      {:error error-msg
       :hint "Function called with incorrect number of arguments."
       :suggestion "Check function signature. Example: (pow base exponent) needs 2 args."}

      ;; Type casting errors
      (or (re-find #"cannot be cast" msg)
          (re-find #"class.*cannot be cast" msg))
      {:error error-msg
       :hint "Type mismatch - trying to use incompatible types."
       :suggestion "Ensure numeric values: use (double x) or check input types."}

      ;; Invalid vector/sequence operations
      (re-find #"don't know how to create" msg)
      {:error error-msg
       :hint "Invalid data structure syntax."
       :suggestion "Use vectors [1 2 3] or sequences with functions: (mean [1 2 3])"}

      ;; Date/time parsing errors
      (re-find #"parse" msg)
      {:error error-msg
       :hint "Date/time parsing failed."
       :suggestion "Use ISO format 'YYYY-MM-DD' or unix timestamps (number)."}

      ;; Negative sqrt/log errors
      (re-find #"nan" msg)
      {:error error-msg
       :hint "Mathematical operation resulted in NaN (Not a Number)."
       :suggestion "Check for negative sqrt/log arguments or invalid operations."}

      ;; DeFi specific errors
      (and (re-find #"zero" msg)
           (or (re-find #"pool" expr)
               (re-find #"reserve" expr)
               (re-find #"liquidity" expr)))
      {:error error-msg
       :hint "DeFi calculation error - likely zero pool reserves."
       :suggestion "Ensure pool reserves and liquidity values are non-zero."}

      ;; Generic fallback
      :else
      {:error error-msg
       :hint "Expression evaluation failed."
       :suggestion "Check syntax: use prefix notation like (+ 1 2) not (1 + 2)."})))

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
          (let [enhanced (enhance-error-message (.getMessage e) expr-string)]
            (deliver result-promise
                     (merge {:expr expr-string
                             :type "error"}
                            enhanced))))))
    (deref result-promise timeout-ms
           {:error "Calculation timeout (>5s)"
            :type "timeout"
            :expr expr-string
            :hint "Expression took too long to evaluate (>5 seconds)."
            :suggestion "Simplify expression or check for infinite loops."})))
