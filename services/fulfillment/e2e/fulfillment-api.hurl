# Fulfillment API — the executable contract of element 11, phase 1 (order processing core), phase 2 (pick list, scan,
# packing, the processing snapshot), phase 3 (parcel registration, readiness, the label, the customer state view) and
# phase 4 (issues, the hold and its resume, the resolver's dashboard).
# Run — six variables are required:
#   hurl --test --variable BASE_URL=http://localhost:8081 \
#     --variable shoppingToken=$SHOP --variable employeeToken=$EMP --variable otherEmployeeToken=$EMP2 \
#     --variable resolverToken=$RES --variable customerToken=$CUS \
#     e2e/fulfillment-api.hurl
# No money appears in any payload (spec D5).
# One section the contract has is missing on purpose: the goods issue note is out of the implementation plan entirely
# (register D15), so `GET …/documents/goods-issue-note` is not asked here.
#
# Two deliberate differences from docs/specs/fulfillment/prototypes/fulfillment-api.hurl remain, recorded in
# docs/phase-1-decisions.md: the processing id is captured instead of asserted as the literal "OP-2026-0001" (the id is
# minted from a sequence and carries the current year), and the second order the readiness refusal needs is handed over here
# rather than assumed to exist, because a fresh database holds no other order.

# ── Unauthenticated ───────────────────────────────────────────────────────
GET {{BASE_URL}}/orders
HTTP 401

GET {{BASE_URL}}/processing/OP-2026-0001
HTTP 401

# ── Handover from shopping (element 12) ───────────────────────────────────
POST {{BASE_URL}}/orders
Authorization: Bearer {{shoppingToken}}
Content-Type: application/json
{
  "orderHash": "sha256:1f3c9d0a",
  "placedAt": "2026-11-19T09:31:12Z",
  "lines": [{ "productNumber": "75423", "name": "LEGO X-Wing 75423", "quantity": 1 }],
  "delivery": { "methodCode": "PARCEL_LOCKER", "location": { "kind": "PICKUP_POINT", "pointId": "KRA01M" } },
  "payment": { "methodCode": "PAY_DEFERRED" },
  "invoicing": { "buyerName": "Jan Kowalski", "nip": null,
                 "address": { "street": "ul. Mineralna 5A", "city": "Kraków", "postalCode": "30-001", "country": "PL" } }
}
HTTP 201
[Captures]
processingId: jsonpath "$.processingId"
[Asserts]
jsonpath "$.processingId" isString
jsonpath "$.processingId" != ""
jsonpath "$.state" == "pending"

# same order again — idempotent, no second processing
POST {{BASE_URL}}/orders
Authorization: Bearer {{shoppingToken}}
Content-Type: application/json
{
  "orderHash": "sha256:1f3c9d0a",
  "placedAt": "2026-11-19T09:31:12Z",
  "lines": [{ "productNumber": "75423", "name": "LEGO X-Wing 75423", "quantity": 1 }],
  "delivery": { "methodCode": "PARCEL_LOCKER", "location": { "kind": "PICKUP_POINT", "pointId": "KRA01M" } },
  "payment": { "methodCode": "PAY_DEFERRED" },
  "invoicing": { "buyerName": "Jan Kowalski", "nip": null,
                 "address": { "street": "ul. Mineralna 5A", "city": "Kraków", "postalCode": "30-001", "country": "PL" } }
}
HTTP 200
[Asserts]
jsonpath "$.processingId" == "{{processingId}}"
jsonpath "$.duplicate" == true

# incomplete order — rejected, no processing record (element 01 RULE-3)
POST {{BASE_URL}}/orders
Authorization: Bearer {{shoppingToken}}
Content-Type: application/json
{ "orderHash": "sha256:bad", "placedAt": "2026-11-19T09:31:12Z", "lines": [] }
HTTP 422
[Asserts]
jsonpath "$.error" == "ORDER_INCOMPLETE"
jsonpath "$.missing" count > 0

