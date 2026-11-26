# 06 — OfferAvailabilityDecision (interface only)

**Role:** **Derived decision**, specified as an **interface**. This element fixes the three outcomes, who supplies each input, and what each outcome means for the rest of the basket. Detection is deliberately **deferred** to issue [#72](https://github.com/michal-michaluk/ecommerce-eval/issues/72) (**D16**), because there is nothing to detect against: `catalog` has no HTTP surface at all (**F13**) and publishes no events (**F11**).

**Provenance:** board policy `product out of stock (non blocking)` — physically **duplicated** on the board (`eventstorming.json` `lanes[2].annotations[1]`), and it is a *crossing* from `browsing-offer → basket-management` (`boundaries[1]`); `[seg 69]` "we don't want that product magically disappear … mark it red or whatever that it is no longer available"; `[seg 70-71]` out of stock is not blocking, "he may even still order products".

## Interface

```text
OfferAvailabilityDecision.decide(item, observation) → Availability

item        = { offerId, lastKnown }                       # business concept, element 02
observation = { offerState: onSale | removed | notRead,
                stock:     Quantity | notRead,
                readAt:    Instant }
Availability = { status: available | outOfStock | unavailable,
                 reason: stillOnSale | removedFromOffer | noStock | notRead,
                 stale:  boolean }
```

## Decision — the three outcomes and their meaning

| Rule | `offerState` | `stock` | `status` | Consequence in the basket |
|---|---|---|---|---|
| A1 | `onSale` | `> 0` | `available` | counted, orderable, blocks nothing |
| A2 | `onSale` | `= 0` | `outOfStock` | counted while selected, **blocks handover**; deselecting the row enables checkout for the rest (D33 — the operator's decision, which overrides the board's `product out of stock (non blocking)` and `[seg 70-71]`) |
| A3 | `removed` | any | `unavailable` | excluded from totals (D18), **blocks** handover, row keeps its last-known snapshot (element 02 RULE-8) |
| A4 | read attempted and **failed** (timeout, `5xx`, `401`/`403`) — with or without a previous observation | any | `unavailable`, `stale: true` | the basket is still shown, the row is greyed and marked unavailable, and it **blocks handover** (D26/D33). A failed read never keeps a previous `available` |
| A5 | `onSale` | `notRead` | `available`, `stale: true` | the offer answered, only the stock did not: **not blocking** — `stale` alone never blocks, only `unavailable` does. The bands of a later increment (R14) will replace this |

## Parameter and value-object origins

| Value object | Values | Origin | Source |
|---|---|---|---|
| `item` | offerId, lastKnown snapshot | Business concept | element 02 |
| `offerState`, `stock` | onSale/removed/notRead, quantity/notRead | Integration | `OfferCatalog` port (element 08) |
| `readAt` | instant | — | clock |
| `Availability` | status, reason, stale | Derived | this element |

## Blocking set

| status | counted in totals | blocks handover | unblocked by |
|---|---|---|---|
| `available` (incl. `stale: true` from A5) | yes | **no** — `stale` alone never blocks | — |
| `outOfStock` | yes, while selected | **yes** (D33) | deselecting the row |
| `unavailable` | no (D18) | **yes** | removing the row (it can never be ordered) |
| `unavailable` + `stale` (read failed) | no | **yes** (D26) | retrying until the read succeeds, then acting on the real status |

## Events — the fourth input

Besides the pull (element 08), the decision is re-evaluated when an **offer change event** arrives (element 10, D25): price change, offer removed, stock changed. The event carries a new `observation`; the decision runs again with it and the basket re-renders. An event is never trusted to be the only truth — a read after an event still wins, and out-of-order events must be discarded by `readAt`.

## Interface scenarios

| # | offerState | stock | expected |
|---|---|---|---|
| I1 | `onSale` | 12 | `available`, `stillOnSale`, `stale: false` |
| I2 | `onSale` | 0 | `outOfStock`, `noStock`, `stale: false` |
| I3 | `removed` | — | `unavailable`, `removedFromOffer`, `stale: false` |
| I4 | read fails after a previous `available` | any | `unavailable`, `stale: true` — fail-closed, the previous `available` is not kept (D26) |
| I5 | `onSale` | `notRead` | `available`, `stillOnSale`, `stale: true` |
| I6 | first read ever and the read fails | `notRead` | `unavailable` + `stale: true` — same rule as I4, no exception for a first read |
| I7 | first read ever, offer already `removed` | any | `unavailable` — a **known** removal is data, not a guess, so the item is created already blocked |

## Open questions

| ID | Question | Recommendation |
|---|---|---|
| Q23 **answered (D25)** | How long may an observation be trusted? | Answered: freshness is **event-driven** — product/offer changes reach the basket as events (element 10) and re-evaluate the affected items; there is no timer |
| Q24 **answered (D26)** | What happens when the offer read fails? | Answered: **show the basket, grey the product, mark it unavailable** (`stale: true`). Fail-closed by decision, with R13 recording that a catalog outage then blocks handover |

## Recommendations

1. **Implement A4/A5 before A1–A3** — the failure paths are what the deferred detection (#72) will lean on, and they are the only part testable without a live catalog.
2. **Keep `blocking` a property of the outcome, not of the view** — the two non-available statuses (`outOfStock`, `unavailable`) block, `stale` alone never does (D33 blocking set); if the view or the handover re-derives this, element 01 and element 09 will disagree.
3. **The duplicated board sticky is a warning, not noise**: the same policy appears on both sides of the browsing→basket boundary, so the decision must live in the basket and be *fed* by browsing, never owned twice (R9).
4. **`stale` must reach the API** (element 07) — the client needs it to render an honest screen, and hiding it would make A4 indistinguishable from A1.
