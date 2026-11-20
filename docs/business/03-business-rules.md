## 3 Business rules

Each section is one rule the system implements. Every rule carries the same four labelled parts, in the same order:
what it reads, what it answers, what a deployment can change, and which stored records it consults. The label
"no configuration" means the rule has no key a deployment can set — not that its behaviour is unchangeable.

### 3.1 An order is accepted once, and only once

A handover of an order creates the processing or answers the one that already exists. The duplicate test is not the
client's hash alone: the accepted order's own content digest is compared, so the same hash over different content is
refused instead of silently swallowing an order nobody will ever fulfil.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderIntakeService.java:16-20, services/fulfillment/src/main/java/shop/fulfillment/processing/OrderIntakeService.java:45-51 -->

- **Inputs** — the handed-over order: order hash, placing time, at least one line with product number, name and positive
  quantity, one delivery target of exactly one kind, one payment method code, and the buyer's invoicing data.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/Order.java:44-55, services/fulfillment/src/main/java/shop/fulfillment/kernel/DeliveryLocation.java:13-22 -->
- **Output** — one of: processing created with identity `OP-YYYY-NNNN` and state `pending` (answered `201`); the existing
  processing with `duplicate: true` (answered `200`) and no second position set and no second parcel; a refusal naming
  the missing parts (answered `422`); a conflict when the hash already names a different order (answered `409`).
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderIntakeService.java:38-51, docs/specs/fulfillment/12-order-handover.md:21-26 -->
- **Configuration** — no configuration. The accepted shape is fixed in code; the only deployment fact on this path is the
  `shopping-service` scope that may call the handover.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:75 -->
- **Internal data fetched** — table `order_processing` through `OrderProcessingRepository`, read by `order_hash` and
  written once per accepted order; sequence `order_processing_id_seq` mints the identity.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderIntakeService.java:26-30, services/fulfillment/src/main/resources/db/changelog/0003-order-processing.yaml:37 -->

### 3.2 An accepted order is never edited here (element 01 RULE-2)

Nothing in this context edits an order. The handover is the only operation that receives one, and it turns the
document into one processing; every later command addresses the processing instead, so an amended commitment
cannot be produced here — a changed commitment is a different order, delivered as a second handover.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/Order.java:18-36, services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:44, services/fulfillment/src/main/java/shop/fulfillment/processing/OrderIntakeService.java:38-43 -->

- **Inputs** — the handed-over document: its hash, placing time, lines, delivery, payment and invoicing, and the
  content digest computed from them.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/Order.java:91-94 -->
- **Output** — no edit at all. The processing holds the document once and never writes it back; a second delivery
  with the same content is answered with the processing that already exists, and the same hash over different
  content is refused.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/Order.java:29-36, services/fulfillment/src/main/java/shop/fulfillment/processing/OrderIntakeService.java:45-51 -->
- **Configuration** — no configuration. No key enables or forbids an amendment, because no path offers one.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:44 -->
- **Internal data fetched** — table `order_processing` through `OrderProcessingRepository`, whose row holds the
  aggregate's own snapshot; the order travels inside that snapshot and a later command rewrites the row without
  touching it.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/DocumentOrderProcessingRepository.java:27-41 -->

### 3.3 The delivery and payment methods are opaque codes (element 01 RULE-5)

The order says how the parcel must travel and how the customer paid, and both arrive as strings this context
never enumerates. There is no list of methods to validate against, no mapping table and no default: the code
travels to the carrier as the parcel's service code and to the customer as the delivery method he bought, and
the only test applied here is that the code is present and not blank.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/Payment.java:5-16, services/fulfillment/src/main/java/shop/fulfillment/kernel/Delivery.java:8-13, services/fulfillment/src/main/java/shop/fulfillment/parcel/CarrierRegistration.java:59 -->

- **Inputs** — `delivery.methodCode` and `payment.methodCode`, two non-blank strings.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/Payment.java:12-16, services/fulfillment/src/main/java/shop/fulfillment/kernel/Delivery.java:10-13 -->
- **Output** — the same two strings, unchanged: the delivery code becomes the carrier request's service code and
  the delivery method in the customer's view, and the payment code stays part of the order content nothing here
  interprets.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/parcel/CarrierRegistration.java:59, services/fulfillment/src/main/java/shop/fulfillment/customer/CustomerState.java:56-60 -->
