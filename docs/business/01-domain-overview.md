## 1 Domain overview

Fulfillment turns a finished, unchangeable order into a delivered parcel. A warehouse employee owns the order alone,
a pick list walks the warehouse, every position is confirmed by scanning, the parcel is registered with a carrier
before it exists, and every delivery state — and every warehouse problem — reaches the customer.
<!-- evidence: docs/specs/fulfillment/spec.md:7-9 -->

The business reason is stated once: after checkout, the shop must physically deliver the goods and keep the customer
informed. The three measures of success are that an order is never processed twice, that every state change reaches the
customer view, and that the parcel carries a carrier identifier before the first delivery leg.
<!-- evidence: docs/specs/fulfillment/spec.md:96, docs/specs/fulfillment/spec.md:97 -->

### Who acts in this domain

| Actor | What this actor does here | Where the right is granted |
| --- | --- | --- |
| Warehouse employee | lists current orders, claims one order, reads the pick list, confirms each position by scanning, packs, marks the order ready for delivery, reports a warehouse problem, prints the parcel label | `fulfillment-employee` scope on `/orders`, the processing reads and every processing command <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:75-76, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:84-94, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:110-114 --> |
| Issue resolver | reads the read-only dashboard of every open issue, opens an order's processing state, and resumes a held order once the issues are closed | `fulfillment-resolver` scope on `GET /issues`, `GET /processing/{processingId}` and `POST …/hold/resume` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:108, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:94, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:118 --> |
| Customer | follows the processing and delivery states of an order; reads only, and sees neither the assignee nor the issue | `fulfillment-customer` scope on `GET /processing/{processingId}/state` only <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:116, docs/specs/fulfillment/11-fulfillment-http-api.md:60-64 --> |

The issue resolver is a role the specification introduces, not one the event-storming board carries: the board's only
actors are the employee and the customer, and the resolver exists because the narration gives the warehouse problem to
"someone else". The right to lift a hold for that role is therefore a recorded decision, not a fact of the domain.
<!-- evidence: docs/specs/fulfillment/spec.md:119, docs/specs/fulfillment/spec.md:81 -->

The customer endpoint accepts any caller holding the customer scope; it does not check that the caller owns the order,
because the order carries no customer identity. The operator recorded this as an accepted risk rather than a defect.
<!-- evidence: docs/decisions/operator-input.md:12, docs/specs/fulfillment/spec.md:87 -->

### Who is not an actor

- **The carrier.** It registers parcels, prints labels, collects and reports legs; its own data, its status codes and
  its legal delivery stay on its side of the boundary.
  <!-- evidence: docs/specs/fulfillment/spec.md:10-11 -->
- **The warehouse system.** It owns stock truth and allocation; fulfillment asks it and never reproduces its answers.
  <!-- evidence: docs/specs/fulfillment/spec.md:50-51 -->
- **Checkout / shopping.** It produces the finished order and owns the delivery promise; fulfillment executes the
  delivery method it receives and never renegotiates it.
  <!-- evidence: docs/specs/fulfillment/spec.md:69 -->
- **The invoicing boundary.** It owns the amounts printed on the goods issue note; fulfillment computes no money.
  <!-- evidence: docs/specs/fulfillment/spec.md:76 -->
- **The seller party.** The seller's name, address and tax id print on the carrier label but are configuration, not
  order content, so no actor maintains them per order.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/parcel/CarrierRegistration.java:14-27 -->

### The twelve capabilities, in two value streams

The main stream carries an order from acceptance to a tracked delivery.

| # | Capability | How the business gets it |
| --- | --- | --- |
| 1 | list current orders with their processing state | `GET /orders` |
| 2 | claim one order exclusively | `POST /processing/{processingId}/assignment` |
| 3 | produce a pick list: product, quantity, location | `GET /processing/{processingId}/pick-list` |
| 4 | reserve the products at the warehouse | placed on the granted claim, inside the same claim command |
| 5 | confirm each position by scanning it | `POST /processing/{processingId}/positions/{productNumber}` |
| 6 | mark the order done, ready for delivery | `POST /processing/{processingId}/readiness` |
| 7 | register the parcel and obtain the carrier's identifier | placed on the first scan, inside that scan command |
| 8 | follow every delivery leg and notify the customer | the carrier status poll feeding `GET /processing/{processingId}/state` |
| 11 | print the goods issue note and the label | `GET /processing/{processingId}/documents/label` serves the carrier's label; the goods issue note is out of scope |
<!-- evidence: docs/specs/fulfillment/spec.md:99-106, docs/specs/fulfillment/spec.md:109, docs/specs/fulfillment/spec.md:85 -->

The exception stream exists because warehouse reality can disagree with the record. It holds three capabilities, and
it is the only way back into the main stream once an order is held.

| # | Capability | How the business gets it |
| --- | --- | --- |
| 9 | report a warehouse problem and put the order on hold | `POST /processing/{processingId}/issues` |
| 10 | see every open issue in one read-only dashboard | `GET /issues` |
| 12 | resume an order from hold once every issue is closed | `POST /processing/{processingId}/hold/resume` |
<!-- evidence: docs/specs/fulfillment/spec.md:107-110, docs/specs/fulfillment/spec.md:82 -->

Capability 11 is delivered in half: the carrier's label is served, and the Polish goods issue
note is not part of the delivered system because no invoicing contract supplies its amounts or its receiver party.
<!-- evidence: docs/decisions/operator-input.md:10, docs/specs/fulfillment/spec.md:89 -->

### Where fulfillment sits

![Where fulfillment sits: the order arrives from checkout, is claimed and picked in the warehouse, is carried by the carrier, and the customer follows the delivery state.](generated/diagrams/business-1-where-fulfillment-sits.svg){#fig-business-1-where-fulfillment-sits width=100%}

The figure is a Mermaid flow of the participants; it is not a BPMN-conformant model and carries no pools, gateways or
message semantics. The four ports it draws are the only ways out of the service, and the two inbound reads are polls
because neither the warehouse nor the first carrier can notify us.
<!-- evidence: docs/specs/fulfillment/spec.md:50-52, services/fulfillment/src/main/java/shop/fulfillment/kernel/WarehouseStock.java:15-16, services/fulfillment/src/main/java/shop/fulfillment/parcel/CarrierPollingIntegration.java:13-18 -->

### Where the responsibility ends

Fulfillment owns the processing of an order, the parcel record and the documents the context must print. It does not own
the order content, the stock, the customer master or the carrier's own data.
<!-- evidence: docs/specs/fulfillment/spec.md:10-11 -->

The order crosses the boundary once, complete and unchangeable; a later change of the commitment is a different order
produced outside the system, which is why order identity is content, not a number.
<!-- evidence: docs/specs/fulfillment/12-order-handover.md:31-33, docs/specs/fulfillment/12-order-handover.md:28-29 -->

Two areas are deliberately outside: returns and refunds, and e-invoicing; carrier and warehouse adapter internals are
specified elsewhere and only their contracts live here.
<!-- evidence: docs/specs/fulfillment/spec.md:56, docs/specs/fulfillment/spec.md:58-59 -->

No money is computed, compared or stored anywhere in this domain; an amount appears only as an opaque value handed over
by its owner.
<!-- evidence: docs/specs/fulfillment/spec.md:76, services/fulfillment/src/main/java/shop/fulfillment/kernel/CarrierPort.java:14-15 -->
