(ns codenames-clj.ui
  (:require [cljfx.api :as fx]
            [codenames-clj.config :as-alias cfg]
            [codenames-clj.core :as core]
            [codenames-clj.ui.palette :as palette]
            [clojure.core.cache :as cache]
            [clojure.math :as math]
            [clojure.string :as str])
  (:import [javafx.scene.control DialogEvent Dialog ButtonBar$ButtonData ButtonType]))

(set! *warn-on-reflection* true)

(defonce *state
  (atom (merge (core/init (core/load-config))
               {:showing true
                :internal {}})))

(def default-card-style
  {:-fx-background-color :lightgray
   :-fx-padding          10
   :-fx-border-color     :black
   :-fx-border-radius    4})

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

(defmethod event-handler ::new-game-requested [{:keys [idx] :as e}]
  (swap! *state assoc ::game-boards-visible true))

(defmethod event-handler ::forfeit-clicked [{:keys [idx] :as e}]
  (prn e)
  (swap! *state assoc ::main-menu-visible true))

(defmethod event-handler ::quit-game-clicked
  [{:fx/keys [context] :as e}]
  (prn e)
  ;;(swap! *state assoc ::main-menu-visible true)
  {:context (fx/swap-context context assoc :showing false)})

;; Styles

(defmulti card-style team)

(defmethod card-style :assassin [{:keys [revealed visible]}]
  {:-fx-background-color (:card-assassin-primary palette/color-palette)
   :-fx-text-fill (:card-assassin-primary-alt palette/color-palette)
   :-fx-opacity (if (and (not revealed) visible) 0.5 1)})

(defmethod card-style :hidden [_] nil)

(defmethod card-style :blue [{:keys [revealed visible]}]
  {:-fx-background-color (:card-blue-team-primary palette/color-palette)
   :-fx-text-fill (:card-blue-team-primary-alt palette/color-palette)
   :-fx-opacity (if (and (not revealed) visible) 0.5 1)})

(defmethod card-style :red [{:keys [revealed visible]}]
  {:-fx-background-color (:card-red-team-primary palette/color-palette)
   :-fx-text-fill (:card-red-team-primary-alt palette/color-palette)
   :-fx-opacity (if (and (not revealed) visible) 0.5 1)})

(defmethod card-style :civilian [{:keys [revealed visible]}]
  {:-fx-background-color (:card-civilian-primary palette/color-palette)
   :-fx-text-fill (:card-civilian-primary-alt palette/color-palette)
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

(defn game-board-view [{::cfg/keys [cols]
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


(defn title-bar [role]
  {:fx/type :label
   :text (str "View: " (str/capitalize (name role)))})

(defn main-menu-view [{:keys [role fx/context] :as state}]
  {:fx/type :stage
   :showing true
   ;;:showing (fx/sub-val context :showing)
   :scene {:fx/type :scene
           :root {:fx/type :v-box
                  :children [{:fx/type :button
                              :text "Start game"
                              :on-action (assoc state
                                                :event/type ::new-game-requested)}
                             {:fx/type :button
                              :text "Quit"
                              :on-action (assoc state
                                                :event/type ::quit-game-requested)}
                             #_(assoc state :fx/type grid-pane)]}}})

(defn game-window-view [{:keys [role fx/context] :as state}]
  {:fx/type :stage
   :showing true
   ;;:showing (fx/sub-val context :showing)
   :scene {:fx/type :scene
           :root {:fx/type :v-box
                  :children [(title-bar role)
                             (assoc state :fx/type game-board-view)]}}})

(defn desc-fn [state]
  {:fx/type fx/ext-many
   :desc [#_(merge state
                 {:fx/type main-menu-view})
          (merge state
                 {:fx/type game-window-view
                  :role :spymaster})
          (merge state
                 {:fx/type game-window-view
                  :role :spy})]})

(defonce renderer ;; todo: does this really belong here?
  (fx/create-renderer
   :middleware (fx/wrap-map-desc desc-fn)
   :opts {:fx.opt/map-event-handler event-handler}))

(renderer)

(comment

  (def cfg (core/load-config))
  (reset! *state (core/init cfg))

  (def *state (atom (core/init cfg)))

  @*state

  (def renderer-dev
    (fx/create-renderer
     :middleware (fx/wrap-map-desc desc-fn)
     :opts {:fx.opt/map-event-handler event-handler}))

  (fx/mount-renderer *state renderer-dev)
  (fx/unmount-renderer *state renderer-dev)

  (fx/mount-renderer *state renderer)
  (fx/unmount-renderer *state renderer)

  (renderer)
  (renderer-dev)

  (def *context
    (atom (fx/create-context
           {:showing true
            :internal {}}
           cache/lru-cache-factory)))

  (def app2
    (fx/create-app *context
                   :event-handler event-handler
                   :desc-fn desc-fn))

  (fx/unbind-context @*context)

  ,)
