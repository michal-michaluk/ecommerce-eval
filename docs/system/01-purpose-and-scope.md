## 1 Purpose and scope

The fulfillment service turns a finished, unchangeable order into a delivered parcel. A warehouse employee
claims the order exclusively, a pick list walks the warehouse, every position is confirmed by scanning, the
parcel is registered with a carrier before anything physical exists, and every delivery state and every
warehouse problem reaches the customer.
<!-- evidence: docs/specs/fulfillment/spec.md:7-9, services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:62-66 -->

The service is the writer of one thing: what happened to that order after checkout. It holds the processing
of the order, the parcel record, and the one document it prints itself — the carrier's label. It owns neither
the order content, nor the stock, nor the customer master, nor anything the carrier knows.
<!-- evidence: docs/specs/fulfillment/spec.md:10-11, services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelEditor.java:66-81 -->

### 1.1 Where the responsibility starts and ends

| Party | Owns | What this service holds of it |
| --- | --- | --- |
| Checkout, through `POST /orders` | the order's content and its immutability; the order arrives finished and is never edited here | one copy of the received order inside its own row, plus a digest of that content, used to tell a repeat delivery from a different order under a reused hash <!-- evidence: docs/specs/fulfillment/spec.md:54, services/fulfillment/src/main/java/shop/fulfillment/kernel/Order.java:91-94, services/fulfillment/src/main/java/shop/fulfillment/processing/OrderIntakeService.java:40-48 --> |
| The warehouse (InvenTree) | stock truth, allocation mechanics and movements | the reservation identifiers of the units it allocated for one claim, the stock observations it reported, and the goods-issue document number it answered — never a quantity this service computed <!-- evidence: docs/specs/fulfillment/spec.md:54-55, docs/api/warehouse-api.md:20-22, docs/specs/fulfillment/13-warehouse-port.md:38-45 --> |
| The carrier | the carrier identity, the printed label and every delivery leg | that identity as an opaque string, the label bytes as delivered, and the furthest leg it reported — nothing authored here <!-- evidence: docs/specs/fulfillment/spec.md:55-57, docs/specs/fulfillment/03-parcel.md:64, services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelLabelProjection.java:49-60 --> |
| Checkout and the invoicing boundary | money, VAT, delivery cost | no amount, price, currency or total: none is computed, compared or stored, and no such field exists <!-- evidence: docs/specs/fulfillment/spec.md:76, services/fulfillment/src/main/java/shop/fulfillment/kernel/Payment.java:8 --> |
| The customer master (CRM) | who the customer is | no customer identity at all, which is why the customer route cannot check that a caller owns the order: the scope is the only gate <!-- evidence: docs/specs/fulfillment/spec.md:87, docs/decisions/operator-input.md:12 --> |

The order received is a value object: content only, no identity, and the processing identifier is minted at
intake. The schema backs that up with a unique index on the order hash, so one order cannot open two
processings even if two deliveries race.
<!-- evidence: docs/specs/fulfillment/spec.md:78, services/fulfillment/src/main/resources/db/changelog/0003-order-processing.yaml:46-50 -->

### 1.2 What it owns, in numbers

- **14 operations** are registered: 2 on `/orders`, 1 on `/issues`, 7 on the processing resource, 1 on the
  pick list, 1 on the workstation screen, 1 on the customer state and 1 on the label.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderIntakeController.java:28, services/fulfillment/src/main/java/shop/fulfillment/orders/OrdersListController.java:25, services/fulfillment/src/main/java/shop/fulfillment/issues/IssueDashboardController.java:26, services/fulfillment/src/main/java/shop/fulfillment/processing/ProcessingSnapshotController.java:24, services/fulfillment/src/main/java/shop/fulfillment/processing/PickListController.java:23, services/fulfillment/src/main/java/shop/fulfillment/processing/PickWorkstationController.java:24, services/fulfillment/src/main/java/shop/fulfillment/customer/CustomerStateController.java:26, services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelLabelController.java:24 -->
- **7 processing states**, one of which is the hold: pending, assigned, picking, packed, on hold, ready for
  delivery, delivered. Every transition is a command; no state is written by a read.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/ProcessingState.java:11-17, docs/specs/fulfillment/02-order-processing.md:41-56 -->
