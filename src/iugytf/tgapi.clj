(ns iugytf.tgapi
  (:require [clojure.string :as string]
            [clj-http.client :as client]
            [clojure.data.json :as json])
  (:import (java.net URLEncoder)))

(defn new-bot [bot-key]
  (let [api (str "https://api.telegram.org/bot" bot-key)
        body (-> (str api "/getMe")
                 (slurp)
                 (json/read-str :key-fn keyword))]
    (when-not (body :ok)
      (throw (Exception. (body :description))))
    (assoc (body :result) :api api)))

(defn- url-encode [v]
  (-> (format "%s" v)
      (URLEncoder/encode "UTF-8")))

(defn- gen-query [kv]
  (->> kv
       (map #(str (url-encode (first %)) "=" (url-encode (second %))))
       (string/join "&")))

(defn- gen-url
  ([bot api]
   (str (bot :api) "/" api))
  ([bot api query]
   (str (bot :api) "/" api "?" (gen-query query))))

(defn get-updates
  ([bot] (get-updates bot 0))
  ([bot offset]
   (let [body (-> (gen-url bot "getUpdates" {"offset" offset})
                  (slurp)
                  (json/read-str :key-fn keyword))]
     (when-not (body :ok)
       (throw (Exception. (body :description))))
     (body :result))))

(defn- if-not-nil-add [k v m]
  (if v
    (assoc m k v)
    m))

(defn answer-inline-query [bot inline_query_id results &
                          {:keys [cache_time is_personal next_offset]}]
  (let [body (-> (gen-url bot "answerInlineQuery")
                 (client/post {:content-type :json
                               :body (->> {"inline_query_id" inline_query_id
                                           "results" results}
                                          (if-not-nil-add "cache_time" cache_time)
                                          (if-not-nil-add "is_personal" is_personal)
                                          (if-not-nil-add "next_offset" next_offset)
                                          (json/write-str))})
                 (get :body)
                 (json/read-str :key-fn keyword))]
    (when-not (body :ok)
      (throw (Exception. (body :description))))
    (body :result)))
