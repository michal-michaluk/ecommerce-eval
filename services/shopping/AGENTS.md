# Shopping service — basket and checkout slices

**Stack:** Spring Boot 4 + Java 25 + PostgreSQL + OAuth2/Keycloak
**Architecture:** Vertical slices — bounded contexts as sub-packages
**No messaging.** The service consumes no event channel in this increment (spec D39): `spring-kafka`,
`spring.kafka.*`, the integration-test broker and the Kubernetes broker address are deliberately
absent. Add them back only together with the transport that reads them.
**Deploy:** `k8s/overlays/k3d` — the shared local k3s cluster, one namespace per service. The
cluster and its kubectl context belong to the workspace, not to this service; the
`.agents/skills/run-apps` skill creates it, gets the image in and overrides the addresses to the
shop-wide Postgres, Kafka and Keycloak.

Generated from the `microservice-java-spring` blueprint.
