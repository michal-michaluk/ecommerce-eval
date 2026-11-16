# 17 — Contexts inside catalog

Element 17 · type: decomposition · scope: one bounded context (catalog) · accepted.

Five contexts inside the service, cut so that each owns one reason to change and every pair talks only through the two mechanisms the
architecture already allows: a **published record** (in-process event) or a **contract port** implemented by a **mediator**
(`docs/arch/microservice-java-spring/context-boundaries.md`). A context is a package directly under `shop.catalog`; the boundary is the
pair of allow-lists in `ArchitectureOf{Context}Test`, so an edge cannot appear without editing two tests.

```mermaid
flowchart LR
  subgraph catalog["shop.catalog"]
    offer["offer<br/>Editor + Definition"]
    pricing["pricing<br/>Editor (living config + history)"]
    review["review<br/>Process"]
    quality["quality<br/>Policy / calculation"]
    reads["reads<br/>Read models"]
  end
  shopping["Shopping<br/>(other service)"]

  offer -- "Offer (snapshot, carries the draft surface)" --> reads
  review -- "Review (snapshot)" --> reads
  pricing -- "PricingChanged (event)" --> reads
  review -- "QualityFindings (contract port, mediator)" --> quality
  reads -- "DraftFindings (contract port, mediator)" --> quality
  reads -- "EffectivePrices (contract port, mediator)" --> pricing
  review -- "OfferPublication (contract port, mediator)" --> offer
  review -- "OfferDraftSnapshot (contract port, mediator)" --> offer
  review -- "OfferReview (contract port, mediator)" --> offer
  offer -- "OfferVersionFrozen (event)" --> pricing
  offer -- "OfferPublished / ProductRemoved (snapshot event, Kafka)" --> shopping
  pricing -- "PricesChanged (snapshot event, Kafka)" --> shopping
```

## Subdomain: offer

**Responsibilities:** owns the offer document — the mutable draft (description content, media, availability start), the immutable
frozen versions, and the product's lifecycle state. It is the only writer of description and media, and the only producer of published
offers.

**Core capabilities:** create offer · edit description draft · attach/reorder media · set availability start · publish a version (invoked
by `review` through the port) · revert · remove.

**Integrations**

| Direction | Counterpart | Type | What crosses |
|---|---|---|---|
| → | `reads` | event | `OfferDraftUpdated`, `OfferReviewRequested`, `OfferVersionFrozen`, `OfferRemoved` |
| → | `pricing` | event | `OfferVersionFrozen` (pricing keeps a local "offer exists" fact) |
| ← | `review` | contract port + mediator | `OfferDraftSnapshot` (read), `OfferReview` (command — ask to enter review) `OfferPublication` (command) |
| → | Shopping | snapshot event | `OfferPublished`, `ProductRemoved` (13) |
| → | `tools` | dependency | `Clock`, JSON mapper |

**Actors:** ContentAuthor, ContentReviewer (revert, removal), Photographer (media component only).

**Components:** *Editor* `OfferEditor` (aggregate, package-private); *Value Object in the shared kernel* `ProductDescription` (+ `Draft`, nested components); *Snapshot* `Offer` (exported — another context reads an offer without its editor); *Definition* `OfferVersion`;
*Invariant* the offer aggregate — one primary image, contiguous positions, availability date shape, immutable versions (02 RULE-1…11).

Exported values (`sharedKernelExposed`): `ProductDescription` (+ its nested `Draft`), `Media`, `OfferCandidate`, `OfferDraft` and the `Offer`
snapshot. The media value moved out of `OfferVersion` so a mediator can name the shape it maps a reviewed candidate into; the review edge
is the only consumer today. `ProductDescription` carries no identity or lifecycle, so `pricing`,
`review`, `quality` and `reads` may all hold it as a value without an edge beyond the shared-kernel list.

## Subdomain: pricing

