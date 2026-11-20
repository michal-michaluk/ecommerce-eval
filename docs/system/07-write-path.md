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

Read models are separate rows written by separate handlers, and they never read the write model:

| Read model | Endpoint | Consumes |
| --- | --- | --- |
| orders list (`orders/OrdersListProjection.java:49-97`) | `GET /orders` | started, assigned, picked, packed, ready, delivered, hold, resume |
| issue dashboard (`issues/IssueDashboardProjection.java:63-83`) | `GET /issues` | started, assigned, issue reported, hold, resume |
| customer state (`customer/CustomerStateProjection.java:61-122`) | `GET /processing/{id}/state` | the same family plus delivery-number, collection and delivery-leg facts |
| pick workstation (`processing/PickWorkstation.java`) | `GET /processing/{id}/pick-workstation` | derived on read from the process snapshot |

Each projection handler follows the same four steps — find-or-create the row, record the fact the event
carries, recompute the row's state from its own facts, save — so replaying an event cannot double-count a
fact and the order of arrival cannot change the outcome. A projection can be rebuilt by replaying the
events it consumes; nothing in the repository schedules such a rebuild, which is `GAP-9`.
