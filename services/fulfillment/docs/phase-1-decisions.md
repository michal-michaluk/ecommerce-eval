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

---

# Phase 3 decisions and deviations — the parcel, readiness, the carrier and the customer view

The same file, the same rules: what phase 3 of the plan had to choose, and where it departs from a rule it was built to follow.
Numbered `P3-n`.

## 14. Parcel registration (`parcel-aggregate`, `carrier-port`)

| # | Question | Decision |
| --- | --- | --- |
| P3-1 | The two write slices may not import one another (`processingDependsOnNoSlice`, `parcelDependsOnNoSlice`), yet a granted scan must register a parcel and a readiness must ask the carrier to collect the same one | the seam is a **contract port in `kernel`**, `Parcels`, implemented by `ParcelService` in the parcel slice and held by the processing slice as an interface. A consumer-owned contract port would force the provider slice to import the consumer (forbidden in both directions) and a mediator would force the parcel slice's service to become `public`; `kernel` already owns the port vocabulary (`CarrierPort`, `WarehouseStock`) and is the one package both slices may read. Recorded as a deviation from `context-boundaries.md`'s "the contract lives in the consumer's package" |
| P3-2 | Where the registration sits in the transaction | inside the scan's own transaction, before the processing is saved: the parcel exists as soon as the scan commits and a carrier failure refuses the scan (`500`) with the position left unscanned. Q-14-2 (an unknown outcome of `register`) keeps its default — no automatic retry and **never** a fresh reference |
| P3-3 | Our reference, and the parcel's own identity | the reference is `<processingId>-1`, the shape element 03's example uses (`OP-2026-0001-1`), minted from the processing id so no second key has to be stored; the parcel id is minted from `parcel_id_seq` as `PCL-<year>-%04d`, the same reasoning as the processing id (P2-1's sibling, phase-1 2.1) |
| P3-4 | Who measures the weight (element 03 RULE-6, UND-06 leave it open) | `fulfillment.carrier.weight-grams`, default **1000**: the deployment states it and no rounding rule is written anywhere |
| P3-5 | The sender block, the service code and the money fields | the sender is `fulfillment.carrier.sender.{street,city,postal-code,country}`; a carrier configured without it is refused at the **first registration** (`fulfillment.carrier.sender is required for this command`) rather than at startup, so a service with no carrier still boots. The service code is the order's own `delivery.methodCode` — the customer's choice, an opaque pass-through — and `cashOnDelivery`/`declaredValue` stay `null` because no amount exists in this context (D5) |
| P3-6 | Where the carrier's printed document lives | a table of its own, `parcel_label`, not a field of the aggregate snapshot: the poll rehydrates every parcel once a tick and must not drag a page of PDF along. The first print asks the carrier for the **same waybill we hold** and stores what comes back (element 06 RULE-9's default for `Q-06-1`); every reprint is the stored document and never calls the carrier. Recorded deviation: the print **is** a write reached through a `GET`, which `http-adapter.md`'s read shape does not have — element 06 calls the print a command to the carrier adapter, and the alternative (a POST that stores and a GET that serves) puts a write the client cannot see behind a route called `documents` |
| P3-7 | What a label request answers for a processing without a parcel | the contract's own `404 PROCESSING_NOT_FOUND`, the same code every other `/processing/{id}/…` route answers: an unknown processing and a processing whose parcel does not exist yet are indistinguishable from the parcel slice, which cannot see processings, so **one** code serves both rather than inventing a second one a client would have to know (`P3-7` closed with the review: an invented `LABEL_NOT_AVAILABLE` was removed). A carrier that is not wired answers `CARRIER_NOT_WIRED` (`500`), never an empty PDF |
| P3-8 | The carrier's own wire | `CarrierHttpClient`: `POST /parcels`, `GET /parcels/{waybill}/label?format=&layout=`, `POST /collections`, `GET /parcels/{waybill}/status`. Endpoints, payloads and codes are this adapter's business (spec D2); its status table maps six carrier words onto L1–L6 and **everything else to `unmapped`**, keeping the raw payload (RULE-6). Credentials are `fulfillment.carrier.{url,token}` and appear in no signature. `Bearer <token>` is the one header scheme; nothing about it reaches the port |
| P3-9 | The poll interval (no source states one) | `fulfillment.carrier.poll-interval`, default **60s**, the same reasoning as P2-10; our interval **is** the delivery-state latency the customer sees (element 18 R-18-1). The poll walks the parcels whose leg state is not `deliveredToCustomer` and saves only when a leg actually changed |
| P3-10 | `port.secondary-no-query-methods` asks for `get`/`save` alone | `ParcelRepository` carries five more methods — `findByProcessing`, `awaitingDelivery`, `nextIdentity`, `labelOf`, `storeLabel` — the identical recorded exception phase 1 took for `OrderProcessingRepository`. The parcel is addressed by its processing and by the set still on its way, neither of which `get` can ask, and the printed document is a second storage axis |

