# ecommerce-eval

Monorepo for the e-commerce eval: one bounded context per microservice, plus the
business materials the services are built from.

## Layout

```
raw/        business materials — event storming summary, platform overview, per-slice features, document samples
api/        external warehouse API — InvenTree OpenAPI spec + the endpoints in scope
services/   one directory per microservice (bounded context)
```

| Service | Bounded context | Stack |
| --- | --- | --- |
| [`services/catalog`](services/catalog/AGENTS.md) | product catalog — draft, review, publication, pricing | Spring Boot 4 + Java 25 + PostgreSQL + Kafka + Keycloak |

## Materials

| File | Content |
| --- | --- |
| `raw/ecommerce-eventstorming.md` | event storming summary of the platform walkthrough |
| `raw/platform-overview.md` | domain spec — slices: catalog, shopping, fulfillment |
| `raw/product-catalog.md` | catalog feature: creation, review, publication, pricing |
| `raw/shopping.md` | shopping feature: browsing, basket, checkout, order creation |
| `raw/fulfillment.md` | fulfillment feature: pick, pack, ship, track, issue handling |
| `raw/product-description.json` | sample published product listing with competing offers |
| `raw/consignment-note.json` | sample carrier consignment note |
| `raw/goods-issue-note.json` | sample goods issue note |
| `api/inventree-openapi.json` | InvenTree API 579 OpenAPI 3.0.3 (293 paths, 427 schemas) |
| `api/warehouse-api.md` | warehouse endpoints in scope and the port they translate into |

## Adding a service

1. Scaffold it as `services/<name>/` from the `microservice-java-spring` blueprint.
2. Read its `AGENTS.md` before writing code — build, quality gates and patterns live there.
3. Add a row to the service table above.

## Running a service

Each service is self-contained: its own Gradle wrapper, `k8s/` manifests and quality gates.

```bash
cd services/catalog
./gradlew build        # compile + tests + quality gates
./gradlew bootRun      # needs PostgreSQL, Kafka and a JWT issuer
```

Prerequisites per service are documented in its `docs/tech-update.md`.