- **4 read surfaces**: three own a table, one is derived on read; the label is served as a stored document
  rather than read from a model. §7 lists them, the label beside them, and what each consumes.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/orders/OrdersListProjection.java:125, services/fulfillment/src/main/java/shop/fulfillment/customer/CustomerStateProjection.java:178, services/fulfillment/src/main/java/shop/fulfillment/issues/IssueDashboardProjection.java:149 -->
- **2 scheduled pollers**, both at a 60-second default interval, described in §12.
  <!-- evidence: services/fulfillment/src/main/resources/application.yml:40-41, services/fulfillment/src/main/resources/application.yml:49-50 -->
- **2 write aggregates**: the order processing (48 source files in its package) and the parcel record (12).
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/package-info.java:2-9, services/fulfillment/src/main/java/shop/fulfillment/parcel/package-info.java:2-7 -->

The warehouse and the carrier are real adapters, not simulators: each is wired from configuration and a
token, and the tests bring their own in-process doubles. A deployment that sets no token for one of them
gets no adapter at all rather than a stub that pretends.
<!-- evidence: docs/decisions/operator-input.md:11, docs/specs/fulfillment/spec.md:88, services/fulfillment/src/main/java/shop/fulfillment/parcel/CarrierIntegration.java:45-47, services/fulfillment/src/main/java/shop/fulfillment/processing/InvenTreeWarehouseIntegration.java:34-39 -->

### 1.3 Deliberately out of scope

| Not built here | Because |
| --- | --- |
| The goods issue note (WZ) | no invoicing contract supplies the amounts or the receiver party, so the document could only be invented <!-- evidence: docs/decisions/operator-input.md:10, docs/specs/fulfillment/spec.md:89 --> |
| More than one parcel per processing | one processing owns exactly one parcel; a registration that asks for a split is refused, not approximated <!-- evidence: docs/decisions/operator-input.md:14, docs/specs/fulfillment/spec.md:84, services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelEditor.java:138-142 --> |
| Paging on the two collections | one envelope and no parameter; no volume figure exists to page against <!-- evidence: docs/decisions/operator-input.md:13, docs/specs/fulfillment/spec.md:90 --> |
| Returns and refunds, e-invoicing, the CMR note | outside the lane's material: the board's decision table excludes the CMR for a domestic parcel and no source funds the rest <!-- evidence: docs/specs/fulfillment/spec.md:54-57, docs/specs/fulfillment/spec.md:189 --> |
| A pickup-point read model and pick-route optimisation | both deferred with their elements, so the code carries the documented default instead <!-- evidence: docs/specs/fulfillment/spec.md:189, docs/specs/fulfillment/spec.md:59-60 --> |
| A broker, an outbox or any message published off-process | the handover is HTTP and everything inside the process is an in-process event <!-- evidence: docs/specs/fulfillment/spec.md:83, docs/arch/microservice-java-spring/integration-events.md:18 --> |

Each exclusion above is a recorded decision rather than an omission; §13 states where the decisions this
system did take are written.
<!-- evidence: docs/specs/fulfillment/spec.md:70-90 -->

One consequence is worth stating because a reader will ask for it: the contract's document route names two
documents, and only the label is served. The goods-issue-note route answers `401` without a token and `403`
with any token, because no route is registered for it — the shape `denyAll()` gives an undeclared path. The
register puts the document itself out of scope (`D15`).
<!-- evidence: docs/specs/fulfillment/11-fulfillment-http-api.md:17, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:54, services/fulfillment/docs/phase-1-decisions.md:286 -->

### 1.4 What this document covers, and what it hands over

| Question | Chapter |
| --- | --- |
| what the service is made of and why it is one package graph | §2 |
| the rules every operation obeys — authentication, the error type, events, money, idempotency | §3 |
| the scope-to-route matrix | §4 |
| every table and column | §5 |
| how the schema was reached, change set by change set | §6 |
| how a command becomes a row, and what the read side recomputes | §7 |
| the columns that hold a payload rather than a value | §8 |
| every domain event, its producer and its consumers | §9 |
| what runs where, and with what probes | §10 |
| what the telemetry surface is, and what it does not define | §11 |
| what an operator sees at runtime | §12 |
| the decision records this document indexes, and where the decisions that exist are written | §13 |
| the numbered gaps of this document | §14 |

The volume, latency and availability this service is expected to sustain are absent from every source, so
nothing in this document states one; GAP-4 records it.
<!-- evidence: docs/specs/fulfillment/spec.md:110, docs/decisions/genre-provenance.md:43-45 -->
