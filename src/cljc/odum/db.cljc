(ns odum.db
  (:require [datascript.core :as d]
            [taoensso.timbre :as log :include-macros true]
            [clojure.pprint :as pp]
            [posh.core :as posh]
            [reagent.core :as r]))


;; Our schema

(def schema
  {:odum.flow/from
   {:db/type :db.type/ref}
   :odum.flow/to
   {:db/type :db.type/ref}
   :person/parent
   {:db/type :db.type/ref}})

;; A predator prey model

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
      :odum.node/x 50
      :odum.node/y 80
      :e/type :odum/node
      :odum.node/energy 1000}
     {:db/id prey-id
      :odum.node/name "Prey"
      :odum.node/x 150
      :odum.node/y 180
      :e/type :odum/node
      :odum.node/energy 10}
     {:db/id pred-id
      :odum.node/name "Predator"
      :odum.node/x 550
      :odum.node/y 280
      :e/type :odum/node
      :odum.node/energy 5}
     ;; Flows
     {:db/id prey-flow-id
      :db/doc "Prey feeding off sun"
      :e/type :odum/flow
      :odum.flow/from source-id
      :odum.flow/to prey-id
      :odum.flow/rate 0.006}
     {:db/id pred-flow-id
      :db/doc "Prededator feeding off prey"
      :e/type :odum/flow
      :odum.flow/from prey-id
      :odum.flow/to pred-id
      :odum.flow/rate 0.003}]))



(defonce conn
  (let [conn (d/create-conn schema)]
    (d/transact conn predator-prey-model)
    conn))

;(d/transact conn predator-prey-model)))


;; Now we define a function for computing a single iteration update on an odum graph.

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

;; Querying out all of our flows and nodes

(def flows
  (d/q
    '[:find [(pull ?f [*]) ...]
      :where [?f :e/type :odum/flow]]
    @conn))

(def nodes
  ;; We put the query results into a map keyed by the entity id, for easier access in odum-step
  (into {}
    (d/q
      '[:find ?e (pull ?e [*])
        :where [?e :e/type :odum/node]]
      @conn)))

;; Now we run the simulation using our odum step and our model data

(defn simulate
  [flows nodes]
  (map
    (fn [tick]
      (into
        {}
        (map
          (fn [[id {:as node :keys [odum.node/name odum.node/energy]}]]
            [name energy])
          tick)))
    ;; Note that we use partial here, because flows stays constant
    (iterate (partial odum-step flows) nodes)))

(comment
  (pp/pprint
    (take 10
      (simulate flows nodes))))
      ;; This map part just reformats the results to be easier to look at


