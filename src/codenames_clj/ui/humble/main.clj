(ns codenames-clj.ui.humble.main
  "The main app namespace.
  Responsible for initializing the window and app state when the app starts."
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [io.github.humbleui.paint :as paint]
   [io.github.humbleui.ui :as ui]
   ;; [io.github.humbleui.window :as window]
   [codenames-clj.ui.humble.state :as state]
   [codenames-clj.ui.palette :as palette]
   [codenames-clj.core :as logic])
  (:import
   [io.github.humbleui.skija Color ColorSpace Font Typeface]
   [io.github.humbleui.jwm Window]
   [io.github.humbleui.jwm.skija LayerMetalSkija]))

(def *state
  (atom {:b "0" :screen :b}))

(def layout
  {:app-padding-top-bottom 100
   :app-padding-left-right 50
   :card-padding-left-right 5
   :card-padding-top-bottom 5})

(type 0xFF797979)
(def color-digit   0xFF797979)
(def color-op      0xFFFF9F0A)
(def color-clear   0xFF646464)
(def color-display 0xFF4E4E4E)
(def padding 10)

(defn stringify [n]
  (let [s (str n)]
    (if (str/ends-with? s ".0")
      (subs s 0 (- (count s) 2))
      s)))

(defn on-click
  ([s] (swap! *state on-click s))
  ([state s]
   (let [{:keys [a op b screen]} state]
     (case s
       "C"
       {:b "0" :screen :b}

       ("0" "1" "2" "3" "4" "5" "6" "7" "8" "9")
       (cond
         (= screen :a)
         (assoc state :screen :b, :b s)

         (= b "0")
         (assoc state :b s)

         (nil? b)
         (assoc state :b s)

         (= b "-0")
         (assoc state :b (str "-" s))

         :else
         (update state :b str s))

       "."
       (cond
         (= :a screen)
         (assoc state :screen :b, :b "0.")

         (not (str/includes? b "."))
         (update state :b str "."))

       ("+" "−" "×" "÷")
       (if op
         (-> state (on-click "=") (assoc :op s))
         (assoc state :screen :a, :a b, :op s))

       "="
       (when (some? op)
         (let [a (some-> a parse-double)
               b (or (some-> b parse-double) a)]
           (case op
             "+" (assoc state :screen :a :a (stringify (+ a b)))
             "−" (assoc state :screen :a :a (stringify (- a b)))
             "×" (assoc state :screen :a :a (stringify (* a b)))
             "÷" (assoc state :screen :a :a (stringify (/ a b))))))

       "±"
       (if (str/starts-with? b "-")
         (update state :b subs 1)
         (update state :b #(str "-" %)))))))

(defn button [text {:keys [text-fill background-color] :as opts}]
  (ui/clickable
    {:on-click (fn [_] (on-click text))}
    (ui/dynamic ctx [{:keys [hui/active? hui/hovered? font-btn]} ctx]
                (let [bg-color (if active?
                                 (bit-or 0x80000000 (bit-and 0xFFFFFF background-color))
                                 background-color)]
        (ui/rect (paint/fill bg-color)
          (ui/center
            (ui/label {:paint text-fill
                       :font font-btn
                       :features ["tnum"]} text)))))))

(defn update-greeting [val]
  (log/debug "Greeting update requested")
  (swap! *state assoc :greeting val))

(comment
  (update-greeting "lala")
  ,)

(defn scale-font [^Font font cap-height']
  (let [size       (.getSize font)
        cap-height (-> font .getMetrics .getCapHeight)]
    (-> size (/ cap-height) (* cap-height'))))

(comment
  (-> ctx-glb ::bounds :height)
  (vec (sort (keys ctx-glb)))
  (-> ctx-glb ::bounds :width)
  ,)

(defn team [{:keys [team visible assassin] :as _card}]
  (cond
    (not visible) :hidden
    assassin :assassin
    (not team) :civilian
    :else team))

(defmulti card-style team)

(defmethod card-style :assassin [{:keys [revealed visible]}]
  {:background-color (:card-assassin-primary palette/color-palette)
   :text-fill (:card-assassin-primary-alt palette/color-palette)
   :opacity (if (and (not revealed) visible) 0.5 1)})

(defmethod card-style :hidden [_] nil)

(defmethod card-style :blue [{:keys [revealed visible]}]
  {:background-color (:card-blue-team-primary palette/color-palette)
   :text-fill (:card-blue-team-primary-alt palette/color-palette)
   :opacity (if (and (not revealed) visible) 0.5 1)})

(defmethod card-style :red [{:keys [revealed visible]}]
  {:background-color (:card-red-team-primary palette/color-palette)
   :text-fill (:card-red-team-primary-alt palette/color-palette)
   :opacity (if (and (not revealed) visible) 0.5 1)})

(defmethod card-style :civilian [{:keys [revealed visible]}]
  {:background-color (:card-civilian-primary palette/color-palette)
   :text-fill (:card-civilian-primary-alt palette/color-palette)
   :opacity (if (and (not revealed) visible) 0.5 1)})

#_(hex (:card-civilian-primary palette/color-palette))

(defn draw-card [{:keys [assassin codename team] :as card}]
  (let [card (update card :visible #(or % (:revealed card)))]
    [:stretch 1 (button codename (card-style card))]))

(defn draw-row [cards]
  (let [row (->> (map #(vec :stretch 1 (button )) cards))])
  [:stretch 2 (apply ui/row
                     [:stretch 1 (button "±" color-clear)]
                     (ui/gap padding 0)
                     [:stretch 1 (button "±" color-clear)]
                     (ui/gap padding 0)
                     [:stretch 1 (button "±" color-clear)]
                     (ui/gap padding 0)
                     [:stretch 1 (button "±" color-clear)]
                     (ui/gap padding 0)
                     [:stretch 1 (button "÷" color-op)])])

(defn draw-grid [cards]
  (let [rows (->> (partition 5 cards)
                  (map draw-row))]
    (ui/column
     [:stretch 2 (ui/row
                  (draw-card (first cards))
                  (ui/gap padding 0)
                  [:stretch 1 (button "±" color-clear)]
                  (ui/gap padding 0)
                  [:stretch 1 (button "±" color-clear)]
                  (ui/gap padding 0)
                  [:stretch 1 (button "±" color-clear)]
                  (ui/gap padding 0)
                  [:stretch 1 (button "÷" color-op)])]
     (ui/gap 0 padding)
     [:stretch 2 (ui/row
                  [:stretch 1 (button "7" color-digit)]
                  (ui/gap padding 0)
                  [:stretch 1 (button "8" color-digit)]
                  (ui/gap padding 0)
                  [:stretch 1 (button "9" color-digit)]
                  (ui/gap padding 0)
                  [:stretch 1 (button "×" color-op)]
                  (ui/gap padding 0)
                  [:stretch 1 (button "×" color-op)])]
     (ui/gap 0 padding)
     [:stretch 2 (ui/row
                  [:stretch 1 (button "4" color-digit)]
                  (ui/gap padding 0)
                  [:stretch 1 (button "5" color-digit)]
                  (ui/gap padding 0)
                  [:stretch 1 (button "6" color-digit)]
                  (ui/gap padding 0)
                  [:stretch 1 (button "−" color-op)])]
     (ui/gap 0 padding)
     [:stretch 2 (ui/row
                  [:stretch 1 (button "1" color-digit)]
                  (ui/gap padding 0)
                  [:stretch 1 (button "2" color-digit)]
                  (ui/gap padding 0)
                  [:stretch 1 (button "3" color-digit)]
                  (ui/gap padding 0)
                  [:stretch 1 (button "+" color-op)])]
     (ui/gap 0 padding)
     [:stretch 2 (ui/row
                  (ui/width #(-> (:width %) (- (* 3 padding)) (/ 2) (+ padding)) (button "0" color-digit))
                  (ui/gap padding 0)
                  [:stretch 1 (button "." color-digit)]
                  (ui/gap padding 0)
                  [:stretch 1 (button "=" color-op)])])))

(defn grid2 []
  (ui/with-bounds ::bounds
    (ui/dynamic
     ctx [{:keys [face-ui font-ui scale] :as ctx2} ctx
          height (:height (::bounds ctx))]
     (let [_ (def ctx-glb ctx2)
           face-ui ^Typeface face-ui
           btn-height     (-> height (- (* 7 padding)) (/ 13) (* 2))
           cap-height'    (-> btn-height (/ 3) (* scale) (Math/floor))
           display-height (-> height (- (* 7 padding)) (/ 13) (* 3))
           cap-height''   (-> display-height (/ 3) (* scale) (Math/floor))]
       (ui/dynamic
        _ [size'  (scale-font font-ui cap-height')
           size'' (scale-font font-ui cap-height'')]

        (ui/with-context {:font-btn     (Font. face-ui (float size'))
                          :font-display (Font. face-ui (float size''))
                          :fill-text    (paint/fill 0xFFEBEBEB)}
          (ui/rect
           (paint/fill color-display)
           (ui/padding
            padding padding
            (draw-grid (:card @*state))))))))))

(defn grid [elems]
  (apply ui/column
   (for [row-elems elems]
     (apply ui/row
      (for [elem row-elems]
        (ui/column elem)
        #_(ui/padding
         (:card-padding-left-right layout)
         (:catd-padding-top-bottom layout)
         (ui/column elem)))))))

(defn card []
  (ui/button (fn [] (update-greeting (str (rand))))
             (ui/dynamic _ [greeting (:greeting @*state)]
                         (ui/label greeting))))

(defn game-view []
  (ui/padding (:app-padding-left-right layout)
              (:app-padding-top-bottom layout)
              (grid2)
              #_(grid [[(card) (card) (card) (card) (card)]
                     [(card) (card) (card) (card) (card)]
                     [(card) (card) (card) (card) (card)]
                     [(card) (card) (card) (card) (card)]
                     [(card) (card) (card) (card) (card)]])
              #_(ui/column
               (ui/gap 5 0)
               (ui/button (fn [] (update-greeting (str (rand))))
                          (ui/dynamic _ [greeting (:greeting @*state)]
                                      (ui/label greeting))))))

(def app
  "Main app definition."
  (do
    (state/redraw!)
    (log/debug "Reloading app!")
    (ui/default-theme {} (game-view))))

;; reset current app state on eval of this ns
(reset! state/*app app)

(defn -main
  "Run once on app start, starting the humble app."
  [& args]
  (ui/start-app!
   (reset! state/*window
           (ui/window
            {:title    "Codenames"
             :bg-color 0xFFFFFFFF}
            state/*app)))
  (state/redraw!))

(state/redraw!)

(comment
  (swap! *state merge (logic/init (logic/load-config)))

  (->> (logic/init (logic/load-config))
       :grid
       (mapcat keys)
       set)

  (state/reset!)
  (state/redraw!)
  (-> @*state
      :greeting)

  (-> @state/*window)

  (clojure.java.io/resource "foo")
  ,)
