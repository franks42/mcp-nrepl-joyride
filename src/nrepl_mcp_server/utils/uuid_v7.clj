(ns nrepl-mcp-server.utils.uuid-v7
  "RFC 9562 compliant UUID v7 implementation for Babashka/Clojure.
   
   UUID v7 provides time-based UUIDs with lexicographic ordering.
   This implementation follows RFC 9562 exactly without dependencies.
   
   CRITICAL: Uses synchronized generation to prevent uniqueness clashes
   when multiple UUIDs are generated within the same millisecond."
  (:require [clojure.string :as str]))

;; Global state for synchronized UUID v7 generation
(defonce ^:private uuid-v7-state
  (atom {:last-timestamp 0
         :sequence-counter 0}))

(defn uuid-v7
  "Generate RFC 9562 compliant UUID v7 with guaranteed temporal ordering.
  
  CRITICAL: NO RANDOM FALLBACK - When sequence overflows, generation waits
  for next millisecond to maintain perfect temporal ordering guarantee.
  
  UUID v7 Layout (128 bits total) - Optimized for Maximum Sequence Space:
   0                   1                   2                   3
   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  |                           unix_ts_ms                          |
  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  |          unix_ts_ms           |  ver  |    sequence (high)    |
  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  |var|        sequence (low)     |           minimal random      |
  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  |                         minimal random                        |
  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  
  Features:
    - 48-bit millisecond precision Unix timestamp
    - Version 7 in bits 48-51 (0x7000)  
    - 26-bit monotonic sequence counter (67M IDs/ms capacity)
    - Variant '10' in bits 64-65 (0x8000)
    - 32-bit randomness for remaining bits (non-uniqueness critical)
    - GUARANTEED temporal ordering (no random fallback ever)
    - Blocks on overflow until next millisecond available
  
  Returns:
    String UUID v7 like: '01934567-89ab-7def-8123-456789abcdef'"
  []
  (let [;; Atomic state update for uniqueness with overflow protection
        state (loop []
                (let [current-state @uuid-v7-state
                      {:keys [last-timestamp sequence-counter]} current-state
                      current-ts (System/currentTimeMillis)]
                  (cond
                    ;; New millisecond: reset sequence
                    (not= current-ts last-timestamp)
                    (if (compare-and-set! uuid-v7-state current-state
                                          {:last-timestamp current-ts
                                           :sequence-counter 0})
                      {:last-timestamp current-ts :sequence-counter 0}
                      (recur)) ; CAS failed, retry

                    ;; Same millisecond but sequence overflow: wait for next millisecond
                    (>= sequence-counter 67108864) ; 2^26
                    (do
                      ;; Busy wait for next millisecond (typically 0-1ms)
                      (while (= (System/currentTimeMillis) current-ts)
                        (Thread/sleep 0 1000)) ; Sleep 1 microsecond to avoid busy loop
                      (recur)) ; Try again with new timestamp

                    ;; Same millisecond: increment sequence
                    :else
                    (if (compare-and-set! uuid-v7-state current-state
                                          {:last-timestamp current-ts
                                           :sequence-counter (inc sequence-counter)})
                      {:last-timestamp current-ts :sequence-counter (inc sequence-counter)}
                      (recur))))) ; CAS failed, retry

        unix-ts-ms (:last-timestamp state)
        sequence (:sequence-counter state)

        ;; Generate random data for remaining bits
        secure-random (java.security.SecureRandom.)
        random-bytes (byte-array 8)
        _ (.nextBytes secure-random random-bytes)

        ;; Extract timestamp components (48 bits total)
        timestamp-high (bit-and (unsigned-bit-shift-right unix-ts-ms 16) 0xFFFFFFFF) ; bits 0-31
        timestamp-low (bit-and unix-ts-ms 0xFFFF) ; bits 32-47

        ;; Use full 26-bit sequence counter: 12 bits (time_hi) + 14 bits (clock_seq)
        ;; No random fallback - overflow protection handled by waiting for next millisecond
        sequence-26bit sequence

        ;; Split 26-bit sequence into 12-bit and 14-bit parts
        sequence-12bit (bit-and (unsigned-bit-shift-right sequence-26bit 14) 0x0FFF) ; Upper 12 bits
        sequence-14bit (bit-and sequence-26bit 0x3FFF) ; Lower 14 bits

        ;; Set version 7 in bits 48-51, sequence upper 12 bits in bits 52-63  
        time-hi-and-version (bit-or 0x7000 sequence-12bit)

        ;; Set variant bits to '10' (bits 64-65), sequence lower 14 bits in bits 66-79
        clock-seq-and-variant (bit-or 0x8000 sequence-14bit)

        ;; Minimal randomness for remaining 48 bits (bits 80-127)
        ;; Only use 4 bytes of random data since sequence handles uniqueness
        rand-b-low (reduce
                    (fn [acc i]
                      (bit-or (bit-shift-left acc 8)
                              (bit-and (aget random-bytes (+ i 4)) 0xFF)))
                    0
                    (range 4)) ; Use bytes 4-7 for 32 bits, pad with zeros for remaining 16 bits

        ;; Format as standard UUID string
        uuid-str (format "%08x-%04x-%04x-%04x-%012x"
                         timestamp-high
                         timestamp-low
                         time-hi-and-version
                         clock-seq-and-variant
                         rand-b-low)]
    uuid-str))

