# 09 — PublishDecision

Element 09 · type: automated decision · accepted (generated with defaults).

Decides whether an accepted review freezes a new offer version.

## Logic definition

```text
Decision: Publish(reviewState, blockingFindings, alreadyPublished, offerState)
| Rule | reviewState | blockingFindings | alreadyPublished | offerState                    | outcome                       |
|------|-------------|------------------|------------------|-------------------------------|-------------------------------|
| R1   | accepted    | empty            | no               | draft, pendingReview          | PUBLISH                       |
| R2   | accepted    | non-empty        | no               | draft, pendingReview          | BLOCK (reason: findings)      |
| R3   | requested   | any              | no               | draft, pendingReview          | BLOCK (reason: not accepted)  |
| R4   | rejected    | any              | no               | any                           | BLOCK (reason: rejected)      |
| R5   | withdrawn   | any              | no               | any                           | BLOCK (reason: withdrawn)     |
| R6   | accepted    | any              | yes              | any                           | NO-OP (idempotent replay)     |
| R7   | accepted    | any              | no               | removed                       | BLOCK (reason: offer removed) |
```

```text
blockingFindings = findings from 08 with severity = blocking     # warnings never block (08 QP-8, QP-12)
```

There is **no override here** (F15, transcript seg. 33: "for sure we want to ensure that quality gate do not publish … if there is any
issue"). The only override in the flow sits at the review *request* — the author may push a review with findings, and that is recorded on
04's candidate. Publication itself cannot be forced.

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
| default | no match | R1–R9 | — |
| no-override | config `catalog.publish.override.enabled = false` | R2 and R4 collapse into R3 | config key `catalog.publish.override.enabled` |

The `no-override` variant exists so an organisation can forbid the override without a code change.

## Executable scenarios

| Scenario | reviewState | findings | override | alreadyPublished | offerState | expected outcome |
|---|---|---|---|---|---|---|
| S1 | accepted | empty | null | no | published | PUBLISH |
| S2 | accepted | 2 findings | valid | no | published | PUBLISH + override recorded |
| S3 | accepted | 2 findings | null | no | published | BLOCK (findings) |
| S4 | accepted | 2 findings | actor ≠ reviewer | no | published | BLOCK (invalid override) |
| S5 | requested | empty | null | no | draft | BLOCK (not accepted) |
| S6 | rejected | 1 finding | null | no | draft | BLOCK (rejected) |
| S7 | withdrawn | empty | null | no | draft | BLOCK (withdrawn) |
| S8 | accepted | empty | null | yes | published | NO-OP |
| S9 | accepted | empty | null | no | removed | BLOCK (offer removed) |
| S10 | accepted | 2 findings | valid | no | published | BLOCK when `override.enabled = false` |

## Domain events

```yaml
- OfferVersionFrozen: { productId: PRD-75423, offerVersion: 2, descriptionHash: "sha256:9f2c…", reviewId: RV-12 }
- PublishBlocked:     { productId: PRD-75423, reviewId: RV-12, reason: findings }
```
