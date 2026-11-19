# Phase 1 decisions and deviations — order processing core

Everything a reader of the code would otherwise have to reconstruct from a chat log: the places where phase 1 of
`docs/specs/fulfillment/spec.md` §Implementation Plan had to choose, and the places where this service knowingly departs from a
rule it was built to follow. Each entry names what was chosen and why; nothing here weakens the architecture test or a quality
gate.

## 1. The executable contract

| # | Question | Decision |
| --- | --- | --- |
| 1.1 | The prototype asserts `jsonpath "$.processingId" == "OP-2026-0001"` (`prototypes/fulfillment-api.hurl:49`) | `e2e/fulfillment-api.hurl` **captures** the id instead. The id is minted from `order_processing_id_seq` and carries the current year, so a literal would pin the suite to one calendar year and one sequence position |
| 1.2 | The prototype's 404 asks `GET /processing/OP-DOES-NOT-EXIST` | that route is a later phase; the same `PROCESSING_NOT_FOUND` expectation is asked of `POST /processing/{id}/assignment`, the only processing-addressed route phase 1 exposes |
| 1.3 | The prototype lists orders before the claim | kept as the prototype has it; the phase-1 file adds one list request after the claim, which proves the projection followed the event (element 15 "refresh on event") |
| 1.4 | `GET /orders` and paging | no paging parameters. The prototype sends none and element 15 says the list has none; `http-adapter.md`'s `Pageable` band would invent a bound no source names (element 11 Q-11-1) |
| 1.5 | Assignment no-op status | `200` with the current state. The prototype does not pin it; `11-fulfillment-http-api.md` D-11-2 makes a repeated command a `200` |
| 1.6 | A handover whose *part* is unusable (a pickup point without `pointId`, a line without a product number or with a non-positive quantity, a duplicated product line) | `422 ORDER_INCOMPLETE` with `missing[]` naming the payload path — `delivery.location.pointId`, `lines.productNumber`, `lines.quantity` — because that is the one refusal element 12 gives a rejected order and the path element 11's 422 row spells out |

## 2. Order processing core

| # | Question | Decision |
| --- | --- | --- |
| 2.1 | `processingId` minting (spec F6 left it undefined) | a Postgres sequence, `order_processing_id_seq`, formatted `OP-<year>-%04d`. A sequence is the only generator two concurrent intakes cannot draw from twice. Beyond 9999 in one year the four-digit form breaks — the format is the element examples', not an invention |
| 2.2 | Two first deliveries of the same `orderHash` at the same time | `findByOrderHash` answers the duplicate; a lost race hits the unique index `order_processing_order_hash_idx` and answers `500`. Nothing beyond the hash exists to dedupe on, and the index is what keeps one-processing-per-order true under concurrency |
| 2.3 | `OrderProcessingRepository` carries `findByOrderHash` and `nextIdentity` | `ports.md` `port.secondary-no-query-methods` asks for a two-method port. Recorded as an exception, the way element 13 records its `public WarehouseStock`: the idempotency key of the handover is not the aggregate's identity, so it cannot be asked through `get`, and an identity mints at storage, not in a service counter that two instances would share |
| 2.4 | Where a granted claim reserves | D11 names the trigger, no source names a location. `WarehouseReservations` takes the first location the warehouse reports with enough free units; element 10 owns location choice when the pick list lands |
| 2.5 | A claim that loses the optimistic lock, or a warehouse that fails on the second position | `WarehouseReservationsIntegration` registers a transaction synchronization: a transaction that ends any way but `committed` releases every reservation the claim took, and a partial reserve releases immediately. `release` is idempotent by port contract, so a compensation never has to ask whether it already ran. The ids stay in memory for the life of the claim; element 13's state S1 gives them a home when the adapter lands (R-13-1 stays open for a crash between reserve and commit) |
| 2.6 | No warehouse adapter exists yet (spec D2, D14) | the service boots without one and refuses the claim with `WAREHOUSE_NOT_WIRED`. No stock is invented and no fallback quantity is served — element 13 RULE-7 forbids exactly that. Tests wire the in-test double `FakeWarehouseStock`; nothing fake is registered in `src/main` |

