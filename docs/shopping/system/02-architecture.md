## 2 Architecture

One process, two slices, one shared kernel. The slice is the unit of ownership: every type a slice owns is
package-private, so a second slice cannot compile against it, and the architecture test enforces that
rather than trusting review.

![The one process, its two slices and the shared kernel.](generated/shopping/diagrams/slices.svg){#fig-slices}

<!-- evidence: services/shopping/src/test/java/shop/shopping/ArchitectureTest.java:18-30, services/shopping/src/main/java/shop/shopping/kernel/ShoppingException.java:12-18 -->

### 2.1 The basket slice

The slice is an editor aggregate plus the policies that price and date it, and it is deliberately flat: no
`domain/`, `application/` or `adapter/` sub-package, and the stereotype is visible in the file name.

| Type | Role |
|---|---|
| `BasketEditor` | the only object allowed to change a basket: merge, quantity, selection, removal, handover identity |
| `BasketService` | the primary port: one method per operation, each loading the aggregate, letting it decide and storing it back |
| `BasketPriceCalculation` | element 04's pure calculation over the rows the selection counts |
| `ShipmentEstimates` | element 05's aggregation: group the rows by seller, ask the policy per shipment, keep the latest `to` |
| `OfferAvailabilityDecision` | element 06's total function from one observation to what the basket may say about it |
| `OfferCatalog` and its fixture | element 08's secondary port — package-private, because nothing outside the slice may call it |
| the five controllers and the projection | the wire: six routes, one read model for the list |

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:18-30, services/shopping/src/main/java/shop/shopping/basket/BasketService.java:17-40, services/shopping/src/main/java/shop/shopping/basket/ShipmentEstimates.java:12-26, services/shopping/src/main/java/shop/shopping/basket/OfferAvailabilityDecision.java:7-27, services/shopping/src/main/java/shop/shopping/basket/OfferCatalog.java:8-15 -->

Two properties of this shape matter more than the file list:

- **The aggregate holds no rule of the process.** It reads no clock, publishes nothing and asks nobody
  anything; the instant a handover is composed at is passed in, and the repository drains the event buffer
  after the write. That is what lets the same aggregate be driven by a test, a route and an ingestion
  command without a second wiring (D39).
- **The policies are pure and take configuration, not lookups.** The price calculation and the estimate
  receive their rates and their days in the constructor, so neither reaches a repository, a clock or a
  configuration utility (D58).

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:28-29, services/shopping/src/main/java/shop/shopping/basket/DocumentBasketRepository.java:33-35, services/shopping/src/main/java/shop/shopping/basket/BasketPriceCalculation.java:27-31 -->

### 2.2 The checkout slice

`CheckoutService` implements the one port the basket holds and is the only crossing between the slices. It
refuses a handover older than the one it already holds and records what it accepted. It creates no order,
because the order belongs to the checkout slice's own specification, which is not written yet.

<!-- evidence: services/shopping/src/main/java/shop/shopping/checkout/CheckoutService.java:15-38, services/shopping/src/main/java/shop/shopping/kernel/CheckoutHandover.java:10-16 -->

### 2.3 The shared kernel

`kernel/` holds the types both slices may read: the money type with its single rounding step, the
availability outcome with its blocking set, the estimate, the handover record and its content digest, and
the one unchecked exception the service raises. It holds no slice type, and no slice type escapes it.

<!-- evidence: services/shopping/src/main/java/shop/shopping/kernel/Money.java:7-30, services/shopping/src/main/java/shop/shopping/kernel/Availability.java:23-45, services/shopping/src/main/java/shop/shopping/kernel/Handover.java:43-60 -->

### 2.4 What the architecture test enforces

The enforced properties are the ones a reader would otherwise have to take on trust: no basket type is
referenced from checkout and no checkout type from basket, every slice type stays package-private, and no
operation whose name begins with `reserve` is declared anywhere in `shop.shopping` — the last one is how
"the basket never reserves stock" becomes a gate instead of a claim, and a deliberately fulfilment-shaped
port is asserted to fail the rule so the rule cannot pass by being vacuous.

<!-- evidence: services/shopping/src/test/java/shop/shopping/ArchitectureTest.java:18-30, docs/specs/basket/prove-of-done.md:60-61 -->
