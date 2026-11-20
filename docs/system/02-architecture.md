## 2 Architecture

Fulfillment is one bounded context, built as one deployable: one container, one PostgreSQL schema, one root
package with 128 source files under it. The context is the unit of ownership — the processing of an order,
its parcel and the four screens the lane needs — and no other service in this repository reads its tables.
<!-- evidence: services/fulfillment/src/main/resources/application.yml:2, services/fulfillment/k8s/base/deployment.yaml:2-6, docs/specs/fulfillment/spec.md:67-68 -->

### 2.1 Why the domain is one package graph

The process aggregate joins the two halves that the board names separately. A scan is what registers the
parcel, so the registration has to happen inside the scan's own transaction: an order that is `picking` owns
exactly one parcel, and that parcel exists from the moment the first scan commits. The same aggregate carries
the issue side, because an issue does not create a second lifecycle — it moves the whole order into `on hold`
and back, which is a state of the processing, not an entity beside it.
<!-- evidence: docs/specs/fulfillment/spec.md:85, services/fulfillment/src/main/java/shop/fulfillment/processing/ProcessingService.java:62-77, services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:145-155 -->

The parcel is still an aggregate of its own, with its own repository, its own table and its own rules: a leg
moves forward only, a carrier identity is only ever assigned, and a split registration is refused. Putting it in the
same process keeps one transaction across the scan and the registration; keeping it a separate aggregate
keeps the carrier's record out of the order's snapshot.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelEditor.java:102-109, docs/specs/fulfillment/03-parcel.md:73, services/fulfillment/docs/phase-1-decisions.md:175 -->

Because the two write slices may not import one another, the seam between them is a contract port declared in
the shared kernel and implemented by the parcel slice: the processing slice asks a kernel interface for a
parcel identity and a collection request, and never sees a parcel type. The reverse edge — a carrier leg
reaching the order so the last one closes it — travels as a domain event consumed inside the same process,
which is why the graph stays acyclic.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/Parcels.java:12-30, services/fulfillment/AGENTS.md:22-24, services/fulfillment/src/main/java/shop/fulfillment/processing/DeliveryStateListener.java:34-41 -->

### 2.2 The kernel and the five slices

| Package | Kind | Files | Visibility |
| --- | --- | --- | --- |
| `kernel` | the immutable vocabulary every slice reads: the order, the delivery, the parcel identity, the issue, the processing state, the domain events and the three ports | 44 | every file declares a public top-level type <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/DomainEvent.java:44, services/fulfillment/src/main/java/shop/fulfillment/kernel/Order.java:20, services/fulfillment/src/main/java/shop/fulfillment/kernel/CarrierPort.java:14, services/fulfillment/AGENTS.md:15 --> |
| `processing` | the order fulfillment process aggregate, its three decisions, the pick list calculation, its repository and services, the intake, assignment and processing routes | 48 | no public top-level type <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/package-info.java:2-9, services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:40 --> |
| `parcel` | the parcel aggregate, its repository, the carrier adapter, the status poller, the label route | 12 | no public top-level type <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/parcel/package-info.java:2-7, services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelEditor.java:34 --> |
| `orders` | the orders list read model and its route | 4 | no public top-level type <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/orders/package-info.java:2-6, services/fulfillment/src/main/java/shop/fulfillment/orders/OrdersListProjection.java:44 --> |
| `issues` | the resolver's issue dashboard and its route | 4 | no public top-level type <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/issues/package-info.java:2-5, services/fulfillment/src/main/java/shop/fulfillment/issues/IssueDashboardProjection.java:48-50 --> |
| `customer` | the customer's state view and its route | 4 | no public top-level type <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/customer/package-info.java:2-5, services/fulfillment/src/main/java/shop/fulfillment/customer/CustomerStateProjection.java:48 --> |

Two packages are neither model nor slice: `tools` holds the JSON mapping, the feature configuration and the
event-type registry, and `mediators` holds a package declaration and nothing else — no mediator class exists,
because the one cross-slice seam is a kernel port instead.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/tools/JsonConfiguration.java:12-21, services/fulfillment/src/test/java/shop/fulfillment/ArchitectureDescription.java:30-32, services/fulfillment/docs/phase-1-decisions.md:174 -->

### 2.3 The rule that enforces the split

Three gates hold the shape, and all three are executable:

- **Visibility.** The kernel is public; every slice type is package-private, so no class outside a slice can
  name one even if it wanted to. The style gate declares that rule per slice package for four of the five
  slices; the `customer` slice carries no such declaration, and GAP-2 records what that leaves ungated.
  <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/.sqt.yaml:16, services/fulfillment/src/main/java/shop/fulfillment/orders/.sqt.yaml:14, services/fulfillment/src/main/java/shop/fulfillment/issues/.sqt.yaml:14, docs/arch/microservice-java-spring/code-structure.md:31 -->
