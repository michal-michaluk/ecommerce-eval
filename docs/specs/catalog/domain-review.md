# catalog — domain review (HALT before persistence / integration / REST)

status: approved — operator approved 2026-11-16; decisions taken: D37 (a review never hides a live offer), D38 (the freeze takes the reviewed candidate), D40 and D41 as implemented. OQ-6 answered by verification (ArchUnit's belongToAnyOf covers nested types, so `quality` may read `ProductDescription.Draft`); OQ-3 and OQ-7 corrected in the spec (13 now carries identifiers, 01 RULE-1 now says 'editable until the next publication'); OQ-1, OQ-2, OQ-4, OQ-5 carried to the plan's open topics. See §10.

Date 2026-11-16 · branch `feat/catalog-domain-model` · code at `d27d505` (01), `cfd3322` (02), `50e0cf0` (03, 10); spec re-read at `d2ad9f9`.
**The spec moved under this review:** `9fc34da`/`76f39db`/`d2ad9f9` decided D37 and D38 and marked D40/D41 approved-as-implemented, so §5
reports *decided-but-divergent* instead of open options.
Gates: `./gradlew cleanTest test --tests 'shop.catalog.offer.*' --tests 'shop.catalog.pricing.*'` → BUILD SUCCESSFUL, 154 tests, 0 failures ·
`sqt check .` → `SUCCESS`, 0 errors, 5 warnings (`event.polymorphic-json` on `OfferEvent` + `PricingEvent`, `event.past-tense` on
`OfferVersionFrozen`, `vo.builder-when-wide` on `OfferVersion` + `Media`).

Paths: `offer/`, `pricing/` = `services/catalog/src/main/java/shop/catalog/…` · `t-offer/`, `t-pricing/` =
`services/catalog/src/test/java/shop/catalog/…` · `spec/` = `docs/specs/catalog/` · `sqt` file = `services/catalog/src/main/java/.sqt.yaml`.

## 1. Scope of this review

- **offer (01, 02)** — `OfferEditor` (package-private aggregate, `offer/OfferEditor.java:31`), `Offer` (public snapshot, `offer/Offer.java:13`),
  `OfferVersion` (frozen version, `offer/OfferVersion.java:15`), and the `ProductDescription` value object with `Draft`, `Identifiers`, `Brand`,
  `Condition`, `Parameter.Value` (8 carriers), `DescriptionBlock` (4 carriers) — all in `offer/ProductDescription.java`. Five events
  (`offer/OfferEvent.java:10…25`); ports `offer/OfferRepository.java:9`, `offer/OfferRegistry.java:7`.
- **pricing (03, 10)** — `PricingModelEditor` (`pricing/PricingModelEditor.java:24`), `PricingModel` (`pricing/PricingModel.java:10`), `Money`,
  `Validity`, `PriceEntry`, `Discount` (+ `Kind`), `PriceId`, `DiscountId`, `PriceChange`, `DiscountChange`, `ActivePrice`,
  `ActivePriceCalculation` (+ `DiscountPolicy`), `ActivationDue`. Six events (`pricing/PricingEvent.java:11…33`); port
  `pricing/PricingRepository.java:6`. 26 main source files in total, 2 of them `package-info.java`.
- **Absent on purpose:** no persistence, no Spring/JPA/Kafka type (grep over `offer/` and `pricing/` empty), no HTTP resource, no `@Scheduled`
  poll, no `quality`/`review`/`reads` domain and no mediator — those packages hold only `package-info.java`.

## 2. Invariant → rule map

### 01 ProductDescription

| Rule | Enforced by | Test |
| --- | --- | --- |
| RULE-1 title 1–200 chars, **editable until publication** | `offer/ProductDescription.java:62-68` strips then validates; the publication half is **not** enforced — `offer/OfferEditor.java:61-70` refuses only `removed` | `t-offer/ProductDescriptionTest.java:63-83`; the gap is pinned as intended by `t-offer/OfferEditorPublicationTest.java:71-78` |
| RULE-2 unique per product; `eanGtin` 13 digits + check digit | value side `offer/ProductDescription.java:70-78`, `:119-139`; population side is 02 RULE-11 | `t-offer/ProductDescriptionIdentifiersTest.java:23-58` |
| RULE-3 a value is immutable, a change yields a new value | record + copied lists `offer/ProductDescription.java:37-60`, `:291-295`; `Draft.merge:302-312` | `t-offer/ProductDescriptionTest.java:118-127`, `t-offer/OfferEditorDraftTest.java:51-60` |
| RULE-4 draft content never leaves the context | structural only: `Draft` is public (`offer/ProductDescription.java:281`) but is **not** in `sharedKernelExposed` (`t-offer/ArchitectureOfOfferTest.java:24`), so `modelIsolation` (`t-offer/ArchitectureDescription.java:90-98`, used at `t-offer/ArchitectureOfOfferTest.java:48-49`) bars other packages; no `Draft` in any event (`offer/OfferEvent.java:12-25`) | no test asserts absence from the wire — `t-offer/ProductDescriptionDraftTest.java:22-32` only asserts editability |
| RULE-5 `code` unique, `valueType` closed set, `label` is display text | `offer/ProductDescription.java:94-106`, `:158-195` (`valueType()` derived from the sealed carrier) | `t-offer/ProductDescriptionParameterTest.java` (5 tests) |
| RULE-6 ≥1 paragraph; `image` blocks reference media owned by 02 | paragraph `offer/ProductDescription.java:108-116`; the media-reference clause is enforced **nowhere** — `DescriptionBlock.Image` only null-checks the id (`:268-272`) and its owner is 08 QP-7 (`spec/08-quality-policy.md:27`), which has no code | paragraph `t-offer/ProductDescriptionTest.java:90-103`; the **opposite** is pinned for media `t-offer/ProductDescriptionTest.java:85-89` |
| RULE-7 equality by value over every component | record components incl. `Identifiers`, `Brand`, `Condition`, lists (`offer/ProductDescription.java:26-33`) | `t-offer/ProductDescriptionTest.java:105-116` |
| RULE-8 population uniqueness is not this object's concern | no counterpart here by design; 02 RULE-11 (port) owns it | see 02 RULE-11 |
| RULE-9 `brand`, `condition`, `categoryPath` are value components | `offer/ProductDescription.java:29-31`, `:141-155` | `t-offer/ProductDescriptionTest.java:53-57`, `t-offer/ProductDescriptionIdentifiersTest.java:63-88` |

### 02 Offer

| Rule | Enforced by | Test |
| --- | --- | --- |
| RULE-1 ≥1 image, exactly one primary | `offer/OfferEditor.java:205-213`, invoked at `:96` and `:182` | `t-offer/OfferEditorLifecycleTest.java:51-82`; freeze side `t-offer/OfferEditorPublicationTest.java:101-108` |
| RULE-2 positions contiguous from 1, ≤1 video | `offer/OfferEditor.java:215-222`, `:224-229` | `t-offer/OfferEditorLifecycleTest.java:84-98` |
| RULE-3 `availabilityStart` may be in the future | `offer/OfferEditor.java:84-91`, `:193-197` | `t-offer/OfferEditorDraftTest.java:104-112`, `t-offer/OfferEditorPublicationTest.java:110-117`. The "hidden until the date" half is 11 `OfferVisibility` in `reads` — **no code** |
| RULE-4 freeze embeds by value, never re-reads a draft | first half `offer/OfferEditor.java:110-118` (`draft.toDefinition()` at `:113`), `offer/OfferVersion.java:15-33`; **second half (freeze the candidate 04 captured, `spec/02-offer.md:59-61`) is not implemented** — `publish` takes the live draft | `t-offer/OfferEditorPublicationTest.java:30-38`, `:71-78` |
| RULE-5 revert copies older content, number never reused | `offer/OfferEditor.java:127-135`, `offer/OfferVersion.java:36-38`, `:158-160` | `t-offer/OfferEditorPublicationTest.java:133-172`, `:183-191` |
| RULE-6 `removed` is terminal | `offer/OfferEditor.java:137-142`, `:162-166`, `:168-172` | `t-offer/OfferEditorLifecycleTest.java:134-164` (4 tests), `:166-200` (5 tests) |
| RULE-7 editor holds the draft in builder form | `offer/OfferEditor.java:39`; `offer/ProductDescription.java:281-317` | `t-offer/OfferEditorDraftTest.java:85-93`, `t-offer/ProductDescriptionDraftTest.java:22-32` |
| RULE-8 quality violations legal in the draft, **only the freeze enforces** | true of the value invariants (`offer/OfferEditor.java:61-70` runs none), **not** of the population and media rules: `:65` refuses a taken identifier on entry, `:96` enforces RULE-1/RULE-2 at the review request | `t-offer/OfferEditorDraftTest.java:85-93`, `t-offer/OfferEditorPublicationTest.java:92-99` |
| RULE-9 the draft is immutable, `merge` returns a new one | `offer/ProductDescription.java:281` (`@Builder(toBuilder = true)`), `:302-312` | `t-offer/OfferEditorDraftTest.java:51-60`, `t-offer/ProductDescriptionDraftTest.java:53`; gates `aggregate.no-setters` (`sqt:686`) and `vo.no-setters` (`sqt:807`) — **`model.no-setters` / `model.no-lombok-data` quoted by `spec/02-offer.md:64` do not exist**; the 51 rule ids carry no Lombok-`@Data` rule |
| RULE-10 `Draft.toDefinition()` is the only producer and throws | `offer/ProductDescription.java:314-316`; invariants in the compact constructor `:37-45` → `:62-116` | `t-offer/ProductDescriptionTest.java:63-76`, `:90-103`, `t-offer/ProductDescriptionDraftTest.java:29-47` |
| RULE-11 uniqueness across offers | **PORT, not the model:** `offer/OfferRegistry.java:7-12`; asked at `offer/OfferEditor.java:51`, `:65`, `:184`, refused at `:231-248` | `t-offer/OfferEditorDraftTest.java:128-142`, `:155-175` |

### 03 PricingModel

| Rule | Enforced by | Test |
| --- | --- | --- |
| RULE-1 `amountMinor:int` + ISO currency, scale from the currency | `pricing/Money.java:10-28` | `t-pricing/PricingValuesTest.java:17-30` |
| RULE-2 half-open, predecessor closed, history is the entries | `pricing/Validity.java:31-33`; `pricing/PriceEntry.java:51-57`; `pricing/PricingModelEditor.java:87-103` | `t-pricing/PricingValuesTest.java:33-42`, `t-pricing/PricingModelEditorTest.java:35-43`, `:235-244` |
| RULE-3 at most one open price per `(offer, currency)` | `pricing/PricingModelEditor.java:195-218` | `t-pricing/PricingModelEditorTest.java:74-82` |
| RULE-4 a scheduled entry is silent outside the context | `pricing/PricingModelEditor.java:76-80`, `:113-140` | `t-pricing/PricingModelEditorTest.java:104-115`, `:129-138` |
| RULE-5 basis points `0 < bp ≤ 10000`; fixed matches a priced currency | `pricing/Discount.java:44-52`; `pricing/PricingModelEditor.java:248-257` — **conditional, see §5 D41** | `t-pricing/PricingValuesTest.java:63-68`, `t-pricing/PricingModelEditorTest.java:204-221` |
| RULE-6 a discount never mutates a price entry | `pricing/Discount.java:11`, `:23-25`; resolved at read time `pricing/ActivePriceCalculation.java:44-62` | `t-pricing/ActivePriceCalculationTest.java:35-58`, `:147-153` |
| RULE-7 a future entry is editable; a started one **closes at now** | **partially covered, and not at now:** no edit command exists (ids immutable, `pricing/PricingModelEditor.java:174-178`), so "editing" is append-with-a-new-id, and the predecessor closes at the **successor's `validFrom`** (`closedBefore:97-103`); a `validFrom` before now is refused outright (`:180-186`) | `t-pricing/PricingModelEditorTest.java:45-53` reaches "closes at now" only because the test passes `now` as the new `validFrom` |
| RULE-8 a started entry cannot be deleted | `pricing/PricingModelEditor.java:46-54`, `:188-193`; discounts `:241-246` | `t-pricing/PricingModelEditorTest.java:93-102`, `:161-171` |
| RULE-9 polled, idempotent, catching-up activation | marker `pricing/PriceEntry.java:43-49`, `pricing/Discount.java:27-33`; decision `pricing/ActivationDue.java:17-29`; command `pricing/PricingModelEditor.java:113-140`. The `@Scheduled` poll and `catalog.pricing.activation-interval` are an adapter — **no code** | `t-pricing/ActivationDueTest.java:19-60`, `t-pricing/PricingModelEditorTest.java:117-127`, `:223-233` |

### 10 ActivePriceCalculation

| Element | Enforced by | Test |
| --- | --- | --- |
| Formula, D3 order (fixed, then percentage, floor 0) | `pricing/ActivePriceCalculation.java:71-86` | `t-pricing/ActivePriceCalculationTest.java:28-83` (S1–S7) |
| `undefined` → ABSENT, never `0` | `pricing/ActivePriceCalculation.java:44-55`; `pricing/ActivePrice.java:9-25` | `t-pricing/ActivePriceCalculationTest.java:85-91` (S8) |
| Half-open bounds at exactly `validFrom` / `validUntil` | `pricing/Validity.java:31-45` | `t-pricing/ActivePriceCalculationTest.java:93-107` (S9, S10) |
| Rounding half-up, once, at the end | `pricing/ActivePriceCalculation.java:88-91` | `t-pricing/ActivePriceCalculationTest.java:138-145` |
| Variants `order = percent-first`, `stacking = exclusive` | `pricing/ActivePriceCalculation.java:57-69`, `:74-76`, `:102-126`; the config keys are **not bound** — the policy is a value, wiring is `pricing-application` | `t-pricing/ActivePriceCalculationTest.java:109-136` |
| A fixed discount counts only in its own currency | `pricing/Discount.java:71-74` | `t-pricing/ActivePriceCalculationTest.java:147-161` |

## 3. Aggregate command inventory

| `OfferEditor` command | Guards | Event appended |
| --- | --- | --- |
| `draftAnOffer` `offer/OfferEditor.java:46` | `COMMAND_VALUE_MISSING` `:47-48`; `PRODUCT_NUMBER_TAKEN`/`EAN_GTIN_TAKEN` `:231-248` | `OfferDraftUpdated` `:52` |
| `updateDescription` `:61` | `COMMAND_VALUE_MISSING` `:62`; `OFFER_REMOVED` `:63`; taken identifiers `:65` | `OfferDraftUpdated` `:68` — nothing when the merge changes nothing `:66` |
| `updateMedia` `:73` | `COMMAND_VALUE_MISSING` `:74`; `OFFER_REMOVED` `:75` | `OfferDraftUpdated` `:79` — nothing when unchanged `:77` |
| `scheduleAvailability` `:84` | `COMMAND_VALUE_MISSING` `:85`; `OFFER_REMOVED` `:86` | `OfferDraftUpdated` `:89` — nothing when unchanged `:87` |
| `requestReview` `:94` | `OFFER_REMOVED` `:95`; the four media errors `:96` | `OfferReviewRequested` `:99` — nothing when already `PENDING_REVIEW` `:97` |
| `publish` `:110` | `COMMAND_VALUE_MISSING` ×2 `:111`; `OFFER_REMOVED`, the four media errors, `AVAILABILITY_START_MISSING`, taken identifiers `:180-185`; then every value invariant through `toDefinition()` `:113` (`OfferException.java:11-22`) | `OfferVersionFrozen` `:117` |
| `revert` `:127` | `COMMAND_VALUE_MISSING` ×2 `:128`; `OFFER_VERSION_MISSING` `:155`; `OFFER_REMOVED`, `REVIEW_OPEN`, taken identifiers `:187-191` | `OfferVersionReverted` `:134` |
| `remove` `:138` | `REMOVAL_NEEDS_PUBLICATION` `:170` | `OfferRemoved` `:141` |

| `PricingModelEditor` command | Guards | Event appended |
| --- | --- | --- |
| `newPricingModelFor` `pricing/PricingModelEditor.java:32` | `NullPointerException` `:33` | none — 03 lists no "opened" fact `:31` |
| `schedulePrice` `:37` | NPE `:38-39`; `PRICE_ID_DUPLICATED` `:40`/`:174-178`; `PRICE_VALID_FROM_IN_PAST` `:89`/`:180-186`; `PRICE_RANGE_OVERLAPS` `:93`/`:195-218` | `PriceScheduled` `:43` |
| `removePrice` `:46` | NPE `:47-48`; `PRICE_NOT_FOUND` `:167-172`; `PRICE_ALREADY_STARTED` `:50`/`:188-193` | `PriceRemoved` `:53` |
| `scheduleDiscount` `:56` | NPE `:57-58`; `DISCOUNT_ID_DUPLICATED` `:59`/`:220-224`; `DISCOUNT_VALID_FROM_IN_PAST` `:60`/`:226-232`; `DISCOUNT_CURRENCY_NOT_PRICED` `:61`/`:248-257` | `DiscountScheduled` `:64` |
| `removeDiscount` `:67` | NPE `:68-69`; `DISCOUNT_NOT_FOUND` `:234-239`; `DISCOUNT_ALREADY_STARTED` `:70`/`:241-246` | `DiscountRemoved` `:73` |
| `activateDueEntries` `:76` | NPE `:77`; nothing else — no due entry is a legal no-op `:115-117`, `:129-131` | one `PriceActivated` per due entry `:123` and one `DiscountActivated` per due discount `:137-138` — the **only** command that may append more than one event |

Queries, no event: `toOffer` `offer/OfferEditor.java:145-148`, `version(int)` `:151-156`, `toPricingModel` `pricing/PricingModelEditor.java:82-85`.

## 4. Executed scenarios and suite sizes

| Scenario | Test (`t-pricing/ActivePriceCalculationTest.java`) | Observed |
| --- | --- | --- |
| S1 no discount | `:28-33` | `30989` |
| S2 fixed 2000 | `:35-41` | `28989` |
| S3 1000 bp | `:43-49` | `27890` |
| S4 fixed 2000 + 1000 bp | `:51-58` | `26090` |
| S5 fixed 40000 (floor) | `:60-66` | `0` |
| S6 two 6000 bp stacked | `:68-75` | `0` (sum capped at 10000) |
| S7 before the discount range | `:77-83` | `30989` |
| S8 before any price entry | `:85-91` | `ABSENT` |
| S9 exactly at `validFrom` | `:93-99` | applies, `30989` |
| S10 exactly at `validUntil` | `:101-107` | `ABSENT` |

Plus the two variants and the rounding/currency cases: `:109-121` (percent-first → `25890`), `:123-136` (exclusive → `25989` vs `23390`),
`:138-145` (half-up once), `:147-161` (currency scope).

Counts from `build/test-results/test`: **offer 61** (`ArchitectureOfOfferTest` 6, `OfferEditorDraftTest` 15, `OfferEditorLifecycleTest` 21,
`OfferEditorPublicationTest` 19) · **description 39** (`ProductDescriptionTest` 14, `ProductDescriptionDraftTest` 6,
`ProductDescriptionIdentifiersTest` 14, `ProductDescriptionParameterTest` 5) · **pricing 54** (`PricingModelEditorTest` 20,
`ActivePriceCalculationTest` 15, `PricingValuesTest` 9, `ActivationDueTest` 4, `ArchitectureOfPricingTest` 6). Filter total **154**.

## 5. The four D-decisions: what the spec now says, what the code does

### D37 — a review never hides a live offer (`spec/spec.md:592`, `spec/02-offer.md:50-52`, `spec/09-publish-decision.md:13-14`, `spec/11-offer-visibility.md:25-26`)

- **Decided:** a published offer **stays `published`** while its candidate is reviewed; `pendingReview` only describes an offer not yet published; 09 R1/R2 now accept `published`.
- **Code diverges:** `requestReview()` writes `PENDING_REVIEW` for any state but `removed` (`offer/OfferEditor.java:94-101`, write at `:98`), pinned by `t-offer/OfferEditorLifecycleTest.java:125-132`. Re-reviewing a live offer therefore clears its published state today.
- **Closing it changes the aggregate** (contrary to what an earlier draft of this pack claimed): `requestReview` must keep `PUBLISHED` and leave the request to `review`; the RULE-1 media gate at `:96` must still run on the request path. `Offer.State.PENDING_REVIEW` then only describes a pre-first-publication offer.
- **Recommendation:** guard the state write, keep the media gate, let 04 record the request. Deferred to `review-application`, which is where the request is first reachable.

### D38 — the freeze takes the candidate, not the live draft (`spec/spec.md:604`, `spec/02-offer.md:60-62`, `spec/17-contexts-inside.md:92`)

- **Decided:** publication freezes the candidate 04 captured at request time, through `OfferPublication` (`spec/spec.md:604`, `spec/02-offer.md:59-61`, `spec/17-contexts-inside.md:92`).
- **Code diverges:** `publish` freezes `draft.toDefinition()` (`offer/OfferEditor.java:113`); neither `OfferPublication` nor `OfferDraftSnapshot` exists (`services/catalog/src/main/java/shop/catalog/mediators/` holds only `package-info.java`).
- **Closing it:** `publish(candidate, at, by, registry)`; `ensureIdentifiersAreFree` (`:184`) must read the candidate; media and `availabilityStart` are frozen with it through the already-existing `OfferVersion` shape (`offer/OfferVersion.java:15-33`).
- **Recommendation:** implement as decided in `offer-application`; no operator action left.

### D40 — removing a scheduled price reopens its predecessor (`spec/spec.md:595-597`, marked *approved as implemented*)

- **Code matches the decision:** `pricesWithout` / `reopenedAfterRemoval` restore the predecessor's `validUntil` (`pricing/PricingModelEditor.java:142-153`), pinned by `t-pricing/PricingModelEditorTest.java:173-183`.
- **Two facts the decision does not state:** the withdrawn row is **deleted** (`:142-147`), so its id becomes free again (`ensurePriceIsNew:174-178` accepts a reuse) and `PriceRemoved` is the *only* trace; and the predecessor is reopened to the withdrawn entry's `validUntil`, which may be `null` (`reopenedUntil:55-57`).
- **Recommendation:** keep the decision, but add to 03 that a removed entry's id stays retired, or the audit argument in the decision no longer holds.

### D41 — a fixed discount in a currency the offer does not price (`spec/spec.md:598-600`, marked *approved as implemented*)

- **The approval rests on an inaccurate description — mine included.** The refusal is conditional:
  `if (!prices.isEmpty() && !pricedCurrencyExists) throw DISCOUNT_CURRENCY_NOT_PRICED` (`pricing/PricingModelEditor.java:248-257`, condition at `:253`).
- **What that means:** with ≥ 1 price in the model the fixed discount is refused (`t-pricing/PricingModelEditorTest.java:204-211`, which starts from `pricingModelWithOneOpenPrice`); **with an empty model any fixed discount is accepted** — exactly the "prepare a EUR discount before the EUR price exists" case the decision says is refused, and no test covers it. A percentage discount always passes (`pricing/Discount.java:55-57`).
- **Options:** (a) approve the empty-model acceptance as intended and fix the sentence in 03 RULE-5 and 12; (b) close the hole by dropping `!prices.isEmpty()`; (c) accept-and-ignore everywhere, as the decision's alternative.
- **Recommendation:** (b) — an unpriced currency is a configuration error the moment the price set is known, and the current condition silently inverts the rule on the one path that matters for the "prepare ahead" workflow.

### Deviations already recorded that touch the domain

| Id | Effect in this code | Recorded at |
| --- | --- | --- |
| D27 | acceptance is never overridable; no override field exists in the model | `spec/spec.md:70`, `:576` |
| D30 | `brand`, `condition`, `categoryPath` are `ProductDescription` components | `spec/spec.md:73`; `offer/ProductDescription.java:29-31`, `:141-155` |
| D31 | one identifier: `offerId` ≡ `productId`, the model names it `offerId` | `spec/spec.md:74`, `spec/02-offer.md:8`; `offer/OfferEditor.java:35` |
| D36 | `OfferReviewRequested` exists because `draft → pendingReview` writes state and must emit | `spec/02-offer.md:85`, `:92-94`; `offer/OfferEvent.java:15-16`, `offer/OfferEditor.java:99` |
| D39 | `descriptionHash` is computed by the outbound adapter, never in the model — no hashing implementation exists (no `MessageDigest`, no `sha256` in `src/main/java`); the identifier occurs once, in a javadoc line | `spec/02-offer.md:90-94`; `offer/ProductDescription.java:19` |

Caveat when reading that table: `spec/spec.md:583-584` are broken (one row cut mid-cell, the next empty) and `spec/spec.md:587` carries
two further issue/fix pairs inside its last cell. The facts are in the table; its shape is not.

## 6. A whole class of defects, not one instance

`aggregate.no-public-members` (`sqt:630-635`) matches `has.public-member` (`sqt:156-172`), whose node list **already includes
`constructor_declaration`** (`sqt:166`) — a hand-written `public OfferEditor(...)` would be caught. What it cannot see is a member the
compiler generates: the rule reads source AST, and `@AllArgsConstructor` with default access yields a `public` constructor with no source node.

`javap` on the current build (nothing was recompiled for this evidence — the "before" is the un-fixed twin the pricing fix did not revisit):

```
$ javap -classpath build/classes/java/main shop.catalog.offer.OfferEditor
class shop.catalog.offer.OfferEditor {                     <- the class itself is not public
  public shop.catalog.offer.OfferEditor(java.lang.String, java.util.List<...OfferEvent>, ...);
}
$ javap -classpath build/classes/java/main shop.catalog.pricing.PricingModelEditor
class shop.catalog.pricing.PricingModelEditor {
  shop.catalog.pricing.PricingModelEditor(java.lang.String, java.util.List<...PricingEvent>, ...);   <- package-private
}
```

- `offer/OfferEditor.java:30` — `@AllArgsConstructor`, no access → **public** constructor in bytecode; gate green.
- `pricing/PricingModelEditor.java:23` — `@AllArgsConstructor(access = AccessLevel.PACKAGE)` → package-private; gate green too.
- Blast radius today is nil (both classes are package-private, `:31` and `:24`), and it stops being nil the first time an aggregate is public — with the gate still green.

**Closing it durably** (none applied — this node writes no code):

1. A bytecode-level rule: ArchUnit imports compiled classes, so it sees Lombok output. One rule factory in
   `t-offer/ArchitectureDescription.java` plus one `@ArchTest` per context test closes the class for **every** generator.
2. A source-text rule that requires the `access` attribute whenever the annotation is present. Note it cannot be "ban the annotation":
   `aggregate.all-args-constructor` (`sqt:709-720`) *mandates* `@AllArgsConstructor` on every aggregate, so the two rules would contradict.
3. The instance fix, one line: `@AllArgsConstructor(access = AccessLevel.PACKAGE)` on `offer/OfferEditor.java:30` — correct, and it leaves the
   gate blind for the next aggregate.

Recommendation: (1) durable, (3) immediately.

## 7. Invariants the code adds that no element states

| Id | What the code does | Where | Why it needs a call |
| --- | --- | --- | --- |
| I-1 | an open review blocks a revert (`REVIEW_OPEN`) | `offer/OfferEditor.java:174-178`, `:189`; `t-offer/OfferEditorPublicationTest.java:174-181` | 02 RULE-5 is silent, and `spec/14-draft-description-view.md:53` says an open review never blocks editing |
| I-2 | no retroactive `validFrom` (`PRICE_VALID_FROM_IN_PAST`, `DISCOUNT_VALID_FROM_IN_PAST`) | `pricing/PricingModelEditor.java:89`, `:180-186`, `:60`, `:226-232` | 03 has no such rule; it is also what makes RULE-7's "closes at now" unreachable for a change that starts later |
| I-3 | a removed entry's id becomes reusable | `pricing/PricingModelEditor.java:142-147` vs `:174-178` | 03 says ids identify an entry; D40's audit argument is weaker than it looks |
| I-4 | a RULE-3 breach answers instead of failing (`findFirst()`) | `pricing/ActivePriceCalculation.java:51-55`, javadoc `:40-43` | 10 states the formula is "subject to exactly one open price entry per (productId, currency)" |
| I-5 | `Money` refuses negative amounts and non-ISO codes | `pricing/Money.java:14-19` | 03 RULE-1 defines the shape only |
| I-6 | the discount set is kept sorted by `validFrom` | `pricing/PricingModelEditor.java:105-111`; `pricing/PricingModel.java:6-9` | not in 03; 16 renders that order |
| I-7 | `EXCLUSIVE` picks `max(validFrom)`, ties broken by encounter order | `pricing/ActivePriceCalculation.java:64-69` | 10's no-stacking variant defines no tie |
| I-8 | `Offer.version` is the count of frozen versions | `offer/OfferEditor.java:145-148` | 02 never defines that field |
| I-9 | the title is stripped, then validated 1–200 | `offer/ProductDescription.java:63-67`; `t-offer/ProductDescriptionTest.java:78-83` | 01 RULE-1 says 1–200 chars |
| I-10 | version numbers start at 1 (`OFFER_VERSION_NUMBER_INVALID`) | `offer/OfferVersion.java:25-27`, `offer/OfferEditor.java:33`, `:158-160` | 02 shows `offerVersion: 1` but states no rule |
| I-11 | `removedAt` has no counterpart | `offer/Offer.java:13`, `offer/OfferEvent.java:24-25` vs `spec/02-offer.md:44` | every other 02 fact carries its timestamp; 13's `ProductRemoved` needs `removedAt` |
| I-12 | identical content emits nothing | `offer/OfferEditor.java:66`, `:77`, `:87`, `:97` | 02 does not say it; it is a state-machine decision, not an implementation detail |

## 8. Open questions the spec still does not answer

| Id | Question | Where the code guessed |
| --- | --- | --- |
| OQ-1 | `Draft.merge` treats `null` as "unchanged", so a scalar component (`title`, `identifiers`, `brand`, `condition`) can never be cleared; list components can, with an empty list. 12's PATCH says an absent component is absent, not "null means keep". | `offer/ProductDescription.java:302-312` (`:304-307` scalars, `:308-310` lists) |
| OQ-2 | `Condition` constants are upper-case while 13 shows `condition: new`; nothing maps them, and the domain deliberately carries no Jackson type. | `offer/ProductDescription.java:151-155` vs `spec/13-shopping-events.md:28` |
| OQ-3 | 13's `OfferPublished` payload carries **no `identifiers` at all** — not `productNumber`, not `eanGtin`, not `setName` — while `ProductDescription` includes them in the value and `descriptionHash` is promised over the value (D39), so a consumer can neither recompute the hash nor dedupe by content. Broader than the "missing `setName`" the plan recorded. | `spec/13-shopping-events.md:21-33` vs `offer/ProductDescription.java:28`, `:119` |
| OQ-4 | 09 R1 admits `offerState = draft`, and the aggregate has no state guard on `publish` — only `removed` is refused, so a second `publish` without a new review is possible; the behaviour is pinned as intended. | `spec/09-publish-decision.md:13`, `offer/OfferEditor.java:110-118`, `:180-185`; `t-offer/OfferEditorLifecycleTest.java:109-116` |
| OQ-5 | `PricingModelEditor` rejects a missing argument with a raw `NullPointerException` while `OfferEditor` raises `COMMAND_VALUE_MISSING`; `PricingException.Error` has no counterpart and `error.domain-throws-context-type` (`sqt:606-619`) only sees a `throw_statement`, never a `Objects.requireNonNull` call. | `pricing/PricingModelEditor.java:33`, `:38-39`, `:47-48`, `:57-58`, `:68-69`, `:77` vs `offer/OfferException.java:23` + `offer/OfferEditor.java:250-254`, `pricing/PricingException.java:10-31` |
| OQ-6 | 01 RULE-4 versus 17's in-process quality policy: `quality` will have to read `ProductDescription.Draft`, but `modelIsolation` bars every package except `shop.catalog.offer` from touching it (`Draft` is not in `sharedKernelExposed`). | `t-offer/ArchitectureDescription.java:90-98`, `t-offer/ArchitectureOfOfferTest.java:24`, `:48-49`; `spec/17-contexts-inside.md` (quality port) |
| OQ-7 | 01 RULE-1's "editable **until publication**" versus the code's "editable forever, a new version carries the change" — the two readings differ and only one can be the rule. | `spec/01-product-description.md` RULE-1 vs `offer/OfferEditor.java:61-70`, `:127-135` |

## 9. How to approve

- Approve: edit `status:` above to `approved` (optionally naming what you decide on D41 and on I-1…I-12); the next run continues with the
  remaining 17 nodes (`quality-domain` … `review-branch`).
- Reject: set `status: rejected` and state the reason — changing a decision sends the plan back through its spec corrections.

## 10. Approval record

- Approved by the operator on 2026-11-16 ("looks nice, my review passed"), with the four D-decisions taken as this document
  recommended; the operator did not answer them individually and asked for the run to continue.
- Corrections applied to the spec at approval: **OQ-3** `OfferPublished` now carries `identifiers` (13 RULE-10); **OQ-7** 01 RULE-1
  restated as "editable until the next publication, each publication freezing a new version"; **OQ-6** closed as a false alarm —
  `belongToAnyOf(ProductDescription.class)` matches nested types (verified empirically in the 01 node), so `quality` reads `Draft`
  and only has to list `ProductDescription` in its own `sharedKernelUsed`.
- Carried into `spec.md` §Open Topics (they need the HTTP/adapters phases, not a domain change): OQ-1 (a scalar draft component
  cannot be cleared through `merge`), OQ-2 (`Condition` upper-case vs the wire's lowercase), OQ-4 (publish from `draft` is legal per
  09 R1), OQ-5 (`PricingModelEditor` raises a raw NPE where `OfferEditor` raises `COMMAND_VALUE_MISSING`).
