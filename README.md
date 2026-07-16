# cloud-itonami-isic-5012

Open Business Blueprint for **ISIC Rev.5 5012**: sea and coastal freight
water transport -- cargo/container vessels and coastal shipping.

This repository publishes a maritime-freight
PORT/LOGISTICS SCHEDULING coordination actor -- cargo/manifest/voyage
record logging, port berth/voyage scheduling coordination, vessel-
maintenance procurement coordination with registered contractors, and
cargo-safety/seaworthiness-concern flagging -- as an OSS business that
any qualified operator can fork, deploy, run, improve and sell, so an
independent port/logistics coordinator never surrenders shipping
operations data to a closed back-office SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, in-mem/Datomic checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **SeaFreightAdvisor ⊣
MaritimeFreightGovernor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:maritime-freight-governor`, is
a distinct, independent build (no naming-collision precedent question --
distinct from sibling ISIC 5020's own `:marine-cargo-governor`).

> **Why an actor layer at all?** An LLM is great at drafting a shipment-
> record summary, a berth-scheduling proposal, or a maintenance-order
> request -- but it has no license to actually finalize a vessel-
> seaworthiness clearance or a cargo-load-safety clearance, no authority
> to override a captain's or harbor master's safety judgment, no way to
> independently confirm a vessel or a maintenance-order contractor is
> actually a registered/verified counterparty, and no notion of when a
> "flag this concern" op quietly turns into a claim to have already
> acted on it. Letting it act directly invites an unverified vessel's
> data entering the ledger, an unverified contractor receiving a
> maintenance order, or -- worst of all -- a fabricated claim to have
> cleared a vessel as seaworthy or overridden a captain's safety call,
> exposing the port operator, the vessel and its crew to real liability.
> This project seals the SeaFreightAdvisor into a single node and wraps
> it with an independent **MaritimeFreightGovernor**, a human **approval
> workflow**, and an immutable **audit ledger**.

## Scope: port/logistics scheduling only, never navigation or safety-clearance authority

This actor is **port/logistics scheduling coordination only**. It never
performs or authorizes:

- directly navigating, dispatching, or rerouting a vessel
- directly finalizing a vessel-seaworthiness clearance or a cargo-
  load-safety clearance
- overriding a captain's or harbor master's safety judgment, or
  bypassing a maritime safety protocol

The governor's `scope-exclusion-violations` check re-scans every
proposal for this failure mode independently of the advisor's own
framing, and treats it as a HARD, permanent block regardless of
confidence or how clean everything else is. Flagging a cargo-safety/
seaworthiness concern for a human (captain/harbor master) to triage is
exactly this actor's job -- `:flag-safety-concern` is never excluded by
this check, only FINALIZING/overriding/directly-acting-on that concern
is.

### Actuation

**Every proposal this actor generates is `:effect :propose`, never a
direct actuation.** Two independent layers enforce this
(`seafreightops.governor`'s `effect-not-propose-violations` HARD check
and `seafreightops.phase`'s phase table, which never puts
`:flag-safety-concern` in any phase's `:auto` set). A human port/
logistics coordinator (or, for a safety concern, the captain/harbor
master) is always the one who actually acts on a flagged concern or
confirms a high-cost maintenance order. This actor never navigates a
vessel and never overrides a captain's or harbor master's safety
judgment.

## The core contract

```
vessel/carrier registration + port/logistics scheduling request
        |
        v
   ┌───────────────────────┐   proposal      ┌────────────────────────────┐
   │ SeaFreightAdvisor     │ ─────────────▶ │ MaritimeFreightGovernor      │  (independent system)
   │ (sealed)              │  + citations    │ vessel-unverified ·          │
   └───────────────────────┘                 │ contractor-unverified (NEW) ·│
          │                 commit ◀┼ effect-not-propose ·               │
          │                         │ scope-excluded (vessel-navigation/  │
    record + ledger        escalate ┼ safety-clearance finalization) ·    │
          │              (ALWAYS for│ op-not-allowed                      │
          │       :flag-safety-     │                                      │
          │       concern/high-cost │                                      │
          │       maintenance-order)└────────────────────────────┘
          ▼
      human approval
