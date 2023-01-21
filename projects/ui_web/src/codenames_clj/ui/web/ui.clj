(ns codenames-clj.ui.web.ui
  (:require [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [com.biffweb :as biff]
            [ring.middleware.anti-forgery :as csrf]))

(def svg-data
  {"trash" {:viewbox "0 0 24 24" :path "M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0"}
   "arrow-left" {:viewbox "0 0 448 512"
                 :path "M9.4 233.4c-12.5 12.5-12.5 32.8 0 45.3l160 160c12.5 12.5 32.8 12.5 45.3 0s12.5-32.8 0-45.3L109.2 288 416 288c17.7 0 32-14.3 32-32s-14.3-32-32-32l-306.7 0L214.6 118.6c12.5-12.5 12.5-32.8 0-45.3s-32.8-12.5-45.3 0l-160 160z"}
   "play" {:viewbox "0 0 24 24", :path "M4.5 5.653c0-1.426 1.529-2.33 2.779-1.643l11.54 6.348c1.295.712 1.295 2.573 0 3.285L7.28 19.991c-1.25.687-2.779-.217-2.779-1.643V5.653z"}})

(defn icon [k & [{:keys [fill-mode] :as opts}]]
  (let [{:keys [view-box path]} (get svg-data k)]
    [:svg.flex-shrink-0.inline
       (merge {:xmlns "http://www.w3.org/2000/svg"
               :viewBox view-box}
              opts)
       [:path (merge
               {:d path}
               (case fill-mode
                 :solid   {:fill "currentColor"}
                 :outline {:fill "none"
                           :stroke "currentColor"}
                 {:fill "none"
                  :stroke "currentColor"}))]]))

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
     (when (bound? #'csrf/*anti-forgery-token*)
       {:hx-headers (cheshire/generate-string
                     {:x-csrf-token csrf/*anti-forgery-token*})})
     body]
    [:div {:class "grow-[2]"}]]))
