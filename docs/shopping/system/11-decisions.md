## 11 Recorded decisions

This repository keeps no architecture decision records — the index in chapter 12 is empty, and it says so
rather than being filled. What it does keep is a decision register beside the specification: one row per
decision, each naming what was decided, why, and what it superseded. That register is the only place the
rationale of this build survives, so the ones that shaped the code are indexed here.

<!-- evidence: docs/specs/basket/log.md:1-4, docs/specs/basket/log.md:108 -->

### 11.1 Scope decisions

| Id | Decision | Consequence in the code |
|---|---|---|
| D38 | the offer read is answered by a fixture adapter in this increment | one implementation of the port, seeded from a ConfigMap key |
| D39 | the transport of offer events is deferred; the application path is in scope | an ingestion command with no listener, exercised by tests |
| D42 | one active basket per customer, enforced by a unique index | a partial index in the schema, not a check in the service |
| D45 | no Pact contract and no simulator service; the executable scenarios are the contract | a `prove` scenario run by one shell entry point |

<!-- evidence: docs/specs/basket/log.md:108, docs/specs/basket/log.md:109, docs/specs/basket/log.md:112, docs/specs/basket/log.md:115, services/shopping/src/main/java/shop/shopping/basket/BasketConfiguration.java:39-57, services/shopping/src/main/resources/db/changelog/0001-basket.yaml:41-47 -->

### 11.2 Decisions about the contract

| Id | Decision | Consequence in the code |
|---|---|---|
| D44 | a refused command is `422`, and a read may create a basket | two recorded deviations, both in the error advice |
| D48 | one unchecked exception in the shared kernel, mapped once | every refusal is one type with an error constant |
| D50 | three removal scopes; the basket's currency is configuration | one removal route with a scope, a normalised currency value |
| D68 | a foreign basket answers `404`, never `403` | ownership is checked where a missing row is checked |
| D69 | `OFFER_UNAVAILABLE` names the one refusal element 07 left unnamed | the sixth `422` code |

<!-- evidence: docs/specs/basket/log.md:114, docs/specs/basket/log.md:118, docs/specs/basket/log.md:120, docs/specs/basket/log.md:137, docs/specs/basket/log.md:138, services/shopping/src/main/java/shop/shopping/BasketErrorAdvice.java:73-82, services/shopping/src/main/java/shop/shopping/basket/BasketService.java:367-377, services/shopping/src/main/java/shop/shopping/kernel/ShoppingException.java:44-51 -->

### 11.3 Decisions about prices, availability and the handover

| Id | Decision | Consequence in the code |
|---|---|---|
| D40 | the ingestion path flattens the catalog's discount data | a row keeps a current price and a discount amount, never a percentage |
| D53 | the list price is never stored on a row and never prices a line | one type may carry it, and no row does |
| D55 | the availability decision takes the observation only | the previous outcome is not consultable, so a failed read cannot fail open |
| D51, D64 | the handover digest covers rows and totals, not the clock-derived estimate | a repeated handover of unchanged content keeps its identity across midnight |
| D72 | the deployed fixture's seed is configuration, and its counters are one process's | `replicas: 1` is load-bearing, not a default |

<!-- evidence: docs/specs/basket/log.md:110, docs/specs/basket/log.md:123, docs/specs/basket/log.md:125, docs/specs/basket/log.md:121, docs/specs/basket/log.md:134, docs/specs/basket/log.md:141, services/shopping/k8s/base/deployment.yaml:8, services/shopping/src/main/java/shop/shopping/basket/BasketItem.java:34-52, services/shopping/src/main/java/shop/shopping/kernel/Handover.java:162-171, services/shopping/src/main/java/shop/shopping/basket/FixtureOfferCatalog.java:34-39 -->

### 11.4 Decisions taken while building, after the register was written

Two facts in this document exist only because a tool behaved differently from its specification, and both
are recorded in the same register so a reader does not have to reconstruct them:

- The fixture's seed grew a `fault` and a `fault-after` key so that the failed-read path is observable over
  HTTP rather than only inside a test. A permanently faulted offer cannot be observed through a read at
  all, because the command that would store it is refused first.
- The contract runner carries both tokens on its command line, because the runner in use offers no file or
  environment indirection for a variable. The exposure is the token's lifetime, not the run's.

<!-- evidence: docs/specs/basket/log.md:141, docs/specs/basket/log.md:140, docs/specs/basket/prove-of-done.md:14-16, docs/specs/basket/prove-of-done.md:111-112 -->

### 11.5 What the register cannot answer

The register records decisions, not measurements. No row states a latency, a volume or a conversion
figure, and the specification says so in its own words when it introduces its value hypotheses. A reader
looking for "how fast" or "how many" will not find it here, and chapter 13 records the absence as a gap
rather than estimating one.

<!-- evidence: docs/specs/basket/index.md:8, docs/specs/basket/index.md:173 -->
