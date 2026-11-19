# 10 — PickListCalculation

**Role:** **Calculation** — derived, pure, stateless. It reads `OrderProcessing` (Process, element 02) and the warehouse
port `stockAt` (element 13), and produces the `pick list` read model (`eventstorming.json:138`) that
`GET /processing/{processingId}/pick-list` serves (element 11). It has no Editor, stores nothing, and emits no event:
run it twice on the same inputs and the same list comes out.

**Definition:** the ordered walk an assigned employee performs — for each order position the product, the quantity to
take, the warehouse location(s) to take it from and how much is free at each. "that's the optimized list, which product
needs to be taken from the warehouse and put into the package" `[seg 118]`, with "locations of products on warehouse"
(`eventstorming.json:146`) and per-place quantities "how much of those items is in which places" `[seg 133]`.

The board puts `picklist calculation policy` in this lane (`eventstorming.json:143`), so the calculation is ours; the
*location data* is not — it comes from the warehouse port, because "we integrate with the warehouse to basically get the
location of the product" `[seg 129]`.

## Inputs / Output

| Input | Origin | Source |
| --- | --- | --- |
| `processingId`, `state`, `assignee` | Business concept | element 02 |
| positions — `productNumber`, `quantity`, `state` | Business concept | element 02 RULE-3 |
| product `name` (display only) | Business concept | element 01 |
| `List<StockAtLocation>` per product | Integration (external) | port `stockAt`, element 13 · `docs/api/warehouse-api.md:29-30`, `:42` |
| location *code* string, ordered lexicographically | Integration (external) — the only ordering key the warehouse gives us | `docs/api/warehouse-api.md:42`; format unowned (`UND-23`) |

Output — one `PickList` value object per request, and one `Stop` per (position, location) pair actually walked:

| Value object | Values | Origin |
| --- | --- | --- |
| `PickList` | `processingId`, `generatedAt`, `stops[]`, `shortfalls[]` | Calculation (this element) |
| `Stop` | `walkOrder`, `productNumber`, `name`, `location`, `quantityToTake`, `available`, `reserved`, `positionState` | Calculation, except `available`/`reserved` per location which are **Integration** (`stockAt`) |
| `Shortfall` | `productNumber`, `missingQuantity` | Calculation |

No price, amount, currency or total appears in either value object (`spec.md` D5, element 01 RULE-8).

## Example — the `75423` order, one unit, one location

```yaml
processingId: "OP-2026-0001"
generatedAt: "2026-11-19T09:41:03Z"
stops:
  - walkOrder: 1
    productNumber: "75423"
    name: "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423"
    location: "M-02-01"
    quantityToTake: 1
    available: 4
    reserved: 0
    positionState: pending
shortfalls: []
```

```yaml
# the same product, quantity 3, split across two locations (default split rule, DEFAULT-2);
# the list as generated at assignment, before the first scan — element 02 shows this same
# processing later, on hold, with its positions packed
processingId: "OP-2026-0002"
generatedAt: "2026-11-19T09:44:12Z"
stops:
  - { walkOrder: 1, productNumber: "75423", name: "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423", location: "M-01-03", quantityToTake: 1, available: 1, reserved: 0, positionState: pending }
  - { walkOrder: 2, productNumber: "75423", name: "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423", location: "M-02-01", quantityToTake: 2, available: 4, reserved: 0, positionState: pending }
shortfalls: []
```

`M-01-03` / `M-02-01` are **illustrative location codes only** — no source defines a code format, a zone/aisle/shelf
model or a warehouse topology (KB `fulfillment/order-processing/pick-list.md:19-30`, `UND-23`). Every other value is the
`75423` order of element 01 and the processing of element 02.

## Algorithm

```text
PickList(processing, at) =
    stops = []
    for p in positions(processing) where p.state = pending
        places = [s for s in stockAt(p.productNumber) where s.available > 0]
                 ordered by s.code ascending                        # DEFAULT-1, DEFAULT-2
        left = p.quantity
        for s in places while left > 0
            take = min(left, s.available)
            stops.append(Stop(p.productNumber, s.code, take, s.available, s.reserved))
            left = left - take
        if left > 0
            shortfalls.append(Shortfall(p.productNumber, left))
    stops ordered by (code ascending, productNumber ascending) → walkOrder = 1..n   # DEFAULT-1
    PickList(processing.id, at, stops, shortfalls)

units      : integer units of product — order quantities are integers ≥ 1 (element 01 RULE-4); no rounding anywhere
determinism: total order (code, then productNumber); equal inputs ⇒ equal list
undefined  : port failure ⇒ no list at all, never a partial one (decision table DT-9)
```

