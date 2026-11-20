# 09 — HoldDecision

**Role:** **Derived** — automated decision (pure verdict over 02 and 04). The hold state lives in `OrderProcessing` (02); this element decides
when a hold exists, **exactly what it blocks**, and what may lift it. It is the element the whole issue area hangs on:
"if we have issue, order is on hold, and someone else will see issue on his dashboard" `[seg 149]`.

**Definition:** the decision that one issue stops one whole order. A hold is not a state of the issue and not a
property of the position — it is the order-level consequence of a warehouse reality gap (KB
`fulfillment/order-processing/issues-and-hold.md:44`, H1), emitted as its own event `order is on hold`
(`eventstorming.json:132`), separate from `issues` (`:131`).

## Decision table — when a hold is granted, kept and lifted

```text
Decision: Hold(command, orderState, openIssues)
| Rule | command     | orderState                   | openIssues | outcome                                 |
|------|-------------|------------------------------|------------|-----------------------------------------|
| H1   | reportIssue | picking, packed              | any        | GRANT: record issue + place hold        |
| H2   | reportIssue | onHold                       | ≥1         | record issue, NO-OP hold (no 2nd event) |
| H3   | reportIssue | pending, assigned            | any        | REFUSE (reason: notStarted)             |
| H4   | reportIssue | readyForDelivery, delivered  | any        | REFUSE (reason: pastHandover)  → Q-09-3    |
| H5   | resumeHold  | onHold                       | 0          | GRANT: close the named issues, resume pre-hold state |
| H6   | resumeHold  | onHold                       | ≥1         | REFUSE (reason: issuesRemain) + `openIssues` |
| H7   | resumeHold  | pending, assigned, picking, packed, readyForDelivery, delivered | any | NO-OP          |
```

A hold is the **state** `onHold` on `OrderProcessing` (02), not a flag beside a state: it has no sibling row and the
table never reads a state and a hold at once. Rows are disjoint on `(command, orderState)`, so no precedence is needed.
`resumeHold` carries the issues it vouches for — `{ "closedIssueIds": [...] }` — so the close is an explicit fact of that
command, never a side effect and never a dashboard action (`spec.md` D8).

Row provenance:

| Rule | Source status | Spec decision |
| --- | --- | --- |
| H1 | supported — `[seg 139-149]`, `picking → onHold` and `packed → onHold` (KB `order-assignment.md:67-68`) | as is |
| H2 | no source | **default:** one hold per order; an issue reported while `onHold` records no second `order is on hold`, so replay and double reporting stay idempotent (02 RULE-6) |
| H3 | supported by absence — "no source places an issue before picking starts", the machine has no such edge on purpose (KB `order-assignment.md:69`, 02 state machine) | as is |
| H4 | no source — reachable only because the fallback is offered next to the happy-path action `[seg 139]`, and "hold arriving after packing is plausible and unaddressed" (KB `issues-and-hold.md:120`) | **default:** refused, because 02 has no reverse edge from `readyForDelivery`; the plausibility is Q-09-3 |
| H5 | supported — `onHold --> processing: issue dealt with, warehouse corrected` `[seg 149-151]`, KB `issues-and-hold.md:95`; resume state fixed by element 02 Q-02-2, the close recorded as `closedAt` (element 04 RULE-1, `spec.md` D8) | as is |
| H6 | supported — the resume label requires the issue dealt with (KB `issues-and-hold.md:95`) | as is: zero open issues on the order — the request cannot close an issue it does not name (`spec.md` D8) |
| H7 | no source | **default:** replay of a resume is a no-op |

## What a hold blocks — and what it does not

This is UND-22's third question. Only the first rows are answered by a source; the delivery column is not (KB
`issues-and-hold.md:47`, H4).

| Action | hold inactive (`picking` / `packed`) | hold active (`onHold`) | Basis |
| --- | --- | --- | --- |
| pick / pack the next position | allowed | **REFUSED** | "the order stops until the issue is dealt with" (KB `fulfillment/order-processing/README.md:59`), `[seg 148-149]` |
| `markReady` — local mark (RF1) | per 08 | **REFUSED** | 02 RULE-4, `spec.md` Q3, element 02 Q-02-4: a hold blocks `markReady` at minimum; element 08 DT-08-4 |
| `markReady` — collection request (RF2) | per 08 | **REFUSED** | same decision; the two effects of 08 RULE-1 never split under a hold |
| `assign` by another employee (07) | per 07 | REFUSED (`alreadyAssigned`) — the hold keeps its assignee | 07 RULE-9; no release exists (element 07 R-07-1) |
| `register parcel` (03) | allowed | **ALLOWED** | registration happens "on the very beginning" `[seg 115-116]`, before an issue can exist at all (03 RULE-1) |
| apply carrier legs L1–L6 to `Parcel` | applied | **applied** | carrier-owned facts cannot be refused or filtered (03 RULE-4, KB `shipping-and-delivery-states.md:63`) |
| resume the order, closing the named issues | n/a | **only H5** | RULE-4, `spec.md` D8 |
| cancel or adjust the order | never | **never** | 01 RULE-2, `[seg 103-105]` outside the system, UND-19 |
| release the assignee | never | **never** | no release path exists (element 07 R-07-1) — whether it should is Q-09-2 (asked once, here) |
| does the physical parcel stop moving | n/a | **UNKNOWN** | no source says a hold blocks delivery (KB `issues-and-hold.md:47`); Q-09-2 |

