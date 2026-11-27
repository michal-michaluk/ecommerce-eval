## 6 Gaps

Questions this guide raises and the repository does not answer. Each is a request to the business owner,
not a recommendation to the builder.

**GAP-B1 — No criticality numbers.** No MTPD, RTO or RPO exists anywhere for this service; see §5.2. Closing
it needs three numbers from the owner and nothing from the code.

**GAP-B2 — No target for any value hypothesis.** The specification states five value rows — the share of
baskets that reach checkout, the share of handovers whose price moved, two safeguards and the share of
baskets that show an estimate — each with the baseline marked absent, because no material carries a
conversion, volume or latency figure. Every target in that table is a hypothesis, and none of them is
monitored by anything in this repository.

<!-- evidence: docs/specs/basket/index.md:173, docs/specs/basket/index.md:8 -->

**GAP-B3 — No owner of the checkout slice's business behaviour.** Nothing creates an order, nothing charges,
nothing reserves, and no material states what happens to a basket after a completed checkout. The rule that
archives a basket is unimplemented, so `archived` exists in the vocabulary and no path reaches it.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketState.java:3-10, services/shopping/src/main/java/shop/shopping/basket/BasketsProjection.java:33-37 -->

**GAP-B4 — No statement of what a customer is told about a stale row.** The availability decision produces
`available` with `stale`, and element 06 records the outcome; no material states the wording or whether the
customer may still check out with such a row. The service allows it — only `outOfStock` and `unavailable`
block — and the client's text is nowhere in this repository.

<!-- evidence: docs/specs/basket/log.md:126, services/shopping/src/main/java/shop/shopping/kernel/Availability.java:70-78 -->

**GAP-B5 — No owner of the vocabulary itself.** The specification normalised the board's wording once —
`added to basket`, not *added from basket*; `discounts`, not *dyscounts* — and recorded that nothing may
re-derive a vocabulary from an image. Who owns the next word that arrives from a design is not recorded,
and a second re-derivation is exactly what that decision was written to prevent.

<!-- evidence: docs/specs/basket/log.md:87, docs/specs/basket/log.md:88 -->
