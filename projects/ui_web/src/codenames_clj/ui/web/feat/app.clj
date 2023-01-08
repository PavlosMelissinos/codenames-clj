(ns codenames-clj.ui.web.feat.app
  (:require [com.biffweb :as biff :refer [q]]
            [codenames-clj.ui.web.middleware :as mid]
            [codenames-clj.ui.web.ui :as ui]
            [codenames-clj.core :as logic]
            [codenames-clj.config :as-alias c]
            [codenames-clj.ui :as ui-utils]
            [codenames-clj.ui.palette :as palette]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [rum.core :as rum]
            [xtdb.api :as xt]
            [ring.adapter.jetty9 :as jetty]
            [ring.middleware.anti-forgery :as anti-forgery]))


;; header

(defn header [db session]
  (let [{:user/keys [email]} (xt/entity db (:uid session))]
    [:div "Signed in as " email ". "
     (biff/form
      {:action "/auth/signout"
       :class "inline"}
      [:button.text-blue-500.hover:text-blue-800 {:type "submit"} "Sign out"])
     "."]))

;; match

(defn words [lang]
  (-> (format "words.%s.txt" lang)
      io/resource
      slurp
      str/split-lines))

(def default-cfg
  {::c/rows      5
   ::c/cols      5
   ::c/civilians 7
   ::c/assassins 1})

(defn match-get [db match-id]
  (biff/lookup db :xt/id (parse-uuid match-id)))

(defn match-create [{:keys [match/cfg session] :as sys}]
  (let [match-id (random-uuid)]
    (biff/submit-tx
     sys
     [{:db/doc-type   :match
       :xt/id         match-id
       :match/grid    (logic/grid cfg)
       :match/creator (:uid session)}])
    match-id))

(defn card-style [{:keys [revealed visible] :as _card}]
  {:assassin ""
   :hidden   nil
   :blue     {:-fx-background-color (:card-blue-team-primary palette/color-palette)
              :-fx-text-fill (:card-blue-team-primary-alt palette/color-palette)
              :-fx-opacity (if (and (not revealed) visible) 0.5 1)}
   :red      {:-fx-background-color (:card-red-team-primary palette/color-palette)
              :-fx-text-fill (:card-red-team-primary-alt palette/color-palette)
              :-fx-opacity (if (and (not revealed) visible) 0.5 1)}
   :civilian {:-fx-background-color (:card-civilian-primary palette/color-palette)
              :-fx-text-fill (:card-civilian-primary-alt palette/color-palette)
              :-fx-opacity (if (and (not revealed) visible) 0.5 1)}})

(def role-classes
  {:hidden {:normal "bg-teal-600 hover:bg-teal-800"
            :revealed "bg-teal-600 text-black"}
   :assassin {:normal "bg-slate-600 hover:bg-slate-800"
              :revealed "bg-slate-600"}
   :civilian {:normal "bg-amber-600 hover:bg-amber-800"
              :revealed "bg-amber-600 text-black"}
   :blue {:normal "bg-blue-600 hover:bg-blue-800"
          :revealed "bg-blue-600 text-black"}
   :red {:normal "bg-red-600 hover:bg-red-800"
         :revealed "bg-red-600 text-black"}})

(def status-classes
  {:normal   "hover:shadow-lg active:shadow-lg"
   :revealed "opacity-50"})

(defn font-size-class [codename]
  (cond
    (> (count codename) 11) "text-xs"
    (> (count codename) 9) "text-sm"
    :else ""))

(defn render-card [{:match/keys [grid id] :as _match} idx]
  (let [{:keys [team revealed visible assassin card/codename]} (get grid idx)
        role    (cond
                  (not (or visible revealed)) :hidden
                  assassin :assassin
                  (not team) :civilian
                  :else team)
        status  (if revealed :revealed :normal)
        classes (format "py-2 px-4 rounded w-full h-full text-white %s %s %s"
                        (get-in role-classes [role status])
                        (get status-classes status)
                        (font-size-class codename))]
    [:button {:hx-get (format "/app/match/%s/card/%s" id idx)
              :hx-swap "outerHTML"
              :type "submit"
              :title (:codename (get grid idx))
              :disabled (boolean revealed)
              :class classes} codename]))

(defn grid [db match-id]
  (-> (biff/lookup db :xt/id match-id)
      :match/grid))

(defn card-info [db {:keys [match-id card-idx]}]
  (-> (biff/lookup db :xt/id match-id)
      :match/grid
      (nth card-idx)))

