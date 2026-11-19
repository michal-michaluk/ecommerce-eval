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

---

# Phase 2 decisions and deviations — warehouse adapter, pick list, picking and packing

The same file, the same rules: what phase 2 of the plan had to choose, and where it departs from a rule it was built to
follow. Numbered `P2-n` so nothing here collides with a phase-1 id.

## 8. The warehouse adapter (`warehouse-port`)

| # | Question | Decision |
| --- | --- | --- |
| P2-1 | Which warehouse surface a reservation is allocated against (element 13 defers this to the adapter spec) | a **transfer order**: `POST /api/order/transfer-order/`, one line (`…/transfer-order-line/`), then `POST /api/order/transfer-order/{pk}/allocate/` with one allocation item per stock item — the only chain every step of which is a path `docs/api/warehouse-api.md` names |
| P2-2 | The port speaks a product number, the vendor addresses a part by a numeric pk, and no documented endpoint searches parts by our number | the number **is** the part pk (`75423` → part `75423`); a non-numeric number is refused with `PRODUCT_NUMBER_MALFORMED` instead of guessing a lookup |
| P2-3 | `LocationCode` is an opaque string; the vendor reports a location pk | the code is the vendor location's **name**, resolved through `GET /api/stock/location/`; an unknown pk or code is refused, never normalised |
| P2-4 | `available` and `reserved` from `quantity` and `allocated` | `reserved = Σ allocated`, `available = Σ quantity − Σ allocated`. The vendor reports the two fields independently; free units are their difference and nothing derives one sum from the other (RULE-2) |
| P2-5 | The vendor reports quantities as doubles, the context counts whole units (`10:41`) | a fractional quantity is **refused** (`WAREHOUSE_QUANTITY_FRACTIONAL`), never rounded — no rounding exists anywhere in this context |
| P2-6 | `IssueNote` needs a document number the warehouse does not answer | the note carries **our reservation id** and today's date (element 13 Q-13-2 default: optimistic note, remote confirmation pending), and the row remembers it so a repeat answers the same note (RULE-5) |
| P2-7 | `release` must address allocations whose pks "are not returned as declared" (state S1) | the pks are read back after the allocate call (`GET …/transfer-order-allocation/?order=`) and stored; `release` deletes exactly those and forgets the row. A reservation we never took releases nothing — never a "not found" (RULE-4) |
| P2-8 | A failed warehouse call | it propagates. No retry, no backoff, no fallback quantity, no cached stock (RULE-7); the framework answers `500` and the poller's next tick re-reads the same window |
| P2-9 | Where the token comes from | configuration only (`fulfillment.warehouse.token`), never code; `url` and `token` are deliberately **absent** from `application.yml` so a deployment supplies them from its secret, and a deployment with no token gets **no adapter bean** — the service boots and the first claim answers `WAREHOUSE_NOT_WIRED` (phase-1 decision 2.6 stays true) |
| P2-9a | The adapter needs an HTTP client, and this module carries no REST-client starter, so no `RestClient.Builder` bean exists | the bean is taken through an `ObjectProvider`: the platform's builder when there is one, a plain `RestClient.builder()` otherwise. `ConfiguredWarehouseWiringTest` found that a configured warehouse would otherwise have failed at startup — the one wording of "the service boots without a warehouse" that a construction-only test could not see |

### The stock-change poller (`warehouse-port`)

