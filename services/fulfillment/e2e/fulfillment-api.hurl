# Fulfillment API — the executable contract of element 11, phase 1 (order processing core), phase 2 (pick list, scan,
# packing, the processing snapshot) and phase 3 (parcel registration, readiness, the label, the customer state view).
# Run — six variables are required:
#   hurl --test --variable BASE_URL=http://localhost:8081 \
#     --variable shoppingToken=$SHOP --variable employeeToken=$EMP --variable otherEmployeeToken=$EMP2 \
#     --variable resolverToken=$RES --variable customerToken=$CUS \
#     e2e/fulfillment-api.hurl
# No money appears in any payload (spec D5).
# Sections this file does not carry yet — issues, resume, the goods issue note — belong to the later phases of the
# implementation plan and land with them; the goods issue note is out of the plan entirely (register D15).
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
jsonpath "$.processingId" exists
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
jsonpath "$.missing" exists

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
jsonpath "$.items" exists
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
jsonpath "$.lines[0].locations" exists

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
jsonpath "$.parcel.waybill" exists

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
jsonpath "$.reasons" exists
jsonpath "$.reasons[0]" == "notPacked"

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
jsonpath "$.legs" exists
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
