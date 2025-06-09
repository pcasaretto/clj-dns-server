(ns dns-server.udp.socket
  (:import (java.net DatagramSocket DatagramPacket))
  (:require
    [clojure.core.async :as async :refer [<! >!]]))

(defn listen [receive-chan close-chan port]
  (let [socket (DatagramSocket. port)]
    (async/go-loop []
      (let [buffer (byte-array 1024)
            packet (DatagramPacket. buffer (count buffer))]
        (try
          (.receive socket packet)
          (>! receive-chan packet)
          (catch java.net.SocketException _ ""))
        (recur)))
    (async/go
      (<! close-chan)
      (.close socket))
   socket))
