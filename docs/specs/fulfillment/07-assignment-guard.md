# 07 — AssignmentGuard

**Role:** **Derived** — automated decision (pure verdict over `OrderProcessing`). It owns no state: the assignee lives in 02; the guard
computes GRANT or REFUSE and nothing else. It is the only concurrency control the sources name — "only one person can
be assigned at a time" `[seg 114]`, KB `fulfillment/order-processing/order-assignment.md:11`.

**Definition:** the decision that an employee's claim on one order is accepted. Exclusivity is not fairness — it is
physical: a second employee means a second pick list against the same stock, a second pack and a second parcel
`[seg 112-113]`, KB `order-assignment.md:16`, `:85`.

## Decision table

```text
Decision: Assign(orderState, assignee, requester)
| Rule       | orderState                    | assignee        | requester       | outcome                          |
|------------|-------------------------------|-----------------|-----------------|----------------------------------|
| DT-07-1   | pending                       | none            | employee        | GRANT                            |
| DT-07-2   | pending                       | none            | customer/system | REFUSE (reason: actor)           |
| DT-07-3   | assigned, picking, packed, onHold | another employee | employee   | REFUSE (reason: alreadyAssigned) |
| DT-07-4   | assigned, picking, packed, onHold | the requester   | employee    | NO-OP (idempotent re-claim)      |
| DT-07-5   | readyForDelivery              | any             | employee        | REFUSE (reason: pastPicking)     |
| DT-07-6   | delivered                     | any             | employee        | REFUSE (reason: closed)          |
```

Rows are evaluated DT-07-1 → DT-07-6, first match wins. DT-07-3's state list stops at `onHold`, so a `readyForDelivery` order is
refused as `pastPicking` (DT-07-5) even when an employee still holds it — the hold on an order is a state, not a flag
(09 Decision table).

**Wire codes.** Each REFUSE row answers `409` with one `error` code — `ACTOR_NOT_ALLOWED` (DT-07-2), `ALREADY_ASSIGNED`
(DT-07-3), `PAST_PICKING` (DT-07-5), `CLOSED` (DT-07-6) — and a NO-OP answers `200` with the unchanged state; the envelope,
its null policy and the status split are element 11's.

Row provenance, with this spec's default where the source is silent:

| Rule    | Source status | Spec decision |
| --- | --- | --- |
| DT-07-1 | supported — `pending` `[seg 110]`, employee assigns himself `[seg 111]` | as is |
| DT-07-2 | supported — board actors are `employee` and `customer` only (`eventstorming.json:114`), and the claimer is the worker `[seg 111]` | as is: the guard has no dispatcher input |
| DT-07-3 | supported — `[seg 112-114]`, KB `order-assignment.md:11-12` | as is |
| DT-07-4 | no source | **default:** a repeated claim by the owner is a no-op, so one claim emits one event (RULE-8) |
| DT-07-5 | no source — nothing says a ready order cannot be claimed | **default:** the pick list is handed at assignment `[seg 117]`; there is nothing left to pick |
| DT-07-6 | derived — `delivered` is terminal (02), the list shows *current* orders `[seg 110]` | as is |

## Inputs and origins

| Value object | Values | Origin | Source |
|---|---|---|---|
| OrderState | `pending` … `delivered` | Business concept | 02 `OrderProcessing.state` |
| Assignee | employee id or none | Business concept | 02 `assignee` (the guard never writes it) |
| Requester | employee id | Business concept | the command's actor, board actor `employee` (`eventstorming.json:114`) |

No integration participates: the decision is closed over in-context state. It carries no amount and computes no
money (spec.md D5).

## Behaviour

- GRANT → 02 sets `assignee = requester`; emits `employee assigned to order fulfilment` (`eventstorming.json:127`);
  the orders list updates `[seg 137-138]`; the employee gets the pick list `[seg 117]`
- REFUSE → a rejection with a reason; **the existing state is untouched** — no release, no reassignment, no queue
- NO-OP → neither event nor write; the caller re-reads the same state
- the verdict is computed and applied in one operation (RULE-6), because two simultaneous claims must not both see
  `assignee: none`

## Domain rules