## 15. Readiness (`readiness-decision`)

| # | Question | Decision |
| --- | --- | --- |
| P3-11 | The order of the two effects, and what a replay does | RF1 then RF2, in one transaction, and the carrier is asked **only when the decision granted**: `OrderFulfillmentProcess.markReady` answers the verdict it applied and the service reads it, instead of re-deriving DT-08-1's condition two lines from the table — a future grant row could not silently skip the collection request. A replay (DT-08-5 `NO-OP`) writes nothing and asks nothing, which is what "calls the carrier once" means on the wire, and the answer is rendered **after** both effects, so `$.parcel.legState` is `parcelReady` — the state element 08's condition diff says the command leaves behind |
| P3-12 | Q-08-2 — RF1 succeeded and RF2 failed | in one transaction the carrier's failure rolls the local mark back with it, so the state the source calls the most likely real-world failure cannot be **observed** through this service: a readiness either grants both effects or neither. Recorded as the chosen answer to an open question, not as a fact about the carrier |
| P3-13 | `NOT_READY.reasons[]` | the decision's own value, spelled by `ReadyForDeliveryDecision.Reason.wireName()` and copied by the one advice: `notPacked`, `noParcel`, `onHold`, `pastHandover`, `positionsOpen`. DT-08-2 (`positionsOpen`) is unreachable through the state machine — `packed` implies all packed — and stays the defensive row element 08 itself calls it |
| P3-14 | Must the actor be the assignee (Q-08-4)? | not checked: the employee scope is the gate, and no source ties `mark done` to the assignee. A scan and a pack still refuse anybody but the assignee (P2-19b) |

## 16. The customer view (`customer-state-projection`)

