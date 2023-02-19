(ns codenames-clj.ui.web.feat.app
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
  (let [match-id (random-uuid)]
    (biff/submit-tx
     sys
     [{:db/doc-type      :match
       :xt/id            match-id
       :match/grid       (logic/grid cfg)
       :match/creator    (:uid session)
       :match/created-at :db/now}])
    (str match-id)))

(defn match-delete [{:keys [path-params] :as sys}]
  (let [match-id (-> path-params :match-id)]
    (biff/submit-tx
     sys
     [{:db/op :delete
       :xt/id (parse-uuid match-id)}])
    {:status 200
     :body match-id}))

(defn matches-list [db user-id]
  (xt/q db
        '{:find [?match-id]
          :in [?user-id]
          :where [[?match :match/creator ?user-id]
                  [?match :xt/id ?match-id]]}
        user-id))

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

(defn handle-player-set-role [{:keys [session params biff/db match] :as req}]
  (let [user-id (:uid session)
        params  (-> params
                    (update :team keyword)
                    (update :role keyword)
                    (select-keys [:team :role])
                    (assoc :match (:xt/id match)))
        player  (player-get db user-id (:xt/id match))]
    (if player
      (player-update req player params)
      (player-add req user-id params))
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
    (> (count codename) 8) "text-xs"
    (> (count codename) 7) "text-sm"
    :else "text-base"))

(defn render-card [{:keys [disabled] :match/keys [grid id] :as _match} idx]
  (let [{:card/keys [codename]
         :keys [team revealed visible assassin]} (nth grid idx)
        role    (cond
                  (not (or visible revealed)) :hidden
                  assassin :assassin
                  (not team) :civilian
                  :else team)
        status  (cond
                  revealed :revealed
                  visible :visible
                  :else :normal)
        classes (format "py-3 px-1 rounded w-full h-full text-white %s %s %s truncate sm:text-base"
                        (get-in role-classes [role status])
                        (get status-classes status)
                        (font-size-class codename))]
    [:button {:hx-get (format "/app/match/%s/card/%s" id idx)
              :hx-swap "outerHTML"
              :id (str "codenames-card-" idx)
              :type "submit"
              :title (:codename (nth grid idx))
              :disabled (or disabled (boolean revealed))
              :class classes} codename]))

(defn grid [db match-id]
  (-> (biff/lookup db :xt/id match-id)
      :match/grid))

(defn card-info [db {:keys [match-id card-idx]}]
  (-> (grid db match-id)
      (nth card-idx)))

(defn card-reveal [{:keys [session path-params biff/db player] :as req}]
  (let [match-id (parse-uuid (:match-id path-params))
        card-idx (parse-long (:idx path-params))
        g        (-> (grid db match-id) (logic/reveal card-idx))]
    (biff/submit-tx req
                    [{:db/doc-type   :match
                      :db/op         :update
                      :xt/id         match-id
                      :match/grid    g
                      :match/creator (:uid session)}
                     {:db/doc-type  :action
                      :db/op        :put
                      :xt/id        (random-uuid)
                      :action/actor (:xt/id player)
                      :action/match match-id
                      :action/type  :codenames/card-revealed}])
    (render-card {:match/grid g :match/id match-id} card-idx)))

(defn render-grid [match-id grid read-only]
  (let [grid-cols (case (count grid)
                    9 "grid-cols-3"
                    16 "grid-cols-4"
                    36 "grid-cols-6"
                    "grid-cols-5")]
    [:div.grid.gap-2.flex-grow
     {:id "codenames-board-area"
      :class grid-cols}
     (for [i (range (count grid))]
       (render-card {:disabled read-only :match/id match-id :match/grid grid} i))]))

