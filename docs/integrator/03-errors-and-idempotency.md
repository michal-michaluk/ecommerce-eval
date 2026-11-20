## 3 Errors and idempotency

### 3.1 The error body

One advice translates the context's single exception type into a status and a code, and it reads the error type and the
domain values the failure carries — never the message, which stays a log line. The code a client branches on is the fault's
own name, never a sentence.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/FulfillmentErrorAdvice.java:18, services/fulfillment/src/main/java/shop/fulfillment/FulfillmentErrorAdvice.java:62, services/fulfillment/src/main/java/shop/fulfillment/kernel/FulfillmentException.java:81 -->

A refusal carries `error` and, when it has one, exactly one extra field:

| Shape | Statuses | Field | Meaning |
| --- | --- | --- | --- |
| `{ "error": "<CODE>", "missing": [ … ] }` | `422` | `missing[]` | the payload paths the handover did not carry |
| `{ "error": "NOT_READY", "reasons": [ … ] }` | `409` | `reasons[]` | the readiness decision's own reason values, never codes |
| `{ "error": "ISSUES_REMAIN", "openIssues": 1 }` | `409` | `openIssues` | how many issues the resume left open |
| `{ "error": "<CODE>" }` | `404`, other `409` | none | the refusal names no value |

A field the answer does not carry is an absent key, never `null`, and no message and no stack trace reaches a client.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/FulfillmentErrorAdvice.java:132, services/fulfillment/src/main/java/shop/fulfillment/FulfillmentErrorAdvice.java:139, services/fulfillment/src/main/java/shop/fulfillment/FulfillmentErrorAdvice.java:140, services/fulfillment/src/main/resources/application.yml:26 -->

`missing[]` is not a fixed list. Its values are payload paths produced by the value objects of the order, so a client
receives `orderHash`, `placedAt`, `lines`, `delivery`, `payment`, `invoicing`, a nested one such as
`delivery.location.pointId`, or — for an absent address component — `address.street` without the object that holds the
address. The constraints of that vocabulary are GAP-11.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/Order.java:45, services/fulfillment/src/main/java/shop/fulfillment/kernel/DeliveryLocation.java:26, services/fulfillment/src/main/java/shop/fulfillment/kernel/Address.java:19 -->

### 3.2 The code for each status

| Status | `error` | When a client meets it | Proof |
| --- | --- | --- | --- |
| `422` | `ORDER_INCOMPLETE` | the handover lacks a required part, or a line, a delivery, a payment or an invoice block is unreadable | asserted with `missing[]` non-empty, and with `missing[]` containing `delivery.location.pointId` |
| `404` | `PROCESSING_NOT_FOUND` | the path names a processing that does not exist, on any `/processing/{id}/…` route | asserted on three routes |
| `404` | `POSITION_NOT_FOUND` | a scan names a product the order does not have | asserted |
| `404` | `ISSUE_UNKNOWN` | a resume names an issue the processing does not account for | asserted |
| `404` | `ISSUE_ID_MALFORMED` | a resume names an id that is not of the form `ISS-YYYY-NNNN` | derived — the id value object raises it, the contract never sends a malformed one |
| `409` | `ORDER_HASH_CONFLICT` | a handover reuses an `orderHash` for an order whose content differs | asserted |
| `409` | `ALREADY_ASSIGNED` | an employee claims an order another employee holds | asserted |
| `409` | `PAST_PICKING` | an employee claims an order that is already `readyForDelivery` | derived — the assignment guard's own reason |
| `409` | `CLOSED` | an employee claims a `delivered` order | derived — the assignment guard's own reason |
| `409` | `NOT_READY` | readiness is refused; carries `reasons[]` | asserted twice, with `notPacked` and with `onHold` |
| `409` | `NOT_STARTED` | an issue is reported on a `pending` or `assigned` order | derived — the hold decision's own reason |
| `409` | `PAST_HANDOVER` | an issue is reported on a `readyForDelivery` or `delivered` order | derived — the hold decision's own reason |
| `409` | `ISSUES_REMAIN` | a resume leaves at least one issue open; carries `openIssues` | asserted twice |
| `409` | `ON_HOLD` | a scan or a pack lands on a held order | asserted for the scan |
| `409` | `POSITION_NOT_PICKED` | a pack names a position that is still `awaitingScan` | derived — packing needs a picked position |
| `409` | `ACTOR_NOT_ALLOWED` | the caller does not hold the order he scans, packs or reports on | derived — a second employee token reaches it, which the contract never sends |

The `reasons[]` values of `NOT_READY` are `positionsOpen`, `notPacked`, `onHold`, `pastHandover` and `noParcel`: the
decision's own wire names, never codes and never objects. The decision is first-match-wins and evaluates a missing parcel
first, then the hold, then the unpacked position, so exactly one entry arrives today and the array is the shape, not a
promise of several.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/ReadyForDeliveryDecision.java:38, services/fulfillment/src/main/java/shop/fulfillment/processing/ReadyForDeliveryDecision.java:42, services/fulfillment/src/main/java/shop/fulfillment/processing/ReadyForDeliveryDecision.java:61, services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:227 -->

