# 08 — OfferCatalog + Warehouse read

**Role:** the **port** through which the basket learns what an offer is, what it costs and whether it can be sold. The basket **pulls on every read** (D10); it subscribes to nothing (F11) and it never reserves stock (R6).

**What this element is and is not.** Per **D13** the HTTP shape of this read is **not** owned here — it belongs to the browsing slice (Q9). This element fixes the port: the calls the basket makes, the fields it consumes, and the mapping from every provider failure to a basket outcome. The provider is `catalog`, which today has **no HTTP surface at all** (F13: no `@RestController` under `services/catalog/src/main`).

**Artifact:** [`prototypes/offer-catalog-port.prove.md`](prototypes/offer-catalog-port.prove.md) — a `prove` scenario marked **assumption**, because no such endpoint exists yet. It becomes runnable the day the browsing slice publishes the contract; `validate` and `explain` gate it today.

**Increments (D38/D39).** The `catalog` HTTP surface exists only on the unmerged `feat/catalog-domain-model` branch and carries no shop-facing offer read, and no service publishes an event yet. So this increment ships the **port and the application path**, not an HTTP client:

- `OfferCatalog` is implemented by a fixture adapter seeded per test and per cluster run, with fault injection (`503`, `401`, `stock: notRead`) — that adapter is also the simulator the negative rows (DoD17–DoD19) need.
- The basket **applies** an observation in one in-process command (idempotent snapshot replacement, `readAt` ordering, EVT-2/EVT-3/EVT-4). Element 10's transport is deferred; the command is what DoD20/DoD21 prove.

## Port

```text
OfferCatalog.read(offerIds: [OfferId]) → [OfferObservation]     # one call per basket read, batched
OfferObservation = { offerId, seller, title, pictureUrl,
                     price: Money,              # list price, element 03
                     currentPrice: Money,       # actual price after catalog's own discount model (D22)
                     discount: Money,           # flattened discount amount, never a rule (D22, Q18/Q19)
                     offerState: onSale | removed,
                     stock: Quantity | notRead, # becomes a band: high | medium | last n (D20, R14)
                     readAt: Instant }
```

- **Batch, never per item** — a basket with 20 items must not make 20 calls on a page render.
- **`stock` may be `notRead`** — the warehouse is a separate system and may be unavailable (element 06 A5 tolerates it).
- **No reservation call exists on this port.** `reserveProductQuantity` belongs to fulfilment (`kernel/WarehouseStock.java:29`, and it is explicitly *not idempotent* — "a repeated call reserves twice"); putting it on the basket path would create exactly the leak R6 forbids.

## Fields consumed — and nothing else

| Field | Used for | If absent |
|---|---|---|
| `offerId` | item identity | the item cannot be read at all → `503` (element 07 error contract) |
| `seller` | shipment grouping (02 RULE-7) | item renders in a shipment labelled `unknown seller`; never merged with another |
| `title`, `pictureUrl` | item row (element 01, board annotation "product title, one picture, price") | last-known snapshot (02 RULE-8) |
| `currentPrice`, `discount` | line total and discount line (element 04) | last-known snapshot; a price that cannot be read is never recomputed. Catalog owns the discount model — the basket only displays the flattened result (D22, D40) |
| `offerState` | availability decision (element 06 A3) | treated as `notRead` → A4 |
| `stock` | availability decision (A2/A5) | `notRead` → A5 |

## Failure mapping

| Provider outcome | Basket outcome | Register |
|---|---|---|
| `200` with the batch | observations applied, `stale: false` | — |
| event pushed between two reads (element 10) | observation applied immediately; the next read still wins | D25 |
| `200` missing some requested ids | those items → `removed` → `unavailable` (A3) | `[seg 69]`, D33 |
| `404` for the whole batch | unchanged; the call is malformed, not the offer | — |
| `401`/`403` | `unavailable`, `stale: true` (A4 — a failed read never keeps a previous `available`) plus a logged configuration fault. The basket never hides itself because our credential is wrong | D26 |
| timeout / `5xx` | row greyed, `unavailable`, `stale: true` (A4) | Q24 → **D26**, R13 |
| warehouse unavailable (`stock: notRead`) | `available`, `stale: true` (A5) | A5/R14 |

## Open questions

| ID | Question | Recommendation |
|---|---|---|
| Q28 **answered (D30)** | Does the basket call the **warehouse** directly? | Answered: **via `OfferCatalog` only** — stock arrives inside `OfferObservation`, no second client for the off-the-shelf system |
| Q30 **answered (D13)** | Which concrete path/verb does the read use? | Answered: **deferred to the browsing slice** — the fixtures pin the shape only |

## Recommendations

1. **Write the adapter now against the fixtures, behind the port.** When the browsing slice lands the real endpoint, only the adapter changes — element 06, 04 and 07 stay untouched.
2. **Batch + cache per request.** Two calls per page render (list + detail) is the difference between a usable basket and an unusable one; a per-request cache also makes `readAt` meaningful.
3. **Never fail the basket because the offer read failed** — the whole of element 06 A4/A5 exists to keep the customer able to see and act on a basket during a catalog incident.
4. **Do not add a reservation call "for safety"** — R6 is the risk this spec was written to prevent; the port is the place where that mistake would enter.
