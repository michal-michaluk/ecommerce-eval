## 10 Domain events

The basket records four facts, all in the past tense, and the set is closed. There is no event for a
selection change and none for opening a basket: selection is state rather than history, and the
specification names no creation fact.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/DomainEvent.java:8-21, services/shopping/src/main/java/shop/shopping/basket/DomainEvent.java:24-48 -->

| Wire name | Record | Carries | Recorded when |
|---|---|---|---|
| `AddedToBasket_v1` | `AddedToBasket` | basket, offer, the row's quantity **after** the merge | an offer is added, or a repeated add merges |
| `BasketQuantityChanged_v1` | `BasketQuantityChanged` | basket, offer, the quantity before and after | a quantity command changes a row |
| `RemovedFromBasket_v1` | `RemovedFromBasket` | basket, offer | any removal scope takes a row |
| `GoToCheckout_v1` | `GoToCheckout` | basket, the offer ids that crossed | a handover is recorded, not repeated |

: The four facts the basket records.

The name is `AddedToBasket`, not the board's `added from basket`: the board's wording is inverted and was
normalised once, in the specification, so that nothing downstream re-derives a vocabulary from an image.

<!-- evidence: docs/specs/basket/log.md:88, docs/specs/basket/log.md:87 -->

### 10.1 Versioned names, and one writer

Each subtype is registered on the sealed event interface with a wire name that carries its version —
`AddedToBasket_v1` and not `AddedToBasket` — so a listener can tell a v1 from a v2 without looking at the
payload. Nothing enforces the suffix: the name is a string in the registration, and the version is a
convention the four records follow rather than a rule the code checks.

The persistence adapter drains the aggregate's buffer before the write and publishes each event once
afterwards, so the events describe a state that is already stored.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/DomainEvent.java:15-21, services/shopping/src/main/java/shop/shopping/basket/DocumentBasketRepository.java:60-70 -->

No handler consumes these events in this increment, and no publisher reaches another process: there is no
broker, and the events exist for the listeners a later increment adds. The repository publishes them on
the application's own event bus, where a listener is a bean in the same process.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/DocumentBasketRepository.java:40-46, services/shopping/k8s/base/configmap.yaml:9-10 -->

### 10.2 The other direction: observations applied in process

The event path the specification does care about runs the other way. An offer observation — what a read
saw of one offer, or what a future event would carry — is applied to the row that already stands for it
through one command with three shapes: an answered read, an unanswered read, and a content refresh from
an event that carries no availability. The command is idempotent and ordered: applying the same
observation twice leaves the document as it was, and an observation older than the row's own read is
discarded.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/OfferObservationCommand.java:14-32, services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:170-188 -->

A content refresh deliberately does not re-decide availability. A price or description change carries no
offer state and no stock, so it must not be a reason to grey a row or to revive one; the availability
decision runs again only on an observation that carries the state it decides from.

<!-- evidence: docs/specs/basket/log.md:131, services/shopping/src/main/java/shop/shopping/basket/OfferObservationCommand.java:128-140 -->
