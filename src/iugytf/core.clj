(ns iugytf.core
  (:require [iugytf.tgapi :as tgapi]
            [clj-yaml.core :as yaml]
            [clj-leveldb :as leveldb]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log])
  (:gen-class))

(def default-kaomoji (atom []))

;; 无符号大端字节序解码
(defn decode-id [bytes]
  (let [length (count bytes)]
    (reduce + (map-indexed #(bit-shift-left (bit-and %2 0x000000FF)
                                            (* (- length 1 %1) 8))
                           bytes))))
;; 大端字节序编码
(defn encode-id [int]
  (loop [int int, result '()]
    (let [r (mod int 256)
          q (quot int 256)]
      (if (not= q 0)
        (recur q (cons r result))
        (byte-array (cons r result))))))

(defn updates-seq
  ([bot] (updates-seq bot 0))
  ([bot offset]
   (Thread/sleep 500)
   (let [updates (try (tgapi/get-updates bot offset)
                      (catch Exception e
                        (log/warnf "Pull updates fail: %s" (.getMessage e)) []))
         new-offset (if (not= (count updates) 0)
                      (-> updates (last) (get :update_id) (+ 1))
                      offset)] ; updates 数量为 0，可能获取失败，使用旧的 offset
     (lazy-cat updates (updates-seq bot new-offset)))))

(defn gen-answer [query results]
  (map #(array-map "type" "article"
                   "id" (str (first %))
                   "title" (second %)
                   "description" (format "预览: %s %s" (second %) query)
                   "message_text" (format "%s %s" (second %) query))
       results))

(defn get-user-kaomoji [db user-id]
  (if-let [raw-data (leveldb/get db (encode-id user-id))]
    (let [json-str (apply str (map char raw-data))
          data (json/read-str json-str)]
      data)
    @default-kaomoji))

(defn some-index [f coll]
  (some #(when (f (second %))
           (first %))
        (map-indexed vector coll)))

(defn update-kaomoji-count [db user-id kaomoji-id]
  (let [data (get-user-kaomoji db user-id)
        index (some-index #(= (first %) kaomoji-id) data)
        data (update-in data [index 2] dec)
        data (sort-by last data)
        json-str (json/write-str data)]
    (leveldb/put db (encode-id user-id) json-str)))

(defmacro cond-let [& clauses]
  (when clauses
    (if (= (first clauses) :else)
      (second clauses)
      `(let ~(first clauses)
         (if ~(first (first clauses))
           ~(second clauses)
           ~(cons 'cond-let (next (next clauses))))))))

(defn -main [& vars]
  (let [config (->> (if-let [config-file (first vars)]
                      config-file
                      "config.yaml")
                    (slurp)
                    (yaml/parse-string))
        bot (tgapi/new-bot (config :key))
        db (leveldb/create-db (config :database) {})]
    (reset! default-kaomoji (vec (map-indexed (fn [i m] [i m 0]) (config :kaomoji))))
    (loop [updates (updates-seq bot)]
      (let [u (first updates)]
        (println u)
        (cond-let
         [query (u :inline_query)]
         (try
           (tgapi/answer-inline-query bot (query :id)
                                        ; TODO: 使用 offset 翻页
                                      (gen-answer (query :query)
                                                  (get-user-kaomoji db (-> query :from :id))))
           (catch Exception e (log/error e "")))

         [chosen (u :chosen_inline_result)]
         (update-kaomoji-count db (-> chosen :from :id) (Integer. (chosen :result_id)))))
      (recur (rest updates)))))
