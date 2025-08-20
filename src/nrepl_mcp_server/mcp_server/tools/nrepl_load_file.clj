(ns nrepl-mcp-server.mcp-server.tools.nrepl-load-file
  "nREPL load-file tool - Execute Clojure's load-file function within nREPL runtime"
  (:require [nrepl-mcp-server.mcp-server.tools.nrepl-eval :as nrepl-eval]
            [clojure.string :as str]
            [cheshire.core :as json]))

(defn- escape-file-path
  "Escape file path for safe inclusion in Clojure string literal"
  [path]
  (-> path
      (str/replace "\\" "\\\\")  ; Escape backslashes first
      (str/replace "\"" "\\\""))) ; Then escape quotes

(defn handle
  "Execute Clojure's load-file function within nREPL runtime.
   
   IMPORTANT: This uses Clojure's built-in (load-file \"path\") function,
   NOT the nREPL protocol's :op \"load-file\" operation.
   
   Recommendation: Use absolute file paths as nREPL working directory may vary."
  [{:keys [file-path timeout connection] :or {timeout 30000}}]

  (cond
    ;; Validation: file-path is required
    (empty? file-path)
    {:content [{:type "text"
                :text (json/generate-string
                       {:status "error"
                        :operation "nrepl-load-file"
                        :error "file-path parameter is required"
                        :hint "Provide the path to the Clojure file to load"
                        :example "Use: {\"file-path\": \"/absolute/path/to/file.clj\"}"}
                       {:pretty true})}]
     :isError true}

    ;; Process the file loading
    :else
    (let [escaped-path (escape-file-path file-path)
          code (str "(load-file \"" escaped-path "\")")
          result (nrepl-eval/handle {:code code :timeout timeout :connection connection})]

      ;; Transform nrepl-eval response to nrepl-load-file format
      (if (:isError result)
        ;; Error case - update operation name and add file-path context
        (let [response-text (-> result :content first :text)
              response-data (json/parse-string response-text true)]
          {:content [{:type "text"
                      :text (json/generate-string
                             (assoc response-data
                                    :operation "nrepl-load-file"
                                    :file-path file-path)
                             {:pretty true})}]
           :isError true})

        ;; Success case - update operation name and add file-path context
        (let [response-text (-> result :content first :text)
              response-data (json/parse-string response-text true)]
          {:content [{:type "text"
                      :text (json/generate-string
                             (assoc response-data
                                    :operation "nrepl-load-file"
                                    :file-path file-path)
                             {:pretty true})}]})))))

(def tool-name "nrepl-load-file")

(def metadata
  {:description "Execute Clojure's load-file function within nREPL runtime with connection selection. Use absolute file paths as nREPL working directory may vary. (Not nREPL protocol operation)"
   :inputSchema {:type "object"
                 :properties {:file-path {:type "string"
                                          :description "Path to Clojure file to load (recommend absolute paths)"}
                              :connection {:type "string"
                                           :description "Connection identifier (nickname, connection-id, or host:port). Optional - uses single connection if not specified."}
                              :timeout {:type "integer"
                                        :description "Timeout in milliseconds (default: 30000)"
                                        :minimum 1000
                                        :maximum 300000}}
                 :required ["file-path"]}})