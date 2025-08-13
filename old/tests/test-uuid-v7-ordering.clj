#!/usr/bin/env bb

(require '[mcp-nrepl-proxy.uuid-v7 :as uuid])

(defn test-uuid-v7-temporal-ordering
  "Test UUID v7 temporal ordering guarantee by generating many UUIDs in succession.
  
  The test:
  1. Generate N UUIDs in rapid succession (insertion order)
  2. Sort them lexicographically 
  3. Verify sorted order == insertion order (temporal guarantee)
  
  This validates the core UUID v7 promise: lexicographic sorting preserves temporal order."
  [n]
  (println (str "🧪 Testing UUID v7 temporal ordering with " n " UUIDs"))
  
  ;; Generate UUIDs in rapid succession (insertion order)
  (println "⏱️  Generating UUIDs in rapid succession...")
  (let [start-time (System/currentTimeMillis)
        ordered-uuids (vec (repeatedly n #(uuid/uuid-v7)))
        generation-time (- (System/currentTimeMillis) start-time)]
    
    (println (str "✅ Generated " n " UUIDs in " generation-time "ms"))
    (println (str "📊 Rate: " (int (/ n (/ generation-time 1000.0))) " UUIDs/second"))
    
    ;; Sort UUIDs lexicographically 
    (println "🔤 Sorting UUIDs lexicographically...")
    (let [sorted-uuids (vec (sort ordered-uuids))]
      
      ;; The critical test: ordered == sorted?
      (let [temporal-ordering-preserved? (= ordered-uuids sorted-uuids)]
        (println (str "🎯 Temporal ordering preserved: " 
                     (if temporal-ordering-preserved? "✅ YES" "❌ NO")))
        
        ;; Additional analysis
        (when temporal-ordering-preserved?
          (println "📈 Perfect temporal ordering - UUID v7 guarantee verified!"))
        
        (when-not temporal-ordering-preserved?
          (println "❌ ORDERING VIOLATION DETECTED!")
          (let [violations (keep-indexed 
                           (fn [idx ordered-uuid]
                             (when (not= ordered-uuid (nth sorted-uuids idx))
                               {:position idx
                                :ordered ordered-uuid  
                                :sorted (nth sorted-uuids idx)}))
                           ordered-uuids)]
            (println (str "🚨 Found " (count violations) " ordering violations:"))
            (doseq [v (take 5 violations)]
              (println (str "  Position " (:position v) ": " 
                           (:ordered v) " -> " (:sorted v))))))
        
        ;; Timestamp analysis
        (println "\n📊 UUID Timestamp Analysis:")
        (let [timestamps (map uuid/extract-timestamp-ms ordered-uuids)
              unique-timestamps (set timestamps)
              timestamp-range (when (seq timestamps) 
                               (- (apply max timestamps) (apply min timestamps)))]
          (println (str "  Total timestamps: " (count timestamps)))
          (println (str "  Unique timestamps: " (count unique-timestamps)))  
          (println (str "  Timestamp range: " timestamp-range "ms"))
          (println (str "  UUIDs per millisecond: " 
                       (if (> (count unique-timestamps) 0)
                         (int (/ (count timestamps) (count unique-timestamps)))
                         0))))
        
        ;; Sample UUIDs
        (println "\n🔍 Sample UUIDs (first 5):")
        (doseq [[idx uuid] (map-indexed vector (take 5 ordered-uuids))]
          (println (str "  " idx ": " uuid 
                       " (ts: " (uuid/extract-timestamp-ms uuid) ")")))
        
        {:success temporal-ordering-preserved?
         :total-uuids n
         :generation-time-ms generation-time
         :rate-per-second (int (/ n (/ generation-time 1000.0)))
         :unique-timestamps (count (set (map uuid/extract-timestamp-ms ordered-uuids)))
         :violations (if temporal-ordering-preserved? 0 
                        (count (filter false? (map = ordered-uuids sorted-uuids))))}))))

(defn test-uuid-v7-with-tags
  "Test UUID v7 with operation tags to verify tag suffix doesn't break ordering."
  [n]
  (println (str "\n🏷️  Testing UUID v7 with tags (" n " UUIDs)"))
  
  (let [operations ["eval" "clone" "close" "describe" "load-file" "info"]
        ordered-tagged-uuids (vec (repeatedly n 
                                             #(uuid/uuid-v7-with-tag 
                                               :tag (rand-nth operations))))
        sorted-tagged-uuids (vec (sort ordered-tagged-uuids))
        temporal-ordering-preserved? (= ordered-tagged-uuids sorted-tagged-uuids)]
    
    (println (str "🎯 Tagged UUID temporal ordering: " 
                 (if temporal-ordering-preserved? "✅ YES" "❌ NO")))
    
    ;; Sample tagged UUIDs
    (println "🔍 Sample tagged UUIDs (first 3):")
    (doseq [[idx uuid] (map-indexed vector (take 3 ordered-tagged-uuids))]
      (println (str "  " idx ": " uuid)))
    
    {:success temporal-ordering-preserved?
     :total-tagged-uuids n}))

(defn stress-test-uuid-v7
  "Stress test UUID v7 generation with multiple batch sizes."
  []
  (println "🚀 UUID v7 Stress Test - Multiple Batch Sizes\n")
  
  (let [batch-sizes [100 1000 5000 10000]
        results (mapv 
                 (fn [size]
                   (println (str "━━━ Batch Size: " size " ━━━"))
                   (let [result (test-uuid-v7-temporal-ordering size)]
                     (test-uuid-v7-with-tags (min 100 size))
                     (println)
                     result))
                 batch-sizes)]
    
    (println "📈 STRESS TEST SUMMARY")
    (println "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    (doseq [[size result] (map vector batch-sizes results)]
      (println (str "📦 " size " UUIDs: "
                   (if (:success result) "✅ PASS" "❌ FAIL")
                   " (" (:rate-per-second result) " UUID/s, "
                   (:unique-timestamps result) " unique timestamps, "
                   (:violations result) " violations)")))
    
    (let [all-passed? (every? :success results)]
      (println (str "\n🎯 Overall Result: " 
                   (if all-passed? "✅ ALL TESTS PASSED" "❌ SOME TESTS FAILED")))
      all-passed?)))

;; Run the stress test
(println "🧪 UUID v7 Temporal Ordering Validation Test")
(println "=" (apply str (repeat 50 "=")))
(println "Testing RFC 9562 UUID v7 temporal ordering guarantee")
(println "Key Validation: insertion order == lexicographic sort order\n")

(let [success? (stress-test-uuid-v7)]
  (System/exit (if success? 0 1)))