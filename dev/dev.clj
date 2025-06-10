(ns dev
  (:require [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [mount.core :as mount]))

(defn start
  "Starts all mount components"
  []
  (mount/start-with-args {:port 2053}))

(defn stop
  "Stops all mount components"
  []
  (mount/stop))

(defn restart
  "Stops all mount components, reloads modified source files, and restarts the system"
  []
  (stop)
  (refresh :after 'dev/start))

(defn reset
  "Stops all mount components, reloads all source files, and restarts the system"
  []
  (stop)
  (refresh-all :after 'dev/start))

;; Convenience aliases for the REPL
(def go start)
(def halt stop)
(def reset-all reset)