- **Configuration** — no configuration: no key lists the allowed methods and no key maps one code to another.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/Order.java:26-28 -->
- **Internal data fetched** — table `order_processing`: both codes are part of the stored order content and of its
  content digest, so a different code is a different order.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/Order.java:105-108 -->

### 3.4 One employee holds one order

A claim on an order is granted, answered as a no-op, or refused with a reason; the rows are evaluated in order and the
first match wins. The decision owns no state — it computes the verdict and the order applies it.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/AssignmentGuard.java:41-79 -->

The six rows, in evaluation order:

| Row | Order state | Holder | Claimant | Answer |
| --- | --- | --- | --- | --- |
| DT-07-1 | `pending` | nobody | an employee | granted: the claimant becomes the holder |
| DT-07-2 | `pending` | nobody | not an employee | refused: `actor` |
| DT-07-3 | `assigned`, `picking`, `packed` or `onHold` | another employee | an employee | refused: `alreadyAssigned` |
| DT-07-4 | `assigned`, `picking`, `packed` or `onHold` | the claimant | an employee | no-op: no event, no write |
| DT-07-5 | `readyForDelivery` | anybody | an employee | refused: `pastPicking` |
| DT-07-6 | `delivered` | anybody | an employee | refused: `closed` |
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/AssignmentGuard.java:60-78, docs/specs/fulfillment/07-assignment-guard.md:17-22 -->

- **Inputs** — the processing state, the current holder (or none), and the employee claiming.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/AssignmentGuard.java:15 -->
- **Output** — granted (holder set, state `assigned`, one "employee assigned" fact), no-op, or refused with one reason
  value; a refusal never clears the existing holder.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:73-81, docs/specs/fulfillment/07-assignment-guard.md:47-49 -->
- **Configuration** — no configuration. The decision carries no I/O, no clock, no timeout and no retry.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/AssignmentGuard.java:7-13 -->
- **Internal data fetched** — table `order_processing` through `OrderProcessingRepository`; the granted claim writes the
  order and, in the same transaction, the reservation rows (`processing_reservation`, `warehouse_reservation`) through
  `ProcessingReservationRepository`.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/AssignmentService.java:53-68 -->

### 3.5 A claim reserves the stock; readiness books it out

The claim is the moment units are allocated at the warehouse: one allocation per position product, taken inside the
claim's own transaction. If that transaction ends any way but committed, every allocation it took is released, so no
stock is left allocated to an order nobody holds. Allocation is never repeated for a re-claim, and `release` has no
path of its own — its only use is this compensation.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/WarehouseReservationsIntegration.java:12-22, services/fulfillment/src/main/java/shop/fulfillment/processing/WarehouseReservationsIntegration.java:36-45 -->

- **Inputs** — the granted claim's positions: product number, quantity, and the location the warehouse allocated from.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/ProcessingReservation.java:25-31 -->
- **Output** — one reservation id per position product, stored beside the order; the goods issue note and its date are
  recorded on the same row when the order is later marked ready.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/WarehouseGoodsIssueIntegration.java:40-54 -->
- **Configuration** — no configuration of the rule. Whether a warehouse adapter exists at all is deployment wiring: with
  no warehouse token the service boots and refuses the claim with `WAREHOUSE_NOT_WIRED` rather than reserving nothing.
  <!-- evidence: services/fulfillment/src/main/resources/application.yml:32-35, services/fulfillment/src/main/java/shop/fulfillment/processing/WarehouseReservationsIntegration.java:47-50 -->
- **Internal data fetched** — tables `processing_reservation` and `warehouse_reservation` through
  `ProcessingReservationRepository` and `WarehouseReservationRepository`; the allocations themselves live at the
  warehouse and are addressed only by the reservation id we mint.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/WarehouseGoodsIssueIntegration.java:14-18, services/fulfillment/src/main/java/shop/fulfillment/kernel/ReservationId.java:3-8 -->

