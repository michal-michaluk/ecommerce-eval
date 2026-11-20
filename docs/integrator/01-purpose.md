## 1 Purpose

Four callers reach this API, each with one OAuth2 scope, and the scope is the whole authorisation model: no role inside the
service grants anything, three framework paths are public, and every route no rule names is denied.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:54, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:71, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:73 -->

### 1.1 Who calls

| Caller | Scope | What it does |
| --- | --- | --- |
| checkout | `shopping-service` | hands one finished order over and receives the `processingId` the warehouse works it by |
| warehouse tooling | `fulfillment-employee` | claims an order, reads the pick list and the workstation screen, scans a position, packs, marks the order ready, reports a warehouse issue, prints the label, reads the orders list and the snapshot |
| issue resolver | `fulfillment-resolver` | reads the open-issue dashboard, opens the processing state of a held order, lifts a hold |
| customer surface | `fulfillment-customer` | reads one processing's customer state: the state, the parcel and its legs, never the assignee and never the issue |

The per-route matrix is §4; §2.3 states what the chain checks and what it does not.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:75, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:76, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:109, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:117, services/fulfillment/src/main/java/shop/fulfillment/customer/CustomerStateController.java:11 -->

The handover answers `201` with the processing it opened and `200` with `duplicate: true` when the same order is delivered
again; the two answers are the whole of §3.3's idempotency rule for the handover.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderIntakeController.java:31, services/fulfillment/src/main/java/shop/fulfillment/processing/OrderIntakeController.java:44 -->

`GET /processing/{processingId}` and `POST /processing/{processingId}/hold/resume` are the two routes two scopes share: the
resolver has to see what is held before he lifts the hold, and no other scope is granted either route.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:95, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:119, services/fulfillment/docs/security-decisions.md:30 -->

### 1.2 What this guide covers

Part I carries the three rules that hold across every operation — scope (§2), the error body and its codes (§3), the
per-route security matrix (§4). Part II, §5 to §18, carries one chapter per operation with method, path, parameters,
request and response bodies and the statuses it can answer; each chapter is rendered from the service's own OpenAPI
dataset and carries its own evidence markers, so this guide states the rules and the chapters state the payloads.
<!-- evidence: docs/integrator-guide.qmd:11, docs/integrator-guide.qmd:19, docs/integrator-guide.qmd:23, docs/integrator-guide.qmd:25, docs/integrator-guide.qmd:51 -->

§19 covers the actuator endpoints, §20 lists the gaps, §21 the revision history, and Appendix B holds the
request and response transcripts.
<!-- evidence: docs/integrator-guide.qmd:53, docs/integrator/20-gaps.md:1, docs/integrator-guide.qmd:59, docs/integrator-guide.qmd:63 -->

The proof of every status in this guide is one executable file: `services/fulfillment/e2e/fulfillment-api.hurl`, 48
requests over the 14 route shapes of the service, run green against a booted service with a Keycloak realm and a
PostgreSQL database. The wire specification behind those requests is element 11.
<!-- evidence: services/fulfillment/e2e/fulfillment-api.hurl:1, services/fulfillment/e2e/fulfillment-api.hurl:19, docs/specs/fulfillment/11-fulfillment-http-api.md:5 -->

### 1.3 What this guide does not cover

The goods issue note (WZ) is not part of the delivered system. No handler serves `GET …/documents/goods-issue-note`; the
chain's final `denyAll()` answers it — `401` without a token, `403` with any token — because no invoicing contract exists
to supply its amounts or its receiver party.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:54, services/fulfillment/docs/security-decisions.md:37, docs/specs/fulfillment/spec.md:89, docs/decisions/operator-input.md:10 -->

No money is on this wire. No JSON payload carries an amount, a price, a currency or a total; the two document routes answer
`application/pdf`, and the price of anything is the invoicing boundary's.
<!-- evidence: docs/specs/fulfillment/11-fulfillment-http-api.md:138, services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelLabelController.java:24 -->

The resolver reads the issue dashboard and lifts a hold; he cannot resolve, close, dismiss or acknowledge an issue, because
no source names such a command. Closing an issue is a fact of `POST …/hold/resume` alone.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/issues/IssueDashboardController.java:14, services/fulfillment/src/main/java/shop/fulfillment/processing/ProcessingService.java:120 -->

An assignment is never released. No route releases one and no scope is granted one; whether a resolver may release, and what
a picked position becomes afterwards, stays open in element 11.
<!-- evidence: docs/specs/fulfillment/11-fulfillment-http-api.md:156, services/fulfillment/docs/security-decisions.md:20 -->

The outbound calls of this service — the warehouse reservation and goods issue, the carrier registration and its polling —
are adapters behind ports and are not part of this guide; it documents the HTTP surface this service offers, not the HTTP
surface it consumes. Deployment, telemetry and schema migrations belong to the system documentation, and no on-call runbook
exists for this repository.
<!-- evidence: docs/decisions/operator-input.md:11, docs/decisions/operator-input.md:8 -->
