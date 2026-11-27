## 2 Glossary

One definition per term, in the sense this service uses it. Where the same word means something else in a
neighbouring system, the other meaning is recorded and rejected.

<!-- evidence: services/shopping/src/main/java/shop/shopping/kernel/Money.java:7-14 -->

### 2.1 The basket

**Basket** — one customer's editable collection of offers, quantities and a selection, in one currency,
belonging to exactly one customer. A customer has exactly one active basket in this increment; the plural
reading is deferred, though the schema keeps the archived ones.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:45-59, docs/specs/basket/log.md:112 -->

**Row** (also *item*) — one offer inside a basket, with a quantity of at least one, a selected flag, the
availability the service last decided, and the last-known description and prices. An offer appears at most
once in a basket: a repeated add merges into the existing row rather than creating a second one.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketItem.java:34-52, services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:61-76 -->

**Selection** — which rows the customer has ticked. Selection is state, not history: it produces no fact, and
it is what the price is computed over.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketSelection.java:5-20, services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:117-137 -->

**Active / archived** — `active` means the basket is still editable. `archived` is the terminal state a
completed checkout leaves behind, and the checkout slice owns that transition; nothing in this increment
archives a basket.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketState.java:3-10 -->

### 2.2 The money

**Amount** — an integer count of units one digit finer than the currency's minor unit. For PLN, one unit is
a tenth of a grosz, so `99,89 zł` is `99890`. The extra digit exists so a discount can be computed without
losing precision; nothing is rounded until an amount leaves the service.

<!-- evidence: services/shopping/src/main/java/shop/shopping/kernel/Money.java:7-14 -->

**Charge and discount** — a charge adds to what the customer pays; a discount is a positive amount reported
beside the price and subtracted in the total line. A discount is never a negative charge, and a percentage
never reaches the basket: the catalogue flattens it into an amount first.

<!-- evidence: services/shopping/src/main/java/shop/shopping/kernel/Money.java:30-44, docs/specs/basket/log.md:110 -->

**Rejected synonym:** *unit price* for the row's `lineTotal`. The row shows the price at its quantity, which
is what the customer sees in a basket; the unit price is a field of the row but not the line's headline
amount.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketContent.java:117-132, docs/specs/basket/log.md:85 -->

### 2.3 The price breakdown

**Product value** — the sum of the line totals of the counted rows. **Counted** means selected and not
unavailable.

**Discount value** — the sum of the counted rows' discounts, reported beside the product value.

**Delivery** — one rate per seller shipment that holds at least one counted row, zero on the SMART channel.
There is no free-delivery threshold: the only thing that makes delivery free is how the customer arrived.

**Total with delivery** — product value plus delivery. It is the number the basket is worth.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketPriceCalculation.java:44-71, services/shopping/src/main/java/shop/shopping/kernel/Handover.java:108-123, docs/specs/basket/log.md:103 -->

### 2.4 The availability of an offer

**Availability** — what the basket is allowed to say about one offer, decided from one observation and never
from the row's history:

| Status | Meaning | Blocks a handover |
|---|---|---|
| `available` | the offer is on sale and the stock read answered with units left, or did not answer at all | no |
| `outOfStock` | the offer is on sale and the stock read answered with none left | yes |
| `unavailable` | the offer was removed, or the read failed | yes |

**Stale** — the row shows what it last knew rather than what a fresh read says. Stale is not a fourth
status: it is a flag beside one of the three.

<!-- evidence: services/shopping/src/main/java/shop/shopping/kernel/Availability.java:23-43, services/shopping/src/main/java/shop/shopping/kernel/Availability.java:70-78, docs/specs/basket/log.md:103 -->

**Rejected reading:** *out of stock is non-blocking.* The board said so and the transcript implied a longer
delivery instead; the operator decided the opposite, because a basket that hands over something nobody can
sell creates a problem in checkout. A customer who wants to buy the rest unticks the row.

<!-- evidence: docs/specs/basket/log.md:103 -->

### 2.5 The delivery estimate

**Estimate** — one inclusive range of calendar days for the whole basket: today plus two days to today plus
four, counted in the `Europe/Warsaw` calendar and supplied as configuration. The basket publishes the latest
end across the seller shipments that answered a range.

**Unknown** — the named non-answer: no row that can be handed over, or no configured rule. The client hides
the line rather than guessing; an unknown estimate is never rendered as a range.

<!-- evidence: services/shopping/src/main/java/shop/shopping/kernel/Estimate.java:30-60, services/shopping/src/main/java/shop/shopping/basket/ShipmentEstimates.java:39-56, docs/specs/basket/log.md:106 -->

### 2.6 The handover

**Handover** — the selection as it crosses to checkout: the rows, their sellers and quantities, the
availability each carried, the four totals, and the identity that makes a repeat of unchanged content the
same handover.

**Revision** — the counter of what the customer can see, and half of the handover's identity. A repeated
handover of unchanged content keeps the revision it had.

**Content digest** — the identity of a handover, computed over the rows and the totals. The estimate is
excluded on purpose: it is derived from the clock, and a handover that changed identity at midnight would be
a new handover of an untouched basket.

<!-- evidence: services/shopping/src/main/java/shop/shopping/kernel/Handover.java:140-171, services/shopping/src/main/java/shop/shopping/basket/HandoverAnswer.java:9-28, docs/specs/basket/log.md:121 -->

### 2.7 The catalogue stand-in

**Offer** — what the catalogue sells: an identity, a seller, a title, one picture, a list price, a current
price, a discounted amount and an availability. The basket stores a row's last-known copy and never turns a
list price into a charge.

**Fixture** — the stand-in that answers the offer read in this increment. It is seeded from deployment
configuration and it is not a catalogue: no search, no browsing, no reservation, and its stock answers are a
per-offer queue whose last entry repeats. It exists so the interface is exercisable before the browsing slice
lands, and the slice's own adapter will replace it without the wiring changing.

<!-- evidence: services/shopping/docs/offer-fixture.md:3-7, services/shopping/docs/offer-fixture.md:44-60, services/shopping/src/main/java/shop/shopping/basket/BasketConfiguration.java:39-57 -->
