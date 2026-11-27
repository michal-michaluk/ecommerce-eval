## 1 Purpose and scope

The shopping service holds one customer's basket: the offers they put into it, how many of each, which of
them they have ticked, what the selection costs including delivery, and when it would arrive. It is one
Spring Boot process with two slices — `basket` and `checkout` — and this document describes only the
basket slice, because the checkout slice is specified separately and, in this increment, has nothing to
describe beyond receiving a handover.

<!-- evidence: docs/specs/basket/index.md:7-8, docs/specs/basket/index.md:169-171 -->

The build it describes is the one that exists: the specification's ten elements are implemented as six
HTTP routes, two persisted documents and one in-process command, and every fact in these pages is read
from that code, from its manifests or from the registers in `docs/specs/basket/`, which record why each
decision was taken.

### 1.1 What the service is for

The basket is where a purchase first shows its real price and its delivery time. Two consequences follow,
and both are visible in the code rather than in prose:

- **The price is the catalog's, never the basket's.** The service consumes a flattened current price and
  a flattened discount amount per offer and multiplies each by its quantity; it computes no discount rule
  and recomputes no price from a percentage (D22, D40).
- **Availability is decisioned, not stored as an opinion.** Every answering route re-reads the offers it
  holds and applies the answer through one ingestion path, so a row's availability can move between two
  commands without any event transport (D66, D39).

<!-- evidence: docs/specs/basket/log.md:110, docs/specs/basket/log.md:109, docs/specs/basket/log.md:135, services/shopping/src/main/java/shop/shopping/basket/BasketPriceCalculation.java:51-61 -->

### 1.2 What is deliberately not here

| Not in this increment | Why, and where it is recorded |
|---|---|
| An HTTP client for the offer read | The provider's shop-facing read does not exist yet; the port is answered by a seeded fixture adapter (D38, D60) |
| Any event transport | No Kafka listener, no producer, no topic: the application path of an observation is in scope, its transport is not (D39) |
| Several active baskets per customer | One active basket is enforced by a partial unique index; the plural reading of the earlier decision is deferred (D42) |
| Order creation, payment, reservation | Out of the basket's scope: checkout receives the selection and creates no order in this increment (D4, HAND-3) |
| A contract written with another tool | No Pact contract, no simulator service, no CLI: the executable scenarios are the contract of record (D45) |

<!-- evidence: docs/specs/basket/log.md:130, docs/specs/basket/log.md:109, docs/specs/basket/log.md:112, docs/specs/basket/log.md:115, services/shopping/src/main/java/shop/shopping/checkout/CheckoutService.java:20-21 -->

The offer catalogue is the one absence a reader will otherwise misread. The service reads offers through
a port whose only implementation in this increment is a fixture seeded from a ConfigMap key. It is a
stand-in for a catalogue, not a catalogue: it has no search, no browsing and no way to reserve anything,
and its read counters belong to one process, which is why the Deployment runs one replica (D67, D72).

<!-- evidence: services/shopping/docs/offer-fixture.md:3-7, services/shopping/k8s/base/configmap.yaml:12-17, services/shopping/k8s/base/deployment.yaml:8 -->

### 1.3 How to read this document

Part I states what the system is and where its boundary is. Part II states how access to it is decided.
Part III describes what is stored and in which order it is written. Part IV collects the decisions the
specification registers, because they are the only record of why the system is shaped this way. Part V
lists what the repository could not establish — every gap is numbered, and every chapter that raises one
names its number.
