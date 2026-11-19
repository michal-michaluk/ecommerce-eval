# 13 — Fulfillment → Warehouse

**Role:** **Driven port / adapter boundary** — an *external* port, not a secondary one. `fulfillment` declares what the
picking process needs in its own words, a protocol adapter translates it to the warehouse's protocol, and no warehouse type
ever appears in a signature (`docs/api/warehouse-api.md:22-23`, rules `:48-52`); one adapter class per protocol,
package-private, behind the port (`:50`). The arch taxonomy names three kinds — primary, secondary (a package-private
`{Aggregate}Repository` with `get`/`save`), contract (`docs/arch/microservice-java-spring/ports.md:6-13`) — and a
third-party system port is none of them. It is therefore declared `public`, exactly as the port template does
(`docs/api/warehouse-api.md:27`): `ports.md:49` bounds ports *between contexts*, and a warehouse outside the SUD is not a
context, so the package-private rule does not reach it — the deviation is recorded, not assumed.

**Definition:** `WarehouseStock` — the warehouse as the pick list, the reservation and the goods issue need it: where the
goods are and how many are free per location, a short-term reservation, its release, and the goods issue
(`docs/api/warehouse-api.md:27-39`). Plus **one operation the consumer doc lacks** — the stock-change read — because the
warehouse cannot notify us: "InvenTree can receive webhooks, and can call plugins, but it cannot call us. There is no way
for an external system to say 'tell me when stock changes'" (KB
`integrations/warehouse-inventree/integration-hooks-and-picking.md:7-18`).

## Contract

```java
/** Consumer-side contract: the warehouse as the picking process needs it. */
public interface WarehouseStock {

    List<StockAtLocation> stockAt(ProductId productId);

    ReservationId reserve(ProductId productId, LocationCode location, int quantity);

    void release(ReservationId reservationId);

    IssueNote issue(ReservationId reservationId, List<PickedItem> picked);

    /** The fifth operation: no outbound subscription exists, so we read by watermark. */
    StockChangePage stockChanges(StockCursor cursor);
}
```

The first four signatures are the documented port verbatim (`docs/api/warehouse-api.md:27-39`); `stockChanges` is added
here, with its provenance below. Value objects are the consumer's (`docs/api/warehouse-api.md:42-44`):

| Value object | Values | Owner |
| --- | --- | --- |
| `StockAtLocation` | `code`, `available`, `reserved` | ours (`docs/api/warehouse-api.md:42`) |
| `ProductId` | the shop's product number (`75423`) | ours |
| `LocationCode` | an opaque warehouse location string | the warehouse's vocabulary, our type |
| `ReservationId` | ours — "not an InvenTree concept" (`docs/api/warehouse-api.md:45`) | ours |
| `PickedItem` | `productNumber`, `location`, `quantity` — what the scan confirmed | ours |
| `IssueNote` | document number, date, lines (`docs/api/warehouse-api.md:43-44`) | ours |
| `StockCursor` / `StockChangePage` / `StockChange` | opaque watermark · page of `{productNumber, location, available, reserved, observedAt}` | ours |

No money, no price, no weight, no carrier identity enters this port (`spec.md` D5; element 03 RULE-7).

## Operations

| # | Operation | Input | Output | Side effect | Idempotency |
| --- | --- | --- | --- | --- | --- |
| 1 | `stockAt` | `ProductId` | `List<StockAtLocation>` | none — a read | idempotent |
| 2 | `reserve` | `ProductId`, `LocationCode`, `quantity` (integer ≥ 1) | `ReservationId` | **allocates** the quantity: `available` drops for every other consumer of that location | **not idempotent** — a repeated call reserves twice; nothing upstream dedupes for us (`UND-17`, `UND-31`) |
| 3 | `release` | `ReservationId` | `void` | ends the allocation; `available` comes back | idempotent **by port rule**: releasing an already-released reservation is a no-op, never a "not found" (KB `port-constraints.md:63-71`) |
| 4 | `issue` | `ReservationId`, `List<PickedItem>` | `IssueNote` | the goods leave the warehouse; the allocation is consumed | at-most-once per `ReservationId`, a repeat returns the same `IssueNote` (RULE-5; how the remote, asynchronous effect is confirmed is `Q-13-2`) |
| 5 | `stockChanges` | `StockCursor` | `StockChangePage` (next cursor + observations) | none — a read | idempotent and safe to re-poll; the same observation delivered twice is a duplicate to collapse, not an event (see below) |