### 3.6 The pick list is composed from positions and the shelves

The list is generated per order, on request, from the positions still to be walked and one stock reading per product.
It holds one entry per pending position and one stop per place actually walked; the quantities taken plus the shortfall
always equal the quantity ordered. The walk order is a documented default, not a route: nothing blocks a scan out of
sequence.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/PickListCalculation.java:16-31, docs/specs/fulfillment/10-pick-list-calculation.md:150-172 -->

One list belongs to one processing and is never shared: the calculation takes the positions of one order and
answers one list, and no route walks two orders together, so two employees cannot be handed the same walk.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/PickListProjection.java:60-71, services/fulfillment/src/main/java/shop/fulfillment/processing/PickListCalculation.java:16-31 -->

A place is walked only when it has free units. The stop carries both numbers the warehouse reported — free and
already allocated — and only the free one decides whether the employee is sent there, so allocated units never
put a shelf on the walk.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/PickListCalculation.java:117-126, services/fulfillment/src/main/java/shop/fulfillment/processing/PickListCalculation.java:128-136 -->

A list does not change while it is being walked. It is computed when it is asked for and stored nowhere, and no
listener reacts to a stock change, so the list an employee holds stays as printed and a later reading produces
the next list, never a rewritten one (system `GAP-10`, where the absence is recorded).
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/PickListProjection.java:60-71, services/fulfillment/src/main/java/shop/fulfillment/kernel/DomainEvent.java:68 -->

- **Inputs** — the order's positions with their state; one reading of free and allocated units per location for every
  product still pending; the moment the list is generated.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/PickListProjection.java:73-83, services/fulfillment/src/main/java/shop/fulfillment/kernel/StockAtLocation.java:3-7 -->
- **Output** — one list: one entry per position in pending state, stops numbered from 1 across the whole walk (ordered
  by location code, then product number), and one shortfall per position the shelves cannot cover. A failed stock
  reading yields no list at all, never a partial one.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/PickListCalculation.java:103-181, services/fulfillment/src/main/java/shop/fulfillment/processing/PickListProjection.java:105-110 -->
- **Configuration** — `fulfillment.picklist.order` (default `location-code`, alternative `scarcity-first`) and
  `fulfillment.picklist.split` (default `multi-location`, alternative `single-location`); an unknown value falls back to
  the default instead of failing the read.
  <!-- evidence: services/fulfillment/src/main/resources/application.yml:44-46, services/fulfillment/src/main/java/shop/fulfillment/processing/PickListProjection.java:88-105 -->
- **Internal data fetched** — table `order_processing` through `OrderProcessingRepository` for the positions; the stock
  reading comes from the warehouse port `WarehouseStock.stockAt` and is never cached or stored; the watermark it polls
  with lives in table `warehouse_stock_cursor` through `StockCursorRepository`.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/PickListProjection.java:60-71, services/fulfillment/src/main/java/shop/fulfillment/kernel/WarehouseStock.java:20-26, services/fulfillment/src/main/resources/db/changelog/0005-warehouse-state.yaml:42 -->

### 3.7 Only the holder works the order, and a position never goes backwards

Scanning and packing are refused unless the caller holds the order and the order is not held; packing additionally
requires the position to have been scanned. A repeated scan or a repeated pack changes nothing, so retried traffic is
not an error.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:204-215, services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:244-249 -->

- **Inputs** — the processing state, the holder, the calling employee, and the product number being confirmed or packed.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:90-116 -->
- **Output** — the position moves `awaitingScan` to `picked` on a scan, `picked` to `packed` on a pack; the order moves
  `assigned` to `picking` on the first scan and to `packed` when every position is in the box; a refusal when the caller
  is not the holder, when the order is on hold, or when a position is packed before it was scanned.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:86-116, services/fulfillment/src/main/java/shop/fulfillment/kernel/ProcessingState.java:19-27 -->
