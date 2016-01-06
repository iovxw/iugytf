(ns iugytf.core
  (:require [iugytf.tgapi :as tgapi])
  (:gen-class))

(defn updates-seq
  ([bot] (updates-seq bot 0))
  ([bot offset]
   (Thread/sleep 500)
   (let [updates (tgapi/get-updates bot offset)
         new-offset (-> updates (last) (get :update_id -1) (+ 1))]
     (lazy-cat updates (updates-seq bot new-offset)))))

(defn -main
  "Nothing ... yet."
  [& args]
  (let [bot (tgapi/new-bot "KEYKEYKEY")]
    (loop [updates (updates-seq bot)]
      (println (first updates))
      (when-let [query ((first updates) :inline_query)]
        (tgapi/answer-inline-query bot (query :id)
                                   [{"type" "article"
                                     "id" "12345"
                                     "title" "o(*￣3￣)o"
                                     "message_text" "o(*￣3￣)o"}]))
      (recur (rest updates)))))
