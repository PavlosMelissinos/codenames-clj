(ns codenames-clj.core
  (:require [codenames-clj.config :as-alias c]
            [clojure.math :as math]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

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

(defn grid
  [{:codenames-clj.config/keys [rows cols civilians assassins]
    :keys [words] :as _cfg}]
  (let [board-size (* rows cols)
        non-spies  (+ civilians assassins)
        team-size  (math/floor-div (- board-size non-spies) 2)
        [t1 t2]    (shuffle [:red :blue])
        codenames  (take board-size (shuffle words))]
    (->> [(repeat (inc team-size) {:card/team t1})
          (repeat team-size {:card/team t2})
          (repeat civilians {:card/team :civilian})
          (repeat assassins {:card/team :assassin})]
         (apply concat)
         shuffle
         (mapv #(assoc %2 :card/codename %1) codenames))))

(defn init
  [cfg]
  (let [g (grid cfg)
        [team] (shuffle [:red :blue])]
    {:grid g
     :phase :clue
     :active-team team
     :guesses-remaining 0
     :status :playing
     :clue nil}))

(defn hidden?
  [card]
  (not (:card/revealed card)))

(defn allowed-move?
  [board idx]
  (some-> (get board idx) hidden?))

(defn reveal
  [grid idx]
  (assoc-in grid [idx :card/revealed] true))

(defn all-spies-revealed?
  [grid team]
  (every? :card/revealed
          (filter #(= (:card/team %) team) grid)))

(defn swap-team
  [team]
  (case team :red :blue :blue :red))

(defn- handle-clue
  [state {:move/keys [type] :as move}]
  (case type
    :give-clue
    (assoc state
           :phase :guess
           :clue {:clue/word (:move/word move)
                  :clue/number (:move/number move)}
           :guesses-remaining (inc (:move/number move)))
    state))

(defn- handle-pass-turn
  [state]
  (assoc state
         :phase :clue
         :active-team (swap-team (:active-team state))
         :guesses-remaining 0
         :clue nil))

(defn- handle-assassin
  [state new-grid]
  (assoc state
         :grid new-grid
         :phase :game-over
         :status (swap-team (:active-team state))
         :guesses-remaining 0))

(defn- handle-own-team
  [state new-grid]
  (let [{:keys [active-team guesses-remaining]} state
        state (assoc state :grid new-grid)]
    (if (all-spies-revealed? new-grid active-team)
      (assoc state :phase :game-over :status active-team :guesses-remaining 0)
      (let [remaining (dec guesses-remaining)]
        (if (zero? remaining)
          (-> state
              (assoc :phase :clue
                     :active-team (swap-team active-team)
                     :guesses-remaining 0
                     :clue nil))
          (assoc state :guesses-remaining remaining))))))

(defn- handle-other-card
  [state new-grid]
  (let [other-team (swap-team (:active-team state))]
    (if (all-spies-revealed? new-grid other-team)
      (assoc state
             :grid new-grid
             :phase :game-over
             :status other-team
             :guesses-remaining 0)
      (assoc state
             :grid new-grid
             :phase :clue
             :active-team other-team
             :guesses-remaining 0
             :clue nil))))

(defn- apply-guess
  [state card-idx]
  (let [new-grid (reveal (:grid state) card-idx)
        card-team (:card/team (nth new-grid card-idx))]
    (cond
      (= card-team :assassin) (handle-assassin state new-grid)
      (= card-team (:active-team state)) (handle-own-team state new-grid)
      :else (handle-other-card state new-grid))))

(defn- handle-guess-phase
  [state {:move/keys [type] :as move}]
  (case type
    :pass-turn (handle-pass-turn state)
    :guess-card
    (let [card-idx (:move/card-idx move)]
      (if (allowed-move? (:grid state) card-idx)
        (apply-guess state card-idx)
        state))
    state))

(defn permitted-actions
  "Returns the set of actions permitted for a given role/team in the
  current game state. Game state only needs :phase, :status, :active-team.
  Player context needs :role and :team."
  [{:keys [phase status active-team]}
   {:keys [role team]}]
  (if (not= :playing status)
    #{}
    (case phase
      :clue  (if (and (= :spymaster role) (= active-team team))
               #{:give-clue}
               #{})
      :guess (if (and (= :spy role) (= active-team team))
               #{:guess-card :pass-turn}
               #{})
      #{})))

(defn advance
  "Given game state and a move, returns the new game state.
  Moves: {:move/type :give-clue  :move/word W :move/number N}
         {:move/type :guess-card :move/card-idx N}
         {:move/type :pass-turn}"
  [{:keys [phase status] :as state} {:move/keys [type] :as move}]
  (if (not= :playing status)
    state
    (case phase
      :clue  (handle-clue state move)
      :guess (handle-guess-phase state move)
      state)))

(comment
  (def cfg (load-config))
  (def state (init cfg))
  ;; Cards are {:card/codename "Lawyer", :card/team :red, :card/revealed false}

  ;; Spymaster gives a clue
  (def s2 (advance state {:move/type :give-clue :move/word "animals" :move/number 3}))
  ;; => phase :guess, guesses-remaining 4, clue set

  ;; Operative guesses a card
  (advance s2 {:move/type :guess-card :move/card-idx 0})
  ;; => grid[0] now :card/revealed true. Phase/team depends on what card 0 is.

  ;; Operative passes
  (advance s2 {:move/type :pass-turn})
  ;; => phase :clue, active-team swapped, guesses-remaining 0

  ;; Game-over is a no-op
  (advance (assoc state :status :red) {:move/type :guess-card :move/card-idx 0})
  ;; => state unchanged

  (def words (-> "words.en.txt" io/resource slurp str/split-lines))
  (-> state :grid first)
  ,)
