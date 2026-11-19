# 11 — FulfillmentHttpApi

Element 11 · type: API we expose · actors: warehouse employee, issue resolver, customer, shopping (handover) · accepted.

Executable contract: [`prototypes/fulfillment-api.hurl`](prototypes/fulfillment-api.hurl) — 23 requests, 14 route shapes,
`hurlfmt --check` clean, runnable with `hurl --test` and six variables — `BASE_URL`, `shoppingToken`, `employeeToken`, `otherEmployeeToken`, `resolverToken`, `customerToken`; the exact command is in the file header. This file carries what a hurl cannot: the auth
matrix, status ownership and the decisions behind the shape.

## Shape — one processing resource, three sub-resources, two documents

| Resource | Path | Endpoints |
| --- | --- | --- |
| the order as received | `/orders` | `POST` (handover, element 12) · `GET` (list) |
| **the processing** | `/processing/{processingId}` | `GET` state · `POST /assignment` · `POST /packing` · `POST /readiness` · `POST /issues` · `POST /hold/resume` |
| pick list | `/processing/{processingId}/pick-list` | `GET` |
| one position | `/processing/{processingId}/positions/{productNumber}` | `POST` (scan → position `picked`) |
| issues | `/issues` | `GET` (dashboard, read-only) |
| customer state | `/processing/{processingId}/state` | `GET` |
| documents | `/processing/{processingId}/documents/{goods-issue-note,label}` | `GET` (rendered PDF) |

`POST` transitions or creates · `GET` reads. No verb segments, no `/api` prefix — same rule catalog follows
(`docs/specs/catalog/12-catalog-http-api.md:8-14`, arch `http-adapter.md:3-12`).

## Authentication and authorization

OAuth 2.0 / OIDC bearer JWT as a resource server (`docs/arch/microservice-java-spring/security.md`); only the actuator
paths are unauthenticated.

| Scope | May |
| --- | --- |
| `fulfillment-employee` | list, claim, pick list, scan, pack, readiness, report issue, resume a hold, both documents |
| `fulfillment-resolver` | issues list, resume a hold |
| `fulfillment-customer` | `GET .../state` only, and only their own processing |
| `shopping-service` | `POST /orders` only |

`GET /issues` returns **403** for an employee token — verified as a request in the hurl.

## Status ownership

| Code | When |
| --- | --- |
| `201` | assignment granted, issue reported, processing created |
| `200` | idempotent re-apply (same scan, same pack, same order handover), readiness granted, hold resumed |
| `401` | no or invalid token |
| `403` | scope does not cover the endpoint |
| `404` | unknown `processingId` or position |
| `409` | `ALREADY_ASSIGNED` (element 07), `NOT_READY` with `reasons[]` (element 08), `ISSUES_REMAIN` on resume (element 09 H6), stale state transition |
| `422` | `ORDER_INCOMPLETE` with `missing[]` — handover rejected, no processing record created (element 01 RULE-3) |

## Decisions behind the shape

- **D-11-1 — state transitions are commands, never `PATCH`es.** A client cannot set `state`; it calls `readiness`,
  `assignment`, `positions/{n}` or `issues`. Element 02 RULE-2 needs exactly this: the guard and the decision own the
  transition, not the caller.
- **D-11-2 — idempotency is explicit in the response.** A repeated scan or a repeated handover answers `200` with the current
  state instead of `409`. Both sources are polled (`spec.md` R3), so retries are normal traffic, not errors.
- **D-11-3 — documents are rendered, not assembled.** Both document endpoints answer `application/pdf`; no amount, price or
  currency appears in any JSON payload (spec.md D5). WZ amounts arrive already computed from the invoicing boundary (element 05).
- **D-11-4 — the customer endpoint is a separate resource**, not a filtered projection of `GET /processing/{id}`: the
  assignment and the issue detail must not be reachable by that scope (element 18).

The resume command is `POST /processing/{processingId}/hold/resume` — introduced by `spec.md` D7 because the sources
name no exit from `onHold`, and the **only** write that touches an issue (`spec.md` D8). Its body names what the caller
vouches for — `{ "closedIssueIds": ["ISS-2026-0031"] }` — and the order leaves `onHold` only when no issue remains open;
otherwise `409 ISSUES_REMAIN` with `openIssues`. There is no resolve endpoint, no resolve command and no issue lifecycle
(`spec.md` D8, element 04 RULE-1, element 17).

## Domain rules

- **RULE-1 No money in any JSON payload.** Verified by inspection of the hurl: no `amount`, `price`, `currency`, `total`.
- **RULE-2 Every write endpoint names the concept that owns the decision** (07, 08, 09, 02) — this API contains no
  business rule of its own. The pick/pack split is the same rule: one scan closes one `pick` (`positions/{productNumber}`),
  `packing` closes the pack step, and neither endpoint advances an order past what its element allows (element 02,
  element 16 Q-16-1).
- **RULE-3 Unknown resource answers `404` with `{error}`; failed precondition answers `409` with the reasons array.** No
  silent success on a refusal.
- **RULE-4 The customer scope never sees `assignee` or `issue`** — asserted as `not exists` in the hurl.

## Domain events

The API emits nothing directly; each accepted command produces the concept's event (element 02).

## Q / R carried into this element

- **Q-11-1 open:** page/size parameters and their defaults for `GET /orders` and `GET /issues` — no volume figure exists
  anywhere (`spec.md` properties), so pagination shape is a default, not a fact.
- **Q-11-2 open:** assignment release — the hurl has no release endpoint, and the auth matrix grants none; whether a resolver
  may release, and what happens to a picked position afterwards, is `Q` in element 07/09.
- **R-11-1 open:** `POST /orders` is the only synchronous cross-context call in a design whose arch docs allow no broker
  (`integration-events.md:18`, `spec.md` R3). If `spec.md` Q5 resolves toward events, the endpoint stays as the fallback, not the primary.
