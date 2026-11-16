# 15 — PendingReviewsView

Element 15 · type: UI view · actor: **ContentReviewer** · accepted (generated with defaults).

The board's `pending reviews` read model plus the review screen. Findings come from 08, the decision from 09.

## Screen 1 — pending list

```yaml
view: PendingReviews
rows:                       # one row per open review, oldest first
  - reviewId: RV-12
    productId: PRD-75423
    title: "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423"
    requestedBy: author.nowak
    requestedAt: "2025-06-14T09:10:00+02:00"
    waitingFor: "1 d 0 h"
    findingsCount: 0
  - reviewId: RV-13
    productId: PRD-75375
    title: "LEGO Star Wars Sokół Millennium 75375 + LEGO Brelok na dzień Ojca"
    requestedBy: author.zielinski
    requestedAt: "2025-06-14T16:40:00+02:00"
    waitingFor: "16 h 30 m"
    findingsCount: 2
emptyState: "Brak ofert oczekujących na przegląd"
```

## Screen 2 — review screen

```yaml
view: ReviewDetail(reviewId)
header: { title, productId, offerVersionCandidate: "nowa wersja", reviewer: reviewer.kowalska }
preview:                    # the frozen candidate, read-only
  description: { … }
  media: [ {id: M-01, position: 1, primary: true}, … ]
  availabilityStart: 2026-07-01
findings:                   # 08 output, recomputed never — the value frozen at request
  - { rule: QP-5, field: media.primary, finding: "brak zdjęcia głównego", severity: blocking }
  - { rule: QP-6, field: description.blocks, finding: "opis bez akapitu", severity: blocking }
authorOverride:            # recorded when the author pushed the review despite findings (04 RULE-8)
  present: false
  actor: null
  findings: []
actions:
  - { name: accept,  enabledWhen: findings empty or override confirmed, effect: 09 then freeze }
  - { name: reject,  requires: reason, effect: "stan rejected, powrót do draftu" }
  - { name: withdraw, visibleOnlyFor: requester }
comparison: "diff wobec wersji opublikowanej"   # optional panel, side by side
```

## Rules

- **RULE-1** the review screen renders the frozen candidate, never the live draft (04 RULE-3).
- **RULE-2** findings are displayed as frozen at request; they are not recomputed while the screen is open.
- **RULE-3** `accept` is disabled while any **blocking** finding exists; there is no reviewer override (F15, 09 R2). A candidate pushed with an author override therefore shows its findings and can only be rejected or fixed.
- **RULE-4** a reviewer never sees their own request in the list (04 RULE-2) — the row is filtered, not merely disabled.
- **RULE-5** `reject` requires a non-empty reason (04 RULE-5).
- **RULE-6** the list refreshes on `ReviewRequested` and drops rows on `ReviewAccepted`, `ReviewRejected`, `ReviewWithdrawn`.

## Parameter and value-object origins

| Value object | Values | Origin | Source |
|---|---|---|---|
| PendingReviewRow | reviewId, title, requestedBy, requestedAt, findingsCount | Business concept | 04 ReviewProcess + 08 QualityPolicy |
| CandidatePreview | description, media, availabilityStart | Business concept | 04 candidate |
| OverrideSwitch | visible, requires | Configuration | config key `catalog.publish.override.enabled` |

## Variants and configurations

| Variant | Selected by | Behaviour | Configuration source |
|---|---|---|---|
| default | no match | findings + override + diff panel | — |
| no-override | `catalog.publish.override.enabled = false` | override block hidden, `accept` requires empty findings | config key |
| no-diff | `catalog.review.diff = false` | comparison panel hidden | config key |

## Scenarios

| Scenario | given | expected |
|---|---|---|
| S1 | two open reviews, different authors | both listed, oldest first |
| S2 | reviewer is the requester | own request absent from the list |
| S3 | findings 2, override disabled | `accept` disabled, override block hidden |
| S4 | findings 2, override enabled, not confirmed | `accept` disabled |
| S5 | findings 2, override confirmed | `accept` enabled, override recorded on accept |
| S6 | reject without reason | action refused with validation message |
| S7 | review accepted by another reviewer in the meantime | row gone on refresh, action refused |

## Domain events

Consumes `ReviewRequested`, `ReviewAccepted`, `ReviewRejected`, `ReviewWithdrawn`, `PublishBlocked`. Emits nothing.
