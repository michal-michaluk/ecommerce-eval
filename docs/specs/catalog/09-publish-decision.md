# 09 — PublishDecision

Element 09 · type: automated decision · accepted (generated with defaults).

Decides whether an accepted review freezes a new offer version.

## Logic definition

```text
Decision: Publish(reviewState, blockingFindings, alreadyPublished(reviewId), offerState)
| Rule | reviewState | blockingFindings | alreadyPublished | offerState           | outcome                       |
|------|-------------|------------------|------------------|----------------------|-------------------------------|
| R1   | accepted    | empty            | no               | draft, pendingReview          | PUBLISH                       |
| R2   | accepted    | non-empty        | no               | draft, pendingReview          | BLOCK (reason: findings)      |
| R3   | requested   | any              | no               | draft, pendingReview          | BLOCK (reason: not accepted)  |
| R4   | rejected    | any              | no               | any                           | BLOCK (reason: rejected)      |
| R5   | withdrawn   | any              | no               | any                           | BLOCK (reason: withdrawn)     |
| R6   | accepted    | any              | yes              | any                           | NO-OP (idempotent replay)     |
| R7   | accepted    | any              | no               | removed              | BLOCK (reason: offer removed) |
```

`alreadyPublished` is per `reviewId`, never per product: replaying the acceptance of the same review is a no-op (D15), a *new* review
after a successful one publishes the next offer version.

```text
blockingFindings = findings from 08 with severity = blocking     # warnings never block (08 QP-8, QP-12)
```

There is **no override here** (F15, transcript seg. 33: "for sure we want to ensure that quality gate do not publish … if there is any
issue"). The only override in the flow sits at the review *request* — the author may push a review with findings, and that is recorded on
04's candidate. Publication itself cannot be forced: a candidate that carries an author override is still **BLOCKed by R2**, so the
reviewer can only reject it or ask for a fix (15 RULE-3, D27).

## Parameter and value-object origins

| Value object | Values | Origin | Source |
|---|---|---|---|
| ReviewOutcome | reviewState, reviewer, reviewId | Business concept | 04 ReviewProcess |
| Findings | rule, field, finding | Business concept | 08 QualityPolicy on the frozen candidate |
| PublishOverride | actor, recordedAt, findings | Business concept | 04 ReviewProcess (`override`) |
| OfferState | state | Business concept | 02 Offer |

No external integration participates: the decision is pure over in-context state.

## Variants and configurations

| Variant | Selected by | Rules | Configuration source |
|---|---|---|---|
| default | no match | R1–R7 | — |
| offer-removed-wins | always | R7 outranks R6 when the offer is already removed | not configurable: a terminal state outranks a replay |

The `no-override` switch belongs to 04 (it governs whether a review may be requested with findings), not to this decision: acceptance
ignores every override, always. This element therefore has no variant of its own (D27).

## Executable scenarios

| Scenario | reviewState | findings | request override | alreadyPublished | offerState | expected outcome |
|---|---|---|---|---|---|---|
| S1 | accepted | empty | null | no | draft | PUBLISH |
| S2 | accepted | 2 findings | present | no | pendingReview | BLOCK (findings) — an author override never unblocks acceptance |
| S3 | accepted | 2 findings | null | no | pendingReview | BLOCK (findings) |
| S4 | requested | empty | null | no | pendingReview | BLOCK (not accepted) |
| S5 | rejected | 1 finding | null | no | draft | BLOCK (rejected) |
| S6 | withdrawn | empty | null | no | draft | BLOCK (withdrawn) |
| S7 | accepted | empty | null | yes | published | NO-OP |
| S8 | accepted | empty | null | no | removed | BLOCK (offer removed) |
| S9 | accepted | blocking + 1 warning finding | present | no | pendingReview | BLOCK (findings) — warnings never block, blocking ones always do |
| S10 | accepted | empty | stale override | no | draft | PUBLISH — the override is irrelevant to this decision |

## Domain events

```yaml
- OfferVersionFrozen: { productId: PRD-75423, offerVersion: 2, descriptionHash: "sha256:9f2c…", reviewId: RV-12 }
- PublishBlocked:     { productId: PRD-75423, reviewId: RV-12, reason: findings }
```

## Transaction boundary

The decision, the acceptance record and the offer freeze are **one transaction**: `review` writes `accepted`, calls
`OfferPublication` through its mediator inside that same transaction, and `offer` freezes the version. A failure inside the freeze rolls
the acceptance back, so an accepted review and an offer version can never disagree; the retry is safe because R6 makes the replay a
no-op (17 R4, D34).