# a pickup point without its point id — rejected, and the payload names the part (element 12 RULE-3)
POST {{BASE_URL}}/orders
Authorization: Bearer {{shoppingToken}}
Content-Type: application/json
{
  "orderHash": "sha256:no-point", "placedAt": "2026-11-19T09:31:12Z",
  "lines": [{ "productNumber": "75423", "name": "LEGO X-Wing 75423", "quantity": 1 }],
  "delivery": { "methodCode": "PARCEL_LOCKER", "location": { "kind": "PICKUP_POINT", "pointId": null } },
  "payment": { "methodCode": "PAY_DEFERRED" },
  "invoicing": { "buyerName": "Jan Kowalski", "nip": null,
                 "address": { "street": "ul. Mineralna 5A", "city": "Kraków", "postalCode": "30-001", "country": "PL" } }
}
HTTP 422
[Asserts]
jsonpath "$.error" == "ORDER_INCOMPLETE"
jsonpath "$.missing" includes "delivery.location.pointId"

# ── Orders list (element 15) ──────────────────────────────────────────────
GET {{BASE_URL}}/orders
Authorization: Bearer {{employeeToken}}
HTTP 200
[Asserts]
jsonpath "$.items" count > 0
jsonpath "$.items[0].processingId" == "{{processingId}}"
jsonpath "$.items[0].state" == "pending"
jsonpath "$.items[0].assignee" exists

# ── Assignment (elements 07, 15) ──────────────────────────────────────────
POST {{BASE_URL}}/processing/{{processingId}}/assignment
Authorization: Bearer {{employeeToken}}
HTTP 201
[Asserts]
jsonpath "$.state" == "assigned"
jsonpath "$.assignee" exists

# second employee — refused, never queued
POST {{BASE_URL}}/processing/{{processingId}}/assignment
Authorization: Bearer {{otherEmployeeToken}}
HTTP 409
[Asserts]
jsonpath "$.error" == "ALREADY_ASSIGNED"

# the same employee again — idempotent, no second claim and no second event
POST {{BASE_URL}}/processing/{{processingId}}/assignment
Authorization: Bearer {{employeeToken}}
HTTP 200
[Asserts]
jsonpath "$.state" == "assigned"

# the list follows the claim (element 15 refresh on event)
GET {{BASE_URL}}/orders
Authorization: Bearer {{employeeToken}}
HTTP 200
[Asserts]
jsonpath "$.items[0].state" == "assigned"
jsonpath "$.items[0].assignee" exists

# the handover is closed to every scope but the shopping service (element 11 auth matrix)
POST {{BASE_URL}}/orders
Authorization: Bearer {{employeeToken}}
Content-Type: application/json
{ "orderHash": "sha256:not-mine", "placedAt": "2026-11-19T09:31:12Z", "lines": [] }
HTTP 403

# ── The processing snapshot (element 11) ─────────────────────────────────
GET {{BASE_URL}}/processing/{{processingId}}
Authorization: Bearer {{employeeToken}}
HTTP 200
[Asserts]
jsonpath "$.processingId" == "{{processingId}}"
jsonpath "$.state" == "assigned"
jsonpath "$.assignee" exists
jsonpath "$.positions[0].state" == "awaitingScan"
jsonpath "$.openIssues" == 0

# the resolver scope opens the same read-only state (element 11 auth matrix)
GET {{BASE_URL}}/processing/{{processingId}}
Authorization: Bearer {{resolverToken}}
HTTP 200

# ── Pick list (element 10) ────────────────────────────────────────────────
GET {{BASE_URL}}/processing/{{processingId}}/pick-list
Authorization: Bearer {{employeeToken}}
HTTP 200
[Asserts]
jsonpath "$.lines[0].productNumber" == "75423"
jsonpath "$.lines[0].quantity" == 1
jsonpath "$.lines[0].locations" count > 0

