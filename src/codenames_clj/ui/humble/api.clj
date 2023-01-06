(ns codenames-clj.ui.humble.api
  (:require [clojure.string :as str]
            [io.github.humbleui.ui :as ui]
            [io.github.humbleui.app :as app]
            [io.github.humbleui.paint :as paint]
            [codenames-clj.ui.humble.state :as state]))

#_(def ui2
  (ui/valign 0.5
    (ui/halign 0.5
      (ui/label "Hello from Humble UI! 👋" nil nil))))
(def *example (atom nil))
(def examples [])
(def example-names [])
(def border-line nil)
(def set-floating! nil)

(def app
  (ui/default-theme {}; :font-size 13
                                        ; :cap-height 10
                                        ; :leading 100
                                        ; :fill-text (paint/fill 0xFFCC3333)
                                        ; :hui.text-field/fill-text (paint/fill 0xFFCC3333)

                    (ui/row
                     (ui/vscrollbar
                      (ui/vscroll
                       (ui/dynamic _ [examples examples
                                      example-names example-names]
                                   (ui/column
                                    (for [ns examples
                                          :let [name (or (example-names ns) (str/capitalize ns))]]
                                      (ui/clickable
                                       {:on-click (fn [_] (reset! *example ns))}
                                       (ui/dynamic ctx [selected? (= ns @*example)
                                                        hovered?  (:hui/hovered? ctx)]
                                                   (let [label (ui/padding 20 10
                                                                           (ui/label name))]
                                                     (cond
                                                       selected? (ui/rect (paint/fill 0xFFB2D7FE) label)
                                                       hovered?  (ui/rect (paint/fill 0xFFE1EFFA) label)
                                                       :else     label)))))))))
                     border-line
                     [:stretch 1
                      (ui/clip
                       (ui/dynamic _ [ui @(requiring-resolve (symbol (str "examples." @*example) "ui"))]
                                   ui))])))

(defn go []
  (ui/start-app!
   (let [{:keys [scale work-area]} (app/primary-screen)
         width (quot (:width work-area) 3)]
     (reset! state/*window
             (ui/window
              {:title    "Humble 🐝 UI"
               :mac-icon "dev/images/icon.icns"
               :width    (/ width scale)
               :height   400
               :x        :left
               :y        :center}
              #'app)))))

#_(defn -main [& args]
  (set-floating! @state/*window @settings/*floating)
  (reset! debug/*enabled? true)
  (redraw)
  (apply nrepl/-main args))

(comment
  (app/terminate)
  1
  (+ 1 1)
  ,)
