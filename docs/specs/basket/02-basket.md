# 02 — Basket

**Role:** **Editor** — the *living configuration* flavour. A customer owns one or more **active** baskets (at least one always exists, D29), always editable and persistent; its current state is authoritative and there is no freeze step. Two derived parts live inside it: availability per item (element 06) and price/estimate (elements 04/05). No `Definition` and no `Execution` role — the immutable artifact of this story is the **order**, which belongs to the checkout slice (D4).

**Actor:** `Customer` (`eventstorming.json` `lanes[2].actors` = `["customer"]`).

**Provenance:** read model `basket content` (`lanes[2].readModels[0]`) with the annotation *"View: - product title, one picture, price"*; commands `add to basket`, `remove from basket`, `go to checkout` (`lanes[2].commands`); events `added from basket` (typo for *to*), `removed from basket`; behaviour from `transcript.json` segments 61–75; screen from `docs/raw/materials/basket.json`.

## Instance A — the capture

```yaml
basketId: B-7f3a
customerId: C-42                          # A1, D11
state: active
items:
  - offerId: OF-1001
    seller: UGREEN_POLSKA
    quantity: 1
    selected: true
    availability: available
    readAt: "2026-11-26T09:58:00Z"          # when the last observation was applied (D59)
    lastKnown: { title: "UGREEN Nexode Szybka Ładowarka Sieciowa USB 2x USB Typ C Zasilacz QC PD 65W",
                 picture: "img-1001", currentPrice: { amount: 99890, currency: PLN },
                 discount: { amount: 0, currency: PLN, kind: discount } }
  - offerId: OF-1002
    seller: UGREEN_POLSKA
    quantity: 1
    selected: true
    availability: available
    readAt: "2026-11-26T09:58:00Z"
    lastKnown: { title: "UGREEN Hub Adapter Stacja Revodok USB-C Do 3xUSB USB-C HDMI 4K 30Hz PD 100W",
                 picture: "img-1002", currentPrice: { amount: 64990, currency: PLN },
                 discount: { amount: 0, currency: PLN, kind: discount } }
# derived at read: one shipment, RAZEM 164,88 · Dostawa od 9,99 · Razem z dostawą 174,87
```

## Instance B — later: two sellers, partial selection, both anomalies

```yaml
basketId: B-7f3a
customerId: C-42
state: active
items:
  - { offerId: OF-1001, seller: UGREEN_POLSKA, quantity: 2, selected: true,  availability: available }
  - { offerId: OF-1002, seller: UGREEN_POLSKA, quantity: 1, selected: false, availability: outOfStock,
      readAt: "2026-11-26T10:02:00Z" }
  - { offerId: OF-2007, seller: ANKER_STORE,   quantity: 1, selected: true,  availability: unavailable,
      readAt: "2026-11-26T10:02:00Z",
      lastKnown: { title: "…", picture: "img-2007", currentPrice: { amount: 129000, currency: PLN },
                   discount: { amount: 0, currency: PLN, kind: discount } } }
# derived at read: two shipments, the unavailable row excluded from the totals (D18)
```

## State diff — A → B

```diff
  items:
-   - { offerId: OF-1001, quantity: 1, selected: true }
+   - { offerId: OF-1001, quantity: 2, selected: true }     # ChangeBasketQuantity
-   - { offerId: OF-1002, quantity: 1, selected: true,  availability: available }
+   - { offerId: OF-1002, quantity: 1, selected: false, availability: outOfStock }
+   - { offerId: OF-2007, quantity: 1, selected: true,  availability: unavailable }
```

## Behaviour

| Operation | Precondition | Effect | Event |
|---|---|---|---|
| `AddToBasket(offerId, quantity≥1)` | basket `active`, customer identified | item appended; if the offer is already there the quantity is **merged** (D19) | `added to basket` |
| `ChangeBasketQuantity(offerId, quantity≥1)` | item exists; `availability ≠ unavailable` (D19) | quantity replaced | `basket quantity changed` — **not on the board (R4)** |
| `RemoveFromBasket(scope)` | scope = item / selection / all (element 07, the contract of record — a shipment is emptied by removing its items; D50) | items removed; an emptied basket stays `active` with no items | `removed from basket` |
| `Select(scope, on/off)` | — | selection flags only; no event | — |
| `HandOver()` | ≥1 selected item and no blocking problem | exposes the selection to the checkout slice (element 09); basket **not** cleared (D19) | `go to checkout` |

