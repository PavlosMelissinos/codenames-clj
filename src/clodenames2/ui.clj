(ns clodenames2.ui
  (:require [cljfx.api :as fx]
            [clodenames.config :as-alias cfg]
            [clodenames2.core :as core]
            [clojure.math :as math]
            [clojure.string :as str]))

(defonce *state (atom (core/init (core/load-config))))

(def default-card-style
  {:-fx-background-color :lightgray
   :-fx-padding          10
   :-fx-border-color     :black
   :-fx-border-radius    4})

(def ^:private colors
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

(defn team [{:keys [team visible assassin] :as _card}]
  (cond
    (not visible) :hidden
    assassin :assassin
    (not team) :civilian
    :else team))

;; Events

(defmulti event-handler :event/type)

(defmethod event-handler ::card-clicked [{:keys [idx] :as e}]
  (swap! *state update :grid core/reveal idx))

;; Styles

(defmulti card-style team)

(defmethod card-style :assassin [{:keys [revealed visible]}]
  {:-fx-background-color (:card-assassin-primary color-palette)
   :-fx-text-fill (:card-assassin-primary-alt color-palette)
   :-fx-opacity (if (and (not revealed) visible) 0.5 1)})

(defmethod card-style :hidden [_] nil)

(defmethod card-style :blue [{:keys [revealed visible]}]
  {:-fx-background-color (:card-blue-team-primary color-palette)
   :-fx-text-fill (:card-blue-team-primary-alt color-palette)
   :-fx-opacity (if (and (not revealed) visible) 0.5 1)})

(defmethod card-style :red [{:keys [revealed visible]}]
  {:-fx-background-color (:card-red-team-primary color-palette)
   :-fx-text-fill (:card-red-team-primary-alt color-palette)
   :-fx-opacity (if (and (not revealed) visible) 0.5 1)})

(defmethod card-style :civilian [{:keys [revealed visible]}]
  {:-fx-background-color (:card-civilian-primary color-palette)
   :-fx-text-fill (:card-civilian-primary-alt color-palette)
   :-fx-opacity (if (and (not revealed) visible) 0.5 1)})

(defn card [{:keys [codename] :as card}]
  (let [card (update card :visible #(or % (:revealed card)))]
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
                             :style (select-keys (card-style card)
                                                 [:-fx-text-fill])
                             :text codename}]}]}))

(defn grid-pane [{::cfg/keys [cols]
                  :keys [grid role]
                  :as _state}]
  {:fx/type :grid-pane
   :style {:-fx-padding 30}
   :children (map-indexed (fn [idx it]
                            (merge it
                                   {:fx/type card
                                    :grid-pane/column (math/floor-div idx cols)
                                    :grid-pane/row (mod idx cols)
                                    :grid-pane/hgrow :always
                                    :grid-pane/vgrow :always
                                    :idx idx}
                                   (when (= role :spymaster)
                                     {:visible true}))) grid)})

(defn game-window-view [{:keys [grid role] :as state}]
  {:fx/type :stage
   :showing true
   :scene {:fx/type :scene
           :root {:fx/type :v-box
                  :children [{:fx/type :label
                              :text (str "View: " (str/capitalize (name role)))}
                             (assoc state :fx/type grid-pane)]}}})

(defonce renderer ;; todo: does this really belong here?
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

(comment

  (def cfg (core/load-config))
  (reset! *state (core/init cfg))

  (def *state (atom (core/init cfg)))

  (fx/mount-renderer *state renderer)

  (fx/unmount-renderer *state renderer)

  (renderer)
  ,)