### Defaults — labelled, because the optimisation is not specified

| # | Default | Why it is a default, not a fact |
| --- | --- | --- |
| DEFAULT-1 | **one pass per list, ordered by location code ascending**; the list is a snapshot taken at `generatedAt` | "optimized" is claimed twice `[seg 118-119]` and never operationalised: no criterion, no topology, no start point, no batching rule — KB `fulfillment/order-processing/pick-list.md:19-30` (G1–G6), `UND-23`. This is the default **accepted in `spec.md` Q2**; the KB's warning against inventing a routing heuristic (KB `pick-list.md:42`) is respected precisely because nothing beyond a code-ordered pass is claimed |
| DEFAULT-2 | when a product sits in several places, quantity is taken in the same ascending-code order as the walk | the sources say a product may be in several places `[seg 133]` but never say how a quantity is split (KB `pick-list.md:29`, G5; `Q-10-1`) |
| DEFAULT-3 | a generated list is **not** recomputed when `stock changed` arrives; a new list is a new snapshot | the sources make stock live during picking `[seg 133-135]` but never say the list is regenerated (KB `pick-list.md:30`, G6; `Q-10-2`) |

### Variants and configuration

| Variant | Selected by | Rules | Configuration source |
| --- | --- | --- | --- |
| `location-code` (**default**) | no match | DEFAULT-1 + DEFAULT-2 | — |
| `scarcity-first` | config `fulfillment.picklist.order = scarcity-first` | a stop whose location holds the last units of a product is walked before stops for the same product elsewhere `[seg 134]` | config key `fulfillment.picklist.order` |
| `no-split` | config `fulfillment.picklist.split = single-location` | a position is listed only if one location covers the whole quantity, else it becomes a `Shortfall` | config key `fulfillment.picklist.split` |

Selection is ordered, first match wins, and `location-code` is the fallback for every dimension. No *better* variant can
be specified than these: a real optimisation needs the criterion and the topology that `UND-23` leaves open, and
inventing one is exactly what the KB forbids ("do not adopt a routing heuristic before 1 and 2 are answered", KB
`pick-list.md:42`).

### Executable scenarios

| Scenario | product | qty | stock per location (`code: available/reserved`) | position | expected |
| --- | --- | --- | --- | --- | --- |
| S1 | 75423 | 1 | `M-02-01: 4/0` | pending | 1 stop, `M-02-01`, take 1, walkOrder 1, no shortfall |
| S2 | 75423 | 3 | `M-01-03: 1/0`, `M-02-01: 4/0` | pending | 2 stops: `M-01-03` take 1, `M-02-01` take 2 |
| S3 | 75423 | 1 | `M-02-01: 0/0` | pending | 0 stops, `Shortfall(1)` |
| S4 | 75423 | 2 | `M-02-01: 0/3` | pending | 0 stops, `Shortfall(2)` — `available` gates the stop, not `reserved` |
| S5 | 75423 | 1 | `M-02-01: 4/0` | `packed` | 0 stops — the position is already in the box |
| S6 | 75423 | 3 | `M-01-03: 1/0`, `M-02-01: 1/0` | pending | 2 stops, take 1 + 1, `Shortfall(1)` |
| S7 | 75423 + 12345 | 1 + 1 | 75423 at `M-02-01: 4/0`, 12345 at `M-01-03: 2/0` | both pending | `M-01-03` walkOrder 1, `M-02-01` walkOrder 2 — the walk interleaves products |
| S8 | 75423 + 12345 | 1 + 1 | both products at `M-02-01` | both pending | 2 stops with equal code, `75423` first (productNumber tie-break), walkOrder 1 and 2 — stops are never merged |
| S9 | 75423 | 1 | `stockAt` fails | pending | no list; the failure surfaces, no partial list |
| S10 | 75423 | 1 | `M-02-01: 3/1` | pending, order `onHold` (issue on another position) | 1 stop, take 1, `available 3`, `reserved 1` — the hold does not change what is on the shelf |

## Domain rules

- **RULE-1 One list per processing.** The command is per order `[seg 117]`; batching several orders into one walk is not
  in any source (KB `pick-list.md:28`, G4) and no variant implies it.
- **RULE-2 Positions come from the order, quantities are the order's.** Every `pending` position appears; Σ
  `quantityToTake` + `Shortfall.missingQuantity` = the position quantity, always (element 02 RULE-3, element 01 RULE-4).
- **RULE-3 Locations are the warehouse's.** No location is stored, invented, normalised or reordered structurally; a code
  is an opaque string used verbatim (`docs/api/warehouse-api.md:42`). Owning a location model would mean owning the
  warehouse layout (`UND-23`).