(defn card-reveal [{:keys [session path-params biff/db] :as req}]
  (let [match-id (parse-uuid (:match-id path-params))
        card-idx (parse-long (:idx path-params))
        g        (-> (grid db match-id) (logic/reveal card-idx))]
    (biff/submit-tx req
                    [{:db/doc-type   :match
                      :db/op         :update
                      :xt/id         match-id
                      :match/grid    g
                      :match/creator (:uid session)}])
    (render-card {:match/grid g :match/id match-id} card-idx)))

(defn render-grid [match-id grid]
  (for [i (range (count grid))]
    (render-card {:match/id match-id :match/grid grid} i)))

(defn component-range [{:keys [min max step label id]}]
  (let [id     (or id (-> label str/lower-case (str/replace #" " "-")))
        values (range min (inc max) step)]
    [:div
     [:label {:for id, :class "block text-sm font-medium text-gray-700"} label]
     [:input {:type "range", :id id, :name id, :class "w-full mt-1 flex items-center",
              :min (str min), :max (str max), :step (str step)}]
     `[:div {:class "flex justify-between ml-[7px] mr-[2px] text-sm text-gray-500"}
       ~@(for [v values] [:div (str (* v v))])]]))

(defn component-match-setup []
  [:div {:class "relative transform overflow-hidden rounded-lg bg-white text-left shadow-xl transition-all sm:my-8 sm:w-full sm:max-w-lg" #_"relative transform mx-auto max-w-4xl py-6 sm:px-6 rounded-lg"}
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

     [:div #_{:class "bg-gray-50 px-4 py-3 text-right sm:px-6"}
      {:class "flex space-x-2 justify-right"}
      [:div {:_ "on click hide #match-config-modal"
             :class "btn inline-flex justify-center rounded-md border border-transparent bg-indigo-600 py-2 px-4 text-sm font-medium text-white shadow-sm hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"}
       [:i {:class "fa-solid fa-circle-chevron-left"}]
       #_"Back"]
      [:button {:type "submit", :class "inline-flex justify-center rounded-md border border-transparent bg-indigo-600 py-2 px-4 text-sm font-medium text-white shadow-sm hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"}
       "Play!"]])]])

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
        w        (take (* grid-size grid-size) (shuffle (words lang)))
        req      (-> (assoc req :match/cfg default-cfg)
                     (assoc-in [:match/cfg :words] w))
        match-id (match-create req)]
    {:status  303
     :headers {"location" (str "/app/match/" match-id)}}))

(defn match [{:keys [path-params biff/db] :as _req}]
  (let [{:keys [match-id]} path-params
        grid (:match/grid (biff/lookup db :xt/id (parse-uuid match-id)))
        grid-cols (case (count grid)
                    9 "grid-cols-3"
                    16 "grid-cols-4"
                    36 "grid-cols-6"
                    "grid-cols-5")]
    [:div {:id "codenames-board-area" :class (str "grid border-black gap-2 " grid-cols)}
     (render-grid match-id grid)]))

(defn start-page [_]
  [:div {:class "contents"}
   (component-modal component-match-setup "match-config-modal")

   [:button {:_ "on click show #match-config-modal"
             :class "inline-block px-6 py-2 bg-blue-600 text-white font-medium text-xs leading-tight uppercase rounded shadow-md hover:bg-blue-700 hover:shadow-lg focus:bg-blue-700 focus:shadow-lg focus:outline-none focus:ring-0 active:bg-blue-800 active:shadow-lg transition duration-150 ease-in-out flex"
             :data-bs-toggle "modal",
             :data-bs-target "#match-config-modal"}
    "New match"]])

(defn app-wrapper
  ([req] (app-wrapper start-page req))
  ([content-fn {:keys [session biff/db] :as req}]
   (ui/page
    {}
    (header db session)
    [:.h-6]
    [:div.text-2xl.text-center "Codenames"]

    (content-fn req))))

(def features
  {:routes ["/app" {:middleware [anti-forgery/wrap-anti-forgery
                                 biff/wrap-anti-forgery-websockets
                                 mid/wrap-signed-in]}
            ["" {:get (partial app-wrapper start-page)}]
            ["/match/:match-id" {:get (partial app-wrapper match)}]
            ["/match/:match-id/card/:idx" {:get card-reveal}]
            ["/match" {:post start-match
                       :get start-match}]]})
