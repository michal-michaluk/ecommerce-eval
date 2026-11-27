## 8 The write path, and what a read costs

The service stores the aggregate as one JSONB document. Everything below follows from that one choice: a
change is one row update, a read is one row plus whatever the offers answer, and the two columns beside
the document are the only ones a query can filter on.

<!-- evidence: services/shopping/src/main/resources/db/changelog/0001-basket.yaml:3-4, services/shopping/src/main/java/shop/shopping/basket/DocumentBasketRepository.java:117-128 -->

### 8.1 A command, step by step

1. The route hands a basket identity and a caller to the service.
2. The service loads the row and rehydrates the aggregate from the document. A row that does not exist, or
   whose stored customer is not the caller, is `BASKET_NOT_FOUND`.
3. The aggregate decides. It refuses, or it mutates and bumps its revision, and a mutation that is history
   records a domain event in the aggregate's own buffer.
4. The answering path re-reads the offers the basket holds, applies each answer through the ingestion
   path, and stores the document only if the command or the pull changed it.
5. The repository copies the buffer, clears it, writes the row and then publishes every event once — so a
   failed write cannot publish a fact twice.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketService.java:285-298, services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:189-197, services/shopping/src/main/java/shop/shopping/basket/DocumentBasketRepository.java:33-35, services/shopping/src/main/java/shop/shopping/basket/DocumentBasketRepository.java:60-70 -->

The ordering in step 5 is the reason the row is written before the events are published: an event that
reached a listener before the row it describes existed would describe a state nobody can read.

### 8.2 The revision is not the version

Two counters move for different readers, and confusing them is the easiest mistake to make here.

| Counter | Moved by | Read by |
|---|---|---|
| `revision` | every mutation of what the customer can see: quantity, selection, seller, availability, title, picture, price, and each handover | the handover's identity, so a repeated handover of unchanged content is the same handover |
| the row's `@Version` | every stored document, freshness included | the optimistic lock, so a write composed from a stale read fails |

A read that finds the same content but a newer `readAt` is stored — the next older answer must be
discarded — and it consumes no revision, because freshness is not a mutation. Without that separation a
version that moved on every render would churn the handover identity for nothing.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:180-197, services/shopping/src/main/java/shop/shopping/basket/BasketItem.java:120-134, docs/specs/basket/log.md:129 -->

### 8.3 What a read costs

A render is one row read, one batched offer read and, when anything moved, one row write. On an
unchanged catalogue the write still happens on the first render after a row's `readAt` moves, which is
why the pull is described as "a write without a revision" rather than as a cache refresh.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketService.java:262-271, services/shopping/src/main/java/shop/shopping/basket/BasketService.java:88-103 -->

Two properties of the read path are worth stating because they are requirements rather than side effects:
a read that fails greys its row instead of failing the basket, so a provider outage still renders; and a
read that answers in another currency keeps the last-known prices and marks the row `stale`, because a
conversion is impossible by construction.

<!-- evidence: docs/specs/basket/log.md:113, docs/specs/basket/log.md:133, services/shopping/src/main/java/shop/shopping/basket/BasketItem.java:136-156 -->

### 8.4 The one constraint the code does not carry

"One active basket per customer" is enforced by the schema, not by the service: a partial unique index on
the customer column, restricted to active rows. Two requests that both found no basket therefore cannot
both create one, and the archived baskets of the same customer stay storable — "several active baskets"
would be a change to this one index rather than to the code.

<!-- evidence: services/shopping/src/main/resources/db/changelog/0001-basket.yaml:41-47, docs/specs/basket/log.md:112 -->