# ── Scan a position — one scan = `picked` (elements 02, 16; Q-16-1 default R-A) ─────────────────────────────────────
POST {{BASE_URL}}/processing/{{processingId}}/positions/75423
Authorization: Bearer {{employeeToken}}
Content-Type: application/json
{ "confirmation": "picked" }
HTTP 200
[Asserts]
jsonpath "$.positions[0].state" == "picked"
jsonpath "$.state" == "picking"

# same scan again — idempotent
POST {{BASE_URL}}/processing/{{processingId}}/positions/75423
Authorization: Bearer {{employeeToken}}
Content-Type: application/json
{ "confirmation": "picked" }
HTTP 200
[Asserts]
jsonpath "$.positions[0].state" == "picked"

# a position the order does not have — the one other resource keyed by a path segment
POST {{BASE_URL}}/processing/{{processingId}}/positions/40404
Authorization: Bearer {{employeeToken}}
Content-Type: application/json
{ "confirmation": "picked" }
HTTP 404
[Asserts]
jsonpath "$.error" == "POSITION_NOT_FOUND"

# packing is the separate second step (element 02 `pack`, element 16 bulk action)
POST {{BASE_URL}}/processing/{{processingId}}/packing
Authorization: Bearer {{employeeToken}}
Content-Type: application/json
{ "productNumbers": ["75423"] }
HTTP 200
[Asserts]
jsonpath "$.positions[0].state" == "packed"
jsonpath "$.state" == "packed"

# ── Ready for delivery (element 08) ───────────────────────────────────────
POST {{BASE_URL}}/processing/{{processingId}}/readiness
Authorization: Bearer {{employeeToken}}
HTTP 200
[Asserts]
jsonpath "$.state" == "readyForDelivery"
jsonpath "$.parcel.waybill" isString
jsonpath "$.parcel.waybill" != ""

# a second order, worked to `picking` — readiness is refused while its positions are open (element 08 DT-08-3)
POST {{BASE_URL}}/orders
Authorization: Bearer {{shoppingToken}}
Content-Type: application/json
{
  "orderHash": "sha256:2b7e4c11",
  "placedAt": "2026-11-19T10:02:00Z",
  "lines": [{ "productNumber": "75423", "name": "LEGO X-Wing 75423", "quantity": 1 },
            { "productNumber": "99881", "name": "LEGO Orchidea 99881", "quantity": 3 }],
  "delivery": { "methodCode": "PARCEL_LOCKER", "location": { "kind": "PICKUP_POINT", "pointId": "KRA01M" } },
  "payment": { "methodCode": "PAY_DEFERRED" },
  "invoicing": { "buyerName": "Jan Kowalski", "nip": null,
                 "address": { "street": "ul. Mineralna 5A", "city": "Kraków", "postalCode": "30-001", "country": "PL" } }
}
HTTP 201
[Captures]
openProcessingId: jsonpath "$.processingId"
[Asserts]
jsonpath "$.state" == "pending"

POST {{BASE_URL}}/processing/{{openProcessingId}}/assignment
Authorization: Bearer {{employeeToken}}
HTTP 201

POST {{BASE_URL}}/processing/{{openProcessingId}}/positions/75423
Authorization: Bearer {{employeeToken}}
Content-Type: application/json
{ "confirmation": "picked" }
HTTP 200
[Asserts]
jsonpath "$.state" == "picking"

POST {{BASE_URL}}/processing/{{openProcessingId}}/packing
Authorization: Bearer {{employeeToken}}
Content-Type: application/json
{ "productNumbers": ["75423"] }
HTTP 200
[Asserts]
jsonpath "$.state" == "picking"

# not ready — a position of the order is still open
POST {{BASE_URL}}/processing/{{openProcessingId}}/readiness
Authorization: Bearer {{employeeToken}}
HTTP 409
[Asserts]
jsonpath "$.error" == "NOT_READY"
jsonpath "$.reasons" count > 0
jsonpath "$.reasons[0]" == "notPacked"

