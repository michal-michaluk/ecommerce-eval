# Fulfillment API — the executable contract of element 11, phase 1 (order processing core).
# Run — six variables are required:
#   hurl --test --variable BASE_URL=http://localhost:8081 \
#     --variable shoppingToken=$SHOP --variable employeeToken=$EMP --variable otherEmployeeToken=$EMP2 \
#     --variable resolverToken=$RES --variable customerToken=$CUS \
#     e2e/fulfillment-api.hurl
# No money appears in any payload (spec D5).
# Sections this file does not carry yet — pick list, scan, packing, readiness, issues, resume, documents, customer
# state — belong to the later phases of the implementation plan and land with them.
#
# Two deliberate differences from docs/specs/fulfillment/prototypes/fulfillment-api.hurl, both recorded in
# docs/phase-1-decisions.md: the processing id is captured instead of asserted as the literal "OP-2026-0001" (the id is
# minted from a sequence and carries the current year), and the unknown-resource 404 is asked of the assignment route,
# because the GET /processing/{id} route it uses in the prototype belongs to a later phase.

# ── Unauthenticated ───────────────────────────────────────────────────────
GET {{BASE_URL}}/orders
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

# ── Unknown resources ─────────────────────────────────────────────────────
POST {{BASE_URL}}/processing/OP-DOES-NOT-EXIST/assignment
Authorization: Bearer {{employeeToken}}
HTTP 404
[Asserts]
jsonpath "$.error" == "PROCESSING_NOT_FOUND"
