(ns dns-server.core
  (:require
   [dns-server.udp.socket :as socket]
   [dns-server.dns.message :as message]
   [mount.core :as mount :refer [defstate]]
   [clojure.core.async :as async :refer [<!]]
   [clojure.tools.cli :refer [parse-opts]])
  (:import (java.net DatagramPacket)))

(def static-header #:dns-server.dns.message {:id 1234 :qr 1 :opcode 0 :aa 0 :tc 0 :rd 0 ;
                                             :ra 0 :z 0 :rcode 0 :qdcount 0 :ancount 0
                                             :nscount 0 :arcount 0})

(defn receive-packet [socket]
  (fn [packet]
    (let [m {:address (.getAddress packet)
              :port (.getPort packet)
              :data (.getData packet)}
          header (message/parse-header (:data m))
          data (message/serialize-header static-header)
          packet (DatagramPacket. data (alength data) (:address m) (:port m))]
      (.send socket packet))
    :ok))

(defn start-receiver [{:keys [port]}]
  (let [close-chan (async/chan)
        packet-chan (async/chan)
        socket (socket/listen packet-chan close-chan port)
        cb (async/chan)]
    (async/pipeline 1 cb (map (receive-packet socket)) packet-chan)
    (async/go-loop [res (<! cb)] (println res) (recur (<! cb)))
    close-chan))

(defstate dns-server
  :start (start-receiver (mount/args))
  :stop (async/close! dns-server))

(defn parse-args [args]
  (let [opts [["-p" "--port PORT" "Port number"
               :default 2053
               :parse-fn #(Integer/parseInt %)
               :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]]]

    (-> (parse-opts args opts)
        :options)))

(defn -main [& args]
  (mount/start-with-args (parse-args args))
  (println "Application running. Press Ctrl+C to stop.")
  (Thread. (loop [] (recur)))
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. #(mount/stop))))
