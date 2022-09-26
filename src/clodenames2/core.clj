(ns clodenames2.core
  (:require [clodenames.config :as-alias cfg]
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
     (println (type words))
     (-> cfg-file slurp edn/read-string (assoc :words words)))))

(defn grid [{::cfg/keys [rows cols civilians assassins]
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
         (mapv #(assoc %2 :codename %1) codenames))))

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
  ,)