**Responsibilities:** owns every price and discount of an offer, current and scheduled, and the history that must never be overwritten.
Answers "what does it cost at instant t".

**Core capabilities:** set/close a price entry · schedule a price · add/remove discounts (percentage, fixed) · keep history ·
compute the active price.

**Integrations**

| Direction | Counterpart | Type | What crosses |
|---|---|---|---|
| ← | `offer` | event | `OfferVersionFrozen` → local `KnownOffer` fact, so a price cannot be set for an unknown offer |
| → | `reads` | event | `PricingChanged` |
| → | Shopping | snapshot event | `PricesChanged` (13) |
| → | `tools` | dependency | `Clock` (activation moments) |

**Actors:** Sales.

**Components:** *Editor* `PricingModelEditor` (aggregate, package-private; living configuration with history); *Snapshot* `PricingModel`; *Policy* `ActivePriceCalculation` (10) — the only place
where stacking (D3) and currency precision (D1) live.

## Subdomain: review

**Responsibilities:** owns the review lifecycle of a candidate — freezes what is being reviewed, records the decision, and triggers
publication on acceptance. It is the pivot: responsibility for the offer moves from authoring to the shop at this point.

**Core capabilities:** request review (ask the offer to enter review, then freeze the candidate) · read pending reviews · accept (publishes) · reject with reason · withdraw.

**Integrations**

| Direction | Counterpart | Type | What crosses |
|---|---|---|---|
| → | `offer` | contract port + mediator | `OfferDraftSnapshot` (read the draft), `OfferReview` (ask the offer to enter review, so 02 RULE-1 and removal decide the request), `OfferPublication` — freeze **the candidate captured at request**, not the live draft (D38) |
| → | `quality` | contract port + mediator | `QualityFindings` (frozen with the candidate, and for the screen) |
| → | `reads` | event | `ReviewRequested`, `ReviewAccepted`, `ReviewRejected`, `ReviewWithdrawn` |
| → | `offer` | contract port + mediator | `OfferReview` (enter review) and `OfferPublication` (freeze) — the offer learns a decision by being asked, there is no listener on `ReviewAccepted` yet (R6) |

**Actors:** ContentAuthor (request, withdraw own), ContentReviewer (read, decide).

**Components:** *Process* `ReviewProcess` (04); *Policy* `PublishDecision` (09).

## Subdomain: quality

**Responsibilities:** answers "what is wrong with this draft" — no persistence of business facts except the per-category configuration.
It is a calculation context, so it answers through a contract port and never reaches into another context's model.

**Core capabilities:** evaluate QP-1…QP-8 universally · evaluate QP-10…QP-12 for the matched category · report findings with severity.

**Integrations**

| Direction | Counterpart | Type | What crosses |
|---|---|---|---|
| ← | `review` | contract port + mediator | draft + media + categoryPath in, `findings[]` out |
| ← | `offer` | contract port + mediator | same port, consulted while editing (14) |
| → | `tools` | dependency | configuration reader |

**Actors:** none directly (ContentAuthor sees its output in 14).

**Components:** *Policy* `QualityPolicy` (08). Deliberately **no** aggregate: a calculation with configuration only.

## Subdomain: reads

**Responsibilities:** serves every `GET` of 12 with denormalized rows — the draft screen, the pending-reviews list, the pricing view.
Also the single owner of the visibility rule (11), because "is it visible today" changes with the clock and must be answered where the
rows live.

**Core capabilities:** offer draft read model · pending reviews read model · pricing read model · visibility evaluation.

**Integrations**

| Direction | Counterpart | Type | What crosses |
|---|---|---|---|
| ← | `offer` | event (snapshot) | `Offer` — state, availability date, the draft in builder form and its media |
| ← | `review` | event (snapshot) | `Review` — the state, the requester, the frozen candidate's title and findings |
| ← | `pricing` | event | `PricingChanged` — the whole price series and every discount |
| → | `quality` | contract port + mediator | `DraftFindings` — the findings 14 renders |
| → | `pricing` | contract port + mediator | `EffectivePrices` — 10's answer for the date 16 previews |
| → | `tools` | dependency | `Clock` (visibility), clock for waiting time in 15 |

