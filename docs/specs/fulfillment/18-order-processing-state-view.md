# 18 — customer → OrderProcessingStateView

**Role:** **Read model, read-only.** The board's `order processing state` (`eventstorming.json:141`) — the customer-facing
projection of every processing state and every delivery leg. The board gives the customer **no command** in this lane:
all eight commands belong to the employee or the system (`eventstorming.json:115-124`).

**Actor:** customer — board actor `customer` (`eventstorming.json:114`), A3 (KB `README.md:28`); "there is a part, of
course, for the customer where he is notified about all those states" `[seg 136]`.

**Purpose:** show the customer every processing and delivery state, updated after each event `[seg 136-138]`.

## Evidence base — and how thin it is

- Two claims only: the customer "is notified about all those states" `[seg 136]`, and "after each event that read model
  have some new information" `[seg 137-138]` — the second applied to every area's read model (KB `README.md:77`).
- The states themselves are the six legs L1–L6 `[seg 125-127]` (KB `shipping-and-delivery-states.md:48-63`) plus the
  derived order states of element 02.
- **No vocabulary for the customer exists.** Which word the customer reads where the employee reads
  `waiting on the delivery guy` is an open question in the KB itself: "Two vocabularies for possibly one state; the
  customer-facing view must pick one" (KB `order-assignment.md:96` question 6) → **Q-18-1**.
- **No screen of this context exists in any source** — the only image in the materials is the event-storming board
  photograph (`docs/raw/materials/README.md:9`). Layout is a default (D-UI2, see 15).
- Freshness is poll-based, not push: only InPost publishes webhooks, and DPD — the reference carrier — is polling-only, so the projection is
  only as fresh as the poller (X1 and consequences 1–5, KB `shipping-and-delivery-states.md:92-112`) → **R-18-1**.

## Data shown

| Field | Source concept | Provenance |
| --- | --- | --- |
| what was ordered — product, quantity | `Order.lines` | element 01 — the customer's own order content |
| delivery target | `Order.delivery` | element 01; a pickup point is only an opaque `pointId` — no name or address is available anywhere (element 01 R-01-1, UND-16 `undecided-register.md:50`) → **default:** the code is shown, nothing more |
| payment method | `Order.payment.methodCode` | element 01 RULE-5 (opaque code); **default: hidden** — no method list exists in any source (UND-21), so nothing can be rendered as a label |
| order state | `OrderProcessing.state`, mapped to customer words | element 02; `[seg 136]`; names derived (UND-20) → **Q-18-1** |
| delivery legs with times | `Parcel.legs[].leg` (L1–L6) | `[seg 125-127]`, KB `shipping-and-delivery-states.md:54-63`, `[seg 138]` |
| hold marker | `OrderProcessing.state = onHold` | `[seg 148]`; whether the customer sees the hold at all is unstated (KB `issues-and-hold.md:121` question 6) → **Q-18-1**. **Default:** shown as a paused marker, without the kind |
| provider ID / waybill | `Parcel.carrier.waybill` | `delivery number generated` (`eventstorming.json:128`); whether the customer sees the parcel number is open (KB `shipping-and-delivery-states.md:125` question 8; the label itself carries none — `consignment-note.json:54`) → **Q-18-2**. **Default: not shown** |
| position progress | `positions[].state` | nothing says the customer sees positions — `[seg 121]` is the employee's screen. **Default: hidden**, at most an aggregate "spakowane" |

Not shown: **any money** (spec.md D5); warehouse locations or stock; the issue kind; the assignee; the pick list; internal state
names that no customer word covers.

## Actions

| Action | Effect | Precondition | Resulting event |
| --- | --- | --- | --- |
| open own order | none — read only | the order belongs to the caller | re-rendered on every order and delivery event `[seg 137-138]` |
| *(absent)* cancel, change address, change method | refused by omission | — | the board gives the customer no command in this lane (`eventstorming.json:115-124`); a change of commitment is a **different order** produced outside the system `[seg 100-107]` (element 01) |
| *(absent)* contact the courier / resend notification / report a problem | refused by omission | — | no source names any of them; issue reporting belongs to the employee `[seg 139-141]` |

## States