| # | Question | Decision |
| --- | --- | --- |
| P3-15 | Why a fourth package instead of a filtered employee snapshot | `shop.fulfillment.customer` is a read slice like `orders`, fed by the domain events and importing neither write slice. `assignee` and `issue` are then absent **structurally** — they are not columns — instead of absent because a DTO chose to omit them (D-11-4, element 11 RULE-4) |
| P3-16 | `state` must be the processing's own state, and no event carries it | the projection derives it from the row's own monotonic facts — the handover order, assigned, picked, the set of packed products, held, readyForDelivery, delivered — first match wins. Any arrival order ends in the same row, and no fact can unset a state, so the timeline never regresses (element 18 "never backwards"). The packed positions are a **set**, not a counter, for the same reason `(parcel, leg)` is: a repeated `PositionPacked` is the same fact and must not advance the order to packed on its own |
| P3-17 | The delivery legs | a second table keyed `(parcel, leg)`, fed by `DeliveryNumberGenerated` (L1), `CollectionRequested` (L2) and `DeliveryStateNotified` (L3–L6). `at` is when **we** received the event and a repeated notification keeps the first moment; the state row learns its parcel from `ReadyForDeliveryConfirmed`, so the two tables are joined at read time and the handlers stay order-independent |
| P3-18 | Element 18 also asks for an order summary (lines, delivery target) at the top of the view | served, from the handover payload the projection copies into its own row (`projection.copy-of-foreign-payload`): `lines[]` as `{productNumber, name, quantity}` and `delivery` as `{methodCode, location{kind, pointId, address}}` — the customer's own content, money-free, a pickup point only as the opaque id the order carries. Element 18's defaults still hold where they say *hidden*: payment method, waybill, position progress and the invoicing party are not fields. A row whose handover has not arrived has no summary to show and is answered `404 PROCESSING_NOT_FOUND` (`projection.absent-read-model-is-null`) |
| P3-19 | Customer scope | no ownership check (D13): the scope alone decides who may ask, and an unknown id answers `PROCESSING_NOT_FOUND`. The employee snapshot stays `403` for this scope and the state resource stays `403` for the employee's |
| P3-20 | `OrderDelivered` | driven: `DeliveryStateListener` consumes `DomainEvent.DeliveryStateNotified` — the fact the parcel slice publishes after storing a leg — and applies it to the aggregate that carries the parcel, so the last leg closes the order. The event names the parcel and the order is addressed by its processing id, so `OrderProcessingRepository` grew `findByParcelId` (the same recorded exception as `findByOrderHash`) and `order_processing` grew a `parcel_id` column beside the JSONB snapshot. A leg of a parcel no processing carries — an order not yet marked ready — is ignored, never an error. `DeliveryStateWiringTest` proves the edge through the real event bus; DT-08-6 (`pastHandover`) and the customer's `delivered` state are therefore reachable |
| P3-25 | Carrier I/O inside a database transaction | both the readiness command and the status poll call the carrier while their transaction is open: RF2 must be written with RF1 it belongs to, and the poll's leg must be stored with the parcel it moves. The consequence is recorded rather than engineered away — a slow carrier holds the connection for its duration, and there is no outbox, no broker and no compensation to appeal to (spec D9, `integration-events.md`). A `CARRIER_NOT_WIRED` or a carrier failure is surfaced (`500`, element 14 RULE-7), never smoothed |
| P3-26 | Element 14 RULE-6 says an unmapped status "lands in `unknown`", and no `LegState.UNKNOWN` exists | read as element 03 RULE-4's own sentence — the status "leaves `legState` unchanged" — with the unmapped observation marked by `LegObservation.leg == null` and **every** payload kept (the aggregate holds a list, not a last-value slot), so two different unknown statuses are both available to a later correction |

## 17. Gates

