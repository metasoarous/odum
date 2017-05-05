(ns odum.db
  (:require [datascript.core :as d]
            [clojure.pprint :as pp]))

(def schema
  {:odum.flow/from
   {:db/type :db.type/ref}
   :odum.flow/to
   {:db/type :db.type/ref}
   :person/parent
   {:db/type :db.type/ref}})

(pp/pprint schema)


(def predator-prey-model
  (let [source-id (d/tempid :db/user)
        prey-id (d/tempid :db/user)
        pred-id (d/tempid :db/user)
        flow-id (d/tempid :db/user)
        pred-flow-id (d/tempid :db/user)
        prey-flow-id (d/tempid :db/user)]
    ;; Our nodes
    [{:db/id source-id
      :odum.node/name "Sun"
      :e/type :odum/node
      :odum.node/energy 1000}
     {:db/id prey-id
      :odum.node/name "Prey"
      :e/type :odum/node
      :odum.node/energy 10}
     {:db/id pred-id
      :odum.node/name "Predator"
      :e/type :odum/node
      :odum.node/energy 5}
     ;; Flows
     {:db/id prey-flow-id
      :db/doc "Prey feeding off sun"
      :e/type :odum/flow
      :odum.flow/from source-id
      :odum.flow/to prey-id
      :odum.flow/rate 0.06}
     {:db/id pred-flow-id
      :db/doc "Prededator feeding off prey"
      :e/type :odum/flow
      :odum.flow/from prey-id
      :odum.flow/to pred-id
      :odum.flow/rate 0.03}]))

(def conn (d/create-conn schema)) 

(d/transact conn predator-prey-model)


(def flows
  (d/q
    '[:find [(pull ?f [*]) ...]
      :in $ %
      :where [?f :e/type :odum/flow]]
    @conn))
(pp/pprint flows)

(def nodes
  (into {}
    (d/q
      '[:find ?e (pull ?e [*])
        :in $ %
        :where [?e :e/type :odum/node]]
      @conn)))
(pp/pprint nodes)


(defn odum-step
  [flows nodes]
  ;; Go through all of the flows
  (let [flows'
        (map
          (fn [{:as flow :keys [odum.flow/from odum.flow/to]}]
            (assoc
              flow
              :odum.flow/step-diff
              (* (:odum.flow/rate flow)
                 (get-in nodes [(:db/id from) :odum.node/energy])
                 (get-in nodes [(:db/id to) :odum.node/energy]))))
          flows)]
    ;; compute the amount of flow through the flow based on current values
    ;; update the node energies
    (reduce
      (fn [nodes {:as flow :keys [odum.flow/from odum.flow/to odum.flow/step-diff]}]
        (-> nodes
          (update-in [(:db/id from) :odum.node/energy] - step-diff)
          (update-in [(:db/id to) :odum.node/energy] + step-diff)))
      nodes
      flows')))

(pp/pprint
  (take 10
    (map
      (fn [node]
        [(:db/id)])
      (iterate (partial odum-step flows) nodes))))


