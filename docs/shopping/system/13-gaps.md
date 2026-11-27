## 13 Gaps

Everything below is a question this document raises and the repository does not answer. Each entry names
what would close it; none of them is a recommendation to change the service.

**GAP-1 — No architecture decision record exists.** The index in chapter 12 is empty because the directory
it indexes is absent, and the decisions that shaped this build live in the specification's own register
(log.md, D38–D73) rather than in records of their own. Closing it means writing one record per decision
that outlives the increment — which is the point of the genre, since a register row records what was
decided and rarely what was rejected.

<!-- evidence: docs/specs/basket/log.md:108, docs/specs/basket/log.md:142 -->

**GAP-2 — The HTTP contract is not machine-readable.** The service ships no OpenAPI artifact, the
annotations declare a media type and nothing about a body, and the executable scenario this service
carries (`e2e/healthcheck.hurl`) calls the management surface only. The contract of record is a `prove`
document of twenty-four steps, which no extractor in this repository reads. Consequences a reader will
notice: the operation chapters tabulate no response field, quote no request or response block, and state
the statuses from the advice rather than from a schema — chapter 11 of the integrator guide fills that hole
from the handler records. Closing it means publishing an OpenAPI document from the build, or teaching the
pipeline the `prove` format.

<!-- evidence: services/shopping/e2e/healthcheck.hurl:1-17, services/shopping/build.gradle:76-79, docs/specs/basket/prototypes/basket-api.prove.md:1-7 -->

**GAP-3 — Two schema objects are outside the extracted model.** The change set that creates the basket
table also creates its partial unique index, and one change set is a tag rather than a table. The schema
chapter lists tables and columns because that is what the extraction reads; the index — which is what
enforces the one-active-basket rule — is described in chapter 8 instead, from the changelog itself.

<!-- evidence: services/shopping/src/main/resources/db/changelog/0001-basket.yaml:41-47, services/shopping/src/main/resources/db/db.changelog.yaml:22-27 -->

**GAP-4 — No quality of service is stated anywhere.** No material carries a latency, volume, availability
or conversion figure; the specification says so explicitly and states its value hypotheses with the
baseline marked absent. Nothing in this document quantifies load, and nothing should be read as if it did.

<!-- evidence: docs/specs/basket/index.md:8, docs/specs/basket/index.md:173 -->

**GAP-5 — The checkout slice has no specification.** Its behaviour after receiving a handover is
deliberately undefined in this increment: it refuses a stale handover and records what it accepted, and it
creates no order. Whether an order is created, what the client then reads, and who owns the seam are all
open — the basket is described here as far as the seam and no further.

<!-- evidence: services/shopping/src/main/java/shop/shopping/checkout/CheckoutService.java:15-21, services/shopping/src/main/java/shop/shopping/checkout/package-info.java:1-13 -->

**GAP-6 — Two route summaries in the source render their path wrongly.** The Javadoc of two routes embeds
an inline code tag around a path that itself contains braces, so the tag closes inside the path and the
text that reaches a reader drops a brace. The defect is in the source commentary, not in the service, and
this docset therefore does not quote operation summaries at all: an operation chapter is titled by its
method and path. Closing it means escaping the braces in those two comments.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketCheckoutController.java:15-16, services/shopping/src/main/java/shop/shopping/basket/BasketSelectionController.java:17 -->

**GAP-7 — The offer catalogue is a fixture, so no real provider contract is documented.** The shape a real
provider answers with — its field names, its failure modes, its pagination, its stock semantics — is
nowhere in this repository. Element 08's port was written against golden assumptions that the browsing
slice has not yet confirmed, and the fixture is the simulator.

<!-- evidence: docs/specs/basket/log.md:102, docs/specs/basket/prototypes/offer-catalog-port.prove.md:1-10 -->
