# 15 — employee → OrdersListView

**Role:** **Read model + one command.** The board's `orders list` (KB `fulfillment/order-processing/README.md:69`,
`eventstorming.json:137`) with `assign to order` (`eventstorming.json:116`) on each row. It is the entry point of the
exclusivity guard (element 07) and the only place a claim can be made.

**Actor:** warehouse employee — board actor `employee` (`eventstorming.json:114`), A1 (KB `README.md:20-26`), "some
employee on the warehouse can assign himself or herself" `[seg 111]`.

**Purpose:** show every current order with its processing state, and let exactly one employee claim one of them.

## Evidence base — and how thin it is

- The board names the read model and assigns it **no consumer**; the employee is inferred from `[seg 110-111]`
  (KB `README.md:65-77`).
- **No screen of this context exists in any source.** The only image in the materials is the event-storming board
  photograph (stickies) — `docs/raw/materials/README.md:9`; no screenshot, layout or wireframe of a fulfillment screen
  is in Tier A or B.
- The only attributes any source gives the list: "we always see the current orders list, and basically some status it is
  processed or it is pending, or it is already waiting on the on the delivery guy or whatever" `[seg 110]` — the "or
  whatever" admits it is not exhaustive (KB `order-assignment.md:28`).
- Therefore: **data + actions + states are specified; every layout choice below is a default (D-UI2), not a fact.**

## Read-model mapping — D-UI1

| Board read model | Board line | Consumer | View |
| --- | --- | --- | --- |
| `orders list` | `eventstorming.json:137` | A1, inferred | **15** |
| `pick list` | `eventstorming.json:138` | A1 | 16 |
| `current state of order processing` | `eventstorming.json:139` | A1, inferred | 16 — same screen as the pick list `[seg 121]` |
| `current issues` | `eventstorming.json:140` | A2 | 17 |
| `order processing state` | `eventstorming.json:141` | A3, inferred | 18 |

**Judgement on the near-identical pair.** `current state of order processing` (`:139`) and `order processing state`
(`:141`) are **not** the same read model here. KB `README.md:75` says nothing in any source distinguishes them and they
may be one model written twice; this spec separates them on consumer and content — the employee's one screen carries the
pick list *and* the processing state (W1, KB `picking-and-packing.md:15`), while the KB entry that owns the customer view
calls `order processing state` "the customer-facing view of all processing states" (KB `README.md:72`), and the delivery
file treats that name as the customer-facing read model that must not assume push latency (KB
`shipping-and-delivery-states.md:92-112` consequence 5). Result: five board read models → four views, none dropped, none invented.
If the operator rules they are one model, 16 and 18 merge into one view with two permission scopes.

**D-UI2 — low-fi gallery.** `specification/references/ui-prototype.md` makes a gallery mandatory when UI is in scope. Because no source contains a screen of this context, the four views are prototyped low-fi and every layout choice stays a marked default: [`prototypes/.storybook`](prototypes/.storybook) — 18 stories, `npm run storybook` on 6011, screenshots in [`prototypes/shots`](prototypes/shots). The stories carry data + actions + states only; the layout is still a default, not a fact.

## Data shown

| Field | Source concept | Provenance |
| --- | --- | --- |
| processing reference | `OrderProcessing.processingId` | spec.md D4 (synthetic id minted at intake) + element 02 example — **no order number exists anywhere** (UND-20, KB `domain-language/undecided-register.md:54`) |
| state (chip) | `OrderProcessing.state` | element 02 state machine; every name is **derived** — provenance table KB `order-assignment.md:50-72`; the board carries events, not states (UND-20) |
| status word | transcript S1 `pending` / S2 `processed` / S3 `waiting on the delivery guy` | `[seg 110]`, KB `order-assignment.md:22-26`; S2 has no defined boundary (KB `order-assignment.md:95` question 5) — **default:** element-02 state drives the chip, S1–S3 supply the label wording |
| assignee | `OrderProcessing.assignee` | `[seg 111-114]`, KB `order-assignment.md:5-16` rules 1–4 |
| position progress `n/m packed` | `OrderProcessing.positions[].state` | `[seg 120-121]`, element 02 RULE-3; both conditions visible — W2, KB `picking-and-packing.md:16` |
| hold marker | `OrderProcessing.state == onHold` — never "an issue is present" | `[seg 148-149]`, element 02 (`picking → onHold`, `packed → onHold`); the hold is the state itself, not a flag beside it (09 RULE-2) |
| recipient name | `Order.invoicing.buyerName` | element 01 example — **default** (a row needs a human handle; no source names a column) → **Q-15-1** |
| placed at | `Order.placedAt` | element 01 example — **default**, used only to order rows |

