## 5 Cross-cutting rules

Five rules apply to more than one operation. They are collected here once, because a reader who meets
them per operation would conclude they are five decisions instead of one policy each.

### 5.1 Identity is the token's subject, and ownership is checked on the one path a request takes

The customer is the token's subject; the service never reads a customer identity from a body, a query
parameter or a header. Every route that answers or commands takes that identity as one value — the
subject plus the channel claim — and loads the basket through the check that answers a basket owned by
somebody else exactly as one that does not exist. A stranger's id is a `404`, never a `403`: a `403` would
confirm that the id exists.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/Caller.java:17-38, services/shopping/src/main/java/shop/shopping/basket/BasketService.java:367-377 -->

The channel claim is the one place where an unrecognised value is not an error: only the literal `smart`
(in any case) makes the caller a SMART customer, and an absent, misspelled or unknown claim is the
default arrival. That is deliberate — a claim the identity provider does not know must not fail a
customer's read.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/Caller.java:19-22, services/shopping/src/main/java/shop/shopping/basket/Caller.java:36-38 -->

### 5.2 Two writes behind a read, and one of them can answer `409` to a `GET`

Both are recorded deviations from the service's own HTTP doctrine, and both are consequences of decisions
taken elsewhere in the specification:

- **`GET /baskets` creates the basket the customer does not have.** A customer always owns one active
  basket, so asking for the list must return one; the creation is refused by a partial unique index when
  two requests race, and the loser re-reads the winner's basket. That is a write behind a read (D29, D42,
  D44).
- **Every route that answers basket content re-reads the offers first.** Without it a row's availability
  could not move between two commands, because the event transport is deferred; with it, a rendered
  `readAt` makes a fresh read a write even when nothing about the row changed, so two concurrent answers
  for one basket can lose the optimistic lock and report `409` — to a `GET` as much as to a `PATCH` (D62,
  D66).

<!-- evidence: docs/specs/basket/log.md:112, docs/specs/basket/log.md:114, docs/specs/basket/log.md:132, docs/specs/basket/log.md:135, services/shopping/src/main/java/shop/shopping/basket/BasketService.java:353-365 -->

Neither deviation is silent. The create path is the only place in the service without an ambient
transaction, precisely so that the refused attempt does not poison the transaction that re-reads the
winner; the pull path is one method every answering route calls, so no route can forget it and no route
can pull twice by accident.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketService.java:36-39, services/shopping/src/main/java/shop/shopping/basket/BasketService.java:281-298 -->

### 5.3 Concurrency is optimistic, at the row

The basket row carries a JPA `@Version`, so a write composed from a stale read fails instead of
overwriting a newer one. The failure is translated into one code (`CONCURRENT_MODIFICATION`) and one
status (`409`), and it is the only failure the service asks the client to retry.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/DocumentBasketRepository.java:130-140, services/shopping/src/main/java/shop/shopping/BasketErrorAdvice.java:43-50 -->

### 5.4 Money is an integer, and rounding happens once

Every amount is a `long` in units one digit finer than the currency's minor unit — for PLN, a tenth of a
grosz — so `99,89 zł` is `99890`. Nothing rounds until an amount leaves the service, and then exactly one
half-up step does it. A negative amount, a discount added to a charge and an amount the currency's scale
cannot hold are all refusals, not clampings.

<!-- evidence: services/shopping/src/main/java/shop/shopping/kernel/Money.java:7-14, services/shopping/src/main/java/shop/shopping/kernel/ShoppingException.java:38-43, docs/specs/basket/log.md:104 -->

A basket holds exactly one currency, decided by the deployment when the basket is opened, and an offer
priced in another currency is refused rather than converted: no rate exists anywhere in the service.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketCurrency.java:8-26, services/shopping/src/main/resources/application.yml:21-25 -->

### 5.5 Time is a dependency, and the estimate's zone is configuration

Nothing in the domain reads the clock. The handover's instant is passed in from a service that owns a
`Clock` bean, which is what makes a handover's identity reproducible in a test. The delivery estimate is
the one place a date is derived: it counts calendar days in `Europe/Warsaw`, and the zone is configuration
rather than a constant, because a deployment in another market would otherwise date every basket wrong.

<!-- evidence: services/shopping/src/main/java/shop/shopping/AppConfiguration.java:31-34, services/shopping/src/main/java/shop/shopping/basket/BasketService.java:239-246, services/shopping/src/main/resources/application.yml:32-40 -->
