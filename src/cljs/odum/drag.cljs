(ns odum.drag
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [odum.views :as views]
            [odum.events :as events]
            [odum.db :as db]
            [goog.events :as gevents]
            [dat.reactor.dispatcher :as dispatcher]
            [datascript.core :as d]
            [taoensso.timbre :as log :include-macros true]
            [re-com.core :as re-com]
            [reagent.core :as r]
            [posh.reagent :as posh])
  (:import [goog.events EventType]))  


;; This namespace provides some utilities for doing drag and drop (takes care of all the messy dom
;; manipulation and such).
;; The main entry point is the dragging function, which must take a on-drag handler.
;; The second import function here is get-bcr, which gets some dom element based on the result of calling
;; `r/current-component` from the component defining the svg node.
;; So you'll see that function called where the SVG node is created, and then passed along to the nodes that
;; you're dragging, so that we can use the drag handler there.


(defn get-bcr [svg-root]
  (-> svg-root
      r/dom-node
      .getBoundingClientRect))

(defn drag-move-fn [on-drag]
  (fn [evt]
    (on-drag (.-clientX evt) (.-clientY evt))))

(defn drag-end-fn [drag-move drag-end on-end]
  (fn [evt]
    (gevents/unlisten js/window EventType.MOUSEMOVE drag-move)
    (gevents/unlisten js/window EventType.MOUSEUP @drag-end)
    (on-end)))

(defn dragging
  ([on-drag] (dragging on-drag (fn []) (fn [])))
  ([on-drag on-start on-end]
   (let [drag-move (drag-move-fn on-drag)
         drag-end-atom (atom nil)
         drag-end (drag-end-fn drag-move drag-end-atom on-end)]
     (on-start)
     (reset! drag-end-atom drag-end)
     (gevents/listen js/window EventType.MOUSEMOVE drag-move)
     (gevents/listen js/window EventType.MOUSEUP drag-end))))


