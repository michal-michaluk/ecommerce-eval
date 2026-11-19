# 17 — resolver → IssueDashboard

**Role:** **Read model, read-only.** The board's `current issues` (`eventstorming.json:140`) and nothing else: the board
carries no resolver-side command, no issue-resolved event and no issue lifecycle — `mark problems with fulfillment`,
`issues` and `order is on hold` all sit on the employee side (`eventstorming.json:119`, `:131`, `:132`). The one exception
is neither on the board nor here: the close is recorded by the resume command (`spec.md` D8), and this view offers no write.

**Actor:** issue resolver — "some other personnel" `[seg 147]`, "someone else will see issue on his dashboard" `[seg 149]`.
He **is not a board actor**: the board carries `employee` and `customer` only (`eventstorming.json:114`), and he "may be
another warehouse employee" `[seg 149]` (A2, KB `README.md:27`; spec.md Step-2 assumption; UND-22
`undecided-register.md:63`).

**Purpose:** show the open warehouse-reality gaps so that someone can walk out and fix the warehouse.

## Evidence base — and how thin it is

- Three claims, all from one sentence each: some other personnel gets a read model with the current issues `[seg 147]`;
  the resolver goes physically to look for the missing pieces `[seg 149]`; resolution includes correcting the warehouse
  when goods sat in the wrong place `[seg 151]` — KB `issues-and-hold.md:51-60`.
- Nothing describes the dashboard: no fields, no filters, no actions, no issue state. KB states it deliberately: "There
  is no `issue` sub-state machine drawn here on purpose: the sources never name an issue lifecycle, only a read model
  called `current issues`" (KB `issues-and-hold.md:86-110`).
- When the issue cannot be fixed on the spot, everything moves **outside the system**: "we need to contact the customer
  and negotiate" `[seg 102]`, "...he will cancel the order entirely, or basically we will take any item from the order
  and deliver without it" `[seg 103]`, "but that is done outside of the system" `[seg 104]`, "after manual scenario, we
  basically have different orders, so adjusted order" `[seg 107]` (KB `issues-and-hold.md:62-84`).
- **No screen of this context exists in any source** — the only image in the materials is the event-storming board
  photograph (`docs/raw/materials/README.md:9`). Layout is a default (D-UI2, see 15).

## Data shown

| Field | Source concept | Provenance |
| --- | --- | --- |
| processing reference | `OrderProcessing.processingId` | element 02, spec.md D4 — no order number exists (UND-20) |
| issue kind | `FulfillmentIssue.kind` — **open taxonomy** | lost / destroyed / stolen `[seg 141]` (T1–T3, KB `issues-and-hold.md:27-29`); wrong location `[seg 150-151]` (T4, `:30`); the expert closes with "whatever" `:34`. `damaged` is **not admissible** — T6 `:32`, appears only in the superseded doc |
| product | issue's position → `productNumber` + `Order.lines[].name` | `[seg 140]` "we have two last units, but we cannot find them"; element 04 — **default:** a resolver needs the piece to look for |
| reporter | `FulfillmentIssue.reportedBy` | `[seg 139-141]`, element 02 example |
| reported at / age | issue timestamp | **no source**; needed to order rows. No timeout, deadline or escalation exists (KB `issues-and-hold.md:119` question 4) — **default**, no question id |
| order state | `OrderProcessing.state` = `onHold` | `[seg 148]`, event `order is on hold` (`eventstorming.json:132`); H1: the whole order, not the position — KB `issues-and-hold.md:36-49` |
| assignee | `OrderProcessing.assignee` | element 02 RULE-1 — **default:** the resolver needs to know whom to hand back to; whether a hold releases the assignee is open (KB `issues-and-hold.md:120` question 5, `order-assignment.md:97` question 7) → element 09 Q-09-2 |
| quantity missing / position reference | not stated | KB `issues-and-hold.md:118` question 3 — the row cannot say "1 of 2" today → element 04 Q-04-5 |
| location | not stated | KB `issues-and-hold.md:123` question 8 — is an issue about an order or a warehouse location? → Q-17-2. **Default: no location column** (the pick list owns locations) |

Not shown: any money (spec.md D5); the customer's contact data and the negotiation — out of system `[seg 104]`; the pick route;
delivery legs (view 18).

## Actions

| Action | Effect | Precondition | Resulting event |
| --- | --- | --- | --- |
| read the list | none | the caller is personnel, not the assignee (`[seg 147]` "some other personnel") | re-rendered on `issues` and `order is on hold` `[seg 137-138]` |
| open an order's processing state (read-only) | shows element 02 data — no scan, no mark, no claim | — | **default**: a read-only detail, since the resolver must know what is held — **default:** a read-only detail — the resolver must know what is held, and `Q-17-2` keeps only the order-vs-location question open |
| *(absent)* resolve / close an issue | refused by omission | — | no command, no event, no lifecycle exists here — KB `issues-and-hold.md:86-110`; the close is recorded by the resume command (`spec.md` D8), not from this view |
| *(absent here)* resume the order from hold | lives on the processing resource, not on this dashboard | every open issue named in `closedIssueIds` (element 09 H5/H6) | `POST /processing/{id}/hold/resume` — introduced by `spec.md` D7, the close by D8; **`Q-17-1` closed by D7/D8** (employee and resolver scopes) |
| *(absent)* correct the warehouse | refused by omission | — | "we need to find it and correct the warehouse" `[seg 151]`; **which** system is corrected and by whom is unstated (KB `issues-and-hold.md:126` outside-knowledge note, UND-22) |
| *(absent)* start the adjustment | refused by omission | — | negotiation and outcome are out of system `[seg 102-107]` |

