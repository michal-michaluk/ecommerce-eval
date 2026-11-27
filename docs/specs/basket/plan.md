# basket — Implementation Plan

Session: `2026-11-26T20:18:12Z` · branch `feat/basket` off `main` @ `62fed7a` · spec ticket #70 (closed) · plan ticket #83

## Tickets

- Spec ticket: [#70 basket — specification session](https://github.com/michal-michaluk/ecommerce-eval/issues/70) (closed)
- Plan ticket: [#83 basket — implementation plan](https://github.com/michal-michaluk/ecommerce-eval/issues/83)
- Impl: sub-tasks created by the `implement` skill; report and PRs recorded there

## Implementation Plan

### Available Infrastructure

- **Build:** Gradle 9.5.1 (wrapper per service), Java 25 toolchain, Spring Boot 4.1.0 — the new service is generated from the `microservice-java-spring` blueprint
- **Test:** JUnit 5 + AssertJ, Testcontainers (PostgreSQL), MockMvc, ArchUnit 1.4.2, hurl e2e, `prove` (0.1.0)
- **Lint / static:** `sqt check .` (project + `src/main/java` + `src/test/java` + per-slice configs), SpotBugs + findsecbugs, jacoco **80 % INSTRUCTION** (blueprint gate, `check.dependsOn jacocoTestCoverageVerification`)
- **Container runtime:** a **podman machine with a docker-compatible socket** (`~/.testcontainers.properties` points Testcontainers at it) — there is no Docker Desktop daemon, but Testcontainers **works**: PostgreSQL and Keycloak containers run, and `services/fulfillment`'s container-backed e2e proves it. Do not assume a container gate is unrunnable on this host; test it.
- **Cluster:** `run-apps` skill (`.agents/skills/run-apps/scripts/run-apps`) — k3s in podman, one shared Postgres/Keycloak/Kafka, host port `<service>` = 8080 catalog, 8081 fulfillment, **8082 shopping**
- **Gate standard for every node:** a Java node runs `sqt check .` (the `.sqt.yaml` set is where the architecture instructions are encoded — aggregate/vo/event/controller/projection suffixes, `structure.slice-package-private`, `contract.value-objects-only`, `api.*`) and, once it exists, `./gradlew test --tests '*Architecture*'` (the six ArchUnit factories); a wire node also runs the `prove` gates (`validate` after any change, `run` against the cluster in the contract phase).
- **Verification skills available:** `prove`, `run-apps` (repo-local), `review`, `demo`, `sqt`, `software-delivery-platform`, `implement`
- **Architecture docs:** `docs/arch/microservice-java-spring/` — `index.md` (phase gates, golden rule: never lower a gate), `code-structure.md`, `adding-a-bounded-context.md`, `arch-unit.md`, `ports.md`, `persistence-adapters.md`, `projection.md`, `http-adapter.md`, `error-handling.md`, `security.md`, `testing.md`, `integration-events.md`, `context-boundaries.md`, `infrastructure-json.md`, `domain-model-{aggregate,value-object,events,policy}.md`
- **Scope authority:** `docs/specs/basket/index.md` (36 DoD rows) and the register decisions `log.md` D38–D46 — no other service is integrated in this increment (no Kafka transport, no catalog HTTP client, no Pact)

### Plan

1. Prepare the service
- scaffold-shopping (index §Prove DoD, 07, test matrix)
2. Identity and money (parallel)
- customer-security (07 §Auth, DoD15/16, 24),
- kernel-money (03, DoD8/8b/9)
3. The basket
- basket-editor (02, 09, DoD20–22/28)
4. Calculations and decisions (parallel, after basket-editor)
- basket-price-calculation (04, DoD5/7/8b),
- basket-availability-decision (06, DoD27),
- basket-delivery-estimate (05, DoD26)
5. Offer read and handover (parallel, after basket-editor)
- offer-catalog-port (08, 10, DoD17–21),
- checkout-handover (09, DoD29/30)
6. HTTP API
- basket-http-api (07, 01, DoD10–16)
7. Contract on the cluster
- prove-harness (07, DoD3b/3c) -> prove-contract (07, DoD5–7/10–16/28–30) -> service-gates (DoD4/23/24/25)
8. Prove of done
- prove-dod (index §Prove DoD, 36 rows)
9. Evidence
- documentation (the slice's own docset, `add-docs`),
- review-branch (whole `feat/basket` diff)

---

### Prepare the service

#### scaffold-shopping
- **Goal:** create `services/shopping` from the `microservice-java-spring` blueprint (root package `shop.shopping`, db `shopping`), wired into the local shop cluster: `run-apps` app entry + `manifests/shopping` + one shared-Postgres database line.
- **Executor:** general
- **Docs:** `docs/arch/microservice-java-spring/index.md` (phase gates), `code-structure.md` (package layout, role suffixes), `arch-unit.md` (§split context), `testing.md`; spec `index.md` §Current code
- **IN / OUT:** IN: blueprint `microservice-java-spring` template, `services/fulfillment` as the live reference, `.agents/skills/run-apps/{SKILL.md,scripts/run-apps,manifests}`; OUT: `services/shopping/**` (build.gradle, settings.gradle, wrapper, `src/main/java/shop/shopping/{AppRunner,AppConfiguration,package-info}.java`, `tools/`, `mediators/package-info.java`, `src/test/java/**` kit, `src/test/resources/application-integration-test.yml`, `k8s/{base,overlays/k3d}`, `.sqt.yaml` ×4, `config/spotbugs/`, `e2e/healthcheck.hurl`, `AGENTS.md`, `docs/`), plus `.agents/skills/run-apps/scripts/run-apps` (`SHOPPING` App + `APP_ORDER`), `.agents/skills/run-apps/manifests/shopping/kustomization.yaml`, `.agents/skills/run-apps/manifests/infra/postgres.yaml` (`shopping:shopping:shopping`), `README.md` service table row. OUT excludes: any business class (later nodes own those) and the customer identity, realm document included (`customer-security`)
- **checks:**
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && ./gradlew compileJava`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && sqt check .`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce && test -f .agents/skills/run-apps/manifests/shopping/kustomization.yaml`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce && grep -q 'shopping:shopping:shopping' .agents/skills/run-apps/manifests/infra/postgres.yaml`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce && grep -q '"shopping"' .agents/skills/run-apps/scripts/run-apps`
- **review_prompt:** Review the work in /Users/michal/workspace/bottega-ai-mind/eval/ecommerce; do not inspect any other repository. Compare `services/shopping` against the `microservice-java-spring` blueprint output and against `services/fulfillment` (the live reference), and report every deviation in build.gradle gates (jacoco 80 %, spotbugs filters, jib image name `shopping`, main class `shop.shopping.AppRunner`), package layout, `.sqt.yaml` inheritance, k8s overlay shape (`overlays/k3d`, no `../../infra`), and `run-apps` registration. Also report anything the blueprint generated that the spec does not need. If a premise below is false, say so and stop. Do not edit files — report only.

### Identity and money

#### customer-security
- **Goal:** the customer identity, end to end — the service's own realm document (realm `shopping`, client scope `customer` included in the token, client `customer-client` with secret and direct access grant, user `customer`), the issuer wiring for the cluster, an `AuthFixture` that mints real tokens from a Keycloak Testcontainer, and a test proving the customer token carries `SCOPE_customer` while the service token does not.
- **Executor:** general
- **Docs:** spec element 07 §Contract decisions (auth row) and §Error contract (`401`/`403` empty), element 07 Q25/D27, index DoD15/DoD16; `docs/arch/microservice-java-spring/security.md` (per-endpoint decision procedure, issuer from an env var, never rely on Spring defaults), `testing.md` §auth profile; precedent `services/fulfillment/src/test/java/shop/fulfillment/auth/AuthFixture.java` + `services/fulfillment/src/test/resources/iot-realm.json`
- **IN / OUT:** IN: element 07 §Auth, the scaffolded service; OUT: `services/shopping/src/test/resources/shopping-realm.json`, `services/shopping/src/test/java/shop/shopping/auth/AuthFixture.java`, `services/shopping/src/test/java/shop/shopping/auth/CustomerIdentityTest.java`, `services/shopping/src/main/resources/application.yml` (`spring.security.oauth2.resourceserver.jwt.issuer-uri: ${JWT_ISSUER_URI:…}` only), `k8s/base/configmap.yaml` + `k8s/overlays/k3d/kustomization.yaml` (`JWT_ISSUER_URI=http://catalog-keycloak.catalog.svc.cluster.local:8080/realms/shopping`), `.agents/skills/run-apps/scripts/run-apps` (`SHOPPING.users = (("customer", "customer-client"),)`, `variables = (("customerToken", "customer"),)`, `App.realm_documents`). Not touched: `AppConfiguration.java`'s route matrix and every controller (`basket-http-api` owns the per-route decision), `kernel/`
- **checks:**
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && ./gradlew test --tests '*CustomerIdentity*'`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce && .agents/skills/run-apps/scripts/run-apps auth >/dev/null 2>&1 && python3 -c "import base64,json,re,pathlib; t=re.search(r\"SHOPPING_CUSTOMER_TOKEN='([^']+)'\", pathlib.Path('.agents/runtime/tokens.env').read_text()).group(1); p=t.split('.')[1]; c=json.loads(base64.urlsafe_b64decode(p+'='*(-len(p)%4))); assert 'customer' in c['scope'].split(), c['scope'];"`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce && python3 -c "import json; d=json.load(open('services/shopping/src/test/resources/shopping-realm.json')); assert d['realm']=='shopping'; assert any(s['name']=='customer' and s['attributes']['include.in.token.scope'] in ('true', True) for s in d['clientScopes']); assert any(c['clientId']=='customer-client' for c in d['clients']); assert any(u['username']=='customer' for u in d['users'])"`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce && grep -q 'customer-client' .agents/skills/run-apps/scripts/run-apps`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && ! grep -rqE 'keycloak\.auth|realms/iot' src/main && grep -q 'JWT_ISSUER_URI' src/main/resources/application.yml`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && sqt check .`
- **review_prompt:** Review the work in /Users/michal/workspace/bottega-ai-mind/eval/ecommerce; do not inspect any other repository. Check the customer identity against element 07 §Contract decisions and `docs/arch/microservice-java-spring/security.md`: the scope is named `customer` and reaches the token as `SCOPE_customer`; one client per scope with a secret and direct access grants; the realm document is the service's own `shopping-realm.json` and is the one `run-apps` imports; the issuer comes from `JWT_ISSUER_URI` and is never hardcoded to `realms/iot` or to a `https://keycloak.auth` placeholder; the service token the fulfillment slice uses must **not** satisfy a customer route (DoD16); and no route relies on Spring Security defaults. Report every piece of the identity that exists only in a test but not in the cluster wiring, or the other way round. If a premise below is false, say so and stop. Do not edit files — report only.

#### kernel-money
- **Goal:** `Money` in `kernel/` — `long amount` in units of `10^-(decimals+1)`, explicit currency, `kind charge|discount`, exact addition, exact multiplication by quantity, one half-up rounding step at the boundary, `pl-PL` rendering with a non-breaking space.
- **Executor:** general
- **Docs:** spec element 03 (RULE-1…RULE-8); `docs/arch/microservice-java-spring/domain-model-value-object.md` (record, compact-constructor invariant, context exception), `error-handling.md` (one exception per context), `infrastructure-json.md` (component not method reaches the wire)
- **IN / OUT:** IN: element 03, `02-basket.md` §Instance A amounts; OUT: `services/shopping/src/main/java/shop/shopping/kernel/Money.java`, `kernel/MoneyKind.java` (or nested), `src/test/java/shop/shopping/kernel/{MoneyTest,MoneyFixture}.java`. No other file touched
- **checks:**
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && ./gradlew test --tests '*Money*'`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && sqt check .`
- **review_prompt:** Review the work in /Users/michal/workspace/bottega-ai-mind/eval/ecommerce; do not inspect any other repository. Check `kernel/Money` against element 03 of `docs/specs/basket/03-money.md` row by row: the scale rule (`10^(decimals+1)`, never a hard-coded 1000), exact integer addition and multiplication, single half-up rounding (the `33333 → 3333` case), no negative storage, discount as `kind`, zero as a valid amount, and the exact `pl-PL` rendering `99,89 zł` with a non-breaking space. Report every rule without a test and every place a `double`/`BigDecimal` sneaks in. If a premise below is false, say so and stop. Do not edit files — report only.

### The basket

#### basket-editor
- **Goal:** the `BasketEditor` aggregate and its persistence — `BasketEditor` + `Basket` snapshot + items + selection, commands `AddToBasket`/`ChangeBasketQuantity`/`RemoveFromBasket`/`Select`/`HandOver`, `revision` monotonic per mutation, `lastHandover` derived field, JSONB document adapter with `@Version`, one active basket per customer enforced by a unique index, and the split-context test scaffolding (root `ArchitectureTest` + per-slice `structure.slice-package-private`).
- **Executor:** general
- **Docs:** spec element 02 (RULE-1…RULE-10, domain events), element 07 §Auto-creation, element 09 HAND-4/6; `docs/arch/microservice-java-spring/domain-model-aggregate.md`, `domain-model-events.md`, `persistence-adapters.md`, `arch-unit.md` §split context, `code-structure.md` §kernel and slices, `testing.md`
- **IN / OUT:** IN: elements 02/09, `kernel/Money`; OUT: `kernel/ShoppingException.java` (the one exception of the service, nested `enum Error`, D48), **`kernel/Money.java` + `src/test/java/shop/shopping/kernel/MoneyTest.java` migrated to it** — the money type currently refuses with `IllegalArgumentException`/`ArithmeticException`/`NullPointerException`, which no HTTP advice can map to `422 CURRENCY_MISMATCH` (review of node 3, D48) — plus `zero(currency, kind)` (a discount sum needs a discount-kind zero; `Money.zero` is charge-only) and the untested edges the same review listed (foreign-currency `toMinor`, a rounding carry into the whole unit, `kind` through `times`/`minus`, null components, locale immunity), `basket/{BasketEditor,Basket,BasketItem,BasketSelection,BasketRepository,DocumentBasketRepository,BasketService,DomainEvent}.java`, `basket/.sqt.yaml`, `src/test/java/shop/shopping/{ArchitectureTest,ArchitectureDescription}.java`, `basket/*Test`, `basket/BasketFixture.java`, `src/main/resources/db/changelog/0001-basket.yaml` + `db.changelog.yaml` include, `src/test/resources/application-integration-test.yml` (liquibase drop-first). Not touched: `basket/*Calculation*`, any controller. The document adapter must tolerate a document whose `kind` is absent (Jackson's creator is the canonical constructor, so a missing component is `null`, not `charge`)
- **checks:**
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && ./gradlew test --tests '*BasketEditor*' --tests '*DocumentBasketRepository*' --tests '*Architecture*'`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && sqt check .`
- **review_prompt:** Review the work in /Users/michal/workspace/bottega-ai-mind/eval/ecommerce; do not inspect any other repository. Check `BasketEditor` against element 02 RULE-1…RULE-10 and element 09 HAND-4/HAND-6: merge on repeat add, quantity ≥ 1, one item per offer, `unavailable` rows kept with their last-known snapshot, selection never exceeding the basket, no clearing on handover, `revision` monotonic per mutation, `lastHandover` digest identity, and the unique `(customerId, active)` index with the concurrent-create path. Then check the architecture: `BasketEditor` and `Basket` package-private, one all-args constructor, a static business-sentence factory, no getters/setters, events nested in one `DomainEvent` union with `_v1` names, JPA entity and Spring Data interface nested in the adapter, `@Version` present, no `tools/` or `org.` import in model code, slices package-private per `.sqt.yaml`. If a premise below is false, say so and stop. Do not edit files — report only.

### Calculations and decisions

#### basket-price-calculation
- **Goal:** `BasketPriceCalculation` — `productValue`, `discountValue`, `deliveryValue`, `totalWithDelivery` over the **selection**, excluded `unavailable`, included `outOfStock` while selected, per-shipment delivery rate with the SMART variant, no rounding outside `Money`.
- **Executor:** general
- **Docs:** spec element 04 (formula, S1–S12), element 03 RULE-4/5; `docs/arch/microservice-java-spring/domain-model-policy.md` (pure, no IO, no exception, total function)
- **IN / OUT:** IN: elements 03/04, `kernel/Money`, `basket/{Basket,BasketItem}`; OUT: `basket/BasketPriceCalculation.java`, `basket/BasketPrice.java`, `basket/DeliveryRates.java` (config record), `src/test/java/shop/shopping/basket/BasketPriceCalculationTest.java` (± `BasketPriceFixture`). Not touched: aggregate, controllers, other calculations
- **checks:**
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && ./gradlew test --tests '*BasketPriceCalculation*'`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && sqt check .`
- **review_prompt:** Review the work in /Users/michal/workspace/bottega-ai-mind/eval/ecommerce; do not inspect any other repository. Map every row S1–S12 of `docs/specs/basket/04-basket-price-calculation.md` to a test and report each missing or mis-expected row. Check that `discountValue` is reported but never added to `total`, that `currentPrice` is never recomputed from price and discount, that delivery is charged once per seller shipment with ≥1 selected item, that the SMART channel makes it zero, and that empty selection yields `0,00 zł` rather than "unknown". If a premise below is false, say so and stop. Do not edit files — report only.

#### basket-availability-decision
- **Goal:** `OfferAvailabilityDecision` — total function over `(offerState, stock, readAt)` producing `available|outOfStock|unavailable` with `reason` and `stale`, implementing A1–A5 (fail-closed) and exposing the blocking set as a value, never as a view concern.
- **Executor:** general
- **Docs:** spec element 06 (interface, A1–A5, blocking set, I1–I7), element 08 §Failure mapping; `docs/arch/microservice-java-spring/domain-model-policy.md` (decision is total, no `Optional`, no exception)
- **IN / OUT:** IN: element 06, `kernel/Money` only if an observation carries a price (otherwise not); OUT: `basket/OfferAvailabilityDecision.java`, **`kernel/Availability.java`** (status + reason + stale — it crosses to the checkout slice, element 09 rec 1), `basket/OfferObservation.java`, `src/test/java/shop/shopping/basket/OfferAvailabilityDecisionTest.java`. Not touched: aggregate, price, estimate, controllers
- **checks:**
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && ./gradlew test --tests '*OfferAvailabilityDecision*'`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && sqt check .`
- **review_prompt:** Review the work in /Users/michal/workspace/bottega-ai-mind/eval/ecommerce; do not inspect any other repository. Check `OfferAvailabilityDecision` against element 06 A1–A5 and the interface scenarios I1–I7 of `docs/specs/basket/06-offer-availability-decision.md` one row at a time, naming any row without a test. Verify the decision is total (no `Optional`, no `null` fallthrough, no exception), that a failed read never keeps a previous `available` (A4 fail-closed), that `stale` alone never blocks and only `outOfStock`/`unavailable` do, and that the blocking set is a property of the outcome rather than of a caller. If a premise below is false, say so and stop. Do not edit files — report only.

#### basket-delivery-estimate
- **Goal:** `BasketDeliveryEstimate` — `estimate(request) → Estimate` with `kind estimate|unknown`, an inclusive day range and `source`, the hardcoded `today + 2 … today + 4` calendar-day default in `Europe/Warsaw`, one estimate per seller shipment, `unknown` rendering nothing, never an exception.
- **Executor:** general
- **Docs:** spec element 05 (interface, config keys incl. `basket.estimate.zone`, I1–I7, D36/D43); `docs/arch/microservice-java-spring/domain-model-policy.md`, `infrastructure-json.md` §clock (injected `Clock`, never `Instant.now()`)
- **IN / OUT:** IN: elements 02/05/06, aggregate's availability value; OUT: `basket/BasketDeliveryEstimate.java`, `basket/Estimate.java`, `basket/EstimateSettings.java` (`@ConfigurationProperties`), `src/test/java/shop/shopping/basket/BasketDeliveryEstimateTest.java`. Not touched: aggregate, price, availability, controllers
- **checks:**
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && ./gradlew test --tests '*BasketDeliveryEstimate*'`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && sqt check .`
- **review_prompt:** Review the work in /Users/michal/workspace/bottega-ai-mind/eval/ecommerce; do not inspect any other repository. Check `BasketDeliveryEstimate` against element 05 I1–I7 with a fixed `Clock` in `Europe/Warsaw`: `today+2 … today+4` calendar days, `unknown` when nothing is handable or the config is absent, blocked rows contributing nothing, SMART not changing the range, one estimate per seller shipment and the latest `to` shown. Report any use of `Instant.now()`/`LocalDate.now()` instead of the injected clock, and any place the estimate feeds a price. If a premise below is false, say so and stop. Do not edit files — report only.

### Offer read and handover

#### offer-catalog-port
- **Goal:** the `OfferCatalog` contract port and its in-process application — and the one place a price could be misused: map `currentPrice`/`discount` (never `price`) into `BasketItem.LastKnown`, `kind` explicitly (`charge` for the price, `discount` for the discount, D53), make a seller-less row's placeholder **unique per offer** so two unknown sellers are never charged one shipment rate, and decide explicitly whether element 08's wider port record **widens** the existing `basket/OfferObservation` or is a separate public type — today the decision's observation is a slice type carrying only `offerId`, `offerState`, `stock`, `readAt`, while element 08's record also needs `seller`, `title`, `pictureUrl`, `price`, `currentPrice`, `discount`; if separate, the mapping is explicit and D53's price discipline applies — one batched read per basket, the fixture adapter with fault injection (`503`, `401`, missing ids, `stock: notRead`), and the ingestion command that applies an observation idempotently with `readAt` ordering and never creates an item.
- **Executor:** general
- **Docs:** spec element 08 (port, fields consumed, failure mapping, §Increments), element 10 (EVT-2…EVT-6, status paragraph D39), element 06 A3/A4/A5; `docs/arch/microservice-java-spring/ports.md` (contract port, primary port), `context-boundaries.md` (listener never throws, copy not pointer), `integration-events.md` (§listening, in-process only)
- **IN / OUT:** IN: elements 06/08/10, `basket/{Basket,BasketService,Availability}`, `kernel/Money`; OUT: `basket/OfferCatalog.java` (**package-private secondary port** — the basket declares it, a fixture adapter in the same slice implements it, D57), `basket/{FixtureOfferCatalog,OfferRecord,OfferObservationCommand}.java` (the ledger lives on the fixture; no read settings exist in this increment, D60) + the service method applying it, `src/test/java/shop/shopping/basket/{OfferCatalogTest,OfferIngestionTest,OfferCatalogFixture.java}` (the ledger lives on the fixture, D60). Not touched: price, estimate, controllers, `OfferAvailabilityDecision`
- **checks:**
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && ./gradlew test --tests '*OfferCatalog*' --tests '*OfferIngestion*'`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && sqt check .`
- **review_prompt:** Review the work in /Users/michal/workspace/bottega-ai-mind/eval/ecommerce; do not inspect any other repository. Check the `OfferCatalog` port and its application against element 08 §Failure mapping and element 10 EVT-2…EVT-6: batched read (never one call per item), one observation applied by snapshot replacement, the same observation twice changes nothing (DoD20), an observation older than the item's `readAt` is discarded (DoD21), an unknown offer never creates an item, a failed read greys the row instead of failing the basket, and no reservation operation exists on the port or anywhere in `shop.shopping` (DoD17). Report any Kafka, HTTP-client or `@KafkaListener` code — the transport is out of scope (D39). If a premise below is false, say so and stop. Do not edit files — report only.

#### checkout-handover
- **Goal:** the in-process handover — `kernel/` contract port `CheckoutHandovers`; while composing `BasketPriceCalculation` here, bind element 04's two configuration keys (`basket.delivery.rate`, `basket.delivery.freeWhenSmart`) through `BasketConfiguration.Settings` so `DeliveryRates.defaultRates()` is not the only source, and add a non-PLN basket test (a hard-coded `"PLN"` inside the calculation currently passes every test). **Also wire the estimate**: construct `BasketDeliveryEstimate` as a bean and ship `basket.estimate.{default-days.from,default-days.to,zone,fallback}` in `application.yml` **and** `k8s/base/configmap.yaml` + its `configMapKeyRef` — without them every deployed basket answers `unknown`, the delivery line is hidden and DoV5 reads 0 %; put the latest-`to`-across-shipments aggregation in the payload builder here too the basket side composing the payload (selection, totals, estimate, `revision`, digest identity), the thin `checkout` slice that receives it, and the refusals (`NOTHING_SELECTED`, `BLOCKED_BY_ITEMS` with `reasons[]`, `BASKET_INACTIVE`).
- **Executor:** general
- **Docs:** spec element 09 (contract, §Shape of record, HAND-1…HAND-6, failure outcomes), element 07 §Error contract; `docs/arch/microservice-java-spring/context-boundaries.md` (pattern 4, contract value objects only), `code-structure.md` §kernel + slice isolation, `arch-unit.md` §split context
- **IN / OUT:** IN: elements 02/04/05/06/09, `kernel/{Money,Availability}`, `basket/{BasketService,BasketPriceCalculation,BasketDeliveryEstimate}`; OUT: `kernel/CheckoutHandovers.java`, `kernel/CheckoutHandover.java`, `kernel/HandoverRefusal.java`, `checkout/CheckoutService.java`, `checkout/package-info.java`, `checkout/.sqt.yaml`, `src/test/java/shop/shopping/{checkout/CheckoutServiceTest.java,basket/BasketHandoverTest.java}`. Not touched: controllers, price/estimate/availability internals
- **checks:**
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && ./gradlew test --tests '*Handover*' --tests '*CheckoutService*' --tests '*Architecture*'`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce && grep -q 'estimate:' services/shopping/src/main/resources/application.yml && grep -q 'default-days' services/shopping/k8s/base/configmap.yaml`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && ./gradlew test --tests '*BasketDeliveryEstimate*' --tests '*BasketPriceCalculation*'`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && sqt check .`
- **review_prompt:** Review the work in /Users/michal/workspace/bottega-ai-mind/eval/ecommerce; do not inspect any other repository. Check the handover against element 09 HAND-1…HAND-6 and its §Shape of record: only handable rows cross (no `outOfStock`, no `unavailable`; `stale` alone does not block), unselected rows are absent rather than zero, the basket is neither cleared nor locked, `revision` and `lastHandover` identity make a repeat return the same handover, and the basket never creates an order. Verify the slice boundary: `basket` names no `checkout` type, the contract lives in `kernel/` and mentions value objects only, and the checkout slice's service is the only implementation. If a premise below is false, say so and stop. Do not edit files — report only.

### HTTP API

#### basket-http-api
- **Goal:** the six routes of element 07 (including the pull on render — `GET` reads the basket's offers through `OfferCatalog` and applies the observations before computing, D10/D60 — and the token-claim → `Channel` mapping, with an unrecognised claim reading as `standard`, D54) — `GET /baskets`, `GET /baskets/{basketId}`, `PATCH …/items/{offerId}`, `PATCH …/items`, `PATCH …/selection`, `PATCH …/checkout` — with the `BasketsProjection` read model, the error advice, and the `SCOPE_customer` matrix (401/403 empty bodies, `denyAll` fallback).
- **Executor:** general
- **Docs:** spec element 07 (all contract decisions, error contract, §Recorded deviations), element 01 (payload shape, `pl-PL` rendering), register D48 (one `ShoppingException` in `kernel/`, mapped here to status + code), `prototypes/basket-api.prove.md` (the contract of record); `docs/arch/microservice-java-spring/http-adapter.md`, `error-handling.md`, `projection.md`, `security.md`, `code-structure.md` §adapters, plus `services/fulfillment/src/main/java/shop/fulfillment/{AppConfiguration,FulfillmentErrorAdvice}.java` as the live precedent
- **IN / OUT:** IN: everything above, plus the realm and `AuthFixture` from `customer-security`; **note:** `select` emits no event (RULE-10), so a `BasketsProjection` fed by `AddedToBasket`/`RemovedFromBasket` would serve a stale `selected` flag — the per-basket read must read the write model, and only the list endpoint may answer from the projection; OUT: `basket/{BasketController,BasketItemsController,BasketSelectionController,BasketCheckoutController,BasketErrorAdvice,BasketsProjection,BasketContent,BasketSummary}.java`, `AppConfiguration.java` — the per-route matrix only, one `hasAuthority(Scopes.CUSTOMER)` per route with `anyRequest().denyAll()` as the fallback and empty 401/403 bodies — and `src/test/java/shop/shopping/basket/{Basket*ControllerTest,BasketsProjectionTest,HttpFixture}.java`. Not touched: `kernel/`, calculations, `BasketEditor` internals, the realm document (`customer-security`)
- **checks:**
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && ./gradlew test --tests '*ControllerTest' --tests '*ProjectionTest'`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && sqt check .`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/docs/specs/basket && prove validate prototypes/*.prove.md`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && ./gradlew test --tests '*Architecture*'`
- **review_prompt:** Review the work in /Users/michal/workspace/bottega-ai-mind/eval/ecommerce; do not inspect any other repository. Compare the controllers and payloads with `docs/specs/basket/prototypes/basket-api.prove.md` step by step (24 steps) and with element 07 §Error contract: verbs, paths, response key sets, money shape `{amount, currency, kind}`, `deliveryEstimate` null-vs-range, `reasons[]` always an array of objects, and the five `422` codes. Check that `422`-over-`409` and the `GET /baskets` write are documented deviations (element 07 §Recorded deviations), that `BasketsProjection` is the only write behind a read, that every route has an explicit security rule with `denyAll()` as the fallback, that 401/403 bodies are empty, and that no controller injects a repository. If a premise below is false, say so and stop. Do not edit files — report only.

### Contract on the cluster

#### prove-harness
- **Goal:** make `prove run` reproducible against the local k3s cluster — `prototypes/prove.yaml` with `env.local` for port 8082, a runner that mints the customer and service tokens and passes `--var base_url/customer_token/service_token/est_from/est_to`, and a `./gradlew e2eProve`-equivalent entry point.
- **Executor:** general
- **Docs:** spec element 07 §Spec artifact and its command block, index DoD3b/DoD3c; `~/.agents/skills/prove/references/variables.md` (four tiers: capture > `--var` > `**vars:**` > env set), `.agents/skills/run-apps/SKILL.md` §auth/§test
- **IN / OUT:** IN: `docs/specs/basket/prototypes/basket-api.prove.md`, `run-apps` token minting; OUT: `docs/specs/basket/prototypes/prove.yaml`, `docs/specs/basket/prototypes/prove-local.sh`, `services/shopping/scripts/prove.sh` (or the gradle task that calls it), and one row in `services/shopping/docs/runbook/`. Not touched: the scenario's goldens, any `src/main` file
- **checks:**
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/docs/specs/basket && prove validate prototypes/*.prove.md`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce && bash -n docs/specs/basket/prototypes/prove-local.sh`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce && test -f docs/specs/basket/prototypes/prove.yaml`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/docs/specs/basket && prove explain prototypes/basket-api.prove.md --format json | python3 -c "import json,sys; d=json.load(sys.stdin); assert d['status']=='success' and len(d['data']['files'][0]['steps'])==24"`
- **review_prompt:** Review the work in /Users/michal/workspace/bottega-ai-mind/eval/ecommerce; do not inspect any other repository. Check that `prove.yaml` and the runner make `prove run prototypes/basket-api.prove.md` reproducible against the local cluster: base URL 8082 (the `run-apps` port for the third app), both tokens minted from the realm, `est_from`/`est_to` computed as `today+2`/`today+4` in `Europe/Warsaw`, and `--var` used for every value the document's `**vars:**` would otherwise shadow (prove's precedence: `--var` beats the markdown block beats the env set). Report any secret written to disk un-scoped, any hard-coded date, and any step that cannot authenticate. If a premise below is false, say so and stop. Do not edit files — report only.

#### prove-contract
- **Goal:** deploy `shopping` into the shop cluster and make all 24 steps of `basket-api.prove.md` pass — starting with the **seeded fixture catalog** (D67): widen `FixtureOfferCatalog` so it can express the scenario's offers (per-offer seller, price pair, currency, and a *described* removed offer, not just `null`), then seed it with them and a fault queue so steps 15 and 20 can turn a row out of stock and one removed — the whole client contract, including the failure paths.
- **Executor:** general
- **Docs:** spec element 07 and the scenario; index DoD5–DoD7, DoD10–DoD16, DoD28–DoD30, DoD19
- **IN / OUT:** IN: nodes 2–6, `.agents/skills/run-apps/scripts/run-apps`; OUT: `services/shopping/**` fixes discovered by the contract, `docs/specs/basket/prototypes/prove.yaml` only if the harness is wrong. Not touched: any golden body of the scenario (a wrong golden is a spec change, reported not edited)
- **checks:**
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && sqt check .`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce && .agents/skills/run-apps/scripts/run-apps deploy shopping`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce && .agents/skills/run-apps/scripts/run-apps status`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce && bash docs/specs/basket/prototypes/prove-local.sh`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/docs/specs/basket && prove validate prototypes/*.prove.md`
- **review_prompt:** Review the work in /Users/michal/workspace/bottega-ai-mind/eval/ecommerce; do not inspect any other repository. Check the passing contract against `docs/specs/basket/index.md` §Prove Definition of Done and `07-basket-http-api.md`: every one of the 24 steps executes (none skipped), the totals in steps 4/6/12/17 equal the spec's arithmetic, the two handover steps prove `handoverId == basketId` and a stable `revision`, the out-of-stock and unavailable rows block with `reasons[]`, 401/403 are empty, and no step was weakened, marked `ignore others`, or reordered to hide a failure. If a premise below is false, say so and stop. Do not edit files — report only.

#### service-gates
- **Goal:** the service's own quality gates green for the whole feature — build with coverage verification, ArchUnit, sqt, SpotBugs.
- **Executor:** general
- **Docs:** `docs/arch/microservice-java-spring/index.md` §phase gates (never lower a threshold), `arch-unit.md`, `tech-update.md` §strict rules; index DoD4/DoD23/DoD24/DoD25
- **IN / OUT:** IN: the whole `services/shopping` tree; OUT: the tests and any production change needed to reach 80 % INSTRUCTION, `.sqt.yaml` fixes, ArchUnit rule additions (never weakenings). Not touched: gate thresholds, blueprints, another service
- **checks:**
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && ./gradlew build`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && ./gradlew test --tests '*Architecture*'`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && sqt check .`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce/services/shopping && ./gradlew jacocoTestCoverageVerification --console=plain --rerun-tasks | tee /tmp/jacoco.log; ! grep -q 'SKIPPED' /tmp/jacoco.log`
- **review_prompt:** Review the work in /Users/michal/workspace/bottega-ai-mind/eval/ecommerce; do not inspect any other repository. Report every way a gate was satisfied rather than met: a lowered jacoco minimum or a widened exclusion list, an excluded package, `allowEmptyShould(true)` on a non-empty layer, a weakened or deleted ArchUnit rule, a disabled SpotBugs filter, a suppressed sqt rule, `@Generated` or `lombok.Generated` abuse in coverage. Verify the specific rows: coverage ≥ 80 % INSTRUCTION, `basket` references no `checkout` type, `structure.slice-package-private` holds for both slices, no `reserve*` operation exists (DoD17). If a premise below is false, say so and stop. Do not edit files — report only.

### Prove of done

#### prove-dod
- **Goal:** execute all 36 rows of `index.md` §Prove Definition of Done and record row → command → observed → verdict.
- **Executor:** general
- **Docs:** `docs/specs/basket/index.md` §Prove Definition of Done (36 rows), `docs/specs/basket/prototypes/*`; `docs/prove-of-done.md` (fulfillment's report) as the shape precedent
- **IN / OUT:** IN: the whole spec folder, `services/shopping`, the cluster; OUT: `docs/specs/basket/prove-of-done.md` with one row per DoD row, the two rows that need a live restart recorded with the observed evidence
- **checks:**
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce && test -f docs/specs/basket/prove-of-done.md`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce && test "$(grep -c '^| DoD' docs/specs/basket/prove-of-done.md)" -ge 36`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce && ! grep -qE 'NOT RUN|PENDING' docs/specs/basket/prove-of-done.md`
- **review_prompt:** Review the work in /Users/michal/workspace/bottega-ai-mind/eval/ecommerce; do not inspect any other repository. Check `docs/specs/basket/prove-of-done.md` against all 36 rows of `docs/specs/basket/index.md` §Prove Definition of Done: report every row missing, unexecuted, marked PASS without an observed result, or whose command does not actually exercise the row. Pay particular attention to DoD17 (ArchUnit + ledger), DoD20/21 (in-process observation), DoD22 (concurrent create), DoD28 (survives a restart) and DoD30 (captured `revision` compared). If a premise below is false, say so and stop. Do not edit files — report only.

### Evidence

#### documentation
- **Goal:** the basket slice's own docset — system documentation, integrator guide, operations manual, business guide — built by the `add-docs` skill from the repository's own facts, as a **second configuration of the existing pipeline** (`DOCS_CONFIG=docs/scripts/config.shopping.json`): `services/fulfillment`'s docset, its pinned `expected_counts` and every authored chapter stay untouched and must stay green.
- **Executor:** general
- **Docs:** `~/.agents/skills/add-docs/SKILL.md` and its references (`docset`, `chapter-catalog`, `pipeline-contracts`, `manual-authoring`, `composition-quarto`, `quality-control`, `document-kind-index`); `docs/scripts/README.md` and `docs/scripts/config.py` (`DOCS_CONFIG` is the supported switch); `docs/specs/basket/index.md` + elements 01–10 (the facts the manual tier must trace to)
- **IN / OUT:** IN: `services/shopping/**` (code, `k8s/`, `db/changelog`, `e2e/`, the seeded offer fixture), `docs/specs/basket/**`, `docs/scripts/{config.py,extract-data.py,render-docs.py,verify-evidence.py,docs-gate.py}`; OUT: `docs/scripts/config.shopping.json` (paths, sources, `expected_counts` read from a real extract, `documents`), `docs/generated/shopping/**`, `docs/{shopping-system-documentation,shopping-integrator-guide,shopping-operations-manual,shopping-business-guide}.qmd` wired into `docs/_quarto.yml`, `docs/decisions/genre-provenance.md` rows for the four genres, and the authored manual-tier chapters. **Not touched:** `docs/scripts/config.json`, `docs/{system-documentation,integrator-guide,business-guide}.qmd`, `docs/generated/{data,chapters,diagrams}` (fulfillment's)
- **checks:**
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce && DOCS_CONFIG=docs/scripts/config.shopping.json python3 docs/scripts/docs-gate.py --fast`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce && DOCS_CONFIG=docs/scripts/config.shopping.json python3 docs/scripts/docs-gate.py`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce && for f in docs/shopping-*.qmd; do test -s "$f"; done`
  - `cd /Users/michal/workspace/bottega-ai-mind/eval/ecommerce && python3 docs/scripts/docs-gate.py --fast` (fulfillment's docset is unchanged — the regression guard)
- **review_prompt:** Review the work in /Users/michal/workspace/bottega-ai-mind/eval/ecommerce; do not inspect any other repository. Judge the new docset against the `add-docs` skill's three tiers: every factual sentence traces to an `<!-- evidence: path:line -->` marker that resolves, every judgement traces to a recorded operator answer or a numbered gap, and no chapter was written from imagination. Check that the four documents match the genre kinds the skill prescribes and that `docs/decisions/genre-provenance.md` carries a row per genre claim; that `expected_counts` are the numbers a real extract produced (not aspirational); and that `services/fulfillment`'s docset is byte-identical to before. Report every unsupported claim, every count that disagrees with the extract, and every chapter that merely restates a table. If a premise below is false, say so and stop. Do not edit files — report only.

#### review-branch
- **Goal:** review the whole `feat/basket` diff (merge-base `main` → HEAD) against the spec and the architecture docs; report findings, do not edit.
- **Executor:** review
- **Docs:** `docs/specs/basket/index.md` + elements 01–10, `docs/specs/basket/log.md` D38–D46, `docs/arch/microservice-java-spring/*`
- **IN / OUT:** IN: `git diff main...HEAD` for `services/shopping`, `.agents/skills/run-apps`, `docs/specs/basket`, `demo/`; OUT: a findings report (issues · risks · gaps, each with file:line) as the final message

---

## Open Topics

- **The catalogue read and the event transport are still on `feat/catalog-domain-model`** (unmerged, issue #40 with a failing pitest gate). When it lands, element 08 gains its HTTP adapter and element 10 its consumer; nothing in this plan has to change shape (D38/D39).
- **The `checkout` slice is a receiver only** — it answers the handover and creates no order. Its own spec (D4) supersedes that stub.
- **Several active baskets per customer** are deferred; the unique index makes that a migration, not a redesign (D42).
- **The architecture test cannot see two things** (found by node 4, true of both services): `modelDependencies` does not cover the root-level `kernel/` package (per `arch-unit.md` a kernel gets `modelIsolation` only), so a kernel type could import a framework class unnoticed; and classification is by suffix, so a `*Configuration` wiring class inside a slice is treated as model code and its Spring annotations are allowed. Both are doctrine gaps — a shape check in `.sqt.yaml` would close them for this service.
- **Before `basket-http-api` wires the pull**: the currency rule on the application path has a decision but no test yet (D63), the `EPOCH` rehydration has no stored-document test (only an in-memory one), and no test carries two rows with different `readAt`s — the evidence that `readAt` belongs per row rather than per basket.
- **The container path is real** (node 7 asserted otherwise and was wrong): `./gradlew test --tests '*DocumentBasketRepository*' --tests '*CustomerIdentityToken*' --rerun-tasks` passes here — 9 + 4 tests against a real Postgres and a real Keycloak container.
- **`sqt` reports seven accepted hints**: four `vo.builder-when-wide` on `Handover`, `Line`, `Basket` and `BasketItem` after the dead `@Builder`s were removed (the named factories are the construction path and the document adapter uses the canonical constructor), and three `event.past-tense` on `AddedToBasket`, `RemovedFromBasket` and `GoToCheckout` — those are element 02's own names, taken from the board, and renaming them would break the event vocabulary the spec fixes.
- **The generated docset does not cover `shopping`** — `docs/scripts/config.json` is pinned to `services/fulfillment` with exact expected counts; repointing it would break every authored chapter, so the service carries its own `docs/runbook` instead.
- **No `retro` skill is installed**, so the plan's retrospective phase is dropped rather than improvised.
- **The demo recording is skipped by operator decision** — the slice is an HTTP API with no UI, and the contract already produces a replayable artefact (`prototypes/prove-local.sh`, 24 steps). The evidence phase therefore carries the docset and the branch review only.
- **DoV1–DoV5 have no baseline** — they stay hypotheses until production data exists (R10).
