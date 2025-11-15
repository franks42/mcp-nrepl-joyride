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
                     :days-remaining (if unlocked 0 days-remaining)}))})

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