- **Dependencies and isolation.** 38 architecture rules run as one test: six rule sets per slice package plus
  one rule per forbidden direction of the graph — the write slices may not reach the read slices, the read
  slices may not reach either write slice, the two write slices may not reach each other, and the kernel may
  reach no slice.
  <!-- evidence: services/fulfillment/src/test/java/shop/fulfillment/ArchitectureTest.java:121-123, services/fulfillment/src/test/java/shop/fulfillment/ArchitectureTest.java:150-152, services/fulfillment/src/test/java/shop/fulfillment/ArchitectureTest.java:170-183, services/fulfillment/src/test/java/shop/fulfillment/ArchitectureTest.java:199-202 -->
- **Package-private adapters.** The model may not be named outside its slice, and an adapter may not be named
  outside its package, so a repository or a controller cannot leak the aggregate it wraps.
  <!-- evidence: services/fulfillment/src/test/java/shop/fulfillment/ArchitectureDescription.java:73-88, services/fulfillment/src/main/java/.sqt.yaml:37-40 -->

The gates are proven green on the recorded run: the architecture test reports 38 tests, 0 failures, and the
style gate reports 213 files with 0 errors.
<!-- evidence: docs/prove-of-done.md:12, docs/prove-of-done.md:11 -->

### 2.4 The slices, as the shipped figure draws them

![The five slice packages of the fulfillment service and the edges between them.](generated/diagrams/fulfillment-slices.svg){#fig-slices}

@fig-slices is drawn from the package documentation of the five slices. It shows the two write slices, the
three read slices, the contract-port edge from the processing slice to the parcel slice, and one event edge
from the processing slice to each read slice.
<!-- evidence: docs/diagrams/fulfillment-slices.mmd:10-22 -->

Two things the figure does not draw are worth knowing before it is used as the map. The kernel is absent from
it, although it is the package every other package depends on; and the parcel slice's own events are not drawn
to their consumers — the customer view records three of them, and one reaches the processing slice to close the
order. GAP-5 records the difference between the figure and the code.
<!-- evidence: docs/diagrams/fulfillment-slices.mmd:1-8, services/fulfillment/src/main/java/shop/fulfillment/customer/CustomerStateProjection.java:109-124, services/fulfillment/src/main/java/shop/fulfillment/processing/DeliveryStateListener.java:34-41 -->

The service's own `AGENTS.md` still counts four slices and omits the `issues` package, which is why the
figure's caption notes the fifth. GAP-3 records it.
<!-- evidence: services/fulfillment/AGENTS.md:8, docs/diagrams/fulfillment-slices.mmd:8 -->

### 2.5 The two services, and what fulfillment touches

![The two services of the repository, the identity provider, the datastore, the broker and the four external systems.](generated/diagrams/context-services.svg){#fig-context-services}

@fig-context-services draws the repository's whole platform, so only half of it concerns this document. The
fulfillment service's outbound edges are the token issuer it validates against, the PostgreSQL instance that
holds its schema, the warehouse it reads stock from and the carrier it registers parcels with. The catalog
service, the broker and the shopping consumer belong to the other bounded context: fulfillment has no edge to
any of them.
<!-- evidence: docs/diagrams/context-services.mmd:21-47, services/fulfillment/src/main/resources/application.yml:17-21, docs/specs/fulfillment/spec.md:83 -->

The CRM node is drawn dashed on purpose: it is the customer master, and the service holds no customer identity,
so no integration exists. The edge that is missing from the figure by design is the one from the broker:
`POST /orders` is the only cross-context transport, decided as a register entry.
<!-- evidence: docs/diagrams/context-services.mmd:44-46, docs/specs/fulfillment/spec.md:83, services/fulfillment/k8s/infra/kafka.yaml:1 -->

### 2.6 What the architecture costs, and where it departs from the pattern

Three recorded deviations matter to a reader of the code:

- **The read side is not uniform.** The pick list, the processing snapshot and the workstation screen answer
  a question about the aggregate's current state rather than about a history, so a materialised table would
  duplicate the write model and be stale exactly where the contract says the answer is read straight after a
  command; the orders list, the issue dashboard and the customer state do own a table, and the label is a
  stored document. §7 lists the four read surfaces and what each consumes, and does not repeat it here.
  <!-- evidence: services/fulfillment/docs/phase-1-decisions.md:143, services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelLabelProjection.java:49-60 -->
- **The contract port lives in the kernel, not in the consumer's package.** Either write slice importing the
  other is forbidden in both directions, so the port sits in the one package both may read.
  <!-- evidence: services/fulfillment/docs/phase-1-decisions.md:174, services/fulfillment/src/main/java/shop/fulfillment/kernel/Parcels.java:12-30 -->
- **The aggregate snapshot is not published.** The pattern asks an adapter to publish the snapshot and the
  events; only the events are published, because the snapshot type is package-private and has no subscriber.
  <!-- evidence: services/fulfillment/docs/phase-1-decisions.md:59, services/fulfillment/src/main/java/shop/fulfillment/processing/DocumentOrderProcessingRepository.java:87-91, docs/arch/microservice-java-spring/persistence-adapters.md:84 -->

The deployment shape that follows from all of this — one container, one replica, four probes and a read-only
filesystem — is a manifest fact and is tabulated in §10 rather than repeated here.
<!-- evidence: services/fulfillment/k8s/base/deployment.yaml:6, services/fulfillment/k8s/base/deployment.yaml:73-86 -->
