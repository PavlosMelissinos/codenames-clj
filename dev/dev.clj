(ns dev
  (:require [cljfx.api :as fx]
            [clodenames2.ui :as ui]
            [clodenames2.core :as core]))

(defonce renderer (atom nil)) ;; maybe this isn't necessary, we'll see

(defn go
  ([] (go (core/load-config)))
  ([m]
   (try
     (fx/unmount-renderer ui/*state @renderer)
     (catch Throwable _ex
       nil))
   (reset! renderer ui/renderer)
   (reset! ui/*state (core/init m))
   (fx/mount-renderer ui/*state @renderer)
   @ui/*state))

(defn halt! []
  (when @renderer
    (fx/unmount-renderer ui/*state @renderer))
  (reset! ui/*state nil)
  (reset! renderer nil))

(defn reset [{:keys [cfg]}]
  (println "Halting old state")
  (halt!)
  (println "Starting new state")
  (go cfg))

(comment
  (def sys (go))
  (reset sys)
  (halt!)
  )