Not shown: any money (spec.md D5); stock or warehouse locations (`pick-list.md:5-15` P3 belongs to view 16); delivery legs (view
18); the pick route (element 10).

## Actions

| Action | Effect | Precondition | Resulting event |
| --- | --- | --- | --- |
| claim (`assign to order`) | `assignee := caller`, state `pending → assigned` | order unclaimed **and** not on hold — a foreign claim on `onHold` is refused (element 07 DT-07-3); caller is a warehouse user | `employee assigned to order fulfilment` (`eventstorming.json:127`); the row re-renders `[seg 137-138]` |
| open workstation | navigates to 16 for this order | caller is the assignee | none |
| refresh on event | row re-rendered | — | consumes `ordered`, `employee assigned to order fulfilment`, `position packed`, `ready for delivery`, `issues`, `order is on hold`, `delivery state notification` (`eventstorming.json:126-134`, `[seg 137-138]`) |
| *(absent)* unassign / release / transfer | refused by omission | — | no source names one — KB `order-assignment.md:87-97` questions 1–3 |
| *(absent)* report an issue | not offered here | — | an issue can only be reported once picking started: the machine has no hold edge from `pending`/`assigned` (element 02, KB `order-assignment.md:50-72`) |

A claim by a second employee is **refused on the spot, never queued** — "only one person can be assigned at a time"
`[seg 114]`, purpose stated twice: no double processing `[seg 112]`, no goods sent twice `[seg 113]` (element 07).

## States

| Case | Behaviour |
| --- | --- |
| empty | no current orders — wording is a **default**, no source |
| loading | read model not yet computed; no cadence exists anywhere (query volume/latency absent from every source, `spec.md` Step 2) |
| error | read model unavailable → the claim action is refused, not queued: a claim must stay atomic (element 07) |
| permissions | **no role exists** — F3-1: no `fulfillment`/`picker` Keycloak role in the realm; who may claim is only "some employee" `[seg 111]` → Q-15-2 |
| claimed by someone else | visible (the list is global, `[seg 110]`) but not actionable, per KB `order-assignment.md:5-16` rule 4 — a second employee cannot take it. Showing **who** holds it is a disclosure default, not a source rule |
| on hold | **refused** — element 07 DT-07-3 refuses a foreign claim while held (`Q-07-4`); the row shows the holder and the reason |

## Layout minimum — **all of this is a default (D-UI2)**

- one table, one row per order; **no detail panel** — details live in 16
- column order: state chip, processing reference, recipient, position progress, assignee, placed at
- one action per row, in the row: `claim` (unclaimed) or `open` (mine); a row held by another employee shows the
  assignee instead of a button, never a disabled `claim` without a reason
- no filter, no search, no pagination, no sort control — no criterion exists in any source (Q-15-1)
- hold shown as a marker on the chip, not as a separate column
- row count / counters: none (no throughput number exists in any source)

## What the view must never do

- **never present a claimed order as claimable**, and never hide who holds it (`order-assignment.md:5-16` rules 3/4)
- **never queue, wait-list or "notify me" a claim** — the guard is exclusive, not advisory `[seg 114]`
- **never show money** — no price, total, currency or VAT column (D5, `spec.md`)
- **never edit the order**, its lines or quantities (element 01 RULE-2, `[seg 98-99]`)
- **never invent a status vocabulary** — no `CONFIRMED`, no nine-value enum, no state word absent from the derived set
  (UND-20, KB `undecided-register.md:54`)
- **never offer unassign / release / transfer** — no source names one (`order-assignment.md:87-97`)
- **never show warehouse locations, stock or the pick route** — that is view 16's surface (`pick-list.md:5-15` P3)
- **never compute anything** about the order: no totals, no weights, no ETA (`spec.md` R2, D5)

## Q / R

- **Q-15-1 open:** which fields identify a row for a human — no source names a single list column, and `processed`
  `[seg 110]` has no boundary (KB `order-assignment.md:95` question 5). Default: recipient name + placement time, element-02 state.
- **Q-15-2 open:** scope, filter and role — "we always see the current orders list" `[seg 110]` reads as a global list, but
  whether an employee sees only his own, whether a state filter exists, and who may claim at all (no `picker` role: F3-1)
  are unanswered. Default: global list, no filter, any authenticated warehouse user may claim. A hold is **not** part of
  this question — a foreign claim on `onHold` is refused (element 07 DT-07-3).
- **R-15-1 open:** a claimed order has **no release path** — no unassign, no timeout, no transfer (`order-assignment.md:87-97`
  questions 1–3); an absent employee blocks the order and the only recovery named in any source is the manual, out-of-system
  path `[seg 104-107]`. This list will accumulate rows nobody can work.
