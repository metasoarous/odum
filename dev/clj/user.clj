(ns user
  (:require
    [clojure.java.javadoc :refer [javadoc]]
    [clojure.pprint :refer [pprint]]
    [clojure.reflect :refer [reflect]]
    [clojure.repl :refer [apropos dir doc find-doc pst source]]
    [com.stuartsierra.component :as component]
    [clojure.tools.namespace.repl :refer [refresh refresh-all]]
    [odum.system :as system]
    [datascript.core :as d]
    [clojure.pprint :as pp]
    [taoensso.timbre :as log :include-macros true]
    [odum.figwheel-server :as fserver]))


;; This is some system setup stuff; ignore for now.... see below for DataScript examples

(def system nil)

(defn init
  ([config-overrides]
   (alter-var-root #'system (fn [_] (assoc (system/create-system config-overrides)
                                      :http-server (component/using (fserver/new-figwheel-server) [:datomic :config :ring-handler])))))
  ([] (init {})))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system
    (fn [s] (when s (component/stop s)))))

(defn run
  ([config-overrides]
   (init config-overrides)
   (start))
  ([] (run {})))

(defn reset
  ;; XXX Hmm... not sure how to get config-overrides with reset because of refresh :after needing a 0-arity fn
  []
  (stop)
  (refresh :after 'user/run))

(defn browser-repl []
  (if system
    (fserver/browser-repl (:http-server system))
    (log/error "The system must be running to open a browser-repl. Use (run) first.")))

(comment
  ;; Run a customized system XXX
  (try
    (run {:datomic {:seed-data "config/local/seed-data.edn"}})
    (catch Exception e (.printStackTrace e)))
  (stop)
  (reset))

;; You can use this to add dependencies without rebooting your repl.
(defmacro add-dependency [dependency]
  "A macro for adding a dependency via Pomegranate.
   Usage: (add-dependency [cheshire \"5.7.0\"])
   Remember that you still need to (require) or (use) the new namespaces."
  `(do (~'require '[cemerick.pomegranate])
       (~'cemerick.pomegranate/add-dependencies :coordinates '[~dependency]
         :repositories (~'merge cemerick.pomegranate.aether/maven-central
                         {"clojars" "http://clojars.org/repo"}))))




;; NOW FOR SOME datascripts!
;; =========================

;; This stuff was some learning sketching on how Datalog works before we started focusing on actually building
;; the app

;; First we start off with a schema, which describes our reference types

(comment

  (def schema
    {:odum.flow/from
     {:db/type :db.type/ref}
     :odum.flow/to
     {:db/type :db.type/ref}
     :person/parent
     {:db/type :db.type/ref}})

  ;; Print the schema

  (pp/pprint schema)

  ;; Now we can actually create our database

  (def conn (d/create-conn schema)) 


  ;; Now we're going to create a predator prey model using Clojure maps, which we'll transact into our
  ;; DataScript database and RDF triples.

  (def predator-prey-model
    (let [source-id (d/tempid :db/user)
          prey-id (d/tempid :db/user)
          pred-id (d/tempid :db/user)
          flow-id (d/tempid :db/user)
          pred-flow-id (d/tempid :db/user)
          prey-flow-id (d/tempid :db/user)]
      ;; Our nodes
      [{:db/id source-id
        :db/doc "The sun... probably"
        :e/type :odum/node}
       {:db/id prey-id
        :db/doc "Some prey"
        :e/type :odum/node}
       {:db/id pred-id
        :db/doc "Predator"
        :e/type :odum/node}
       ;; Flows
       {:db/id prey-flow-id
        :db/doc "Prey feeding off sun"
        :e/type :odum/flow
        :odum.flow/from source-id
        :odum.flow/to prey-id}
       {:db/id pred-flow-id
        :db/doc "Prey feeding off sun"
        :e/type :odum/flow
        :odum.flow/from prey-id
        :odum.flow/to pred-id}]))

  (pp/pprint predator-prey-model)

  ;; Transact our predator prey model

  (d/transact conn predator-prey-model)


  ;; Pull queries; These let us pull out information about a particular entity, by id.

  (d/pull @conn '[*] 1)

  (pp/pprint
    (d/pull @conn '[*] 4))


  ;; Datalog queries with q; These let you query across relationships and do SQL style queries, but more
  ;; powerful, easier to write, and with recursion.

  (pp/pprint
    (d/q
      '[:find [?e ...]
        :where [?e :e/type :odum/flow]
               [?e :odum.flow/from ?p]
               [?p :db/doc "Some prey"]]
      @conn))


  ;; Here's some person and ancestor data we can play with

  (def ancestral-data
    [{:db/id -999
      :person/name "Bob Jones"}
     {:db/id -998
      :person/name "Sally Mae"
      :person/parent -999}
     {:db/id -997
      :person/name "Freddie Mac"
      :person/parent -998}
     {:db/id -996
      :person/name "Jill Stein"
      :person/parent -998}
     {:db/id -995
      :person/name "Bo Willson"
      :person/parent -996}])


  (d/transact conn ancestral-data)

  ;; let's see all parent relationships by name

  (pp/pprint
    (d/q
      '[:find ?n1 ?n2
        :in $ %
        :where [?e1 :person/parent ?e2]
               [?e1 :person/name ?n1]
               [?e2 :person/name ?n2]]
      @conn))

  ;; Now we're going to define some rules; These let us describe "or", and do recursion.

  (def rules
    '[;; Ancestor
      [(ancestor ?e1 ?e2)
       [?e1 :person/parent ?e2]]
       ;; Or an ancestor of a parent
      [(ancestor ?e1 ?e2)
       [?e1 :person/parent ?p]
       [ancestor ?p ?e2]]
      ;; Odum flows
      [(flows-to ?e1 ?e2)
       [?f :odum.flow/from ?e1]
       [?f :odum.flow/to ?e2]]
      [(flows-to ?e1 ?e2)
       [?f :odum.flow/from ?e1]
       [?f :odum.flow/to ?n]
       (flows-to ?n ?e2)]])


  ;; Testing our recursive ancestor rule

  (pp/pprint
    (d/q
      '[:find ?n1 ?n2
        :in $ %
        :where (ancestor ?e1 ?e2)
               [?e1 :person/name ?n1]
               [?e2 :person/name ?n2]]
      @conn
      rules))
      ;; Either a parent

  ;; Testing a recursive rule for our odum flows

  (pp/pprint
    (d/q
      '[:find ?n1 ?n2
        :in $ %
        :where (flows-to ?e1 ?e2)
               [?e1 :db/doc ?n1]
               [?e2 :db/doc ?n2]]
      @conn
      rules))


  (pp/pprint
    (d/q
      '[:find ?n1 ?n2
        :in $ % ?from-what
        :where (flows-to ?from-what ?e2)
               [?from-what :db/doc ?n1]
               [?e2 :db/doc ?n2]]
      @conn
      rules
      1))


  ;; Simple example of how iterate works, and how we can use that for computing our simulation.


  (defn grow
    [x]
    (update x :age inc))

  (grow {:name "calvin" :age 2}) 

  (take
    10
    (iterate grow {:age 3 :name "Jo"}))


  (pp/pprint
    (d/q
      '[:find ?n1 ?n2
        :in $ % ?from-what
        :where (flows-to ?from-what ?e2)
               [?from-what :db/doc ?n1]
               [?e2 :db/doc ?n2]]
      @conn
      rules
      1))


  (defn odum-tick
    [{:as state :keys [nodes flows]}]
    (update)))
    


