## 7 Write path and read models

<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/DocumentOrderProcessingRepository.java:34-39 -->
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/DocumentOrderProcessingRepository.java:87-90 -->
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:64 -->

A command changes the process through one aggregate, and nothing else in the service writes that state.
`OrderFulfillmentProcess` is package-private and is reachable only from the service that owns the
transaction; every method that changes it appends a domain event to the aggregate's own event list before
returning (`OrderFulfillmentProcess.java:64` and its siblings). The aggregate is therefore the only place
where a transition and its announcement are written together.

The persistence adapter drains that list **before** it writes and publishes **after** the write
(`DocumentOrderProcessingRepository.java:34-39`, `:87-90`). Two consequences follow, and they are the
reason the order is fixed:

- a write that fails publishes nothing, because the buffer was drained into a local list whose
  publication is the last statement;
- a write that succeeds publishes each event exactly once, because the drained list is cleared as it is
  read.

Publication happens inside the caller's transaction, not after commit. A listener therefore sees the
event while the row is still uncommitted; the orders-list projection is the one listener on the write
path that relies on this, and the phase register records it as a deliberate, narrow coupling
(`services/fulfillment/docs/phase-1-decisions.md`, P4/F6). Nothing in the service uses an outbox, and no
event leaves the process.

### 7.1 The four read surfaces

Every read of this service lands on one of four surfaces, and this list is the only place they are
enumerated:

| Surface | Endpoint | What it is |
| --- | --- | --- |
| orders list | `GET /orders` | a read model with its own table, written by a projection (`orders/OrdersListProjection.java:49-97`) |
| issue dashboard | `GET /issues` | a read model with its own table, written by a projection (`issues/IssueDashboardProjection.java:63-83`) |
| customer state | `GET /processing/{processingId}/state` | two read-model tables, written by a projection (`customer/CustomerStateProjection.java:61-122`) |
| pick workstation | `GET /processing/{processingId}/pick-workstation` | derived on read from the process snapshot, with no table of its own (`processing/PickWorkstation.java`) |

Two answers sit beside that list. The label is a document route, not a read model: the carrier prints
the document once, the service stores the bytes, and every reprint serves them back
(`parcel/ParcelLabelProjection.java:49-60`). The pick list is a calculation over the aggregate and one
stock reading, made per request and never stored (`processing/PickListProjection.java:60-71`).

Which events each read model consumes, and which listener of §9 feeds it, is §9's table — the read side
does not repeat that wiring.

Each projection handler follows the same four steps — find-or-create the row, record the fact the event
carries, recompute the row's state from its own facts, save — so replaying an event cannot double-count a
fact and the order of arrival cannot change the outcome. A projection can be rebuilt by replaying the
events it consumes; nothing in the repository schedules such a rebuild, which is `GAP-9`.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/orders/OrdersListProjection.java:41-44, services/fulfillment/src/main/java/shop/fulfillment/customer/CustomerStateProjection.java:109-124 -->