- **Configuration** — no configuration: the rule reads no property, and the refusal reasons are fixed values.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/FulfillmentException.java:45-48 -->
- **Internal data fetched** — table `order_processing` through `OrderProcessingRepository`; the orders list read model
  (`orders_list`) is refreshed from the same facts, not read by the rule.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/ProcessingService.java:57-63, services/fulfillment/src/main/resources/db/changelog/0004-orders-list.yaml:11 -->

### 3.8 Ready for delivery: three effects, one command, seven rows

Marking an order ready is decided by one table, evaluated in a fixed order, and a grant produces three effects in one
command: the local mark, the carrier's collection booking and the goods issue at the warehouse. The order of evaluation
guarantees that no order leaves on an effect whose precondition is missing.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/ReadyForDeliveryDecision.java:6-17, services/fulfillment/src/main/java/shop/fulfillment/processing/ProcessingService.java:135-142 -->

The rows, in the order they are evaluated:

| Row | Order state | All positions packed | Parcel registered | Answer |
| --- | --- | --- | --- | --- |
| DT-08-7 | any | any | no | refused: `noParcel` |
| DT-08-4 | `onHold` | any | any | refused: `onHold` |
| DT-08-3 | `pending`, `assigned` or `picking` | any | any | refused: `notPacked` |
| DT-08-2 | `packed` | no | yes | refused: `positionsOpen` |
| DT-08-1 | `packed` | yes | yes | granted: mark, book the collection, book the goods out |
| DT-08-5 | `readyForDelivery` | any | yes | no-op: no second collection booking |
| DT-08-6 | `delivered` | any | any | refused: `pastHandover` |
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/ReadyForDeliveryDecision.java:61-92, docs/specs/fulfillment/08-ready-for-delivery-decision.md:22-31 -->

- **Inputs** — the processing state, whether every position is packed, and whether the order has a registered parcel
  with a carrier identity.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/ReadyForDeliveryDecision.java:18 -->
- **Output** — granted, no-op, or refused with exactly one reason value from `positionsOpen`, `notPacked`, `onHold`,
  `noParcel`, `pastHandover`. There is no undo: no reverse edge exists out of `readyForDelivery`.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/ReadyForDeliveryDecision.java:36-58, docs/specs/fulfillment/08-ready-for-delivery-decision.md:93-94 -->
- **Configuration** — no configuration of the rule. The collection booking needs a carrier adapter and the goods issue a
  warehouse adapter; a deployment without one is refused with `CARRIER_NOT_WIRED` or `WAREHOUSE_NOT_WIRED` rather than
  marked ready.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/WarehouseGoodsIssueIntegration.java:56-59, services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelService.java:95-98 -->
- **Internal data fetched** — table `order_processing` through `OrderProcessingRepository`, table `parcel` through
  `ParcelRepository` (via the `Parcels` contract), and table `processing_reservation` through
  `ProcessingReservationRepository` for the goods issue.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/ProcessingService.java:92-99, services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelService.java:54-58 -->

### 3.9 A parcel exists only after the carrier named it

The parcel record is created on the first scan of its order, and only from a carrier's answer: our reference is minted
by us, the carrier identity comes back from registration, and nothing physical is recorded before that answer arrives.
One order carries exactly one parcel, and a repeated registration answers the parcel that already exists.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelEditor.java:56-78, services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelService.java:60-65 -->

- **Inputs** — the processing identity, the order's delivery target and method code, the configured sender, the
  configured weight, and the carrier's answer with its own identity and the printed sequence.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/parcel/CarrierRegistration.java:39-61 -->
- **Output** — one parcel with our reference (`<processingId>-1`), the carrier's identity, the first leg recorded, and a
  "delivery number generated" fact; a registration that asks for more than one parcel is refused, and so is an answer
  whose reference differs from ours.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelEditor.java:67-78, services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelEditor.java:138-150 -->
- **Configuration** — `fulfillment.carrier.weight-grams` (default 1000) and the sender block
  `fulfillment.carrier.sender.street`, `.city`, `.postal-code`, `.country`; the carrier's URL and token are deployment
  secrets and are absent from the configuration file.
  <!-- evidence: services/fulfillment/src/main/resources/application.yml:36-39, services/fulfillment/src/main/java/shop/fulfillment/parcel/CarrierIntegration.java:35-51 -->
