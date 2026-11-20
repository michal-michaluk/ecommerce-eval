## 3 Cross-cutting rules

Five rules apply to every operation in this chapter's scope: who may call, how a refusal is spelled, how
facts travel inside the process, what is deliberately not represented, and what a repeat of a command does.
The scope-to-route matrix itself is §4 and is not repeated here.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:42-60 -->

### 3.1 One authentication model: a bearer token, four scopes

Every request carries a JSON Web Token that the service validates against the issuer named by
`JWT_ISSUER_URI`, defaulting to the `iot` realm of the local issuer. The session is stateless and CSRF is off,
because no cookie is ever issued: the token is the whole identity.
<!-- evidence: services/fulfillment/src/main/resources/application.yml:17-21, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:52-58 -->

The token's `scope` claim is read as a Spring authority, and exactly four scopes exist; each names one
caller, and the route it reaches is the matrix in §4:

| Scope | Authority the service checks | Caller |
| --- | --- | --- |
| `fulfillment-employee` | `SCOPE_fulfillment-employee` | the warehouse employee |
| `fulfillment-resolver` | `SCOPE_fulfillment-resolver` | the issue resolver |
| `fulfillment-customer` | `SCOPE_fulfillment-customer` | the customer |
| `shopping-service` | `SCOPE_shopping-service` | checkout, for the handover |

<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:31-35, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:75-95, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:108-119 -->

Three paths are reachable without a token — the health group, the info endpoint and the framework's error
dispatch — and everything else under the actuator prefix needs a token but no scope. Any request the matrix
does not name is denied, so a route added later cannot become "any authenticated caller" by omission.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:71-74, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:54 -->

A missing token answers `401` with an empty body and a `Bearer` challenge; a token without the scope answers
`403` with an empty body and no reason. Neither case leaks whether the resource exists.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:122-133 -->

Two consequences are recorded rather than hidden. The customer route checks the scope and nothing else: the
order carries no customer identity, so "only my own processing" cannot be implemented here. And because the
actuator prefix needs only a token, a customer-scoped caller can read the environment and metrics endpoints;
`GAP-1` keeps that as an accepted risk.
<!-- evidence: docs/decisions/operator-input.md:12, docs/specs/fulfillment/spec.md:87, docs/specs/fulfillment/spec.md:570 -->

### 3.2 One error type, one advice, one vocabulary

The context raises exactly one exception type, and that type carries a nested enumeration of 37 error
constants. Each constant owns its message template; the transport reads the constant's name, never the
message, so no caller matches on a string and no domain error needs a class of its own.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/FulfillmentException.java:12-15, services/fulfillment/src/main/java/shop/fulfillment/kernel/FulfillmentException.java:52, services/fulfillment/src/main/java/shop/fulfillment/kernel/FulfillmentException.java:80-98 -->

A single advice renders the wire. It routes 16 of the 37 constants to a status and a body, and rethrows the
other 21 unchanged — so a warehouse failure, a carrier that was never configured or a value-object invariant
answers `500` with no message and no stack trace, because the configuration excludes the message from the
error body.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/FulfillmentErrorAdvice.java:59-72, services/fulfillment/src/main/resources/application.yml:23-26 -->

The 16 routed constants answer four statuses, and two bodies carry a value beside `error`:

- `422` for a handover that lacks a part; its body is `{error, missing[]}`, the payload paths the handover did
  not carry;
- `404` for a resource the path names and this order does not have, and `409` for every refusal about the
  order's state, both answering `{error}` alone;
