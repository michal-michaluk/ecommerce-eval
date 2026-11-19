# 16 — employee → PickWorkstation

**Role:** **Read models + commands on one screen.** The board's `pick list` (`eventstorming.json:138`) and
`current state of order processing` (`eventstorming.json:139`) rendered together, as `[seg 121]` demands; commands
`scan packed product` (`eventstorming.json:117`), `mark problems with fulfillment` (`:119`) and the happy-path
`mark done / ready for delivery` (`:118`), whose guard is element 08, not this view.

**Actor:** the employee **assigned** to that order — assignment is what hands over the list ("we want to assign an
employee, and the employee gets the pick list" `[seg 117]`), and only one person may hold it `[seg 114]`.

**Purpose:** walk the warehouse and confirm each position by scanning, on the one screen that also shows how far the
processing got.

## Evidence base — and how thin it is

- Four properties, and nothing else: one screen shows the pick list and the processing state together **W1**; the screen
  distinguishes positions already picked from positions already packed **W2**; the employee is the actor for scanning and
  marking **W3**; no paper or handheld is described **W4** — KB `picking-and-packing.md:11-18`, all from `[seg 120-121]`.
- The pick list's content is five rows **P1–P5** and the fifth is "claimed, not specified" — KB `pick-list.md:5-13`.
- **No screen of this context exists in any source**; the only image in the materials is the event-storming board
  photograph (`docs/raw/materials/README.md:9`). Every layout choice below is a default (D-UI2, see 15).
- The board itself flags doubt about this very screen: "packed product / scan packed product stickies overlap on the
  board" (`eventstorming.json:148`).

## Data shown

| Field | Source concept | Provenance |
| --- | --- | --- |
| position product | `positions[].productNumber` + `Order.lines[].name` | **P1** `[seg 118]`, KB `pick-list.md:9` |
| quantity to take | position / line quantity | **P2** `[seg 130]`, `[seg 133-134]`, KB `pick-list.md:10` |
| location(s) on the warehouse | location resolved through the warehouse port (element 13) | **P3** `[seg 119]`, `[seg 129]`, board annotation "locations of products on warehouse" (`eventstorming.json:146`), KB `pick-list.md:11` |
| walk order (row order) | `PickListCalculation` output (element 10) | **P5** `[seg 118-119]` — optimisation claimed, criterion missing (`spec.md` Q2, UND-23 `undecided-register.md:64`) |
| position state | `positions[].state` — pre-scan (unnamed, PS1), `picked` (PS2), `packed` (PS3) | `[seg 121]` W2, `[seg 120]`, KB `picking-and-packing.md:22-28`; `position packed` (`eventstorming.json:129`) |
| processing state of the order | `OrderProcessing.state` | `[seg 121]` W1, element 02 |
| positions packed `n/m` | derived from position states | element 02 RULE-3 — a **derived** counter, not a source field |
| assignee | `OrderProcessing.assignee` | `[seg 114]`, element 02 RULE-1 — **default:** shown so the employee sees he owns the screen |
| stock per location | warehouse `stockAt` per location (element 13) | **P4** `[seg 133]` — **default and optional**: the expert says "theoretically" (KB `pick-list.md:15`), urgency use at `[seg 134]` |
| scan input | — (an input, not data) | the only scan command is `scan packed product` (`eventstorming.json:117`); the confirmed unit is one position, not the order **C1/C2** — KB `picking-and-packing.md:58-65` |

Not shown: any money (spec.md D5); delivery legs and waybill (view 18); the customer's invoicing data — nothing on the pick list
needs it (KB `pick-list.md:5-13`); the calculation's internals (element 10).

## Actions

| Action | Effect | Precondition | Resulting event |
| --- | --- | --- | --- |
| scan one position (`scan packed product`) | position `picked`; order `assigned → picking` on the first confirmation (element 02) | caller is the assignee; position not already confirmed; scanned code matches the position | none — the board's only position event is `position packed` (`eventstorming.json:129`), emitted by the packing step below (**Q-16-1**) |
| mark picked positions packed (bulk) | `picked → packed` for the marked positions (`POST /processing/{id}/packing`, element 11) | positions are `picked` | `position packed` per position — **default**, no source names an action for this step **Q-16-1** |
| mark done / ready for delivery | order `packed → readyForDelivery` + collection requested on the parcel (two effects, `[seg 122]`) | element 08 `ReadyForDeliveryDecision` — all positions packed, no blocking issue; a hold blocks it (element 02 RULE-4/RULE-7, element 02 Q-02-4) | `ready for delivery` (`eventstorming.json:130`) |
| report a problem (`mark problems with fulfillment`) | order → `onHold`, issue recorded | order in `picking` or `packed` — the machine has no hold edge from `pending`/`assigned` (element 02) | `issues` (`:131`) then `order is on hold` (`:132`) |
| leave the workstation | none | caller is the assignee | none — the claim persists; assignment has no timeout in any source (KB `order-assignment.md:87-97` question 2) |

`[seg 139]` puts the fallback **next to** the happy path — both actions belong on this screen, and the problem button is
available at any moment during picking, not only at the end.

## States

| Case | Behaviour |
| --- | --- |
| empty | an order with positions cannot be empty (element 02 RULE-3); the honest empty case is an order already fully packed or no longer assigned to the caller — **default:** processing state shown, no rows |
| loading | the pick list is computed on claim (element 10) and locations come over the warehouse port — a real wait, no latency budget exists in any source |
| error — pick list unavailable | scanning is refused, with the reason shown; no offline queueing (the warehouse port forbids inventing state, element 13) |
| error — scanned code is not this position's product | no source describes it (KB `picking-and-packing.md:69-82` question 6); **default:** refuse the confirmation and surface the `report a problem` action, which is exactly the wrong-location situation `[seg 150-151]` |
| duplicate scan of a confirmed position | no second confirmation, no state change — idempotency is mandatory (element 02 RULE-6); no undo exists (KB `picking-and-packing.md:69-82` question 5) |
| `stock changed` during the walk | list may be stale `[seg 133-135]`; **default:** refresh only on explicit action or on re-entry, never reorder under a walking employee (gap **G6**, KB `pick-list.md:19-30`) |
| order on hold | all confirmations and `mark done` refused (element 02 RULE-7, element 09); resume is not on this screen and not on view 17 (read-only, element 17:54) — it is the processing resource, `POST /processing/{id}/hold/resume` (element 11, `spec.md` D7) |
| permissions | assignee only `[seg 114]`; **no role exists** (F3-1) — **default:** a non-assignee opening the order sees it read-only |

## Layout minimum — **all of this is a default (D-UI2)**

- one screen, two regions side by side or stacked: **positions** (product, quantity, location, state) and **processing
  state** (order state, `n/m packed`, assignee) — W1 requires both on one screen, it does not say where
- **barcode scanner as keyboard input, always-focused scan field** — **default**: no source names a symbology, a scanner
  or a device (KB `picking-and-packing.md:18` W4 "no paper or handheld is described", `:69-82` question 4); a keyboard-emulating
  scanner needs no driver and no extra device contract
- scan submits on Enter/CR; every other action is a button, so the loop is hands-free
- next position highlighted; confirmed rows stay visible and are greyed, not removed — W2 needs `picked` and `packed`
  distinguishable at the same time (KB `picking-and-packing.md:20`)
- `mark done / ready for delivery` and `report a problem` both always visible `[seg 139]`
- **no walk-order enforcement**: an out-of-order scan is accepted — whether the walk order is advisory or enforced is
  undecided (KB `pick-list.md:32-42`, question 4)
- stock-per-location panel collapsible (P4, "theoretically" — KB `pick-list.md:15`)

## What the view must never do

- **never show money** — no price, no total, no weight (spec.md D5)
- **never let a non-assignee confirm, pack or mark** (`order-assignment.md:5-16` rules 3/4, `[seg 114]`)
- **never edit a line** — no quantity change, no product substitution, no line deletion: the order is immutable
  (`[seg 98-99]`, element 01 RULE-2)
- **never mark the order done on its own** — completion is the employee's declaration, not a computed consequence of
  scans **C4** (KB `picking-and-packing.md:65`); no source requires blocking it while positions are unscanned
  (KB `picking-and-packing.md:69-82` question 8, carried as element 08's decision)
- **never infer a quantity from the number of scans** — serial-level vs quantity-level scanning is open
  (KB `picking-and-packing.md:69-82` question 2); default is one scan per position with the declared quantity
- **never reorder the route behind the walking employee**, and never recompute silently on `stock changed` (G6)
- **never show the customer's invoicing data or address** — the pick list needs product, quantity, location only
  (KB `pick-list.md:5-13`)
- **never show the delivery state or the waybill** — carrier legs are view 18's surface and the customer's (element 03)

## Prototype

Low-fi gallery (D-UI2, element 15): `npm run storybook` in
[`prototypes/.storybook`](prototypes/.storybook) → http://localhost:6011, kind `Fulfillment/Pick Workstation`.
Stories, each with a screenshot: [`scan-idle`](prototypes/shots/fulfillment-pick-workstation--scan-idle.png) · [`position-picked`](prototypes/shots/fulfillment-pick-workstation--position-picked.png) · [`mixed`](prototypes/shots/fulfillment-pick-workstation--mixed.png) · [`all-packed`](prototypes/shots/fulfillment-pick-workstation--all-packed.png) · [`blocked-on-hold`](prototypes/shots/fulfillment-pick-workstation--blocked-on-hold.png).

## Q / R

- **Q-16-1 open:** what one scan produces — `picked`, `packed`, or both. `[seg 120]` says the scan confirms the pack and the
  positions are then marked "as packed"; `[seg 121]` shows both `picked` and `packed` on the same screen; no second scan
  is described anywhere. Three readings, all in KB `picking-and-packing.md:46-56` (R-A/R-B/R-C). **Default taken here:**
  R-A — one scan = `pick` (position → `picked`), a separate bulk action sets `packed`, matching element 02's two commands;
  KB itself calls R-A "the one the sources support least badly". R-A is the operator's decision, not a reading of the
  source, and the executable contract asserts it: one scan → `picked`, then `POST /processing/{id}/packing` → `packed`
  (`prototypes/fulfillment-api.hurl`, element 11).
- **Q-16-2 open:** what the employee submits when reporting a problem — the issue record's fields (product, photo, free text)
  and its kinds are all unstated (KB `issues-and-hold.md:112-126` question 3, UND-22
  `undecided-register.md:63`). **Default:** one of the named kinds (lost / destroyed / stolen / wrong location, `[seg 141]`,
  `[seg 150-151]`) plus free text — never a closed list, the expert ends his own enumeration with "whatever" `[seg 141]`.
- **R-16-1 open:** the position state **before scanning has no name in any source** (PS1, KB `picking-and-packing.md:26`) and
  must not borrow the order-level word `pending`. Whatever label this screen shows is invented vocabulary in a context
  whose vocabulary is already undefined (UND-20) — and it leaks: the same label reaches the customer view (18) unless it
  is deliberately kept internal.
