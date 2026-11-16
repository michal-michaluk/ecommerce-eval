# 02 — Offer

Element 02 · type: business concept · roles: **Editor** (mutable draft surface) + **Definition** (frozen version) · accepted.

The editor for an immutable `ProductDescription`: drafts tolerate quality violations, publication freezes a version that
holds the description **by value**. One offer per product (Q8 closed: 1 : 1).

## Instance A — OfferEditor, draft with a tolerated violation (no eanGtin yet)

```yaml
productId: PRD-75423
description:                            # ProductDescription.Draft
  title: "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423"
  ids: { productNumber: "75423", eanGtin: null }     # violation, allowed while drafting
  parameters: [ { code: pieceCount, value: 581, valueType: integer } ]
  blocks: [ { type: paragraph, text: "Odkryj nową galaktykę…" } ]
media: [ { id: M-01, kind: image, position: 1, primary: true, format: jpg } ]
availabilityStart: 2026-07-01
```

## Instance A — OfferVersion v1, frozen by publication

```yaml
productId: PRD-75423
offerVersion: 1
description: { title: "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423", … }   # the value object, embedded
media: [ {id: M-01, position: 1, primary: true}, {id: M-02, position: 2}, {id: M-03, position: 3} ]
availabilityStart: 2026-07-01
publishedAt: "2025-06-15T10:00:00+02:00"
publishedBy: reviewer.kowalska
state: published          # visible from 2026-07-01 (see 11)
```

## Instance B — second product, past availability, then removed

```yaml
productId: PRD-75375
offerVersion: 3
description: { … }
availabilityStart: 2026-05-10
state: removed
removedAt: "2026-06-01T09:12:00+02:00"
```

## Lifecycle

`draft → pendingReview (04) → published → removed`. `removed` is reachable only from `published`; revert is a new
version, not a state.

## Domain rules

- **RULE-1** ≥1 image required before a review can be requested; exactly one `primary: true`.
- **RULE-2** `position` explicit and contiguous from 1; at most one video per offer.
- **RULE-3** `availabilityStart` is a date, may lie in the future; publication with a future date leaves the offer hidden (11 OfferVisibility).
- **RULE-4** publication embeds the description **by value** in the frozen offer version; the frozen version never re-reads a draft, so a later edit cannot change what was published.
- **RULE-5** revert = create a new offer version copying an older version's content; the old version number is never reused (01 RULE-3).
- **RULE-6** `removed` is terminal for that product's offer (Q11).
- **RULE-7** the editor holds the description in builder form (`ProductDescription.Draft`): same components, nullable fields, no invariants. It never holds a frozen `ProductDescription`; a frozen `OfferVersion` pins a `descriptionVersion` number.
- **RULE-8** quality violations are legal in the draft; only freeze enforces invariants. 08 QualityPolicy reports findings continuously on the draft, 09 PublishDecision blocks the transition.
- **RULE-9** the draft is immutable like everything else — `merge(...)` returns a new draft, no setters, no Lombok `@Data` (`model.no-setters`, `model.no-lombok-data`); `@Builder(toBuilder = true)` allowed.
- **RULE-10** `Draft.toDefinition()` is the only producer of a `ProductDescription`; it throws on a violated invariant — one rule set, one place.
- **RULE-11** the aggregate enforces the population rules a value object cannot: `productNumber` and `eanGtin` are unique across offers.

## Instance diff — draft → published with future availability

```diff
  productId: PRD-75423
+ offerVersion: 1
+ description: { … }                 # embedded by value
+ publishedAt: "2025-06-15T10:00:00+02:00"
+ state: published
  availabilityStart: 2026-07-01
```

## Domain events

```yaml
- OfferDraftUpdated:     { productId: PRD-75423 }          # in-context only
- OfferVersionFrozen:    { productId: PRD-75423, offerVersion: 1, descriptionHash: "sha256:9f2c…" }
- OfferRemoved:          { productId: PRD-75423 }
- OfferVersionReverted:  { productId: PRD-75423, offerVersion: 3, copiedFrom: 1 }
```

## Implementation note

Two files, one concept each: `Offer.java` (editor + frozen version + lifecycle) and `ProductDescription.java`
(the Definition and its nested `Draft`, per `01-product-description.md`). The draft reuses the components declared in
`ProductDescription.java` — `Identifiers`, `Parameter`, `DescriptionBlock` (+ sealed alternatives) — so no second
shape of the same content exists.

## Open questions

- **Q11** after removal, may the same product be offered again, and does numbering continue or restart?
- **Q12** media formats and minimum quality are unspecified on the board; RULE-1 assumes JPG/PNG/WebP, no size limit.
