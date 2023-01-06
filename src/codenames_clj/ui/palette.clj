(ns codenames-clj.ui.palette)

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
