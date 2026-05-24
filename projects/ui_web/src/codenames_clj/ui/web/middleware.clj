(ns codenames-clj.ui.web.middleware
  (:require [com.biffweb :as biff]
            [xtdb.api :as xt]))

(defn wrap-redirect-signed-in [handler]
  (fn [{:keys [session] :as ctx}]
    (if (some? (:uid session))
      {:status 303
       :headers {"location" "/app"}}
      (handler ctx))))

(defn wrap-signed-in [handler]
  (fn [{:keys [biff/db session] :as ctx}]
    (if-some [user (xt/entity db (:uid session))]
      (handler (assoc ctx :user user))
      {:status 303
       :headers {"location" "/"}})))

(defn wrap-match [handler]
  (fn [{:keys [biff/db session user path-params] :as ctx}]
    (let [match-id (parse-uuid (:match-id path-params))
          match  (biff/lookup db :xt/id match-id)
          player (biff/lookup db
                              :player/user (:xt/id user)
                              :player/match match-id)]
      (if (some? (:uid session))
        (handler (assoc ctx :player player :match match))
        {:status 303
         :headers {"location" "/"}}))))
