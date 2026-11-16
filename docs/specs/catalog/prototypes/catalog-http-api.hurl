# catalog — HTTP API (element 12)
# Four resource shapes, one edit endpoint per family.
#   /offers/{offerId}            — the offer itself: state (removal), publish history
#   /offers/{offerId}/draft      — description draft + media + availability
#   /offers/{offerId}/pricing    — prices and discounts
#   /offers/{offerId}/review     — review process
# POST = start / init / create · PATCH = edit · GET = read
#
# Usage: hurl --test --variable BASE_URL=http://localhost:8080 prototypes/catalog-http-api.hurl
# Scopes: catalog-author, catalog-reviewer, catalog-photographer, catalog-sales

# ── 0. Unauthenticated is refused ───────────────────────────────────────────────────────────────
GET {{BASE_URL}}/offers?page=0&size=20
HTTP 401

# ══ Offer ═══════════════════════════════════════════════════════════════════════════════════════

# ── 1. ContentAuthor creates the offer (title + identifiers, empty draft) ──────────────────────
POST {{BASE_URL}}/offers
Authorization: Bearer {{author_token}}
Content-Type: application/json
{
  "title": "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423",
  "productNumber": "75423",
  "eanGtin": "5702017817705",
  "categoryPath": ["Dziecko", "Zabawki", "Klocki", "LEGO", "Zestawy", "Star Wars"]
}
HTTP 201
[Asserts]
jsonpath "$.offerId" exists
jsonpath "$.offerState" == "draft"
jsonpath "$.offerVersion" == null
header "Location" exists

# ══ Draft — description + media + availability ══════════════════════════════════════════════════

# ── 2. Author edits the description draft ──────────────────────────────────────────────────────
PATCH {{BASE_URL}}/offers/{{offer_id}}/draft
Authorization: Bearer {{author_token}}
Content-Type: application/json
{
  "description": {
    "parameters": [
      { "code": "childAge",     "label": "Wiek dziecka",     "value": "6+", "valueType": "rangeFrom", "from": 6, "unit": "lat" },
      { "code": "pieceCount",   "label": "Liczba elementów", "value": 581,  "valueType": "integer" },
      { "code": "gender",       "label": "Płeć",             "value": ["boys", "girls", "unisex"], "valueType": "enumSet" },
      { "code": "certificates", "label": "Certyfikaty, opinie, atesty", "value": ["CE", "EN 71"], "valueType": "list" },
      { "code": "safetyInfo",   "label": "Informacje dot. bezpieczeństwa i zgodności produktu",
        "value": "Nie nadaje się dla dzieci w wieku poniżej 36 miesięcy", "valueType": "text" },
      { "code": "condition",    "label": "Stan",             "value": "Nowy", "valueType": "text" }
    ],
    "blocks": [
      { "type": "paragraph", "text": "Odkryj nową galaktykę LEGO® Star Wars™ dzięki interaktywnej zabawce konstrukcyjnej X-Wing™ Czerwona Piątka Luke'a (75423). Ten zestaw Wszystko w jednym, będący częścią serii zestawów obsługiwanych przez system LEGO SMART Play™, zawiera wszystkie elementy SMART Play™ — SMART klocek i ładowarkę, dwie SMART minifigurki LEGO oraz pięć SMART Tagów, które umożliwiają responsywną zabawę, dynamiczne efekty dźwiękowe i świetlne oraz wiele innych funkcji." },
      { "type": "paragraph", "text": "Posłuchaj ryku silnika podczas lotu myśliwca X-wing. Aktywuj dźwięki strzałów laserowych X-winga i wieżyczki imperialnej. Monitoruj komunikację z centrum dowodzenia. Ożyw SMART Luke'a Skywalkera i SMART Księżniczkę Leię. Zatankuj i napraw X-winga za pomocą transportera." },
      { "type": "bulletList", "items": ["Interaktywna zabawka konstrukcyjna ożywia X-winga poprzez zabawę aktywowaną ruchem", "SMART klocek umożliwia korzystanie z systemu SMART Play™ ze światłami i dźwiękami", "Model emituje dźwięki i światła w reakcji na ruchy i zabawę dzieci"] }
    ]
  }
}
HTTP 200
[Asserts]
jsonpath "$.description.title" == "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423"
jsonpath "$.quality.findings[*].rule" not contains "QP-2"

# ── 3. Photographer adds media through the same draft PATCH ───────────────────────────────────
PATCH {{BASE_URL}}/offers/{{offer_id}}/draft
Authorization: Bearer {{photographer_token}}
Content-Type: application/json
{
  "media": {
    "add": [
      { "kind": "image", "format": "jpg", "position": 1, "primary": true,  "url": "https://cdn.example/M-01.jpg" },
      { "kind": "image", "format": "jpg", "position": 2, "primary": false, "url": "https://cdn.example/M-02.jpg" },
      { "kind": "video", "format": "mp4", "position": 3, "primary": false, "url": "https://cdn.example/M-03.mp4", "durationSec": 18 }
    ]
  }
}
HTTP 200
[Asserts]
jsonpath "$.media[0].primary" == true
jsonpath "$.media[2].kind" == "video"

