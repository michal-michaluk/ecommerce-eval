# 08 — ReadyForDeliveryDecision

**Role:** **Derived** — automated decision (pure verdict over 02 and 04). The decision itself computes GRANT or REFUSE; the two effects of a
GRANT are executed by `OrderProcessing` (02) and `Parcel` (03). It is the only place where "the order is done" is
decided.

**Definition:** the decision that a packed order leaves the warehouse's hands, and it produces **two effects in one
command** — "he can mark that this order is done is ready for delivery, which marks it in our system, plus we can make a
request to deliver it" `[seg 122]`, KB `fulfillment/order-processing/shipping-and-delivery-states.md:34-44`: RF1 the
local mark, RF2 the carrier collection request, whose meaning is "they know they should pick it" `[seg 123]`.

The board carries `mark done / ready for delivery` as the employee's command and `ready for delivery` a second time as
both command and event (`eventstorming.json:118`, `:121`, `:130`); the duplicated sticky is one action
(F10, matching the board's own overlap annotation `eventstorming.json:148`).

## Decision table

```text
Decision: MarkReady(orderState, positionsAllPacked, parcelRegistered)
| Rule       | orderState      | positionsAllPacked | parcelRegistered | outcome                          |
|------------|-----------------|--------------------|------------------|----------------------------------|
| DT-08-1   | packed          | yes                | yes              | GRANT: RF1 mark + RF2 collect    |
| DT-08-2   | packed          | no                 | yes              | REFUSE (reason: positionsOpen)   |
| DT-08-3   | pending, assigned, picking | any      | any              | REFUSE (reason: notPacked)       |
| DT-08-4   | onHold          | any                | any              | REFUSE (reason: onHold)          |
| DT-08-5   | readyForDelivery| any                | yes              | NO-OP (replay: no second RF2)    |
| DT-08-6   | delivered       | any                | any              | REFUSE (reason: pastHandover)    |
| DT-08-7   | any             | any                | no               | REFUSE (reason: noParcel)        |
```

Evaluation order is DT-08-7, DT-08-4, DT-08-3, DT-08-2, DT-08-1, DT-08-5, DT-08-6 — first match wins, so no rule can produce an effect without a parcel and
a held order is refused as `onHold` rather than as `positionsOpen`.

The hold is a **state**, never a flag beside one: `onHold` is what 09 sets on `OrderProcessing.state` (02 state machine,
09 H1), so DT-08-4 is keyed on the state itself: a `packed`-and-held order is simply `onHold`, and DT-08-4 refuses it.
DT-08-5 ignores `positionsAllPacked` on purpose — once an order has crossed into `readyForDelivery`, its positions were packed
by definition, and a replay must be a no-op. `readyForDelivery` appears in DT-08-5 alone and `delivered` in DT-08-6 alone, so
no row is shadowed by an earlier one.

Row provenance:

| Rule    | Source status | Spec decision |
| --- | --- | --- |
| DT-08-1 | supported — `[seg 122-123]`, `packed → readyForDelivery` in the machine (KB `order-assignment.md:65`) | as is |
| DT-08-2 | **no source** — completion is the employee's declaration `[seg 122]`, "no source requires the system to block it while positions remain unscanned" (KB `picking-and-packing.md:67`), guard unstated (KB `picking-and-packing.md:80`) | **spec default** taken in 02 RULE-4: all positions `packed` is required (Q-08-1) |
| DT-08-3 | supported — the command sits after packing `[seg 120-122]` | as is |
| DT-08-4 | **no source** — whether a hold blocks `mark done` is UND-22 (KB `issues-and-hold.md:120`) | **spec default**, owned by 09: the guard reads `state = onHold` and never re-derives a hold from issues |
| DT-08-5 | no source | **default:** a replay is a no-op so a polled/retried command never asks the carrier to collect twice (`spec.md` Step 2 *Resilience*) |
| DT-08-6 | derived — 02's machine has no edge back from `readyForDelivery` | as is |
| DT-08-7 | derived — registration precedes everything `[seg 115-116]`, so a missing parcel is unreachable; RF2 has no provider ID to address `[seg 116]` | **defensive** refusal, not a modelled state |

`onHold` is 09's verdict over 04's open issues: one issue puts the **whole** order on hold (KB
`issues-and-hold.md:44`). The word blocking here is not the basket's `blocking` / `non-blocking` severity — that
vocabulary belongs to checkout, is ambiguous there and has no undecided-register entry (KB
`domain-language/ubiquitous-language.md:227`, `:232`).

## Inputs and origins

| Value object | Values | Origin | Source |
|---|---|---|---|
| OrderState | `pending` … `delivered` | Business concept | 02 `OrderProcessing.state` |
| Positions | `picked` \| `packed` per position | Business concept | 02 `positions` — the guard reads state, never scan counts (Q-08-1) |
| ParcelRef | `parcelId`, provider ID | Business concept | 03 `Parcel` |
| CollectionRequest | provider ID, parcel reference | Integration (outbound port) | `CarrierPort`, our contract only (spec.md D2) |

No variant list: one rule set, no configuration key, no tenant dimension — no source offers an alternative reading of
`mark done` `[seg 122]`.

## Behaviour

- GRANT, effect 1: `OrderProcessing.packed → readyForDelivery` and the event `ready for delivery`
  (`eventstorming.json:130`)
- GRANT, effect 2: `Parcel.requestCollection()` through the carrier port; the parcel goes `parcelRegistered →
  parcelReady` (03), and the carrier's L2 leg (`[seg 125]`) later confirms it
- GRANT is ordered local-then-remote, as the source sentence itself is ordered `[seg 122]`
- REFUSE → neither effect runs; the reason is returned to the workstation; nothing is written
- NO-OP → neither effect, in particular no duplicate RF2

## Domain rules

- **RULE-1 Two effects, one command.** A GRANT that applies RF1 without RF2 leaves a customer-facing "ready" on an order
  nobody agreed to collect — the failure the source does not describe (R-08-2, Q-08-2).
- **RULE-2 The precondition is positions plus hold.** Positions `packed` (DT-08-2) and no active hold (DT-08-4); the guard reads
  no other input, and in particular never re-derives the hold from issues (09 owns that).
- **RULE-3 Employee declaration, machine guard.** The employee decides he is done `[seg 122]`; the guard only checks
  that his declaration is consistent with recorded state. It never blocks on an unrecorded fact.
- **RULE-4 No undo.** There is no `unmark ready`, no retraction and no reverse edge in any source; a mistake after a
  GRANT is corrected outside the machine (DT-08-6, Q-08-3).
- **RULE-5 Idempotent replay.** `(processingId, readyForDelivery)` applied twice changes nothing and calls the carrier
  once (rule DT-08-5; both carrier and warehouse inputs are polled, `spec.md` Step 2 *Resilience*).
- **RULE-6 No money.** The collection request carries no amount, no price and no rounding; a COD or declared-value
  figure is an opaque pass-through value owned by its source and stored nowhere here (spec.md D5).

## Scenario table

Order `OP-2026-0001`, one position, parcel registered.

| Scenario | orderState | positions | parcel | expected |
|---|---|---|---|---|
| S1 | packed | 1 × packed | registered | GRANT → mark + collect |
| S2 | packed | 1 × picked | registered | REFUSE (positionsOpen) |
| S3 | picking | 1 × picked | registered | REFUSE (notPacked) |
| S4 | onHold | 1 × packed | registered | REFUSE (onHold) |
| S5 | readyForDelivery | 1 × packed | ready | NO-OP, no second collect |
| S6 | readyForDelivery | 1 × picked | ready | NO-OP (DT-08-5 ignores positions) |
| S7 | delivered | 1 × packed | delivered | REFUSE (pastHandover) |
| S8 | packed | 1 × packed | none | REFUSE (noParcel) |

## Condition diff — `packed` → `readyForDelivery`

```diff
# 02
- state: packed
+ state: readyForDelivery
# 03
- legState: parcelRegistered
+ legState: parcelReady
# L1 stays as recorded at registration (03 RULE-1); L2 arrives later as the carrier's acknowledgement, not here
```

## Domain events

```yaml
- ready for delivery: { processingId: "OP-2026-0001", parcelId: "PCL-2026-0007" }
```

One event for two effects: the board has a single `ready for delivery` (`eventstorming.json:130`), and L2 of the
delivery chain — "we mark it as ready as delivery" `[seg 125]` — arrives later as the carrier's acknowledgement of RF2
(KB `shipping-and-delivery-states.md:63`).

## Q / R carried into this element

- **F10 — the board carries `ready for delivery` twice**, as command and as event (`eventstorming.json:121`, `:130`),
  next to `mark done / ready for delivery` (`:118`). Treated as one employee action with two effects; the duplicate
  sticky is recorded, not resolved (same class as the board's own `packed product` overlap note `:148`).
- **Q-08-1 open:** does an unscanned position block `mark done`, and is a scan enforced before `packed` at all (KB
  `picking-and-packing.md:75`, `:80`)? Default: positions must be `packed` (02 RULE-4); scans are never inspected here.
- **Q-08-2 open:** RF1 succeeds and RF2 fails — nothing describes recovery, and the source calls it the most likely
  real-world failure (KB `shipping-and-delivery-states.md:46`, `:118`).
- **Q-08-3 open:** is `mark done` reversible in any way, and what happens to a parcel asked to be collected by mistake? No
  reverse leg, no cancellation (KB `shipping-and-delivery-states.md:88`).
- **Q-08-4 open:** must the actor be the current assignee? The board's actor is `employee` (`eventstorming.json:114`) and
  nothing ties the command to the assignee — the guard does not check it.
- **R-08-1 open:** with `packed` reachable without a scan (Q-08-1), an order can be declared ready with zero scanning
  evidence, which is precisely the confirmation the scan exists to give `[seg 120]`.
- **R-08-2 open:** the two effects can diverge permanently — the order says ready, the carrier was never asked to collect
  (KB `shipping-and-delivery-states.md:46`).
