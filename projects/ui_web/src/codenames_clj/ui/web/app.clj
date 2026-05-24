(ns codenames-clj.ui.web.app
  (:require [codenames-clj.ui.web.middleware :as mid]
            [codenames-clj.ui.web.ui :as ui]
            [codenames-clj.core :as logic]
            [codenames-clj.config :as-alias c]
            [com.biffweb :as biff]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ring.adapter.jetty9 :as jetty]
            [ring.middleware.anti-forgery :as anti-forgery]
            [rum.core :as rum]
            [xtdb.api :as xt]))

;; match

(defn words [lang]
  (-> (format "words.%s.txt" lang)
      io/resource
      slurp
      str/split-lines))

(def default-cfg
  {9  {::c/rows      3
       ::c/cols      3
       ::c/civilians 3
       ::c/assassins 1}
   16 {::c/rows      4
       ::c/cols      4
       ::c/civilians 4
       ::c/assassins 1}
   25 {::c/rows      5
       ::c/cols      5
       ::c/civilians 7
       ::c/assassins 1}
   36 {::c/rows      6
       ::c/cols      6
       ::c/civilians 12
       ::c/assassins 1}})

;; match

(defn match-get [db match-id]
  (biff/lookup db :xt/id match-id))

(defn match-create [{:keys [match/cfg session] :as sys}]
  (let [match-id (random-uuid)
        game     (logic/init cfg)]
    (biff/submit-tx
      sys
      [{:db/doc-type            :match
        :xt/id                  match-id
        :match/grid             (:grid game)
        :match/phase            (:phase game)
        :match/active-team      (:active-team game)
        :match/guesses-remaining (:guesses-remaining game)
        :match/status           (:status game)
        :match/clue             (:clue game)
        :match/creator          (:uid session)
        :match/created-at       :db/now}])
    match-id))

(defn match-delete [{:keys [session path-params match] :as sys}]
  (let [match-id (-> path-params :match-id)]
    (when (= (:match/creator match) (:uid session))
      (biff/submit-tx
        sys
        [{:db/op :delete
          :xt/id (parse-uuid match-id)}]))
    {:status 200
     :body match-id}))

(defn matches-list [db user-id]
  (let [created (xt/q db
                      '{:find [?match-id]
                        :in [?user-id]
                        :where [[?match :match/creator ?user-id]
                                [?match :xt/id ?match-id]]}
                      user-id)
        joined  (xt/q db
                      '{:find [?match-id]
                        :in [?user-id]
                        :where [[?player :player/match ?match-id]
                                [?player :player/user ?user-id]]}
                      user-id)]
    (->> (concat created joined)
         distinct
         (filter (fn [[id]] (match-get db id))))))

;; player

(defn players-list [db match-id]
  (map first (xt/q db
                   '{:find [(pull ?player [:xt/id {:player/user [:user/email]} :player/role :player/nick :player/team])]
                     :in [?match-id]
                     :where [[?player :player/match ?match-id]]}
                   match-id)))

(defn nickname [{:player/keys [nick user]}]
  (or nick (:user/email user)))

(defn player-get [db user-id match-id]
  (biff/lookup db :player/user user-id :player/match match-id))

(defn player-add [sys user-id {:keys [match team role]}]
  (let [player (cond-> {:db/doc-type :player
                        :xt/id (random-uuid)
                        :player/user user-id
                        :player/match match
                        :player/role (or role :observer)}
                 team (assoc :player/team team))]
    (log/info "Adding player to db")
    (biff/submit-tx sys [player
                         {:db/doc-type  :action
                          :db/op        :put
                          :xt/id        (random-uuid)
                          :action/actor (:xt/id player)
                          :action/match match
                          :action/type  :codenames/player-added}
                         [::xt/fn :biff/ensure-unique {:player/user user-id
                                                       :player/match match}]])))

