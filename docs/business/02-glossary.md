## 2 Glossary

One definition per term, alphabetically. A synonym is recorded where the sources use a second word for the same thing;
a rejected name is recorded where this documentation deliberately does not use the word, with the reason.

**Assignee** — the one warehouse employee who holds an order at a given moment. At most one exists per order, and a
second employee is refused rather than queued.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/AssignmentGuard.java:7-15, docs/specs/fulfillment/07-assignment-guard.md:11-13 -->

**Carrier identity** — the identifier a carrier assigns to one parcel. Synonyms in the sources: *waybill*, *tracking
number*, *numerNadania*, *shipment id*, and the board's *delivery number*; the identifier the specification calls
*provider ID* is the same value. It is only ever taken from a carrier response, never invented and never derived from
our own reference.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/CarrierIdentity.java:3-9, docs/specs/fulfillment/spec.md:143 -->

**Collection request** — the booking that asks a carrier to pick a parcel up, made as the second effect of marking an
order ready for delivery. At most one is open per parcel; the operation is not idempotent, so a repeat books a second
pickup.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelService.java:72-81, services/fulfillment/src/main/java/shop/fulfillment/kernel/CarrierPort.java:36-43 -->

**Consignment note** — the carrier's own printable document for one parcel, served as a PDF from data the carrier
holds. Rejected name: *waybill* for the document — the waybill is the identifier, not the paper; rejected name:
*goods issue note* — that is the Polish warehouse document, and it is a different document.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/CarrierPort.java:28-34, services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelLabelController.java:24 -->

**Customer state** — the customer-facing view of one processing: the order lines, where the parcel goes, the state in
customer words, the leg timeline and a hold marker. It never carries the assignee or the issue.
<!-- evidence: docs/specs/fulfillment/11-fulfillment-http-api.md:33, docs/specs/fulfillment/spec.md:470-471 -->

**Delivery leg** — one of the six ordered steps a parcel passes: L1 registered, L2 collected by the provider, L3
assigned for pickup, L4 picked up, L5 in the sorting facility, L6 the customer took it. L1 and L2 acknowledge our own
actions; L3 to L6 are carrier-owned facts.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/Leg.java:3-16 -->

**Delivery state notification** — the fact that a carrier reported one leg for one parcel. Nothing announces a hold, an
issue or a resume as a delivery state.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/DomainEvent.java:97-98, docs/specs/fulfillment/03-parcel.md:89-90 -->

**Goods issue note (WZ)** — the Polish warehouse document that would state what left the warehouse with the amounts an
invoicing boundary owns. It is outside the delivered system; only the carrier label is printed here.
<!-- evidence: docs/decisions/operator-input.md:10, docs/specs/fulfillment/spec.md:89 -->

