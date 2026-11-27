## 3 Business rules

Twelve rules, each with the four parts an auditor needs: what goes in, what comes out, what configures it and
what the service reads to decide. Where the code states a rule but the business meaning behind it is absent
from the repository, the entry says so instead of inventing one.

### 3.1 A customer always has exactly one active basket

**Inputs** — the customer identity from the token; nothing from the client.
**Output** — the customer's active basket, created empty in the deployment's currency when none exists; the
list route therefore never answers an empty list.
**Configuration** — `basket.currency` decides the currency a new basket is opened in; nothing else.
**Internal data fetched** — `basket` by `customer_id` and `active` through `BasketRepository.findActiveFor`;
a refused creation is resolved by re-reading the winner of the race.

The uniqueness is the schema's promise, not the code's: a partial unique index on the customer column
restricted to active rows. That is what makes two concurrent first requests end with one basket instead of
two.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketService.java:64-74, services/shopping/src/main/java/shop/shopping/basket/BasketService.java:353-365, services/shopping/src/main/resources/db/changelog/0001-basket.yaml:41-47 -->

### 3.2 A basket holds one currency, and offers in another are refused

**Inputs** — the offer as the catalogue answered it, with its currency.
**Output** — the row is accepted, or the command is refused with `CURRENCY_MISMATCH`; nothing is converted,
because no rate exists in the service.
**Configuration** — `basket.currency`, normalised to its canonical upper case when the bean is created, so
`pln` and `PLN` are one currency. A code ISO-4217 does not know stops the process at start.
**Internal data fetched** — nothing; the decision compares the offer's currency with the basket's own.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:296-300, services/shopping/src/main/java/shop/shopping/basket/BasketCurrency.java:8-26 -->

### 3.3 A quantity is at least one; removal is a different command

**Inputs** — the absolute quantity the client states, or `null` on the removal route.
**Output** — the row holds that number of units; a number below one is refused with `QUANTITY_NOT_POSITIVE`,
and a removal that carries a number at all is refused as a malformed body.
**Configuration** — none.
**Internal data fetched** — the row being changed, from the basket document.

The two commands are separate on purpose: the route that carries an offer only ever sets an absolute
quantity, and the route that carries none is the only one that removes. A relative change would double a
basket when a client retries; a removal that quietly ignored a quantity would remove what the client meant to
keep.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:78-91, services/shopping/src/main/java/shop/shopping/basket/BasketItemsController.java:56-83 -->

### 3.4 A repeated add merges into the row and selects it

**Inputs** — an offer the basket does or does not already hold, and a quantity.
**Output** — one row per offer. A second add of the same offer adds its quantity to the row and leaves the
seller, the availability and the last-known description untouched; a new row is selected.
**Configuration** — none.
**Internal data fetched** — the current basket document; for an offer the basket does not hold, one read of
that offer through the offer port, which is refused with `404` when the catalogue no longer has it and with
`503` when the read failed and there is no snapshot to describe a row with.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:61-76, services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:208-214, services/shopping/src/main/java/shop/shopping/basket/BasketService.java:310-336 -->

### 3.5 The quantity of an unavailable row cannot change; an out-of-stock row may

**Inputs** — a quantity command and the row's availability as last decided.
**Output** — `422 OFFER_UNAVAILABLE` on a row that is `unavailable`, because the basket cannot promise a
price it could not read; the same command on an `outOfStock` row succeeds, because its price is real and the
handover is where the blockage belongs.
**Configuration** — none.
**Internal data fetched** — the row and its availability, from the basket document.

One deliberate exception: a repeated *add* of an unavailable row still merges its quantity. The customer
asked for more units of something already in the basket, and the row stays blocked and excluded either way.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:78-91, services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:308-312, docs/specs/basket/log.md:122 -->

### 3.6 The selection decides what is priced

**Inputs** — each row's selected flag, quantity, current price, discount; the customer's channel.
**Output** — four amounts: product value, discount value, delivery, total with delivery. An `unavailable`
row is excluded from every sum even while selected; an `outOfStock` row is counted while selected; a
deselected row counts for nothing.
**Configuration** — `basket.delivery.rate` and `basket.delivery.freeWhenSmart`; no percentage, no rounding
rule and no discount policy exists in the basket.
**Internal data fetched** — the basket document only. The catalogue's price is what the row stores as
last-known; the calculation re-derives nothing.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketPriceCalculation.java:44-71, services/shopping/src/main/java/shop/shopping/basket/BasketPriceCalculation.java:9-32 -->

### 3.7 Delivery is charged per seller shipment, and is free on the SMART channel

