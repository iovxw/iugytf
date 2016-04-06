(ns iugytf.core
  (:require [iugytf.tgapi :as tgapi]
            [clj-yaml.core :as yaml]
            [clj-leveldb :as leveldb]
            [clojure.string :as string]
            [clojure.data.json :as json]
            [clojure.core.match :refer [match]]
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


(defn split-message [text max-len]
  (let [text-len (count text)]
    (loop [begin 0, offset 0, result []]
      (let [n (if-let [n (string/index-of text "\n" offset)] n text-len)
            line-len (- n offset)
            now-len (- n begin)] ; 当前所选定的长度
        (if (<= (- text-len begin) max-len) ; 剩下的长度小于最大长度
          (conj result (subs text begin text-len)) ; DONE
          (if (> line-len max-len) ; 单行大于最大长度，进行行内切分
            (let [split-len (- max-len (- offset begin))
                  split-point (+ offset split-len)]
              (recur split-point split-point
                     (conj result (subs text begin split-point))))
            (if (> now-len max-len)
              (recur offset (inc n) ; 这里的 (dec offset) 是为了去掉最后一个换行
                     (conj result (subs text begin (dec offset))))
              (recur begin (inc n) result))))))))

(defn send-message [bot chat-id text & opts]
  (if (<= (count text) 1024)
    (apply tgapi/send-message bot chat-id text opts)
    (let [messages (split-message text 1024)]
      (doseq [msg messages]
        (apply tgapi/send-message bot chat-id msg opts)))))

(defn prn-kaomoji-list [bot db user-id raw?]
  (send-message bot user-id
                (if raw?
                  (reduce #(format "%s\n%s" %1 (second %2))
                          "" (get-user-kaomoji db user-id))
                  (reduce #(format "%s\n%s. %s" %1 (first %2) (second %2))
                          "" (get-user-kaomoji db user-id)))))

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
        (prn u)
        (try
          (cond-let
           [query (u :inline_query)]
           (tgapi/answer-inline-query bot (query :id)
                                        ; TODO: 使用 offset 翻页
                                      (gen-answer (query :query)
                                                  (get-user-kaomoji db (get-in query [:from :id]))))

           [chosen (u :chosen_inline_result)]
           (update-kaomoji-count db (get-in chosen [:from :id]) (Integer. (chosen :result_id)))

           [message (u :message)]
           (when-let [text (message :text)]
             (when (= (get-in message [:chat :type]) "private") ; 只在私聊中可以使用命令
               (match (tgapi/parse-cmd bot text)
                      ["list" _] (prn-kaomoji-list bot db (get-in message [:chat :id]) false)
                      ["list_raw" _] (prn-kaomoji-list bot db (get-in message [:chat :id]) true)
                      :else (log/warnf "Unable to parse command: %s" text)))))
          (catch Exception e (log/error e ""))))
      (recur (rest updates)))))
