(ns codenames-clj.ui.web
  (:require [com.biffweb :as biff]
            [codenames-clj.ui.web.feat.app :as app]
            [codenames-clj.ui.web.feat.auth :as auth]
            [codenames-clj.ui.web.feat.home :as home]
            [codenames-clj.ui.web.feat.worker :as worker]
            [codenames-clj.ui.web.schema :refer [malli-opts]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :as test]
            [clojure.tools.logging :as log]
            [ring.middleware.anti-forgery :as anti-forgery]
            [nrepl.cmdline :as nrepl-cmd]))

(def features
  [app/features
   auth/features
   home/features
   worker/features])

(def routes [["" {:middleware [biff/wrap-site-defaults]}
              (keep :routes features)]
             ["" {:middleware [biff/wrap-api-defaults]}
              (keep :api-routes features)]])

(def handler (-> (biff/reitit-handler {:routes routes})
                 biff/wrap-base-defaults))

(def static-pages (apply biff/safe-merge (map :static features)))

(defn generate-assets! [sys]
  (biff/export-rum static-pages "target/resources/public")
  (biff/delete-old-files {:dir "target/resources/public"
                          :exts [".html"]}))

(defn on-save [sys]
  (biff/add-libs)
  (biff/eval-files! sys)
  (generate-assets! sys)
  (test/run-all-tests #"codenames-clj.ui.web.test.*"))

(def components
  [biff/use-config
   biff/use-secrets
   biff/use-xt
   biff/use-queues
   biff/use-tx-listener
   biff/use-wrap-ctx
   biff/use-jetty
   biff/use-chime
   (biff/use-when
    :codenames-clj.ui.web/enable-beholder
    biff/use-beholder)])

(defn start []
  (biff/start-system
   {:codenames-clj.ui.web/match-clients (atom {})
    :biff/features #'features
    :biff/after-refresh `start
    :biff/handler #'handler
    :biff/malli-opts #'malli-opts
    :biff.beholder/on-save #'on-save
    :biff.xtdb/tx-fns biff/tx-fns
    :biff/components components})
  (generate-assets! @biff/system)
  (log/info "Go to" (:biff/base-url @biff/system)))

(defn -main [& args]
  (start)
  (apply nrepl-cmd/-main args))
