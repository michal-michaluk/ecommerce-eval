# Fulfillment API — executable contract (element 11)
# Run — six variables are required (shoppingToken and otherEmployeeToken included):
#   hurl --test --variable BASE_URL=http://localhost:8081 \
#     --variable shoppingToken=$SHOP --variable employeeToken=$EMP --variable otherEmployeeToken=$EMP2 \
#     --variable resolverToken=$RES --variable customerToken=$CUS \
#     prototypes/fulfillment-api.hurl
# No money appears in any payload (spec D5).

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
jsonpath "$.processingId" == "OP-2026-0001"
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

# ── Orders list (element 15) ──────────────────────────────────────────────
GET {{BASE_URL}}/orders
Authorization: Bearer {{employeeToken}}
HTTP 200
[Asserts]
jsonpath "$.items" exists
jsonpath "$.items[0].processingId" exists
jsonpath "$.items[0].state" exists
jsonpath "$.items[0].assignee" exists

# ── Assignment (elements 07, 15) ──────────────────────────────────────────
POST {{BASE_URL}}/processing/OP-2026-0001/assignment
Authorization: Bearer {{employeeToken}}
HTTP 201
[Asserts]
jsonpath "$.state" == "assigned"
jsonpath "$.assignee" exists

# second employee — refused, never queued
POST {{BASE_URL}}/processing/OP-2026-0001/assignment
Authorization: Bearer {{otherEmployeeToken}}
HTTP 409
[Asserts]
jsonpath "$.error" == "ALREADY_ASSIGNED"

# ── Pick list (element 10) ────────────────────────────────────────────────
GET {{BASE_URL}}/processing/OP-2026-0001/pick-list
Authorization: Bearer {{employeeToken}}
HTTP 200
[Asserts]
jsonpath "$.lines[0].productNumber" == "75423"
jsonpath "$.lines[0].quantity" == 1
jsonpath "$.lines[0].locations" exists

# ── Scan a position — one scan = `picked` (elements 02, 16; Q-16-1 default R-A) ─────────────────────────────────────
POST {{BASE_URL}}/processing/OP-2026-0001/positions/75423
Authorization: Bearer {{employeeToken}}
Content-Type: application/json
{ "confirmation": "picked" }
HTTP 200
[Asserts]
jsonpath "$.positions[0].state" == "picked"
jsonpath "$.state" == "picking"

# same scan again — idempotent
POST {{BASE_URL}}/processing/OP-2026-0001/positions/75423
Authorization: Bearer {{employeeToken}}
Content-Type: application/json
{ "confirmation": "picked" }
HTTP 200
[Asserts]
jsonpath "$.positions[0].state" == "picked"

# packing is the separate second step (element 02 `pack`, element 16 bulk action)
POST {{BASE_URL}}/processing/OP-2026-0001/packing
Authorization: Bearer {{employeeToken}}
Content-Type: application/json
{ "productNumbers": ["75423"] }
HTTP 200
[Asserts]
jsonpath "$.positions[0].state" == "packed"
jsonpath "$.state" == "packed"

# ── Ready for delivery (element 08) ───────────────────────────────────────
POST {{BASE_URL}}/processing/OP-2026-0001/readiness
Authorization: Bearer {{employeeToken}}
HTTP 200
[Asserts]
jsonpath "$.state" == "readyForDelivery"
jsonpath "$.parcel.waybill" exists

# not ready — open blocking issue
POST {{BASE_URL}}/processing/OP-2026-0002/readiness
Authorization: Bearer {{employeeToken}}
HTTP 409
[Asserts]
jsonpath "$.error" == "NOT_READY"
jsonpath "$.reasons" exists

# ── Issues and hold (elements 04, 09, 17) ─────────────────────────────────
POST {{BASE_URL}}/processing/OP-2026-0002/issues
Authorization: Bearer {{employeeToken}}
Content-Type: application/json
{ "kind": "LOST", "productNumber": "75423" }
HTTP 201
[Asserts]
jsonpath "$.issueId" exists
jsonpath "$.orderState" == "onHold"

GET {{BASE_URL}}/issues
Authorization: Bearer {{resolverToken}}
HTTP 200
[Asserts]
jsonpath "$.items[0].issueId" exists
jsonpath "$.items[0].kind" exists

# resolver endpoints are closed to the employee scope
GET {{BASE_URL}}/issues
Authorization: Bearer {{employeeToken}}
HTTP 403

# ── Resume from hold (element 09 H5/H6, spec.md D7/D8) ───────────────────────
POST {{BASE_URL}}/processing/OP-2026-0002/hold/resume
Authorization: Bearer {{resolverToken}}
Content-Type: application/json
{ "closedIssueIds": ["ISS-2026-0031"] }
HTTP 200
[Asserts]
jsonpath "$.state" == "picking"
jsonpath "$.openIssues" == 0
jsonpath "$.closedIssueIds[0]" == "ISS-2026-0031"

# an issue the request does not name stays open and blocks the resume; the employee scope may call it too
POST {{BASE_URL}}/processing/OP-2026-0003/hold/resume
Authorization: Bearer {{employeeToken}}
Content-Type: application/json
{ "closedIssueIds": [] }
HTTP 409
[Asserts]
jsonpath "$.error" == "ISSUES_REMAIN"
jsonpath "$.openIssues" == 1

# ── Documents (elements 05, 06) ───────────────────────────────────────────
GET {{BASE_URL}}/processing/OP-2026-0001/documents/goods-issue-note
Authorization: Bearer {{employeeToken}}
HTTP 200
[Asserts]
header "Content-Type" == "application/pdf"

GET {{BASE_URL}}/processing/OP-2026-0001/documents/label
Authorization: Bearer {{employeeToken}}
HTTP 200
[Asserts]
header "Content-Type" contains "application/pdf"

# ── Customer state view (element 18) ──────────────────────────────────────
GET {{BASE_URL}}/processing/OP-2026-0001/state
Authorization: Bearer {{customerToken}}
HTTP 200
[Asserts]
jsonpath "$.state" == "readyForDelivery"
jsonpath "$.legs" exists
jsonpath "$.assignee" not exists
jsonpath "$.issue" not exists

# ── Unknown resources ─────────────────────────────────────────────────────
GET {{BASE_URL}}/processing/OP-DOES-NOT-EXIST
Authorization: Bearer {{employeeToken}}
HTTP 404
[Asserts]
jsonpath "$.error" == "PROCESSING_NOT_FOUND"
