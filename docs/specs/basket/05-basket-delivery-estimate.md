# 05 — BasketDeliveryEstimate (interface only)

**Role:** **Derived**, specified as an **interface**. This element fixes *what goes in, what comes out and what happens when it cannot be computed*; the algorithm is deliberately **deferred** to issue [#71](https://github.com/michal-michaluk/ecommerce-eval/issues/71) (**D16**).

**Provenance:** board policy `delivery time calculation policy` (`eventstorming.json` `lanes[2].policies[0]`); `[seg 74]` "clients is seeing the content with the estimated delivery time and price"; `[seg 71]` out-of-stock makes delivery "longer than client expects". The reasoning behind the estimate is **missing** — a 71.2 s stretch (`00:14:50–00:16:01`) was not transcribed (F5, **R1**), so no formula exists and none may be invented (D7).

## Interface

```text
BasketDeliveryEstimate.estimate(request) → Estimate

request  = { items: [ { offerId, seller, quantity, availability } ],
             channel: TokenChannel,             # SMART etc., from the token (Q9/D13 boundary)
             now: Instant }
Estimate = { kind: estimate | unknown,
             from: Date, to: Date,              # inclusive day range, ISO-8601 dates
             source: configured | observed }    # whether a rule or a fallback produced it }
# no `basis`, no out-of-stock range: an out-of-stock item blocks handover (D33),
# so it never belongs to a checkable basket and cannot lengthen an estimate.
```

- **Granularity:** one `Estimate` per **seller shipment**; the basket shows the latest `to` across shipments.
- **Only `kind: estimate` is renderable**; `unknown` hides the delivery line rather than printing a guess.
- **The interface never returns a `Money`** — delivery price is element 04's business, not the estimate's.

## Parameter and value-object origins

| Value object | Values | Origin | Source |
|---|---|---|---|
| `items[]` | offerId, seller, quantity, availability | Business concept | element 02, element 06 |
| `channel` | SMART or default | Integration | token claim (D13 — HTTP shape deferred) |
| `now` | instant | — | clock |
| `Estimate` | day range, source | Configuration + Derived | this element |

## Configuration

| Key | Meaning | Default (D7 — no invented numbers) |
|---|---|---|
| `basket.estimate.defaultDays` | the estimate, in days from `now` | **`{ from: 2, to: 4 }` — hardcoded by decision (D36)**: the estimate is `today + 2 days` … `today + 4 days` |
| `basket.estimate.fallback` | what to return when configuration is missing | `unknown` |

## Interface scenarios

| # | input | expected output |
|---|---|---|
| I1 | both items `available`, default channel | `kind: estimate`, range from `defaultDays` |
| I2 | one item `outOfStock`, still selected, others not blocked | `kind: estimate` over the **non-blocking** items only; the blocked row contributes nothing (D33) |
| I3 | one item `unavailable` | the unavailable item is **ignored**; range as I1 (it cannot be ordered anyway, D18) |
| I4 | no selected items | `kind: unknown` — nothing to estimate |
| I5 | configuration key absent | `kind: unknown`, `source: configured` missing → fallback; never an exception |
| I6 | channel `SMART` | range unchanged (SMART changes the **price**, not the day range — S6 in element 04) |
| I7 | **all** selected items blocked | `kind: unknown` — nothing is handable over, so there is nothing to estimate |

## Open questions

| ID | Question | Recommendation |
|---|---|---|
| Q21 **answered (D24/D36)** | Is the estimate a day range, a single date, or an hour window? | Answered: **inclusive day range**, hardcoded as `now + 2` … `now + 4` days — no algorithm is built in this increment |
| Q22 **answered (D24)** | What is shown when the estimate is `unknown`? | Answered: **hide the information** — `kind: unknown` renders nothing; `Razem z dostawą` from element 04 stays |

## Recommendations

1. **Ship the interface with `unknown` implemented and the 2–4 day range hardcoded behind it** (D36) — the constant satisfies D16 and lets the real algorithm (#71) drop in without touching the view or the API.
2. **Do not let the estimate feed the price** — `Dostawa od 9,99 zł` comes from `DeliveryRate` (element 04); the estimate only says *when*.
3. **The 2–4 day range is a decision, not a measurement** (D36): it is hardcoded so the shape is testable; issue #71 may replace the constant with the real policy.
4. **The staleness of `now`** — compute against a request-scoped clock, never a cached one, or a basket left open overnight will show yesterday's range.