| Case | Behaviour |
| --- | --- |
| empty | order not found — **default:** must not distinguish "does not exist" from "not yours" (a disclosure default, no source) |
| loading | first render before the projection is computed |
| error | **default:** never fall back to a fabricated "in progress"; show the last known state with its age |
| stale | the poller has not run, or the carrier returned a status we cannot map → the leg state is unchanged (element 03 RULE-4), so the screen legitimately shows the same step for longer → **R-18-1** |
| permissions | only the customer's own order; guest orders have no account behind them (UND-30 `undecided-register.md:82`, element 01 Q-01-3) → **Q-18-2** |
| on hold | paused marker, no issue detail — **default**, dependent on Q-18-1 |

## Layout minimum — **all of this is a default (D-UI2)**

- one vertical timeline, oldest first: the order states (received → in progress → packed → handed to the carrier →
  delivered), then the delivery legs L1–L6; current step marked, later steps greyed but visible
  (the sources name the states; showing what is still to come is a default, not a claim)
- **append-only**: a state once shown is never removed and never replaced (element 03 RULE-4, `[seg 126]`)
- no percentage and no fractional progress bar — the number of legs is not fixed in any source (KB
  `shipping-and-delivery-states.md:120` question 3)
- order content summary once at the top; delivery target once; no waybill by default (Q-18-2)
- one timestamp per state = **when we received the event** `[seg 138]` — the carrier's own timestamp is not mapped anywhere
  (KB `shipping-and-delivery-states.md:122` question 5)
- no counters, no map, no tracking link (no source names any)

## What the view must never do

- **never show money** — no price, no order total, no delivery cost, no VAT, no COD amount (D5, `spec.md`)
- **never show internal warehouse locations or stock**, and never any pick-list content — locations are the pick list's
  internal data `[seg 119]`, board annotation `eventstorming.json:146`, KB `pick-list.md:11`
- **never show the issue kind verbatim** — `lost`, `destroyed`, `stolen` are internal warehouse realities with an open
  taxonomy `[seg 141]` (KB `issues-and-hold.md:23-34`, UND-22); the customer gets a paused marker at most
- **never show the assignee or any employee identity** — no source needs it and none names it
- **never show the invoicing data (NIP, address, amounts)** — that belongs to the document layer (elements 05/06)
- **never move a state backwards or drop one** `[seg 126]`, element 03 RULE-4 — a re-read of an old carrier status must not
  regress the timeline
- **never compute an ETA or a promised delivery date** — delivery time is unformulated in this whole project
  (`spec.md` R2, UND-01/UND-05); `[seg 125-127]` names legs and never a time
- **never show a raw carrier status string** — the leg mapping is unspecified (KB `shipping-and-delivery-states.md:122`
  question 5); our six leg names only
- **never show another customer's order** (default; no source grants any sharing)

## Prototype

Low-fi gallery (D-UI2, element 15): `npm run storybook` in
[`prototypes/.storybook`](prototypes/.storybook) → http://localhost:6011, kind `Fulfillment/Customer State`.
Stories, each with a screenshot: [`ready-for-delivery`](prototypes/shots/fulfillment-customer-state--ready-for-delivery.png) · [`in-transit`](prototypes/shots/fulfillment-customer-state--in-transit.png) · [`held`](prototypes/shots/fulfillment-customer-state--held.png) · [`error`](prototypes/shots/fulfillment-customer-state--error.png).

## Q / R

- **Q-18-1 open:** the customer's own state vocabulary — `processed` / `waiting on the delivery guy` `[seg 110]` describe the
  employee's list, `ready for delivery` `[seg 122]` is ours, and no material gives a customer-facing word for either;
  KB `order-assignment.md:96` question 6 says the customer-facing view "must pick one", and whether the hold is one of the
  customer's states is also unstated (KB `issues-and-hold.md:121` question 6). **Default:** one word per step, six delivery legs
  plus four order states, hold as a paused marker without the kind.
- **Q-18-2 open:** two identity questions the view cannot answer itself — is the carrier's parcel number shown to the
  customer (KB `shipping-and-delivery-states.md:125` question 8; the only label artifact carries no parcel number,
  `consignment-note.json:54`, element 03 Q-03-1), and how does a guest order reach its own view (UND-30, element 01 Q-01-3)?
  **Default:** no waybill, and guest access is not decided here.
- **R-18-1 open:** "updated after each event" `[seg 138]` is only true as often as the poller succeeds — DPD publishes no
  push and the rest of the matrix barely does (KB `shipping-and-delivery-states.md:92-112` consequences 1–5), and an unknown or
  repeated carrier status leaves the state unchanged (element 03 RULE-4). A stalled parcel therefore looks exactly like a
  slow poll on this screen; the view must not be worded to imply live tracking.
