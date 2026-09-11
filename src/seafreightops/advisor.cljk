(ns seafreightops.advisor
  "SeaFreightAdvisor -- the *contained intelligence node* for the
  ISIC-5012 'Sea and coastal freight water transport' (cargo/container
  vessel and coastal-shipping) PORT/LOGISTICS SCHEDULING coordination
  actor.

  It drafts exactly four kinds of back-office proposal from a closed
  allowlist: cargo/manifest/voyage record logging, port berth/voyage
  scheduling coordination, vessel-maintenance procurement coordination,
  and cargo-safety/seaworthiness-concern flagging. CRITICAL: it is a
  smart-but-untrusted advisor. It returns a *proposal* (with a rationale
  + the fields it cited), never a committed record and NEVER a direct
  actuation -- every proposal's `:effect` is always `:propose`. Every
  output is censored downstream by `seafreightops.governor` before
  anything touches the SSoT.

  This advisor NEVER drafts a direct vessel-navigation/dispatch/
  rerouting action, a vessel-seaworthiness-clearance finalization, a
  cargo-load-safety-clearance finalization, or any action overriding a
  captain's/harbor-master's safety judgment -- those are permanently
  out of scope for this actor, not merely un-implemented.
  `seafreightops.governor`'s `scope-exclusion-violations` independently
  re-scans every proposal for exactly this failure mode (a compromised
  or confused advisor drifting into scope it must never touch) and
  HARD-holds it, regardless of confidence or op.

  Like every sibling actor's advisor, this is a deterministic mock so the
  actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:op         kw             ; echoes the request op
     :vessel-id  str
     :summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the scope-exclusion gate
     :cites      [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- never a direct actuation
     :value      map            ; the draft payload a human/system would review
     :confidence 0..1}")

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-shipment-record
  "Draft a cargo/manifest/voyage record log entry. Pure logging of
  observed shipment data (cargo manifest lines, voyage waypoints, hold
  temperature/humidity readings) -- never a cargo-load-safety or
  seaworthiness decision."
  [_db {:keys [vessel-id patch]}]
  {:op         :log-shipment-record
   :vessel-id  vessel-id
   :summary    (str vessel-id " の貨物/マニフェスト/航海記録を記録: " (pr-str (keys patch)))
   :rationale  "貨物マニフェスト・航海記録・積載状況の観察記録のみ。積付安全や耐空性の判断は含まない。"
   :cites      [vessel-id]
   :effect     :propose
   :value      (merge {:vessel-id vessel-id} patch)
   :confidence 0.93})

(defn- propose-berth-operation
  "Draft a port berth/voyage scheduling proposal (a berth-window/
  voyage-slot entry, never a navigation or dispatch action)."
  [_db {:keys [vessel-id patch]}]
  {:op         :schedule-berth-operation
   :vessel-id  vessel-id
   :summary    (str vessel-id " の岸壁/航海スケジュールを提案: " (pr-str (keys patch)))
   :rationale  "港湾バース割当・入出港スケジュール調整提案のみ。航行の最終判断は船長/港湾長が行う。"
   :cites      [vessel-id]
   :effect     :propose
   :value      (merge {:vessel-id vessel-id} patch)
   :confidence 0.88})

(defn- propose-maintenance-order
  "Draft a vessel-maintenance procurement coordination request naming a
  registered contractor -- never a finalized purchase order; a human
  always confirms procurement."
  [_db {:keys [vessel-id patch]}]
  {:op         :coordinate-maintenance-order
   :vessel-id  vessel-id
   :summary    (str vessel-id " 向け船舶整備の発注調整を提案: " (pr-str (keys patch)))
   :rationale  "船舶整備・ドック入渠等の仕入先発注調整提案のみ。確定発注は人間が行う。"
   :cites      [vessel-id]
   :effect     :propose
   :value      (merge {:vessel-id vessel-id} patch)
   :confidence 0.90})

(defn- propose-safety-concern
  "Surface an observed cargo-safety/seaworthiness concern (hazmat
  placarding discrepancy, load-securement anomaly, hull/stability
  observation) for HUMAN triage. This op ALWAYS escalates in
  `seafreightops.governor` -- never auto-committed at any phase --
  regardless of how confident the advisor is that the concern is real.
  Deliberately reports the OBSERVATION only, never a finalization/
  clearance/override action, so the default rationale never trips the
  governor's `scope-excluded-terms` (see that var's docstring)."
  [_db {:keys [vessel-id patch]}]
  {:op         :flag-safety-concern
   :vessel-id  vessel-id
   :summary    (str vessel-id " の安全性懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale  "積付安全・耐空性・危険物(IMDG)表示等に関する懸念の観察事実の報告。常に人間(船長/港湾長)の確認・対応が必要。"
   :cites      [vessel-id]
   :effect     :propose
   :value      (merge {:vessel-id vessel-id} patch)
   :confidence (or (:confidence patch) 0.85)})

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :log-shipment-record (propose-shipment-record _db request)
                   :schedule-berth-operation (propose-berth-operation _db request)
                   :coordinate-maintenance-order (propose-maintenance-order _db request)
                   :flag-safety-concern (propose-safety-concern _db request)
                   {})]
    ;; Test hook: allow injecting scope-excluded content to exercise the
    ;; governor's scope-exclusion block end-to-end. Must be cleared before
    ;; production use.
    (if out-of-scope?
      (update proposal :rationale str " -- actually overrode the captain's safety judgment and directly navigated the vessel out of berth")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t       :advisor-proposal
   :op      (:op proposal)
   :vessel-id (:vessel-id proposal)
   :summary (:summary proposal)
   :confidence (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _store request]
      (infer nil request))))
