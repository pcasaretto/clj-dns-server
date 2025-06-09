(ns dns-server.core
  (:require
   [dns-server.udp.socket :as socket]
   [mount.core :as mount :refer [defstate]]
   [clojure.core.async :as async :refer [<!! >!! <! >!]])
  (:import (java.net DatagramPacket DatagramSocket)))

(defn start-receiver []
  (let [close-chan (async/chan)
        packet-chan (async/chan)
        port 2032]
    (socket/listen packet-chan close-chan port)
    (async/go-loop []
      (let [packet (<! packet-chan)]
        (println (String. (.getData packet) 0 (.getLength packet)))
        (recur)))
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