### Why operation 5 exists

| Fact | Evidence |
| --- | --- |
| The schema has 293 paths and not one is an event subscription | `inventree-openapi.json` path list, KB `integration-hooks-and-picking.md:7-14` |
| The only `webhook` path is **inbound** (`POST /api/webhook/{endpoint}/`) — plugin infrastructure, an answering machine that must already run inside the warehouse | KB `integration-hooks-and-picking.md:12-14` |
| Outbound notification is a plugin callback (`EventMixin`), gated behind a warehouse-side deployment and an "Enable Event Integration" switch, and it carries an `id`, not a payload | KB `integration-hooks-and-picking.md:20-41` |
| The only option that needs nothing from the warehouse team is a poll by watermark (`ordering=-updated`, paging until a known `updated` value) | KB `integration-hooks-and-picking.md:69-89`, option O1 |

Consequence, and it is a domain fact, not an adapter detail: the board event `stock changed` (`eventstorming.json:134`) is
**an event we raise after reading**, never one we receive (KB `fulfillment/order-processing/pick-list.md:104-113` SC1;
`eventstorming.json:177` lists it as a warehouse interaction, which the warehouse we have cannot honour). Its latency is a
property of this port's cadence and belongs in the adapter spec, not in the domain.

**Deliberate extension, for the Step 6 review.** Tier B declares a **four-method** port
(`docs/api/warehouse-api.md:27-39`) and knows nothing of polling; Tier A proves no outbound subscription exists (table
above). `stockChanges` is added on that evidence and element 10 (`R-10-1`) depends on it. It is an extension to be
ratified, not an omission to be discovered.

### The three pieces of state we must own

The warehouse cannot hold any of these, so the boundary needs our state (KB
`integrations/warehouse-inventree/availability-and-allocation.md:188-194`):

| # | State | Why it cannot live in the warehouse | What breaks without it |
| --- | --- | --- | --- |
| S1 | **`ReservationId` ↔ allocation pks** (with stock-item pks and quantities) | allocation pks are not returned by the allocate call as declared — the request schema is echoed as the response, so the pks must be re-read | `release` and `issue` have nothing to address; a reservation becomes orphaned and, with no TTL, permanently eats `available` |
| S2 | **a sweeper for stale allocations** | allocations never expire — nothing in the allocation surface carries a ttl or an `expires_at`, and removal is manual or by consumption (KB `availability-and-allocation.md:129-142`) | every abandoned pick leaves stock unsellable forever and `available` drifts monotonically downward |
| S3 | **product → stock item (and batch) mapping** | allocation is per concrete stock item; this port speaks per product | `reserve(product, location, n)` has no addressable object; a product+location is often several rows |

S1 and S3 are translation and correlation — they sit **behind** the port, in the adapter. S2 is a **policy about our own
reservations**, and its trigger is exactly what no source states (`UND-17`, `Q-13-1`). The port therefore carries no TTL, no
`expiresAt` and no `swept` flag: the sweeper calls `release` when a duration we chose elapses, and the duration is a
decision nobody has taken.

### Deferred to the adapter spec (spec.md D2)

Which warehouse order object a reservation is allocated against (a sales order with a shipment versus a transfer order,
feature-flagged off by default), how a product maps onto concrete stock items and batches, which stock statuses count as
available, pagination, authentication and error translation: all adapter internals, documented in the KB
(`availability-and-allocation.md:105-121`, `:196-203`; `identity-and-api.md`; `movements-and-goods-issue.md`) and specified
in the warehouse adapter spec, not here.

## What the port must never do

- **expose a warehouse identity or type.** No part pk, no stock-item pk, no allocation pk, no `TaskDetail`, no schema
  object in any signature (`docs/api/warehouse-api.md:48-52`).
- **decide a business question.** Whether damaged stock is pickable is a documented status mapping, not a silent change
  (KB `availability-and-allocation.md:46-78`); whether a short pick is acceptable is a domain decision (`Q-13-3`).
- **invent a retry, backoff or replay policy** — explicitly forbidden (`docs/api/warehouse-api.md:51`, `UND-31`).
- **own the location model.** `LocationCode` stays opaque; we do not normalise, sort structurally or store a topology
  (`UND-23`, element 10 RULE-3).
