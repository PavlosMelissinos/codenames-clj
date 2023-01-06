(ns codenames-clj.core
  (:require [codenames-clj.config :as-alias c]
            [clojure.math :as math]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def phase-transitions {:hint :guess, :guess :hint})
(def teams [:red :blue])
(def roles [:spymaster :spy])

(defn load-config
  ([] (-> "config.edn" io/resource load-config))
  ([cfg-file]
   (let [words (-> "words.en.txt"
                   io/resource
                   slurp
                   str/split-lines)]
     (-> cfg-file slurp edn/read-string (assoc :words words)))))

(defn grid [{:codenames-clj.config/keys [rows cols civilians assassins]
             :keys [words] :as _cfg}]
  (let [board-size (* rows cols)
        non-spies  (+ civilians assassins)
        team-size  (math/floor-div (- board-size non-spies) 2)
        [t1 t2]    (shuffle [:red :blue])
        codenames  (take board-size (shuffle words))]
    (->> [(repeat (inc team-size) {:team t1})
          (repeat team-size {:team t2})
          (repeat civilians {})
          (repeat assassins {:assassin true})]
         (apply concat)
         shuffle
         (mapv #(assoc %2 :card/codename %1 :codename %1) codenames))))

(defn init [cfg] (assoc cfg :grid (grid cfg)))
(defn hidden? [card] (-> card :revealed not))
(defn allowed-move? [board move] (some-> (get board move) hidden?))

(defn reveal [grid loc] (update grid loc assoc :revealed true))

(comment
  (def words (-> "words.en.txt"
                 io/resource
                 slurp
                 str/split-lines))

  (def cfg (-> "config.edn"
               io/resource
               slurp
               edn/read-string
               (assoc :words words)))

  (def state (init cfg))

  (-> state
      :grid
      first)

  (apply merge (:grid state))
  ,)

(comment

  (def base-numbers [0 1 2 3 4 5 6 7 8 9 \x])

  (defn op-prime [op x y]
    (let [idxs (zipmap base-numbers (range))
          x (get idxs x)
          y (get idxs y)
          end-idx (mod (op x y) (count base-numbers))]
      (get base-numbers end-idx)))

  (defn +prime [x y] (op-prime + x y))
  (defn -prime [x y] (op-prime - x y))
  (defn *prime [x y] (op-prime * x y))

  (defn div-prime [x y] nil)

  (+prime 5 7)
  (-prime 5 4)
  (*prime 7 8)

  (+prime 0 \x)
  (+prime \x \x)
  (+prime 5 8)
  (+prime 5 \x)

  (*prime 3 6)
  (*prime 8 8)

  (*prime 0 0)
  (*prime 1 1)
  (*prime 5 4)


  (*prime 7 9)
  (*prime 0 0)
  (*prime \x \x)


  (div-prime 1 7)

  (let [x 2
        y 3]
    (mod (+ x y) (count base-numbers)))
  ,)
