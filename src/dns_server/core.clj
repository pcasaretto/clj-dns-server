(ns dns-server.core
  (:require
   [dns-server.udp.socket :as socket]
   [mount.core :as mount :refer [defstate]]
   [clojure.core.async :as async :refer [<!! >!! <! >!]])
  (:import (java.net DatagramPacket DatagramSocket)))


(defn packet->map [packet]
  {:address (.getAddress packet)
   :port (.getPort packet)
   :data (.getData packet)})

(defn packetmap->out-packet [m]
  (DatagramPacket. (:data m) (alength (:data m)) (:address m) (:port m)))

(defn send-packet [socket]
  (fn [packet] (.send socket packet) :ok))

(defn process [socket]
  (comp
   (map packet->map)
   (map packetmap->out-packet)
   (map (send-packet socket))))


(defn start-receiver []
  (let [close-chan (async/chan)
        packet-chan (async/chan)
        port 2053
        socket (socket/listen packet-chan close-chan port)
        cb (async/chan)]
    (async/pipeline 1 cb (process socket) packet-chan)
    (async/go-loop [res (<! cb)] (println res) (recur (<! cb)))
    close-chan))

(defstate dns-server
  :start {:chan (start-receiver)}
  :stop (async/close! (:chan dns-server)))

(defn -main [& args]
  (mount/start)
  (println "Application running. Press Ctrl+C to stop.")
  (Thread. (loop [] (recur)))
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. #(mount/stop))))
