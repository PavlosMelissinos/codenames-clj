(ns clodenames2.ui
  (:require [cljfx.api :as fx]
            [clodenames2.core :as core]
            [clojure.math :as math]
            [clojure.string :as str]))

(def card-width 100)
(def card-height 100)

(def default-card-style
  {:-fx-background-color :lightgray
   :-fx-padding          10
   :-fx-border-color     :black
   :-fx-border-radius    4})

(def colors
  ;; names from https://colornames.org
  {:tastefully-pumpkin "#DC8665" ;; salmon
   :theom "#138086"
   :aquarium-rocks "#16949b"
   :barney-shet "#554869"
   :tired-peach-pink "#CD7672"
   :sick-camel "#EEB462"
   :cherry-blossom-yoghurt "#F5CDC6"
   :burnt-bubblegum "#EF9796"
   :peach-eyeshadow "#FFC98B"
   :peached-out "#FFB284"
   :introverted-broccoli "#C6C09b"
   :coral "#FF7F50"
   :sail-far-blue "#4fd0ff"
   :white "#FFFFFF"
   :black "#000000"})

(def color-palette
  ;; https://digitalsynopsis.com/wp-content/uploads/2019/04/beautiful-color-gradient-palettes-31-1024x795.jpg
  {:card-red-team-primary (:tired-peach-pink colors)
   :card-blue-team-primary (:theom colors)
   :card-assassin-primary (:barney-shet colors)
   :card-civilian-primary (:sick-camel colors)
   :card-red-team-primary-alt (:black colors)
   :card-blue-team-primary-alt (:white colors)
   :card-assassin-primary-alt (:white colors)
   :card-civilian-primary-alt (:black colors)})

(defn team [{:keys [team revealed assassin] :as card}]
  (cond
    (not revealed) :hidden
    assassin :assassin
    (not team) :civilian
    :else team))

;; Events

(defmulti event-handler :event/type)

(defmethod event-handler ::card-clicked [{:keys [idx] :as e}]
  (swap! *state update :grid core/reveal idx))

;; Styles

(defmulti card-style team)

(defmethod card-style :assassin [card]
  {:-fx-background-color (:card-assassin-primary color-palette)
   :-fx-text-fill (:card-assassin-primary-alt color-palette)})

(defmethod card-style :hidden [card] nil)

(defmethod card-style :blue [card]
  {:-fx-background-color (:card-blue-team-primary color-palette)
   :-fx-text-fill (:card-blue-team-primary-alt color-palette)})

(defmethod card-style :red [card]
  {:-fx-background-color (:card-red-team-primary color-palette)
   :-fx-text-fill (:card-red-team-primary-alt color-palette)})

(defmethod card-style :civilian [card]
  {:-fx-background-color (:card-civilian-primary color-palette)
   :-fx-text-fill (:card-civilian-primary-alt color-palette)})

(defn card [{:keys [codename revealed assassin idx] :as card}]
  {:fx/type :h-box
   :style {:-fx-padding 5}
   :on-mouse-clicked (assoc card :event/type ::card-clicked)
   :alignment :center
   :children [{:fx/type  :v-box
               :h-box/hgrow :always
               :alignment :center
               :style    (merge default-card-style (card-style card))
               :children [{:fx/type :label
                           :v-box/margin 5
                           :style (merge {:-fx-background-color :lightgray}
                                         (card-style card))
                           :text codename}]}]})

(defn grid-pane [{:keys [grid event role] :as _state}]
  {:fx/type :grid-pane
   :style {:-fx-padding 30}
   :children (map-indexed (fn [idx it]
                            (merge it
                                   {:fx/type card
                                    :grid-pane/column (math/floor-div idx 5)
                                    :grid-pane/row (mod idx 5)
                                    :grid-pane/hgrow :always
                                    :grid-pane/vgrow :always
                                    :idx idx}
                                   (when (= role :spymaster)
                                     {:revealed true}))) grid)})

(defn slider-view [{:keys [min max value label event]}]
  {:fx/type :h-box
   :children [{:fx/type :label
               :text label}
              {:fx/type :slider
               :min min
               :max max
               :value value
               :on-value-changed {:event/type event}
               :major-tick-unit max
               :show-tick-labels true}]})

(defn game-window-view [{:keys [gravity friction grid role]
                         :or {friction 0 gravity 0} :as state}]
  {:fx/type :stage
   :showing true
   :scene {:fx/type :scene
           :root {:fx/type :v-box
                  :children [{:fx/type :label
                              :text (str "View: " (str/capitalize (name role)))}
                             (assoc state :fx/type grid-pane)]}}})

(def renderer
  (fx/create-renderer
   :middleware (fx/wrap-map-desc (fn [state]
                                   {:fx/type fx/ext-many
                                    :desc [(merge state
                                                  {:fx/type game-window-view
                                                   :role :spymaster})
                                           (merge state
                                                  {:fx/type game-window-view
                                                   :role :spy})]}))
   :opts {:fx.opt/map-event-handler event-handler}))

;;(renderer)

(comment

  (def cfg (core/load-config))

  (def *state (atom {:grid (core/init cfg)}))

  (reset! *state {:grid (core/init cfg)})

  (fx/mount-renderer *state renderer)

  (fx/unmount-renderer *state renderer)

  (renderer)
  ,)