## Inputs and origins

| Value object | Values | Origin | Source |
|---|---|---|---|
| OrderState | `pending` … `delivered` | Business concept | 02 `OrderProcessing.state` |
| OpenIssueSet | issue records | Business concept | 04 `FulfillmentIssue`, unclosed — read only as a count of open records |
| Command | `reportIssue` \| `resumeHold` | Business concept | board commands `mark problems with fulfillment` (`eventstorming.json:119`) and the resume, which the board does not carry |
| Actor | employee id | — | **not evaluated here.** The board has no resolver actor (`eventstorming.json:114`); who may invoke is fixed by `spec.md` D7 |

No integration participates: no carrier signal, no warehouse signal and no clock is an input. No amount is read or
computed (spec.md D5).

## Behaviour

- an issue (`issues`, `eventstorming.json:131`) → H1 (first hold, legal only from `picking` or `packed`) or H2 (further
  issue on an order already held) → `order is on hold` (`:132`) → `OrderProcessing.state = onHold` (02), emitted once
- while held: positions do not advance, `markReady` is refused (element 08 DT-08-4) and the resolver works the dashboard
  (`current issues`, `eventstorming.json:140`, `[seg 147]`)
- the resolver recovers the units and corrects the warehouse `[seg 149-151]`; that correction is warehouse-side and
  closes nothing by itself — the close is recorded when he names the issue to the resume command (`closedAt`, element 04
  RULE-1, `spec.md` D8)
- `resumeHold(closedIssueIds)` with zero issues left open → H5: the named issues get `closedAt`, the order returns to the
  state it was held from (element 02 Q-02-2); one issue the request did not name stays open → H6, `409 ISSUES_REMAIN`
  with `openIssues`
- the customer-facing view updates after each event `[seg 137-138]`; whether a hold is one of the shown states is element 04 Q-04-4

## Domain rules

- **RULE-1 One issue holds the whole order.** Not the position, not the line — one missing unit stops the fully
  available positions too (KB `issues-and-hold.md:44`, `:49`, `[seg 148-149]`).
- **RULE-2 Hold is its own fact.** Two events, two records: `issues` and `order is on hold` (`eventstorming.json:131`,
  `:132`). The hold is not a boolean folded into the issue (04 RULE-7).
- **RULE-3 Hold blocks `markReady`, both effects.** This is the accepted minimum of a question the sources leave open:
  `spec.md` Q3 and element 02 Q-02-4 fix it "until answered", and element 08 DT-08-4 consumes it instead of re-deriving it.
- **RULE-4 Only `resumeHold` that leaves zero open issues lifts a hold.** It is also the only close path: an issue's
  `closedAt` is written by this command and by nothing else (element 04 RULE-1, `spec.md` D8). No carrier event, no
  warehouse event, no elapsed time, no clock and no retry lifts it. The list of lifters is one row long on purpose (H5) and the carrier path is excluded
  mechanically: a delivery notification writes only `Parcel.legState` (03), the order moves
  `readyForDelivery → delivered` on L6 alone (element 02 Q-02-3), and a held order is never in `readyForDelivery` — there is no
  edge from a held state to `delivered` (02 state machine). Holding is therefore sticky (02 RULE-7).
- **RULE-5 No timeout, escalation or SLA.** No number, deadline or escalation exists in any source (KB
  `issues-and-hold.md:119`); the decision is clock-free.
- **RULE-6 The hold never touches the order document.** No cancellation, no amendment, no replacement: those are
  negotiated with the customer and "done outside of the system" `[seg 103-105]`, and the order is immutable (01 RULE-2).
  An adjusted order is a different document produced outside (UND-19).
- **RULE-7 No release of the assignee.** A hold leaves `assignee` untouched, because no release operation exists at all
  (element 07 R-07-1); whether it should release him is Q-09-2, not a silent behaviour.
