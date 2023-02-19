(ns codenames-clj.ui.web.schema
  (:require [malli.core :as malc]
            [malli.registry :as malr]))

(def schema
  {:user/id    :uuid
   :user       [:map {:closed true}
                [:xt/id :user/id]
                [:user/email :string]
                [:user/joined-at inst?]]
   :card       [:map
                [:card/codename :string]
                [:team {:optional true} [:enum :blue :red]]
                [:assassin {:optional true} :boolean]
                [:revealed {:optional true} :boolean]]
   :match/id   :uuid
   :match      [:map
                [:xt/id :match/id]
                [:match/grid [:vector :card]]
                [:match/creator :user/id]
                [:match/created-at inst?]]
   :player/id  :uuid
   :player     [:map
                [:xt/id :player/id]
                [:player/user :user/id]
                [:player/match :match/id]
                [:player/nick {:optional true} :string]
                [:player/role [:enum {:default :observer} :spymaster :spy :observer]]
                [:player/team {:optional true} [:enum :blue :red]]]
   :action/id  :uuid
   :action     [:map
                [:xt/id :action/id]
                [:action/actor :player/id]
                [:action/match :match/id]
                [:action/type [:enum
                               :codenames/card-revealed :codenames/team-role-selected
                               :codenames/player-added]]
                [:action/data  {:optional true} [:map]]]
   :mem/id     :uuid
   :membership [:map {:closed true}
                [:xt/id :mem/id]
                [:mem/player :player/id]
                [:mem/role [:enum :spymaster :spy :observer]]
                [:mem/team {:optional true} [:enum :blue :red]]]})

(def malli-opts {:registry (malr/composite-registry malc/default-registry schema)})


(comment
  (malc/validate [:vector int?] [1 2 3])
  (malc/validate [:enum :spymaster :spy :observer] :spymaster)
  ,)