- `409 NOT_READY`, whose `reasons[]` carries the readiness decision's own reason values, and `409
  ISSUES_REMAIN`, whose `openIssues` is a count rather than a reason. No other `409` carries a field.

The integrator guide's §3 tabulates that status-to-code mapping one row per code, and this chapter does not
repeat it.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/FulfillmentErrorAdvice.java:62-70, services/fulfillment/src/main/java/shop/fulfillment/FulfillmentErrorAdvice.java:96-106, services/fulfillment/src/main/java/shop/fulfillment/FulfillmentErrorAdvice.java:131-141 -->

The derivation is deliberate: six constants are spelled differently on the wire than in the domain, because
the contract fixes the code a client branches on.

| Domain constant | Code on the wire |
| --- | --- |
| `ORDER_LINE_DUPLICATE` | `ORDER_INCOMPLETE`, with `missing: ["lines.productNumber"]` |
| `POSITION_UNKNOWN` | `POSITION_NOT_FOUND` |
| `READINESS_REFUSED` | `NOT_READY` |
| `ISSUE_NOT_REPORTED_BY_ASSIGNEE` | `ACTOR_NOT_ALLOWED` |
| `WORK_IS_ON_HOLD` | `ON_HOLD` |
| `WORK_NOT_STARTED` | `NOT_STARTED` |

<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/FulfillmentErrorAdvice.java:62-70, services/fulfillment/src/main/java/shop/fulfillment/FulfillmentErrorAdvice.java:29-51, services/fulfillment/docs/phase-1-decisions.md:118 -->

### 3.3 Facts travel inside the process, never across it

A command's effects are published as domain events on the application's own event bus, inside the caller's
transaction, after the row was written. The listeners are three read-model projections and one listener that
commands the write model; all four run in the publisher's transaction, so a rolled-back command leaves no row
and no event behind.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/DocumentOrderProcessingRepository.java:87-91, services/fulfillment/src/main/java/shop/fulfillment/orders/OrdersListProjection.java:41-44, services/fulfillment/src/main/java/shop/fulfillment/processing/DeliveryStateListener.java:30-41 -->

Nothing leaves the process. The broker client is on the classpath and the configuration names a consumer group,
yet the service registers no listener and no producer — the transport itself is not declared in the repository —
and the handover over HTTP is the only cross-context transport. `GAP-8` records the absent outbox and broker.
<!-- evidence: services/fulfillment/build.gradle:30, services/fulfillment/src/main/resources/application.yml:14-16, docs/specs/fulfillment/spec.md:83 -->

### 3.4 No money is represented

No amount, price, currency, VAT rate or total appears in this context — not in the order, not in the pick list,
not in the snapshot, not in a read-model row. The three places a decimal type appears at all convert the
warehouse's fractional quantities into whole units, and a fractional quantity is refused rather than rounded.
<!-- evidence: docs/specs/fulfillment/spec.md:76, services/fulfillment/src/main/java/shop/fulfillment/processing/InvenTreeWarehouseClient.java:360, services/fulfillment/src/main/java/shop/fulfillment/processing/InvenTreeWarehouseClient.java:377, services/fulfillment/docs/phase-1-decisions.md:96 -->

### 3.5 What a repeat of each command does

Idempotency is decided per operation family, not globally. The table below is the whole posture; the status a
replay answers is tabulated in the integrator guide's §3.3, and the per-route matrix is §4.

| Family | The key | A repeat does |
| --- | --- | --- |
| handover of an order | the client's `orderHash`, plus a digest the service computes over the order content | same hash and same content answers `200` with `duplicate: true` and the same processing; same hash with different content is `409 ORDER_HASH_CONFLICT`, never a silent drop. A unique index on the hash means two racing first deliveries cannot both insert <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderIntakeService.java:40-48, services/fulfillment/src/main/java/shop/fulfillment/kernel/Order.java:91-94, services/fulfillment/src/main/resources/db/changelog/0003-order-processing.yaml:46-50 --> |
| claim of an order | the assignee | the same employee claiming again is a no-op answered with the current state; another employee is `409 ALREADY_ASSIGNED`; a stale write fails on the row's version column instead of overwriting <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/AssignmentGuard.java:47-58, services/fulfillment/src/main/java/shop/fulfillment/processing/AssignmentGuard.java:66-70, services/fulfillment/src/main/java/shop/fulfillment/processing/DocumentOrderProcessingRepository.java:137 --> |
| scan of one position | the position's state | a position already picked or packed changes nothing and raises no event; the command still answers `200` with the snapshot <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:90-98 --> |
| packing one position | the position's state | a position already in the box changes nothing and raises no event; a position still awaiting a scan is `409 POSITION_NOT_PICKED` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:107-116 --> |
| readiness | the order's state | once the order crossed into ready for delivery the decision answers no-op, so nothing is written and the carrier is not asked a second time <!-- evidence: docs/specs/fulfillment/08-ready-for-delivery-decision.md:26, services/fulfillment/src/main/java/shop/fulfillment/processing/ProcessingService.java:110-122 --> |
| reporting an issue | nothing — a report is always a new fact | a second report on a held order writes a second issue row and raises no second hold; the answer is `201` for both cases <!-- evidence: docs/specs/fulfillment/09-hold-decision.md:19, services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:145-155 --> |
| resuming from hold | the open-issue count | an order that is not held changes nothing; a resume that would leave an issue open answers `409` with the count in `openIssues` <!-- evidence: docs/specs/fulfillment/09-hold-decision.md:22-24, services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:165-174 --> |
| parcel registration | the processing | a processing that already has a parcel answers that parcel's identity and consumes no second carrier identity <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelService.java:70-75 --> |
| collection request | the parcel's leg state | a parcel whose legs moved past registration books nothing — at most one collection is open per parcel <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelEditor.java:88-97 --> |
| a carrier's delivery leg | `(carrier identity, leg)`, forward only | the same notification applied twice changes nothing, and an earlier leg arriving after a later one moves nothing <!-- evidence: docs/specs/fulfillment/03-parcel.md:73, services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelEditor.java:161-170 --> |
| a label print | the stored document | the first print asks the carrier and stores the bytes; every reprint serves those bytes and calls nobody <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelLabelProjection.java:49-60 --> |
| warehouse reservation and goods issue | the reservation identifier | the port contract makes release idempotent, and a goods issue skips a reservation that already carries a document, so a readiness issues at most once per reservation <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/WarehouseGoodsIssueIntegration.java:47, services/fulfillment/docs/phase-1-decisions.md:95 --> |
| warehouse reservation correlation | its own transaction | every method of the reservation store runs in its own transaction, because a real allocation outlives a claim that rolls back <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/DocumentWarehouseReservationRepository.java:38-52 --> |

One operation is not idempotent by design: the issue report. Each accepted report is a fact that did not exist
before, and there is no request key that could collapse two reports into one.
<!-- evidence: services/fulfillment/docs/phase-1-decisions.md:249, docs/specs/fulfillment/09-hold-decision.md:18-19 -->
