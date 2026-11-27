# 09 — Checkout handover

**Role:** integration **inside the same microservice** (D5): the `basket` slice hands over to the `checkout` slice. In-process command, **keyed by `basketId`** (**D32**), not a network call — so the artifact is a payload contract, not a `prove` scenario.

**Provenance:** board boundary `basket-management → checkout-process`, single crossing `go to checkout` (`eventstorming.json` `boundaries[2]`); `[seg 85-88]` "terminal action … guarded action … we need to have all that matching". The checkout slice itself is **deferred** (D4).

## Contract — what the basket hands over

```yaml
handoverId: B-7f3a            # == basketId (D32): one active handover per basket
revision: 7                   # monotonic per basket; checkout discards a handover received stale (D35)
basketId: B-7f3a
createdAt: "2026-11-26T15:40:00Z"
customerId: C-42              # from the token, never from the payload
currency: PLN                 # one per basket (03 RULE-2)
selection:                            # every row below is handable: no outOfStock, no unavailable (HAND-1, D33)
  - { offerId: OF-1001, seller: UGREEN_POLSKA, quantity: 2,
      currentPrice: { amount: 99890, currency: PLN },
      discount:     { amount: 0,    currency: PLN, kind: discount },
      lineTotal:    { amount: 199780, currency: PLN },
      availability: available, stale: false }
  - { offerId: OF-1002, seller: UGREEN_POLSKA, quantity: 1,
      currentPrice: { amount: 64990, currency: PLN },
      discount:     { amount: 0,    currency: PLN, kind: discount },
      lineTotal:    { amount: 64990, currency: PLN },
      availability: available, stale: false }
totals:
  productValue:      { amount: 264770, currency: PLN }
  discountValue:     { amount: 0,     currency: PLN, kind: discount }
  delivery:          { amount: 9990,   currency: PLN }
  totalWithDelivery: { amount: 274760, currency: PLN }
deliveryEstimate: { kind: estimate, from: "2026-11-28", to: "2026-11-30", source: configured }   # element 05, D36
```

**Shape of record (D41).** The `yaml` above is the **in-process command payload**, not the HTTP answer. The answer is fixed by [`prototypes/basket-api.prove.md`](prototypes/basket-api.prove.md) steps 18–19: `{ handoverId, basketId, createdAt, customerId, currency, selection: [offerId], revision, totals }` — no `checkoutId`, no `deliveryEstimate`. `checkoutId` stays inside the service: the receiving slice returns it, the basket does not publish it. The answer's `selection` is the **offer ids**, not this payload's line rows — the HTTP response is a projection of the handover, which is why `Handover.Line` (a slice type) never reaches the client. `handoverId` equals `basketId` (D32), `revision` is the one captured in step 18 and echoed in step 19 (D35), and the basket is **not cleared** (D19).

The basket keeps exactly one derived field of it — `lastHandover { revision, contentDigest, handoverId, createdAt }` — so a repeat of an unchanged basket returns the same handover (HAND-4, DoD30) without the basket ever depending on checkout's state. The digest is computed over the canonical handover payload, the way `Order.canonicalDigest()` is the idempotency key of `POST /orders` (`services/fulfillment/src/main/java/shop/fulfillment/kernel/Order.java:91`). The canonical form is **versioned** — `basket-handover-v3` today: it covers the **rows and the totals**, so a configuration change that moves the amounts already produces a new identity (D51). It deliberately does **not** cover the estimate, whose range is clock-derived: including it would make an untouched basket handed over across midnight a new handover (D64). Widening it again is a `v4`, never a silent change.

## Rules

- **HAND-1 Nothing blocked crosses.** Refused while any **selected** item's status is `unavailable` or `outOfStock` (element 06 blocking set, **D33**); a row that is `available` with `stale: true` (stock unread) does **not** block. The customer deselects the offending row and hands over the remaining subset; the materials called out-of-stock non-blocking (`[seg 70-71]`), the operator decided otherwise, and D33 records the divergence.
- **HAND-2 Selection only.** Unselected items are absent, not zero-quantity.
- **HAND-3 The basket never creates the order.** Order creation and the downstream `ordered` crossing into fulfilment (`boundaries[3]`, `POST /orders` with `SCOPE_shopping-service`) belong to the checkout slice.
- **HAND-4 One active handover per basket.** The key is `basketId` (D32): repeating the command returns the same `handoverId` while the basket content is unchanged, and a **changed** basket produces a new handover that supersedes the old one. The identity is the content digest stored in `lastHandover` (D41): same digest ⇒ same `handoverId` and the same `revision`; different digest ⇒ the next `revision` and a new handover.
- **HAND-5 Snapshot, not authority.** Prices cross as the basket computed them; the checkout slice **re-prices and reports the difference** (**D31**). The basket never claims its price is final.
- **HAND-6 No clearing, no locking.** The basket stays `active` and editable (D19); a failing checkout must not cost the customer the basket.

## Failure outcomes

| Failure | Basket outcome |
|---|---|
| nothing selected | refused before handover — `422 NOTHING_SELECTED` (element 07) |
| a selected item is `unavailable` or `outOfStock` | refused — `422 BLOCKED_BY_ITEMS` with the offer ids, their statuses and the `stale` flag (D33) |
| basket became `active → archived` meanwhile | refused — `422 BASKET_INACTIVE` |
| checkout slice rejects the payload | `500`, logged; basket unchanged, retry allowed. **Unreachable in this increment**: the only refusal the in-process receiver can answer is its own `HANDOVER_STALE` (D35), and the basket hands over the revision it has just stored |
| checkout slice not deployed yet | `503`; basket unchanged — expected until D4's spec lands. **Unreachable in this increment**: both slices ship in one process, so there is nothing to be undeployed |

## Resolved questions

| ID | Question | Answer |
|---|---|---|
| Q31 | does checkout re-price? | **Yes, and reports the difference** (D31) — HAND-5 |
| Q32 | synchronous call or internal command? | **In-process command keyed by `basketId`** (D32) — HAND-4; replay-safe without a content digest |

## Recommendations

1. **Put the payload type in `kernel/`** — the only package both slices may share (F10), together with `Money` and `Availability`.
2. **`revision` is now part of the contract** (D35, closes R16): increment it on every basket mutation and let checkout reject a handover older than the one it already holds.
3. **Do not validate checkout's rules here** (payment and delivery methods, `[seg 77-79]`) — the basket enforces HAND-1/HAND-2 and nothing else.
4. **Negative test first:** "handover with an `unavailable` item selected is refused *and the basket is untouched*" proves HAND-1 and HAND-6 together.
