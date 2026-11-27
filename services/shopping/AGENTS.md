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

## Gates

| Gate | Command | What it proves |
| --- | --- | --- |
| contract | `scripts/prove.sh` (add `--dry-run` to check only the environment) | the 24 steps of `docs/specs/basket/prototypes/basket-api.prove.md` against the GREEN local k3s cluster (`127.0.0.1:8082`); the runner mints both realm-`shopping` tokens through `.agents/skills/run-apps`, derives `est_from`/`est_to` as `today+2`/`today+4` in `Europe/Warsaw`, asserts on every run that `--var` still outranks the document's `**vars:**`, and probes `GET /baskets` with both tokens (customer → 200, service → 403) so a stale image or a misconfigured client is named instead of surfacing as step 1 or step 24 — no secret and no date is typed by hand. `--dry-run` is **not** side-effect free: its customer probe is step 1's own request, which auto-creates the customer's basket (D29) |
