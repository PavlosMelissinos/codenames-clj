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

(defn render-card [{:match/keys [grid id] :as _match} idx]
  (let [{:keys [team revealed visible assassin card/codename]} (get grid idx)
        role           (cond
                         (not (or visible revealed)) :hidden
                         assassin :assassin
                         (not team) :civilian
                         :else team)
        status         (if revealed :revealed :normal)
        classes (format "py-2 px-4 rounded w-full h-full text-white %s %s"
                        (get-in role-classes [role status])
                        (get status-classes status))]
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

(defn words [lang]
  (-> (format "words.%s.txt" lang)
      io/resource
      slurp
      str/split-lines))

(defn start-match [{:keys [params] :as req}]
  (log/info "Starting match")
  (let [lang     (or (:lang params) "el")
        w        (take 25 (shuffle (words lang)))
        req      (-> (assoc req :match/cfg default-cfg)
                     (assoc-in [:match/cfg :words] w))
        match-id (match-create req)]
    {:status 303
     :headers {"location" (str "/app/match/" match-id)}}))

(defn match [{:keys [path-params biff/db session] :as _req}]
  (let [{:keys [match-id]} path-params
        grid (:match/grid (biff/lookup db :xt/id (parse-uuid match-id)))]
    (ui/page
     {}
     (header db session)
     ;;[:.h-6]
     [:div.text-2xl.text-center "Codenames"]
     [:div {:id "codenames-board-area" :class "grid border-black grid-cols-5 gap-2"}
      (render-grid match-id grid)])))

(defn app [{:keys [session biff/db] :as _req}]
  (ui/page
   {}
   (header db session)
   [:.h-6]
   [:div.text-2xl.text-center "Codenames"]

   [:div {:class "flex gap-2"}
    [:label {:for "lang"} "Select match language"]
    [:select {:name "lang" :id "lang"}
     [:option {:value "en"} "English"]
     [:option {:value "el"} "Ελληνικά"]]]
   [:button {:class "inline-block px-6 py-2 bg-blue-600 text-white font-medium text-xs leading-tight uppercase rounded shadow-md hover:bg-blue-700 hover:shadow-lg focus:bg-blue-700 focus:shadow-lg focus:outline-none focus:ring-0 active:bg-blue-800 active:shadow-lg transition duration-150 ease-in-out flex"
             :type "target"
             :hx-get "/app/match"
             :hx-trigger "click"
             :hx-target "#codenames-board-area"
             :hx-include "[id='lang']"}
    "New match"]
   #_[:form
      {:method "get"}
      [:div {:class "flex gap-2"}
       [:select {:name "lang"}
        [:option {:value "en" :label "English"}]
        [:option {:value "el" :label "Ελληνικά"}]]
       [:button {:class "inline-block px-6 py-2 bg-blue-600 text-white font-medium text-xs leading-tight uppercase rounded shadow-md hover:bg-blue-700 hover:shadow-lg focus:bg-blue-700 focus:shadow-lg focus:outline-none focus:ring-0 active:bg-blue-800 active:shadow-lg transition duration-150 ease-in-out flex"
                 :type "target"
                 ;;:type "button"
                 :hx-get "/app/match"
                 :hx-trigger "click"
                 :hx-target "#codenames-board-area"}
        "New match"]]]
   [:div {:id "codenames-board-area"}]))

(def features
  {:routes ["/app" {:middleware [anti-forgery/wrap-anti-forgery
                                 biff/wrap-anti-forgery-websockets
                                 mid/wrap-signed-in]}
            ["" {:get app}]
            ["/match/:match-id" {:get match}]
            ["/match/:match-id/card/:idx" {:get card-reveal}]
            ["/match" {:post start-match
                       :get start-match}]]})
