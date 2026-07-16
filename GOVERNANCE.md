# Governance

`cloud-itonami-isic-5012` is an OSS open-business blueprint for
maritime-freight port/logistics scheduling coordination (ISIC Rev.5
5012 -- sea and coastal freight water transport).

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a proposal for an unverified/unregistered vessel, or a maintenance
  order naming an unverified/unregistered contractor, can never commit.
- the MaritimeFreightGovernor remains independent of the advisor.
- hard policy violations (non-`:propose` effect, vessel-navigation/
  vessel-seaworthiness-clearance/cargo-load-safety-clearance-
  finalization content, an op outside the closed allowlist) cannot be
  overridden by human approval.
- this actor never directly navigates, dispatches or reroutes a vessel,
  and never overrides a captain's or harbor master's safety judgment.
- every shipment-record log, berth-operation schedule, maintenance-
  order coordination and safety-concern flag is auditable.
- vessel, crew, cargo and shipper data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or
license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is
a separate trust mark and should require security, audit and data-flow
review.

Certified operators can lose certification for:
- bypassing shipment-record, berth-scheduling, maintenance-order or
  safety-concern policy checks
- mishandling vessel, crew, cargo or shipper data
- misrepresenting certification status
- failing to respond to security or safety incidents
