## 5 Business impact

This chapter states the business function the capability serves, what is lost when a dependency is unavailable, and the
impact figures the repository does not hold. Every figure that a business-continuity plan needs is written as a numbered
gap, never as an estimate.
<!-- evidence: docs/decisions/genre-provenance.md:19, docs/decisions/genre-provenance.md:35 -->

### 5.1 The business function served

Fulfillment is the shop's physical delivery step: after checkout accepts an order, this capability gets the goods out of
the warehouse, hands the parcel to a carrier and tells the customer where it is. Its success measure is stated in
business terms: an order is never processed twice, every state change reaches the customer, and the parcel has a carrier
identifier before its first delivery leg.
<!-- evidence: docs/specs/fulfillment/spec.md:96-97 -->

Two services are consumed and three roles act on it; the capability has no alternative path in this repository: there is
no manual fallback, no paper process and no second carrier integration behind it.
<!-- evidence: docs/specs/fulfillment/spec.md:94, docs/specs/fulfillment/spec.md:143 -->

- **GAP-1 — criticality and business owner.** No source in the repository states how critical this capability is, which
  tier it belongs to, or who owns it when it degrades. The specification names the actors, the success measures and the
  service boundaries, and stops there.
- **GAP-2 — maximum tolerable period of disruption (MTPD).** No source states how long the shop can process no order at
  all before the business consequence becomes unacceptable.
- **GAP-3 — recovery time objective (RTO).** No source states how quickly order processing has to be available again
  after an outage. The deployment declares one replica and no restart or failover objective.
  <!-- evidence: services/fulfillment/k8s/base/deployment.yaml:8 -->
- **GAP-4 — recovery point objective (RPO).** No source states how much processing state may be lost. The deployment
  declares no backup, no restore procedure and no replication, so nothing bounds the loss of the state held in
  `order_processing`, `parcel` and the read models.
- **GAP-5 — order volume and latency.** Volume and latency figures are absent from every source, so no recovery
  commitment can be sized against a load, and no queue length or backlog figure exists to judge a degraded period.
  <!-- evidence: docs/specs/fulfillment/spec.md:117 -->
- **GAP-6 — the deadline on a held order.** A hold has no timeout, no escalation and no service level; the only exit is
  the resume command. How long an order may sit on hold before the customer relationship is at risk is a business
  decision that no source records.
  <!-- evidence: docs/specs/fulfillment/09-hold-decision.md:102-104 -->
- **GAP-7 — who is woken, and when.** No alert rule, no threshold, no paging route and no on-call rotation exists in the
  repository. Nothing states who learns that the service, the warehouse or the carrier is unavailable, or within what
  time.
  <!-- evidence: docs/decisions/genre-provenance.md:17, docs/decisions/genre-provenance.md:15 -->
- **GAP-8 — the business rule behind a divergent hand-over.** A grant of "ready for delivery" applies the local mark
  before the carrier is asked to collect, and nothing describes recovery when the second effect fails. Whether that
  divergence is an incident, a manual follow-up or a customer-visible delay is not recorded.
  <!-- evidence: docs/specs/fulfillment/08-ready-for-delivery-decision.md:113, services/fulfillment/src/main/java/shop/fulfillment/processing/ProcessingService.java:135-142 -->
- **GAP-9 — the promise the customer is given.** The delivery promise and its cost belong to checkout; this capability
  executes the method it receives. What the customer is told when fulfillment is unavailable, and whether the shop's
  commitment is breached by a delay here, is outside every source in this repository.
  <!-- evidence: docs/specs/fulfillment/spec.md:69 -->

### 5.2 What is lost when a dependency is unavailable

| Dependency unavailable | Business capability lost | How the loss becomes visible |
| --- | --- | --- |
| Warehouse system | claiming an order (and therefore the reservation), the pick list, the goods issue at hand-over | a claim is refused with `WAREHOUSE_NOT_WIRED`; a pick list is refused the same way <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/WarehouseReservationsIntegration.java:47-50, services/fulfillment/src/main/java/shop/fulfillment/processing/PickListProjection.java:105-110 --> |
| Warehouse system, wired but unreachable | the same three capabilities | the failure is surfaced, never smoothed: no fallback quantity and no cached stock served as fresh <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/WarehouseStock.java:11-13 --> |
| Carrier (registration) | the parcel and its carrier identity, and therefore hand-over; scanning starts the parcel, so the first scan is the step that fails | a scan is refused with `CARRIER_NOT_WIRED`, and readiness is later refused as `noParcel` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelService.java:95-98, services/fulfillment/src/main/java/shop/fulfillment/processing/ReadyForDeliveryDecision.java:83 --> |
| Carrier (status) | the delivery legs and the message to the customer; the order keeps its state | legs are read by a poll every 60 seconds, so an outage delays states instead of failing a command <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/parcel/CarrierPollingIntegration.java:20-26 --> |
| Carrier (label) | printing the consignment note; picking and hand-over continue | the label is served from stored bytes and never re-registers the parcel <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelService.java:83-89 --> |
| Identity provider | every capability of all three roles: no caller can authenticate | every endpoint answers `401` and only the health and info probes stay open <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:122-128, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:71-74 --> |
| Database | every capability, read and write; nothing is durable | no read model and no processing state survives the outage <!-- evidence: services/fulfillment/src/main/resources/application.yml:3-8 --> |
| Invoicing boundary | the Polish goods issue note only; picking, hand-over and tracking are unaffected | the goods issue note is outside the delivered system because no invoicing contract supplies its amounts <!-- evidence: docs/decisions/operator-input.md:10 --> |

Two accepted risks change the impact of an unavailable or misused dependency without being incidents: the customer
endpoint accepts any caller with the customer scope, so a customer token can read another order's state; and the
management surface is reachable by any authenticated caller, so a customer token can read environment and metric data.
<!-- evidence: docs/decisions/operator-input.md:12, docs/specs/fulfillment/spec.md:570 -->

### 5.3 What the figures would change

The gaps above are not documentation debts. Without MTPD there is no statement of when a stuck hold becomes a commercial
problem (GAP-2, GAP-6); without RTO and RPO there is no objective against which a restart, a restore or a replica count
can be judged (GAP-3, GAP-4, and the single replica the deployment declares); without volume there is no basis for a
capacity decision (GAP-5); and without an alert route there is no answer to who acts at 03:00 (GAP-7). Each of these is a
request to the business owner named in GAP-1, not a finding this document can settle.
<!-- evidence: docs/specs/fulfillment/spec.md:117, docs/decisions/genre-provenance.md:31-32 -->
