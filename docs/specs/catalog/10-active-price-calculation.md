# 10 — ActivePriceCalculation

Element 10 · type: calculation · accepted (generated with defaults).

The price a customer pays for one product, one currency, at one instant.

## Logic definition

```text
ActivePrice(productId, currency, at) =
    p      = the single open price entry where validFrom <= at < (validUntil or +inf)
    fixed  = sum(d.amountMinor      for d in discounts where kind = fixed      and active(at))
    pct    = min(10000, sum(d.basisPoints for d in discounts where kind = percentage and active(at)))
    net    = max(0, p.amountMinor - fixed)
    result = roundHalfUp(net * (10000 - pct) / 10000)

where
    active(at)  = validFrom <= at < (validUntil or +inf)  and  d.currency = currency (fixed only)
    subject to  exactly one open price entry per (productId, currency)   (03 RULE-3)

units      : integer minor units of the currency; scale from the currency (D1)
rounding   : half-up, once, at the end; no intermediate rounding
undefined  : no open price entry -> result is ABSENT (not 0), reported as "no price"
```

The order is fixed by D3: **fixed amounts are subtracted first, percentages applied afterwards**, floor at 0.

## Parameter and value-object origins

| Value object | Values | Origin | Source |
|---|---|---|---|
| PriceEntry | amountMinor, currency, validFrom, validUntil | Business concept | 03 PricingModel |
| Discount | kind, amountMinor \| basisPoints, currency, validFrom, validUntil | Business concept | 03 PricingModel |
| ActivationInstant | at | Integration (internal) | clock + `PriceActivated` / `DiscountActivated` events |
| Money | amountMinor, currency | Business concept | 03 PricingModel (D1) |

No external system participates.

## Variants and configurations

| Variant | Selected by | Formula | Configuration source |
|---|---|---|---|
| default | no match | fixed → percentage, floor 0 | — |
| percent-first | config `catalog.pricing.order = percent-first` | percentage first, then fixed, floor 0 | config key `catalog.pricing.order` |
| no-stacking | config `catalog.pricing.stacking = exclusive` | only the discount with the latest `validFrom` applies | config key `catalog.pricing.stacking` |

Default is D3. The alternatives exist only so the stacking policy is a decision, not a rewrite.

## Executable scenarios

Price 309,89 PLN, currency scale 2.

| Scenario | price | fixed | percent | at | expected |
|---|---|---|---|---|---|
| S1 | 30989 | — | — | in range | 30989 |
| S2 | 30989 | 2000 | — | in range | 28989 |
| S3 | 30989 | — | 1000 bp | in range | 27890.1 → 27890 |
| S4 | 30989 | 2000 | 1000 bp | in range | (30989-2000)*0.9 = 26090.1 → 26090 |
| S5 | 30989 | 40000 | — | in range | 0 (floor) |
| S6 | 30989 | — | 6000 + 6000 bp (two stacked) | in range | 0 (sum 12000 capped at 10000) |
| S7 | 30989 | 2000 | — | before discount range | 30989 |
| S8 | 30989 | — | — | before any price entry | ABSENT |
| S9 | 30989 | — | — | at exactly validFrom | applies |
| S10 | 30989 | — | — | at exactly validUntil | does not apply |

## Domain events

This calculation emits nothing; it is a pure function of 03 state and the clock.
`PriceActivated` / `DiscountActivated` (03) are the events that change its output.
