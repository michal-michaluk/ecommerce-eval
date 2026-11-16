# 16 — PricingView

Element 16 · type: UI view · actor: **Sales** · accepted (generated with defaults).

Current price, scheduled future entries and discounts, and the historical prices — kept visually apart, because the
board's editor exists precisely to stop history from being overwritten.

## Screen — pricing per product

```yaml
view: Pricing(productId, currency)
current:                          # 03 open entry
  priceId: P-1
  amountMinor: 30989
  currency: PLN
  validFrom: "2026-05-01T00:00:00Z"
  validUntil: null
scheduled:                        # future entries, soonest first
  - { priceId: P-2, amountMinor: 29900, validFrom: "2026-07-01T00:00:00Z" }
  - { priceId: P-3, amountMinor: 27900, validFrom: "2026-09-01T00:00:00Z" }
discounts:
  active:    [ { discountId: D-1, kind: percentage, basisPoints: 1000, validUntil: "2026-06-30T23:59:59Z" } ]
  scheduled: [ { discountId: D-2, kind: fixed, amountMinor: 2000, validFrom: "2026-07-01T00:00:00Z" } ]
  expired:   [ { discountId: D-0, kind: percentage, basisPoints: 500, validUntil: "2026-05-31T23:59:59Z" } ]
history:                          # closed entries, newest first, read-only
  - { priceId: P-0, amountMinor: 31900, validFrom: "2026-01-10T00:00:00Z", validUntil: "2026-05-01T00:00:00Z" }
effectivePreview:                 # 10's output for the chosen date
  at: 2026-07-02
  amountMinor: 26090
actions:
  - { name: "zmień cenę", effect: "close current at now, append new entry" }
  - { name: "zaplanuj cenę", requires: validFrom > now }
  - { name: "dodaj rabat", kinds: [percentage, fixed], requires: date range }
  - { name: "usuń zaplanowany wpis", enabledWhen: validFrom > now }
```

## Rules

- **RULE-1** the current price is never edited in place: a change closes the current entry and appends a new one (03 RULE-2).
- **RULE-2** only future entries can be deleted; a started entry is closed by a new one instead (03 RULE-7).
- **RULE-3** a fixed discount's currency is fixed to the price currency and cannot be typed freely (03 RULE-5).
- **RULE-4** the screen shows `effectivePreview` from 10 for a date the user picks, so the effect of stacking (D3) is visible before saving.
- **RULE-5** history rows are read-only, no action rendered.
- **RULE-6** validation: `basisPoints` 1…10000; `amountMinor` > 0; `validFrom` < `validUntil` when both present.

## Parameter and value-object origins

| Value object | Values | Origin | Source |
|---|---|---|---|
| PriceEntries | current, scheduled, history | Business concept | 03 PricingModel |
| DiscountEntries | active, scheduled, expired | Business concept | 03 PricingModel |
| EffectivePreview | at, amountMinor | Business concept | 10 ActivePriceCalculation |

## Variants and configurations

| Variant | Selected by | Behaviour | Configuration source |
|---|---|---|---|
| default | no match | per currency, tabs current/scheduled/history | — |
| multi-currency | `catalog.pricing.currencies` lists ≥2 | currency switcher rendered, one pricing model per currency (D2) | config key |
| no-preview | `catalog.pricing.preview = false` | `effectivePreview` hidden | config key |

## Scenarios

| Scenario | given | expected |
|---|---|---|
| S1 | one open price | shown under `current`, history empty |
| S2 | change price now | current closed at now, new row under `current`, old row under `history` |
| S3 | schedule price for 2026-07-01 | row under `scheduled`, no event emitted (03 RULE-4) |
| S4 | delete scheduled entry | row removed, no history entry created |
| S5 | delete started entry | action absent (RULE-2) |
| S6 | percentage 12000 bp | validation error (RULE-6) |
| S7 | fixed discount in EUR with PLN price | currency locked, cannot be chosen (RULE-3) |
| S8 | two currencies priced | switcher visible, totals never mixed |

## Domain events

Consumes `PriceActivated`, `DiscountActivated`. Emits commands only (`set price`, `schedule price`, `add discount`) — no events of its own.
