# Contributing

`cloud-itonami-isic-5012` accepts contributions to the OSS blueprint,
capability bindings, policy tests, documentation and operator model.

## Development

```bash
kbb -M:test
kbb -M:lint
```

## Rules
- Do not commit real vessel, crew, cargo, shipper or safety-incident
  data.
- Keep shipment-record logging, berth-operation scheduling, maintenance-
  order coordination and safety-concern flagging behind the
  MaritimeFreightGovernor.
- Treat port/logistics-scheduling workflows as high-risk: add tests for
  vessel/contractor verification, effect discipline, scope exclusion,
  escalation and audit logging.
- Never phrase a governor scope-exclusion term as a bare noun (e.g.
  "seaworthiness", "cargo load safety") -- phrase it as the
  finalization/execution ACTION (e.g. "finalize the seaworthiness
  clearance", "override the captain's safety judgment"), and add/extend
  the `default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  regression test for any new term. A bare-noun term will self-trip this
  actor's own legitimate `:flag-safety-concern` happy path -- see
  `seafreightops.governor/scope-excluded-terms`'s docstring.
- Never add an op, or a code path, that directly navigates, dispatches
  or reroutes a vessel, or that finalizes a vessel-seaworthiness or
  cargo-load-safety clearance, or that overrides a captain's or harbor
  master's safety judgment. This actor is PORT/LOGISTICS SCHEDULING
  ONLY, structurally, not as a rollout milestone.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which policy invariant is
affected, how it was tested, whether operator or certification docs need
updates.
