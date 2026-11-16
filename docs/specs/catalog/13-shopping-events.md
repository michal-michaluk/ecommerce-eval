# 13 — ShoppingEvents

Element 13 · type: API we expose (outbound integration) · consumer: Shopping · accepted (generated with defaults).

The only channel between this context and the shop. R1 is resolved here: **snapshot events** — one event carries the
whole published offer version, so a consumer never has to reassemble state or replay a partial update.

## Contract

```yaml
envelope:
  eventId:      UUID            # idempotency key, unique per publication
  eventType:    string
  eventVersion: 1
  occurredAt:   RFC3339 instant
  key:          productId       # partition key: one product's events stay ordered
  payload:      …
```

```yaml
OfferPublished:
  productId: PRD-75423
  offerVersion: 1
  descriptionHash: "sha256:9f2c…"     # equal content ⇒ equal hash; consumers may dedupe
  availabilityStart: 2026-07-01
  title: "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423"
  brand: { name: LEGO, verified: true }
  condition: new
  categoryPath: [Dziecko, Zabawki, Klocki, LEGO, Zestawy, Star Wars]
  parameters: [ {code: pieceCount, label: "Liczba elementów", value: 581, valueType: integer} ]
  description: { blocks: [ … ] }
  media: [ {id: M-01, position: 1, primary: true, url: "…"}, {id: M-02, position: 2, kind: video} ]
```

```yaml
PricesChanged:
  productId: PRD-75423
  currency: PLN
  price: { amountMinor: 29900, scale: 2 }
  discounts: [ {kind: percentage, basisPoints: 1000, validUntil: "2026-06-30T23:59:59Z"} ]
  effectiveFrom: 2026-07-01T00:00:00Z
```

```yaml
ProductRemoved:
  productId: PRD-75423
  removedAt: 2026-06-01T09:12:00+02:00
```

## Rules

- **RULE-1** exactly three public event types: `OfferPublished`, `PricesChanged`, `ProductRemoved`. Nothing about drafts, reviews or quality findings ever leaves.
- **RULE-2** `OfferPublished` is a snapshot of the frozen offer version (02 RULE-4, 04 RULE-3); a revert publishes a new version number with the older content, never a "rollback" event.
- **RULE-3** `PricesChanged` fires only when a price or discount becomes **active** (03 RULE-4) — scheduling is silent.
- **RULE-4** ordering: events for one `productId` are delivered in publication order (single partition key); no cross-product ordering guarantee.
- **RULE-5** delivery is at-least-once; consumers deduplicate on `eventId`.
- **RULE-6** `availabilityStart` travels in `OfferPublished`; whether the shop hides the offer until then is its own decision (11 is the reference behaviour, not a mandate).
- **RULE-7** no event carries a price for a currency that has no open price entry; a product published without a price is legal (prices arrive later).

## Parameter and value-object origins

| Value object | Values | Origin | Source |
|---|---|---|---|
| OfferSnapshot | title, brand, condition, categoryPath, parameters, description, media | Business concept | 02 Offer + 01 ProductDescription (frozen version) |
| PriceSnapshot | currency, amountMinor, discounts | Business concept | 10 ActivePriceCalculation |
| Envelope | eventId, occurredAt, key | Configuration | topic contract `catalog.offer.events.v1` |

## Variants and configurations

| Variant | Selected by | Behaviour | Configuration source |
|---|---|---|---|
| default | no match | snapshot event, enrichment included | — |
| thin | config `catalog.events.payload = thin` | `OfferPublished` carries ids + version only, consumers fetch the rest | config key `catalog.events.payload` |

`thin` exists for consumers that prefer to pull; the default stays `snapshot` because the board offers no reason to split.

## Executable scenarios

| Scenario | action | expected events |
|---|---|---|
| S1 | review accepted, findings empty | `OfferPublished v1` |
| S2 | price scheduled for 2026-07-01 | none |
| S3 | clock reaches 2026-07-01 | `PricesChanged` |
| S4 | discount scheduled | none |
| S5 | discount activates | `PricesChanged` |
| S6 | offer removed | `ProductRemoved` |
| S7 | revert to version 1 while at version 3 | `OfferPublished v4` with version-1 content |
| S8 | same publication replayed | consumer ignores duplicate `eventId` |
| S9 | draft edited | none |

## Domain events

Public: `OfferPublished` · `PricesChanged` · `ProductRemoved`.
In-context only (never public): `DescriptionEdited`, `OfferDraftUpdated`, `PriceScheduled`, `DiscountScheduled`, `ReviewRequested`, `ReviewAccepted`, `ReviewRejected`, `ReviewWithdrawn`, `PublishBlocked`.
