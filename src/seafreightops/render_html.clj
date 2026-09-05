(ns seafreightops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo: there was previously
  NO demo page and no generator at all. This namespace drives the REAL
  actor stack (`seafreightops.operation` -> `seafreightops.governor` ->
  `seafreightops.store`) and renders the resulting store + append-only
  ledger. Every id, status, hold rule, hold detail and ledger row on
  the page is produced by an actual `langgraph.graph/run*` through the
  compiled OperationActor -- nothing on the page is typed by hand
  except `action-gate-rows` (a static description of this actor's own
  fixed op contract, clearly marked below as documentation-of-code, and
  even there the op list and the numeric thresholds are read from the
  real `governor`/`phase` vars so they cannot silently drift).

  INPUT PROVENANCE -- every subject id below is seeded.
  This repo's own demo driver (`seafreightops.sim`, `clojure -M:dev:run`,
  run BEFORE writing this file) reaches its unregistered-vessel HARD hold
  with `vessel-99`, an id deliberately ABSENT from
  `seafreightops.store/demo-data`. Passing a fabricated id through real
  code would still put a fabricated row on the page, so this scenario
  does NOT reuse it. Every `:vessel-id` and `:contractor-id` here is one
  of the five records actually seeded by `store/demo-data`:
  `vessel-1`, `vessel-2`, `vessel-3`, `contractor-1`, `contractor-2`.
  The `:vessel-unverified` HARD hold is reached instead with the seeded
  `vessel-3` (`:registered? true :verified? false`), which trips exactly
  the same governor check on a record that really exists.

  Scenario -> what each seeded subject exercises:

    vessel-1  (registered + verified)  -- the full clean lifecycle:
              a phase-1 `:log-shipment-record` (phase gate escalates
              even though the governor is clean -> human approves ->
              commit), the same op at phase 3 (auto-commit), a phase-3
              `:schedule-berth-operation` (auto-commit), a low-cost
              `:coordinate-maintenance-order` naming the verified
              `contractor-1` (auto-commit), a HIGH-cost
              `:coordinate-maintenance-order` (governor `high-stakes?`
              -> always escalates even at phase 3 -> approved), and a
              `:flag-safety-concern` (never in any phase's `:auto` set,
              and in the governor's `always-escalate-ops` -- two
              independent layers -> approved by the captain/harbor
              master).

    vessel-2  (registered + verified)  -- the deviation cases:
              `:contractor-unverified` HARD hold (maintenance order
              naming the seeded-but-unverified `contractor-2`),
              `:effect-not-propose` HARD hold (an advisor variant that
              claims `:effect :commit`, built with the repo's own
              `advisor/infer` seam exactly as `sim` does),
              `:scope-excluded` HARD hold (the request-level
              `:out-of-scope?` hook makes the advisor drift into
              vessel-navigation / safety-clearance-finalization
              territory), and one SOFT `:approval-rejected` (a
              high-cost maintenance order a human declines).

    vessel-3  (registered, NOT verified) -- `:vessel-unverified` HARD
              hold on a plain `:log-shipment-record`.

  Four of the four HARD governor rules and both SOFT escalation gates
  therefore fire for real in one run.

  DETERMINISM: no timestamps, no random ids, no wall-clock reads. The
  store sorts its directories by id, the ledger is append-only in
  scenario order, and every patch map is <= 8 keys (so Clojure keeps it
  an array-map and `(keys patch)` in the advisor summaries is insertion
  ordered). Two consecutive runs are byte-identical.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [langgraph.graph :as g]
            [seafreightops.advisor :as advisor]
            [seafreightops.governor :as governor]
            [seafreightops.operation :as op]
            [seafreightops.phase :as phase]
            [seafreightops.store :as store]))

(def ^:private coordinator-phase-1
  {:actor-id "coord-1" :actor-role :port-logistics-coordinator :phase 1})

(def ^:private coordinator-phase-3
  {:actor-id "coord-1" :actor-role :port-logistics-coordinator :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "port-logistics-coordinator-1"}}
          {:thread-id tid :resume? true}))