# ── 4. Author sets availability start ─────────────────────────────────────────────────────────
PATCH {{BASE_URL}}/offers/{{offer_id}}/draft
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "availabilityStart": "2026-07-01" }
HTTP 200
[Asserts]
jsonpath "$.availabilityStart" == "2026-07-01"

# ── 5. Read the draft — description, media, availability, findings ────────────────────────────
GET {{BASE_URL}}/offers/{{offer_id}}/draft
Authorization: Bearer {{author_token}}
HTTP 200
[Asserts]
jsonpath "$.description.parameters[0].code" == "childAge"
jsonpath "$.media[0].primary" == true
jsonpath "$.availabilityStart" == "2026-07-01"
jsonpath "$.quality.findings" isCollection

# ══ Pricing ═════════════════════════════════════════════════════════════════════════════════════

# ── 6. Sales sets the current price and a discount — one PATCH ────────────────────────────────
PATCH {{BASE_URL}}/offers/{{offer_id}}/pricing
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{
  "prices": [ { "currency": "PLN", "amountMinor": 30989 } ],
  "discounts": [ { "kind": "percentage", "basisPoints": 1000, "validFrom": "2026-06-20T00:00:00Z", "validUntil": "2026-06-30T23:59:59Z" } ]
}
HTTP 200
[Asserts]
jsonpath "$.current.amountMinor" == 30989
jsonpath "$.current.validUntil" == null
jsonpath "$.discounts.active[0].basisPoints" == 1000

# ── 7. Sales schedules a future price through the same PATCH ──────────────────────────────────
PATCH {{BASE_URL}}/offers/{{offer_id}}/pricing
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{ "prices": [ { "currency": "PLN", "amountMinor": 29900, "validFrom": "2026-07-01T00:00:00Z" } ] }
HTTP 200
[Asserts]
jsonpath "$.scheduled[0].amountMinor" == 29900

# ── 8. Read pricing — current, scheduled, history, discounts ──────────────────────────────────
GET {{BASE_URL}}/offers/{{offer_id}}/pricing
Authorization: Bearer {{sales_token}}
HTTP 200
[Asserts]
jsonpath "$.current.amountMinor" == 30989
jsonpath "$.scheduled[*].amountMinor" contains 29900
jsonpath "$.history" isCollection
jsonpath "$.discounts.active[0].kind" == "percentage"

# ── 9. Remove a scheduled entry through the same PATCH ───────────────────────────────────────
PATCH {{BASE_URL}}/offers/{{offer_id}}/pricing
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{ "removePriceIds": ["{{scheduled_price_id}}"] }
HTTP 200
[Asserts]
jsonpath "$.scheduled" isEmpty

# ══ Review ══════════════════════════════════════════════════════════════════════════════════════

# ── 10. Author starts the review (POST = start, freezes the candidate) ────────────────────────
POST {{BASE_URL}}/offers/{{offer_id}}/review
Authorization: Bearer {{author_token}}
Content-Type: application/json
{}
HTTP 201
[Asserts]
jsonpath "$.reviewId" exists
jsonpath "$.state" == "requested"
jsonpath "$.findings" isCollection

# ── 11. Reviewer reads the pending list ───────────────────────────────────────────────────────
GET {{BASE_URL}}/reviews?page=0&size=20
Authorization: Bearer {{reviewer_token}}
HTTP 200
[Asserts]
jsonpath "$.items[0].reviewId" exists
jsonpath "$.items[0].findingsCount" isInteger
jsonpath "$.page.totalElements" isInteger

# ── 12. Reviewer reads the review — frozen candidate + findings ───────────────────────────────
GET {{BASE_URL}}/offers/{{offer_id}}/review
Authorization: Bearer {{reviewer_token}}
HTTP 200
[Asserts]
jsonpath "$.state" == "requested"
jsonpath "$.candidate.description.title" exists
jsonpath "$.candidate.media[0].primary" == true

# ── 13. Reviewer edits the review to accepted — the edit publishes (D15) ──────────────────────
PATCH {{BASE_URL}}/offers/{{offer_id}}/review
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "decision": "accepted" }
HTTP 200
[Asserts]
jsonpath "$.state" == "accepted"
jsonpath "$.publishedOfferVersion" == 1

GET {{BASE_URL}}/offers/{{offer_id}}
Authorization: Bearer {{author_token}}
HTTP 200
[Asserts]
jsonpath "$.offerState" == "published"
jsonpath "$.offerVersion" == 1
jsonpath "$.visible" == false

# ── 14. Replaying the same decision is a no-op, not a second version ──────────────────────────
PATCH {{BASE_URL}}/offers/{{offer_id}}/review
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "decision": "accepted" }
HTTP 200
[Asserts]
jsonpath "$.publishedOfferVersion" == 1

