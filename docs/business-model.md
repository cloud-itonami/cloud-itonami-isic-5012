# Business Model: Maritime-Freight Port/Logistics Scheduling Coordination

## Classification
- Repository: `cloud-itonami-isic-5012`
- ISIC Rev.5: `5012` -- sea and coastal freight water transport
  (cargo/container vessels and coastal shipping)
- Social impact: safety, supply-chain resilience, transparency

## Customer
- independent port operators and freight-forwarding coordinators
  needing an auditable operations-coordination platform
- multi-vessel/multi-berth operators needing consistent maintenance/
  scheduling/safety governance across a fleet
- programs that cannot accept closed, unauditable back-office platforms
  for cargo and voyage records

## Offer
- cargo/manifest/voyage record logging
- port berth/voyage scheduling coordination
- vessel-maintenance procurement coordination with registered, verified
  contractors
- cargo-safety/seaworthiness-concern flagging (hazmat placarding,
  load-securement anomalies, hull/stability observations) for human
  (captain/harbor master) triage
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per port/vessel
- support retainer with SLA

## Trust Controls
- `:maritime-freight-governor` never lets a proposal for an
  unregistered/unverified vessel, or a maintenance order naming an
  unregistered/unverified contractor, commit or even escalate
- every proposal's `:effect` must be `:propose` -- a claim to directly
  actuate is a HARD, un-overridable block
- directly finalizing a vessel-seaworthiness clearance or a cargo-load-
  safety clearance, overriding a captain's/harbor-master's safety
  judgment, bypassing a maritime safety protocol, or directly
  navigating/dispatching a vessel is permanently out of scope, not a
  rollout milestone -- the actor may only flag a concern for a human
- a `:flag-safety-concern` proposal, and a high-cost `:coordinate-
  maintenance-order`, always require human sign-off
- sensitive vessel, crew, cargo and shipper data stays outside Git