# ── Issues and hold (elements 04, 09, 17) ─────────────────────────────────
# the second order is `picking` with one position still open: one issue holds the whole order (H1)
POST {{BASE_URL}}/processing/{{openProcessingId}}/issues
Authorization: Bearer {{employeeToken}}
Content-Type: application/json
{ "kind": "LOST", "productNumber": "99881" }
HTTP 201
[Captures]
issueId: jsonpath "$.issueId"
[Asserts]
jsonpath "$.issueId" isString
jsonpath "$.issueId" != ""
jsonpath "$.orderState" == "onHold"
jsonpath "$.state" == "onHold"
jsonpath "$.openIssues" == 1

# a second issue on a held order is recorded, and places no second hold (H2)
POST {{BASE_URL}}/processing/{{openProcessingId}}/issues
Authorization: Bearer {{employeeToken}}
Content-Type: application/json
{ "kind": "WRONG_LOCATION", "productNumber": "99881" }
HTTP 201
[Captures]
secondIssueId: jsonpath "$.issueId"
[Asserts]
jsonpath "$.orderState" == "onHold"
jsonpath "$.openIssues" == 2

# a hold blocks every command of the order, the scan of an open position included
POST {{BASE_URL}}/processing/{{openProcessingId}}/positions/99881
Authorization: Bearer {{employeeToken}}
Content-Type: application/json
{ "confirmation": "picked" }
HTTP 409
[Asserts]
jsonpath "$.error" == "ON_HOLD"

# … readines too, with the decision's own reason
POST {{BASE_URL}}/processing/{{openProcessingId}}/readiness
Authorization: Bearer {{employeeToken}}
HTTP 409
[Asserts]
jsonpath "$.error" == "NOT_READY"
jsonpath "$.reasons[0]" == "onHold"

# the resolver's dashboard carries the open gaps
GET {{BASE_URL}}/issues
Authorization: Bearer {{resolverToken}}
HTTP 200
[Asserts]
jsonpath "$.items[0].issueId" isString
jsonpath "$.items[0].issueId" != ""
jsonpath "$.items[0].kind" exists
jsonpath "$.items[0].issueId" == "{{issueId}}"
jsonpath "$.items[0].orderState" == "onHold"
jsonpath "$.items[0].product.productNumber" == "99881"

# resolver endpoints are closed to the employee scope
GET {{BASE_URL}}/issues
Authorization: Bearer {{employeeToken}}
HTTP 403

# a resume that leaves an issue open is refused, and says how many remain (H6)
POST {{BASE_URL}}/processing/{{openProcessingId}}/hold/resume
Authorization: Bearer {{resolverToken}}
Content-Type: application/json
{ "closedIssueIds": ["{{issueId}}"] }
HTTP 409
[Asserts]
jsonpath "$.error" == "ISSUES_REMAIN"
jsonpath "$.openIssues" == 1

# an issue the order does not account for is the unknown resource it is (register D11)
POST {{BASE_URL}}/processing/{{openProcessingId}}/hold/resume
Authorization: Bearer {{resolverToken}}
Content-Type: application/json
{ "closedIssueIds": ["ISS-2026-9999"] }
HTTP 404
[Asserts]
jsonpath "$.error" == "ISSUE_UNKNOWN"

# the resume that names every open issue lifts the hold to the state it was held from (H5)
POST {{BASE_URL}}/processing/{{openProcessingId}}/hold/resume
Authorization: Bearer {{resolverToken}}
Content-Type: application/json
{ "closedIssueIds": ["{{issueId}}", "{{secondIssueId}}"] }
HTTP 200
[Asserts]
jsonpath "$.state" == "picking"
jsonpath "$.openIssues" == 0
jsonpath "$.issue" == null
jsonpath "$.closedIssueIds[0]" == "{{issueId}}"

# the dashboard drops the order whose hold was lifted
GET {{BASE_URL}}/issues
Authorization: Bearer {{resolverToken}}
HTTP 200
[Asserts]
jsonpath "$.items[0]" not exists

