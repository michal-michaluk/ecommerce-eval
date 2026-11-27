# 04 — BasketPriceCalculation

**Role:** **Derived** — sums what the customer pays for what is selected. It computes **no discount rule**: catalog owns the price and discount model and hands it over flattened as an actual price plus a discount amount (**D22**, operator: *"we need to respect that but flattened to current/actual price and discount"*).

**Provenance:** board policy `current price of basket including dyscounts` (`eventstorming.json` `lanes[2].policies[1]`), crossing `browsing-offer → basket-management` (`boundaries[1]`); `[seg 23:33]` "we can apply automatically some discounts on the basket"; the arithmetic is fixed by the screen (`basket.json` `derivedChecks.arithmetic`).

## Logic — formula

```text
BasketPrice(basket, selection, config) =
    productValue  = sum over selected items: LineTotal(item)
    discountValue = sum over selected items: DiscountLine(item)
    deliveryValue = sum over shipments with ≥1 selected item: Rate(shipment)
    total         = productValue + deliveryValue

LineTotal(item)     = item.currentPrice × item.quantity          # exact, fine units (03 RULE-4)
DiscountLine(item)  = item.discount     × item.quantity          # flattened amount, may be 0
Rate(shipment)      = config.delivery.rate(seller, channel)      # 9,99 zł in the capture
```

- **`productValue` already reflects the discount** — `currentPrice` is the actual price (D22). `discountValue` is reported so the view can print `Rabat −10,00 zł`; adding it again would double-count.
- **Excluded from every sum:** items with `unavailable` and items not selected (D18, element 01 `VIEW-1`).
- **Included:** `outOfStock` items **while selected** — the price is real; the *checkout* is what D33 blocks. Deselecting the row is how the customer proceeds with the subset.
- **One currency per basket** (03 RULE-2); no rounding happens here.

## Parameters and value-object origins

| Value object | Values | Origin | Source |
|---|---|---|---|
| `Basket` | items, quantities, selection, availability | Business concept | element 02 |
| `Money` | `amount`, `currency`, `kind` | Business concept | element 03 |
| `currentPrice`, `discount` | flattened per item | Integration | `OfferObservation` (element 08, D22) |
| `DeliveryRate` | amount per seller/shipment | Configuration | `basket.delivery.rate` (default **`9990`** fine units = 9,99 zł — the only value any material shows) |
| `Channel` | for the rate selector | Integration | token claim (D13 boundary) |

## Variants and configurations

| Variant | Selected by | Formula | Configuration source |
|---|---|---|---|
| Default | no match | formula above | `basket.delivery.rate` |
| Free delivery | channel `SMART` | `Rate(shipment) = 0` | `basket.delivery.freeWhenSmart = true` — the capture states `0,00 zł ze SMART` |
| Multi-seller | >1 `seller` in the selection | one `Rate` per shipment, summed | same as default |

No free-delivery threshold exists (**D23**, `Q20`), so there is no third variant and no selector.

## Executable scenarios — the acceptance table

| # | selection | quantities | availability | price handed over | expected |
|---|---|---|---|---|---|
| S1 | both items (capture) | 1 + 1 | available | 99,89 / 64,99, no discount | product **164,88** + delivery 9,99 → **174,87 zł** |
| S2 | only item 1 | 1 | available | 99,89 | 99,89 + 9,99 → **109,88 zł** |
| S3 | both items | 2 + 1 | available | 99,89 / 64,99 | 264,77 + 9,99 → **274,76 zł** |
| S4 | both, item 2 `outOfStock` **selected** | 1 + 1 | one out of stock | as S1 | **174,87 zł** — the price still counts it; *handover* is blocked by element 09 (D33), not by this element |
| S5 | both, item 2 `unavailable` | 1 + 1 | one unavailable | as S1 | 99,89 + 9,99 → **109,88 zł** (excluded, D18) |
| S6 | both, SMART | 1 + 1 | available | as S1 | 164,88 + 0,00 → **164,88 zł** |
| S7 | both, catalog discount 10,00 on item 1 | 1 + 1 | available | currentPrice 89,89, discount 10,00 / 64,99 | product 154,88 + 9,99 → **164,87 zł**, discount row −10,00 |
| S8 | two sellers, one item each | 1 + 1 | available | 99,89 / 64,99 | 164,88 + 19,98 → **184,86 zł** |
| S9 | nothing selected | — | — | — | 0,00 + 0,00 → **0,00 zł** (an amount, not "unknown", 03 RULE-8) |
| S10 | quantity 0 | 0 | — | — | **rejected** — 02 RULE-3 |
| S11 | item priced in another currency | 1 | available | EUR amount in a PLN basket | **rejected** — `422 CURRENCY_MISMATCH` (03 RULE-2, D21) |
| S12 | both, item 2 `outOfStock` **deselected** | 1 + 1 | one out of stock | as S1 | 99,89 + 9,99 → **109,88 zł** — the subset that may be checked out (D33) |

## Resolved questions

| ID | Question | Answer |
|---|---|---|
| Q19 | discount per item line or basket? | **Not decided here anymore** — catalog flattens per item (D22); the basket sums `discount` lines and never applies a rule |
| Q20 | free-delivery threshold? | **None** (D23) |

## Recommendations

1. **Never recompute `currentPrice` from `price` and `discount`** — catalog is the authority (D22); recomputing re-introduces the rounding question the basket just escaped.
2. **Keep `discountValue` out of `total`** — S7 catches the double-count in one row.
3. **`currentPrice` is per unit** — S3 is the row that proves multiplication happens before summation.
4. **Add S11 to the scenario** (element 07 step 9) — currency mismatch is the one rejection the money type itself must raise.
