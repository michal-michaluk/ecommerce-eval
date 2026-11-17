# ecommerce-eval

Monorepo for the e-commerce eval: one bounded context per microservice, plus the
business materials the services are built from.

## Layout

```
docs/raw/    business materials — event storming diagram, transcript, platform overview, per-slice features, document samples
docs/api/    external warehouse API — InvenTree OpenAPI spec + the endpoints in scope
docs/arch/   blueprint architecture docs — read before coding a service
services/    one directory per microservice (bounded context)
```

| Service | Bounded context | Stack |
| --- | --- | --- |
| [`services/catalog`](services/catalog/AGENTS.md) | product catalog — draft, review, publication, pricing | Spring Boot 4 + Java 25 + PostgreSQL + Kafka + Keycloak |

## Materials

| File | Content |
| --- | --- |
| `docs/raw/ecommerce-eventstorming.jpg` | event storming diagram (primary source) |
| `docs/raw/ecommerce-eventstorming.improved.txt` | walkthrough transcript (primary source) |
| `docs/raw/ecommerce-eventstorming.improved.srt` | walkthrough subtitles (primary source) |
| `docs/raw/ecommerce-eventstorming.md` | event storming summary of the platform walkthrough |
| `docs/raw/platform-overview.md` | domain spec — slices: catalog, shopping, fulfillment |
| `docs/raw/product-catalog.md` | catalog feature: creation, review, publication, pricing |
| `docs/raw/shopping.md` | shopping feature: browsing, basket, checkout, order creation |
| `docs/raw/fulfillment.md` | fulfillment feature: pick, pack, ship, track, issue handling |
| `docs/raw/product-description.json` | sample published product listing with competing offers |
| `docs/raw/consignment-note.json` | sample carrier consignment note |
| `docs/raw/goods-issue-note.json` | sample goods issue note |
| `docs/api/inventree-openapi.json` | InvenTree API 579 OpenAPI 3.0.3 (293 paths, 427 schemas) |
| `docs/api/warehouse-api.md` | warehouse endpoints in scope and the port they translate into |

## Adding a service

1. `scaffold generate microservice-java-spring services/<name> micro_name=<package> db_name=<name>`
2. Read its `AGENTS.md` — build, quality gates and patterns live there — plus `docs/arch/microservice-java-spring/`.
3. Add its row to the service table above.

## Running a service

Each service is self-contained: its own Gradle wrapper, `k8s/` manifests and quality gates.

```bash
cd services/<name>
./gradlew build        # compile + tests + quality gates
./gradlew bootRun      # needs PostgreSQL, Kafka and a JWT issuer
```
