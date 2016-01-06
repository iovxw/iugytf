(defproject iugytf "0.1.0"
  :description "An Emoticon bot for Telegram, use inline mode"
  :url "http://iovxw.net"
  :license {:name "The MIT License (MIT)"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/data.json "0.2.6"]
                 [clj-http "2.0.0"]
                 [clj-yaml "0.4.0"]
                 [org.clojure/tools.logging "0.3.1"]]
  :main ^:skip-aot iugytf.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
