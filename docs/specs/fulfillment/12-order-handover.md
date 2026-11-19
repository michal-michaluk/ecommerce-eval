# 12 — shopping ↔ Fulfillment

Element 12 · type: integration with another component · direction: inbound · accepted.

The board's only crossing into this lane is `ordered` (`eventstorming.json:169-172`). Checkout produces a finished,
immutable order and this context "basically need[s] to fulfill it" `[seg 106]` — it re-validates nothing and edits
nothing (KB `shopping/checkout-order/order.md:7-25`).

## Contract

`POST /orders` (element 11), scope `shopping-service`.

| Part | Content | Provenance |
| --- | --- | --- |
| identity | `orderHash` — canonical content hash of the `Order` value object (element 01 RULE-1) | `spec.md` D4 |
| content | `lines[]` (productNumber, name, quantity), `delivery` (methodCode + one location), `payment` (methodCode), `invoicing` (buyerName, nip, address) | element 01, KB `order.md:39-56` |
| time | `placedAt` — when checkout accepted the order | `[seg 95]` |
| absent | prices, totals, currency, seller party, order number | `spec.md` D5, F4 |

Response: `201` with `processingId` + `state: pending`, or `200` with `duplicate: true` when the same `orderHash` is
delivered again, or `422 ORDER_INCOMPLETE` with `missing[]`.

## Why these and nothing else

- **Immutability is the contract.** The board has no `order amended` event (`eventstorming.json:125-135`), and the
  narration says "in normal processing, we shouldn't add anything on the order" `[seg 99]`. So there is no `PATCH`, no
  `PUT`, no cancel.
- **The commitment changes only outside the system** `[seg 100-107]`; an adjusted order arrives as a **new** `Order` with
  its own `orderHash` — which is why the hash, not an order number, is the identity.
- **No money crosses.** Checkout keeps the prices it accepted; this context needs quantity, place and consignee only
  (`spec.md` D5).

## Domain rules

- **RULE-1 Content is complete or refused.** The handover is atomic: either a processing record with all positions is
  created, or nothing is stored (element 01 RULE-3, element 02 RULE-3).
- **RULE-2 Duplicate delivery is a no-op.** Same `orderHash` → same `processingId`, `200`, no second position set, no second
  parcel (spec.md D4, element 02 RULE-6).
- **RULE-3 Method codes are opaque.** This context stores `delivery.methodCode` / `payment.methodCode` verbatim and never
  validates them (`UND-21`) — except for one rule of its own: `location.kind = PICKUP_POINT` requires `pointId`, and
  `ADDRESS` requires `address` (element 01 RULE-7).
- **RULE-4 The first state is `pending`.** Nothing may be pre-assigned by the caller `[seg 110-111]`.

## Transport — the open architectural question

| Option | Consequence | Status |
| --- | --- | --- |
| synchronous `POST /orders` | the only transport the arch docs currently allow: `ApplicationEventPublisher` is in-process only, "no Kafka topic, no outbox worker" (`docs/arch/microservice-java-spring/integration-events.md:18`) | **default** |
| broker event `ordered` | matches the board's event semantics and decouples the two contexts; requires the arch doc to change, plus an outbox | deferred with `Q-12-1` |

Either way the **payload and the idempotency rule stay identical** — that is why this element specifies content, not
transport.

## Domain events

Inbound: `ordered` (as a request, not a message). Outbound on acceptance: `OrderProcessingStarted` (element 02).

## Q / R carried into this element

- **Q-12-1 open:** transport — the default is HTTP; a broker would need `integration-events.md` revised.
- **Q-12-2 open:** who notifies the customer that the order was accepted — checkout already answers the customer at the
  moment of `ordered`; this context's first customer-facing state is `pending`.
- **R-12-1 open:** a synchronous call means a fulfillment outage blocks checkout's terminal action. Checkout's guard treats
  acceptance as a commitment `[seg 90]`, so the failure mode (refuse the order vs accept and retry) is a checkout
  decision, not ours.
