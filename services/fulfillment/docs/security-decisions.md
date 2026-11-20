# Security decisions — fulfillment service

One recorded authN/authZ decision per endpoint of element 11 (`docs/specs/fulfillment/11-fulfillment-http-api.md`), as
`docs/arch/microservice-java-spring/security.md` requires: no endpoint is reachable by omission, and every route the service
exposes appears in this table before it appears in code.

The matrix itself is `AppConfiguration.filterChain()`: OAuth 2.0 / OIDC bearer JWT as a resource server, CSRF off and the
session stateless because every caller presents a token and no cookie is issued. A route no row names is denied — the chain
ends in `anyRequest().denyAll()`.

The four scopes are provisioned in `src/test/resources/iot-realm.json` (test and e2e realm) and mirrored in
`k8s/infra/keycloak.yaml` (the deployment's realm import): `fulfillment-employee`, `fulfillment-resolver`,
`fulfillment-customer` and `shopping-service`. Each is a Keycloak client scope, so the value the resource server reads is the
token's own `scope` claim and no protocol mapper can produce a second one.

| # | Endpoint | Authentication | Authorization | Decision |
| --- | --- | --- | --- | --- |
| 1 | `POST /orders` | bearer JWT required | `shopping-service` only | the handover of element 12 is checkout's call and nobody else's; an employee token answers `403` |
| 2 | `GET /orders` | bearer JWT required | `fulfillment-employee` only | the orders list of element 15 is the warehouse's screen; `fulfillment-customer` may not read the queue |
| 3 | `POST /processing/{processingId}/assignment` | bearer JWT required | `fulfillment-employee` only | claiming is the warehouse employee's own action (element 07 RULE-5), and the assignee is the token's subject, never a body value |
| 4 | `GET /processing/{processingId}` | bearer JWT required | `fulfillment-employee` **or** `fulfillment-resolver` | element 11 grants the snapshot to both: the resolver's read-only "open an order's processing state" is this read (element 17), and a resolver deciding a resume has to see what is held. The customer scope has its own resource (element 18), so `assignee` and `issue` never reach it |
| 5 | `GET /processing/{processingId}/pick-list` | bearer JWT required | `fulfillment-employee` only | the walk of element 10 is the assigned employee's working document; a resolver has no use for it, and warehouse locations never reach the customer |
| 6 | `GET /processing/{processingId}/pick-workstation` | bearer JWT required | `fulfillment-employee` only | the one screen an assigned employee works from (element 16). The route is this service's own — element 11 names no path for that screen — and it is read-only, so nothing here can act for another view's actor |
| 7 | `POST /processing/{processingId}/positions/{productNumber}` | bearer JWT required | `fulfillment-employee` only | one scan closes one position (element 02, element 16). The scope is the endpoint's; the caller must additionally be the assignee, and the aggregate refuses anybody else with `409 ACTOR_NOT_ALLOWED` (element 16, "never let a non-assignee confirm") |
| 8 | `POST /processing/{processingId}/packing` | bearer JWT required | `fulfillment-employee` only | the bulk packing step (element 02, element 16), with the same assignee rule as the scan |
| 9 | `POST /processing/{processingId}/readiness` | bearer JWT required | `fulfillment-employee` only | declaring the order done is the warehouse employee's own action (element 08); the guard reads the order's state, not the caller, and no source ties `mark done` to the assignee (Q-08-4) |
| 10 | `GET /processing/{processingId}/documents/label` | bearer JWT required | `fulfillment-employee` only | the consignment note of element 06 is printed in the warehouse by the employee who packs. The bytes are the carrier's own document, so nothing here reveals an internal state a wider scope would have to be trusted with anyway |
| 11 | `GET /processing/{processingId}/state` | bearer JWT required | `fulfillment-customer` only | element 18's resource is the customer's own view, and D-11-4 keeps it separate so `assignee` and `issue` cannot be reached by this scope. No ownership check exists — the order carries no customer identity to compare against (register D13), so the scope alone decides |
| 12 | `POST /processing/{processingId}/issues` | bearer JWT required | `fulfillment-employee` only | reporting a warehouse reality gap is the picking employee's own call (element 04 RULE-2, `mark problems with fulfillment`), and the caller must additionally be the assignee — the aggregate refuses anybody else with `409 ACTOR_NOT_ALLOWED` (phase-2 P2-19b). A resolver reads the gaps, he does not report them |
| 13 | `POST /processing/{processingId}/hold/resume` | bearer JWT required | `fulfillment-employee` **or** `fulfillment-resolver` | the one write that closes an issue (registers D7/D8). Element 09 RULE-8 grants it to both: the resolver works the dashboard and must be able to lift the hold he closed, and no other scope is granted. No assignee check applies here — an assignee check would make the resolver unable to lift a hold he is the only one to see (element 09 RULE-8) |
| 14 | `GET /issues` | bearer JWT required | `fulfillment-resolver` only | the dashboard of element 17 is the resolver's screen: an employee token answers `403`, asserted in the hurl. Read-only, so no scope here can act for another view's actor; the board carries no resolver-side command at all |
| 15 | `GET /actuator/health/**` | none | public | Kubernetes probes |
| 16 | `GET /actuator/info` | none | public | build information |
| 17 | `GET /actuator/**` (any other actuator endpoint) | bearer JWT required | any authenticated caller | metrics and environment are not public, and no scope is invented for them |
| 18 | `/error` | none | public | Spring's error dispatch, not a business route: without it a container-level failure is answered by the security chain instead of by the status it carries. No handler of this service is reachable through it |

## Not yet exposed

`GET …/documents/goods-issue-note` does not exist: element 05 is out of the implementation plan entirely (register D15), so
`denyAll()` answers it and no row of this table grants it. Every other route of element 11 is granted above.

## Refusals on the wire

- `401` — no token or an invalid one. The body is empty and the `WWW-Authenticate: Bearer` header names the scheme.
- `403` — the token's scopes do not cover the route. The body is empty: a caller who may not ask learns nothing.
- `409` / `404` / `422` — the domain's own refusals, translated by `FulfillmentErrorAdvice` from the error type and the values
  the exception carries. No message and no stack trace reaches a client (`server.error.include-message: never`).

## The realm, and what is provisioned where

Each of the four scopes is a Keycloak **client scope** plus one client that carries it as a default scope, because that is the
one mechanism that puts a value into the token's `scope` claim — the claim this service reads as `SCOPE_*`. A test user signs
in through the client of its scope; `AuthFixture` and `FulfillmentRealmFixture` do exactly that.

`src/test/resources/iot-realm.json` is the test and e2e realm. `k8s/infra/keycloak.yaml` carries the same document for the
**dev cluster** (a `start-dev` Keycloak with a bootstrapped `admin`/`admin`): its users exist so the dev cluster can be driven
by hand, and its passwords and the clients' `secret` are development values, never a production credential. A production
deployment imports its own realm and takes every secret from a Kubernetes secret; no warehouse or carrier credential appears
in this repository.
