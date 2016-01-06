(ns iugytf.core
  (:require [iugytf.tgapi :as tgapi]
            [clj-yaml.core :as yaml]
            [clojure.tools.logging :as log])
  (:gen-class))

(defn updates-seq
  ([bot] (updates-seq bot 0))
  ([bot offset]
   (Thread/sleep 500)
   (let [updates (try (tgapi/get-updates bot offset)
                      (catch Exception e (log/error e "") []))
         new-offset (-> updates (last) (get :update_id -1) (+ 1))]
     (lazy-cat updates (updates-seq bot new-offset)))))

(defn gen-answer [query results]
  (map-indexed #(array-map "type" "article"
                           "id" (format "%s" %1)
                           "title" %2
                           "description" (format "预览: %s %s" %2 query)
                           "message_text" (format "%s %s" %2 query))
               results))

(defn -main [config-file]
  (let [config (->> (if config-file
                      config-file
                      "config.yaml")
                    (slurp)
                    (yaml/parse-string))
        elist (config :emoticons)
        bot (tgapi/new-bot (config :key))]
    (loop [updates (updates-seq bot)]
      (println (first updates))
      (when-let [query ((first updates) :inline_query)]
        (try
          (tgapi/answer-inline-query bot (query :id)
                                     ; TODO: 使用 offset 翻页
                                     (gen-answer (query :query) elist))
          (catch Exception e (log/error e "")))) ; TODO
      (recur (rest updates)))))