Every code of the table is defined in source, including the six element 11 derives rather than asserts: `PAST_PICKING`,
`CLOSED` and `ACTOR` are the assignment guard's reason values, `NOT_STARTED` and `PAST_HANDOVER` the hold decision's,
and `POSITION_NOT_PICKED` and `ACTOR_NOT_ALLOWED` are error constants of the kernel. The advice emits the reason value's own
name, so a decision's reason reaches the wire unchanged. `ACTOR` is the one value a client never receives: a claim on a
`pending` order is granted to any caller whose subject is present, and the subject always is.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/FulfillmentErrorAdvice.java:64, services/fulfillment/src/main/java/shop/fulfillment/FulfillmentErrorAdvice.java:67, services/fulfillment/src/main/java/shop/fulfillment/processing/AssignmentGuard.java:44, services/fulfillment/src/main/java/shop/fulfillment/processing/AssignmentGuard.java:62, services/fulfillment/src/main/java/shop/fulfillment/processing/HoldDecision.java:50, services/fulfillment/src/main/java/shop/fulfillment/processing/HoldDecision.java:51 -->

Everything the table does not name is rethrown, so the framework answers `500` with its own error document and no contract
code: that is where `WAREHOUSE_NOT_WIRED`, `CARRIER_NOT_WIRED` and every malformed domain value land. A client cannot tell a
wiring failure from a crash, which is GAP-7.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/FulfillmentErrorAdvice.java:71, services/fulfillment/src/main/java/shop/fulfillment/kernel/FulfillmentException.java:19, services/fulfillment/src/main/resources/application.yml:30 -->

A `400` is outside this vocabulary. No handler advice for a validation failure or an unreadable body exists in the service,
so a body that violates a constraint — a blank `kind`, an empty `productNumbers`, a `confirmation` other than `picked`, a
missing `closedIssueIds` — is answered by the framework's default error document with no `error` code of this contract. GAP-5.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/ScanPosition.java:13, services/fulfillment/src/main/java/shop/fulfillment/processing/PackPositions.java:13, services/fulfillment/src/main/java/shop/fulfillment/processing/ReportIssue.java:13 -->

### 3.3 Idempotency

A repeated command answers `200` with the state it already had, and a granted command answers what it created: the decision
is explicit in the status rather than in a second resource.
<!-- evidence: docs/specs/fulfillment/11-fulfillment-http-api.md:118, services/fulfillment/src/main/java/shop/fulfillment/processing/ProcessingService.java:25 -->

| Operation | First answer | Repeat answer |
| --- | --- | --- |
| `POST /orders`, same order | `201` + `processingId` | `200` + the same `processingId` and `duplicate: true`; no record and no event |
| `POST /orders`, same `orderHash`, different content | — | `409 ORDER_HASH_CONFLICT`; nothing is written |
| `POST …/assignment`, by the holder | `201` + `state: assigned` | `200`, same state, no second claim, no second reservation |
| `POST …/assignment`, by another employee | — | `409 ALREADY_ASSIGNED`; the claim is never queued |
| `POST …/positions/{productNumber}`, same product | `200` + the snapshot | `200`, the position stays `picked`, no second event |
| `POST …/packing`, same product | `200` + the snapshot | `200`, nothing changes, no second event |
| `POST …/readiness` | `200` + the snapshot with its parcel | `200` with the state it already had; the carrier is not asked a second time |
| `POST …/issues` | `201` + `issueId` | `201` with a **new** `issueId`: a report is a new fact every time |
| `POST …/hold/resume` | `200` + `closedIssueIds` | `200` with `closedIssueIds: []`; an order that is not held answers the same |
| `GET …/documents/label` | `200` + `application/pdf` | `200` with the same stored bytes, no second registration |

The identity of "the same order" is the digest of the order's canonical form, not the `orderHash` the client claims: a
second delivery under the same hash answers the processing only when the content's digest agrees with the one the
processing was opened with.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderIntakeService.java:40, services/fulfillment/src/main/java/shop/fulfillment/processing/OrderIntakeService.java:47, services/fulfillment/src/main/java/shop/fulfillment/kernel/Order.java:91 -->

The repeat answers of the table are the decisions' own no-op verdicts applied by the aggregate: the assignment guard's
`DT-07-4` for the owner's re-claim, the readiness decision's `DT-08-5` once the order crossed into `readyForDelivery`, and a
resume that closes only issues that were open before it ran.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/AssignmentGuard.java:46, services/fulfillment/src/main/java/shop/fulfillment/processing/ReadyForDeliveryDecision.java:68, services/fulfillment/src/main/java/shop/fulfillment/processing/ProcessingService.java:159 -->

Nothing but a token identifies a request: there is no idempotency key header, no stored key and no expiry, so a client
cannot carry its own retry identity and cannot make an `POST …/issues` repeatable. That is GAP-6.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/ReportIssue.java:13, services/fulfillment/src/main/java/shop/fulfillment/processing/OrderIntakeService.java:16 -->

A refused command writes nothing — the command and its save share one transaction — so a retry after the cause is gone starts
from the state before the refusal, not from a half-applied one.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/ProcessingService.java:25, services/fulfillment/src/main/java/shop/fulfillment/processing/AssignmentService.java:21 -->
