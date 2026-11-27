## 3 Context and dependencies

The service sits between a customer's client and everything that owns product data. It owns the basket and
nothing else: offers, stock, identity and the database are other people's, and the service reaches them
through one port, one issuer and one datasource.

![The shopping service and everything it talks to.](generated/shopping/diagrams/context.svg){#fig-context}

<!-- evidence: docs/specs/basket/index.md:60-74 -->

### 3.1 The systems around it

| Counterpart | What crosses | State of the wiring |
|---|---|---|
| Customer's client | six HTTP routes, authorised by a bearer token | implemented |
| Identity provider (Keycloak, realm `shopping`) | the token the service validates; the realm also provisions a second, non-customer identity | implemented, and part of the service's own provisioning |
| PostgreSQL, database `shopping` | one JSONB document per basket, one JSONB summary row per basket | implemented; the same instance serves every service of the shop, one database each |
| Offer source | description, current price, discount and stock of one offer | **a seeded fixture in this increment** — the provider's read has no endpoint yet |
| Checkout slice, in the same process | the selected rows, their totals, the handover's identity | implemented as an in-process command |
| OpenTelemetry collector | traces and logs over OTLP | implemented in the `k3d` overlay only |

<!-- evidence: services/shopping/src/main/resources/application.yml:1-19, services/shopping/src/test/resources/shopping-realm.json:2-3, services/shopping/k8s/overlays/k3d/kustomization.yaml:34-40, services/shopping/k8s/base/deployment.yaml:104-105, services/shopping/src/main/java/shop/shopping/kernel/CheckoutHandovers.java:8-20 -->

### 3.2 The identity the service is given, and the one it refuses

The realm `shopping` carries two identities, and the second one exists for a reason a reader will not
guess: a realm-`shopping` token always carries the realm's default scopes, so a scenario that must show
"a valid token without the customer scope is refused" could not be constructed without an identity that
is valid in this realm and is not a customer.

| Identity | Client | Scope | What it can do here |
|---|---|---|---|
| customer | `customer-client` | `customer` | every basket route |
| service | `shopping-service-client` | `shopping-service` only | reach the management surface; `403` on every basket route |

<!-- evidence: services/shopping/src/test/resources/shopping-realm.json:31, services/shopping/src/test/resources/shopping-realm.json:40-41, services/shopping/src/test/resources/shopping-realm.json:51, services/shopping/src/test/resources/shopping-realm.json:70-71, docs/specs/basket/log.md:118-119 -->

The issuer address is configuration, and its default is deliberately unresolvable so that a deployment
which forgets to state it fails to start rather than validating tokens against a stranger: the packaged
default names a domain reserved by RFC 2606, the base ConfigMap names a `keycloak` Service in this
namespace that only a self-contained deployment would run, and the `k3d` overlay names the one Keycloak
the shop cluster runs.

<!-- evidence: services/shopping/src/main/resources/application.yml:18-19, services/shopping/k8s/base/configmap.yaml:37, services/shopping/k8s/overlays/k3d/kustomization.yaml:27-32 -->

### 3.3 The database

One logical database per service on one instance: the URL, the user and the password arrive from a Secret
the overlays generate, and Liquibase creates the schema at start. The pool is capped at fifteen
connections, which is the only throughput number this deployment states.

<!-- evidence: services/shopping/src/main/resources/application.yml:3-13, services/shopping/k8s/overlays/k3d/kustomization.yaml:34-40 -->

### 3.4 There is no broker

The service consumes no event channel. `spring-kafka` is not on the classpath, no broker address is
configured, and no manifest of this service names one — a key that nothing reads is a key a reader would
mistake for a working integration. The observation path that a broker would feed exists and is exercised
in process; only the transport is deferred.

<!-- evidence: services/shopping/build.gradle:24-34, services/shopping/k8s/base/configmap.yaml:9-10, services/shopping/AGENTS.md:6-8 -->