(defn component-range [{:keys [min max step label id]}]
  (let [id     (or id (-> label str/lower-case (str/replace #" " "-")))
        values (range min (inc max) step)]
    [:div
     [:label {:for id, :class "block text-sm font-medium text-gray-700"} label]
     [:input {:type "range", :id id, :name id, :class "w-full mt-1 flex items-center",
              :min (str min), :max (str max), :step (str step)}]
     [:div {:class "flex justify-between ml-[7px] mr-[2px] text-sm text-gray-500"}
       (for [v values] [:div (str (* v v))])]]))

(defn component-match-setup []
  [:div {:class "relative transform overflow-hidden rounded-lg bg-white text-left shadow-xl transition-all sm:my-8 sm:max-w-lg"}
   [:div {:class "mt-5 md:col-span-2 md:mt-0 shadow-lg rounded-lg p-6"}
    (biff/form
     {:action "/app/match"
      :hx-include "[id='grid-size']"}
     [:div {:class "space-y-6 bg-white py-5"}
      (component-range {:min 3, :max 6, :step 1 :label "Grid size"})
      [:div
       [:label {:for "lang", :class "block text-sm font-medium text-gray-700"} "Word theme"]
       [:div {:class "mt-1 flex rounded-md shadow-sm"}
        [:select {:name "lang" :id "lang" :class "rounded-md text-sm border-gray-300 w-full shadow-sm"}
         [:option {:value "en"} "English words"]
         [:option {:value "el"} "Ελληνικές λέξεις"]]]]]

     [:div {:class "flex flex-row-reverse justify-between hover:wiggle"}
      [:button {:type "submit",
                :class "rounded-md bg-blue-600 py-2 px-4 text-sm font-medium text-white shadow-sm hover:bg-blue-700 flex items-center wiggle"}
       (ui/icon "play" {:class "w-6 h-6" :fill-mode :solid})

       "Play!"]
      [:div {:_ "on click hide #match-config-modal"
             :class "flex items-center text-blue-400 hover:underline hover:text-blue-500 my-2 h-full"}
       (ui/icon "arrow-left" {:class "w-6 h-6" :fill-mode :solid})
       "Back"]])]])

(defn component-modal [content-fn id]
  [:div {:id id
         :style {:display "none"}
         :class "relative z-10"
         :aria-hidden true}
   [:div {:class "fixed inset-0 bg-gray-500 bg-opacity-75 transition-opacity"}]
   [:div {:class "fixed inset-0 z-10 overflow-y-auto"}
    [:div {:class "flex min-h-full items-end justify-center p-4 text-center sm:items-center sm:p-0"}
     (content-fn)]]])

(defn start-match [{:keys [params] :as req}]
  (log/info "Starting match")
  (let [lang (get params :lang "en")
        grid-size (parse-long (get params :grid-size "5"))
        grid-area (* grid-size grid-size)
        w        (take grid-area (shuffle (words lang)))
        req      (-> (assoc req :match/cfg (get default-cfg grid-area))
                     (assoc-in [:match/cfg :words] w))
        match-id (match-create req)]
    {:status  303
     :headers {"location" (str "/app/match/" match-id)}}))

(defn team-info-component [{:keys [biff/db team match-id player]}]
  #_["bg-red-200" "bg-red-300" "bg-red-400"]
  #_["bg-blue-200" "bg-blue-300" "bg-blue-400"]
  [:div.rounded-lg.p-3.flex.md:flex-col.gap-2
   {:class (format "bg-%s-400" (name team))}
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
      [:div (nickname p)])]])

(defn observer? [player] (= :observer (:player/role player)))

(defn match [{{:keys [xt/id match/grid]} :match
              :keys [biff/db player session] :as sys}]
  (log/info "Loading match...")
  (let [player (or player (player-add sys (:uid session) {:match id}))
        match-id (str id)
        grid (map #(assoc % :visible (= (:player/role player) :spymaster)) grid)]
    [:div.space-y-2
     {:hx-ext     "ws"
      :ws-connect (format "/app/match/%s/event" match-id)}
     [:div.flex.flex-col.md:flex-row.gap-2
      (team-info-component {:biff/db db :team :red :match-id match-id :player player})
      (render-grid match-id grid (observer? player))
      (team-info-component {:biff/db db :team :blue :match-id match-id :player player})]
     [:div.flex.p-2.bg-gray-400.rounded-lg
      "Currently observing: "
      (for [{:player/keys [user] :as p} (players-list db id)
            :when (observer? p)]
        [:span.px-2 (:user/email user)])]]))

(defn match-event [{:codenames-clj.ui.web/keys [match-clients]
                    :keys [player match]}]
  (log/info "Loading match events")
  (let [match-id  (:xt/id match)
        player-id (:xt/id player)]
    {:status 101
     :headers {"upgrade" "websocket"
               "connection" "upgrade"}
     :ws {:on-connect (fn [ws]
                        (prn :connect (swap! match-clients
                                             assoc-in [match-id player-id] ws)))
          :on-close (fn [ws status-code reason]
                      (let [x :wip
                            ;;old-client (-> @match-clients match-id player-id)
                            ]
                        (prn :disconnect
                             (swap! match-clients
                                    update match-id dissoc player-id))))}}))

(defn start-page [_]
  [:div {:class "contents"}
   (component-modal component-match-setup "match-config-modal")

   [:button {:_ "on click show #match-config-modal"
             :class (str "inline-block px-6 py-2 bg-blue-600 text-white leading-tight uppercase rounded shadow-md flex"
                         "hover:bg-blue-700 hover:shadow-lg"
                         "focus:bg-blue-700 focus:shadow-lg focus:outline-none focus:ring-0"
                         "active:bg-blue-800 active:shadow-lg")
             :data-bs-toggle "modal",
             :data-bs-target "#match-config-modal"}
    "New match"]])

(defn settings [{:keys [session biff/db] :as _req}]
  (let [match-ids (matches-list db (:uid session))]
    [:div.contents
      [:div {:class "flex items-center"}
       [:div {:class "text-xl px-2 my-4"} "My matches"
        [:span {:class "inline-block py-1 px-1.5 leading-none text-center whitespace-nowrap align-baseline bg-gray-600 text-white rounded-xl ml-2"} (str (count match-ids))]]]
      [:div {:class "contents"}
       (for [[m & _] match-ids]
           [:div {:class "flex items-center h-full"}
            [:a {:href (str "/app/match/" m)
                 :class "px-6 py-2 m-2 bg-blue-600 text-white font-medium leading-tight uppercase rounded shadow-md hover:bg-blue-700 hover:shadow-lg focus:bg-blue-700 focus:shadow-lg font-mono tracking-tighter"
                 :title m}
             (-> (match-get db m) :match/created-at)]
            [:button {:id (str "btn-delete-" m)
                      :class "hover:bg-red-500 rounded p-1 m-1"
                      :hx-delete (str "/app/match/" m)}
             (ui/icon "trash" {:stroke-width "1.5", :class "w-7 h-7"})]])]]))

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

(defn banner [req]
  [:div.flex.h-full.rounded-xl.justify-between.bg-orange-300.items-center
   [:a {:href "/app"
        :class "px-3 py-3 mx-2 my-2 text-gray-600 font-bold text-2xl rounded focus:ring-0 hover:text-gray-500 text-center truncate"}
    "Codenames"]
   (right-banner req)])

;; banner end

(defn app-wrapper
  ([content-fn req]
   (ui/page
    {}
    (banner req)
    [:.h-10]
    (content-fn req)))
  ([req] (app-wrapper start-page req)))

(defn on-action [{:keys [biff/db]
                  :codenames-clj.ui.web/keys [match-clients]}
                 tx]
  (doseq [[op & args] (::xt/tx-ops tx)
          :when (= op ::xt/put)
          :let [[{:action/keys [match type actor]}] args
                {:match/keys [grid]} (xt/entity db match)
                player (xt/entity db actor)
                grid (map #(assoc % :visible (= (:player/role player) :spymaster)) grid)
                html (rum/render-static-markup
                      (render-grid match grid (observer? player)))]
          :when type
          [_ c] (get @match-clients match)]
    (jetty/send! c html)))

(def features
  {:routes ["/app" {:middleware [anti-forgery/wrap-anti-forgery
                                 biff/wrap-anti-forgery-websockets
                                 mid/wrap-signed-in]}
            ["" {:get (partial app-wrapper start-page)}]
            ["/match" {:post start-match}]
            ["/match/:match-id" {:middleware [mid/wrap-match]}
             ["" {:get (partial app-wrapper match)
                  :delete match-delete}]
             ["/player" {:post handle-player-set-role}]
             ["/card/:idx" {:get card-reveal}]
             ["/event" {:get match-event}]]
            ["/settings" {:get (partial app-wrapper settings)}]]
   :on-tx on-action})