**Hold** — the state `onHold` of an order, caused by at least one open issue, and the only state a polled signal never
enters or leaves. Rejected names: *blocking issue* (severity belongs to the basket's vocabulary, not here), *pause* or
*flag* (a hold is the order's state, not an attribute beside one).
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/ProcessingState.java:10-17, docs/specs/fulfillment/08-ready-for-delivery-decision.md:74-77 -->

**Issue** — one report that the warehouse system and the shelf disagree. Synonyms in the sources: the board's
*problems with fulfillment*; rejected names: *problem*, *defect* (an issue is not a defect of the order) and
*stock count mismatch* (a situation, not a term).
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/FulfillmentIssue.java:9-19, docs/specs/fulfillment/04-fulfillment-issue.md:8-10 -->

**Issue kind** — the free-form code describing what disagrees: `LOST`, `DESTROYED`, `STOLEN`, `WRONG_LOCATION`, or any
other code entered verbatim. The set is open on purpose. Rejected member: `DAMAGED`, which exists only in a superseded
document.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/IssueKinds.java:3-22, docs/specs/fulfillment/04-fulfillment-issue.md:44-52 -->

**Issue resolver** — the role that works the read-only issue dashboard and may resume a held order. The event-storming
board carries no such actor; the role exists so the warehouse problem has an owner.
<!-- evidence: docs/specs/fulfillment/spec.md:119, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:91-95 -->

**Order** — the finished, unchangeable document saying what the customer bought and under which terms: lines,
delivery target, payment method code, invoicing data. It carries no identity, no status, no history and no money.
Rejected reading: *order* as a mutable record owned by fulfillment — a changed commitment is a different order.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/Order.java:18-36, docs/specs/fulfillment/01-order.md:8-9 -->

**Order hash** — the idempotency key of the handover: the client's claim of the order's identity. Fulfillment keeps it
and compares the order's own content digest beside it, so the same hash over different content is a conflict, not a
duplicate.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderIntakeService.java:16-20, services/fulfillment/src/main/java/shop/fulfillment/kernel/Order.java:71-90 -->

**Order processing** — the mutable record of one order's journey: who holds it, how far each position got, whether it is
on hold, which parcel carries it, which issues it accounts for, and the state it was held from.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderProcessing.java:14-33 -->

**Order processing id** — our synthetic identity of one processing, `OP-YYYY-NNNN`, minted when the order is accepted.
It addresses claims, pick lists, parcels and the customer view; the order hash stays the idempotency key.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/OrderProcessingId.java:5-11 -->

**Parcel** — one consignment of one order: our reference, the carrier's identity, the sequence and service printed on
the label, the legs reached and every carrier payload we could not map. One parcel exists per processing.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/parcel/Parcel.java:16-36, docs/decisions/operator-input.md:14 -->

**Parcel reference** — our own correlation key of one parcel, at most 50 characters, addressed by registration, label
and collection. Rejected reading: the reference is never the carrier's number, and a printed label never gives it back.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/ParcelReference.java:3-17 -->

**Pick list** — the ordered walk for one order: per position the product, the quantity to take, the place to take it
from and what is free there, plus what the shelf cannot cover. The walk order is advisory; a scan confirms a position.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/PickList.java:10-24, docs/specs/fulfillment/10-pick-list-calculation.md:186-188 -->

**Position** — one order line as work to be done in the warehouse: product number, name, quantity and its own state.
Rejected name: *line* for this — a line is order content and never changes; a position is processing state.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/OrderPosition.java:5-9, services/fulfillment/src/main/java/shop/fulfillment/kernel/OrderLine.java:5-12 -->

**Position state** — `awaitingScan`, then `picked` when a scan confirmed it, then `packed` when it went into the box.
It never regresses. `awaitingScan` is the specification's own word for the unnamed pre-scan state.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/PositionState.java:3-25, docs/specs/fulfillment/02-order-processing.md:35-38 -->

**Processing state** — one of `pending`, `assigned`, `picking`, `packed`, `readyForDelivery`, `onHold`, `delivered`. A
state changes only through a command; there is no edge from `pending` or `assigned` into `onHold`, and none out of
`delivered`.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/ProcessingState.java:3-17 -->

**Reservation** — one allocation of units at one warehouse location, taken on the granted claim and booked out when the
order is marked ready. It is kept beside the order, never on it.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/ProcessingReservation.java:14-31, services/fulfillment/src/main/java/shop/fulfillment/processing/AssignmentService.java:63-68 -->

**Scan** — the act that confirms one position by naming its product. One scan closes one position; repeating it changes
nothing; a scan is never required in walk order.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:83-98, docs/specs/fulfillment/10-pick-list-calculation.md:190 -->

**Shortfall** — the quantity of one position the warehouse cannot cover, stated per product. It is carried beside the
pick list, so a gap is never hidden.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/PickListCalculation.java:176-181, docs/specs/fulfillment/10-pick-list-calculation.md:41 -->

**Stock at location** — how many units of one product are free and how many are already allocated at one place: two
independent sums, never derived from each other. Only the free number decides whether a place is walked.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/StockAtLocation.java:3-7, docs/specs/fulfillment/10-pick-list-calculation.md:154 -->

**Stop** — one place to walk to for one position, with the quantity to take there and its number in the walk. Stops are
never merged, even when two products sit on the same shelf.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/PickList.java:60-79, docs/specs/fulfillment/10-pick-list-calculation.md:160 -->

**Warehouse employee** — the actor who claims an order, scans, packs, marks it ready, reports a warehouse problem and
prints the label. The board's actors are the employee and the customer only.
<!-- evidence: docs/specs/fulfillment/spec.md:95, docs/specs/fulfillment/spec.md:119 -->