(defn player-update [sys
                     {old-role :player/role :as player}
                     {:keys [match team role]}]
  (if (and (= :spymaster old-role) (not (= :spymaster role)))
    (log/info "Not allowed")
    (let [player (merge
                  player
                  {:db/op :update
                   :db/doc-type :player
                   :player/match match
                   :player/role role}
                  (when team {:player/team team}))]
      (log/info "Updating player info")
      (biff/submit-tx sys [player
                           {:db/doc-type  :action
                            :db/op        :put
                            :xt/id        (random-uuid)
                            :action/actor (:xt/id player)
                            :action/match match
                            :action/type  :codenames/team-role-selected}]))))

(defn player-delete [sys id]
  (log/info "Deleting player")
  (biff/submit-tx sys [{:db/op :delete
                        :xt/id (parse-uuid id)}]))

(defn handle-player-set-role [{:keys [session params biff/db match] :as ctx}]
  (let [user-id (:uid session)
        params  (-> params
                    (update :team keyword)
                    (update :role keyword)
                    (select-keys [:team :role])
                    (assoc :match (:xt/id match)))
        player  (player-get db user-id (:xt/id match))]
    (if player
      (player-update ctx player params)
      (player-add ctx user-id params))
    [:div]))

(defn handle-set-nickname [{:keys [session params player match] :as ctx}]
  (let [nick    (get params :nick)
        player  (merge player
                       {:db/op :update
                        :db/doc-type :player
                        :player/nick nick})]
    (biff/submit-tx ctx
      [player
       {:db/doc-type  :action
        :db/op        :put
        :xt/id        (random-uuid)
        :action/actor (:xt/id player)
        :action/match (:xt/id match)
        :action/type  :codenames/nickname-set}])
    [:div]))

;; player end

(def role-classes
  {:hidden {:normal "bg-teal-600 hover:bg-teal-800"
            :visible "bg-teal-600 hover:bg-teal-800"
            :revealed "bg-teal-600 text-black"}
   :assassin {:normal "bg-slate-600 hover:bg-slate-800"
              :visible "bg-slate-600 hover:bg-slate-800"
              :revealed "bg-slate-600"}
   :civilian {:normal "bg-amber-600 hover:bg-amber-800"
              :visible "bg-amber-600 hover:bg-amber-800"
              :revealed "bg-amber-600 text-black"}
   :blue {:normal "bg-blue-600 hover:bg-blue-800"
          :visible "bg-blue-600 hover:bg-blue-800"
          :revealed "bg-blue-600 text-black"}
   :red {:normal "bg-red-600 hover:bg-red-800"
         :visible "bg-red-600 hover:bg-red-800"
         :revealed "bg-red-600 text-black"}})

(def status-classes
  {:normal   "hover:shadow-lg active:shadow-lg"
   :visible  "hover:shadow-lg active:shadow-lg"
   :revealed "opacity-50"})

(defn font-size-class [codename]
  (cond
    (> (count codename) 14) "text-[7px]"
    (> (count codename) 12) "text-[8px]"
    (> (count codename) 10) "text-[9px]"
    (> (count codename) 8) "text-xs"
    (> (count codename) 7) "text-sm"
    :else "text-base"))

(defn render-card [{:keys [can-guess] :match/keys [grid id] :as _match} idx]
  (let [{:card/keys [codename team revealed]
         :keys [visible]} (nth grid idx)
        role    (if (or visible revealed) team :hidden)
        status  (cond
                  revealed :revealed
                  visible :visible
                  :else :normal)
        classes (format "py-3 px-1 rounded w-full h-full text-white %s %s %s truncate sm:text-base"
                        (get-in role-classes [role status])
                        (get status-classes status)
                        (font-size-class codename))]
    [:button {:hx-post (format "/app/match/%s/card/%s" id idx)
              :hx-swap "none"
              :type "submit"
              :title codename
              :disabled (or (not can-guess) (boolean revealed))
              :class classes} codename]))

(defn grid [db match-id]
  (-> (biff/lookup db :xt/id match-id)
      :match/grid))

(defn card-info [db {:keys [match-id card-idx]}]
  (-> (grid db match-id)
      (nth card-idx)))