# a resume of an order that is not held is the no-op of H7; the employee scope may call it too
POST {{BASE_URL}}/processing/{{openProcessingId}}/hold/resume
Authorization: Bearer {{employeeToken}}
Content-Type: application/json
{ "closedIssueIds": [] }
HTTP 200
[Asserts]
jsonpath "$.state" == "picking"
jsonpath "$.closedIssueIds" isEmpty

# a third order, held for a gap nobody closed yet: an empty request cannot vouch for it
POST {{BASE_URL}}/orders
Authorization: Bearer {{shoppingToken}}
Content-Type: application/json
{
  "orderHash": "sha256:3c9d7e21",
  "placedAt": "2026-11-19T10:41:09Z",
  "lines": [{ "productNumber": "75423", "name": "LEGO X-Wing 75423", "quantity": 1 }],
  "delivery": { "methodCode": "PARCEL_LOCKER", "location": { "kind": "PICKUP_POINT", "pointId": "KRA01M" } },
  "payment": { "methodCode": "PAY_DEFERRED" },
  "invoicing": { "buyerName": "Jan Kowalski", "nip": null,
                 "address": { "street": "ul. Mineralna 5A", "city": "Kraków", "postalCode": "30-001", "country": "PL" } }
}
HTTP 201
[Captures]
heldProcessingId: jsonpath "$.processingId"
[Asserts]
jsonpath "$.state" == "pending"

POST {{BASE_URL}}/processing/{{heldProcessingId}}/assignment
Authorization: Bearer {{employeeToken}}
HTTP 201

POST {{BASE_URL}}/processing/{{heldProcessingId}}/positions/75423
Authorization: Bearer {{employeeToken}}
Content-Type: application/json
{ "confirmation": "picked" }
HTTP 200
[Asserts]
jsonpath "$.state" == "picking"

POST {{BASE_URL}}/processing/{{heldProcessingId}}/issues
Authorization: Bearer {{employeeToken}}
Content-Type: application/json
{ "kind": "STOLEN", "productNumber": "75423" }
HTTP 201
[Asserts]
jsonpath "$.orderState" == "onHold"

POST {{BASE_URL}}/processing/{{heldProcessingId}}/hold/resume
Authorization: Bearer {{employeeToken}}
Content-Type: application/json
{ "closedIssueIds": [] }
HTTP 409
[Asserts]
jsonpath "$.error" == "ISSUES_REMAIN"
jsonpath "$.openIssues" == 1

# ── Documents (elements 05, 06) ───────────────────────────────────────────
GET {{BASE_URL}}/processing/{{processingId}}/documents/label
Authorization: Bearer {{employeeToken}}
HTTP 200
[Asserts]
header "Content-Type" contains "application/pdf"

# ── Customer state view (element 18) ──────────────────────────────────────
GET {{BASE_URL}}/processing/{{processingId}}/state
Authorization: Bearer {{customerToken}}
HTTP 200
[Asserts]
jsonpath "$.state" == "readyForDelivery"
jsonpath "$.legs" count > 0
jsonpath "$.assignee" not exists
jsonpath "$.issue" not exists

# ── Unknown resources ─────────────────────────────────────────────────────
POST {{BASE_URL}}/processing/OP-DOES-NOT-EXIST/assignment
Authorization: Bearer {{employeeToken}}
HTTP 404
[Asserts]
jsonpath "$.error" == "PROCESSING_NOT_FOUND"

GET {{BASE_URL}}/processing/OP-DOES-NOT-EXIST
Authorization: Bearer {{employeeToken}}
HTTP 404
[Asserts]
jsonpath "$.error" == "PROCESSING_NOT_FOUND"

# the same refusal on the documents route: one 404 code per /processing/{id}/… path
GET {{BASE_URL}}/processing/OP-DOES-NOT-EXIST/documents/label
Authorization: Bearer {{employeeToken}}
HTTP 404
[Asserts]
jsonpath "$.error" == "PROCESSING_NOT_FOUND"
