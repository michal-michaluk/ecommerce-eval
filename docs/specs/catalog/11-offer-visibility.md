# 11 — OfferVisibility

Element 11 · type: automated decision · accepted (generated with defaults).

Whether a customer may see an offer, given its publication state and its availability start.

## Logic definition

```text
Decision: Visible(offerState, availabilityStart, today)
| Rule | offerState   | availabilityStart      | outcome |
|------|--------------|------------------------|---------|
| R1   | published    | null or <= today       | VISIBLE |
| R2   | published    | > today                | HIDDEN  |
| R3   | pendingReview| any                    | HIDDEN  |
| R4   | draft        | any                    | HIDDEN  |
| R5   | removed      | any                    | HIDDEN |

today = current date in the business timezone, `Europe/Warsaw` (config `catalog.timezone`)
R2 becomes R1 at local midnight of availabilityStart; no event is emitted by the transition
itself — 13 ShoppingEvents already carried `availabilityStart` in `OfferPublished`.
```

## Parameter and value-object origins

| Value object | Values | Origin | Source |
|---|---|---|---|
| OfferState | state, availabilityStart | Business concept | 02 Offer |
| BusinessDate | today | Configuration | config key `catalog.timezone` |

## Variants and configurations

| Variant | Selected by | Rules | Configuration source |
|---|---|---|---|
| default | no match | R1–R5 (hidden before availability) | — |
| announce | config `catalog.visibility.future = announce` | R2 → `VISIBLE_SCHEDULED` with label "dostępne od <date>" | config key `catalog.visibility.future` |

The board and transcript do not say what a customer sees between publication and availability (**Q10**); the default is
*hidden*, because less is published that way and the read model cannot leak a not-yet-available product.

## Executable scenarios

| Scenario | offerState | availabilityStart | today | expected |
|---|---|---|---|---|
| S1 | published | null | 2026-06-15 | VISIBLE |
| S2 | published | 2026-05-10 | 2026-06-15 | VISIBLE |
| S3 | published | 2026-07-01 | 2026-06-15 | HIDDEN |
| S4 | published | 2026-06-15 | 2026-06-15 | VISIBLE |
| S5 | published | 2026-07-01 | 2026-06-15 | VISIBLE_SCHEDULED under the `announce` variant |
| S6 | pendingReview | null | any | HIDDEN |
| S7 | draft | null | any | HIDDEN |
| S8 | removed | 2026-05-10 | 2026-06-15 | HIDDEN |

## Domain events

None. Visibility is derived; the removal event belongs to 02 (`OfferRemoved`).
