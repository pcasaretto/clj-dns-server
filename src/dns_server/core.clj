(ns dns-server.core
  (:require
   [dns-server.udp.socket :as socket]
   [dns-server.dns.message :as message]
   [mount.core :as mount :refer [defstate]]
   [clojure.core.async :as async :refer [<!]]
   [clojure.tools.cli :refer [parse-opts]])
  (:import (java.net DatagramPacket)))

(defn packet->packetmap [packet]
  {:address (.getAddress packet)
   :port (.getPort packet)
   :data (.getData packet)})

(defn packetmap->out-packet [m]
  (println (message/parse-header (:data m)))
  (DatagramPacket. (:data m) (alength (:data m)) (:address m) (:port m)))

(defn send-packet [socket]
  (fn [packet] (.send socket packet) :ok))

(defn process [socket]
  (comp
   (map packet->packetmap)
   (map packetmap->out-packet)
   (map (send-packet socket))))

(defn start-receiver [{:keys [port]}]
  (let [close-chan (async/chan)
        packet-chan (async/chan)
        socket (socket/listen packet-chan close-chan port)
        cb (async/chan)]
    (async/pipeline 1 cb (process socket) packet-chan)
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