The action table is deliberately almost empty — **the dashboard has no write side in any source.** That is the finding,
not an omission of this spec.

## States

| Case | Behaviour |
| --- | --- |
| empty | no open issues — wording is a **default**, no source |
| loading | read model not yet projected |
| error | **default:** never render an empty list on error — show the last known issues plus their age. A dashboard that hides issues behind a failure tells the resolver the warehouse is fine |
| permissions | `fulfillment-resolver` only — `GET /issues` answers **403** for an employee token (element 11 auth matrix, `prototypes/fulfillment-api.hurl`). The board has no resolver actor (F3-1: no `fulfillment`/`picker` role exists in the realm); whether the reporter also sees his own issue here is unstated → **Q-17-2** |
| rows that leave | only through the resume command (`spec.md` D8) — never from this view → **R-17-1** |

## Layout minimum — **all of this is a default (D-UI2)**

- one flat list, **grouped by order** — an issue holds the whole order (H1, KB `issues-and-hold.md:36-49`), so rows per
  order must be readable together
- per row: processing reference, issue kind, product, reporter, age since report
- **no counters, no charts, no SLA badges, no colour thresholds** — no number, deadline or escalation exists
  (KB `issues-and-hold.md:119` question 4)
- no actions column, with one line of explanatory text instead of a disabled button: resolution is physical `[seg 149-151]`
  and the fallback is out of system `[seg 104]` (`ui-prototype.md` quality rule: a blocked action always carries its
  reason)
- oldest first (no source for ordering; default)

## What the view must never do

- **never offer a resolve / close / dismiss button** — no command, no event and no lifecycle exists
  (KB `issues-and-hold.md:86-110`, UND-22)
- **never claim, scan, pack or mark an order ready** — the resolver reads `[seg 147]`; those are the assignee's commands
  `[seg 111-122]` and the guard is exclusive `[seg 114]`
- **never show an issue kind no source names** — no `damaged` (T6, KB `issues-and-hold.md:32`; the superseded doc it
  comes from is rejected, KB `sources-and-references.md:51`)
- **never present the list as closed or complete** — the taxonomy is open by the expert's own "whatever" `[seg 141]`
- **never edit the order, negotiate, or start the adjustment** — that is outside the system `[seg 104-107]`
- **never show money or a compensation amount** (spec.md D5)
- **never present `on hold` as blocking delivery, cancelling anything or releasing the assignee** — nothing in any source
  says it does (H4, KB `issues-and-hold.md:47`; UND-22)
- **never surface the customer's personal data** for the negotiation — that conversation happens off-system `[seg 102-105]`

## Prototype

Low-fi gallery (D-UI2, element 15): `npm run storybook` in
[`prototypes/.storybook`](prototypes/.storybook) → http://localhost:6011, kind `Fulfillment/Issue Dashboard`.
Stories, each with a screenshot: [`open-issues`](prototypes/shots/fulfillment-issue-dashboard--open-issues.png) · [`empty`](prototypes/shots/fulfillment-issue-dashboard--empty.png) · [`error`](prototypes/shots/fulfillment-issue-dashboard--error.png).

## Q / R

- **Q-17-1 closed by `spec.md` D7/D8:** the issue-and-hold exit exists — closing an issue is a fact recorded by the resume
  command (`closedAt`), which restores the state held before the hold; the dashboard stays read-only. Residual
  questions moved to their own elements: assignee release → element 09 Q-09-2, issue quantity → element 04 Q-04-5,
  location → Q-17-2.
- **Q-17-2 open:** is an issue tied to an order or to a warehouse location? The board has no resolver actor
  (`eventstorming.json:114`); he may be another warehouse employee `[seg 149]`; a wrong-location issue is a warehouse
  correction that exists independently of the order `[seg 151]` (KB `issues-and-hold.md:60`, `:123`). The **role** half of
  this question is no longer open: `fulfillment-resolver` is the only scope that may read this list (element 11,
  `spec.md` D7). **Default:** order-scoped rows, no location column, read-only detail.
- **R-17-1 open:** **nothing obliges anyone to work this list** — no timeout, escalation or SLA exists in any source, and
  every exit other than the resume command is outside the system (physical recovery `[seg 149-151]`, negotiation
  `[seg 102-105]`, the adjusted order `[seg 107]`, KB `issues-and-hold.md:62-84`). Rows leave only when a human names them
  to the resume command (`spec.md` D8), so the list grows with every hold.
