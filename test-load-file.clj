;; Test file for nREPL load-file operation
(ns test-load-file
  (:require [clojure.string :as str]))

(defn greeting 
  "A simple greeting function"
  [name]
  (str "Hello, " name "!"))

(defn add-numbers
  "Add two numbers together"
  [a b]
  (+ a b))

(def test-data
  {:message "This file was loaded successfully"
   :timestamp (System/currentTimeMillis)})

;; This should be accessible after loading
(defn loaded-marker []
  :file-successfully-loaded)

(println "test-load-file.clj loaded successfully!")