(ns codenames-clj.ui.web.middleware
  (:require [com.biffweb :as biff]
            [xtdb.api :as xt]))

(defn wrap-redirect-signed-in [handler]
  (fn [{:keys [session] :as req}]
    (if (some? (:uid session))
      {:status 303
       :headers {"location" "/app"}}
      (handler req))))

(defn wrap-signed-in [handler]
  (fn [{:keys [biff/db session] :as req}]
    (if-some [user (xt/entity db (:uid session))]
      (handler (assoc req :user user))
      {:status 303
       :headers {"location" "/"}})))

(defn wrap-match [handler]
  (fn [{:keys [biff/db session user path-params] :as req}]
    (let [match-id (parse-uuid (:match-id path-params))
          match  (biff/lookup db :xt/id match-id)
          player (biff/lookup db
                              :player/user (:xt/id user)
                              :player/match match-id)]
      (if (some? (:uid session))
        (handler (assoc req :player player :match match))
        {:status 303
         :headers {"location" "/"}}))))
