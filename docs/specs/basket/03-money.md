# 03 — Money

**Role:** **Definition** — immutable value object. Every amount in the basket is an instance: item line totals, discounts, deliveries, basket totals.

**Provenance:** currency and formatting come from the only basket screen — `basket.json` shows `99,89 zł`, `164,88 zł`, `174,87 zł`, `9,99 zł`, PLN with a comma separator. The representation is decision **D34**, which refines **D14**; the codebase has no money type at all (**F14** — `kernel/Payment.java:10`, `processing/ProcessingSnapshot.java:27`).

## Representation

| Field | Type | Rule |
|---|---|---|
| `amount` | `long` | the amount in units of **10^-(decimals+1)** — one digit finer than the currency's minor unit. For PLN (2 decimals) one unit is a **tenth of a grosz**, so 99,89 zł is `99890` |
| `currency` | `String`, ISO-4217 | carried explicitly, never implied (D21: any currency; one per basket) |
| `kind` | `charge` (default) or `discount` | only a discount is ever rendered with a minus |

**Why one digit finer than minor.** The extra digit exists so a discount or a rate can be *computed* without losing precision, while the basket keeps control of the single rounding step: compute in the fine unit, round **half-up once** to the minor unit when an amount is presented or compared. That is exactly the operator's reason: *"amount is number of minor cents/groszy × 10, so calculating discounts gives us control over rounding"*.

```text
fine( zł )     = zł × 1000          # PLN: 2 decimals + 1
minor( zł )    = round_half_up( fine / 10 )
render( fine ) = "99,89 zł"
```

## Instance examples

```yaml
- { amount: 99890,  currency: PLN }   # 99,89 zł   — item current price, one unit
- { amount: 64990,  currency: PLN }   # 64,99 zł
- { amount: 164880, currency: PLN }   # 164,88 zł  — shipment RAZEM / Wartość produktów
- { amount: 9990,   currency: PLN }   # 9,99 zł    — Dostawa od
- { amount: 174870, currency: PLN }   # 174,87 zł  — Razem z dostawą
- { amount: 10000,  currency: PLN, kind: discount }   # rendered Rabat −10,00 zł
```

## Domain rules

- **RULE-1 Immutable.** An amount is never edited; a change produces a new amount.
- **RULE-2 One currency per basket.** The type carries any ISO code (D21), but a basket holds exactly one: an item priced in another currency is refused (`422 CURRENCY_MISMATCH`, element 07), never converted.
- **RULE-3 Exact addition.** Sums are exact in the fine unit — integers only, no floating point, no rounding at any step.
- **RULE-4 Multiplication by quantity is exact.** `lineTotal = currentPrice × quantity`.
- **RULE-5 Rounding is explicit and happens once.** Every computation stays in the fine unit; when an amount must be presented or compared in the minor unit, it is rounded **half-up exactly once**, at that boundary. No intermediate rounding, ever.
- **RULE-6 No negative storage.** A discount is a positive `amount` with `kind: discount`; a subtraction that would go negative is refused.
- **RULE-7 Rendering is `pl-PL`.** Two decimals, comma separator, non-breaking space before the code: `99,89 zł`. Rendering never changes the stored value.
- **RULE-8 Zero is a valid amount.** `0,00 zł` exists (`za 0,00 zł ze SMART`) and is not the same as "unknown".

## Resolved questions

| ID | Question | Answer |
|---|---|---|
| Q17 | any currency other than PLN? | **Any ISO-4217 currency**, one per basket (D21) |
| Q18 | discounts ever a percentage? | Not in the basket — catalog flattens them (D22); the fine unit is what makes a percentage computable upstream without losing precision |

## Recommendations

1. **Implement as `record Money(long amount, String currency, Kind kind)` in `kernel/`** — the only package both slices share (F10).
2. **Never expose `amount` to a client without `currency`** — the pair travels together, always.
3. **Put the scale in one place** — a currency's `decimals` decides the factor (`10^(decimals+1)`); hard-coding 1000 breaks the first non-PLN currency D21 promises.
4. **Test RULE-5 with a value that needs rounding** — a discount of `33333` fine units is 3333,3 groszy: the single half-up step must produce `3333` groszy, not `3333,3` carried forward.