## Domain rules

- **State.** A basket is `active` while the customer can change it; `archived` is the terminal state after a checkout completes (the checkout slice owns that transition, element 09).
- **RULE-1 Identity.** A customer owns **one active basket** and always has one: the backend creates it when none exists, so an empty list is impossible (D42, refining D29; several active baskets are deferred). Enforced by a unique index on `(customerId, active)` so two concurrent reads cannot create two baskets. `GET /baskets` still answers a **list** — of length one today. `basketId` is synthetic and appears in every API path; `customerId` always comes from the token.
- **RULE-2 No reservation.** No basket operation reserves stock — not add, not quantity change, not handover (R6, `[seg 66-68]`).
- **RULE-3 Quantity is ≥ 1.** Zero is not a quantity; removal is the only exit (capture: minus disabled at 1).
- **RULE-4 One item per offer.** An offer appears at most once; a repeat add merges quantities (D19).
- **RULE-5 Nothing disappears.** An item whose offer is gone stays as `unavailable` (`[seg 69]`), is excluded from the totals (D18) and blocks handover.
- **RULE-6 Out-of-stock blocks.** `outOfStock` is counted while selected but never crosses the handover; deselecting the row enables checkout for the rest (D33 — the operator's decision, which overrides `[seg 70-71]` and closes R7).
- **RULE-7 Grouping is derived.** Shipping grouping is `seller` on the item, not a stored shipment entity.
- **RULE-8a Read instant.** Every row keeps the `readAt` of the last observation applied to it, so an observation older than the row is discarded (element 10 EVT-3, DoD21) — across requests, which is why it is persisted (D59). A document written before the component existed rehydrates it as `EPOCH`, so the next observation always applies. It advances on a **non-answer** too (a failed read keeps `lastKnown`), an observation of the **same** instant is applied rather than discarded, and a discarded observation leaves the document untouched — no write, no version.
- **RULE-8 Snapshot is last known.** The item keeps the last successfully read snapshot so an erased offer still renders. It is the **price source** when a fresh read is unavailable — element 04 sums its `currentPrice` and `discount` — but it is never **re-derived** from other fields (D22: no `currentPrice = price − discount`) and never **authoritative**: element 09 crosses it as a snapshot and checkout re-prices it (D31, HAND-5). A snapshot that can no longer be trusted is the `unavailable` row D18 excludes.
- **RULE-9 Selection never exceeds the basket.** Deselecting everything leaves a valid basket with an empty selection; handover is then refused.
- **RULE-10 Selection is not persisted history.** Selection is part of the basket state, not an event stream — no event is emitted for it.

## Domain events

```yaml
- addedToBasket:         { basketId: B-7f3a, offerId: OF-1001, quantity: 1 }
- basketQuantityChanged: { basketId: B-7f3a, offerId: OF-1001, from: 1, to: 2 }
- removedFromBasket:     { basketId: B-7f3a, offerId: OF-1002 }   # board wording, typo corrected (R9)
- goToCheckout:          { basketId: B-7f3a, selection: [OF-1001, OF-2007] }
```

## Open questions

| ID | Question | Recommendation |
|---|---|---|
| Q29 **answered (D20)** | Is there a maximum item count or maximum quantity per item? | **No limit in this increment.** A later increment brings warehouse stock **bands** (high / medium / last *n*) inside the product info and the basket will respect them — so quantity handling must not harden into "unbounded is correct" (R14) |

## Recommendations

1. **Model the basket as one aggregate with items as values** — not three concepts (D15). It keeps `Selection` and `Availability` from becoming separate lifecycles.
2. **Never let a command reserve stock** (RULE-2). This is the single most likely implementation defect (R6) and deserves a negative test: adding 10 units of a 1-unit-stock offer must succeed on the offer's terms.
3. **Keep the last-known snapshot even after the offer is erased** (RULE-8) — otherwise the view cannot render the `unavailable` row that D18 requires.
4. **Do not clear the basket on handover** (D19): the checkout slice may fail, and a cleared basket would lose the customer's work.