| # | Question | Decision |
| --- | --- | --- |
| P2-10 | The poll interval (no source states a number) | `fulfillment.warehouse.poll-interval`, default **60s**: fast enough that a stale pick list is a minute old, slow enough that one instance is not a load on the warehouse. Recorded here because element 13 leaves the cadence to the adapter spec |
| P2-11 | Where the watermark lives | `warehouse_stock_cursor` — one row, one opaque value, written **after** the page was published, so a failed poll re-reads the same window instead of losing it |
| P2-12 | Where a fresh deployment starts reading | at the moment of its first poll, not at the beginning of the warehouse's history: no source asks a new instance to replay every stock change ever recorded |
| P2-13 | The event the poller raises | `DomainEvent.StockChanged`, one per observation, in-process only (no broker, no outbox) — the warehouse cannot notify us, so `stock changed` is a fact we raise (RULE-6) |
| P2-14 | State S2 — the stale-allocation sweeper, a **named output** of the `warehouse-port` node (`spec.md:390-392`) | **not built**: what releases a reservation and how long it lives is `Q-13-1`, still open in element 13, and a duration nobody chose would be an invented business rule. Recorded as an open topic, not as done |
| P2-14a | The S1 correlation is written inside the caller's transaction, so the claim's compensation would run after the rollback and find nothing (R-13-1) | every `WarehouseReservationRepository` method runs in its **own** transaction (`REQUIRES_NEW`): the warehouse is not in our transaction, so the row that addresses a real allocation must outlive the transaction that took it. Proven by `DocumentWarehouseReservationRepositoryTest` |

## 9. Pick list (`pick-list-calculation`)