(defn card-reveal [{:keys [session path-params biff/db player match] :as ctx}]
  (let [match-id (parse-uuid (:match-id path-params))
        card-idx (parse-long (:idx path-params))
        {:match/keys [grid phase active-team guesses-remaining status clue]} match
        game     {:grid grid :phase phase :active-team active-team
                  :guesses-remaining guesses-remaining :status status :clue clue}
        player-ctx {:role (:player/role player) :team (:player/team player)}]
    (when (contains? (logic/permitted-actions game player-ctx) :guess-card)
      (let [new-game (logic/advance game {:move/type :guess-card :move/card-idx card-idx})]
        (when (not= game new-game)
          (biff/submit-tx ctx
            [{:db/doc-type            :match
              :db/op                  :update
              :xt/id                  match-id
              :match/grid             (:grid new-game)
              :match/phase            (:phase new-game)
              :match/active-team      (:active-team new-game)
              :match/guesses-remaining (:guesses-remaining new-game)
              :match/status           (:status new-game)
              :match/clue             (:clue new-game)
              :match/creator          (:uid session)}
             {:db/doc-type  :action
              :db/op        :put
              :xt/id        (random-uuid)
              :action/actor (:xt/id player)
              :action/match match-id
              :action/type  :codenames/card-revealed}]))))
    {:status 200}))

(defn handle-give-clue [{:keys [session params player match] :as ctx}]
  (let [match-id (:xt/id match)
        {:match/keys [grid phase active-team guesses-remaining status clue]} match
        game     {:grid grid :phase phase :active-team active-team
                  :guesses-remaining guesses-remaining :status status :clue clue}
        player-ctx {:role (:player/role player) :team (:player/team player)}]
    (when (contains? (logic/permitted-actions game player-ctx) :give-clue)
      (let [word     (get params :word)
            number   (parse-long (get params :number))
            new-game (logic/advance game {:move/type :give-clue :move/word word :move/number number})]
        (when (not= game new-game)
          (biff/submit-tx ctx
            [{:db/doc-type            :match
              :db/op                  :update
              :xt/id                  match-id
              :match/grid             (:grid new-game)
              :match/phase            (:phase new-game)
              :match/active-team      (:active-team new-game)
              :match/guesses-remaining (:guesses-remaining new-game)
              :match/status           (:status new-game)
              :match/clue             (:clue new-game)
              :match/creator          (:uid session)}
             {:db/doc-type  :action
              :db/op        :put
              :xt/id        (random-uuid)
              :action/actor (:xt/id player)
              :action/match match-id
              :action/type  :codenames/clue-given}]))))
    {:status 200}))

(defn handle-pass-turn [{:keys [session player match] :as ctx}]
  (let [match-id (:xt/id match)
        {:match/keys [grid phase active-team guesses-remaining status clue]} match
        game     {:grid grid :phase phase :active-team active-team
                  :guesses-remaining guesses-remaining :status status :clue clue}
        player-ctx {:role (:player/role player) :team (:player/team player)}]
    (when (contains? (logic/permitted-actions game player-ctx) :pass-turn)
      (let [new-game (logic/advance game {:move/type :pass-turn})]
        (when (not= game new-game)
          (biff/submit-tx ctx
            [{:db/doc-type            :match
              :db/op                  :update
              :xt/id                  match-id
              :match/grid             (:grid new-game)
              :match/phase            (:phase new-game)
              :match/active-team      (:active-team new-game)
              :match/guesses-remaining (:guesses-remaining new-game)
              :match/status           (:status new-game)
              :match/clue             (:clue new-game)
              :match/creator          (:uid session)}
             {:db/doc-type  :action
              :db/op        :put
              :xt/id        (random-uuid)
              :action/actor (:xt/id player)
              :action/match match-id
              :action/type  :codenames/pass-turn}]))))
    {:status 200}))

;;; components