- **RULE-4 `available` gates a stop, `reserved` only informs it.** A location with `available = 0` is not walked even when
  `reserved > 0`; both numbers come from the port and are never derived from each other (element 13, KB
  `integrations/warehouse-inventree/port-constraints.md:35-45`).
- **RULE-5 The walk order is advisory.** Nothing blocks a scan out of sequence: the scan confirms a *position*, not a
  stop (KB `fulfillment/order-processing/picking-and-packing.md:58`), and no source makes order enforcement a rule (KB
  `pick-list.md:39`).
- **RULE-6 Stops are never merged.** One stop per (position, location); merging two products at one shelf would make the
  scan ambiguous — the workstation confirms positions, not shelves (element 16).
- **RULE-7 No re-planning mid-walk.** A list handed to an employee stays as printed until a new list is requested
  (DEFAULT-3); a `stock changed` arrival does not silently rewrite a screen being read.
- **RULE-8 All or nothing.** A port failure produces no list at all; a partial list would send an employee walking on
  incomplete data (DT-9).
- **RULE-9 No money, ever.** The pick list carries quantity, place and state only (spec.md D5).

## Decision table — edge cases

| # | Condition | Outcome | Rule |
| --- | --- | --- | --- |
| DT-1 | `available = 0` at every location of a product | no stops; `Shortfall(quantity)`; the position stays visible so the gap is not hidden | RULE-2, RULE-4 |
| DT-2 | one location covers the whole quantity | one stop, `quantityToTake = quantity` | RULE-2 |
| DT-3 | several locations together cover it, none alone | split in ascending code order until `left = 0` | DEFAULT-2 |
| DT-4 | Σ `available` < quantity | take everything available, `Shortfall(quantity − Σ take)`; the walk is not silently short `[seg 134]` | RULE-2 |
| DT-5 | position already `picked` | not listed — the units are in hand (element 02) | RULE-2 |
| DT-6 | position already `packed` | not listed — `[seg 120]`, `eventstorming.json:129` | RULE-2 |
| DT-7 | order `onHold` | the list renders unchanged; the hold is element 09's concern, not the shelf's | RULE-7 |
| DT-8 | two positions at one location code | two adjacent stops, tie broken by `productNumber` | RULE-6 |
| DT-9 | `stockAt` fails for any product | no list at all; the failure is surfaced (no retry policy invented — `UND-31`) | RULE-8 |
| DT-10 | `available` shrinks between two lists | a new list reflects the **same** pinned order — only the stops and shortfalls change, never the quantity ordered (element 01 RULE-2) | RULE-2 |

## What this calculation must never do

- invent a route, a distance metric, an aisle graph or a start point (`UND-23`; KB `pick-list.md:19-30`)
- commission stock, push a movement to the warehouse, or reserve anything — `reserve` is the port's operation, called by
  the process, not by a read model (element 13, KB `pick-list.md:44-58`)
- recompute itself behind a walking employee's back (DEFAULT-3)
- carry a price, an amount, a currency or a rounding (spec.md D5)
- treat `reserved` as a synonym of `available`, or trust a warehouse-side `available` filter instead of the two sums
  (KB `port-constraints.md:35-45`, `UND-18`)

## State diff

Not applicable — the calculation is stateless. Its output is a read model (`eventstorming.json:138`), replaced per request.

## Domain events

Emits none. It is re-run by whoever asked for the list (the workstation, element 16, or element 11's `GET`). Consumed
indirectly: `stock changed` (`eventstorming.json:134`) makes a *new* list different from the previous one — the
calculation itself never listens.

## Q / R carried into this element

- **Q-10-1 open:** how a quantity is distributed when the product sits in several places (KB `pick-list.md:29`, G5).
  Default: ascending location code, same order as the walk (DEFAULT-2). Any answer changes `Stop[]`, not the value object.
- **Q-10-2 open:** whether a list is regenerated on `stock changed`, and how a regeneration reaches an employee already walking
  (KB `pick-list.md:30`, G6). Default: snapshot, no recompute (DEFAULT-3).
- **R-10-1 open:** per-place quantities are assembled **by us** from the warehouse read — there is no push, so
  `stock changed` is an event we raise and the list is stale by at least one read (KB `pick-list.md:104-113` SC1–SC3,
  KB `integrations/warehouse-inventree/integration-hooks-and-picking.md:7`). Freshness therefore has no source and no number.
- **R-10-2 open:** ordering by location code presumes the codes sort in walking order — nothing states the code format, the
  zone/aisle/shelf model, or who owns the layout (`UND-23`, KB `pick-list.md:26` G2). The default may walk the warehouse
  backwards, and no test would notice.
