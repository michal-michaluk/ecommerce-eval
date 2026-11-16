# 03 — PricingModel

Element 03 · type: business concept · role: **Editor** (living configuration with history, no freeze step) · accepted.

Current price, scheduled future prices and discounts, and historical prices — kept apart from the current one.

Named per `domain-model-aggregate.md:33`: the aggregate is `PricingModelEditor` (package-private, holds the
`final List<PricingEvent> events` buffer), the published snapshot record is `PricingModel` (current, scheduled, history, discounts).

## Instance A — one active price + one scheduled change + two discounts

```yaml
productId: PRD-75423
prices:
  - { priceId: P-1, currency: PLN, amountMinor: 30989, validFrom: "2026-05-01T00:00:00Z", validUntil: "2026-07-01T00:00:00Z" }   # current
  - { priceId: P-2, currency: PLN, amountMinor: 29900, validFrom: "2026-07-01T00:00:00Z", validUntil: null }   # scheduled
discounts:
  - { discountId: D-1, kind: percentage, basisPoints: 1000, validFrom: "2026-06-20T00:00:00Z", validUntil: "2026-07-01T00:00:00Z" }
  - { discountId: D-2, kind: fixed, amountMinor: 2000, currency: PLN, validFrom: "2026-07-01T00:00:00Z", validUntil: "2026-07-08T00:00:00Z" }
```

## Instance B — historical price kept after a change

```yaml
productId: PRD-75375
prices:
  - { priceId: P-7, currency: PLN, amountMinor: 28499, validFrom: "2026-01-10T00:00:00Z", validUntil: "2026-05-01T00:00:00Z" }
  - { priceId: P-8, currency: PLN, amountMinor: 27900, validFrom: "2026-05-01T00:00:00Z", validUntil: null }
discounts: []
```

## Domain rules

- **RULE-1** money is `amountMinor:int` + `currency`; scale comes from the currency (D1: PLN 2, JPY 0) — never a float, never a BigDecimal for storage.
- **RULE-2** price validity ranges are half-open `[validFrom, validUntil)`; a price change closes the previous entry (`validUntil = newValidFrom`) and appends a new one — no in-place overwrite, so history is the table.
- **RULE-3** at most one open price per `(productId, currency)` at any instant — enforced by closing the predecessor in the same operation.
- **RULE-4** a scheduled price or discount is silent **outside the context**: no `*Activated` fact and nothing leaves the context
  until `validFrom` is reached (transcript 45–47, R3). The in-context `*Scheduled`/`*Removed` records are bookkeeping, not publication.
- **RULE-5** percentage discount stores integer basis points, `0 < basisPoints ≤ 10000`; fixed discount stores `amountMinor` + `currency` and must match the price currency.
- **RULE-6** a discount never mutates a price entry; it is resolved at read time by 10 ActivePriceCalculation.
- **RULE-7** editing a future entry is allowed at any time; editing an entry whose range already started closes it at now and appends the new one.
- **RULE-8** a started entry cannot be deleted — only closed by a new one; only entries with `validFrom > now` may be removed (`409 PRICE_ALREADY_STARTED` in 12).
- **RULE-9** activation is polled, not slept on: a scheduled task (`@Scheduled`, interval `catalog.pricing.activation-interval`, default
  `PT60S`) asks the `Clock` for now and activates every entry whose `validFrom <= now` and that carries no activation marker yet, in
  `validFrom` order. Activation is idempotent — the marker is written in the same transaction as the `*Activated` event — and it
  catches up: entries that became due while the service was down activate at the first poll after start, emitted with `occurredAt` =
  the moment of the poll and `effectiveFrom` = the entry's `validFrom` (13 RULE-8).

## Instance diff — schedule a price change

```diff
  prices:
    - { priceId: P-1, amountMinor: 30989, validFrom: "2026-05-01T00:00:00Z", validUntil: "2026-07-01T00:00:00Z" }
+   - { priceId: P-2, amountMinor: 29900, validFrom: "2026-07-01T00:00:00Z", validUntil: null }
```

## Domain events

```yaml
- PriceScheduled:    { productId: PRD-75423, priceId: P-2, validFrom: "2026-07-01T00:00:00Z" }   # in-context
- DiscountScheduled: { productId: PRD-75423, discountId: D-2, validFrom: "2026-07-01T00:00:00Z" } # in-context
- PriceRemoved:      { productId: PRD-75423, priceId: P-2 }        # in-context, a never-started entry
- DiscountRemoved:   { productId: PRD-75423, discountId: D-2 }     # in-context, a never-started entry
- PriceActivated:    { productId: PRD-75423, currency: PLN, amountMinor: 29900 }                 # leaves the context
- DiscountActivated: { productId: PRD-75423, discountId: D-2 }
```

Only the `*Activated` events leave the context, and they **map onto the public type** of 13 RULE-1:
`PriceActivated`/`DiscountActivated` are published as `PricesChanged` (13 RULE-3) — they never travel under their own name.

## Decisions taken at acceptance

- **Q13 → D2 currency scope:** currency per price entry, so one product may carry a PLN and a EUR price independently; at most one open entry per currency.
- **Q14 → D3 discount stacking:** discounts stack deterministically — fixed amounts summed and subtracted first, then percentages summed and applied, floor at 0; overlapping ranges allowed.