**Inputs** — the counted rows, grouped by seller; the caller's channel claim.
**Output** — one rate per seller shipment that holds at least one counted row, so a multi-seller selection
sums the rates; zero on the SMART channel; zero when nothing is counted.
**Configuration** — `basket.delivery.rate` (in the basket's currency) and `basket.delivery.freeWhenSmart`.
An absent block answers the two stated defaults — `9990` fine units and free on SMART — while a half-stated
block defaults key by key, never as a pair.
**Internal data fetched** — the basket document, grouped by the seller each row carries.

There is no free-delivery threshold and no per-unit delivery: one shipment costs the same whether it holds
one unit or a hundred, and the only thing that changes the price is how the customer arrived.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/DeliveryRates.java:7-44, services/shopping/src/main/java/shop/shopping/basket/BasketConfiguration.java:88-108, docs/specs/basket/log.md:103 -->

### 3.8 The basket publishes one delivery range, or a named non-answer

**Inputs** — every row's seller; the instant of the read; the customer's channel.
**Output** — the latest end date across the seller shipments that answered a range, inclusive, or the named
`unknown` when no shipment answered one. The channel changes the price, never the range.
**Configuration** — `basket.estimate.default-days.from`, `.to`, `basket.estimate.zone` and
`basket.estimate.fallback`. The policy carries no built-in number, so a deployment that omits the block
answers `unknown` rather than guessing a range.
**Internal data fetched** — the basket document; nothing else, and no clock beyond the instant passed in.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/ShipmentEstimates.java:39-56, services/shopping/src/main/resources/application.yml:32-40, docs/specs/basket/log.md:128 -->

### 3.9 A selection never exceeds the basket

**Inputs** — the scope the client names (one item, one seller's shipment, the whole basket) and the direction.
**Output** — the rows the scope covers switch on or off; a scope that names an offer the basket does not hold
is refused with `404 OFFER_NOT_FOUND`; a scope that covers nothing is a no-op rather than an error, because
an empty selection is a valid basket.
**Configuration** — none.
**Internal data fetched** — the basket document; the selection is stored on each row, never as a second list.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:117-137, services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:314-318 -->

### 3.10 A handover is refused when nothing is selected or a selected row is blocked

**Inputs** — the selected rows and the availability each carries.
**Output** — `422 NOTHING_SELECTED` for an empty selection; `422 BLOCKED_BY_ITEMS` with one reason per
blocking row (`offerId`, `status`, `stale`) when any selected row is `outOfStock` or `unavailable`. The
customer's remedy is to untick the blocking row, which is then the only thing not handed over.
**Configuration** — none.
**Internal data fetched** — the basket document. The decision table is element 06's and is consulted before
the handover, so a refused handover reaches no port and writes nothing.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:320-332, services/shopping/src/main/java/shop/shopping/basket/BasketService.java:216-221, docs/specs/basket/log.md:103 -->

### 3.11 A repeat of unchanged content is the same handover

**Inputs** — the selected rows, their quantities and prices, the four totals, and the instant the handover
is composed at.
**Output** — a handover identity, a revision and the totals. Content that already crossed at the same
revision is answered again with the same identity and the same instant, and nothing crosses a second time.
**Configuration** — none; the identity is versioned in the code, not configured.
**Internal data fetched** — the basket document, which stores the last handover's revision, digest, identity
and instant.

The estimate is excluded from the identity because it is derived from the clock; the channel is not, because
another channel moves the delivery rate, and therefore the totals.

<!-- evidence: services/shopping/src/main/java/shop/shopping/kernel/Handover.java:140-171, services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:139-168, docs/specs/basket/log.md:121 -->

### 3.12 An offer's availability is decided from one observation, and a failed read greys the row

**Inputs** — one observation of one offer: its state, its stock answer, and when it was read.
**Output** — `available` when the offer is on sale with units left, or on sale while only its stock could not
be read; `outOfStock` when the stock answer is a count with nothing left, including a count below zero;
`unavailable` when the offer is known to be removed or when the read failed. A failed read inside a basket
render greys the row and keeps the last-known price, and it blocks the handover.
**Configuration** — none: the decision takes the observation and nothing else, so a previous `available` is
not consultable and cannot survive a failure.
**Internal data fetched** — nothing beyond the observation. The row's own read instant is the ordering key: an
observation older than the row's own read is discarded.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/OfferAvailabilityDecision.java:28-55, services/shopping/src/main/java/shop/shopping/basket/BasketItem.java:117-134, docs/specs/basket/log.md:126 -->

The business meaning of a `stale available` row — the offer answered but its stock did not — is the one place
where the repository states a rule without stating its intent: element 06 records the outcome and the
specification does not say what a customer should be told. The outcome is implemented and observable; the
wording is the client's, and no material states it.

<!-- evidence: docs/specs/basket/06-offer-availability-decision.md:1-19 -->
