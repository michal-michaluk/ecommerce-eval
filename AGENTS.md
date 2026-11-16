# AGENTS.md — ecommerce eval

Eval monorepo: `docs/raw` is the business ground truth, `services/` holds one microservice per bounded context
built from it. Services are scaffolded from the `microservice-java-spring` blueprint — architecture-first,
read the matching doc before writing code.

<br />

## Architecture docs

`docs/arch/microservice-java-spring/index.md` owns the mandatory workflow, the task routing table and the
hard constraints. Read it first; the table below is the entry index.

| File | When to read |
| --- | --- |
| `docs/arch/microservice-java-spring/index.md` | always, before the first code change in any service |
| `docs/arch/microservice-java-spring/code-structure.md` | adding, moving or naming classes and packages |
| `docs/arch/microservice-java-spring/context-boundaries.md` | a task touches two contexts, a mediator or a contract port |
| `docs/arch/microservice-java-spring/adding-a-bounded-context.md` | starting a new context slice |
| `docs/arch/microservice-java-spring/domain-model-aggregate.md` | aggregate lifecycle, command methods, snapshot, invariants |
| `docs/arch/microservice-java-spring/domain-model-value-object.md` | records, compact-constructor invariants, units |
| `docs/arch/microservice-java-spring/domain-model-events.md` | domain events, payload, publication order |
| `docs/arch/microservice-java-spring/domain-model-policy.md` | a calculation or decision that accumulates rules |
| `docs/arch/microservice-java-spring/error-handling.md` | raising or translating a failure |
| `docs/arch/microservice-java-spring/http-adapter.md` | REST endpoint — thin controller, command record, rejection mapping |
| `docs/arch/microservice-java-spring/protocol-adapters.md` | inbound protocol endpoint translated to a mediator or aggregate |
| `docs/arch/microservice-java-spring/persistence-adapters.md` | persisted shape — snapshot, event-sourcing, normalizing adapters |
| `docs/arch/microservice-java-spring/projection.md` | read model, idempotent handling, rebuild |
| `docs/arch/microservice-java-spring/integration-events.md` | publishing or consuming across the broker |
| `docs/arch/microservice-java-spring/ports.md` | declaring an in-context port and its fake |
| `docs/arch/microservice-java-spring/infrastructure-json.md` | serialization, event types, configuration, clock |
| `docs/arch/microservice-java-spring/security.md` | a new endpoint needs an authN/authZ decision |
| `docs/arch/microservice-java-spring/testing.md` | writing any test — pyramid, fixtures, integration tests, asserts |
| `docs/arch/microservice-java-spring/arch-unit.md` | touching ArchUnit rules or adding a context's architecture test |
| `docs/arch/microservice-java-spring/tech-update.md` | upgrading dependencies, build plugins, base images |

<br />

## Business materials

| File | When to read |
| --- | --- |
| `docs/raw/ecommerce-eventstorming.jpg` | verifying a flow against the primary source diagram |
| `docs/raw/ecommerce-eventstorming.improved.txt` | verifying wording or intent the summary flattened |
| `docs/raw/ecommerce-eventstorming.md` | naming events, commands and policies across the platform |
| `docs/raw/platform-overview.md` | deciding which slice a feature belongs to; stakeholders and actors |
| `docs/raw/product-catalog.md` | catalog slice: draft, review, publication, pricing, FE↔BE contract |
| `docs/raw/shopping.md` | shopping slice: browse, basket, checkout, Gherkin scenarios |
| `docs/raw/fulfillment.md` | fulfillment slice: pick, pack, ship, delivery, issue handling |
| `docs/raw/product-description.json` | sample offer document for catalog read models |
| `docs/raw/consignment-note.json` | sample carrier consignment note |
| `docs/raw/goods-issue-note.json` | sample goods issue note |

<br />

## External systems

| File | When to read |
| --- | --- |
| `docs/api/warehouse-api.md` | fulfillment must talk to the warehouse — endpoints in scope, the consumer-side port, adapter rules |
| `docs/api/inventree-openapi.json` | verbatim request/response schema for those endpoints |

<br />

## Services

| Service | Bounded context | Gate |
| --- | --- | --- |
| `services/catalog` | product catalog — draft, review, publication, pricing | `cd services/catalog && ./gradlew build` · `./gradlew e2eHurl` (P5, needs `hurl`) |

`./gradlew build` is the proof of done: compile, tests, jacoco, spotbugs. `e2eHurl` is the separate proof of P5 (the HTTP contract
of 12 over a booted service); `build` excludes it, because it boots Docker, Keycloak and the application. A change is finished when it is green
and the touched docs above still match the code.

New service: `scaffold generate microservice-java-spring services/<name> micro_name=<package> db_name=<name>`,
then `bash hooks/post-generate.sh <package>` and add its row above.

<br />

## Reference example

`.agents/blueprints/microservice-java-spring/` is the generated reference app the docs describe — read it to
see a pattern applied, never copy from it into a service.
