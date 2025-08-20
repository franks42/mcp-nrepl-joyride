(ns nrepl-mcp-server.mcp-server.tools.load-file-shared
  "Shared utilities for load-file tool implementations"
  (:require [cheshire.core :as json]
            [clojure.string :as str])
  (:import [java.io File]))

;; =============================================================================
;; File Path Utilities
;; =============================================================================

(defn validate-file-path
  "Validate that file path is provided and file exists.
   Throws exception with descriptive message if validation fails."
  [file-path]
  (when (or (nil? file-path) (empty? file-path))
    (throw (ex-info "file-path parameter is required"
                    {:status :validation-error
                     :hint "Provide the path to the Clojure file to load"
                     :example "Use: {\"file-path\": \"/absolute/path/to/file.clj\"}"})))

  (let [file (File. file-path)]
    (when-not (.exists file)
      (throw (ex-info "File not found"
                      {:status :file-not-found
                       :file-path file-path
                       :message (str "File does not exist: " file-path)})))

    (when-not (.isFile file)
      (throw (ex-info "Path is not a file"
                      {:status :invalid-path
                       :file-path file-path
                       :message (str "Path exists but is not a file: " file-path)})))

    (when-not (.canRead file)
      (throw (ex-info "File is not readable"
                      {:status :permission-error
                       :file-path file-path
                       :message (str "Cannot read file: " file-path)})))))

(defn escape-file-path
  "Escape file path for safe inclusion in Clojure string literal.
   Used when constructing code strings for nREPL evaluation."
  [path]
  (-> path
      (str/replace "\\" "\\\\")  ; Escape backslashes first
      (str/replace "\"" "\\\""))) ; Then escape quotes

;; =============================================================================
;; Parameter Validation
;; =============================================================================

(defn validate-parameters
  "Validate that required parameters are present.
   Returns validated parameters map.
   Throws exception if required parameters are missing."
  [params required-keys]
  (doseq [key required-keys]
    (when (or (nil? (get params (keyword key)))
              (and (string? (get params (keyword key)))
                   (empty? (get params (keyword key)))))
      (throw (ex-info (str key " parameter is required")
                      {:status :validation-error
                       :missing-parameter key
                       :provided-parameters (keys params)}))))
  params)

;; =============================================================================
;; Response Formatting
;; =============================================================================

(defn format-load-file-response
  "Format a successful load-file response with consistent structure.
   Adapts different result formats to unified JSON structure."
  [result operation file-path]
  (cond
    ;; Handle nrepl-eval delegation response format
    (and (map? result) (:content result))
    (let [response-text (-> result :content first :text)
          response-data (json/parse-string response-text true)]
      {:content [{:type "text"
                  :text (json/generate-string
                         (assoc response-data
                                :operation operation
                                :file-path file-path)
                         {:pretty true})}]})

    ;; Handle direct execution result format  
    (map? result)
    {:content [{:type "text"
                :text (json/generate-string
                       (assoc result
                              :status "success"
                              :operation operation
                              :file-path file-path)
                       {:pretty true})}]}

    ;; Handle simple value result
    :else
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "success"
                        :operation operation
                        :file-path file-path
                        :value (pr-str result)}
                       {:pretty true})}]}))

(defn handle-load-file-error
  "Format a load-file error response with consistent structure."
  [error operation file-path]
  (let [error-data (ex-data error)
        error-status (:status error-data :error)]
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "error"
                        :operation operation
                        :file-path file-path
                        :error (.getMessage error)
                        :error-type error-status}
                       {:pretty true})}]
     :isError true}))