```

**The SeaFreightAdvisor never commits a proposal the
MaritimeFreightGovernor would reject, and a safety-concern flag or a
high-cost maintenance order never commits without a human sign-off.**
Hard violations (an unregistered/unverified vessel; an unregistered/
unverified maintenance-order contractor; a non-`:propose` effect;
content touching vessel-navigation/safety-clearance finalization; an op
outside the closed allowlist) force **hold** and *cannot* be approved
past.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
may perform physical domain work** (here: port-side cargo handling,
container yard operations) under human/robot floor operations gated by
port policy. This actor itself does not dispatch robot/hardware
actions, and it never navigates a vessel -- it is strictly the port/
logistics-scheduling coordination layer (shipment-record logging,
berth-operation scheduling, maintenance-order coordination, safety-
concern flagging) any physical-dispatch layer could eventually feed
proposals into, always gated the same way by the independent
MaritimeFreightGovernor.

## Features

- **Closed proposal-op allowlist**: `log-shipment-record`,
  `schedule-berth-operation`, `coordinate-maintenance-order`,
  `flag-safety-concern` (all `:effect :propose`). None of these ops
  navigate a vessel or finalize a vessel-seaworthiness/cargo-load-safety
  clearance.
- **Four HARD governor checks** (permanent, un-overridable):
  1. **Vessel unverified** -- the target vessel/carrier's registration
     must exist AND be independently registered/verified in the store.
  2. **Contractor unverified** (FLAGSHIP NEW) -- for `:coordinate-
     maintenance-order` only, the named maintenance contractor must
     exist AND be independently registered/verified -- a maintenance-
     supply-chain counterparty-verification gate no sibling
     retail/commerce actor has had reason to add.
  3. **Effect is :propose** -- any other `:effect` value is rejected.
  4. **Scope exclusion** -- directly finalizing a vessel-seaworthiness
     clearance or a cargo-load-safety clearance, overriding a captain's/
     harbor-master's safety judgment, bypassing a maritime safety
     protocol, directly navigating/dispatching a vessel, and an op
     outside the closed allowlist are all permanently blocked.
- **Two ESCALATE (SOFT) gates**, either forces human sign-off:
  - `:flag-safety-concern` -- ALWAYS escalates, regardless of confidence
    or phase. A "flag a concern" op is never auto-commit eligible and
    never finalizes a safety-clearance decision itself -- it only
    surfaces the concern for a human (captain/harbor master).
  - `:coordinate-maintenance-order` above a cost threshold -- a
    large-value procurement proposal always needs a human sign-off.
  - (LLM confidence below the floor also escalates, as with every
    sibling actor.)
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: shipment-record logging only (approval-gated)
  - Phase 2: + berth-operation scheduling, maintenance-order proposals
    (approval-gated)
  - Phase 3: auto-commits clean, high-confidence, low-cost proposals
    (safety concerns and high-cost maintenance orders always escalate)
- **Append-only audit ledger** -- every decision is an immutable log
  entry.
- **langgraph-clj StateGraph** -- one request = one supervised run;
  human-in-the-loop via `interrupt-before`.

### Development

```bash
# Install dependencies (if inside the superproject, use :dev alias for local overrides)
clojure -M:dev -P

# Run tests
clojure -M:test

# Run linter
clojure -M:lint

# Run demo
clojure -M:run
```

### Test suite

- `test/seafreightops/governor_test.clj` -- unit tests of governor hard
  checks, scope exclusion, and the self-trip regression test
- `test/seafreightops/advisor_test.clj` -- advisor proposal shape and
  consistency
- `test/seafreightops/phase_test.clj` -- rollout phase logic
- `test/seafreightops/governor_contract_test.clj` -- full graph
  integration, audit trail
- `test/seafreightops/store_contract_test.clj` -- Store protocol and
  MemStore implementation

### Modules

- `seafreightops.store` -- SSoT (MemStore, String-keyed vessel/
  contractor directories, append-only ledger)
- `seafreightops.advisor` -- contained intelligence node (mock +
  real-LLM seam)
- `seafreightops.governor` -- independent compliance layer
- `seafreightops.phase` -- staged rollout (0→3)
- `seafreightops.operation` -- langgraph-clj StateGraph
- `seafreightops.sim` -- demo driver

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`5012`).

## Business-process coverage (honest)

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Cargo/manifest/voyage record logging (`:log-shipment-record`) | Real vessel-tracking/AIS/port-community-system integration |
| Port berth/voyage scheduling coordination (`:schedule-berth-operation`) | Direct vessel navigation, dispatch or rerouting |
| Vessel-maintenance procurement coordination with a registered, verified contractor, HARD-gated on contractor verification and a double-actuation-free single-proposal shape (`:coordinate-maintenance-order`) | Real drydock/maintenance-management-system integration |
| Cargo-safety/seaworthiness-concern flagging, ALWAYS human-gated (`:flag-safety-concern`) | Directly finalizing any vessel-seaworthiness or cargo-load-safety clearance, or overriding a captain's/harbor-master's safety judgment -- permanently out of scope, not a gap |
| Immutable audit ledger for every log/schedule/order/flag decision | Customs/regulatory filing integration -- a follow-up slice, not in this R0 |

Extending coverage is additive: add the next op (e.g. a demurrage-
notice or a cargo-discrepancy-escalation check) as its own governed op
with its own HARD checks and tests, following the SAME "an independent
governor re-verifies against the actor's own records before any
real-world act" pattern this repo's flagship checks already establish.

## Maturity

`:implemented` -- `SeaFreightAdvisor` + `MaritimeFreightGovernor` run as
real, tested code (see `Development` above), following the SAME
governed-actor architecture as every prior actor across this fleet,
with its own distinct, independently-named governor and its own novel
maintenance-contractor-verification check.

## License

Code and implementation templates are AGPL-3.0-or-later.
