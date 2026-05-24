(ns codenames-clj.ui.web.schema)

(def schema
  {:user/id    :uuid
   :user       [:map {:closed true}
                [:xt/id :user/id]
                [:user/email :string]
                [:user/joined-at inst?]]
   :card       [:map
                [:card/codename :string]
                [:card/team [:enum :red :blue :civilian :assassin]]
                [:card/revealed {:optional true} :boolean]]
   :match/id   :uuid
   :match      [:map
                [:xt/id :match/id]
                [:match/grid [:vector :card]]
                [:match/creator :user/id]
                [:match/created-at inst?]
                [:match/phase [:enum :clue :guess :game-over]]
                [:match/active-team [:enum :red :blue]]
                [:match/guesses-remaining :int]
                [:match/status [:enum :playing :red :blue]]
                [:match/clue {:optional true} [:maybe [:map
                                                       [:clue/word :string]
                                                       [:clue/number :int]]]]]
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
                               :codenames/clue-given :codenames/pass-turn
                               :codenames/nickname-set
                               :codenames/player-added]]
                [:action/data  {:optional true} [:map]]]
   :mem/id     :uuid
   :membership [:map {:closed true}
                [:xt/id :mem/id]
                [:mem/player :player/id]
                [:mem/role [:enum :spymaster :spy :observer]]
                [:mem/team {:optional true} [:enum :blue :red]]]})

(def plugin
  {:schema schema})
