# 04 — FulfillmentIssue

**Role:** **Definition** (record, produced by an employee). One report of a warehouse reality gap — no source names an
edit or a close, only the report and its consequence, the hold (KB `fulfillment/order-processing/issues-and-hold.md:110`).
Append-only except one fact: `closedAt`, set by the resume command (`spec.md` D8), never by the dashboard. The state it
causes belongs to `OrderProcessing` (02) and is decided by `HoldDecision` (09).

**Definition:** the record that what the warehouse system claims and what is physically on the shelf disagree —
"the reality of the warehouse is not expressed correctly in the system" `[seg 142]`, KB `issues-and-hold.md:7`. It is
not an order defect: it is a stock/location fact the pick list tripped over.

## Example instances

```yaml
# reported mid-picking, units are gone — the shape of [seg 140-141]
issueId: "ISS-2026-0031"
processingId: "OP-2026-0002"
kind: LOST
reference: { productNumber: "75423" }
reportedBy: "employee-17"
reportedAt: "2026-11-19T12:40:12Z"
closedAt: null                      # the record's only mutable fact (RULE-1, spec.md D8)
```

```yaml
# reported after packing, units exist but elsewhere — [seg 150-151]
issueId: "ISS-2026-0032"
processingId: "OP-2026-0005"
kind: WRONG_LOCATION
reference: { productNumber: "75423" }
reportedBy: "employee-22"
reportedAt: "2026-11-19T14:05:55Z"
```

`productNumber` is the whole reference: the pick list's own column (`eventstorming.json:146`) and exactly what the
resolver's only described job needs — look for that piece and correct the warehouse `[seg 149-151]`. The narration names
a quantity ("we have two last units, but we cannot find them" `[seg 140]`) and the pick list a location; neither is
carried, because no source shows anyone reading one back (Q-04-5).

## Issue kinds — open by the source's own wording

| Code | Source wording | Status |
| --- | --- | --- |
| `LOST` | "they are lost" `[seg 141]` | named |
| `DESTROYED` | "destroyed" `[seg 141]` | named |
| `STOLEN` | "stolen" `[seg 141]` | named |
| `WRONG_LOCATION` | "someone put them on the warehouse in wrong place" `[seg 150]`; "we have them physically, but we don't know the correct location" `[seg 151]` | named |
| any other code | the enumeration closes with "whatever" `[seg 141]` | **open** — stored verbatim, never rejected |

The list is **not** closed and must not be closed in a schema (KB `issues-and-hold.md:34`,
`domain-language/ubiquitous-language.md:389`). `DAMAGED` is **not** a member: the word exists only in the superseded
document and is explicitly rejected as a type (KB `issues-and-hold.md:32`).

## Behaviour

- `reportIssue(kind, reference)` → a new immutable record; emits `issues`; the order goes to `onHold` through 09
- read by the `current issues` read model (dashboard, `eventstorming.json:140`, KB `issues-and-hold.md:55`) and, as the
  open-issue set, by `HoldDecision` (09) — read by nothing else, and it never changes the pick list, the parcel or the order
- resolution on the spot is physical: the resolver finds the pieces and corrects the warehouse `[seg 149-151]`; the
  record moves only in `closedAt`, when the resume command names it (`spec.md` D8, element 09 H5)
- when it cannot be resolved, the customer is negotiated with and an adjusted order leaves the system `[seg 102-107]`
  — outside this context (UND-19)

## Domain rules

- **RULE-1 Append-only, one exception.** An issue is written once and never mutated, except `closedAt` — an optional
  timestamp set only by the resume command (`spec.md` D8, element 09 H5/H6), never by the dashboard (element 17). No
  source names `resolved`, `closed` or `cancelled` as an issue state (KB `issues-and-hold.md:110`); open means
  `closedAt == null` (R-04-1).
- **RULE-2 Reporter is the employee holding the order.** The board's actors are `employee` and `customer`
  (`eventstorming.json:114`); the reporter is the `employee`, the command is `mark problems with fulfillment`
  (`eventstorming.json:119`), the moment is picking `[seg 139-141]`.
  No system-detected issue exists; no warehouse signal creates one.