(defn uuid-v7-with-tag
  "Generate UUID v7 with optional tag suffix for operation identification.
  
  Args:
    tag - String tag to append (default: 'msg')
  
  Returns:
    UUID v7 + tag like: '01934567-89ab-7def-8123-456789abcdef-eval'
    
  Benefits:
    - RFC 9562 compliant UUID v7 base
    - Natural temporal/lexicographic ordering 
    - Tag suffix for operation identification
    - Sortable by creation time"
  [& {:keys [tag] :or {tag "msg"}}]
  (str (uuid-v7) "-" tag))

(defn extract-timestamp-ms
  "Extract Unix timestamp in milliseconds from UUID v7.
  
  Args:
    uuid-v7-str - UUID v7 string (with or without tag suffix)
  
  Returns:
    Unix timestamp in milliseconds, or nil if not a valid UUID v7"
  [uuid-v7-str]
  (let [;; Handle both plain UUID and UUID with tag suffix
        uuid-only (if (re-find #"-[a-zA-Z]" uuid-v7-str)
                    ;; Has tag suffix, extract UUID part
                    (str/join "-" (take 5 (str/split uuid-v7-str #"-")))
                    ;; No tag suffix, use as-is
                    uuid-v7-str)]
    (when (re-matches #"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}" uuid-only)
      (let [parts (str/split uuid-only #"-")
            time-high (Long/parseLong (nth parts 0) 16)
            time-low (Long/parseLong (nth parts 1) 16)]
        (bit-or (bit-shift-left time-high 16) time-low)))))

(defn uuid-v7-string
  "Generate RFC 9562 compliant UUID v7 as string - alias for uuid-v7 for compatibility.
  
  Returns:
    String UUID v7 like: '01934567-89ab-7def-8123-456789abcdef'"
  []
  (uuid-v7))

(defn validate-uuid-v7
  "Validate that a string is a proper UUID v7 format.
  
  Args:
    uuid-str - String to validate
  
  Returns:
    Boolean indicating if the string is a valid UUID v7 format"
  [uuid-str]
  (let [uuid-part (first (str/split uuid-str #"-" 6))] ; Handle tag suffix
    (if uuid-part
      (let [cleaned-uuid (str/join "-" (take 5 (str/split uuid-part #"-")))]
        ;; Check format: version 7, variant 10
        (boolean (re-matches #"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}" cleaned-uuid)))
      false)))