## 2 Authentication and scopes

Every request but three paths needs a bearer JWT. The service validates the token as an OAuth 2.0 resource server, issues
none, reads no cookie and keeps no session, so a request carries its whole authority in its own `Authorization` header.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:53, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:58, services/fulfillment/src/main/resources/application.yml:21 -->

### 2.1 The token

A JWT is accepted when its signature verifies against the realm's keys and its `iss` equals
`spring.security.oauth2.resourceserver.jwt.issuer-uri`; the value in configuration is `https://keycloak.auth/realms/iot`
and a deployment overrides it through `JWT_ISSUER_URI`. There is no second authentication mechanism: no basic
authentication, no API key, no client certificate.
<!-- evidence: services/fulfillment/src/main/resources/application.yml:21, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:58 -->

CSRF protection is disabled and the session policy is stateless because every caller presents a token and no cookie is
issued.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:52, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:53 -->

### 2.2 How a scope becomes an authority

The resource server reads the token's `scope` claim and Spring Security spells each value as an authority with the `SCOPE_`
prefix, so the scope `fulfillment-employee` arrives as `SCOPE_fulfillment-employee`. The chain compares exactly four such
strings and nothing else.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:31, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:32, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:35 -->

The four values are provisioned in the realm as Keycloak client scopes, one client carrying each as its default scope, which
puts the value into the token's own `scope` claim with no protocol mapper in between. A token whose `scope` claim does not
carry one of the four values therefore has no authority on any route of this service.
<!-- evidence: services/fulfillment/docs/security-decisions.md:51, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:54 -->

### 2.3 Which scope reaches which endpoint

| Scope | Reaches | Matcher |
| --- | --- | --- |
| `shopping-service` | `POST /orders` | `AppConfiguration.java:75` |
| `fulfillment-employee` | `GET /orders`, `POST …/assignment`, `POST …/positions/{productNumber}`, `POST …/packing`, `POST …/readiness`, `POST …/issues`, `GET …/pick-list`, `GET …/pick-workstation`, `GET …/documents/label`, `GET /processing/{processingId}`, `POST …/hold/resume` | `AppConfiguration.java:76` to `AppConfiguration.java:119` |
| `fulfillment-resolver` | `GET /issues`, `GET /processing/{processingId}`, `POST …/hold/resume` | `AppConfiguration.java:109`, `AppConfiguration.java:95`, `AppConfiguration.java:119` |
| `fulfillment-customer` | `GET /processing/{processingId}/state` | `AppConfiguration.java:117` |
| any authenticated caller | `/actuator/**` other than health and info | `AppConfiguration.java:74` |
| nobody | any route no rule names | `AppConfiguration.java:54` |

First match wins, and §4 lists the same decisions route by route. The employee scope does not reach the issue dashboard: an
employee token on `GET /issues` answers `403`, which the contract asserts.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:54, services/fulfillment/e2e/fulfillment-api.hurl:345, services/fulfillment/docs/security-decisions.md:31 -->

The resolver reaches the processing snapshot read but none of its commands except the resume, and the customer scope reaches
neither: the state view is a separate resource precisely so that `assignee` and `issue` cannot arrive through it.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:119, services/fulfillment/src/main/java/shop/fulfillment/customer/CustomerStateController.java:13, docs/specs/fulfillment/11-fulfillment-http-api.md:122 -->

### 2.4 What `401` and `403` mean

`401` means no token or a token that does not validate — a bad signature, a foreign issuer, an expired one. The
authentication entry point answers the status alone, sets `WWW-Authenticate: Bearer`, and writes no body; the contract
asserts it on two routes without a header.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:122, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:125, services/fulfillment/e2e/fulfillment-api.hurl:19, services/fulfillment/e2e/fulfillment-api.hurl:22 -->

`403` means the token validated and its scopes do not cover the route. The access denied handler answers the status alone
with an empty body: a caller who may not ask learns nothing about the resource.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:130, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:132, services/fulfillment/docs/security-decisions.md:45 -->

The chain decides before any handler runs, so a `403` does not depend on the resource existing: a customer token asking for
the snapshot of a real processing answers `403`, not a filtered payload and not `404`. The contract asserts `403` for an
employee token delivering an order and for an employee token reading the dashboard.
<!-- evidence: services/fulfillment/src/test/java/shop/fulfillment/processing/ProcessingSnapshotControllerTest.java:99, services/fulfillment/e2e/fulfillment-api.hurl:153, services/fulfillment/e2e/fulfillment-api.hurl:345 -->

The chain ends in `denyAll()`, so a route no rule names answers `401` without a token and `403` with any token. The goods
issue note is the one route of element 11 in that position.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:54, services/fulfillment/docs/security-decisions.md:40 -->

### 2.5 The customer route does not check ownership

`GET /processing/{processingId}/state` is granted by scope alone. The handler reads the projection by the id in the path and
compares no identity, because the order carries no customer identity to compare against; any token with the
`fulfillment-customer` scope reads any processing's state view. The decision is recorded as an accepted risk, and the
consequence is GAP-2.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/customer/CustomerStateController.java:17, docs/specs/fulfillment/spec.md:87, docs/decisions/operator-input.md:12 -->

### 2.6 The actuator floor

`/actuator/health/**`, `/actuator/info` and `/error` are public; every other actuator endpoint requires authentication and no
scope, and the exposed set is health, info, env and prometheus. A customer token therefore reaches `env` and `prometheus`
as well as an employee token does — recorded as an accepted risk, not as a grant.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:71, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:74, services/fulfillment/src/main/resources/application.yml:55, services/fulfillment/docs/security-decisions.md:34, docs/decisions/operator-input.md:15 -->