(defn- reject! [actor tid]
  (g/run* actor {:approval {:status :rejected :by "port-logistics-coordinator-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through the scenario documented in the ns
  docstring. Returns the resulting store -- every field `render` reads
  below is real governor/store output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)
        ;; The repo's own `sim` misbehaving-advisor seam: an advisor that
        ;; claims a direct actuation instead of a proposal. Same store,
        ;; same governor -- only the (untrusted) advisor differs.
        actor-direct (op/build db {:advisor (reify advisor/Advisor
                                              (-advise [_ _ req]
                                                (assoc (advisor/infer nil req) :effect :commit)))})]

    ;; --- vessel-1: full clean lifecycle -----------------------------
    (exec! actor "v1-log-p1"
           {:op :log-shipment-record :vessel-id "vessel-1"
            :patch {:manifest-lines 42 :cargo-weight-tonnes 3200 :hazmat-class "none"}}
           coordinator-phase-1)
    (approve! actor "v1-log-p1")

    (exec! actor "v1-log-p3"
           {:op :log-shipment-record :vessel-id "vessel-1"
            :patch {:manifest-lines 30 :cargo-weight-tonnes 1800 :hazmat-class "none"}}
           coordinator-phase-3)

    (exec! actor "v1-berth"
           {:op :schedule-berth-operation :vessel-id "vessel-1"
            :patch {:berth "berth-4" :eta "2026-07-20T06:00:00Z" :etd "2026-07-20T18:00:00Z"}}
           coordinator-phase-3)

    (exec! actor "v1-maint-low"
           {:op :coordinate-maintenance-order :vessel-id "vessel-1"
            :patch {:item "routine hull inspection" :estimated-cost 1200.0
                    :contractor-id "contractor-1"}}
           coordinator-phase-3)

    (exec! actor "v1-maint-high"
           {:op :coordinate-maintenance-order :vessel-id "vessel-1"
            :patch {:item "main engine overhaul" :estimated-cost 42000.0
                    :contractor-id "contractor-1"}}
           coordinator-phase-3)
    (approve! actor "v1-maint-high")

    (exec! actor "v1-safety"
           {:op :flag-safety-concern :vessel-id "vessel-1"
            :patch {:concern "hazmat placard mismatch on hold 3 manifest, possible cargo securement anomaly"
                    :confidence 0.92}}
           coordinator-phase-3)
    (approve! actor "v1-safety")

    ;; --- vessel-2: one clean commit, then the deviation cases -------
    (exec! actor "v2-berth"
           {:op :schedule-berth-operation :vessel-id "vessel-2"
            :patch {:berth "berth-1" :eta "2026-07-21T05:30:00Z" :etd "2026-07-21T14:00:00Z"}}
           coordinator-phase-3)

    ;; SOFT: high-cost maintenance order the human declines.
    (exec! actor "v2-maint-rejected"
           {:op :coordinate-maintenance-order :vessel-id "vessel-2"
            :patch {:item "propeller shaft replacement" :estimated-cost 68000.0
                    :contractor-id "contractor-1"}}
           coordinator-phase-3)
    (reject! actor "v2-maint-rejected")

    ;; HARD: contractor-2 is seeded but :verified? false.
    (exec! actor "v2-maint-unverified-contractor"
           {:op :coordinate-maintenance-order :vessel-id "vessel-2"
            :patch {:item "drydock survey" :estimated-cost 3000.0
                    :contractor-id "contractor-2"}}
           coordinator-phase-3)

    ;; HARD: advisor claims :effect :commit instead of :propose.
    (exec! actor-direct "v2-effect-not-propose"
           {:op :schedule-berth-operation :vessel-id "vessel-2"
            :patch {:berth "berth-2" :eta "2026-07-22T08:00:00Z"}}
           coordinator-phase-3)

    ;; HARD: advisor drifts into permanently-excluded scope.
    (exec! actor "v2-scope-excluded"
           {:op :log-shipment-record :vessel-id "vessel-2"
            :out-of-scope? true
            :patch {:manifest-lines 12}}
           coordinator-phase-3)

    ;; --- vessel-3: seeded but :verified? false ----------------------
    (exec! actor "v3-log"
           {:op :log-shipment-record :vessel-id "vessel-3"
            :patch {:manifest-lines 10}}
           coordinator-phase-3)
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw-name [v] (if (keyword? v) (name v) (str v)))

(defn- yes-no [b]
  (if b "<span class=\"ok\">yes</span>" "<span class=\"critical\">no</span>"))

(defn- ledger-for [ledger vessel-id]
  (filterv #(= vessel-id (:vessel-id %)) ledger))

(defn- hold-rules
  "The governor rule keywords carried by one ledger fact."
  [fact]
  (or (seq (:basis fact)) (map :rule (:violations fact))))

(defn- status-cell
  "Last ledger fact for a vessel. Branches ONLY on fact types this
  repo's `operation.cljc` actually appends to the ledger --
  `:committed` (the `:commit` node), `:governor-hold` and
  `:approval-rejected` (both via the `:hold` node). `:approval-granted`
  and `:approval-requested` are audit-channel-only and never reach
  `store/ledger`, so they are deliberately not branched on here."
  [ledger vessel-id]
  (let [f (last (ledger-for ledger vessel-id))]
    (case (:t f)
      :committed "<span class=\"ok\">committed</span>"
      :governor-hold (str "<span class=\"critical\">HARD hold &middot; "
                          (esc (str/join ", " (map kw-name (hold-rules f))))
                          "</span>")
      :approval-rejected "<span class=\"warn\">approval declined by human</span>"
      "<span class=\"muted\">no activity</span>")))

(defn- vessel-row [ledger coord-log {:keys [vessel-id name registered? verified?]}]
  (let [facts (ledger-for ledger vessel-id)
        commits (count (filter #(= vessel-id (:vessel-id %)) coord-log))
        holds (count (filter #(#{:governor-hold :approval-rejected} (:t %)) facts))]
    (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
            (esc vessel-id) (esc name)
            (yes-no registered?) (yes-no verified?)
            commits holds
            (status-cell ledger vessel-id))))

(defn- contractor-row [coord-log {:keys [contractor-id name registered? verified?]}]
  (let [orders (count (filter #(and (= :coordinate-maintenance-order (:op %))
                                    (= contractor-id (get-in % [:value :contractor-id])))
                              coord-log))]
    (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
            (esc contractor-id) (esc name)
            (yes-no registered?) (yes-no verified?)
            orders)))

(defn- hold-row [{:keys [op vessel-id violations] :as f}]
  (format "        <tr><td><code>%s</code></td><td><code>%s</code></td><td><code>%s</code></td><td>%s</td></tr>"
          (esc (str/join ", " (map kw-name (hold-rules f))))
          (esc (kw-name (or op :n-a)))
          (esc vessel-id)
          (esc (str/join " / " (map :detail violations)))))

(defn- ledger-row [{:keys [t op vessel-id actor summary] :as f}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (kw-name t))
          (esc (kw-name (or op :n-a)))
          (esc vessel-id)
          (esc actor)
          ;; commits carry the advisor's :summary; holds carry the
          ;; governor's own :detail strings. A human-declined approval
          ;; (:approval-rejected) has neither, so fall back to the
          ;; :basis rule keywords the hold fact really carries.
          (esc (or summary
                   (some->> (seq (remove nil? (map :detail (:violations f)))) (str/join " / "))
                   (some->> (seq (hold-rules f)) (map kw-name) (str/join ", "))
                   ""))))

(defn- coord-row [{:keys [op vessel-id payload]}]
  (format "        <tr><td><code>%s</code></td><td><code>%s</code></td><td><code>%s</code></td></tr>"
          (esc (kw-name op)) (esc vessel-id) (esc (pr-str payload))))

;; Static description of this actor's own CLOSED op contract
;; (README `Features`, `seafreightops.governor`, `seafreightops.phase`).
;; This is documentation of fixed behaviour, not runtime telemetry, so
;; the prose is legitimately hand-written -- but the op list itself and
;; every number are read from the real vars below, and `action-gate-rows`
;; asserts the prose map covers exactly `governor/allowed-ops`, so an op
;; added to the allowlist can never be silently missing from this page.
(def ^:private op-gate-prose
  {:log-shipment-record
   (str "phase 3: auto-commits when the governor is clean and confidence &ge; "
        governor/confidence-floor "; phase 1/2: human approval")
   :schedule-berth-operation
   (str "phase 3: auto-commits when the governor is clean and confidence &ge; "
        governor/confidence-floor "; phase 0/1: not writable at all")
   :coordinate-maintenance-order
   (str "phase 3: auto-commits when clean, the named contractor is independently verified, "
        "and estimated-cost &le; " governor/maintenance-cost-threshold
        "; above that threshold it ALWAYS escalates to a human, at any phase")
   :flag-safety-concern
   (str "ALWAYS human approval &middot; never in any phase's auto set AND in the governor's "
        "always-escalate-ops -- two independent layers agree")})

(defn- action-gate-rows []
  (assert (= (set (keys op-gate-prose)) governor/allowed-ops)
          "op-gate-prose must describe exactly governor/allowed-ops")
  (for [op (sort-by kw-name governor/allowed-ops)
        :let [auto? (contains? (get-in phase/phases [3 :auto]) op)]]
    (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
            (esc (kw-name op))
            (if auto?
              "<span class=\"ok\">auto-eligible at phase 3</span>"
              "<span class=\"warn\">never auto at any phase</span>")
            (get op-gate-prose op))))

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        coord-log (vec (store/coordination-log db))
        holds (filterv #(= :governor-hold (:t %)) ledger)]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-5012 &middot; sea-and-coastal-freight-water-transport</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Sea and coastal freight water transport (ISIC 5012) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · port/logistics scheduling only · never navigates a vessel, never finalizes a seaworthiness or cargo-load-safety clearance</span>\n"
     "</header>\n"
     "<main>\n"

     "  <section class=\"card\">\n"
     "    <h2>Vessels / carriers</h2>\n"
     "    <p class=\"muted\">Build-time-generated from <code>seafreightops.store</code> via <code>seafreightops.render-html</code> (<code>clojure -M:dev:render-html</code>). Registration and verification are read from each vessel's own record — the governor never trusts a proposal's self-report.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Vessel</th><th>Name</th><th>Registered</th><th>Verified</th><th>Committed ops</th><th>Held ops</th><th>Last ledger fact</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map (partial vessel-row ledger coord-log) (store/all-vessel-records db))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Maintenance contractors</h2>\n"
     "    <p class=\"muted\">The maintenance-supply-chain counterparty gate. A <code>:coordinate-maintenance-order</code> naming a contractor that is not independently registered <em>and</em> verified is a HARD block — this repo's flagship new check.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Contractor</th><th>Name</th><th>Registered</th><th>Verified</th><th>Orders committed</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map (partial contractor-row coord-log) (store/all-contractor-records db))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Action gate (MaritimeFreightGovernor + rollout phase)</h2>\n"
     "    <p class=\"muted\">The closed four-op allowlist. Any op outside it is a scope violation by construction. HARD holds cannot be approved past.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Auto-commit</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (action-gate-rows)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>HARD governor holds (this run)</h2>\n"
     "    <p class=\"muted\">Permanent, un-overridable blocks the governor raised independently of the advisor's own framing. None of these ever reached a human approver.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Rule</th><th>Op</th><th>Vessel</th><th>Governor detail</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map hold-row holds)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every commit, HARD hold and declined approval this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Vessel</th><th>Actor</th><th>Summary / detail</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map ledger-row ledger)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Committed coordination log</h2>\n"
     "    <p class=\"muted\">The SSoT writes. A payload carrying <code>:approved-by</code> was signed off by a human before it committed.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Vessel</th><th>Committed payload</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map coord-row coord-log)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "("
             (count (store/ledger db)) "ledger facts,"
             (count (filter #(= :governor-hold (:t %)) (store/ledger db))) "HARD holds,"
             (count (store/coordination-log db)) "committed coordination records )")))
