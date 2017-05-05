(ns odum.run
  (:gen-class)
  (:require [taoensso.timbre :as log :include-macros true]
            [com.stuartsierra.component :as component]
            [odum.config :as config]
            [odum.system :refer [create-system]]))

(defn -main [& args]
  ;; XXX Eventually hook command line args into config-overwrides here so they flow through system. Room for
  ;; lib work...
  (component/start (create-system))
  (log/info "odum started"))

