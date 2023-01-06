(ns codenames-clj.ui.web.ui
  (:require [clojure.java.io :as io]
            [com.biffweb :as biff]))

(defn css-path []
  (if-some [f (io/file (io/resource "public/css/main.css"))]
    (str "/css/main.css?t=" (.lastModified f))
    "/css/main.css"))

(defn base [opts & body]
  (apply
   biff/base-html
   (-> opts
       (merge #:base{:title "Codenames"
                     :lang "en-US"
                     :description "Play Codenames with your friends"
                     :image "/img/logo.png"
                     })
       (update :base/head (fn [head]
                            (concat [[:link {:rel "stylesheet" :href (css-path)}]
                                     [:script {:src "https://unpkg.com/htmx.org@1.6.1"}]
                                     [:script {:src "https://unpkg.com/hyperscript.org@0.9.3"}]]
                                    head))))
   body))

(defn page [opts & body]
  (base
   opts
   [:.bg-orange-50.flex.flex-col.flex-grow
    [:.grow]
    [:.p-3.mx-auto.max-w-screen-sm.w-full
     body]
    [:div {:class "grow-[2]"}]]))