- **Internal data fetched** — table `parcel` through `ParcelRepository`, keyed one-to-one by processing id; the served
  label document is stored in table `parcel_label`; the trigger reads table `order_processing` through
  `OrderProcessingRepository`.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelService.java:83-89, services/fulfillment/src/main/resources/db/changelog/0006-parcel.yaml:52-58 -->

### 3.10 Delivery legs move forward only, and the last one closes the order

A carrier observation is applied only forward: a leg already recorded changes nothing, a leg that would move the parcel
backwards is not applied, and a status we cannot map moves nothing while keeping the carrier's raw payload for later
correction. Only the last leg moves the order itself.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelEditor.java:96-109, services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelEditor.java:152-169 -->

- **Inputs** — the carrier's observations for one parcel: the leg it maps to, the carrier's status text, its timestamp and
  its raw payload.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/LegObservation.java:5-18 -->
- **Output** — the parcel's furthest leg and its ordered leg history, one "delivery state notified" fact per newly
  reached leg, one "delivery status unrecognised" fact per unmapped payload, and the order moving `readyForDelivery` to
  `delivered` on the last leg alone.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/DeliveryStateListener.java:40-45, services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:173-186 -->
- **Configuration** — `fulfillment.carrier.poll-interval` (default 60s) sets how often the carrier is read; the first
  carrier publishes no push, so the reading interval is also the delivery-state latency the customer sees.
  <!-- evidence: services/fulfillment/src/main/resources/application.yml:40-41, services/fulfillment/src/main/java/shop/fulfillment/parcel/CarrierPollingIntegration.java:24-31 -->
- **Internal data fetched** — table `parcel` through `ParcelRepository` for every parcel still on its way; the customer
  read models `customer_processing_state` and `customer_delivery_leg` hold the timeline the customer sees.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/parcel/CarrierPollingIntegration.java:51-61, services/fulfillment/src/main/resources/db/changelog/0007-customer-state.yaml:11 -->

### 3.11 An issue is written once, and only one fact ever moves on it

An issue records that the warehouse system and the shelf disagree: its kind, the product, who reported it and when. The
record carries no state of its own, and the one fact that ever changes is the closing timestamp, written only by the
resume command. The read-only dashboard neither closes nor edits anything.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/FulfillmentIssue.java:9-19, docs/specs/fulfillment/04-fulfillment-issue.md:64-70 -->

- **Inputs** — the issue kind (free-form, four known codes), the product number, the reporting employee, and the moment
  of the report; a close carries the identifiers the caller vouches for.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/ProcessingService.java:144-153, services/fulfillment/src/main/java/shop/fulfillment/kernel/IssueKinds.java:10-22 -->
- **Output** — a new immutable record with an identity `ISS-YYYY-NNNN` and an open state, one "issue reported" fact, and
  the hold the issue causes; a close writes the timestamp and nothing else; naming an issue this order does not account
  for is refused.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:145-155, services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:261-268 -->
- **Configuration** — no configuration: the kind set is open in code, and no property narrows or extends it.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/IssueKinds.java:3-9 -->
- **Internal data fetched** — table `order_processing` through `OrderProcessingRepository` (the issues live in its
  snapshot); the dashboard reads tables `issue_dashboard` and `issue_dashboard_order` through
  `IssueDashboardProjection`.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderProcessing.java:23-33, services/fulfillment/src/main/resources/db/changelog/0011-issue-dashboard.yaml:10 -->

### 3.12 Only the employee holding the order reports an issue (element 04 RULE-2)

An issue is written by the employee who holds the order and by nobody else: before the record is created the
aggregate compares the reporter with the current holder, so a report from another employee is refused rather
than attributed. No warehouse signal and no other actor creates an issue — the rule is about who may write the
fact, which is why the record carries the reporter.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:251-258, services/fulfillment/src/main/java/shop/fulfillment/kernel/FulfillmentException.java:42, services/fulfillment/src/main/java/shop/fulfillment/FulfillmentErrorAdvice.java:67 -->