- **RULE-1 Exclusivity.** At most one `assignee` at any instant, across every state `[seg 114]`, KB
  `order-assignment.md:11`.
- **RULE-2 Grant or refuse, never queue.** There is no waiting list, no reservation of a future claim and no
  "requested" assignment state in any source — the hole list KB `order-assignment.md:87-97` names none of them; a
  refused employee must re-query the orders list.
- **RULE-3 No silent release.** A refusal never changes the current assignee, and the guard has no operation that
  clears one — release/unassign does not exist in the sources at all (KB `order-assignment.md:91`, R-07-1).
- **RULE-4 Reason is physical.** The refusal reason is `alreadyAssigned`, justified by two parcels for one order
  `[seg 112-113]` — not by workload, seniority or shift (KB `order-assignment.md:16`).
- **RULE-5 The claimer is the assignee.** The guard takes `requester` as the candidate; it cannot assign on behalf of
  someone else — "some employee on the warehouse can assign himself or herself" `[seg 111]`.
- **RULE-6 Atomic verdict.** Check and set in one operation; a read-then-write sequence recreates the exact failure the
  guard exists to prevent (KB `order-assignment.md:85`).
- **RULE-7 Pure.** No I/O, no clock, no timeout, no retry: the guard reads 02 state and returns a verdict (Q-07-1).
- **RULE-8 One grant, one event.** Each GRANT emits exactly one `employee assigned to order fulfilment`
  (`eventstorming.json:127`); DT-07-4's NO-OP reuses it idempotently (Q-07-2).
- **RULE-9 Hold does not open the guard.** A held order still has its assignee — a hold never releases it (09 RULE-7,
  Q-07-4) — so DT-07-3 refuses a foreign claim exactly as on an unheld order.

## Scenario table

| Scenario | orderState | assignee | requester | expected |
|---|---|---|---|---|
| S1 | pending | none | employee-42 | GRANT |
| S2 | pending | none | customer | REFUSE (actor) |
| S3 | pending, claimed concurrently by employee-42 | employee-42 | employee-17 | REFUSE (alreadyAssigned) |
| S4 | picking | employee-42 | employee-42 | NO-OP |
| S5 | onHold | employee-42 | employee-17 | REFUSE (alreadyAssigned) |
| S6 | onHold | employee-42 | employee-42 | NO-OP |
| S7 | readyForDelivery | employee-17 | employee-42 | REFUSE (pastPicking) |
| S8 | delivered | employee-17 | employee-42 | REFUSE (closed) |

## Condition diff — `pending` → `assigned`

```diff
- assignee: null
+ assignee: "employee-42"
# verdict GRANT; one event: employee assigned to order fulfilment
```

## Domain events

```yaml
- employee assigned to order fulfilment: { processingId: "OP-2026-0001", assignee: "employee-42" }
```

REFUSE and NO-OP emit nothing: the board has no `assignment refused` event (`eventstorming.json:125-135`), and a
refusal is a response to the caller, not a fact of the order.

## Q / R carried into this element

- **R-07-1 open:** no release, unassign or expiry exists in any source (KB `order-assignment.md:91`) — an absent employee
  blocks his orders forever, and the guard cannot help because RULE-3 forbids it.
- **Q-07-1 open:** is there a timeout or expiry on an assignment? No source names one (KB `order-assignment.md:92`).
  Default: none; the guard is clock-free (RULE-7).
- **Q-07-2 open:** can an order be transferred between employees, and would a transfer emit a second
  `employee assigned to order fulfilment` (KB `order-assignment.md:93`)? Default: no transfer exists, so the event is a
  claim record only.
- **Q-07-3 open:** is the guard order-level only, or also per position when two employees share a pick (KB
  `order-assignment.md:94`)? Default: order-level — the sources describe one employee per order `[seg 114]`.
- **Q-07-4 open — guard half of element 09 Q-09-2:** is the guard consulted at all for an order already `onHold`, or does the state
  bypass it (KB `order-assignment.md:97`, UND-22)? Default: no bypass — DT-07-3 refuses a foreign claim exactly as on an
  unheld order. Whether a hold *should* release the assignee is asked once, in element 09 Q-09-2.
