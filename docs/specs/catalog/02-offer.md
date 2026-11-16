# 02 — Offer

Element 02 · type: business concept · roles: **Editor** (mutable draft surface) + **Definition** (frozen version) · accepted.

The editor for an immutable `ProductDescription`: drafts tolerate quality violations, publication freezes a version that
holds the description **by value**. One offer per product (Q8 closed: 1 : 1).

`productId` in these instances is the offer's own identifier — the same value 12 exposes as `offerId` and 13 carries as `productId` (D31).

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
version, not a state. A review opened on a **published** offer does not move it back: the offer stays `published` and keeps
serving its current version while the candidate is reviewed (D37 — otherwise re-reviewing a live product would take it off the shop,
because 11 R3 hides `pendingReview`).

## Domain rules

- **RULE-1** ≥1 image required before a review can be requested; exactly one `primary: true`.
- **RULE-2** `position` explicit and contiguous from 1; at most one video per offer.
- **RULE-3** `availabilityStart` is a date, may lie in the future; publication with a future date leaves the offer hidden (11 OfferVisibility).
- **RULE-4** publication embeds the description **by value** in the frozen offer version; the frozen version never re-reads a draft, so a later
  edit cannot change what was published. The content frozen is the **candidate 04 froze at request time**, handed over through
  `OfferPublication` — never the live draft, which may have changed since the request (D38: `publish(candidate, at, by, registry)`).
- **RULE-5** revert = create a new offer version copying an older version's content; the old version number is never reused (01 RULE-3).
- **RULE-6** `removed` is terminal for that product's offer (Q11).
- **RULE-7** the editor holds the description in builder form (`ProductDescription.Draft`): same components, nullable fields, no invariants. It never holds a frozen `ProductDescription`, and a frozen `OfferVersion` embeds the value — there is no `descriptionVersion` reference to resolve (D24/D25).
- **RULE-8** quality violations are legal in the draft; only freeze enforces invariants. 08 QualityPolicy reports findings continuously on the draft, 09 PublishDecision blocks the transition.
- **RULE-9** the draft is immutable like everything else — `merge(...)` returns a new draft, no setters, no Lombok `@Data`; `@Builder(toBuilder = true)` allowed. Encoded: `aggregate.no-setters` and `vo.no-setters`. **Not encoded:** there is no rule against `@Data` — the ids `model.no-setters`/`model.no-lombok-data` cited earlier do not exist in this service (verified against `src/main/java/.sqt.yaml`), so immutability here is a review rule; a `@Data` check would be a gate addition (see plan §Open Topics).
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
- OfferDraftUpdated:     { productId: PRD-75423 }          # in-context only, ids only (01 RULE-4)
- OfferReviewRequested:  { productId: PRD-75423, reviewId: RV-12 }   # D36: the draft -> pendingReview fact
- OfferVersionFrozen:    { productId: PRD-75423, offerVersion: 1, description: { … } }   # the value itself
- OfferRemoved:          { productId: PRD-75423 }
- OfferVersionReverted:  { productId: PRD-75423, offerVersion: 3, copiedFrom: 1 }

`descriptionHash` is **not** part of these in-context events: the model may not import a hashing tool
(`model.no-tools-dependency`, and 01 says the frozen version "carries the value"). The hash promised by 13 is computed at
the contract boundary by the outbound adapter, from the same value it serialises (D39). Five events is the whole
catalogue — `OfferReviewRequested` had to be added, because 02's own `draft -> pendingReview` edge writes state and
`aggregate.mutation-emits-event` forbids a state write without an event (D36).
```

## Implementation note

Four files, one concept each, named as `domain-model-aggregate.md:33` prescribes — the **editor** aggregate carries the role
suffix, the bare business noun is the snapshot record:

- `OfferEditor.java` — the package-private aggregate: draft surface, lifecycle commands, revert/removal, and the
  `final List<OfferEvent> events` buffer (`domain-model-aggregate.md:35`). This is the only writer.
- `Offer.java` — the public snapshot record published after a write: id, state, current version number, `availabilityStart`.
- `OfferVersion.java` — the frozen version: version number, the embedded `ProductDescription` **by value** (RULE-4), media,
  `publishedAt`/`publishedBy`, `copiedFrom`.
- `ProductDescription.java` — the Definition and its nested `Draft` (01).

The draft reuses the components declared in `ProductDescription.java` — `Identifiers`, `Parameter`, `DescriptionBlock` (+ sealed
alternatives) — so no second shape of the same content exists.

## Decisions taken at acceptance

- **Q11** removal is terminal: the same product is never offered again and version numbering never restarts (RULE-6).
- **Q12** media `format` is accepted as sent, unvalidated (JPG/PNG/WebP per D17); no size limit, no dimension check.