- **Inputs** — the processing, its holder, and the caller's own subject, which the service passes on as the
  reporter.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/ProcessingService.java:111-116 -->
- **Output** — one issue whose reporter is the holder, plus the hold the issue causes; a caller who is not the
  holder is answered `409 ACTOR_NOT_ALLOWED` and nothing is written.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/FulfillmentErrorAdvice.java:67, services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:255-258 -->
- **Configuration** — no configuration: the check reads the holder of the order, not a role, a group or a
  property.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:251-254 -->
- **Internal data fetched** — table `order_processing` through `OrderProcessingRepository`: the holder is part of
  that row, and the new issue joins the issues the row already carries.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderProcessing.java:23-33 -->

### 3.13 One issue holds the whole order; one resume lifts it

Reporting an issue on an order that is being picked or packed puts the whole order on hold, not the position. While an
order is held, scanning and packing are refused and readiness is refused. The only exit is an explicit resume that
leaves no issue open; nothing else — no carrier signal, no warehouse signal, no elapsed time — lifts a hold.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/HoldDecision.java:5-14, docs/specs/fulfillment/09-hold-decision.md:97-111 -->

The seven rows:

| Row | Command | Order state | Open issues after the command | Answer |
| --- | --- | --- | --- | --- |
| H1 | report an issue | `picking` or `packed` | any | granted: record the issue and hold the order |
| H2 | report an issue | `onHold` | at least one | the issue is recorded, no second hold |
| H3 | report an issue | `pending` or `assigned` | any | refused: `notStarted` |
| H4 | report an issue | `readyForDelivery` or `delivered` | any | refused: `pastHandover` |
| H5 | resume | `onHold` | none | granted: close the named issues, return to the state held from |
| H6 | resume | `onHold` | at least one | refused: `issuesRemain`, with the number still open |
| H7 | resume | any other state | any | no-op |
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/HoldDecision.java:46-105, docs/specs/fulfillment/09-hold-decision.md:16-24 -->

- **Inputs** — which command is being applied, the processing state, and the count of issues that remain open after the
  command's own closes.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/HoldDecision.java:15 -->
- **Output** — granted, no-op, or refused with one reason from `notStarted`, `pastHandover`, `issuesRemain`; a grant
  records the state held from, and the resume writes the closing timestamp on the named issues and restores that state.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:270-285 -->
- **Configuration** — no configuration: the decision is clock-free and reads no deadline, escalation or service level.
  <!-- evidence: docs/specs/fulfillment/09-hold-decision.md:102-104 -->
- **Internal data fetched** — table `order_processing` through `OrderProcessingRepository` for the state, the holder and
  the issues; the held marker and the state held from are also written to table `orders_list` for the employee's list.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/ProcessingService.java:127-133, services/fulfillment/src/main/resources/db/changelog/0014-orders-list-restored-state.yaml:12 -->

### 3.14 No money is computed, compared or stored

Money is outside this context. The order carries no prices, no goods issue note amounts are produced here, and the
carrier request passes a cash-on-delivery or declared-value figure through as an opaque value it never reads. The
repeated audit of the source tree finds no amount, price, currency or tax field.
<!-- evidence: docs/specs/fulfillment/spec.md:76, docs/prove-of-done.md:22 -->

- **Inputs** — none. The rule has nothing to read: no amount enters the context from checkout, and the carrier request
  receives whatever its owner supplied without inspecting it.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/CarrierPort.java:14-15 -->
- **Output** — no money value is produced, stored or published; every payload this context serves is quantity, place,
  state and time.
  <!-- evidence: docs/specs/fulfillment/11-fulfillment-http-api.md:138, services/fulfillment/src/main/java/shop/fulfillment/processing/PickList.java:18-19 -->
- **Configuration** — no configuration. There is no currency, tax rate or rounding key anywhere in the deployment
  settings.
  <!-- evidence: services/fulfillment/src/main/resources/application.yml:30-50 -->
- **Internal data fetched** — none. No table of this service holds an amount, so no rule can fetch one.
  <!-- evidence: docs/prove-of-done.md:22 -->
