# 11 — FulfillmentHttpApi

Element 11 · type: API we expose · actors: warehouse employee, issue resolver, customer, shopping (handover) · accepted.

Executable contract: [`prototypes/fulfillment-api.hurl`](prototypes/fulfillment-api.hurl) — 23 requests, 14 route shapes,
`hurlfmt --check` clean, runnable with `hurl --test` and six variables — `BASE_URL`, `shoppingToken`, `employeeToken`, `otherEmployeeToken`, `resolverToken`, `customerToken`; the exact command is in the file header. This file carries what a hurl cannot: the auth
matrix, status ownership and the decisions behind the shape.

## Shape — one processing resource, three sub-resources, two documents

| Resource | Path | Endpoints |
| --- | --- | --- |
| the order as received | `/orders` | `POST` (handover, element 12) · `GET` (list) |
| **the processing** | `/processing/{processingId}` | `GET` snapshot (below) · `POST /assignment` · `POST /packing` · `POST /readiness` · `POST /issues` · `POST /hold/resume` |
| pick list | `/processing/{processingId}/pick-list` | `GET` |
| one position | `/processing/{processingId}/positions/{productNumber}` | `POST` (scan → position `picked`) |
| issues | `/issues` | `GET` (dashboard, read-only) |
| customer state | `/processing/{processingId}/state` | `GET` |
| documents | `/processing/{processingId}/documents/{goods-issue-note,label}` | `GET` (rendered PDF) |

`POST` transitions or creates · `GET` reads. No verb segments, no `/api` prefix — same rule catalog follows
(`docs/specs/catalog/12-catalog-http-api.md:8-14`, arch `http-adapter.md:3-12`).

Both collections — `GET /orders`, `GET /issues` — answer one envelope, `{ "items": [ … ] }`, and take no parameter at all
(D-11-5). A row of `GET /orders` carries at least `processingId`, `state` and `assignee` — `assignee: null` while the order is
unclaimed, the same null rule as the snapshot — and element 15 owns the rest; a row of `GET /issues` carries at least
`issueId` and `kind`, and element 17 owns the rest.

## Snapshot — `GET /processing/{processingId}` and every command response

One payload, one shape: the state read and the write responses of the processing resource (`assignment`, `positions/{n}`,
`packing`, `readiness`, `hold/resume`) return this object, so any of them re-renders a screen. Two commands add to it: `issues`
carries `issueId` — the record it just wrote — and `orderState`, the same value as `state` under the name the contract fixed
(`$.orderState` in the hurl); `hold/resume` carries `closedIssueIds[]`, the ids that command closed (element 09 H5,
`spec.md` D8).

| Field | Type | Null policy |
| --- | --- | --- |
| `processingId` | string | never null |
| `state` | `pending` \| `assigned` \| `picking` \| `packed` \| `onHold` \| `readyForDelivery` \| `delivered` | never null (element 02) |
| `assignee` | string | **explicit `null` while unclaimed** — never an empty string, never an absent key |
| `positions[]` | array, at least one entry (element 02 RULE-3) | never null |
| `positions[].productNumber`, `positions[].quantity` | string, integer | never null |
| `positions[].state` | `awaitingScan` \| `picked` \| `packed` | never null — the enum and its pre-scan value are element 02's |
| `parcel` | object | **explicit `null` until the first scan registers it** (`spec.md` D11) |
| `parcel.reference`, `parcel.waybill`, `parcel.legState` | string | never null once `parcel` is present. Element 03 keeps the waybill under `carrier`; the hurl asserts `$.parcel.waybill`, so the wire flattens it onto the parcel |
| `issue` | object | **explicit `null` while no issue is open** — never reported, or every issue closed by a resume; the open set is not claimed here (`spec.md` F6) |
| `issue.issueId`, `issue.kind`, `issue.productNumber`, `issue.reportedBy`, `issue.reportedAt`, `issue.closedAt` | string × 4, timestamp × 2 | `closedAt` is `null` while the issue is open (element 04 RULE-1) |
| `openIssues` | integer | never null — the count of issues with `closedAt == null`, the same number `409 ISSUES_REMAIN` reports (element 09 H6) |

Three fields are optional and each has exactly one spelling: `assignee: null` while unclaimed, `parcel: null` before
registration, `issue: null` while no issue is open — never reported, or all closed by a resume. An absent key means "not part
of this payload" — it never means "empty".

## Authentication and authorization

OAuth 2.0 / OIDC bearer JWT as a resource server (`docs/arch/microservice-java-spring/security.md`); only the actuator
paths are unauthenticated.

