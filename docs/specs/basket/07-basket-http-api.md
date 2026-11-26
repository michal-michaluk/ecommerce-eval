# 07 — Basket HTTP API

**Role:** the API the customer's client calls. Reads are `GET`, every update is `PATCH` (**D28**). A customer may own **several active baskets** and always owns at least one (**D29**), so `basketId` is in every path and a list endpoint exists.

**Spec artifact:** [`prototypes/basket-api.prove.md`](prototypes/basket-api.prove.md) — the `prove` scenario **is** the contract: 24 steps, full-body goldens, every error path. Nothing below repeats it. Gate after every change: `prove validate` → `prove explain` → `prove run`.

**Provenance:** board commands `add to basket`, `remove from basket`, `go to checkout` (`lanes[2].commands`) plus `basket quantity changed`, which the board lacks and the transcript and stepper require (R4, D19). Error shape and status choice follow `services/fulfillment` (`processing/OrderIntakeController.java:28`, `FulfillmentErrorAdvice.java:30,59-73,75-110`).

## Contract decisions

| Decision | Value | Why |
|---|---|---|
| Verbs | `GET` for reads, `PATCH` for every update (D28) | one update verb, no `PUT`/`POST` split to remember |
| Update idempotency | `PATCH /baskets/{basketId}/items/{offerId}` carries the **absolute** quantity | a retried relative change silently doubles a basket; the repo has no `Idempotency-Key` support (F16) |
| Basket identity | `basketId` in the path; `GET /baskets` lists the customer's active baskets | D29 — several baskets are possible, one always exists |
| Auto-creation | `GET /baskets` creates a basket when the customer has none and returns it | D29 "backend creates one always, when none" |
| Selection | `PATCH /baskets/{basketId}/selection` with `scope` ∈ `item` / `shipment` / `all` | mirrors `basket.json` `selection` and the three checkboxes |
| Removal | `PATCH /baskets/{basketId}/items` with `{ "scope": "item" \| "selection" \| "all", "offerId": <required when scope=item>, "quantity": null }` — `quantity: null` is what removes | one endpoint owns every removal scope; the item endpoint `PATCH …/items/{offerId}` only ever sets a quantity ≥ 1, so a path carrying an offer can never mean "clear the basket" |
| Handover | `PATCH /baskets/{basketId}/checkout` → handover payload keyed by `basketId` (element 09, D32) | `go to checkout` is a command, not a navigation |
| Auth | bearer token, authority **`SCOPE_customer`** (D27) | operator: "just `customer`"; `fulfillment` keeps `SCOPE_shopping-service` for the service hop |
| Money in the payload | `{ "amount": 174870, "currency": "PLN" }` plus a display-only `label` | element 03; a client never parses `label` |
| Status vocabulary | `available` / `outOfStock` / `unavailable` + `stale` | element 06; the client renders, never re-derives |
| Read shape | one `GET /baskets/{basketId}` returns content **and** totals **and** the estimate | D10 pull on read; a client that computes its own totals will disagree on the first discount |

## Error contract

| Status | When | Body |
|---|---|---|
| `401` | no/invalid token | empty (`AppConfiguration.java:123`) |
| `403` | valid token, not `SCOPE_customer` | empty (`:131`) |
| `404` | basket or offer unknown | `{ "error": "BASKET_NOT_FOUND" \| "OFFER_NOT_FOUND", … }` |
| `422` | quantity ≤ 0; quantity change on `unavailable`; empty selection on checkout; checkout blocked by a selected row whose status is `unavailable` or `outOfStock` (`stale` alone never blocks); currency mismatch; basket not `active` | `{ "error": …, "reasons": [ { "offerId", "status", "stale" } ] }` — `reasons[]` is **always** an array of objects; `BLOCKED_BY_ITEMS` uses it (D33). There is no `missing[]` |
| `503` | `OfferCatalog` unreachable **and** the item has no snapshot — otherwise D26 applies (grey + `unavailable`) | `{ "error": "OFFER_READ_UNAVAILABLE" }` |

## Resolved questions

| ID | Question | Answer |
|---|---|---|
| Q25 | authority name | **`SCOPE_customer`** (D27) |
| Q26 | pull or push | **Pull** `GET`; updates `PATCH` (D28) |
| Q27 | empty basket | **Not a special case** — the backend auto-creates a basket (D29); `GET /baskets` never returns an empty list, an individual basket may have `items: []` |

## Recommendations

1. **`GET /baskets` creating a basket is a write behind a read** — it must be idempotent per customer and never create a second basket under concurrency (unique constraint on `customerId` + `active`).
2. **Return `basketId` in the handover** (D32) so the checkout slice needs no second lookup.
3. **Keep `stale` in every item of the payload** — D26 makes a greyed row reachable, and the client must not have to guess why.
4. **Prove the rejections in CI**: quantity 0, currency mismatch, unavailable quantity change, checkout with no selection, checkout blocked by `unavailable`.
