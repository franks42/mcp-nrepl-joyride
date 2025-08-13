(ns mcp-nrepl-proxy.tools.introspection
  "MCP tools for code introspection and documentation"
  (:require [clojure.string :as str]
            [mcp-nrepl-proxy.nrepl-client :as nrepl]
            [mcp-nrepl-proxy.utils :as utils]))

(defn tool-nrepl-doc
  "Get documentation for a Clojure symbol"
  [state ensure-nrepl-connection {:keys [symbol session ns]}]
  (let [conn-result (ensure-nrepl-connection)]
    (if (:success conn-result)
      (try
        (let [conn (:connection conn-result)
              result (nrepl/doc conn symbol :session session :ns ns)
              doc-text (:doc result)
              arglists (:arglists result)]
          (if (or doc-text arglists)
            {:content [{:type "text"
                        :text (str "📖 Documentation for " symbol "\n\n"
                                   (when arglists (str "Usage: " arglists "\n\n"))
                                   (or doc-text "No documentation available."))}]}
            {:content [{:type "text"
                        :text (str "❌ No documentation found for: " symbol)}]
             :isError true}))
        (catch Exception e
          (utils/log state :error "Doc lookup failed:" (.getMessage e))
          {:content [{:type "text"
                      :text (str "❌ Doc lookup failed: " (.getMessage e))}]
           :isError true}))
      {:content [{:type "text"
                  :text "❌ No nREPL connection available. Use nrepl-connect first."}]
       :isError true})))

(defn tool-nrepl-source
  "Get source code for a Clojure symbol"
  [state ensure-nrepl-connection {:keys [symbol session ns]}]
  (let [conn-result (ensure-nrepl-connection)]
    (if (:success conn-result)
      (try
        (let [conn (:connection conn-result)
              result (nrepl/source conn symbol :session session :ns ns)
              source-text (:source result)
              file (:file result)]
          (if source-text
            {:content [{:type "text"
                        :text (str "📄 Source code for " symbol
                                   (when file (str " from " file)) "\n\n"
                                   "```clojure\n" source-text "\n```")}]}
            {:content [{:type "text"
                        :text (str "❌ No source code found for: " symbol)}]
             :isError true}))
        (catch Exception e
          (utils/log state :error "Source lookup failed:" (.getMessage e))
          {:content [{:type "text"
                      :text (str "❌ Source lookup failed: " (.getMessage e))}]
           :isError true}))
      {:content [{:type "text"
                  :text "❌ No nREPL connection available. Use nrepl-connect first."}]
       :isError true})))

(defn tool-nrepl-complete
  "Get symbol completions for a prefix"
  [state ensure-nrepl-connection {:keys [prefix session ns context]}]
  (let [conn-result (ensure-nrepl-connection)]
    (if (:success conn-result)
      (try
        (let [conn (:connection conn-result)
              result (nrepl/complete conn prefix :session session :ns ns :context context)
              completions (:completions result)]
          (if (and completions (seq completions))
            {:content [{:type "text"
                        :text (str "🔍 Completions for \"" prefix "\":\n\n"
                                   (->> completions
                                        (take 20) ; Limit to first 20 results
                                        (map-indexed (fn [i completion]
                                                       (str (inc i) ". " completion)))
                                        (str/join "\n")))}]}
            {:content [{:type "text"
                        :text (str "❌ No completions found for: " prefix)}]
             :isError true}))
        (catch Exception e
          (utils/log state :error "Completion failed:" (.getMessage e))
          {:content [{:type "text"
                      :text (str "❌ Completion failed: " (.getMessage e))}]
           :isError true}))
      {:content [{:type "text"
                  :text "❌ No nREPL connection available. Use nrepl-connect first."}]
       :isError true})))

(defn tool-nrepl-apropos
  "Find symbols matching a pattern"
  [state ensure-nrepl-connection {:keys [query session ns search-ns privates? case-sensitive?]}]
  (let [conn-result (ensure-nrepl-connection)]
    (if (:success conn-result)
      (try
        (let [conn (:connection conn-result)
              result (nrepl/apropos conn query
                                    :session session
                                    :ns ns
                                    :search-ns search-ns
                                    :privates? privates?
                                    :case-sensitive? case-sensitive?)
              symbols (:apropos-matches result)]
          (if (and symbols (seq symbols))
            {:content [{:type "text"
                        :text (str "🔍 Symbols matching \"" query "\":\n\n"
                                   (->> symbols
                                        (take 30) ; Limit to first 30 results
                                        (map-indexed (fn [i sym]
                                                       (str (inc i) ". " sym)))
                                        (str/join "\n")))}]}
            {:content [{:type "text"
                        :text (str "❌ No symbols found matching: " query)}]
             :isError true}))
        (catch Exception e
          (utils/log state :error "Apropos search failed:" (.getMessage e))
          {:content [{:type "text"
                      :text (str "❌ Apropos search failed: " (.getMessage e))}]
           :isError true}))
      {:content [{:type "text"
                  :text "❌ No nREPL connection available. Use nrepl-connect first."}]
       :isError true})))