- **RULE-3 The order, not the position, is affected.** The record names a product, but the hold lands on
  the whole order — one missing unit stops the fully available positions too (KB `issues-and-hold.md:44`, `:49`).
- **RULE-4 Open kind set.** Four codes are named, others enter verbatim; the kind is never validated against a closed
  enum (Q-04-1).
- **RULE-5 Minimum reference.** `kind`, `reference.productNumber`, `reportedBy` and `reportedAt` are the whole record; a
  position-level quantity or location is not carried (Q-04-5), photo or free text is not either (Q-04-2).
- **RULE-6 No adjustment of the order.** Replacement, cancellation and "deliver without the missing item" are agreed
  with the customer and "done outside of the system" `[seg 103-105]`; an issue never edits the pinned `Order`
  (01 RULE-2) and never creates an adjusted order here.
- **RULE-7 No hold field.** The issue carries no state; the hold is `OrderProcessing.state = onHold` (02), decided by
  `HoldDecision` (09). A hold event is emitted once by 09, never by this record (KB `issues-and-hold.md:45` gives
  `issues` and `order is on hold` as two separate events).
- **RULE-8 Not a money object.** No amount, price or rounding appears on an issue; the declared value of the goods is
  not an issue attribute (D5, `spec.md`).

## Condition diff — what changes is not the issue

```diff
# the order, when the issue appears (02) and when the hold is lifted (09)
- state: picking
+ state: onHold
+ issue: { issueId: "ISS-2026-0031", kind: LOST, … }

# the issue record, across the same two moments — nothing
  kind: LOST
  reference: { productNumber: "75423" }
# …and `closedAt` is the one fact that ever moves on it: set by the resume command (spec.md D8), never by the hold
```

## Domain events

Emitted: `issues` (`eventstorming.json:131`), raised by the command `mark problems with fulfillment`
(`eventstorming.json:119`). Consumed by nobody in this context — the dashboard and 09 read the record, not the event.
No resolution event is named anywhere (element 09 Q-09-4): closing is a field write on the record (`closedAt`, `spec.md` D8),
not an event.

## Q / R carried into this element

- **F9 — `NOT_FOUND` was not a source-named kind; it is now `LOST`** (`[seg 141]`) in the element-02 examples (02:31, 02:94)
  and in the issue request body of `prototypes/fulfillment-api.hurl`. `stock-count mismatch` stays a *situation*, not a type
  name (KB `issues-and-hold.md:31`).
- **Q-04-1 open:** the kind list is open — "whatever" `[seg 141]`, UND-22. Default: four named codes plus free entry,
  stored verbatim; the dashboard groups by code and shows unknown ones as-is.
- **Q-04-2 open:** does an issue carry a photo or free text (KB `issues-and-hold.md:118`). Default: kind + product +
  reporter + time; nothing else.
- **Q-04-3 open:** is the issue tied to the order or to the warehouse location? A wrong-location issue is a warehouse
  correction that exists independently of the order `[seg 151]`, KB `issues-and-hold.md:123`. Default: the record
  references the processing; the correction is the warehouse port's business.
- **Q-04-4 open:** does the customer see the issue/hold, or only the delay? `[seg 136]` promises "all those states" and
  does not enumerate them (KB `issues-and-hold.md:121`). Default here: the issue, its kind and the reporter are never shown;
  whether the hold itself appears as a paused marker is element 18's call (Q-18-1), which defaults to showing it.
- **Q-04-5 open:** is a position-level quantity ever needed on an issue — "we have two last units" `[seg 140]` — and can a
  partial close ("found 1 of 2") be expressed (KB `issues-and-hold.md:118` question 3)? Default: no quantity on the
  record, one `closedAt` per issue (`spec.md` D8), so a partial close cannot be expressed and the dashboard row cannot say
  "1 of 2" (element 17).
- **R-04-1 resolved by `spec.md` D8:** no source gives an issue a lifecycle, so the close is one field — `closedAt`, set by
  the resume command and by nothing else (RULE-1). Residual risk: the lift is unannounced (element 09 Q-09-4) and a
  partial close has no expression (Q-04-5).