(defn- game-over [winner]
  [:div.p-4.rounded-lg.bg-gray-600.text-white.text-center.space-y-3
   [:div.text-xl.font-bold
    (str "Game over! "
         (case winner :red "Red" :blue "Blue") " team wins!")]
   [:div.flex.justify-center.gap-3
    [:a.rounded.bg-blue-600.px-4.py-1.text-white.text-sm.hover:bg-blue-700
     {:href "/app"}
     "Back to Lobby"]
    [:a.rounded.bg-green-600.px-4.py-1.text-white.text-sm.hover:bg-green-700
     {:href "/app"}
     "Play Again"]]])

(defn- maybe-game-over [status]
  (when (not= status :playing)
    (game-over status)))

(defn- slider [{:keys [min max step label id]}]
  (let [id     (or id (-> label str/lower-case (str/replace #" " "-")))
        values (range min (inc max) step)]
    [:div
     [:label {:for id, :class "block text-sm font-medium text-gray-700"} label]
     [:input {:type "range", :id id, :name id, :class "w-full mt-1 flex items-center",
              :min (str min), :max (str max), :step (str step)}]
     [:div {:class "flex justify-between ml-[7px] mr-[2px] text-sm text-gray-500"}
      (for [v values] [:div (str (* v v))])]]))

(defn- modal [content-fn id]
  [:div {:id id
         :style {:display "none"}
         :class "relative z-10"
         :aria-hidden true}
   [:div {:class "fixed inset-0 bg-gray-500 bg-opacity-75 transition-opacity"}]
   [:div {:class "fixed inset-0 z-10 overflow-y-auto"}
    [:div {:class "flex min-h-full items-end justify-center p-4 text-center sm:items-center sm:p-0"}
     (content-fn)]]])

(defn render-grid [match-id grid can-guess?]
  (let [grid-cols (case (count grid)
                    9 "grid-cols-3"
                    16 "grid-cols-4"
                    36 "grid-cols-6"
                    "grid-cols-5")]
    [:div.grid.gap-2.flex-grow
     {:class grid-cols}
     (for [i (range (count grid))]
       (render-card {:can-guess can-guess? :match/id match-id :match/grid grid} i))]))

(defn component-match-setup []
  [:div {:class "relative transform overflow-hidden rounded-lg bg-white text-left shadow-xl transition-all sm:my-8 sm:max-w-lg"}
   [:div {:class "mt-5 md:col-span-2 md:mt-0 shadow-lg rounded-lg p-6"}
    (biff/form
     {:action "/app/match"
      :hx-include "[id='grid-size']"}
     [:div {:class "space-y-6 bg-white py-5"}
      (slider {:min 3, :max 6, :step 1 :label "Grid size"})
      [:div
       [:label {:for "lang", :class "block text-sm font-medium text-gray-700"} "Word theme"]
       [:div {:class "mt-1 flex rounded-md shadow-sm"}
        [:select {:name "lang" :id "lang" :class "rounded-md text-sm border-gray-300 w-full shadow-sm"}
         [:option {:value "en"} "English words"]
         [:option {:value "el"} "Ελληνικές λέξεις"]]]]]

     [:div {:class "flex flex-row-reverse justify-between"}
      [:button {:type "submit",
                :class "rounded-md bg-blue-600 py-2 px-4 text-sm font-medium text-white shadow-sm hover:bg-blue-700 flex items-center animate-wiggle"}
       (ui/icon "play" {:class "w-6 h-6" :fill-mode :solid})

       "Play!"]
      [:div {:_ "on click hide #match-config-modal"
             :class "flex items-center text-blue-400 hover:underline hover:text-blue-500 my-2 h-full"}
       (ui/icon "arrow-left" {:class "w-6 h-6" :fill-mode :solid})
       "Back"]])]])

(defn start-match [{:keys [params] :as ctx}]
  (log/info "Starting match")
  (let [lang (get params :lang "en")
        grid-size (parse-long (get params :grid-size "5"))
        grid-area (* grid-size grid-size)
        w        (take grid-area (shuffle (words lang)))
        ctx      (-> (assoc ctx :match/cfg (get default-cfg grid-area))
                     (assoc-in [:match/cfg :words] w))
        match-id (match-create ctx)]
    {:status  303
     :headers {"location" (str "/app/match/" match-id)}}))

(defn team-info-component [& {:keys [biff/db team match-id player active-team]}]
  #_["bg-red-200" "bg-red-300" "bg-red-400"]
  #_["bg-blue-200" "bg-blue-300" "bg-blue-400"]
  [:div.rounded-lg.p-3.flex.md:flex-col.gap-2
   {:class (format "bg-%s-400 %s" (name team) (if (= team active-team) "animate-pulse-ring" ""))}
   [:div.flex-col.space-y-3
    (biff/form
     {:hidden {:team team, :role :spymaster}
      :hx-post (format "/app/match/%s/player" match-id)
      :hx-swap "none"
      :_ (str "on htmx:afterRequest"
              " add @disabled to .join-as-op")}
     [:button.w-36.rounded.p-1.enabled:hover:font-bold.disabled:opacity-60.disabled:text-gray-400
      (merge {:type "submit"
              :class (format "bg-%s-200" (name team))}
             (when (and (= :spymaster (:player/role player))
                        (= team (:player/team player)))
               {:disabled true}))
      "Join as Spymaster"])
    (biff/form
     {:hidden {:team team, :role :spy}
      :hx-post (format "/app/match/%s/player" match-id)
      :hx-swap "none"}
     [:button.w-36.rounded.p-1.enabled:hover:font-bold.disabled:opacity-60.disabled:text-gray-400
      (merge {:type "submit"

              :class (format "bg-%s-200%s" (name team) (if (= :spymaster (:player/role player)) " disabled" ""))
              :id (format "join-as-%s-operative" (name team))}
             (when (= :spymaster (:player/role player))
               {:disabled true}))
      "Join as Operative"])]
   [:div.rounded.flex-grow.text-center.truncate
    {:class (format "bg-%s-300" (name team))}
    (for [p (->> (players-list db (parse-uuid match-id))
                 (filter #(= (:player/team %) team))
                 (sort-by nickname))]
      (if (= (:xt/id p) (:xt/id player))
        (let [fid (str "nick-form-" (:xt/id p))]
          [:div
           [:span.cursor-pointer.hover:underline.font-bold
            {:_ (str "on click hide me then show #" fid)}
            (str (nickname p) " (you)")]
           [:form {:id fid
                   :style {:display "none"}
                   :hx-post (format "/app/match/%s/nickname" match-id)
                   :hx-swap "none"}
            [:div.flex.gap-1.p-1
             [:input.rounded.border-gray-300.text-xs.w-full {:type "text" :name "nick" :placeholder (nickname p)}]
             [:button.rounded.bg-gray-400.px-1.py-0.text-white.text-xs {:type "submit"} "Set"]]]])
        [:div (nickname p)]))]])

(defn observer? [player] (= :observer (:player/role player)))

(defn render-match-content
  "Returns Hiccup for the full match content area. Takes the XTDB db,
  match-id (UUID), and the player entity. Renders game-over banner,
  clue form, status bar, team panels, grid, and observer list from
  the given player's perspective."
  [db match-id player]
  (let [match (xt/entity db match-id)
        {:match/keys [grid phase status active-team guesses-remaining clue]} match
        spymaster? (= (:player/role player) :spymaster)
        game {:status status :phase phase :active-team active-team}
        player-ctx {:role (:player/role player) :team (:player/team player)}
        allowed (logic/permitted-actions game player-ctx)
        can-give-clue? (contains? allowed :give-clue)
        can-guess? (contains? allowed :guess-card)
        can-pass? (contains? allowed :pass-turn)
        grid (map #(assoc % :visible spymaster?) grid)
        match-id-str (str match-id)]
    [:div#codenames-match-content.space-y-2
     (maybe-game-over status)
     (when can-give-clue?
       [:div.p-3.rounded-lg.bg-gray-200
        (biff/form
         {:hx-post (format "/app/match/%s/clue" match-id-str)
          :hx-swap "none"}
         [:div.flex.gap-2.items-end
          [:div
           [:label.block.text-sm.font-medium.text-gray-700 "Clue word"]
           [:input.rounded.border-gray-300 {:type "text" :name "word" :required true}]]
          [:div
           [:label.block.text-sm.font-medium.text-gray-700 "Number"]
           [:input.rounded.border-gray-300 {:type "number" :name "number" :min 0 :required true :style {:width  "80px"}}]]
          [:button.rounded.bg-blue-600.px-3.py-1.text-white.text-sm {:type "submit"} "Give clue"]])])
     [:div.p-2.text-sm.text-gray-600
      (str "Current clue: " (get clue :clue/word "none")
           " (" (get clue :clue/number 0) ")")
      " | Phase: " (name phase)
      " | Turn: " (name active-team)
      (when (and (= phase :guess) (pos? guesses-remaining))
        (str " | Guesses left: " guesses-remaining))]
     (when can-pass?
       [:div
        (biff/form
         {:hx-post (format "/app/match/%s/pass" match-id-str)
          :hx-swap "none"}
         [:button.rounded.bg-gray-500.px-3.py-1.text-white.text-sm {:type "submit"} "Pass Turn"])])
     [:div.flex.flex-col.md:flex-row.gap-2
      (team-info-component :biff/db db :team :red :match-id match-id-str :player player :active-team active-team)
      (render-grid match-id-str grid can-guess?)
      (team-info-component :biff/db db :team :blue :match-id match-id-str :player player :active-team active-team)]
     [:div.flex.p-2.bg-gray-400.rounded-lg
      "Currently observing: "
      (for [{:player/keys [user] :as p} (players-list db match-id)
            :when (observer? p)]
        [:span.px-2 (:user/email user)])]]))

(defn match [{{:keys [xt/id]} :match
              :biff/keys [db] :keys [player session] :as sys}]
  (log/info "Loading match...")
  (let [player (or player (player-add sys (:uid session) {:match id}))]
    [:div.space-y-2
     {:hx-ext     "ws"
      :ws-connect (format "/app/match/%s/event" id)}
     (render-match-content db id player)]))

(defn match-event [{:codenames-clj.ui.web/keys [match-clients]
                    :keys [player match]}]
  (log/info "Loading match events")
  (let [match-id  (:xt/id match)
        player-id (:xt/id player)
        csrf-token (when (bound? #'anti-forgery/*anti-forgery-token*)
                     anti-forgery/*anti-forgery-token*)]
    {:status 101
     :headers {"upgrade" "websocket"
               "connection" "upgrade"}
     :ws {:on-connect (fn [ws]
                        (prn :connect (swap! match-clients
                                             assoc-in [match-id player-id]
                                             {:ws ws :csrf-token csrf-token})))
          :on-close (fn [ws status-code reason]
                      (prn :disconnect
                           (swap! match-clients
                                  (fn [match-clients]
                                    (let [match-clients (update match-clients match-id dissoc player-id)]
                                      (if (empty? (get match-clients match-id))
                                        (dissoc match-clients match-id)
                                        match-clients))))))}}))

(defn start-page [_]
  [:div {:class "contents"}
   (modal component-match-setup "match-config-modal")

   [:button {:_ "on click show #match-config-modal"
             :class (str "inline-block px-6 py-2 bg-blue-600 text-white leading-tight uppercase rounded shadow-md flex"
                         "hover:bg-blue-700 hover:shadow-lg"
                         "focus:bg-blue-700 focus:shadow-lg focus:outline-none focus:ring-0"
                         "active:bg-blue-800 active:shadow-lg")
             :type "button"}
    "New match"]])

(defn settings [{:keys [session biff/db] :as _ctx}]
  (let [match-ids (matches-list db (:uid session))]
    [:div.contents
     [:div {:class "flex items-center"}
      [:div {:class "text-xl px-2 my-4"} "My matches"
       [:span {:class "inline-block py-1 px-1.5 leading-none text-center whitespace-nowrap align-baseline bg-gray-600 text-white rounded-xl ml-2"} (str (count match-ids))]]]
     [:div {:class "contents"}
      (for [[m & _] match-ids
            :let [match (match-get db m)]
            :when match]
        [:div {:class "flex items-center h-full"}
         [:a {:href (str "/app/match/" m)
              :class "px-6 py-2 m-2 bg-blue-600 text-white font-medium leading-tight uppercase rounded shadow-md hover:bg-blue-700 hover:shadow-lg focus:bg-blue-700 focus:shadow-lg font-mono tracking-tighter"
              :title m}
          (:match/created-at match)]
         (when (= (:match/creator match) (:uid session))
           [:button {:id (str "btn-delete-" m)
                     :class "hover:bg-red-500 rounded p-1 m-1"
                     :hx-delete (str "/app/match/" m)}
            (ui/icon "trash" {:stroke-width "1.5", :class "w-7 h-7"})])])]]))

;; banner start

(defn left-banner []
  [:a {:href "/app"
       :class "px-3 py-3 mx-2 my-2 text-gray-600 font-medium text-xs leading-tight rounded hover:bg-blue-700 hover:shadow-lg hover:text-white focus:ring-0 text-center"}
   (ui/icon "fa-house" {:fill-mode :solid :stroke-width "1.5", :class "w-5 h-5"})])

(defn right-banner [{:keys [biff/db session]}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))]
    [:div.flex.flex-row-reverse.items-center
     (biff/form
      {:action "/auth/signout"
       :class "inline"}
      [:button {:type "submit"
                :class "px-3 py-3 mx-1 my-1 text-gray-600 text-xs rounded hover:bg-blue-700 hover:shadow-lg hover:text-white focus:ring-0"}
       (ui/icon "fa-arrow-right-from-bracket"
                {:fill-mode :solid :stroke-width "1.5", :class "w-5 h-5"})])
     [:a {:href "/app/settings"
          :class "inline-block px-3 py-3 mx-1 my-1 text-gray-600 font-medium rounded hover:bg-blue-700 hover:shadow-lg hover:text-white focus:ring-0 flex"
          :title email}
      (ui/icon "fa-user-gear" {:fill-mode :solid :stroke-width "1.5", :class "w-5 h-5"})]]))

(defn banner [ctx]
  [:div.flex.h-full.rounded-xl.justify-between.bg-orange-300.items-center
   [:a {:href "/app"
        :class "px-3 py-3 mx-2 my-2 text-gray-600 font-bold text-2xl rounded focus:ring-0 hover:text-gray-500 text-center truncate"}
    "Codenames"]
   (right-banner ctx)])

;; banner end

(defn app-wrapper
  ([content-fn ctx]
   (ui/page
    {}
    (banner ctx)
    [:.h-10]
    (content-fn ctx)))
  ([ctx] (app-wrapper start-page ctx)))

(defn on-action [{:keys [biff/db]
                  :codenames-clj.ui.web/keys [match-clients]}
                 tx]
  (doseq [[op & args] (::xt/tx-ops tx)
          :when (= op ::xt/put)
          :let [[{:action/keys [match type]}] args]
          :when type
          [player-id {:keys [ws csrf-token]}] (get @match-clients match)
          :let [player (xt/entity db player-id)
                html (rum/render-static-markup
                      (binding [anti-forgery/*anti-forgery-token* csrf-token]
                        (render-match-content db match player)))]]
    (jetty/send! ws html)))

(def plugin
  {:routes ["/app" {:middleware [anti-forgery/wrap-anti-forgery
                                 biff/wrap-anti-forgery-websockets
                                 mid/wrap-signed-in]}
            ["" {:get (partial app-wrapper start-page)}]
            ["/match" {:post start-match}]
            ["/match/:match-id" {:middleware [mid/wrap-match]}
             ["" {:get (partial app-wrapper match)
                  :delete match-delete}]
             ["/player" {:post handle-player-set-role}]
             ["/clue" {:post handle-give-clue}]
             ["/pass" {:post handle-pass-turn}]
             ["/nickname" {:post handle-set-nickname}]
             ["/card/:idx" {:post card-reveal}]
             ["/event" {:get match-event}]]
            ["/settings" {:get (partial app-wrapper settings)}]]
   :on-tx on-action})
