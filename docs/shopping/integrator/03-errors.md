## 3 Errors, idempotency and retries

One body shape per family, one code per refusal, and one refusal a client must retry. The rules below hold
for every route, so they are stated once instead of per operation.

<!-- evidence: services/shopping/src/main/java/shop/shopping/BasketErrorAdvice.java:26-56 -->

### 3.1 The two refusal bodies

A refused command:

```json
{"error": "OFFER_NOT_FOUND", "offerId": "OF-0000"}
```

A body the command could not take:

```json
{"errors": [{"field": "quantity", "message": "quantity must be at least 1"}]}
```

Fields that do not apply are omitted, not sent as `null`: the advice serialises with `NON_NULL`, so
`basketId` and `reasons` are absent when the code does not name them. A client branches on `error` and reads
the field that code names. No message, no stack trace and no human text about the refusal reaches a client —
the `message` in the second body is the rule a component states, which is the client's own fault to fix.

<!-- evidence: services/shopping/src/main/java/shop/shopping/BasketErrorAdvice.java:208-236, services/shopping/src/main/java/shop/shopping/BasketErrorAdvice.java:164-176 -->

`OF-0000` in the example above is the identity the contract of record uses for "an offer nobody sells": the
fixture does not know it, and that is what makes it a `404`. Any unseeded identity answers the same way.

<!-- evidence: services/shopping/docs/offer-fixture.md:58-58 -->

### 3.2 The statuses and the codes

| Status | Code | When | Fields |
|---|---|---|---|
| `400` | — | a component the command does not declare, a value that does not read as its type, or a violated constraint | `errors[]` with `field` and `message` |
| `401` | — | no token, or a token the issuer did not sign | empty body |
| `403` | — | a valid token without the customer scope | empty body |
| `404` | `BASKET_NOT_FOUND` | the basket does not exist — or belongs to another customer | `basketId` |
| `404` | `OFFER_NOT_FOUND` | the offer is not in the basket, or the catalogue does not know it | `offerId` |
| `409` | `CONCURRENT_MODIFICATION` | the write lost the optimistic lock | none |
| `422` | `QUANTITY_NOT_POSITIVE` | a quantity below one | none |
| `422` | `CURRENCY_MISMATCH` | the offer is priced in another currency | none |
| `422` | `NOTHING_SELECTED` | a handover with nothing selected | none |
| `422` | `BLOCKED_BY_ITEMS` | a selected row is out of stock or unavailable | `reasons[]` |
| `422` | `BASKET_INACTIVE` | the basket is archived | none |
| `422` | `OFFER_UNAVAILABLE` | a quantity change on a row that is unavailable | none |
| `503` | `OFFER_READ_UNAVAILABLE` | the offer could not be read and has no snapshot to grey the row with | none |

: Every refusal this service raises, with the status the contract gives it.

Two consequences of the `400` row are worth stating because they are easy to get wrong. An **unknown
component is refused**, not ignored: a typo in a field name would otherwise drop the value the client meant
to send and the basket would answer `200` to a command it never received. And a `400` names the components,
never the servlet path — the framework's own error page reports where the request was routed, which is not
what a client has to fix.

<!-- evidence: services/shopping/src/main/java/shop/shopping/BasketErrorAdvice.java:96-110, services/shopping/src/main/resources/application.yml:4-9 -->

`422` rather than `409` for a refused command is a recorded deviation from the service's own HTTP doctrine:
the executable scenario of record writes it that way, and the scenario is the contract. `409` is reserved
for a lost optimistic lock.

<!-- evidence: services/shopping/src/main/java/shop/shopping/kernel/ShoppingException.java:35-51, services/shopping/src/main/java/shop/shopping/BasketErrorAdvice.java:73-82, docs/specs/basket/log.md:114 -->

`reasons[]` is the one array in the refusal vocabulary, and it is an array of objects:
`{"offerId", "status", "stale"}` per blocking row, so a client greys exactly the rows the service names
instead of parsing a message. `stale` on a blocking reason is the difference between "the offer is gone" and
"we could not read it".

<!-- evidence: services/shopping/src/main/java/shop/shopping/kernel/HandoverRefusal.java:8-34, services/shopping/src/main/java/shop/shopping/BasketErrorAdvice.java:123-140 -->

An error the contract gives no status — the amount and currency refusals of the value types — is rethrown and
the framework answers `500` without a message: those are not a client's refusal, and pretending otherwise
would invite a client to branch on a programming error.

<!-- evidence: services/shopping/src/main/java/shop/shopping/BasketErrorAdvice.java:50-55, services/shopping/src/main/java/shop/shopping/BasketErrorAdvice.java:80-81 -->

### 3.3 Two refusals are also an incident

The two refusals an operator has to find afterwards are logged, once each, where every answer passes:
a write that lost the race logs the basket and the route it lost on, and a handover the customer could not
complete logs the basket and the offers that blocked it. Neither line carries a token or a body, and no other
refusal is logged.

<!-- evidence: services/shopping/src/main/java/shop/shopping/BasketErrorAdvice.java:129-146 -->

### 3.4 Idempotency, without an idempotency header

The service has no `Idempotency-Key` header and no dedupe table. It does not need one for the commands it
offers, and the reasons are worth knowing before you retry:

| Command | Repeat behaviour |
|---|---|
| set a row's quantity | absolute, so the second call writes the same number — and the answer reports the same totals |
| remove | a row that is not there is refused with `OFFER_NOT_FOUND`, and a bulk removal of nothing is a no-op |
| select | the same selection applied twice changes nothing after the first |
| hand over | the same content re-composes the same handover: same identity, same revision, same totals, and nothing crosses a second time |

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:208-214, services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:262-271, services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:128-137, services/shopping/src/main/java/shop/shopping/basket/BasketService.java:248-253 -->

The handover's identity is a digest of the rows and the totals, so a repeat of unchanged content is
recognised without a header. The estimate is deliberately not part of it: it is clock-derived, and an
identity that moved at midnight would make every handover of an untouched basket a new one.

<!-- evidence: services/shopping/src/main/java/shop/shopping/kernel/Handover.java:143-171, docs/specs/basket/log.md:134 -->

### 3.5 What to do with `409`, and what not to do with anything else

`409` means the basket changed between the read your write was composed from and the write itself. Retry
the call — after re-reading, if your client composes the body from state. It is the only refusal in this
vocabulary that a retry fixes by itself.

Nothing else is retryable. `503` means the provider could not be read: the row you asked to add has no
snapshot, so waiting for the provider is a client decision, not a blind retry. A `422` is a statement about
the basket, and retrying it unchanged produces the same answer.

<!-- evidence: services/shopping/src/main/java/shop/shopping/BasketErrorAdvice.java:78-78, services/shopping/src/main/java/shop/shopping/basket/BasketService.java:310-325 -->

Because every answering route pulls, a `409` can also reach a `GET`. That is the price of availability that
moves without an event transport, it is recorded as a deviation, and a client that treats a `409` on a read
as retryable is correct.

<!-- evidence: docs/specs/basket/log.md:132, docs/specs/basket/log.md:135 -->