## 3. HTTP adapters

| # | Question | Decision |
| --- | --- | --- |
| 3.1 | `http-adapter.md:34` bans a manual success status; element 11 needs `201` for a create and `200` for a replay on the same route | the two write handlers set the status through the injected `HttpServletResponse`. `ResponseEntity` is not an option either: `api.no-entity-in-signature` reads it as a persistence type. A doc/contract conflict, recorded here rather than hidden in a helper |
| 3.2 | `api.unsafe-method-has-body` requires a `@RequestBody` on a POST; the claim's contract has no body | `AssignOrder` is an empty command record taken with `required = false`. The claimer is the token's subject (element 07 RULE-5), so there is nothing to bind. The rule and the contract disagree; the record satisfies the rule without inventing a field a client could misuse |
| 3.3 | `api.path-no-rpc-verb` flags `/processing` (it reads `process` as a verb) | the path is element 11's contract verbatim; the hint is left standing rather than reworded away from the wire |

## 4. Read model

| # | Question | Decision |
| --- | --- | --- |
| 4.1 | Storing the state | the `orders_list` column holds the `ProcessingState` name and the served row derives the wire word from it, so a stored value never has to be parsed as a client vocabulary |
| 4.2 | An `assignee` nobody holds | served as an explicit `null`, never as an absent key (element 11's list row), and asserted both in the read test and in `e2e/fulfillment-api.hurl` |
| 4.3 | A row that cannot be represented (a claim that arrived before its handover has no placement time or recipient) | `OrdersListEntry.ofNullable` answers `null` and the query drops it, so the list never shows a half row (`projection.absent-read-model-is-null`) |
| 4.4 | In-test double for the secondary port | `FakeOrderProcessingRepository` double-tests the two services. `ports.md:64-90` endorses exactly this ("a secondary port exists so the use case can be tested without a database"); the plan's "fakes only for the warehouse" is narrower than the arch doc, and the arch doc wins |

## 5. Security

| # | Question | Decision |
| --- | --- | --- |
| 5.1 | `/error` is `permitAll` | it is Spring's error dispatch, not a business route: without it a container-level failure is answered by the security chain instead of by the status it carries. Recorded in `docs/security-decisions.md` as the fourth public path |
| 5.2 | The four scopes are Keycloak **client scopes**, and every test user signs in through the client of its scope | that is what puts a value into the token's `scope` claim — the claim the resource server reads. Realm roles of the same name were removed: they would have documented a mechanism that grants nothing |
| 5.3 | The realm in `k8s/infra/keycloak.yaml` carries developer users with `directAccessGrantsEnabled` | it is the dev-cluster realm (`start-dev`, bootstrapped admin), not a production identity source. A production deployment imports its own realm and its credentials from a secret; no warehouse or carrier credential is in code |

## 6. Publication

| # | Question | Decision |
| --- | --- | --- |
| 6.1 | `persistence.publish-after-write` asks the adapter to publish the aggregate snapshot once per changed save | only the domain events are published. `OrderProcessing` is package-private, has no subscriber in phase 1 and no consumer outside the slice; the orders list is fed by the events. Recorded so the omission is a decision, not an oversight |
| 6.2 | A rolled-back claim still publishes to in-process listeners immediately | the events are published inside the caller's transaction and the only listener (the orders projection) joins it, so a rolled-back claim rolls its rows back with it. The claim's *warehouse* effect is the one that needs the compensation of 2.5, because the warehouse is not in our transaction |

## 7. Known gaps owned by later phases

- `release` has no path of its own (D11: never called); element 13 state S2's sweeper lands with the adapter spec.
- `stockAt`, `issue` and `stockChanges` are unused by phase 1; the real adapter is its own spec.
- The `orders` list derives state from the facts it has (order + assignee); picking, packing and hold facts arrive with their
  events and extend the same recompute.
- Concurrency of the claim is proven at the row level (the `@Version` column refuses a stale write); an end-to-end two-thread
  race is not part of phase 1's suite.