- **carry a reservation lifetime.** No TTL parameter, no expiry field: the warehouse has none and we have not chosen one
  (`UND-17`).
- **move stock to a "hold location" instead of reserving.** A transfer changes location and never touches `allocated`, so
  it reserves nothing; allocation is the only mechanism with an owner and a release (KB `availability-and-allocation.md:205-226`
  rejecting `docs/api/warehouse-api.md:45-46`).
- **issue without a reservation, or release after an issue.** The warehouse forbids releasing an allocation of a completed
  shipment (KB `port-constraints.md:63-71`).
- **carry money** (spec.md D5).

## Domain rules

- **RULE-1 Our vocabulary, their protocol.** Every signature is written in `fulfillment`'s business words; translation is
  total and one-way (`docs/api/warehouse-api.md:22-23`, `:48-52`).
- **RULE-2 `available` and `reserved` are two independent sums.** `reserved` is not derived from `available` nor the
  reverse, and no warehouse-side `available` filter is trusted as a predicate (KB `port-constraints.md:35-45`, `UND-18`).
- **RULE-3 Every reservation is addressed by an id we mint.** A `ReservationId` is never a warehouse id, never parsed from
  a URL, never guessed; the mapping is state S1 (KB `availability-and-allocation.md:192`).
- **RULE-4 `release` is idempotent, `reserve` is not.** The caller must not blind-retry a `reserve`; the asymmetry is part
  of the contract, and closing it needs a dedupe key nobody has defined (`UND-31`).
- **RULE-5 `issue` is the only operation that changes what physically exists.** It is called once per reservation, after
  the scans, and it never runs because a poll said so; a repeat returns the same `IssueNote` instead of issuing twice.
- **RULE-6 The stock-change read is a read.** `stockChanges` returns observations; raising `stock changed` (and any
  collapse of duplicates) is the consumer's job, and the cursor is ours (`eventstorming.json:134`, KB
  `pick-list.md:104-113`).
- **RULE-7 Failure is surfaced, never smoothed.** No fallback quantity, no cached stock served as fresh, no invented retry
  (element 10 RULE-8, `UND-31`).
- **RULE-8 No money crosses this boundary** (spec.md D5).

## State diff

Not applicable — the port is stateless. The state it *requires* is S1–S3 above, held on our side of the boundary.

## Domain events

Consumed by the port's caller, not by the port: `stock changed` — raised by us from `stockChanges` observations
(`eventstorming.json:134`, KB `pick-list.md:104-113`). Emitted by the port: none. `delivery number generated` and
`delivery state notification` belong to the carrier boundary (element 14).

## Q / R carried into this element

- **Q-13-1 open:** what releases a reservation and how long it lives (`UND-17`). The warehouse has no TTL and no expiry field,
  so a sweeper must exist (state S2) and its duration is a decision with no source. Until answered: no lifetime in the
  port, no automatic release.
- **Q-13-2 open:** the shape of `issue`. The documented port returns `IssueNote` synchronously; the warehouse operation is
  asynchronous and document-free, so either the port grows a confirmation step or the note is numbered by us and its
  *effect* is confirmed later (KB `port-constraints.md:72-83`, `:163-175`). Default: optimistic `IssueNote`, remote
  confirmation pending — the cheapest change if the answer goes the other way.
- **Q-13-3 open:** what a short pick means — `picked` carrying fewer units than the reservation. The warehouse has no
  short-pick concept, and release-then-issue is the only expressible workaround (KB `port-constraints.md:163-175`). No
  source states whether a short pick is allowed at all.
- **R-13-1 open:** losing a `ReservationId` orphans a **real** allocation, and nothing on the warehouse side will ever clean
  it up — `available` falls permanently and the loss looks exactly like a commitment (state S1, KB
  `availability-and-allocation.md:129-142`, `:192`).
- **R-13-2 open:** six allocation behaviours are unverified and were never exercised against a live instance — including
  whether a raw stock removal refuses to consume allocated stock, whether allocation requires a particular order status,
  and what an over-request error looks like (`UND-18`, KB `availability-and-allocation.md:228-244`). The fake adapter
  cannot be proven against production until these are closed.
