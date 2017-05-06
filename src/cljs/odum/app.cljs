(ns odum.app
  (:require-macros [cljs.core.async.macros :refer [go-loop]]
                   [reagent.ratom :refer [reaction]])
  (:require [odum.views :as views]
            [odum.events :as events]
            [odum.db :as db]
            [odum.drag :as drag]
            [goog.events :as gevents]
            [dat.reactor.dispatcher :as dispatcher]
            [datascript.core :as d]
            [taoensso.timbre :as log :include-macros true]
            [re-com.core :as re-com]
            [reagent.core :as r]
            [posh.reagent :as posh])
 (:import [goog.events EventType]))  


;; Set up posh on our application db connection.
;; This sets up listeners so that we can use the `posh/q` and `posh/pull` functions below.

(defonce poshed?
  (posh/posh! db/conn))


;; And what that lets us do is define "materialized views", or "live queries", that you can think of like
;; excel spreadsheet cells, which update when the inputs change.
;; But here, everything is based on queries off of the database.
;; So... update database, these queries update automatically.

(def flows
  (posh/q
    '[:find [?f ...]
      :where [?f :e/type :odum/flow]]
    db/conn))

(def nodes
  (posh/q
    '[:find [?e ...]
      :where [?e :e/type :odum/node]]
    db/conn))


;; Now we're going to start defining our view functions
;; We use the Reagent library, which let's us write functions that map domain data to HTML
;; However, this mapping doesn't happen directly, but via an intermediate data representation of HTML called
;; hiccup.
;; Hiccup looks like this: `[:div [:h1 "A header"] [:p "A paragraph"]]`
;; With reagent, we don't just have to use keywords for our tags (div, p, etc), we can use other view
;; functions.
;; This sets things up so that updates to the DOM are minimal and fast.

;; The first thing we're going to do is set up a movable/draggable point representation of an odum node.

(def point-defaults
  {:stroke "black"
   :stroke-width 2
   :fill "grey"
   :r 10})

(defn point [{:keys [on-drag]} [x y]]
  [:circle 
   (merge point-defaults
          {:on-mouse-down #(drag/dragging on-drag)
           :cx x
           :cy y})])

;; Now for our move-node handler which will look at where we drag the node, and update the database
;; accordingly

(defn move-node [svg-root node-id]
  (fn [x y]
    (let [bcr (drag/get-bcr svg-root)]
      (let [new-x (- x (.-left bcr))
            new-y (- y (.-top bcr))]
        (d/transact! db/conn [{:db/id node-id :odum.node/x new-x :odum.node/y new-y}])))))

;; A component for editing the attributes of a node

(defn edit-node-view
  [node-id]
  (let [{:as node-data :keys [odum.node/name odum.node/energy]}
        @(posh/pull db/conn '[*] node-id)]
    [:div
     ;; We'll use re-com for the inputs, cause they're awesome
     [re-com/input-text
      :model name
      :on-change (fn [new-name] (d/transact! db/conn [{:db/id node-id :odum.node/name new-name}]))] 
     [re-com/input-text
      :model (str energy)
      :on-change (fn [new-val] (d/transact! db/conn [{:db/id node-id :odum.node/energy (js/parseFloat new-val)}]))]]))
    
;; Here's our final node view, which ties together the more generic dragable point and applies it towards our
;; SVG viz, as well as sets up an editable context for edit-node-view

(defn node-view
  [svg-node node-id]
  (let [edit-toggle (r/atom false)]
    (fn [svg-node node-id]
      (let [{:as node-data :keys [odum.node/x odum.node/y odum.node/name odum/editable?]}
            @(posh/pull db/conn '[*] node-id)]
        [:g
         [point {:on-drag (move-node svg-node node-id)} [x y]]
         [:text {:x x :y (+ y 30) :on-click #(swap! edit-toggle not)} name]
         (when @edit-toggle
           ;; Since we're in SVG, if we want regular HTML nodes, we need to nest them in a foreignObject node
           [:foreignObject
            {:x (+ x 20) :y (+ y 60) :height 200 :width 300}
            [:div
             [edit-node-view node-id]]])]))))
             

;; Here's a much simpler view of of a flow as a line between two nodes
;; Might want to do something like this for arrows: http://vanseodesign.com/web-design/svg-markers/
;; Or this: http://stackoverflow.com/questions/11808860/how-to-place-arrow-head-triangles-on-svg-lines

(defn flow-view
  [flow-id]
  (let [{:as flow-data :keys [odum.flow/from odum.flow/to]}
        @(posh/pull db/conn '[{:odum.flow/from [*] :odum.flow/to [*]}] flow-id)
        {from-x :odum.node/x from-y :odum.node/y} from
        {to-x :odum.node/x to-y :odum.node/y} to]
    [:g
     [:line {:x1 from-x :y1 from-y :x2 to-x :y2 to-y :style {:stroke "grey" :stroke-width 2}}]]))

;; And finally we wrap everything together in a single svg view

(defn diagram-view
  []
  [:svg {:height "800" :width "1000"}
   (for [flow @flows]
     ^{:key flow}
     [flow-view flow])
   (for [node @nodes]
     ^{:key node}
     [node-view (r/current-component) node])])


;; A control bar view for creating new nodes

(defn new-node
  ([] (new-node {}))
  ([settings]
   (merge
     {:odum.node/name "New node"
      :odum.node/x 100
      :odum.node/y 100
      :e/type :odum/node
      :odum.node/energy 100}
     settings)))

(defn controls
  []
  [:div
   [:button
    {:on-click (fn [_] (d/transact! db/conn [(new-node)]))}
    "New node"]])

;; This is our top-level application view

(defn app
  []
  [:div
   [:h1 "Odum"]
   [controls]
   [diagram-view]])

;; Which we install below with reagent

(defn ^:export main []
  (log/info "Running main function")
  (when-let [root (.getElementById js/document "app")]
    (r/render-component [app] root)))


(main)