| # | Question | Decision |
| --- | --- | --- |
| P3-21 | `sqt` after three new routes | `api.path-no-rpc-verb` counts **seven** hints now, one per `/processing/…` route — the paths are element 11's contract verbatim (phase-1 3.3, P2-27) — plus three `function_statements` **warnings** where the band only warns (the customer row's derivation, the aggregate's readiness command and its mutation). `0 error` throughout |
| P3-22 | The executable contract | 30 requests. `e2e/fulfillment-api.hurl` grew by phase 3's requests in the prototype's order: readiness, a readiness refusal, the label and the customer state. The refusal needs an order that is not ready, so the suite hands one over, claims it, scans one of its two positions and packs that one — the refusal the suite exercises is `notPacked`, and its value is now asserted (`$.reasons[0] == "notPacked"`), which pins the decision's own spelling on the wire; `noParcel` and `onHold` are asserted in the Java suites only (`positionsOpen`, DT-08-2, is unreachable). The label's `404` is asked at the end beside the other unknown-resource requests. The prototype's `documents/goods-issue-note` request stays **out**: element 05 is out of the plan (D15) |
| P3-23 | Coverage | 95.40 % instruction and 84.89 % branch, up from 94.3 % instruction: the carrier adapter is exercised through its translation tests only, exactly as the warehouse one is, and the configured-bean path has its own wiring test (`ConfiguredCarrierWiringTest`, P2-9's sibling) |
| P3-24 | The integration profile's connection pool | `spring.datasource.hikari.maximum-pool-size: 2` in `application-integration-test.yml`: the reused PostgreSQL container holds every test context at once, and fifteen connections per context exhausted it ("sorry, too many clients already") once phase 3 added three contexts |

## 18. Known gaps owned by later nodes

- **D11's third write trigger — warehouse `issue` at readiness — has no caller.** `WarehouseStock.issue` is unused by `src/main`,
  and the readiness node's goal names RF1 and RF2 only. Wiring it is not a one-line fix: the claim's reservation ids live in
  `WarehouseReservations` for the life of the claim and the aggregate deliberately does not carry them (their own javadoc says so),
  so `prove-dod` row 9 ("exactly one `reserve` on the granted claim and one `issue` on readiness") needs an owned
  processing → reservation store first. Recorded here as the owner's gap, not as delivered work.
- A blind resend after a **failed** registration asks the carrier again with the **same** reference (P3-2, Q-14-2: never a fresh
  reference) and is covered by `ParcelServiceTest.asksAgainWithTheSameReferenceWhenTheCarrierFailed`; nothing refuses the repeat
  itself, because no source gives `register` an idempotency key we could send.
- Element 05's goods issue note is out of the plan (D15); `GET …/documents/goods-issue-note` stays unimplemented and the matrix
  answers `denyAll()`.
- The carrier adapter is exercised against recorded payloads only; a live carrier is `R-14-1`/`R-14-2`, unchanged.
- Issues, hold and resume belong to phase 4.

---

# Phase 4 decisions and deviations — issues, the hold, and the resolver's dashboard

The same file, the same rules: what phase 4 of the plan had to choose, and where it departs from a rule it was built to follow.
Numbered `P4-n`.

## 19. Issues and hold (`issues-hold`)

| # | Question | Decision |
| --- | --- | --- |
| P4-1 | Element 04's board event is `issues: {processingId, issueId, kind}`, and element 17's dashboard shows the product, the reporter and the moment | `DomainEvent.IssueReported` carries the whole record the employee wrote — `kind`, `productNumber`, `reportedBy`, `reportedAt` besides the two ids. The addition is forced by `projection.no-cross-context-read`: the read slice may not open the aggregate for three fields, and the event is in-process only (D9 — no broker, no persisted contract to migrate) |
| P4-2 | Where an `IssueId` comes from (element 04 states the format, nothing states the generator) | a sequence, `issue_id_seq`, formatted `ISS-<year>-%04d` — the reasoning of phase-1 2.1 verbatim: two employees report at once and only a sequence gives both a number that is theirs. It mints behind `OrderProcessingRepository.nextIssueIdentity()`, the same recorded exception as `nextIdentity` (phase-1 2.3) |
| P4-3 | Element 11 says "one payload, one shape … two commands add to it" | `ProcessingSnapshot` grows three components — `issueId`, `orderState` (the same value as `state` under the name the contract fixed) and `closedIssueIds` — each an **absent key** unless that command wrote it (per-component `@JsonInclude(NON_NULL)`), so the state read and the other commands keep the shape they had. A wrapper record with `@JsonUnwrapped` was rejected: it puts a serialization trick behind a contract the hurl reads field by field |
| P4-4 | A second issue on a held order (H2) still creates a record | the answer is `201` for both H1 and H2 — a report is a fact that did not exist before, and the hold it did not place is visible as `openIssues: 2` and as the missing second `HoldPlaced`. The `200`-replay row of element 11's status table does not apply: nothing was re-applied |
| P4-5 | The refusal codes of the two new routes | `HOLD_REFUSED` answers the decision's own reason as the code — `NOT_STARTED` (H3) and `PAST_HANDOVER` (H4), both `409`, both element 11's derived rows. `ISSUE_NOT_REPORTED_BY_ASSIGNEE` answers `409 ACTOR_NOT_ALLOWED`, the code phase 2 established for "this actor may not" (P2-19b) — the assignee check itself is the aggregate's existing `ensureIssueIsReportedHere`, reused, not a second rule |
| P4-6 | An unknown id versus a malformed one in `closedIssueIds` | both are `404` (register D11 names the unknown case), but each keeps its own code — `ISSUE_UNKNOWN` and `ISSUE_ID_MALFORMED` — so a client can tell an id this order does not account for from a typo. Answering `409` for the malformed one would have made element 11's rule 3 ("unknown resource → 404, refused precondition → 409") say two things at once |
| P4-7 | `409 ISSUES_REMAIN` carries `openIssues` | the count is a detail of the exception (`ISSUES_REMAIN, processingId, remaining, remaining.size()`) and the one advice turns it into the `openIssues` field beside `error`. It is the same number the snapshot reports, so the two can never disagree |
| P4-8 | What `closedIssueIds` in the resume answer means | the ids that command **actually** closed, read back from the snapshot after the command — not an echo of the request. On H7 (nothing was held, nothing named) it is `[]`; on H5 it is the named ids; an id the request named that was never ours never reaches the answer, because it answers `404` first |
| P4-9 | Who may report and who may resume | reporting requires the assignee (element 04 RULE-2, enforced by the aggregate). Resuming checks no assignee: element 09 RULE-8 grants the lift to the resolver, who never holds the order, so an assignee check would make the resolver unable to work his own dashboard. Both routes are scoped per element 11's matrix (`employee` for `issues`, `employee` **and** `resolver` for `hold/resume`) |
| P4-10 | The hold blocks the whole order — what the suite proves on the wire | the e2e asks a scan (`409 ON_HOLD`) and a readiness (`409 NOT_READY`, `reasons[0] == "onHold"`) of a held order, then resumes and scans again (`200`). `NOT_READY.reasons[]` keeps the decision's own spelling (P3-13) |

## 20. The warehouse `issue` at readiness (`readiness-decision`, closing phase-3 gap 9)

| # | Question | Decision |
| --- | --- | --- |
| P4-11 | The processing → reservation correlation, which the aggregate must not carry | `WarehouseReservations` now **returns** what it took (one `ProcessingReservation` per position: the reservation id, the product, the location the units were allocated from and the quantity) and `WarehouseReservationsIntegration` writes those rows through the new `ProcessingReservationRepository` → `processing_reservation`. The aggregate stays exactly as wide as it was; readiness addresses the reservations by the processing id |
| P4-12 | Why a second reservation table instead of widening `warehouse_reservation` | the two describe different things and need opposite transaction rules. `warehouse_reservation` is the **warehouse's** side: it addresses the allocation rows and is written in `REQUIRES_NEW` transactions because a real allocation outlives a claim that fails (P2-14a). `processing_reservation` is **ours**: one row per reservation a claim of this order took, written in the claim's own transaction, so a claim that rolls back leaves no correlation behind. It also carries the two facts only the claim knows — the product and the location — without which `issue` could not name what left the shelf. Recorded as a deviation from the literal "reuse its repository" of the node's brief |
| P4-13 | How many `issue` calls a readiness makes | one per reservation, each **at most once** (`issued()` skips a row that already has a document; the adapter is idempotent too, P2-6). `prove-dod` row 9's "one `issue` on readiness" is the one-place order; a two-place order reserved twice and is issued twice, which is the same rule element 13 states ("at most once per reservation") |
| P4-14 | The order of the three effects, and what a failure leaves behind | RF1 (the local mark) is written, then RF2 (the collection request), then RF3 (`issue`) — all the one transaction the command owns, so a warehouse failure rolls the local mark back with it and an order is **never** left marked ready with no goods issue. The residual is the carrier's: a collection already requested cannot be un-asked (P3-25 restates it), and a warehouse that fails *after* an earlier reservation was booked out leaves that removal made, with no invented compensation (element 13 RULE-7) |
| P4-15 | A deployment with no warehouse adapter | a readiness for an order that holds **no** reservation of ours still succeeds — there is nothing to book out, and `WarehouseStock` is `Optional`-wired. The moment a reservation exists and nobody answers the port, the command is refused `WAREHOUSE_NOT_WIRED` rather than pretending the goods left (phase-1 2.6 stays true) |
| P4-16 | Where the goods issue note is read later | on the correlation row: `issue_document` + `issue_date`, stamped from the `IssueNote` the port answered, so a later reader (the WZ of element 05, out of the plan per D15) reads the document we numbered instead of asking the warehouse again. The adapter stamps its own row too (P2-6), so the two agree without a second call |

## 21. The issue dashboard (`issue-dashboard-projection`)

| # | Question | Decision |
| --- | --- | --- |
| P4-17 | Where the read slice lives | `shop.fulfillment.issues`, the fourth read slice beside `orders` and `customer`: it imports no write model, reaches the processing only through the events and the kernel, and `ArchitectureTest` gained the same six rules plus `issuesDependsOnNoWriteSlice` — and `writeSlicesDependOnNoReadSlice` / `kernelDependsOnNoSlice` name the new package too. No rule was deleted or narrowed |
| P4-18 | Element 17 says "grouped by order", the prototype reads `$.items[0].issueId` | the rows are **flat** and the grouping is the ordering: every row repeats its order (`processingId`, `orderState`, `assignee`), rows of one order sit together and the group reported first comes first (element 17's "oldest first" default). A nested `{order, issues[]}` envelope would have broken the one assertion the contract makes about this collection |
| P4-19 | Two tables | `issue_dashboard` (one row per issue: kind, product, reporter, reported-at, closed-at) and `issue_dashboard_order` (state, assignee, and the handover payload the product **name** is read from — the `projection.copy-of-foreign-payload` pattern of P3-18). A row whose order never arrived is not served, never served half (`projection.absent-read-model-is-null`) |
| P4-20 | How a row leaves the list | `closed_at`, set on `OrderResumed` for every open issue of that order. A partial lift cannot exist — H6 refuses a resume that leaves one open — and register D12's payload stays state-only, so the projection closes by order rather than by id list. Deleted rows were rejected: a replayed event would resurrect them, a stamped row cannot |
| P4-21 | `age` | served as `ageSeconds`, read at the moment the list is served from the projection's clock. No deadline, SLA, escalation or badge exists in any source (element 09 RULE-5), so nothing heavier than a number is invented |
| P4-22 | What the row must not carry | no quantity (element 04 Q-04-5: a partial close has no expression), no location (Q-17-2: the pick list owns locations), no money (D5), no action. There is no POST route on `/issues` at all — the security matrix denies every verb but `GET`, and a test asserts the `403` |
| P4-23 | The row's order-state field is called `orderState` | the issues command already answers that spelling for the same value, and a row on this list is an issue first, an order second; `state` beside `issueId` would read as the issue's own state, which no source gives it (element 04 RULE-7) |

## 22. Gates

| # | Question | Decision |
| --- | --- | --- |
| P4-24 | Coverage | **96.20 % instruction / 84.79 % branch**, up from 95.40 % / 84.89 %: the new read slice, the reservation store and the goods issue are all exercised, the adapter paths are exercised through their translation tests as before |
| P4-25 | `sqt` after two new routes and a read slice | `0 error`. New hints/warnings, all recorded rather than silenced: `vo.builder-when-wide` on `IssueReported` (six components; a domain event carries no builder anywhere in this context) and on `IssueDashboardEntry` (built by its own `ofNullable` factory, like `OrdersListEntry`), three `function_statements` warnings (`markReady`, the resolved-hold handler, `findOpen`) and one more `api.path-no-rpc-verb` for the `/processing/…` contract paths (phase-1 3.3, P2-27) |
| P4-26 | The executable contract | **46 requests**, up from 30: phase 4 appended the prototype's issue/dashboard/resume block in the prototype's order — issue (H1), second issue (H2), scan and readiness refused under the hold, dashboard `200` for the resolver and `403` for the employee, `409 ISSUES_REMAIN` with `openIssues`, `404 ISSUE_UNKNOWN`, the lift (H5), the emptied dashboard, the H7 no-op, and the prototype's third-order `409 ISSUES_REMAIN` (that order is handed over in the suite instead of assumed, phase-1 1.2's reasoning). Two assertions are the suite's own, not the prototype's: `$.items[0].orderState` on a dashboard row and `$.items[0] not exists` once the last hold is lifted |

## 23. Known gaps owned by later nodes

- The goods issue note has a home (P4-16) but no read route: element 05 is out of the plan (D15), so `GET …/documents/goods-issue-note`
  stays unimplemented and the matrix answers `denyAll()`.
- The stale-allocation sweeper (P2-14) and the adapter's behaviour against a live instance (R-13-2) stay open, unchanged.
- `release` still has no caller (D11 says "never"), so a reservation whose order is never marked ready holds its units — R-13-1
  remains a real risk and nothing in phase 4 shortens it.
- A resume closes every open issue of an order by construction (P4-20); if element 09 later allows a partial lift, the dashboard
  needs the closed ids on the event and this decision has to be revisited.

---

# Phase 5 decisions and deviations — the orders list's progression and the handover's content check

The same file, the same rules: what this fix had to choose, and where it departs from a rule it was built to follow. Numbered
`P5-n`.

## 24. The orders list row (element 15)

| # | Question | Decision |
| --- | --- | --- |
| P5-1 | Element 15 requires the chip to be `OrderProcessing.state` and the progress `n/m packed` from `positions[]`, refreshed on seven events; the row was fed by four of them | the row also handles `PositionPicked`, `PositionPacked`, `ReadyForDeliveryConfirmed` and `OrderDelivered`, each in the same find-or-create + record-fact + recompute shape as before. The packed positions are a **set of product numbers**, never a counter — P3-16's reasoning verbatim: a repeated `PositionPacked` is the same fact and must not advance the order to packed on its own — and `n` is the set's size when the list is served |
| P5-2 | `positions_packed` (migration 0004) was never written once the set exists | dropped in `0015-orders-list-progress.yaml`, the same changeset that adds `packed_products` (jsonb), `picked`, `ready_for_delivery` and `delivered`. `restored_state` (0014) stays: it is still the state a lift falls back on |
| P5-3 | `restored_state` won over every fact, so a delivered order read `assigned` (or whatever it was resumed to) for the rest of its life | the state is derived in one place — held, then delivered, then ready for delivery, then the furthest of the row's own facts and the restored state. The restored state is a **floor**, not an answer: the row lands on it only while its own facts have not reached that far (register D12, element 09 H5), so a pack or a readiness after a lift moves the chip. The ladder is `CustomerStateProjection`'s (P3-16), so the employee's chip and the customer's state read the same word for every state element 02 can reach — the two do order `held` and `delivered` differently, and a delivery can only follow `readyForDelivery`, so that difference cannot surface; recorded rather than unified |

## 25. The handover's idempotency key (element 12)

| # | Question | Decision |
| --- | --- | --- |
| P5-4 | Element 12 RULE-2 answers `200 duplicate` to a second delivery of the same `orderHash`, and element 01 RULE-1 says that hash *is* the canonical content hash; the intake compared the client's string alone, so any order sent under a used hash was answered `200` and never entered fulfillment | intake computes its **own** digest of the order content (`Order.canonicalDigest()`) and compares it with the digest of the order the processing was opened with. Same hash + same content is the documented `200 duplicate:true`; same hash + different content is `409 ORDER_HASH_CONFLICT`, a contract error rather than a duplicate — two handovers whose content differs cannot be the same order (RULE-1) |
| P5-5 | The canonical form — nothing defines it, and the client's hash cannot be re-derived from content | documented on `Order.canonicalDigest()`: the concatenation of name-tagged records, in this order — the form's version, `placedAt` (ISO-8601), one `line` record per line in **ascending product number** (the order the constructor already guarantees), `delivery` (method code, location kind, point id, four address components), `payment` (method code), `invoicing` (buyer name, tax id, four address components). Every component is written `-` when absent or `<length>:<text>` (length in UTF-16 units), so the form is **injective**: a component can never be read as another component or as another record, whatever characters it carries, and absent is never the empty text (`0:`). The earlier draft of this form was separator-delimited and relied on no component carrying the separators — a guarantee no value object enforces, which let a one-line order with a crafted `name` digest exactly like a two-line order (`OrderTest.digestsANameCarryingSeparatorsAwayFromTheLongerOrderItWouldImitate`, which fails on that draft). The digest is `sha256:` + lowercase hex; the claimed `orderHash` is **not** part of the form, and neither its format, its length nor a `sha256:` prefix is assumed |
| P5-6 | Where the digest is stored | nowhere: the processing's JSONB snapshot already carries the order it was opened with (`OrderProcessing.order`), so a repeat delivery re-digests that content. No column, no second source of truth for the same order, and a changed hash cannot make two different orders equal |
| P5-7 | The new refusal's code and body | `409 ORDER_HASH_CONFLICT` with `{"error":"ORDER_HASH_CONFLICT"}` — element 11's `Reason` shape, which the one advice already answers for every other `409`. The endpoint's authZ decision does not change (the `shopping` scope owns the handover), so `docs/security-decisions.md` is untouched |

## 26. Gates

| # | Question | Decision |
| --- | --- | --- |
| P5-8 | Coverage | **96.36 % instruction / 85.76 % branch**, up from P4-24's 96.20 % / 84.79 %: the four new handlers, the digest — including the order whose `name` carries the separators of the form the first draft used — and the conflict path each have their own cases, and the threshold (`0.8` instruction) is untouched |
| P5-9 | `sqt` after the fix | `0 error`, and no new hint or warning: the row's derivation is decomposed (`derivedState` → `reachedState` → `packedState` → `unpackedState` / `allPositionsPacked`) so `function_statements` stays inside the band, the way `CustomerStateProjection` does |
| P5-10 | The executable contract | **48 requests**, up from 46: `e2e/fulfillment-api.hurl` asks `409 ORDER_HASH_CONFLICT` straight after the duplicate handover (the same hash over a different line quantity), and one `GET /orders` after the readiness asserts the chip `readyForDelivery` beside `1/1 packed` — the two things element 15 requires and the four-handler list could never serve |

## 27. Known gaps owned by this node

- The progress counts a set of product numbers, not the aggregate's `positions[]`. An event naming a product the order does not
  carry is not expressible in element 02's machine, so no guard was written for it; a `PositionPacked` that arrives before its
  handover is counted the moment the handover lands (`n/total`), never before the row is served.
- The restored state and the facts are compared by `ProcessingState` ordinal. Both values on that path are pre-hold states and
  element 02 has no nested hold, so the comparison is total; a state inserted between `PACKED` and `READY_FOR_DELIVERY` would
  have to revisit it.
- `orderHash` is still what `findByOrderHash` looks up with a unique index behind it (phase-1 2.2): the content digest narrows
  *what* a repeat means, not how two concurrent first deliveries of the same hash are serialised.