- **RULE-8 Who may lift a hold.** `resumeHold` is granted to `fulfillment-employee` and `fulfillment-resolver`
  (`spec.md` D7, element 11); the board names no resolver actor (`eventstorming.json:114`) and no other role is granted.
  The grant is a spec decision, not a source fact — `Q-09-1` closed by `spec.md` D7.
- **RULE-9 Idempotent.** Repeated `reportIssue` while held records the issue and emits no second hold (H2); repeated
  `resumeHold` while unheld is a no-op (H7). Both are required because the surrounding inputs are polled (`spec.md`
  Step 2 *Resilience*).
- **RULE-10 No money.** A hold compares no amount, applies no penalty and stores no declared value (spec.md D5).

## Scenario table

Order `OP-2026-0002`, one position, assignee `employee-17`; pre-hold state `picking` unless the row says otherwise.

| Scenario | command | orderState | openIssues | expected |
|---|---|---|---|---|
| S1 | reportIssue (`LOST`, qty 2) | picking | 0 | GRANT: issue + hold; state `onHold` |
| S2 | reportIssue (`WRONG_LOCATION`) | onHold | 1 | issue recorded, hold NO-OP, one hold event total |
| S3 | reportIssue (`LOST`) | pending | 0 | REFUSE (notStarted), no state change |
| S4 | reportIssue (`LOST`) | assigned | 0 | REFUSE (notStarted) |
| S5 | reportIssue (`LOST`) | readyForDelivery | 0 | REFUSE (pastHandover) — Q-09-3 |
| S6 | resumeHold, all goods found | onHold | 0 | GRANT: back to `picking` (pre-hold state) |
| S7 | resumeHold, units still missing | onHold | 1 | REFUSE (issuesRemain) |
| S8 | resumeHold | packed (not held) | 0 | NO-OP |
| S9 | carrier L6 notification | onHold | 1 | unchanged — order stays `onHold`, only `Parcel` moves (RULE-4) |
| S10 | `markReady` | onHold | 1 | REFUSE (onHold) — element 08 DT-08-4 |

## Condition diff — `picking` → `onHold` → back

```diff
- state: picking
+ state: onHold
+ issue: { issueId: "ISS-2026-0031", kind: LOST, productNumber: "75423", reportedBy: "employee-17" }
  assignee: "employee-17"          # unchanged by the hold (RULE-7)

# after H5, zero open issues
- state: onHold
+ state: picking                  # the state held from (element 02 Q-02-2)
  assignee: "employee-17"
```

## Domain events

```yaml
- issues:            { processingId: "OP-2026-0002", issueId: "ISS-2026-0031", kind: LOST }   # 04 owns the record
- order is on hold:  { processingId: "OP-2026-0002", since: "2026-11-19T12:40:12Z" }            # 09's own fact
```

`order is on hold` is `eventstorming.json:132`; `issues` is `:131`. **No event announces a resume** — the board's event
list stops at `order is on hold` (`eventstorming.json:125-135`, Q-09-4), so the lift is currently state-only, which the
customer view cannot see without inventing an event.

## Q / R carried into this element

- **Q-09-1 closed by `spec.md` D7:** who may lift a hold is decided — `fulfillment-employee` and `fulfillment-resolver`,
  nothing else. The board names no resolver actor (`eventstorming.json:114`) and the recovered `Issue Resolver` role is an
  invention (KB `domain-language/ubiquitous-language.md:396`), so the grant is a spec decision, not a source fact.
- **Q-09-2 open:** does a hold block delivery, cancel anything or release the assignee (UND-22, KB `issues-and-hold.md:47`,
  `:120`)? **Until answered: it blocks `markReady` only** — registration, parcel legs and the physical delivery are not
  blocked, and the assignee keeps the order.
- **Q-09-3 open:** may a hold be placed after `readyForDelivery`, i.e. after the carrier was asked to collect `[seg 139]`?
  Plausible and unaddressed (KB `issues-and-hold.md:120`); H4 refuses it today because 02 draws no such edge.
- **Q-09-4 open:** what announces a resume? Nothing on the board (`eventstorming.json:125-135`), yet the customer view
  updates after each event `[seg 137-138]`.
- **R-09-1 open:** if the goods are never found and the customer agrees to nothing, nothing ends the hold — cancellation
  exists only as a negotiated outcome outside the system `[seg 103-104]`, KB `issues-and-hold.md:124`.
- **R-09-2 open:** a hold placed after the carrier was asked to collect would hold an order already physically moving —
  no source gives the machine a way to stop or reverse a parcel (KB `shipping-and-delivery-states.md:88`).