The two producer edges carry a **snapshot**, not the domain events the graph of a pure event model would name: 02 and 04 publish their
facts with identifiers only (`OfferDraftUpdated` is `{productId}`), while a projection copies what it is handed and asks nothing back
(`integration.payload-complete`). The snapshot is published inside the same transaction as the write, so 17 RULE-5's read-your-writes
holds for both.

**Actors:** read side of all four roles (12 matrix).

**Components:** *Read model* `OfferDraftReads`, `PendingReviewsReads`, `PricingReads`; *Policy* `OfferVisibility` (11).

## Event design strategy

| Kind | Events | Scope | Why |
|---|---|---|---|
| **Domain event** | `OfferDraftUpdated`, `PricingChanged`, `ReviewRequested` | in-process, inside catalog | consequences of a single business action, too granular for another service |
| **Pivotal event** | `ReviewAccepted` | in-process, drives publication | the moment responsibility transfers from authoring to the shop |
| **Snapshot event** (event-carried state transfer) | `OfferPublished`, `PricesChanged`, `ProductRemoved` | Kafka, to Shopping | the shop must render an offer without calling back; aligns with 13 R1 |

## Rules that follow from the cut

- **RULE-1** only `mediators/` may name two contexts; every other edge is an event or a port (`context-boundaries.md`).
- **RULE-2** `quality` and `reads` own no aggregate and never write another context's tables.
- **RULE-3** `review` never mutates the offer directly — it asks through `OfferReview` to enter review and through `OfferPublication` to freeze the version, so the offer stays the only writer of its own state (02 RULE-1, D49).
- **RULE-4** `pricing` learns that an offer exists from the published **`Offer` snapshot**, never by querying `offer` (RULE-1 consequence,
  D44): `OfferVersionFrozen` stays inside `offer`'s package-private `OfferEvent`, so it is not a cross-context wire.
- **RULE-5** the draft screen's read-your-writes is guaranteed by the in-process listener: `OfferDraftUpdated` is handled inside the
  producer's transaction, so the projection cannot lag behind a `PATCH` reply (`integration.listener-joins-transaction`).
- **RULE-6** visibility (11) is computed only in `reads`; `offer` carries `availabilityStart` and state, nothing else.

## Risks and assumptions

- **R4 → D34 (resolved)** `review` calls `offer` through a mediator inside **one** transaction: acceptance, the `publishedOfferVersion`
  record and the freeze commit or roll back together, so an accepted review always has its version. The retry is safe because the
  publication is idempotent per `reviewId` (09 R6). No compensating action, no saga, no second write path.
- **R5** the visibility rule must stay in one context (RULE-6); duplicating it in `offer`'s query path would drift.
- **R6 (open)** the ask has **no counterpart**: `OfferReview` makes the offer enter review, and only a successful freeze clears
  `reviewOpen` (`OfferEditor.freeze`). A rejection or a withdrawal therefore leaves the offer believing a review is still open — a
  `published` one refuses a revert with `REVIEW_OPEN`, a `draft` one stays `pendingReview` although 04 RULE-5 returns the product to
  draft. Closing it needs the offer's own command ("the review ended") and a second review → offer port; until then the next
  acceptance is the only way out, and the round trip is not symmetric.
- **A4** `quality` is a separate context rather than a policy inside `offer`, because it carries per-category configuration and two
  consumers (14 and 04) — worth the port.
- **A5** `reads` serves all three views from one context instead of one per view: they share the same event sources and the same deployable;
  splitting later is a package move, not a boundary change.

## What this does not change

The external surface stays exactly as specified: 12 (HTTP), 13 (Kafka events), 14/15/16 (views), and the four business concepts of 01–04.
This element only fixes who inside the service owns what.