# ── 15. Revert creates a new published version (POST = create) ────────────────────────────────
POST {{BASE_URL}}/offers/{{offer_id}}/versions
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "copyFrom": 1 }
HTTP 201
[Asserts]
jsonpath "$.offerVersion" == 2

# ── 16. Removal is an edit of the offer state (not of the draft) ──────────────────────────────
PATCH {{BASE_URL}}/offers/{{offer_id}}
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "offerState": "removed" }
HTTP 200
[Asserts]
jsonpath "$.offerState" == "removed"
jsonpath "$.visible" == false

# ══ Error paths ═══════════════════════════════════════════════════════════════════════════════

# 401 — no token
POST {{BASE_URL}}/offers
Content-Type: application/json
{ "title": "x" }
HTTP 401

# 403 — an author may not decide a review
PATCH {{BASE_URL}}/offers/{{offer_id}}/review
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "decision": "accepted" }
HTTP 403

# 403 — field-level scope: a photographer may not edit the description
PATCH {{BASE_URL}}/offers/{{offer_id}}/draft
Authorization: Bearer {{photographer_token}}
Content-Type: application/json
{ "description": { "title": "x" } }
HTTP 403
[Asserts]
jsonpath "$.reason" == "FIELD_NOT_IN_SCOPE"

# 403 — field-level scope: an author may not touch media
PATCH {{BASE_URL}}/offers/{{offer_id}}/draft
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "media": { "add": [ { "kind": "image", "format": "jpg", "position": 4, "primary": false, "url": "https://cdn.example/M-04.jpg" } ] } }
HTTP 403
[Asserts]
jsonpath "$.reason" == "FIELD_NOT_IN_SCOPE"

# 403 — Sales may not edit the draft
PATCH {{BASE_URL}}/offers/{{offer_id}}/draft
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{ "availabilityStart": "2026-08-01" }
HTTP 403

# 400 — invalid GTIN check digit
POST {{BASE_URL}}/offers
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "title": "LEGO", "productNumber": "1", "eanGtin": "5702017817706", "categoryPath": ["Dziecko"] }
HTTP 400
[Asserts]
jsonpath "$.errors[0].field" == "eanGtin"

# 400 — draft PATCH with an unknown component
PATCH {{BASE_URL}}/offers/{{offer_id}}/draft
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "titlee": "typo" }
HTTP 400

# 400 — missing required paging parameter
GET {{BASE_URL}}/reviews
Authorization: Bearer {{reviewer_token}}
HTTP 400

# 404 — unknown offer
GET {{BASE_URL}}/offers/OFR-000000
Authorization: Bearer {{author_token}}
HTTP 404

# 409 — start review without a primary image (02 RULE-1)
POST {{BASE_URL}}/offers/{{offer_without_media}}/review
Authorization: Bearer {{author_token}}
Content-Type: application/json
{}
HTTP 409
[Asserts]
jsonpath "$.reason" == "MEDIA_IMAGE_MISSING"

# 409 — acceptance blocked by blocking findings, no override exists (09 R2)
PATCH {{BASE_URL}}/offers/{{offer_with_findings}}/review
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "decision": "accepted" }
HTTP 409
[Asserts]
jsonpath "$.reason" == "QUALITY_FINDINGS"
jsonpath "$.findings" isCollection

# 201 — the author pushes a review past the gate with an explicit override (04 RULE-8)
POST {{BASE_URL}}/offers/{{offer_with_findings}}/review
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "override": { "findings": ["QP-6"] } }
HTTP 201
[Asserts]
jsonpath "$.state" == "requested"
jsonpath "$.override.findings[0]" == "QP-6"

# 409 — a started price entry cannot be removed, only closed by a new one (03 RULE-8)
PATCH {{BASE_URL}}/offers/{{offer_id}}/pricing
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{ "removePriceIds": ["{{started_price_id}}"] }
HTTP 409
[Asserts]
jsonpath "$.reason" == "PRICE_ALREADY_STARTED"

# 409 — second open review for the same offer (04 RULE-1)
# The offer is the one whose review the author started with an override above and nobody has decided yet: `{{offer_id}}` is removed
# by now, and the request asks the offer first (12 D49, 17 RULE-3), so it would answer OFFER_REMOVED instead.
POST {{BASE_URL}}/offers/{{offer_with_findings}}/review
Authorization: Bearer {{author_token}}
Content-Type: application/json
{}
HTTP 409
[Asserts]
jsonpath "$.reason" == "REVIEW_ALREADY_OPEN"

# 409 — no open review to decide
PATCH {{BASE_URL}}/offers/{{offer_without_review}}/review
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "decision": "rejected", "reason": "brak opisu" }
HTTP 409
[Asserts]
jsonpath "$.reason" == "REVIEW_NOT_OPEN"

# 409 — a removed offer cannot be edited, draft included
PATCH {{BASE_URL}}/offers/{{offer_id}}/draft
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "availabilityStart": "2026-08-01" }
HTTP 409
[Asserts]
jsonpath "$.reason" == "OFFER_REMOVED"