| Scope | May |
| --- | --- |
| `fulfillment-employee` | list, the processing snapshot, claim, pick list, scan, pack, readiness, report issue, resume a hold, both documents |
| `fulfillment-resolver` | issues list, the processing snapshot (`GET …`, read-only — element 17's "open an order's processing state"), resume a hold |
| `fulfillment-customer` | `GET .../state` only, and only their own processing |
| `shopping-service` | `POST /orders` only |

`GET /issues` returns **403** for an employee token — verified as a request in the hurl.

`GET /processing/{processingId}` is granted to `fulfillment-employee` **and** `fulfillment-resolver`: element 17's read-only
"open an order's processing state — shows element 02 data" is exactly this read, and a resolver deciding a resume has to see
what is held. The hurl exercises it with an employee token only. The customer scope never reaches it — `assignee` and `issue`
must not, which is why its state view is a separate resource (D-11-4, asserted `not exists` in the hurl).

## Status ownership

| Code | When |
| --- | --- |
| `201` | assignment granted, issue reported, processing created — a **granted** claim only; the owner re-claiming his own order is a `NO-OP` and answers `200` (element 07 DT-07-4) |
| `200` | idempotent re-apply (same scan, same pack, same order handover, **the owner's re-claim** — element 07 DT-07-4), readiness granted, hold resumed |
| `401` | no or invalid token |
| `403` | scope does not cover the endpoint |
| `404` | unknown `processingId` — `PROCESSING_NOT_FOUND`, asserted in the hurl — or an unknown position of a known processing: `POSITION_NOT_FOUND`, **derived** (the hurl asserts only the first) and shaped like it, because a position is the only other resource keyed by a path segment |
| `409` | a refusal — one `error` code per refusal, table below |
| `422` | `ORDER_INCOMPLETE` with `missing[]` — one string per failed order part, named as a payload path (`lines`, `delivery`, `payment`, `invoicing`, or a nested one such as `delivery.location.pointId`); **the format is a default**, the hurl asserts only that the key exists. Handover rejected, no processing record created (element 01 RULE-3, RULE-7) |

### Every refusal has a code

A `409` answers `{ "error": "<CODE>", … }`. Naming rule: the code is the refusal's own name in `SCREAMING_SNAKE_CASE`, and
where the hurl fixes a spelling that spelling wins — `NOT_READY` is the readiness decision's verdict, not one of its
`reasons` values. `ALREADY_ASSIGNED`, `NOT_READY` and `ISSUES_REMAIN` are asserted by the hurl; every other row is derived
from the refusal named beside it and is labelled as such, so nothing derived is read as a contract fact.

| Code | Endpoint | Refusal, as named in | Origin |
| --- | --- | --- | --- |
| `ALREADY_ASSIGNED` | `POST …/assignment` | element 07 DT-07-3, reason `alreadyAssigned` | hurl |
| `ACTOR_NOT_ALLOWED` | `POST …/assignment` | element 07 DT-07-2, reason `actor` | derived — unreachable behind the employee scope, which answers `403` first |
| `PAST_PICKING` | `POST …/assignment` | element 07 DT-07-5 | derived |
| `CLOSED` | `POST …/assignment` | element 07 DT-07-6 | derived |
| `NOT_READY` | `POST …/readiness` | element 08, any REFUSE row; carries `reasons[]` | hurl |
| `NOT_STARTED` | `POST …/issues` | element 09 H3, reason `notStarted` | derived |
| `PAST_HANDOVER` | `POST …/issues` | element 09 H4, reason `pastHandover` | derived |
| `ISSUES_REMAIN` | `POST …/hold/resume` | element 09 H6, reason `issuesRemain`; carries `openIssues` | hurl |
| `ON_HOLD` | `POST …/positions/{productNumber}`, `POST …/packing` | element 09, *what a hold blocks*: pick and pack are refused while the order is held | derived — no decision row names it |
| `POSITION_NOT_PICKED` | `POST …/packing` | element 02: packing needs a `picked` position and states nothing else as packable | derived — element 02 states the edge, the refusal is its absence |

A refusal with no code would send an implementer guessing, so the two hold/pack refusals get one even though the hurl does
not exercise the endpoints in that state.

`NOT_READY.reasons[]` is an array of **strings** — element 08's reason values (`notPacked`, `positionsOpen`, `onHold`,
`noParcel`, `pastHandover`), never codes and never objects. Element 08 evaluates first-match-wins, so exactly one entry exists
today; the array is the shape, not a claim that several reasons co-exist.

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
- **D-11-5 — v1 is unpaginated, and both collections answer `{ "items": [ … ] }`.** The hurl is the contract and calls
  `GET /orders` and `GET /issues` with no parameter, reading `$.items[0]`; no volume or latency figure exists anywhere
  (`spec.md` Step 2 *Volume/latency*), so a page/size pair and its default would be invented, and an implementer who adds one
  changes the contract silently. Paging later is additive — a key beside `items` plus an optional parameter — so a client
  written against `items[]` survives. Element 15 likewise offers no page control.

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
- **RULE-3 Unknown resource answers `404` with `{error}`; a refused precondition answers `409` with an `error` code.** A
  `NOT_READY` refusal adds `reasons[]`; no refusal is a silent success and none is a bare status.
- **RULE-4 The customer scope never sees `assignee` or `issue`** — asserted as `not exists` in the hurl.

## Domain events

The API emits nothing directly; each accepted command produces the concept's event (element 02).

## Q / R carried into this element

- **Q-11-1 closed — D-11-5:** page/size parameters and their defaults for `GET /orders` and `GET /issues`. v1 has neither: the
  hurl calls both collections without a parameter and no volume figure exists anywhere (`spec.md` Step 2 *Volume/latency*), so
  the default would have been invention.
- **Q-11-2 open:** assignment release — the hurl has no release endpoint, and the auth matrix grants none; whether a resolver
  may release, and what happens to a picked position afterwards, is `Q` in element 07/09.
- **R-11-1 open:** `POST /orders` is the only synchronous cross-context call in a design whose arch docs allow no broker
  (`integration-events.md:18`, `spec.md` R3). If `spec.md` Q5 resolves toward events, the endpoint stays as the fallback, not the primary.
