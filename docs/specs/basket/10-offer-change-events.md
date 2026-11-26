# 10 — Offer change events

**Role:** the **event channel between microservices** the operator asked for (**D25**: *"we should add model of events between microservices: new product details may update basket"*). It is what makes element 06's staleness question answerable without a timer: an item is refreshed when something actually changed, not when a clock says so.

**Status in this increment (D39).** The **transport is deferred**: no Kafka listener, no producer, no topic — no service emits an event today, and the operator scoped this increment to the basket itself ("there is no events emitted on kafka … do not focus on integration now"). What *is* specified and implemented is the **application path**: the in-process command that takes one observation and rewrites one item's snapshot. Elements 06 and 08 consume it, and it is what makes DoD20 (same event twice) and DoD21 (an event older than `readAt`) provable without a broker. When the transport lands, only an adapter is added; the rules below do not move.

**Why a new element.** The repo has **no event traffic at all** today (F11): `spring-kafka` is on the classpath, `kafka-bootstrap-servers` is configured (`services/fulfillment/k8s/base/configmap.yaml:8`), and `services/*/src` holds zero producers and zero listeners — events are in-process `ApplicationEventPublisher` only, and `catalog` publishes nothing. This element defines the first cross-service flow, so it is a specification element, not a detail of element 08.

**Provenance:** board crossings `product description snapshot when published`, `product prices changed`, `product removed from offer` (`eventstorming.json` `boundaries[0]`, `offer-management → browsing-offer`) and `product out of stock (non blocking)` (`boundaries[1]`, `browsing-offer → basket-management`); `[seg 23:33]` "when a price change occurs, an event is emitted to the outside world".

## Events consumed

| Event | Payload (fields we consume) | Effect on the basket |
|---|---|---|
| `product prices changed` | `offerId`, `price`, `currentPrice`, `discount`, `changedAt` | re-evaluate the item's price snapshot; the view and element 04 re-render. **Content only** — availability is untouched (D61) |
| `product description snapshot when published` | `offerId`, `title`, `pictureUrl`, `publishedAt` | refresh the item's last-known snapshot (02 RULE-8). **Content only** — availability is untouched (D61) |
| `product removed from offer` | `offerId`, `removedAt` | element 06 A3 → `unavailable`, greyed, handover blocked |
| `stock changed` | `offerId`, `stock` (quantity today, a band later — R14), `changedAt` | element 06 A2/A5 re-evaluated — a transition to `outOfStock` now **blocks** handover (D33) |

## Rules

- **EVT-1 No topic, no schema, no key is invented here.** No material names a topic, a payload field or a partition key; the table above lists the fields the basket *needs*, and the concrete channel is a `plan` decision constrained by the repo's absent traffic (F11).
- **EVT-2 Events never create items.** An unknown `offerId` is ignored — a basket item is created only by `AddToBasket` (02).
- **EVT-3 Reads still win.** After an event, the next read (element 08) is authoritative; events carry `changedAt` and an out-of-order event older than `readAt` is discarded (element 06, `readAt`).
- **EVT-4 At-least-once is assumed.** Applying the same event twice is a no-op because the effect is a snapshot replacement, not an increment. This is the one place the basket tolerates duplication by design.
- **EVT-5 A customer with no active basket is unaffected.** The aggregator (or a basket index) skips baskets that do not contain the `offerId` — the cost of the flow must not grow with the number of inactive baskets.
- **EVT-6 Events do not reserve.** A `stock changed` event may never be answered with a reservation (R6).

## Failure and ordering

| Case | Behaviour |
|---|---|
| event arrives for an item that is `unavailable` | applies — an offer can come back; element 06 re-decides |
| event older than the item's `readAt` | discarded (EVT-3) |
| broker unavailable | the basket keeps working on pull (element 08); freshness degrades, no item is blocked by a missing event |
| duplicate delivery | no-op (EVT-4) |
| event during a handover | the handover already carries a snapshot; the change affects the next handover only |

## Open items carried

- **R14** — the stock band model (high / medium / last *n*) arrives in the same channel; `stock` must stay untyped in the payload until then (D20).
- **R13** — D26 greys an item when a read fails; an event is never the reason to grey, only a reason to refresh.

## Recommendations

1. **Start with one consumer, one event type** (`product removed from offer`): it is the only event with a blocking consequence, so it proves the flow end-to-end with the least surface.
2. **Make `readAt`/`changedAt` ordering explicit from the first commit** — retro-fitting ordering to a live stream is the expensive part (EVT-3).
3. **Keep the aggregator stateless per basket**: index `offerId → basketId` so EVT-5 holds and a price change does not scan every basket.
4. **Do not let this element grow into the checkout handover path** — checkout is an in-process command keyed by `basketId` (D32); events stop at the basket boundary.