| # | Question | Decision |
| --- | --- | --- |
| P2-15 | The wire shape of the list | `lines[]` per the reconciled element 10 and the hurl: `PickListLine{productNumber, name, quantity, state, locations[]}` with `Stop{walkOrder, productNumber, location, quantityToTake, available, reserved}`. `state` and `location` are the **wire** words (a `PositionState` gains `wireName()`, a location code is its string), so the read model needs no second DTO |
| P2-16 | The walk order across several positions | one total order for the whole list: stops sorted by (location code, product number) and numbered `1..n`, then handed back to their own lines — so the payload groups by position while the walk interleaves products (S7/S8) |
| P2-17 | A product of a position the list does not walk | not asked about at all: a fully picked order needs the warehouse for nothing |
| P2-18 | No warehouse adapter, a failed read, or a product with no observation | no list at all (`WAREHOUSE_NOT_WIRED`, the port's own failure, `STOCK_OBSERVATION_MISSING`) — never a partial list (RULE-8) |

## 10. Picking and packing (`pick-pack-commands`)

| # | Question | Decision |
| --- | --- | --- |
| P2-19 | The refusal codes the three new routes can answer | `POSITION_NOT_FOUND` (`404`, derived), `POSITION_NOT_PICKED` (`409`, derived), `ACTOR_NOT_ALLOWED` (`409`, the code element 11 already derives for "this actor may not", reached by a caller who does not hold the order), `ON_HOLD` (`409`, derived — the aggregate's own error name is not the wire code) and `NOT_STARTED` (`409`, the code element 11 already uses for "the order has not started", reached here by a scan before the claim) |
| P2-19a | A repeated pack: element 11's status ownership lists `200` for "the same scan, **the same pack**", while D-11-2 names only the scan | the contract's status table wins: packing a position that is already in the box changes nothing and answers `200` with the state it already had. A `pending` position is still `409 POSITION_NOT_PICKED` — the edge element 02 states is not the same as a replay |
| P2-19b | Element 16 makes the **assignee** the actor of scan and pack ("never let a non-assignee confirm, pack or mark"), element 11 grants the employee scope broadly | both commands read the caller from the token and the aggregate refuses anybody but the assignee with `409 ACTOR_NOT_ALLOWED`. The state machine's own refusals come first, so a held order refuses everybody with `ON_HOLD` and an unclaimed one with `NOT_STARTED` |
| P2-20 | `{confirmation: "..."}` carries exactly one legal value | `@Pattern("picked")` on the command record: anything else is an invalid **request** and answers `400` (element 11 D-11-1: the transition is a command, not a value the client picks) |
| P2-21 | A packing request naming a second position that is not picked | the whole command rolls back — one transaction, one save, so a half-applied box is impossible |
| P2-22 | The snapshot's `parcel` field | an explicit **null**: element 03 owns the parcel and its registration, and until that node lands the field keeps the contract's own null policy rather than the key disappearing |
| P2-23 | `issue` on the snapshot | the first **open** issue (element 11: an explicit null while none is open); `openIssues` counts them, so the field and the count can never disagree |

## 11. The pick workstation (`pick-workstation-read`)

| # | Question | Decision |
| --- | --- | --- |
| P2-24 | Element 16 has data and actions but **no route**, and element 11's table names none | one read route of this service's own: `GET /processing/{processingId}/pick-workstation`, employee scope. The payload is exactly the doc's fields — position product, name, quantity, state, its stops, processing state, `n/m packed`, assignee — and the "stock per location" panel is the stops themselves (one warehouse read, not two) |
| P2-25 | Confirmed positions on the screen | every position of the order is listed with its stops where the pick list still walks it and `[]` where it does not: W2 needs `picked` and `packed` visible next to `awaitingScan`, and an absent key never means empty |
| P2-26 | The screen offers no action of another view's actor | the route is a **read**: readiness (element 08), issues and resume (09) are not reachable from it and stay on the processing resource |

## 12. Pick-list variants

| # | Question | Decision |
| --- | --- | --- |
| P2-23a | Element 10 selects `scarcity-first` by `fulfillment.picklist.order` and `no-split` by `fulfillment.picklist.split`; phase 2 hard-wired `defaultRules()` | both keys exist and are read by the query handler, with the documented default as the fallback for a missing or unknown value (`location-code`, `multi-location`) — selection is first-match-wins and never fails a read |

## 12a. Layering

| # | Question | Decision |
| --- | --- | --- |
| P2-23b | Three read handlers (`PickListProjection`, `ProcessingSnapshotProjection`, `PickWorkstationProjection`) are `*Projection`s that rehydrate the aggregate instead of owning a read table, which `projection.md`'s `projection.own-table` and `http.no-write-model-in-read` forbid in their own words | recorded as a deliberate deviation, not silently taken. Element 10 defines its list as a **Calculation** over the Process and element 11's snapshot **is** the aggregate's own payload, so neither read has a shape worth materialising; a read table would duplicate the aggregate's state and be stale exactly where the contract says the state is read straight after a command. The three handlers keep the `Projection` name because the alternative classifier (`model`) would make them depend on the repository through a rule that forbids it; `projection.*`'s remaining rules (package-private, read-only queries, no public fields) all hold |
| P2-23c | What a goods issue books out, and what may never follow it | the removal comes off the units a location **physically holds**, never off its free units — the allocation of this very reservation already sits in the vendor's `allocated`. A `release` after an `issue` is **refused** (`WAREHOUSE_ALLOCATION_CONSUMED`) rather than attempted: element 13 forbids exactly that call. All picked items go out in **one** removal request, so a failure cannot leave half the goods issued with no note (RULE-5) |
| P2-23d | `available` excludes our **own** reservation, so a position whose units we just allocated can render as a shortfall | recorded as a known risk, not fixed: attributing the vendor's `allocated` between our own holds and everybody else's needs the per-allocation mapping the vendor does not declare, and R-13-2 leaves the allocation behaviour unverified. The fake reports a fixed quantity, which is why the executable contract cannot see it |

## 13. Gates

| # | Question | Decision |
| --- | --- | --- |
| P2-27 | `sqt`'s `api.path-no-rpc-verb` reads `process` as a verb | five hints now stand, one per `/processing/…` route: the paths are element 11's contract verbatim, the same decision as phase-1 3.3, and no rewording would keep the wire |
| P2-28 | The executable contract | `e2e/fulfillment-api.hurl` grew by the prototype's phase-2 requests in the prototype's order (pick list, scan, repeated scan, packing) plus the two `GET /processing/…` requests the prototype already had — which **closes** phase-1 decision 1.2. It also asks `POSITION_NOT_FOUND`, the one derived code the hurl can reach |

## 13. Known gaps owned by later nodes

- The stale-allocation sweeper (P2-14) and the adapter's behaviour against a live instance (R-13-2) stay open.
- `available` counts every allocation, our own included (P2-23d) — the pick list can show a shortfall for units we hold.
- `stockChanges` is still element 13's unratified fifth port method; phase 2 exercises it but does not ratify it.
- The parcel registration, readiness, issues and the customer view belong to phases 3 and 4.
- Coverage fell from 93.3 % to 92.3 % instruction because the adapter is exercised only through its translation tests; no
  exclusion was added.
