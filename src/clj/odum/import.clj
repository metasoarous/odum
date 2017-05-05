(ns odum.import
  (:require [taoensso.timbre :as log :include-macros true]
            [datomic.api :as d]
            [odum.ws :as ws]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [odum.app :as app]))

(defrecord Importer [config datomic]
  component/Lifecycle
  (start [component]
    (log/info "Importing data")
    (let [data (-> "resources/test-data.edn" slurp read-string)]
      @(d/transact (:conn datomic) data)))
  (stop [component]
       component))


(defn new-importer []
  (map->Importer {}))

